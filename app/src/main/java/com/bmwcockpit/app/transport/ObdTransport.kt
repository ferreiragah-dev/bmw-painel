package com.bmwcockpit.app.transport

import kotlinx.coroutines.channels.ReceiveChannel
import java.io.IOException

/**
 * Byte stream to an ELM327-compatible adapter. Implementations: [ClassicSppTransport] (RFCOMM/SPP),
 * [BleTransport] (GATT write + notify) and [FakeTransport] (scripted/recorded responses for tests).
 *
 * All methods are main-safe: implementations perform blocking I/O on Dispatchers.IO.
 */
interface ObdTransport {
    val description: String

    /** Opens the link. Throws [TransportException] with a user-readable message on failure. */
    suspend fun open()

    /** Writes raw bytes (an ELM command terminated by '\r'). */
    suspend fun write(data: ByteArray)

    /** Bytes received from the adapter, in order. Closed when the link drops. */
    val incoming: ReceiveChannel<ByteArray>

    /** Releases sockets / GATT / notifications. Idempotent. */
    fun close()
}

class TransportException(message: String, cause: Throwable? = null) : IOException(message, cause)

enum class LinkType(val label: String) { CLASSIC("Bluetooth Classic (SPP)"), BLE("Bluetooth LE") }

data class DeviceRef(val name: String, val address: String, val type: LinkType)
