package com.bmwcockpit.app.obd

import kotlinx.serialization.Serializable

@Serializable
data class Dtc(val code: String, val description: String?)

/** Outcome of one DTC request. Only [NoCodes] means "no codes"; failures are never shown as absence. */
@Serializable
sealed class DtcResult {
    @Serializable
    data class Codes(val codes: List<Dtc>) : DtcResult()

    @Serializable
    data object NoCodes : DtcResult()

    @Serializable
    data class Incomplete(val reason: String, val partial: List<Dtc>) : DtcResult()

    @Serializable
    data class NoResponse(val reason: String) : DtcResult()

    @Serializable
    data class Unsupported(val reason: String) : DtcResult()
}

object DtcDecoder {

    /** Converts the two raw bytes of a DTC into the SAE notation (e.g. 0x01 0x71 → P0171). */
    fun code(a: Int, b: Int): String {
        val letter = "PCBU"[(a shr 6) and 0x03]
        val d1 = (a shr 4) and 0x03
        val d2 = a and 0x0F
        return "%c%d%X%02X".format(letter, d1, d2, b and 0xFF)
    }

    /**
     * Decodes the answer to service 03 / 07 / 0A.
     * CAN: [0x43, count, A1, B1, …] (count is explicit, so a short payload is detected as incomplete).
     * Legacy: one or more frames [0x43, A1, B1, A2, B2, A3, B3] padded with 00 00.
     */
    fun decode(service: Int, messages: List<EcuMessage>, family: ProtocolFamily, status: ElmStatus): DtcResult {
        val responseSid = service + 0x40
        when (status) {
            ElmStatus.TIMEOUT -> return DtcResult.NoResponse("Tempo esgotado aguardando a ECU")
            ElmStatus.UNABLE_TO_CONNECT -> return DtcResult.NoResponse("Adaptador não conseguiu falar com a ECU")
            ElmStatus.BUS_ERROR -> return DtcResult.NoResponse("Erro no barramento durante a leitura")
            ElmStatus.STOPPED -> return DtcResult.NoResponse("Leitura interrompida pelo adaptador")
            ElmStatus.UNKNOWN_COMMAND -> return DtcResult.Unsupported("Comando não aceito pelo adaptador")
            ElmStatus.NO_DATA -> return DtcResult.NoResponse("ECU não respondeu (NO DATA)")
            ElmStatus.OK -> Unit
        }
        if (messages.isEmpty()) return DtcResult.NoResponse("Resposta sem dados reconhecíveis")
        val negative = messages.firstOrNull { it.data.size >= 2 && (it.data[0].toInt() and 0xFF) == 0x7F }
        val valid = messages.filter { it.data.isNotEmpty() && (it.data[0].toInt() and 0xFF) == responseSid }
        if (valid.isEmpty()) {
            return if (negative != null) DtcResult.Unsupported("ECU recusou o serviço %02X".format(service))
            else DtcResult.NoResponse("Resposta inesperada da ECU")
        }
        val codes = ArrayList<Dtc>()
        var incomplete: String? = null
        val isCan = family == ProtocolFamily.CAN_11 || family == ProtocolFamily.CAN_29 ||
            (family == ProtocolFamily.UNKNOWN && valid.any { it.data.size >= 2 && it.data.size % 2 == 0 })
        for (m in valid) {
            if (!m.complete) incomplete = "Mensagem multi-quadro incompleta da ECU ${m.ecu}"
            if (isCan) {
                if (m.data.size < 2) { incomplete = "Resposta truncada"; continue }
                val count = m.data[1].toInt() and 0xFF
                val available = (m.data.size - 2) / 2
                for (i in 0 until minOf(count, available)) {
                    val a = m.data[2 + i * 2].toInt() and 0xFF
                    val b = m.data[3 + i * 2].toInt() and 0xFF
                    if (a == 0 && b == 0) continue
                    val c = code(a, b)
                    codes += Dtc(c, DtcDescriptions.describe(c))
                }
                if (available < count) incomplete = "ECU informou $count código(s) mas enviou $available"
            } else {
                var i = 1
                while (i + 1 < m.data.size) {
                    val a = m.data[i].toInt() and 0xFF
                    val b = m.data[i + 1].toInt() and 0xFF
                    if (a != 0 || b != 0) {
                        val c = code(a, b)
                        codes += Dtc(c, DtcDescriptions.describe(c))
                    }
                    i += 2
                }
            }
        }
        val distinct = codes.distinctBy { it.code }
        return when {
            incomplete != null -> DtcResult.Incomplete(incomplete, distinct)
            distinct.isEmpty() -> DtcResult.NoCodes
            else -> DtcResult.Codes(distinct)
        }
    }
}

/** Portuguese descriptions for common generic (SAE) codes and codes frequent on the N20 engine. */
object DtcDescriptions {
    private val table = mapOf(
        "P0010" to "Atuador do comando de admissão (VANOS) – circuito",
        "P0011" to "Comando de admissão – posição adiantada demais",
        "P0012" to "Comando de admissão – posição atrasada demais",
        "P0013" to "Atuador do comando de escape (VANOS) – circuito",
        "P0014" to "Comando de escape – posição adiantada demais",
        "P0015" to "Comando de escape – posição atrasada demais",
        "P0016" to "Correlação virabrequim/comando de admissão",
        "P0017" to "Correlação virabrequim/comando de escape",
        "P0087" to "Pressão do rail de combustível muito baixa",
        "P0088" to "Pressão do rail de combustível muito alta",
        "P0101" to "Sensor de fluxo de ar (MAF) – faixa/desempenho",
        "P0102" to "Sensor de fluxo de ar (MAF) – sinal baixo",
        "P0103" to "Sensor de fluxo de ar (MAF) – sinal alto",
        "P0106" to "Sensor MAP – faixa/desempenho",
        "P0107" to "Sensor MAP – sinal baixo",
        "P0108" to "Sensor MAP – sinal alto",
        "P0111" to "Sensor de temperatura do ar de admissão – faixa",
        "P0112" to "Sensor de temperatura do ar de admissão – sinal baixo",
        "P0113" to "Sensor de temperatura do ar de admissão – sinal alto",
        "P0116" to "Sensor de temperatura do líquido de arrefecimento – faixa",
        "P0117" to "Sensor de temperatura do líquido de arrefecimento – sinal baixo",
        "P0118" to "Sensor de temperatura do líquido de arrefecimento – sinal alto",
        "P0121" to "Sensor de posição da borboleta – faixa/desempenho",
        "P0122" to "Sensor de posição da borboleta – sinal baixo",
        "P0123" to "Sensor de posição da borboleta – sinal alto",
        "P0128" to "Termostato – temperatura abaixo da regulagem",
        "P0130" to "Sonda lambda (banco 1, sensor 1) – circuito",
        "P0131" to "Sonda lambda (B1S1) – tensão baixa",
        "P0132" to "Sonda lambda (B1S1) – tensão alta",
        "P0133" to "Sonda lambda (B1S1) – resposta lenta",
        "P0135" to "Aquecimento da sonda lambda (B1S1) – circuito",
        "P0136" to "Sonda lambda (B1S2) – circuito",
        "P0141" to "Aquecimento da sonda lambda (B1S2) – circuito",
        "P0171" to "Mistura pobre (banco 1)",
        "P0172" to "Mistura rica (banco 1)",
        "P0191" to "Sensor de pressão do rail – faixa/desempenho",
        "P0201" to "Injetor cilindro 1 – circuito",
        "P0202" to "Injetor cilindro 2 – circuito",
        "P0203" to "Injetor cilindro 3 – circuito",
        "P0204" to "Injetor cilindro 4 – circuito",
        "P0234" to "Sobrepressão do turbo",
        "P0236" to "Sensor de pressão do turbo – faixa/desempenho",
        "P0237" to "Sensor de pressão do turbo – sinal baixo",
        "P0238" to "Sensor de pressão do turbo – sinal alto",
        "P0299" to "Pressão do turbo abaixo do esperado (underboost)",
        "P0300" to "Falha de ignição aleatória/múltiplos cilindros",
        "P0301" to "Falha de ignição, cilindro 1",
        "P0302" to "Falha de ignição, cilindro 2",
        "P0303" to "Falha de ignição, cilindro 3",
        "P0304" to "Falha de ignição, cilindro 4",
        "P0325" to "Sensor de detonação 1 – circuito",
        "P0335" to "Sensor de posição do virabrequim – circuito",
        "P0340" to "Sensor de posição do comando – circuito",
        "P0351" to "Bobina de ignição A – circuito",
        "P0352" to "Bobina de ignição B – circuito",
        "P0353" to "Bobina de ignição C – circuito",
        "P0354" to "Bobina de ignição D – circuito",
        "P0420" to "Eficiência do catalisador abaixo do limite (banco 1)",
        "P0440" to "Sistema EVAP – mau funcionamento",
        "P0441" to "Sistema EVAP – fluxo de purga incorreto",
        "P0442" to "Sistema EVAP – pequeno vazamento",
        "P0455" to "Sistema EVAP – grande vazamento",
        "P0456" to "Sistema EVAP – vazamento muito pequeno",
        "P0500" to "Sensor de velocidade do veículo",
        "P0505" to "Sistema de controle de marcha lenta",
        "P0506" to "Marcha lenta abaixo do esperado",
        "P0507" to "Marcha lenta acima do esperado",
        "P0562" to "Tensão do sistema baixa",
        "P0563" to "Tensão do sistema alta",
        "P0597" to "Termostato eletrônico – circuito aberto",
        "P0600" to "Falha de comunicação serial",
        "P0601" to "Módulo de controle – erro de checksum da memória",
        "P0606" to "Processador do módulo de controle",
        "P0700" to "Sistema de controle da transmissão (consultar TCM)",
        "P1000" to "Ciclo de monitoramento OBD não concluído",
        "P2187" to "Mistura pobre em marcha lenta (banco 1)",
        "P2188" to "Mistura rica em marcha lenta (banco 1)",
        "P2195" to "Sonda lambda (B1S1) indicando pobre",
        "P2196" to "Sonda lambda (B1S1) indicando rico",
        "P2261" to "Válvula de alívio do turbo (blow-off) – mecânica",
        "P2563" to "Sensor de posição da wastegate – faixa/desempenho",
        "U0100" to "Perda de comunicação com o módulo do motor (ECM)",
        "U0101" to "Perda de comunicação com o módulo da transmissão (TCM)",
        "U0121" to "Perda de comunicação com o módulo do ABS",
        "U0140" to "Perda de comunicação com o módulo da carroceria",
    )

    fun describe(code: String): String? = table[code.uppercase()]
}
