package com.bmwcockpit.app.obd

import kotlinx.coroutines.delay
import java.io.IOException

class ObdInitException(message: String) : IOException(message)

data class VehicleSessionInfo(
    val elmVersion: String,
    val protocolNumber: String,
    val protocolName: String,
    val family: ProtocolFamily,
    /** ECU selected for mode 01 requests (e.g. 7E8). */
    val ecu: String,
    /** Every ECU that answered the 0100 request. */
    val ecusSeen: List<String>,
    val supportedPids: Set<Int>,
    val headerFilterApplied: Boolean,
)

/**
 * ELM327 initialization: reset, echo/linefeed/spaces off, headers on (to separate ECUs), adaptive timing,
 * automatic protocol search, protocol report, ECU selection and supported-PID discovery.
 * Only read-only (AT and service 01) commands are sent.
 */
class Elm327Initializer(
    private val session: ElmSession,
    private val onStep: (String) -> Unit = {},
) {
    suspend fun initialize(): VehicleSessionInfo {
        onStep("Reiniciando adaptador (ATZ)")
        var reset = session.send("ATZ", 4_000)
        if (reset.status == ElmStatus.TIMEOUT) {
            // Some BLE adapters drop the first command right after the notification subscription.
            delay(500)
            reset = session.send("ATZ", 4_000)
            if (reset.status == ElmStatus.TIMEOUT) {
                throw ObdInitException("O adaptador não respondeu ao comando ATZ. Verifique se é um ELM327/Vgate e se está ligado à tomada OBD.")
            }
        }
        val version = reset.lines.firstOrNull { it.uppercase().contains("ELM") } ?: reset.lines.lastOrNull() ?: "ELM327"
        delay(300)
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH1", "ATAT1")) {
            val r = session.send(cmd, 2_000)
            if (!r.ok && cmd == "ATH1") {
                throw ObdInitException("O adaptador não aceitou ativar cabeçalhos (ATH1); sem eles não é possível separar as ECUs com segurança.")
            }
        }
        onStep("Procurando protocolo do veículo (ATSP0)")
        session.send("ATSP0", 2_000)
        val probe = session.send("0100", 10_000)
        when (probe.status) {
            ElmStatus.OK -> Unit
            ElmStatus.UNABLE_TO_CONNECT -> throw ObdInitException("O adaptador não encontrou um protocolo OBD2. Ligue a ignição (motor ligado ou posição de ignição) e tente novamente.")
            ElmStatus.NO_DATA -> throw ObdInitException("A ECU não respondeu ao pedido 0100. Ligue a ignição e tente novamente.")
            ElmStatus.TIMEOUT -> throw ObdInitException("Tempo esgotado na busca de protocolo.")
            else -> throw ObdInitException("Falha na comunicação com a ECU (${probe.status}).")
        }
        val dpn = session.send("ATDPN", 2_000).lines.firstOrNull() ?: ""
        val dp = session.send("ATDP", 2_000).lines.firstOrNull() ?: "Desconhecido"
        val family = ObdFrameParser.familyFromProtocolNumber(dpn)

        onStep("Identificando ECUs")
        val answers = ObdFrameParser.parse(probe.lines, family)
            .mapNotNull { m -> PidDecoder.supportedPids(0x00, m.data)?.let { m.ecu to it } }
        if (answers.isEmpty()) throw ObdInitException("Resposta de 0100 não reconhecida: ${probe.lines.joinToString(" / ")}")
        // Prefer the engine ECU: answers RPM (0C) and speed (0D); tie → lowest address (7E8 before 7E9).
        val chosen = answers.sortedWith(
            compareByDescending<Pair<String, Set<Int>>> { (_, s) -> (if (0x0C in s) 1 else 0) + (if (0x0D in s) 1 else 0) }
                .thenByDescending { it.second.size }
                .thenBy { it.first },
        ).first()
        val ecu = chosen.first

        var filtered = false
        if (family == ProtocolFamily.CAN_11 && ecu.length == 3) {
            val rx = ecu.toInt(16)
            if (rx in 0x7E8..0x7EF) {
                val tx = "%03X".format(rx - 8)
                filtered = session.send("ATSH$tx").ok && session.send("ATCRA$ecu").ok
            }
        } else if (family == ProtocolFamily.CAN_29 && ecu.length == 8 && ecu.startsWith("18DAF1")) {
            val node = ecu.substring(6)
            filtered = session.send("ATCP18").ok && session.send("ATSHDA${node}F1").ok && session.send("ATCRA$ecu").ok
        }

        onStep("Descobrindo PIDs suportados")
        val supported = chosen.second.toMutableSet()
        var base = 0x20
        while (base <= 0xA0 && (base in supported)) {
            val r = session.send("01%02X".format(base), 3_000)
            val set = ObdFrameParser.parse(r.lines, family)
                .filter { it.ecu == ecu }
                .firstNotNullOfOrNull { PidDecoder.supportedPids(base, it.data) }
                ?: break
            supported += set
            base += 0x20
        }
        return VehicleSessionInfo(
            elmVersion = version,
            protocolNumber = dpn,
            protocolName = dp,
            family = family,
            ecu = ecu,
            ecusSeen = answers.map { it.first },
            supportedPids = supported,
            headerFilterApplied = filtered,
        )
    }
}
