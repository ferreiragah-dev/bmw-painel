package com.bmwcockpit.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun Dashboard(vm:CockpitVM,go:(String)->Unit){
    val o by vm.options.collectAsStateWithLifecycle();val f by vm.frame.collectAsStateWithLifecycle()
    val hist by vm.history.collectAsStateWithLifecycle()
    var sensor by remember{mutableStateOf<Sensor?>(null)};var sim by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().testTag("screen_dash").padding(horizontal=if(Portrait.current)9.dp else 16.dp,vertical=5.dp)){
        Box(Modifier.fillMaxWidth().height(36.dp)){
            Row(Modifier.align(Alignment.Center).clickable{go("layouts")},verticalAlignment=Alignment.CenterVertically){
                if(o.layout==Layout.M)MMark(Modifier.size(37.dp,20.dp)) else Icon(if(o.layout==Layout.RACE)Icons.Outlined.SportsScore else Icons.Outlined.Speed,null,Modifier.size(23.dp),LocalPalette.current.accent)
                Txt(if(o.layout==Layout.M)"Performance" else o.layout.title,21,white,true,true)
            }
            IconButton(onClick={go("layouts")},modifier=Modifier.align(Alignment.CenterStart).testTag("choose_layout").size(35.dp)){Icon(Icons.Outlined.Dashboard,"Escolher painel",tint=muted)}
            if(o.demo)IconButton(onClick={sim=true},modifier=Modifier.align(Alignment.CenterEnd).size(35.dp)){Icon(Icons.Outlined.Tune,"Controlar simulação",tint=muted)}
        }
        Box(Modifier.fillMaxWidth().weight(1f)){
            if(o.overlay)WidgetField(o.widgets,f,Modifier.fillMaxSize())
            else when(o.layout){
                Layout.M->Performance(f)
                Layout.RACE->Race(f,o.demo)
                Layout.GT->GT(f,hist)
                Layout.MIN->Minimal(f)
            }
        }
        if(o.layout!=Layout.RACE || o.overlay){
            val rows=if(Portrait.current)o.strip.chunked(3) else listOf(o.strip)
            rows.forEach{row->Row(Modifier.fillMaxWidth().height(if(Portrait.current)68.dp else 65.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                row.forEach{s->Tile(s,f,Modifier.weight(1f).fillMaxHeight()){sensor=s}}
            };Spacer(Modifier.height(4.dp))}
        }
        Row(Modifier.fillMaxWidth().height(25.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Txt("F30  ·  320i ActiveFlex",11,muted,true,true);MMark(Modifier.size(31.dp,16.dp));Txt(if(o.demo)"SPORT+  ·  DEMO" else "OBD2  ·  CAN",11,if(o.demo)red else muted,true,true)
        }
    }
    sensor?.let{s->AlertDialog(onDismissRequest={sensor=null},title={Text(s.title)},text={Column{
        Text("${s.format(f[s].value)} ${s.unit}  ·  ${f[s].source.title}")
        Spacer(Modifier.height(14.dp));Text("Substituir nesta posição");selectable.filter{it !in o.strip}.forEach{other->TextButtonLocal(other.title){vm.options{it.copy(strip=it.strip.map{old->if(old==s)other else old})};sensor=null}}
    }},confirmButton={TextButtonLocal("Fechar"){sensor=null}})}
    if(sim)AlertDialog(onDismissRequest={sim=false},title={Text("Simulador")},text={SimControls(vm)},confirmButton={TextButtonLocal("Fechar"){sim=false}})
}
@Composable fun Performance(f:Frame){
    val car:@Composable ()->Unit={Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        F30(Modifier.fillMaxWidth().weight(1f,false));Txt("F30  320i",13,muted,true,true)
        Txt(f.gear?.let{"D"+it}?:"—",32,white,true,true);Txt(if(f.gear!=null)"SPORT+  ·  DEMO" else "MARCHA",12,red,true,true)
    }}
    if(Portrait.current)Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){
        Row(Modifier.fillMaxWidth().weight(1f)){Gauge(Sensor.SPEED,f,Modifier.weight(1f).fillMaxHeight());Gauge(Sensor.RPM,f,Modifier.weight(1f).fillMaxHeight())}
        Box(Modifier.fillMaxWidth(.53f).weight(.88f),contentAlignment=Alignment.Center){car()}
    }else Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){
        Gauge(Sensor.SPEED,f,Modifier.weight(1f).fillMaxHeight());Box(Modifier.weight(.51f).fillMaxHeight(.77f),contentAlignment=Alignment.Center){car()};Gauge(Sensor.RPM,f,Modifier.weight(1f).fillMaxHeight())
    }
}
@Composable fun RaceCard(s:Sensor,f:Frame,mod:Modifier=Modifier){
    val p=LocalPalette.current;val hot=(s==Sensor.OIL && (f[s].value?:0.0)>130)||(s==Sensor.WATER && (f[s].value?:0.0)>110)
    Row(mod.plate().padding(6.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.width(3.dp).fillMaxHeight(.8f).background(if(hot)Color.Yellow else p.needle))
        Spacer(Modifier.width(7.dp));Icon(sensorIcon(s),null,Modifier.size(22.dp),if(hot)Color.Yellow else muted)
        Spacer(Modifier.width(8.dp));Column{Txt(if(hot)s.short+"  ↑" else s.short,12,if(hot)Color.Yellow else muted);Row(verticalAlignment=Alignment.Bottom){Txt(s.format(f[s].value),24,p.digits,true,true);Txt(" "+s.unit,12,muted)}}
    }
}
@Composable fun Race(f:Frame,demo:Boolean){
    val left=listOf(Sensor.SPEED,Sensor.BOOST,Sensor.OIL,Sensor.WATER);val right=listOf(Sensor.POWER,Sensor.TORQUE,Sensor.VOLTS,Sensor.IAT)
    @Composable fun Stack(items:List<Sensor>,m:Modifier){Column(m,verticalArrangement=Arrangement.spacedBy(5.dp)){items.forEach{RaceCard(it,f,Modifier.fillMaxWidth().weight(1f))}}}
    if(Portrait.current)Column(Modifier.fillMaxSize()){Gauge(Sensor.RPM,f,Modifier.fillMaxWidth().weight(1.1f),race=true);Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(5.dp)){Stack(left,Modifier.weight(1f));Stack(right,Modifier.weight(1f))}}
    else Row(Modifier.fillMaxSize().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        Stack(left,Modifier.weight(.84f).fillMaxHeight(.94f));Gauge(Sensor.RPM,f,Modifier.weight(1.45f).fillMaxHeight(),race=true);Stack(right,Modifier.weight(.84f).fillMaxHeight(.94f))
    }
}
@Composable fun GT(f:Frame,h:List<Frame>){Column(Modifier.fillMaxSize().padding(8.dp)){
    Row(Modifier.fillMaxWidth().weight(1f)){Gauge(Sensor.SPEED,f,Modifier.weight(1f).fillMaxHeight(),tint=cyan);Gauge(Sensor.RPM,f,Modifier.weight(1f).fillMaxHeight(),tint=cyan)}
    Chart(h,listOf(Sensor.RPM,Sensor.SPEED),Modifier.fillMaxWidth().weight(.55f))
}}
@Composable fun Minimal(f:Frame){Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
    Txt(Sensor.SPEED.format(f[Sensor.SPEED].value),if(Portrait.current)84 else 98,LocalPalette.current.digits,true,true);Txt("km/h",18,muted)
    Spacer(Modifier.height(12.dp));LinearProgressIndicator(((f[Sensor.RPM].value?:0.0)/8000).toFloat().coerceIn(0f,1f),Modifier.fillMaxWidth(.65f).height(6.dp),color=LocalPalette.current.needle,backgroundColor=edge)
    Txt(Sensor.RPM.format(f[Sensor.RPM].value)+" RPM",17,muted,true)
}}
@Composable fun Preview(layout:Layout,mod:Modifier,skin:Skin?=null){
    val c=skin?.let{palette(it).accent}?:LocalPalette.current.accent
    Canvas(mod){
        val w=size.width;val h=size.height
        fun dial(x:Float,y:Float,r:Float,angle:Float){
            drawArc(muted,137f,266f,false,Offset(x-r,y-r),Size(2*r,2*r),style=Stroke(r*.065f))
            drawArc(c,329f,68f,false,Offset(x-r*.89f,y-r*.89f),Size(1.78f*r,1.78f*r),style=Stroke(r*.115f))
            for(i in 0..10){val a=(137+i*26.6)*Math.PI/180;drawLine(white,Offset(x+(kotlin.math.cos(a)*r*.83).toFloat(),y+(kotlin.math.sin(a)*r*.83).toFloat()),Offset(x+(kotlin.math.cos(a)*r*.95).toFloat(),y+(kotlin.math.sin(a)*r*.95).toFloat()),1.3f)}
            val a=angle*Math.PI/180;drawLine(red,Offset(x,y),Offset(x+(kotlin.math.cos(a)*r*.82).toFloat(),y+(kotlin.math.sin(a)*r*.82).toFloat()),2f)
        }
        val r=minOf(h*.43f,w*.22f)
        when(layout){Layout.M,Layout.GT->{dial(w*.26f,h*.53f,r,227f);dial(w*.74f,h*.53f,r,290f)
            if(layout==Layout.GT)drawLine(c,Offset(w*.17f,h*.96f),Offset(w*.84f,h*.92f),2f)}
            Layout.RACE->dial(w*.5f,h*.53f,minOf(h*.46f,w*.37f),290f)
            Layout.MIN->{val paint=android.graphics.Paint(1).apply{color=white.toArgb();textSize=h*.58f;textAlign=android.graphics.Paint.Align.CENTER;typeface=android.graphics.Typeface.DEFAULT_BOLD};drawContext.canvas.nativeCanvas.drawText("112",w/2,h*.69f,paint)}
        }
    }
}
@Composable fun Selector(vm:CockpitVM,done:()->Unit){
    val o by vm.options.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().testTag("screen_layouts").padding(16.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
        Txt("Selecione um Painel",22,white,true);Spacer(Modifier.height(20.dp))
        Layout.values().toList().chunked(if(Portrait.current)2 else 4).forEach{row->
            Row(Modifier.fillMaxWidth().height(if(Portrait.current)142.dp else 174.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                row.forEach{l->val selected=l==o.layout;Column(Modifier.weight(1f).fillMaxHeight().plate(12).border(1.dp,if(selected)LocalPalette.current.accent else edge,RoundedCornerShape(12.dp)).testTag("pick_"+l.name).clickable{
                    vm.options{it.copy(layout=l,overlay=false)};done()
                }.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){Preview(l,Modifier.fillMaxWidth().weight(1f));Txt(l.title,17,if(selected)LocalPalette.current.accent else white,true)}}
            };Spacer(Modifier.height(9.dp))
        }
    }
}
