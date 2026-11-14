package com.bmwcockpit.app.obd

import com.bmwcockpit.app.transport.ObdTransport
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

enum class ElmStatus { OK, NO_DATA, UNKNOWN_COMMAND, UNABLE_TO_CONNECT, BUS_ERROR, STOPPED, TIMEOUT }

data class ElmResponse(
    val command: String,
    val raw: String,
    /** Cleaned lines: echo, prompt, "SEARCHING..." and blank lines removed. */
    val lines: List<String>,
    val status: ElmStatus,
) {
    val ok: Boolean get() = status == ElmStatus.OK
}

class LinkLostException(message: String) : IOException(message)

/**
 * Serial command/response session with an ELM327. Only one command is in flight at a time ([mutex]), which
 * is what ELM327 firmware (and clones) require: sending while the adapter is still answering aborts the
 * previous request with "STOPPED".
 */
class ElmSession(
    private val transport: ObdTransport,
    private val trace: ((String) -> Unit)? = null,
) {
    private val mutex = Mutex()
    private val pending = StringBuilder()

    suspend fun send(command: String, timeoutMs: Long = 2_000): ElmResponse = mutex.withLock {
        // Discard bytes left over from a previous timed-out command.
        while (true) {
            val stale = transport.incoming.tryReceive().getOrNull() ?: break
            trace?.invoke("<< (descartado) ${String(stale, Charsets.US_ASCII).trim()}")
        }
        pending.setLength(0)
        trace?.invoke(">> $command")
        transport.write("$command\r".toByteArray(Charsets.US_ASCII))
        val raw = withTimeoutOrNull(timeoutMs) {
            while (pending.indexOf(">") < 0) {
                val chunk = try {
                    transport.incoming.receive()
                } catch (e: ClosedReceiveChannelException) {
                    throw LinkLostException("Conexão com o adaptador foi encerrada")
                }
                pending.append(String(chunk, Charsets.US_ASCII))
            }
            pending.substring(0, pending.indexOf(">"))
        }
        if (raw == null) {
            trace?.invoke("<< TIMEOUT ($command) parcial='${pending.toString().trim()}'")
            return@withLock ElmResponse(command, pending.toString(), cleanLines(pending.toString(), command), ElmStatus.TIMEOUT)
        }
        trace?.invoke("<< ${raw.replace("\r", " | ").trim()}")
        val lines = cleanLines(raw, command)
        ElmResponse(command, raw, lines, classify(lines))
    }

    companion object {
        private val NOISE = listOf("SEARCHING", "BUS INIT")

        fun cleanLines(raw: String, command: String): List<String> {
            val cmdNorm = command.replace(" ", "").uppercase()
            return raw.replace("\u0000", "")
                .split('\r', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != ">" }
                .filterNot { line -> line.replace(" ", "").uppercase() == cmdNorm } // echo
                .map { line ->
                    // "SEARCHING..." may be glued to the first data line on some clones.
                    var l = line
                    for (n in NOISE) if (l.uppercase().startsWith(n)) {
                        val dots = l.lastIndexOf("...")
                        l = if (dots >= 0) l.substring(dots + 3).trim() else ""
                        if (l.uppercase().startsWith("OK")) l = l.substring(2).trim()
                    }
                    l
                }
                .filter { it.isNotEmpty() }
        }

        fun classify(lines: List<String>): ElmStatus {
            val joined = lines.joinToString(" ").uppercase()
            return when {
                lines.isEmpty() -> ElmStatus.NO_DATA
                joined.contains("NO DATA") -> ElmStatus.NO_DATA
                joined.contains("UNABLE TO CONNECT") -> ElmStatus.UNABLE_TO_CONNECT
                joined.contains("STOPPED") -> ElmStatus.STOPPED
                listOf("CAN ERROR", "BUS ERROR", "BUS BUSY", "FB ERROR", "DATA ERROR", "BUFFER FULL", "LV RESET", "ERR")
                    .any { joined.contains(it) } -> ElmStatus.BUS_ERROR
                lines.all { it == "?" } -> ElmStatus.UNKNOWN_COMMAND
                else -> ElmStatus.OK
            }
        }
    }
}
