package com.bmwcockpit.app.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Known GATT profiles of BLE OBD2 adapters ("transparent UART" services).
 * The exact Vgate model still has to be confirmed; the iCar Pro BLE 4.0 is reported to use FFF0/FFF1/FFF2.
 */
data class BleUartProfile(val name: String, val service: UUID, val write: UUID, val notify: UUID)

object BleProfiles {
    private fun u16(x: String) = UUID.fromString("0000$x-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = u16("2902")

    val known = listOf(
        BleUartProfile("FFF0 (Vgate iCar Pro BLE / clones ELM327 BLE)", u16("fff0"), u16("fff2"), u16("fff1")),
        BleUartProfile("FFE0 (módulo HM-10/CC254x)", u16("ffe0"), u16("ffe1"), u16("ffe1")),
        BleUartProfile("18F0 (vLinker / Vgate recentes)", u16("18f0"), u16("2af1"), u16("2af0")),
        BleUartProfile(
            "E781 (Kiwi/OBDLink-like)",
            UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"),
            UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"),
            UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"),
        ),
    )

    private fun canWrite(c: BluetoothGattCharacteristic) =
        c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

    private fun canNotify(c: BluetoothGattCharacteristic) =
        c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

    data class Match(val profileName: String, val write: BluetoothGattCharacteristic, val notify: BluetoothGattCharacteristic, val heuristic: Boolean)

    /** Picks a known profile, or falls back to a single vendor service exposing write + notify characteristics. */
    fun match(services: List<BluetoothGattService>): Match? {
        for (p in known) {
            val s = services.firstOrNull { it.uuid == p.service } ?: continue
            val w = s.getCharacteristic(p.write)
            val n = s.getCharacteristic(p.notify)
            if (w != null && n != null && canWrite(w) && canNotify(n)) return Match(p.name, w, n, false)
        }
        val standardPrefixes = listOf("00001800", "00001801", "0000180a", "0000180f")
        for (s in services) {
            if (standardPrefixes.any { s.uuid.toString().startsWith(it) }) continue
            val w = s.characteristics.firstOrNull { canWrite(it) } ?: continue
            val n = s.characteristics.firstOrNull { canNotify(it) } ?: continue
            return Match("Heurístico: serviço ${s.uuid}", w, n, true)
        }
        return null
    }
}

/**
 * BLE transport: subscribes to the adapter's notify characteristic and writes commands in MTU-sized
 * chunks, waiting for each write callback (GATT allows only one outstanding operation).
 */
@SuppressLint("MissingPermission") // callers verify BLUETOOTH_CONNECT before creating the transport
class BleTransport(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val address: String,
    private val connectTimeoutMs: Long = 15_000,
) : ObdTransport {
    override var description = "Bluetooth LE ($address)"
        private set
    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ByteArray> get() = channel

    @Volatile private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private val writeMutex = Mutex()
    @Volatile private var pendingOp: CompletableDeferred<Int>? = null
    private var connected = CompletableDeferred<Int>()
    private var servicesDiscovered = CompletableDeferred<Int>()
    private var mtuChanged = CompletableDeferred<Int>()
    @Volatile private var payloadSize = 20

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(status)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected.complete(if (status == BluetoothGatt.GATT_SUCCESS) -1 else status)
                pendingOp?.complete(-1)
                channel.close()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { servicesDiscovered.complete(status) }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) payloadSize = (mtu - 3).coerceIn(20, 244)
            mtuChanged.complete(status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingOp?.complete(status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pendingOp?.complete(status)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION")
                c.value?.let { channel.trySend(it.copyOf()) }
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            channel.trySend(value.copyOf())
        }
    }

    override suspend fun open() {
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw TransportException("Endereço Bluetooth inválido: $address", e)
        }
        val g = withContext(Dispatchers.Main) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } ?: throw TransportException("Android recusou abrir a conexão GATT")
        gatt = g
        val st = withTimeoutOrNull(connectTimeoutMs) { connected.await() }
        if (st == null || st != BluetoothGatt.GATT_SUCCESS) {
            close()
            throw TransportException(if (st == null) "Tempo esgotado conectando via BLE" else "Falha na conexão BLE (status $st)")
        }
        // Larger MTU reduces fragmentation of ELM responses; ignore failures (default 23 still works).
        if (g.requestMtu(185)) withTimeoutOrNull(3_000) { mtuChanged.await() }
        if (!g.discoverServices()) { close(); throw TransportException("Não foi possível iniciar a descoberta de serviços BLE") }
        val ds = withTimeoutOrNull(10_000) { servicesDiscovered.await() }
        if (ds != BluetoothGatt.GATT_SUCCESS) { close(); throw TransportException("Falha na descoberta de serviços BLE") }
        val match = BleProfiles.match(g.services)
        if (match == null) {
            val found = g.services.joinToString { it.uuid.toString().substring(4, 8).uppercase() }
            close()
            throw TransportException(
                "Perfil BLE do adaptador desconhecido: nenhum serviço com escrita + notificação. " +
                    "Serviços encontrados: [$found]. Informe esses UUIDs para adicionar suporte, ou use o modo Bluetooth Classic se o adaptador oferecer.",
            )
        }
        writeChar = match.write
        notifyChar = match.notify
        description = "Bluetooth LE – ${match.profileName}"
        if (!enableNotifications(g, match.notify)) {
            close()
            throw TransportException("Não foi possível ativar notificações na característica ${match.notify.uuid}")
        }
    }

    private suspend fun enableNotifications(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
        if (!g.setCharacteristicNotification(c, true)) return false
        val cccd = c.getDescriptor(BleProfiles.CCCD) ?: return true // some adapters notify without CCCD
        val value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        return writeMutex.withLock {
            val op = CompletableDeferred<Int>().also { pendingOp = it }
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, value) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
            started && withTimeoutOrNull(3_000) { op.await() } == BluetoothGatt.GATT_SUCCESS
        }
    }

    override suspend fun write(data: ByteArray) {
        val g = gatt ?: throw TransportException("BLE não conectado")
        val c = writeChar ?: throw TransportException("Característica de escrita ausente")
        val noResponse = c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0
        val type = if (noResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        writeMutex.withLock {
            var i = 0
            while (i < data.size) {
                val chunk = data.copyOfRange(i, minOf(data.size, i + payloadSize))
                val op = CompletableDeferred<Int>().also { pendingOp = it }
                val started = if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(c, chunk, type) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    c.writeType = type
                    @Suppress("DEPRECATION")
                    c.value = chunk
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(c)
                }
                if (!started) throw TransportException("Falha ao escrever no adaptador BLE")
                val res = withTimeoutOrNull(2_000) { op.await() }
                if (res == null || res != BluetoothGatt.GATT_SUCCESS) throw TransportException("Escrita BLE sem confirmação (status $res)")
                i += chunk.size
            }
        }
    }

    override fun close() {
        val g = gatt
        gatt = null
        if (g != null) {
            runCatching { notifyChar?.let { g.setCharacteristicNotification(it, false) } }
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
        pendingOp?.complete(-1)
        channel.close()
    }
}
