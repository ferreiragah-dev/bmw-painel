package com.bmwcockpit.app

import java.io.IOException
import java.util.Locale
import kotlin.math.min

/** ISO 15765 with ELM headers enabled. Never splice different ECUs or incomplete frames. */
object Obd {
    fun replies(raw:String):Map<String,List<Int>> {
        val complete=linkedMapOf<String,List<Int>>()
        data class Assembly(val bytes:MutableList<Int>,val length:Int,var next:Int=1)
        val pending=mutableMapOf<String,Assembly>()
        for(line in raw.uppercase().lines()){
            val words=line.trim().split(Regex("\\s+"))
            val tokens=if(words.size>=6 && words.take(4).all { it.matches(Regex("[0-9A-F]{2}")) })
                listOf(words.take(4).joinToString(""))+words.drop(4) else words
            val id=tokens.first()
            if(!id.matches(Regex("[0-9A-F]{3}|[0-9A-F]{8}")))continue
            val bytes=tokens.drop(1).mapNotNull { if(it.matches(Regex("[0-9A-F]{2}")))it.toInt(16) else null }
            if(bytes.size!=tokens.size-1 || bytes.isEmpty())continue
            val pci=bytes[0]
            when(pci ushr 4){
                0 -> if(pci in 1 until bytes.size)complete[id]=bytes.drop(1).take(pci)
                1 -> if(bytes.size>2){val len=((pci and 15) shl 8)+bytes[1];pending[id]=Assembly(bytes.drop(2).toMutableList(),len)}
                2 -> pending[id]?.let { a ->
                    if((pci and 15)!=a.next){pending.remove(id)} else {
                        a.next=(a.next+1)%16;a.bytes.addAll(bytes.drop(1))
                        if(a.bytes.size>=a.length){complete[id]=a.bytes.take(a.length);pending.remove(id)}
                    }
                    Unit
                }
            }
        }
        return complete
    }
    fun supported(base:Int,bytes:List<Int>):Set<Int> {
        if(bytes.size<6 || bytes[0]!=0x41 || bytes[1]!=base)return emptySet()
        return (0 until 32).filter { bytes[2+it/8] and (128 shr (it%8)) != 0 }.map { base+1+it }.toSet()
    }
    fun parseDtcs(payload:List<Int>):List<String>{
        if(payload.size<2 || payload[0]!=0x43)throw IOException("Resposta de diagnóstico incompleta")
        val n=payload[1]
        if(payload.size<2+n*2)throw IOException("Resposta de diagnóstico truncada")
        return (0 until n).map { i ->
            val a=payload[2+2*i];val b=payload[3+2*i]
            "%c%X%X%02X".format(Locale.US,"PCBU"[a ushr 6],(a shr 4) and 3,a and 15,b)
        }.filterNot { it=="P0000" }
    }
    val pids=listOf(0x0C,0x0D,0x0B,0x33,0x05,0x0F,0x5C,0x42,0x04,0x11,0x62,0x63)
    fun frame(raw:Map<Int,List<Int>>,now:Long):Frame {
        fun a(k:Int)=raw[k]?.getOrNull(0)?.toDouble()
        fun word(k:Int)=raw[k]?.takeIf{it.size>=2}?.let{it[0]*256.0+it[1]}
        val out=mutableMapOf<Sensor,Reading>()
        fun put(s:Sensor,v:Double?,src:Source=Source.LIVE){ if(v!=null)out[s]=Reading(v,src) }
        val rpm=word(0x0C)?.div(4)
        put(Sensor.RPM,rpm);put(Sensor.SPEED,a(0x0D));put(Sensor.OIL,a(0x5C)?.minus(40))
        put(Sensor.WATER,a(5)?.minus(40));put(Sensor.IAT,a(0x0F)?.minus(40));put(Sensor.VOLTS,word(0x42)?.div(1000))
        put(Sensor.LOAD,a(4)?.times(100.0/255));put(Sensor.THROTTLE,a(0x11)?.times(100.0/255))
        val map=a(0x0B);val baro=a(0x33)
        if(map!=null && baro!=null)put(Sensor.BOOST,(map-baro)/100,Source.ESTIMATE)
        val pct=a(0x62);val ref=word(0x63)
        if(pct!=null && ref!=null){
            val torque=(pct-125)*ref/100
            put(Sensor.TORQUE,torque,Source.ESTIMATE);put(Sensor.POWER,rpm?.let { torque*it/7023.5 },Source.ESTIMATE)
        }
        return Frame(now,out)
    }
}
interface Link:java.io.Closeable { suspend fun exchange(command:String):String }
class Ecu(private val link:Link){
    private var ecu=""
    private var supported=emptySet<Int>()
    suspend fun initialize(){
        for(cmd in listOf("ATZ","ATE0","ATL0","ATS1","ATH1","ATSP0")){
            val reply=link.exchange(cmd)
            if(cmd!="ATZ" && !reply.contains("OK",true))throw IOException("Adaptador recusou "+cmd)
        }
        val maps=Obd.replies(link.exchange("0100")).filterValues{it.size>=6 && it.take(2)==listOf(0x41,0)}
        ecu=maps.keys.sortedWith(compareBy({it!="7E8" && it!="18DAF110"},{it})).firstOrNull()?:throw IOException("Sem resposta CAN da ECU")
        val found=Obd.supported(0,maps.getValue(ecu)).toMutableSet()
        for(base in listOf(32,64,96)){
            if(base !in found)break
            val payload=Obd.replies(link.exchange("01%02X".format(base)))[ecu].orEmpty()
            found+=Obd.supported(base,payload)
        }
        supported=found
    }
    suspend fun poll():Frame{
        val raw=mutableMapOf<Int,List<Int>>()
        for(pid in Obd.pids.filter{it in supported}){
            val payload=Obd.replies(link.exchange("01%02X".format(pid)))[ecu]?:continue
            if(payload.size>=3 && payload[0]==0x41 && payload[1]==pid)raw[pid]=payload.drop(2)
        }
        if(raw.isEmpty())throw IOException("Nenhum PID de telemetria respondeu")
        return Obd.frame(raw,System.currentTimeMillis())
    }
    suspend fun dtcs():List<String> = Obd.parseDtcs(Obd.replies(link.exchange("03"))[ecu].orEmpty())
}
