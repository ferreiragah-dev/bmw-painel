@file:Suppress("DEPRECATION","MissingPermission")
package com.bmwcockpit.app

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class Device(val name:String,val address:String,val ble:Boolean)
class Radio(private val ctx:Context){
    private val adapter get()=ctx.getSystemService(BluetoothManager::class.java)?.adapter
    fun discover()=callbackFlow<Device>{
        val a=adapter?:throw IOException("Bluetooth indisponível")
        if(!a.isEnabled)throw IOException("Ative o Bluetooth da multimídia")
        fun emitDevice(d:BluetoothDevice,low:Boolean){trySend(Device(d.name?:"Adaptador sem nome",d.address,low))}
        a.bondedDevices.forEach{if(it.type!=BluetoothDevice.DEVICE_TYPE_LE)emitDevice(it,false);if(it.type!=BluetoothDevice.DEVICE_TYPE_CLASSIC)emitDevice(it,true)}
        val rx=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){
            val d=intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?:return
            if(d.type!=BluetoothDevice.DEVICE_TYPE_LE)emitDevice(d,false)
        }}
        if(Build.VERSION.SDK_INT>=33)ctx.registerReceiver(rx,IntentFilter(BluetoothDevice.ACTION_FOUND),Context.RECEIVER_EXPORTED)
        else ctx.registerReceiver(rx,IntentFilter(BluetoothDevice.ACTION_FOUND))
        val scan=object:ScanCallback(){override fun onScanResult(type:Int,result:ScanResult){emitDevice(result.device,true)}}
        val scanner=a.bluetoothLeScanner
        try{
            scanner?.startScan(scan)
            delay(4500)
            scanner?.stopScan(scan)
            a.startDiscovery()
            delay(11000)
        }finally{runCatching{scanner?.stopScan(scan)};runCatching{a.cancelDiscovery()};runCatching{ctx.unregisterReceiver(rx)}}
        close()
    }
    suspend fun open(d:Device):Link{
        val a=adapter?:throw IOException("Bluetooth indisponível");a.cancelDiscovery()
        val dev=a.getRemoteDevice(d.address)
        return if(d.ble)BleLink.open(ctx,dev) else {
            val sock=dev.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))
            timedSocket(14000,sock){sock.connect()}
            ClassicLink(sock)
        }
    }
}
// Closing the socket releases blocking Android I/O on timeout or cancellation.
private suspend fun <T> timedSocket(ms:Long,s:BluetoothSocket,op:()->T):T = withTimeout(ms) {
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation{runCatching{s.close()}}
        Dispatchers.IO.dispatch(kotlin.coroutines.EmptyCoroutineContext,Runnable{
            try{val value=op();cont.resumeWith(Result.success(value))}
            catch(e:Exception){runCatching{s.close()};cont.resumeWith(Result.failure(e))}
        })
    }
}
private fun allowed(cmd:String)=cmd in setOf("ATZ","ATE0","ATL0","ATS1","ATH1","ATSP0","03") ||
    cmd.matches(Regex("01[0-9A-F]{2}"))
private class ClassicLink(val socket:BluetoothSocket):Link{
    override fun close()=socket.close()
    override suspend fun exchange(command:String):String{
        require(allowed(command))
        return timedSocket(if(command=="0100")14000 else 5500,socket){
            socket.outputStream.write((command+"\r").toByteArray(Charsets.US_ASCII))
            val text=StringBuilder()
            while(text.length<8192){
                val b=socket.inputStream.read()
                if(b<0)throw IOException("Bluetooth desconectado")
                if(b==62)break
                text.append(if(b==13)'\n' else b.toChar())
            }
            if(text.length>=8192)throw IOException("Resposta muito longa")
            text.toString()
        }
    }
}
private val cccd=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
private fun uuid16(s:String)=UUID.fromString("0000"+s+"-0000-1000-8000-00805f9b34fb")
private val profiles=listOf(
    Triple(uuid16("fff0"),uuid16("fff1"),uuid16("fff2")),
    Triple(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")))
private class BleLink:BluetoothGattCallback(),Link{
    private var gatt:BluetoothGatt?=null
    private lateinit var tx:BluetoothGattCharacteristic
    private var rxId:UUID?=null
    private val ready=CompletableDeferred<Unit>()
    private val bytes=Channel<ByteArray>(128)
    private val writes=Channel<Int>(1)
    private val closed=AtomicBoolean()
    companion object{
        suspend fun open(ctx:Context,d:BluetoothDevice):BleLink {
            val link=BleLink()
            try{
                link.gatt=d.connectGatt(ctx,false,link,BluetoothDevice.TRANSPORT_LE)
                withTimeout(15000){link.ready.await()};return link
            }catch(t:Throwable){link.close();throw t}
        }
    }
    private fun fail(why:String){val e=IOException(why);ready.completeExceptionally(e);bytes.close(e);writes.close(e)}
    override fun onConnectionStateChange(g:BluetoothGatt,status:Int,state:Int){
        if(status!=0||state==BluetoothProfile.STATE_DISCONNECTED)fail("BLE desconectado ("+status+")")
        else if(state==BluetoothProfile.STATE_CONNECTED && !g.discoverServices())fail("Serviços BLE indisponíveis")
    }
    override fun onServicesDiscovered(g:BluetoothGatt,status:Int){
        if(status!=0){fail("Erro nos serviços BLE");return}
        for((service,receive,send) in profiles){
            val s=g.getService(service)?:continue
            val rx=s.getCharacteristic(receive)?:continue
            val writer=s.getCharacteristic(send)?:continue
            val desc=rx.getDescriptor(cccd)?:continue
            val notify=rx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY!=0
            if(!notify && rx.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE==0)continue
            if(writer.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)==0)continue
            tx=writer;rxId=rx.uuid
            if(!g.setCharacteristicNotification(rx,true)){fail("Falha ao assinar BLE");return}
            desc.value=if(notify)BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            if(!g.writeDescriptor(desc))fail("Falha ao assinar notificações")
            return
        }
        fail("Perfil UART BLE desconhecido para este adaptador")
    }
    override fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,status:Int){if(status==0)ready.complete(Unit) else fail("Notificações recusadas")}
    override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic){if(c.uuid==rxId && !bytes.trySend(c.value.clone()).isSuccess)fail("Fluxo BLE interrompido")}
    override fun onCharacteristicWrite(g:BluetoothGatt,c:BluetoothGattCharacteristic,status:Int){writes.trySend(status)}
    override suspend fun exchange(command:String):String{
        require(allowed(command))
        return withTimeout(if(command=="0100")14000 else 5500){
            tx.writeType=if(tx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE!=0)BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            tx.value=(command+"\r").toByteArray(Charsets.US_ASCII)
            if(gatt?.writeCharacteristic(tx)!=true || writes.receive()!=0)throw IOException("Envio BLE recusado")
            val out=StringBuilder()
            while(true){
                val chunk=bytes.receive().toString(Charsets.US_ASCII).replace('\r','\n')
                out.append(chunk.substringBefore('>'))
                if(out.length>8192)throw IOException("Resposta BLE muito longa")
                if('>' in chunk)break
            }
            out.toString()
        }
    }
    override fun close(){if(closed.compareAndSet(false,true)){gatt?.disconnect();gatt?.close();bytes.close();writes.close()}}
}
