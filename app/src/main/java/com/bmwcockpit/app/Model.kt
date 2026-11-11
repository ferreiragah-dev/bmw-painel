package com.bmwcockpit.app

import java.util.Locale
import kotlin.math.*

enum class Source(val title: String) { DEMO("Simulado"), LIVE("Lido"), ESTIMATE("Estimado"), MISSING("Indisponível") }
enum class Sensor(val title: String, val short: String, val unit: String, val max: Double, val digits: Int = 0) {
    SPEED("Velocidade","Velocidade","km/h",300.0), RPM("Rotação","RPM","RPM",8000.0),
    BOOST("Pressão de turbo","Turbo","bar",2.5,2), OIL("Temperatura do óleo","Temp. óleo","°C",150.0),
    WATER("Líquido de arrefecimento","Temp. água","°C",140.0), OIL_P("Pressão do óleo","Press. óleo","bar",8.0,1),
    IAT("Ar de admissão","IAT","°C",100.0), VOLTS("Tensão do módulo","Tensão","V",18.0,1),
    LOAD("Carga do motor","Carga","%",100.0), THROTTLE("Posição do acelerador","Acelerador","%",100.0),
    POWER("Potência estimada","Potência est.","cv",350.0), TORQUE("Torque estimado","Torque est.","Nm",500.0)
}
val selectable = Sensor.values().take(10)
val baseStrip = listOf(Sensor.BOOST,Sensor.OIL,Sensor.WATER,Sensor.OIL_P,Sensor.VOLTS,Sensor.IAT)
val plotSensors = listOf(Sensor.RPM,Sensor.BOOST,Sensor.OIL,Sensor.WATER,Sensor.SPEED,Sensor.VOLTS)
data class Reading(val value: Double?, val source: Source = Source.MISSING)
data class Frame(val time: Long = 0, val values: Map<Sensor,Reading> = emptyMap(), val gear: Int? = null) {
    operator fun get(s: Sensor) = values[s] ?: Reading(null)
}
private val br = Locale.forLanguageTag("pt-BR")
fun Sensor.format(v: Double?): String = v?.let {
    String.format(br, if (digits == 0) "%,.0f" else "%.${digits}f", it)
} ?: "—"
fun demoFrame(t: Double, stamp: Long): Frame {
    val wave=(1.0+sin(t/6.2))*.5
    val speed=(114+116*sin(t/8.0)).coerceAtLeast(0.0)
    val gear=(speed/38+1).toInt().coerceIn(1,7)
    val rpm=1800+(speed%38)*76+wave*580
    val torque=155+wave*225
    val v=mapOf(Sensor.SPEED to speed,Sensor.RPM to rpm,Sensor.BOOST to (.36+wave*1.62),
        Sensor.OIL to (110+5*sin(t/17)+2.1*sin(t/3.3)+.45*sin(t*2.7)),Sensor.WATER to (94+3*sin(t/13)+1.3*cos(t/4.1)+.25*sin(t*1.8)),
        Sensor.OIL_P to (2.7+rpm/2100),Sensor.IAT to (38+6*sin(t/15)+1.2*sin(t/2.4)),
        Sensor.VOLTS to (14.15+.14*cos(t/9)), Sensor.LOAD to (32+wave*62),
        Sensor.THROTTLE to (20+wave*75),Sensor.TORQUE to torque,Sensor.POWER to torque*rpm/7023.5)
    return Frame(stamp,v.mapValues { Reading(it.value,Source.DEMO) },gear)
}
enum class Layout(val title: String){ M("M Performance"), RACE("Race Dashboard"), GT("GT Telemetry"), MIN("Minimal") }
enum class Skin(val title:String){ M("M Performance"), RED("Race Red"), CARBON("Dark Carbon"), ICE("Blue Ice"), AMBER("Amber") }
enum class Style { DIAL, DIGITAL, BAR }
data class Widget(val id:Int,val sensor:Sensor,val x:Float=.5f,val y:Float=.5f,val size:Float=.47f,val color:Int=0,val style:Style=Style.DIAL)
fun defaultWidgets()=listOf(Widget(1,Sensor.SPEED))
data class Options(val skin:Skin=Skin.M,val layout:Layout=Layout.M,val strip:List<Sensor> = baseStrip,
    val widgets:List<Widget> = defaultWidgets(),val overlay:Boolean=false,val reconnect:Boolean=true,
    val address:String="",val ble:Boolean=false,val demo:Boolean=true,val accent:Int=0,
    val designName:String="Meu painel",val saved:Map<String,List<Widget>> = emptyMap())
data class Session(val file:String,val start:Long,val count:Int)
data class DtcLog(val whenRead:Long,val demo:Boolean,val codes:List<String>)
val dtcNames=mapOf("P0300" to "Falhas de combustão em múltiplos cilindros","P0302" to "Falha de combustão no cilindro 2",
    "P0171" to "Mistura pobre (banco 1)","P0118" to "Sinal alto de temperatura do líquido","P0299" to "Pressão de sobrealimentação insuficiente")
