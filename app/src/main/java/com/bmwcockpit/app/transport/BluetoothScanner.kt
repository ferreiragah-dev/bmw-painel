package com.bmwcockpit.app.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class FoundDevice(
    val name: String,
    val address: String,
    val type: LinkType,
    val bonded: Boolean,
    val rssi: Int?,
    /** Name looks like an OBD adapter (Vgate, OBD, ELM, iCar, vLinker…). */
    val looksLikeObd: Boolean,
)

object BluetoothPermissions {
    fun required(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun connectGranted(ctx: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun scanGranted(ctx: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    fun adapter(ctx: Context): BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
}

/**
 * Lists bonded devices and discovers nearby Classic and BLE devices. The flow emits the cumulative list;
 * collection cancellation stops discovery and the BLE scan.
 */
@SuppressLint("MissingPermission") // flow is only collected after BluetoothPermissions.scanGranted()
class BluetoothScanner(private val context: Context) {

    private fun obdName(n: String) = listOf("OBD", "VGATE", "ICAR", "ELM", "VLINK", "V-LINK", "KONNWEI", "OBDLINK", "VEEPEAK")
        .any { n.uppercase().contains(it) }

    fun scan(): Flow<List<FoundDevice>> = callbackFlow {
        val adapter = BluetoothPermissions.adapter(context)
        if (adapter == null) {
            close(TransportException("Este aparelho não possui Bluetooth"))
            return@callbackFlow
        }
        if (!adapter.isEnabled) {
            close(TransportException("Bluetooth desligado. Ative o Bluetooth e tente novamente."))
            return@callbackFlow
        }
        val found = LinkedHashMap<String, FoundDevice>()
        fun put(d: FoundDevice) {
            val prev = found[d.address + d.type]
            found[d.address + d.type] = if (prev != null && d.name.isBlank()) d.copy(name = prev.name) else d
            trySend(found.values.sortedWith(compareByDescending<FoundDevice> { it.looksLikeObd }.thenByDescending { it.bonded }.thenBy { it.name }))
        }
        runCatching {
            adapter.bondedDevices?.forEach { d ->
                val name = d.name ?: d.address
                val type = if (d.type == BluetoothDevice.DEVICE_TYPE_LE) LinkType.BLE else LinkType.CLASSIC
                put(FoundDevice(name, d.address, type, true, null, obdName(name)))
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_FOUND) return
                val d: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }) ?: return
                if (d.type == BluetoothDevice.DEVICE_TYPE_LE) return // reported by the LE scanner
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt().takeIf { it != Short.MIN_VALUE.toInt() }
                val name = d.name ?: ""
                put(FoundDevice(name.ifBlank { d.address }, d.address, LinkType.CLASSIC, d.bondState == BluetoothDevice.BOND_BONDED, rssi, obdName(name)))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
        runCatching { adapter.startDiscovery() }

        val leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device
                val name = result.scanRecord?.deviceName ?: d.name ?: ""
                put(FoundDevice(name.ifBlank { d.address }, d.address, LinkType.BLE, d.bondState == BluetoothDevice.BOND_BONDED, result.rssi, obdName(name)))
            }
        }
        val scanner = adapter.bluetoothLeScanner
        runCatching {
            scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), leCallback)
        }

        awaitClose {
            runCatching { adapter.cancelDiscovery() }
            runCatching { scanner?.stopScan(leCallback) }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}
