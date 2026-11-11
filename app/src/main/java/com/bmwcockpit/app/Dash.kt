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
    val hist by vm.history.collectAsStateWithLifecycle();val trip by vm.tripKm.collectAsStateWithLifecycle()
    var sensor by remember{mutableStateOf<Sensor?>(null)};var sim by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().testTag("screen_dash").padding(horizontal=if(Portrait.current)9.dp else 16.dp,vertical=5.dp)){
        Box(Modifier.fillMaxWidth().height(36.dp)){
            Row(Modifier.align(Alignment.Center).clickable{go("layouts")},verticalAlignment=Alignment.CenterVertically){
                if(o.layout==Layout.M)MMark(Modifier.size(37.dp,20.dp)) else Icon(if(o.layout==Layout.RACE)Icons.Outlined.SportsScore else Icons.Outlined.Speed,null,Modifier.size(23.dp),LocalPalette.current.accent)
                Txt(if(o.overlay)o.designName else if(o.layout==Layout.M)"Performance" else o.layout.title,21,white,true,true)
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
            Txt("Trip   %.1f km".format(trip),11,muted,true,true)
            MMark(Modifier.size(31.dp,16.dp))
            Txt("Potência est.   "+Sensor.POWER.format(f[Sensor.POWER].value)+" cv",11,muted,true,true)
            Txt("Torque est.   "+Sensor.TORQUE.format(f[Sensor.TORQUE].value)+" Nm",11,muted,true,true)
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
        F30(Modifier.fillMaxWidth().aspectRatio(220f/116f));Txt("F30  320i",13,muted,true,true)
        Box(Modifier.fillMaxWidth().height(57.dp),contentAlignment=Alignment.Center){
            Canvas(Modifier.matchParentSize()){
                val w=size.width;val h=size.height
                for(i in 0..1){
                    fun x(v:Float)=if(i==0)v*w else (1-v)*w
                    val shape=Path().apply{moveTo(x(.04f),h*.72f);lineTo(x(.21f),h*.19f);lineTo(x(.36f),h*.19f)}
                    drawPath(shape,Brush.horizontalGradient(listOf(red,Color(0xff4f1720),red)),style=Stroke(2.dp.toPx()))
                    drawLine(blue.copy(alpha=.52f),Offset(x(.06f),h*.81f),Offset(x(.33f),h*.81f),1.dp.toPx())
                }
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                Txt(f.gear?.let{"D"+it}?:"—",28,white,true,true)
                Txt(if(f.gear==null)"MARCHA" else if((f[Sensor.THROTTLE].value?:0.0)>55)"SPORT+ · DEMO" else "SPORT · DEMO",10,red,true,true)
            }
        }
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
    val right=s in listOf(Sensor.POWER,Sensor.TORQUE,Sensor.VOLTS,Sensor.IAT)
    val trim=if(hot)Color(0xffffcb42) else p.needle
    Box(mod){
        Canvas(Modifier.matchParentSize()){
            val w=size.width;val h=size.height;val cut=minOf(w*.09f,h*.31f)
            val shape=Path().apply{
                if(right){moveTo(cut,1f);lineTo(w-1,1f);lineTo(w-1,h-1);lineTo(cut,h-1);lineTo(1f,h/2)}
                else {moveTo(1f,1f);lineTo(w-cut,1f);lineTo(w-1,h/2);lineTo(w-cut,h-1);lineTo(1f,h-1)}
                close()
            }
            val colors=listOf(panel,Color(0xff090e15),trim.copy(alpha=.30f))
            drawPath(shape,Brush.horizontalGradient(if(right)colors.reversed() else colors))
            drawPath(shape,edge,style=Stroke(1.dp.toPx()))
            val near=if(right)cut else w-cut;val tip=if(right)1f else w-1
            drawLine(trim.copy(alpha=.82f),Offset(near,1f),Offset(tip,h/2),1.5.dp.toPx())
            drawLine(trim.copy(alpha=.82f),Offset(tip,h/2),Offset(near,h-1),1.5.dp.toPx())
        }
        Row(Modifier.fillMaxSize().padding(horizontal=19.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(sensorIcon(s),null,Modifier.size(22.dp),if(hot)Color.Yellow else muted)
            Spacer(Modifier.width(9.dp));Column{Txt(if(hot)s.short+"  ↑" else s.short,12,if(hot)Color.Yellow else muted)
                Row(verticalAlignment=Alignment.Bottom){Txt(s.format(f[s].value),25,p.digits,true,true);Txt(" "+s.unit,12,muted)}}
        }
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
    val current=LocalPalette.current
    val colors=skin?.let{palette(it)}?:current
    val sample=remember{demoFrame(.1,1)}
    CompositionLocalProvider(LocalPalette provides colors){
        Column(mod.background(Brush.verticalGradient(listOf(Color(0xff0d1825),ink))).padding(3.dp)){
            when(layout){
                Layout.M,Layout.GT->Row(Modifier.fillMaxWidth().weight(1f),verticalAlignment=Alignment.CenterVertically){
                    Gauge(Sensor.SPEED,sample,Modifier.weight(1f).fillMaxHeight())
                    if(layout==Layout.M)F30(Modifier.weight(.46f).aspectRatio(1.9f))
                    Gauge(Sensor.RPM,sample,Modifier.weight(1f).fillMaxHeight())
                }
                Layout.RACE->Row(Modifier.fillMaxWidth().weight(1f),verticalAlignment=Alignment.CenterVertically){
                    @Composable fun Stack(m:Modifier){Column(m,verticalArrangement=Arrangement.spacedBy(3.dp)){repeat(4){Spacer(Modifier.fillMaxWidth().weight(1f).background(colors.needle.copy(alpha=.26f)))}}}
                    Stack(Modifier.weight(.45f).fillMaxHeight(.63f))
                    Gauge(Sensor.RPM,sample,Modifier.weight(1f).fillMaxHeight(),race=true)
                    Stack(Modifier.weight(.45f).fillMaxHeight(.63f))
                }
                Layout.MIN->Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){Txt("112",33,colors.digits,true,true)}
            }
            if(layout==Layout.M || layout==Layout.GT)Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){
                baseStrip.forEach{Spacer(Modifier.weight(1f).height(2.dp).background(sensorColor(it).copy(alpha=.7f)))}
            }
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
