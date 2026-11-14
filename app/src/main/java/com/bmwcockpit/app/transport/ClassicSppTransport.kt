package com.bmwcockpit.app.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth Classic RFCOMM / Serial Port Profile transport (Vgate iCar 2 / iCar Pro "Bluetooth 3.0" and
 * most ELM327 clones). The adapter must be paired in Android settings first (default PIN 1234 or 0000).
 */
@SuppressLint("MissingPermission") // callers verify BLUETOOTH_CONNECT before creating the transport
class ClassicSppTransport(
    private val adapter: BluetoothAdapter,
    private val address: String,
    private val connectTimeoutMs: Long = 12_000,
) : ObdTransport {
    override val description = "Bluetooth Classic SPP ($address)"
    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ByteArray> get() = channel

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var readerJob: Job? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw TransportException("Endereço Bluetooth inválido: $address", e)
        }
        runCatching { adapter.cancelDiscovery() } // discovery slows down/aborts RFCOMM connects
        val attempts: List<() -> BluetoothSocket> = listOf(
            { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            {
                // Channel 1 fallback used by many ELM327 clones whose SDP record is broken.
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            },
        )
        var lastError: Throwable? = null
        for (create in attempts) {
            val s = try { create() } catch (t: Throwable) { lastError = t; continue }
            socket = s
            try {
                // BluetoothSocket.connect() is blocking and ignores interruption: enforce the timeout by
                // closing the socket from the timeout path.
                var connectError: Throwable? = null
                withTimeout(connectTimeoutMs) {
                    val connectJob = scope.launch {
                        try { s.connect() } catch (t: Throwable) { connectError = t }
                    }
                    try { connectJob.join() } catch (c: Throwable) { runCatching { s.close() }; throw c }
                }
                connectError?.let { throw IOException(it.message, it) }
                if (!s.isConnected) throw IOException("socket não conectado")
                output = s.outputStream
                startReader(s.inputStream)
                return@withContext
            } catch (e: TimeoutCancellationException) {
                runCatching { s.close() }
                lastError = e
            } catch (e: IOException) {
                runCatching { s.close() }
                lastError = e
            }
        }
        throw TransportException(
            "Não foi possível abrir a porta serial (SPP) do adaptador. Verifique se ele está pareado, ligado e " +
                "não conectado a outro aparelho. (${lastError?.message ?: "erro desconhecido"})",
            lastError,
        )
    }

    private fun startReader(input: InputStream) {
        readerJob = scope.launch {
            val buf = ByteArray(1024)
            try {
                while (isActive) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) channel.trySend(buf.copyOf(n))
                }
            } catch (_: IOException) {
                // socket closed or link lost
            } finally {
                channel.close()
            }
        }
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val out = output ?: throw TransportException("Porta serial não aberta")
        try {
            out.write(data)
            out.flush()
        } catch (e: IOException) {
            throw TransportException("Falha ao enviar dados ao adaptador: ${e.message}", e)
        }
    }

    override fun close() {
        readerJob?.cancel()
        runCatching { output?.close() }
        runCatching { socket?.close() }
        socket = null
        output = null
        channel.close()
        scope.cancel()
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
