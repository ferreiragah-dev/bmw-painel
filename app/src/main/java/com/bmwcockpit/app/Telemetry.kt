package com.bmwcockpit.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

fun clock(t:Long)=DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t))
fun stamp(t:Long)=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(t))
private val lineColors=listOf(Color(0xffff9d31),cyan,Color(0xffbd64f6),Color(0xff47ce81),Color(0xffe85d6b),white)
@Composable fun Chart(frames:List<Frame>,sensors:List<Sensor>,mod:Modifier=Modifier){
    val same=sensors.map{it.unit}.distinct().size==1
    val temperature=same && sensors.firstOrNull()?.unit=="°C"
    val low=if(temperature)20.0 else 0.0
    val high=if(temperature)maxOf(140.0,frames.maxOfOrNull{f->sensors.maxOfOrNull{f[it].value?:0.0}?:0.0}?:140.0) else if(same)sensors.maxOf{it.max} else 100.0
    val last=frames.lastOrNull()?.time?:0L
    val first=maxOf(last-300000L,frames.firstOrNull()?.time?:0L)
    Column(mod.plate().padding(10.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Txt(if(temperature)"Temperaturas (°C)" else if(same)sensors.first().title+" ("+sensors.first().unit+")" else "Histórico · % da escala",15,white,true)
            Spacer(Modifier.weight(1f));sensors.take(3).forEachIndexed{i,s->Txt("● "+s.short,10,lineColors[i]);Spacer(Modifier.width(5.dp))}}
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(top=6.dp)){
            val x0=34.dp.toPx();val w=(size.width-x0-8.dp.toPx()).coerceAtLeast(1f);val y0=8.dp.toPx();val h=(size.height-y0-18.dp.toPx()).coerceAtLeast(1f)
            val paint=android.graphics.Paint(1).apply{color=muted.toArgb();textSize=10.sp.toPx()}
            for(i in 0..4){val y=y0+h*i/4;drawLine(edge.copy(alpha=.65f),Offset(x0,y),Offset(x0+w,y),1f);drawContext.canvas.nativeCanvas.drawText((high-(high-low)*i/4).toInt().toString(),1f,y+4,paint)}
            for(i in 0..5){val x=x0+w*i/5;drawLine(edge.copy(alpha=.3f),Offset(x,y0),Offset(x,y0+h),1f)
                paint.textAlign=if(i==5)android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
                val sec=(last-first)*(5-i)/5000;val label=if(i==5)"Agora" else if(sec>=60)"−${sec/60}m" else "−${sec}s"
                drawContext.canvas.nativeCanvas.drawText(label,x,y0+h+14.dp.toPx(),paint)}
            sensors.forEachIndexed{i,s->
                val path=Path();var running=false;var prev=0L
                for(f in frames){
                    if(f.time<first)continue
                    val value=f[s].value
                    if(value==null){running=false;continue}
                    val number=if(same)value else value/s.max*100
                    val x=x0+w*((f.time-first).toDouble()/(last-first).coerceAtLeast(1)).toFloat()
                    val y=y0+h*(1-((number-low)/(high-low)).toFloat().coerceIn(0f,1f))
                    if(!running || f.time-prev>6000)path.moveTo(x,y) else path.lineTo(x,y)
                    running=true;prev=f.time
                }
                drawPath(path,lineColors[i%lineColors.size],style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
            }
        }
    }
}
@Composable fun Rail(items:List<String>,active:String,onPick:(String)->Unit){
    if(Portrait.current)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=3.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
        items.forEach{Action(it,it==active){onPick(it)}}
    }else Column(Modifier.width(155.dp).fillMaxHeight().plate().verticalScroll(rememberScrollState()).padding(5.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
        items.forEachIndexed{i,label->
            val sym=listOf(Icons.Outlined.ShowChart,Icons.Outlined.ListAlt,Icons.Outlined.Speed,Icons.Outlined.Flag,Icons.Outlined.Timer,Icons.Outlined.ErrorOutline)
            Row(Modifier.fillMaxWidth().height(41.dp).background(if(label==active)LocalPalette.current.accent.copy(alpha=.27f) else Color.Transparent).clickable{onPick(label)}.padding(horizontal=7.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(sym[i%sym.size],null,Modifier.size(18.dp),if(label==active)cyan else muted);Spacer(Modifier.width(7.dp));Txt(label,13,if(label==active)white else muted,label==active)
            }
        }
    }
}
@Composable fun WithRail(rail:@Composable ()->Unit,body:@Composable ()->Unit){
    if(Portrait.current)Column(Modifier.fillMaxSize().padding(9.dp)){rail();Spacer(Modifier.height(8.dp));Box(Modifier.weight(1f)){body()}}
    else Row(Modifier.fillMaxSize().padding(12.dp)){rail();Spacer(Modifier.width(12.dp));Box(Modifier.weight(1f)){body()}}
}
private val sections=listOf("Gráficos","Sessões","0–100 km/h","1/4 de milha","Voltas","Diagnóstico")
@Composable fun Telemetry(vm:CockpitVM,go:(String)->Unit,route:String){
    val kind by vm.metric.collectAsStateWithLifecycle()
    val active=when(route){"logs"->"Sessões";"timing"->kind;"diagnostics"->"Diagnóstico";else->"Gráficos"}
    Box(Modifier.fillMaxSize().testTag("screen_"+route)){
        WithRail({Rail(sections,active){label->
            if(label in sections.subList(2,5))vm.metric.value=label
            go(when(label){"Gráficos"->"telemetry";"Sessões"->"logs";"Diagnóstico"->"diagnostics";else->"timing"})
        }}){
            when(route){
                "logs"->SessionPage(vm)
                "timing"->TimingPage(vm,kind)
                "diagnostics"->Diagnostics(vm)
                else->Graphs(vm)
            }
        }
    }
}
@Composable private fun Graphs(vm:CockpitVM){
    val hist by vm.history.collectAsStateWithLifecycle();val f by vm.frame.collectAsStateWithLifecycle()
    var frozen by remember{mutableStateOf<List<Frame>?>(null)}
    var chosen by rememberSaveable{mutableStateOf(listOf(Sensor.OIL.name,Sensor.WATER.name,Sensor.IAT.name))}
    val sensors=chosen.map{Sensor.valueOf(it)}
    var select by remember{mutableStateOf(false)}
    val rec by vm.recording.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Action("Sensores",icon=Icons.Outlined.Tune){select=true}
            Action(if(frozen==null)"Pausar" else "Retomar",frozen!=null,Icons.Outlined.Pause){frozen=if(frozen==null)hist else null}
            Action(if(rec)"Finalizar sessão" else "Gravar sessão",rec,Icons.Outlined.FiberManualRecord){vm.record()}
        }
        Chart(frozen?:hist,sensors,Modifier.fillMaxWidth().weight(1f))
        Row(Modifier.fillMaxWidth().height(69.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(Sensor.BOOST,Sensor.OIL_P,Sensor.VOLTS).forEach{Tile(it,f,Modifier.weight(1f).fillMaxHeight())}}
    }
    if(select)AlertDialog(onDismissRequest={select=false},title={Text("Sensores do gráfico")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        (plotSensors+Sensor.IAT).forEach{s->Action(s.title,s.name in chosen,icon=sensorIcon(s),mod=Modifier.fillMaxWidth().padding(2.dp)){
            chosen=if(s.name in chosen){if(chosen.size>1)chosen-s.name else chosen}else chosen+s.name
        }}
    }},confirmButton={TextButtonLocal("Aplicar"){select=false}})
}
@Composable private fun SessionPage(vm:CockpitVM){
    val sessions by vm.files.collectAsStateWithLifecycle();val rec by vm.recording.collectAsStateWithLifecycle()
    var file by rememberSaveable{mutableStateOf("")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->if(uri!=null)vm.export(file,uri)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Txt("Sessões de telemetria",22,white,true);Action(if(rec)"Finalizar gravação" else "Iniciar gravação",rec,Icons.Outlined.FiberManualRecord){vm.record()}
        if(sessions.isEmpty())Txt("As gravações finalizadas aparecem aqui.",14,muted)
        sessions.forEach{s->Row(Modifier.fillMaxWidth().plate().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Txt(stamp(s.start),15,white,true);Txt("${s.count} amostras",12,muted)}
            Action("Exportar CSV"){file=s.file;picker.launch("BMW-"+s.file)}
        }}
    }
}
@Composable private fun Diagnostics(vm:CockpitVM){
    val logs by vm.logs.collectAsStateWithLifecycle();val o by vm.options.collectAsStateWithLifecycle();val live by vm.connected.collectAsStateWithLifecycle()
    val last=logs.firstOrNull{it.demo==o.demo}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Txt("Diagnóstico (OBD2)",22,white,true)
        Row(Modifier.fillMaxWidth().plate().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Txt(if(o.demo)"ECU simulada" else if(live)"ECU conectada" else "ECU desconectada",16,white,true);Txt("Códigos armazenados · serviço 03",12,muted)}
            Action("Ler códigos",icon=Icons.Outlined.Search){vm.diagnose()}
        }
        Txt(if(last==null)"Aguardando leitura" else if(last.codes.isEmpty())"Nenhum código armazenado na última leitura" else "Códigos encontrados (${last.codes.size})",16,LocalPalette.current.digits,true)
        last?.codes?.forEach{code->
            Row(Modifier.fillMaxWidth().plate().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Outlined.WarningAmber,null,Modifier.size(20.dp),Color(0xffffbb38))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)){Txt(code+"  ·  "+(dtcNames[code]?:"Descrição indisponível"),14,white,true);Txt(if(last.demo)"Exemplo do simulador" else "Lido da ECU",11,muted)}
                Txt(clock(last.whenRead),12,muted)
            }
        }
        Divider(color=edge);Txt("Histórico de leituras",16,white,true)
        logs.filter{it.demo==o.demo}.forEach{item->Txt(stamp(item.whenRead)+"   ·   "+(if(item.demo)"Simulação" else "ECU")+"   ·   "+(item.codes.joinToString().ifBlank{"0 códigos"}),13,muted)}
    }
}
@Composable private fun TimingPage(vm:CockpitVM,kind:String){
    val time by vm.timing.collectAsStateWithLifecycle();val opt by vm.options.collectAsStateWithLifecycle()
    val seconds=if(time.kind==kind || time.results.firstOrNull()?.first==kind)time.seconds else null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Txt("Medições de performance",20,white,true)
        val reading:@Composable (Modifier)->Unit={m->
            Column(m.plate().padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                Txt(kind,16,muted,true,true);Txt(seconds?.let{"%.2f s".format(it)}?:"— s",45,white,true,true);Txt(time.status,13,muted)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    Action(if(kind=="Voltas" && time.kind==kind)"Marcar volta" else "Iniciar",true){vm.arm(kind)}
                    Action("Reiniciar"){vm.cancelTiming()}
                }
                if(opt.demo)TextButtonLocal("Partir do zero"){vm.demoRest()}
            }
        }
        val recent:@Composable (Modifier)->Unit={m->Column(m.plate().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Txt("Últimas medições",16,white,true)
            val results=time.results.filter{it.first==kind}
            if(results.isEmpty())Txt("Aguardando medição",13,muted)
            results.forEach{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Txt("%.2f s".format(it.second),18,white,true,true);Txt(kind,12,muted)};Divider(color=edge)}
        }}
        if(Portrait.current){reading(Modifier.fillMaxWidth());recent(Modifier.fillMaxWidth())}
        else Row(Modifier.fillMaxWidth().heightIn(min=215.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)){
            reading(Modifier.weight(1.08f));recent(Modifier.weight(1f))
        }
    }
}
