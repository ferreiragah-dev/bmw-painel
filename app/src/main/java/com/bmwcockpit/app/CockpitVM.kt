package com.bmwcockpit.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.IOException

data class Timing(val kind:String="",val status:String="Pronto",val seconds:Double?=null,val results:List<Pair<String,Double>> = emptyList())
class CockpitVM(app:Application):AndroidViewModel(app){
    private val prefs=Prefs(app)
    private val radio=Radio(app)
    private val sessions=Sessions(app)
    private val saves=Channel<Options>(Channel.CONFLATED)
    val options=MutableStateFlow(Options())
    val frame=MutableStateFlow(Frame())
    val tripKm=MutableStateFlow(0.0)
    val history=MutableStateFlow<List<Frame>>(emptyList())
    val ready=MutableStateFlow(false)
    val playing=MutableStateFlow(true)
    val speedFactor=MutableStateFlow(1f)
    val connection=MutableStateFlow("Desconectado")
    val connected=MutableStateFlow(false)
    val devices=MutableStateFlow<List<Device>>(emptyList())
    val scanning=MutableStateFlow(false)
    val recording=MutableStateFlow(false)
    val files=MutableStateFlow<List<Session>>(emptyList())
    val logs=MutableStateFlow<List<DtcLog>>(emptyList())
    val notice=MutableStateFlow("")
    val timing=MutableStateFlow(Timing())
    val metric=MutableStateFlow("0–100 km/h")
    private var start=0L
    private var prior:Frame?=null
    private var distance=0.0
    private var atRest=false
    private var time=0.0
    private var lastSample=0L
    private var active=false
    private var stream:Job?=null
    private var scan:Job?=null
    private var readRequested=false
    private var target:Device?=null
    private val disk=Channel<()->Unit>(Channel.UNLIMITED)
    init{
        viewModelScope.launch(Dispatchers.IO){for(task in disk)runCatching{task()}.onFailure{notice.value="Armazenamento: "+it.message;recording.value=false}}
        viewModelScope.launch{for(o in saves)runCatching{prefs.save(o)}.onFailure{notice.value="Preferências não salvas"}}
        viewModelScope.launch{
            val o=try{prefs.get()}catch(e:IOException){notice.value="Falha ao carregar preferências";Options()};options.value=o
            logs.value=try{prefs.history()}catch(e:IOException){emptyList()};disk.trySend{files.value=sessions.list()}
            if(!o.demo && o.address.isNotBlank() && o.reconnect)target=Device("Adaptador salvo",o.address,o.ble)
            ready.value=true;restart()
        }
    }
    fun options(f:(Options)->Options){val next=f(options.value);options.value=next;saves.trySend(next)}
    fun foreground(on:Boolean){
        active=on
        if(!on){scan?.cancel();stopRecording();cancelTiming()}
        restart()
    }
    private fun clearFeed(){tripKm.value=0.0;frame.value=Frame();history.value=emptyList();prior=null;lastSample=0;cancelTiming();readRequested=false}
    fun mode(demo:Boolean){
        if(options.value.demo==demo)return
        stopRecording();target=null;clearFeed();options{it.copy(demo=demo)};restart()
    }
    fun connect(d:Device){
        scan?.cancel();stopRecording();clearFeed();target=d
        options{it.copy(address=d.address,ble=d.ble,demo=false)};restart()
    }
    fun disconnect(){target=null;readRequested=false;stopRecording();clearFeed();options{it.copy(address="")};restart()}
    private fun restart(){
        stream?.cancel();stream=null;connected.value=false
        if(!options.value.demo)frame.value=Frame()
        connection.value="Desconectado"
        if(!ready.value || !active)return
        if(options.value.demo){
            if(history.value.isEmpty()){
                val now=System.currentTimeMillis()
                history.value=(120 downTo 1).map { i->demoFrame(time-i*.5,now-i*500L) }
            }
            stream=viewModelScope.launch{while(isActive){
                if(playing.value || frame.value.time==0L){
                    publish(demoFrame(time,System.currentTimeMillis()));time+=.12*speedFactor.value
                }
                delay(120)
            }};return
        }
        val d=target?:return
        stream=viewModelScope.launch{
            var backoff=1500L
            while(isActive){
                var link:Link?=null
                try{
                    connection.value="Conectando…";link=radio.open(d);val ecu=Ecu(link)
                    connection.value="Buscando ECU…";ecu.initialize()
                    while(isActive){
                        val f=ecu.poll();publish(f);connected.value=true;connection.value="Conectado";backoff=1500
                        if(readRequested){readRequested=false
                            try{addLog(ecu.dtcs(),false)}catch(e:IOException){notice.value=e.message?:"Leitura falhou"}
                        }
                        delay(100)
                    }
                }catch(e:CancellationException){if(!isActive)throw e;connection.value="Tempo de resposta excedido"}
                catch(e:Exception){connection.value=e.message?:"Conexão encerrada"}
                finally{runCatching{link?.close()}}
                connected.value=false;frame.value=Frame();prior=null;cancelTiming()
                if(!options.value.reconnect)break
                connection.value+=" · nova tentativa";delay(backoff);backoff=(backoff*2).coerceAtMost(15000)
            }
        }
    }
    fun demoRest(){time= -Math.PI*4;prior=null;atRest=false;playing.value=true}
    fun scan(){
        scan?.cancel();devices.value=emptyList()
        scan=viewModelScope.launch{
            scanning.value=true
            try{radio.discover().collect{d->devices.update{(it+d).distinctBy{dev->dev.address to dev.ble}}}}
            catch(e:CancellationException){throw e}
            catch(e:Exception){notice.value=e.message?:"Falha ao buscar adaptadores"}
            finally{scanning.value=false}
        }
    }
    private fun publish(f:Frame){
        val previous=frame.value
        val dt=f.time-previous.time
        val a=previous[Sensor.SPEED].value;val b=f[Sensor.SPEED].value
        if(dt in 1..3500 && a!=null && b!=null)tripKm.value+=(a+b)*.5*dt/3600000
        frame.value=f;updateTiming(f)
        if(f.time-lastSample>=450){lastSample=f.time;history.update{(it+f).takeLast(600)}
            if(recording.value){val demo=options.value.demo;disk.trySend{sessions.append(f,demo)}}}
    }
    fun record(){
        if(recording.value){stopRecording();return}
        if(!options.value.demo && !connected.value){notice.value="Conecte o adaptador antes de gravar";return}
        val stamp=System.currentTimeMillis();disk.trySend{sessions.start(stamp)};recording.value=true
    }
    private fun stopRecording(){if(recording.value){recording.value=false;disk.trySend{sessions.stop();files.value=sessions.list()}}}
    fun export(file:String,uri:Uri){disk.trySend{
        val out=getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?:throw IOException("Destino indisponível")
        out.use{sessions.export(file,it)};notice.value="CSV exportado"
    }}
    fun diagnose(){
        if(options.value.demo)addLog(listOf("P0302","P0171"),true)
        else if(connected.value){readRequested=true;notice.value="Consultando a ECU…"}else notice.value="Conecte o veículo para ler códigos"
    }
    private fun addLog(codes:List<String>,demo:Boolean){
        val l=(listOf(DtcLog(System.currentTimeMillis(),demo,codes))+logs.value).take(30);logs.value=l
        viewModelScope.launch{prefs.log(l)}
    }
    fun arm(kind:String){
        if(kind=="Voltas" && timing.value.kind==kind && start>0){
            val now=frame.value.time;if(now>start)finished(kind,(now-start)/1000.0);return
        }
        start=0;distance=0.0;atRest=false;prior=null
        timing.value=Timing(kind,if(kind=="Voltas")"Cronometrando volta" else "Aguardando imobilidade",null,timing.value.results)
    }
    fun cancelTiming(){start=0;atRest=false;prior=null;timing.value=Timing(results=timing.value.results)}
    private fun finished(k:String,t:Double){timing.value=Timing(status="Concluído",seconds=t,results=(listOf(k to t)+timing.value.results).take(10));start=0}
    private fun updateTiming(f:Frame){
        val k=timing.value.kind;if(k.isEmpty())return
        val speed=f[Sensor.SPEED].value?:return
        val last=prior;prior=f
        if(last!=null && f.time-last.time>3500){cancelTiming();return}
        if(k=="Voltas" && start==0L)start=f.time
        if(start==0L && speed<=1){atRest=true;timing.value=timing.value.copy(status="Aguardando arrancada")}
        if(start==0L && atRest && speed>1)start=last?.time?:f.time
        if(start==0L)return
        var seconds=(f.time-start)/1000.0
        val v0=last?.get(Sensor.SPEED)?.value?:speed
        val dt=if(last==null)0.0 else (f.time-last.time)/1000.0
        val prevDist=distance;distance+=(speed+v0)/7.2*dt
        val done=when(k){
            "0–100 km/h"->speed>=100
            "1/4 de milha"->distance>=402.336
            else->false
        }
        if(done){
            val fraction=if(k=="0–100 km/h" && speed>v0)(100-v0)/(speed-v0) else if(distance>prevDist)(402.336-prevDist)/(distance-prevDist) else 1.0
            seconds-=dt*(1-fraction.coerceIn(0.0,1.0));finished(k,seconds)
        }else timing.value=timing.value.copy(status="Em medição",seconds=seconds)
    }
    override fun onCleared(){scan?.cancel();stream?.cancel();disk.close();saves.close();sessions.stop();super.onCleared()}
}
