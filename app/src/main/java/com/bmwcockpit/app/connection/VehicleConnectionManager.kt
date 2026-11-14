package com.bmwcockpit.app.connection

import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.data.telemetry.Clock
import com.bmwcockpit.app.data.telemetry.TelemetryHub
import com.bmwcockpit.app.obd.DtcDecoder
import com.bmwcockpit.app.obd.DtcResult
import com.bmwcockpit.app.obd.Elm327Initializer
import com.bmwcockpit.app.obd.ElmSession
import com.bmwcockpit.app.obd.ObdFrameParser
import com.bmwcockpit.app.obd.ObdPoller
import com.bmwcockpit.app.obd.PidDecoder
import com.bmwcockpit.app.obd.VehicleSessionInfo
import com.bmwcockpit.app.transport.DeviceRef
import com.bmwcockpit.app.transport.ObdTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data class Connecting(val device: DeviceRef, val step: String) : ConnectionState()
    data class Connected(val device: DeviceRef, val info: VehicleSessionInfo, val transport: String) : ConnectionState()
    data class Reconnecting(val device: DeviceRef, val attempt: Int, val maxAttempts: Int, val lastError: String) : ConnectionState()
    data class Failed(val device: DeviceRef?, val message: String) : ConnectionState()
}

@Serializable
data class DtcReport(
    val timestampEpochMs: Long,
    val simulated: Boolean,
    val ecu: String,
    val milOn: Boolean?,
    val milCount: Int?,
    val stored: DtcResult,
    val pending: DtcResult,
    val permanent: DtcResult,
    /** Set when the report as a whole cannot be trusted (e.g. MIL count does not match the codes read). */
    val warning: String? = null,
)

/**
 * Owns the link to the OBD2 adapter: open transport → ELM327 init → polling, with timeout, optional
 * automatic reconnection and clean shutdown (socket/GATT closed, coroutines cancelled, hub source ended).
 */
class VehicleConnectionManager(
    private val hub: TelemetryHub,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val transportFactory: (DeviceRef) -> ObdTransport,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _trace = MutableStateFlow<List<String>>(emptyList())
    /** Last ELM327 exchanges, for the connection screen's log. */
    val trace: StateFlow<List<String>> = _trace.asStateFlow()

    @Volatile var autoReconnect: Boolean = true
    @Volatile var economyMode: Boolean = false
    @Volatile var pollingPaused: Boolean = false

    private var job: Job? = null
    private val lifecycle = Mutex()
    @Volatile private var session: ElmSession? = null
    @Volatile private var info: VehicleSessionInfo? = null
    private var hubToken = -1L

    private fun log(line: String) {
        val l = _trace.value
        _trace.value = (if (l.size >= 200) l.drop(l.size - 199) else l) + line
    }

    fun connect(device: DeviceRef) {
        scope.launch {
            lifecycle.withLock {
                stopLocked()
                _trace.value = emptyList()
                hubToken = hub.beginSource(SourceKind.VEHICLE)
                val myToken = hubToken
                job = scope.launch(ioDispatcher) { runLoop(device, myToken) }
            }
        }
    }

    fun disconnect() {
        scope.launch { lifecycle.withLock { stopLocked() } }
    }

    private suspend fun stopLocked() {
        job?.let {
            it.cancel()
            it.join()
        }
        job = null
        session = null
        info = null
        if (hubToken >= 0) hub.endSource(hubToken)
        hubToken = -1
        _state.value = ConnectionState.Idle
    }

    private suspend fun runLoop(device: DeviceRef, token: Long) {
        var attempt = 0
        var everConnected = false
        while (scope.isActive && hub.isCurrent(token)) {
            val transport = transportFactory(device)
            try {
                _state.value = ConnectionState.Connecting(device, "Abrindo ${device.type.label}")
                withTimeout(20_000) { transport.open() }
                val s = ElmSession(transport) { log(it) }
                val i = withTimeout(45_000) {
                    Elm327Initializer(s) { step -> _state.value = ConnectionState.Connecting(device, step) }.initialize()
                }
                session = s
                info = i
                attempt = 0
                _state.value = ConnectionState.Connected(device, i, transport.description)
                everConnected = true
                ObdPoller(s, i, clock, { economyMode }, { pollingPaused }) { batch -> hub.publish(token, batch) }.run()
            } catch (e: CancellationException) {
                transport.close()
                throw e
            } catch (e: Exception) {
                transport.close()
                session = null
                val msg = when (e) {
                    is kotlinx.coroutines.TimeoutCancellationException -> "Tempo esgotado na conexão/inicialização do adaptador"
                    else -> e.message ?: e.javaClass.simpleName
                }
                log("!! $msg")
                info = null
                attempt++
                if (autoReconnect && everConnected && attempt <= MAX_RECONNECT) {
                    _state.value = ConnectionState.Reconnecting(device, attempt, MAX_RECONNECT, msg)
                    delay(minOf(2_000L * (1 shl (attempt - 1)), 15_000L))
                    continue
                }
                _state.value = ConnectionState.Failed(device, msg)
                return
            } finally {
                transport.close()
            }
        }
    }


    /** Reads stored (03), pending (07) and permanent (0A) DTCs. Read-only: never sends service 04. */
    suspend fun readDtcs(): DtcReport {
        val s = session ?: throw IllegalStateException("Sem conexão com o veículo")
        val i = info ?: throw IllegalStateException("Sem conexão com o veículo")
        val mil = run {
            val r = s.send("0101", 3_000)
            ObdFrameParser.parseFrom(r.lines, i.family, i.ecu).firstNotNullOfOrNull { PidDecoder.monitorStatus(it.data) }
        }
        suspend fun read(service: Int): DtcResult {
            val r = s.send("%02X".format(service), 6_000)
            val msgs = ObdFrameParser.parseFrom(r.lines, i.family, i.ecu)
            return DtcDecoder.decode(service, msgs, i.family, r.status)
        }
        val stored = read(0x03)
        val pending = read(0x07)
        val permanent = read(0x0A).let {
            if (it is DtcResult.NoResponse && it.reason.contains("NO DATA")) DtcResult.Unsupported("ECU não respondeu ao serviço 0A (pode não suportar códigos permanentes)") else it
        }
        var warning: String? = null
        if (mil != null) {
            val readCount = (stored as? DtcResult.Codes)?.codes?.size ?: 0
            if (stored is DtcResult.NoCodes && mil.second > 0) warning = "A ECU indica ${mil.second} código(s) confirmados, mas nenhum foi recebido. Leitura não confiável."
            else if (stored is DtcResult.Codes && readCount < mil.second) warning = "A ECU indica ${mil.second} código(s), mas apenas $readCount foram recebidos."
        }
        return DtcReport(wallClock(), false, i.ecu, mil?.first, mil?.second, stored, pending, permanent, warning)
    }

    val isConnected: Boolean get() = _state.value is ConnectionState.Connected

    companion object {
        const val MAX_RECONNECT = 5

    }
}
