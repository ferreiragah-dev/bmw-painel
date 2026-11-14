package com.bmwcockpit.app.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay

/**
 * Scripted ELM327 adapter used by tests and recorded-response replays.
 *
 * [responder] receives each command (without '\r') and returns the adapter's raw reply, which should end
 * with the '>' prompt. Returning null simulates a silent adapter (timeout). Replies may be split into
 * chunks with [chunkSize] to reproduce partial reads, as happens with BLE notifications of 20 bytes.
 */
class FakeTransport(
    private val chunkSize: Int = Int.MAX_VALUE,
    private val latencyMs: Long = 0,
    private val failOpen: String? = null,
    private val responder: (String) -> String?,
) : ObdTransport {
    override val description = "Transporte simulado (respostas gravadas)"
    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ByteArray> get() = channel
    val sent = mutableListOf<String>()
    var closed = false
        private set

    override suspend fun open() {
        failOpen?.let { throw TransportException(it) }
    }

    override suspend fun write(data: ByteArray) {
        if (closed) throw TransportException("Transporte fechado")
        val cmd = String(data, Charsets.US_ASCII).trim()
        sent += cmd
        val reply = responder(cmd) ?: return
        if (latencyMs > 0) delay(latencyMs)
        val bytes = reply.toByteArray(Charsets.US_ASCII)
        var i = 0
        while (i < bytes.size) {
            val end = minOf(bytes.size, i + chunkSize)
            channel.trySend(bytes.copyOfRange(i, end))
            i = end
        }
    }

    /** Simulates the link dropping. */
    fun drop() {
        channel.close()
        closed = true
    }

    override fun close() {
        closed = true
        channel.close()
    }

    companion object {
        /**
         * Builds a responder from a map of command → reply body (without prompt). Unknown commands reply "?".
         * Keys are matched ignoring spaces and case.
         */
        fun fromMap(map: Map<String, String>, echo: Boolean = false): (String) -> String? {
            val norm = map.mapKeys { it.key.replace(" ", "").uppercase() }
            return { cmd ->
                val key = cmd.replace(" ", "").uppercase()
                val body = norm[key] ?: "?"
                (if (echo) "$cmd\r" else "") + body + "\r\r>"
            }
        }
    }
}
