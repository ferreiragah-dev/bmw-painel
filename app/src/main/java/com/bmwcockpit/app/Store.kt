package com.bmwcockpit.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Context.store by preferencesDataStore("native_cockpit")
inline fun <reified E:Enum<E>> enumOr(raw:String,fallback:E):E = enumValues<E>().firstOrNull{it.name==raw}?:fallback
class Prefs(private val context:Context){
    private val key=stringPreferencesKey("settings")
    private val diagnostics=stringPreferencesKey("diagnostic_log")
    suspend fun get():Options{
        val s=context.store.data.first()[key]?:return Options()
        return runCatching{
            val j=JSONObject(s)
            val strip=j.optJSONArray("strip")?.let{a->(0 until a.length()).mapNotNull{i->selectable.find{it.name==a.optString(i)}}.distinct().take(6)}.orEmpty()
            val widgets=j.optJSONArray("widgets")?.let(::decodeWidgets)?:defaultWidgets()
            val named=j.optJSONObject("saved")?:JSONObject()
            val saved=named.keys().asSequence().take(16).mapNotNull{k->named.optJSONArray(k)?.let{k to decodeWidgets(it)}}.toMap()
            Options(enumOr(j.optString("skin"),Skin.M),enumOr(j.optString("layout"),Layout.M),strip.ifEmpty{baseStrip},widgets,
                j.optBoolean("overlay"),j.optBoolean("reconnect",true),j.optString("address"),j.optBoolean("ble"),j.optBoolean("demo",true),j.optInt("accent").coerceIn(0,6),j.optString("name","Meu painel"),saved)
        }.getOrDefault(Options())
    }

    private fun decodeWidgets(a:JSONArray):List<Widget> = (0 until minOf(a.length(),12)).mapNotNull{i->
                a.optJSONObject(i)?.let{ w->Widget(w.optInt("id",i),enumOr(w.optString("sensor"),Sensor.SPEED),
                    w.optDouble("x",.5).toFloat().coerceIn(0f,1f),w.optDouble("y",.5).toFloat().coerceIn(0f,1f),
                    w.optDouble("size",.47).toFloat().coerceIn(.21f,.82f),w.optInt("color").coerceIn(0,5),enumOr(w.optString("style"),Style.DIAL)) }
            }.distinctBy{it.id}
    private fun encodeWidgets(items:List<Widget>):JSONArray{
        val widgets=JSONArray()
        for(w in items)widgets.put(JSONObject().put("id",w.id).put("sensor",w.sensor.name).put("x",w.x.toDouble()).put("y",w.y.toDouble())
            .put("size",w.size.toDouble()).put("color",w.color).put("style",w.style.name))
        return widgets
    }
    suspend fun save(o:Options){
        val saved=JSONObject()
        o.saved.forEach{(name,items)->saved.put(name,encodeWidgets(items))}
        val json=JSONObject().put("saved",saved).put("name",o.designName).put("skin",o.skin.name).put("layout",o.layout.name).put("strip",JSONArray(o.strip.map{it.name}))
            .put("widgets",encodeWidgets(o.widgets)).put("overlay",o.overlay).put("reconnect",o.reconnect).put("address",o.address)
            .put("ble",o.ble).put("demo",o.demo).put("accent",o.accent)
        context.store.edit{it[key]=json.toString()}
    }
    suspend fun history():List<DtcLog>{
        val raw=context.store.data.first()[diagnostics]?:return emptyList()
        return runCatching{val a=JSONArray(raw);(0 until a.length()).map{i->val j=a.getJSONObject(i);val c=j.getJSONArray("codes")
            DtcLog(j.getLong("t"),j.getBoolean("demo"),(0 until c.length()).map{c.getString(it)})}}.getOrDefault(emptyList())
    }
    suspend fun log(entries:List<DtcLog>){
        val a=JSONArray();entries.take(30).forEach{a.put(JSONObject().put("t",it.whenRead).put("demo",it.demo).put("codes",JSONArray(it.codes)))}
        context.store.edit{it[diagnostics]=a.toString()}
    }
}
class Sessions(ctx:Context){
    private val dir=File(ctx.filesDir,"sessions").apply{mkdirs()}
    private var writer:java.io.BufferedWriter?=null
    @Synchronized fun list()=dir.listFiles().orEmpty().filter{it.extension=="csv"}.sortedByDescending{it.name}.map{
        Session(it.name,it.nameWithoutExtension.toLongOrNull()?:0,it.useLines{lines->(lines.count()-1).coerceAtLeast(0)})}
    @Synchronized fun start(now:Long){
        stop()
        writer=File(dir,"$now.csv").bufferedWriter().also{w->
            w.write("timestamp_ms;mode;"+Sensor.values().joinToString(";"){it.name+"_"+it.unit+";"+it.name+"_source"}+"\n")
        }
    }
    @Synchronized fun append(f:Frame,demo:Boolean){writer?.apply{
        write(f.time.toString()+";"+(if(demo)"DEMO" else "OBD2")+";"+Sensor.values().joinToString(";"){s->
            val r=f[s];(r.value?.toString()?:"")+";"+r.source.name}+"\n");flush()
    }}
    @Synchronized fun stop(){writer?.close();writer=null}
    fun export(name:String,out:java.io.OutputStream){require(name.matches(Regex("[0-9]+\\.csv")));File(dir,name).inputStream().use{it.copyTo(out)}}
}
