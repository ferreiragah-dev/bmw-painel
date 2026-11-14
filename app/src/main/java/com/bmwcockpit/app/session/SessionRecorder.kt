package com.bmwcockpit.app.session

import com.bmwcockpit.app.data.model.Reading
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.data.telemetry.Clock
import com.bmwcockpit.app.data.telemetry.HistoryStore
import com.bmwcockpit.app.data.telemetry.TelemetryHub
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class SessionMeta(
    val id: String,
    val startedEpochMs: Long,
    val endedEpochMs: Long? = null,
    val source: SourceKind,
    val samples: Long = 0,
    val interrupted: Boolean = false,
    val endReason: String? = null,
)

data class RecordingState(
    val recording: Boolean = false,
    val id: String? = null,
    val startedEpochMs: Long = 0,
    val samples: Long = 0,
    val lastMessage: String? = null,
)

object CsvFormat {
    const val HEADER = "timestamp_utc,elapsed_ms,sensor,value,unit,origin"
    private val iso = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

    fun iso(epochMs: Long): String = iso.get()!!.format(Date(epochMs))

    fun line(epochMs: Long, elapsedMs: Long, r: Reading): String? {
        val v = r.value ?: return null
        val value = String.format(Locale.US, "%.${maxOf(r.sensor.decimals, 2)}f", v)
        val unit = r.unit.replace(",", " ")
        return "${iso(epochMs)},$elapsedMs,${r.sensor.name},$value,$unit,${r.origin.name}"
    }
}

/**
 * Records telemetry sessions to CSV files in app storage, written line by line and flushed every second
 * (`<id>.csv.part`). A session is finalised by renaming to `.csv`. If the process dies mid-recording, the
 * next start recovers the `.part` file (dropping a possibly truncated last line) and marks it interrupted,
 * so a session is never left corrupted. Switching data source ends the running session.
 */
class SessionRecorder(
    private val dir: File,
    private val hub: TelemetryHub,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()
    private val _sessions = MutableStateFlow<List<SessionMeta>>(emptyList())
    val sessions: StateFlow<List<SessionMeta>> = _sessions.asStateFlow()

    private var job: Job? = null
    private var writer: BufferedWriter? = null
    private var meta: SessionMeta? = null
    private var monoStart = 0L
    private var wallStart = 0L

    init {
        scope.launch(io) {
            mutex.withLock {
                dir.mkdirs()
                recoverInterrupted()
                refreshList()
            }
        }
    }

    private fun partFile(id: String) = File(dir, "$id.csv.part")
    fun csvFile(id: String) = File(dir, "$id.csv")
    private fun metaFile(id: String) = File(dir, "$id.json")

    private fun recoverInterrupted() {
        dir.listFiles { f -> f.name.endsWith(".csv.part") }?.forEach { part ->
            val id = part.name.removeSuffix(".csv.part")
            val lines = truncateIncompleteLastLine(part)
            part.renameTo(csvFile(id))
            val m = readMeta(id) ?: SessionMeta(id, part.lastModified(), source = SourceKind.NONE)
            writeMeta(m.copy(endedEpochMs = m.endedEpochMs ?: csvFile(id).lastModified(), interrupted = true,
                samples = maxOf(0, lines - 1), endReason = "Gravação interrompida (app encerrado); dados recuperados até a última linha completa"))
        }
    }

    /** Removes a trailing partial line; returns the number of complete lines. */
    private fun truncateIncompleteLastLine(f: File): Long {
        RandomAccessFile(f, "rw").use { raf ->
            val len = raf.length()
            if (len == 0L) return 0
            var pos = len - 1
            raf.seek(pos)
            if (raf.read() != '\n'.code) {
                while (pos > 0) {
                    pos--
                    raf.seek(pos)
                    if (raf.read() == '\n'.code) break
                }
                raf.setLength(if (pos <= 0) 0 else pos + 1)
            }
        }
        return f.useLines { it.count().toLong() }
    }

    private fun readMeta(id: String): SessionMeta? =
        runCatching { json.decodeFromString(SessionMeta.serializer(), metaFile(id).readText()) }.getOrNull()

    private fun writeMeta(m: SessionMeta) {
        val tmp = File(dir, "${m.id}.json.tmp")
        tmp.writeText(json.encodeToString(SessionMeta.serializer(), m))
        tmp.renameTo(metaFile(m.id)).also { if (!it) { metaFile(m.id).delete(); tmp.renameTo(metaFile(m.id)) } }
    }

    private fun refreshList() {
        _sessions.value = (dir.listFiles { f -> f.name.endsWith(".csv") }?.toList() ?: emptyList())
            .mapNotNull { f -> readMeta(f.name.removeSuffix(".csv")) }
            .sortedByDescending { it.startedEpochMs }
    }

    fun start() {
        scope.launch(io) {
            mutex.withLock {
                if (writer != null) return@withLock
                val source = hub.activeSource
                if (source == SourceKind.NONE) {
                    _state.value = _state.value.copy(lastMessage = "Sem fonte de dados ativa para gravar")
                    return@withLock
                }
                wallStart = wallClock()
                monoStart = clock.nowMs()
                val id = "sessao_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(wallStart))
                val m = SessionMeta(id, wallStart, source = source)
                meta = m
                writeMeta(m)
                val w = partFile(id).bufferedWriter()
                w.write(CsvFormat.HEADER + "\n")
                writer = w
                _state.value = RecordingState(true, id, wallStart, 0, null)
                job = scope.launch(io) { collectLoop(source) }
            }
        }
    }

    private suspend fun collectLoop(source: SourceKind) {
        var lastFlush = clock.nowMs()
        hub.samples.collect { batch ->
            mutex.withLock {
                val w = writer ?: return@withLock
                if (batch.source != source) {
                    finishLocked("Fonte de dados alterada", interrupted = false)
                    return@withLock
                }
                var n = 0
                for (r in batch.readings) {
                    val line = CsvFormat.line(wallStart + (r.timestampMs - monoStart), r.timestampMs - monoStart, r) ?: continue
                    w.write(line); w.write("\n")
                    n++
                }
                val now = clock.nowMs()
                if (now - lastFlush >= 1_000) { w.flush(); lastFlush = now }
                _state.value = _state.value.copy(samples = _state.value.samples + n)
            }
        }
    }

    /** Called when the hub switches source (from the app container). */
    fun onSourceChanged() {
        scope.launch(io) { mutex.withLock { if (writer != null) finishLocked("Fonte de dados alterada", false) } }
    }

    fun stop(reason: String = "Encerrada pelo usuário") {
        scope.launch(io) { mutex.withLock { finishLocked(reason, false) } }
    }

    private fun finishLocked(reason: String, interrupted: Boolean) {
        val w = writer ?: return
        val m = meta ?: return
        writer = null
        job?.cancel()
        job = null
        runCatching { w.flush(); w.close() }
        partFile(m.id).renameTo(csvFile(m.id))
        writeMeta(m.copy(endedEpochMs = wallClock(), samples = _state.value.samples, interrupted = interrupted, endReason = reason))
        meta = null
        refreshList() // list first, so observers never see 'stopped' without the saved session
        _state.value = RecordingState(false, null, 0, 0, "Sessão salva (${_state.value.samples} amostras)")
    }

    fun delete(id: String) {
        scope.launch(io) {
            mutex.withLock {
                if (meta?.id == id) return@withLock
                csvFile(id).delete(); metaFile(id).delete()
                refreshList()
            }
        }
    }

    suspend fun exportSession(id: String, out: OutputStream) = withContext(io) {
        csvFile(id).inputStream().use { it.copyTo(out) }
        out.flush()
    }

    /** Exports the in-memory history (last minutes) of [sensors] as CSV. */
    suspend fun exportHistory(history: HistoryStore, sensors: List<SensorId>, out: OutputStream) = withContext(io) {
        val nowMono = clock.nowMs()
        val nowWall = wallClock()
        val w = out.bufferedWriter()
        w.write(CsvFormat.HEADER + "\n")
        val rows = sensors.flatMap { s -> history.range(s, 0, Long.MAX_VALUE).map { s to it } }.sortedBy { it.second.t }
        val first = rows.firstOrNull()?.second?.t ?: nowMono
        for ((s, p) in rows) {
            val r = Reading(s, p.v, s.unit, p.t, p.origin)
            CsvFormat.line(nowWall - (nowMono - p.t), p.t - first, r)?.let { w.write(it); w.write("\n") }
        }
        w.flush()
    }
}
