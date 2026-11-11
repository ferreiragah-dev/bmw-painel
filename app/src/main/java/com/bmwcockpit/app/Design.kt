package com.bmwcockpit.app

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun WidgetField(items:List<Widget>,f:Frame,mod:Modifier=Modifier,selected:Int?=null,edit:Boolean=false,
    choose:(Int)->Unit={},move:(Int,Float,Float)->Unit={_,_,_->}){
    val onMove by rememberUpdatedState(move);val onPick by rememberUpdatedState(choose)
    BoxWithConstraints(mod){
        val areaW=maxWidth;val areaH=maxHeight;val density=LocalDensity.current
        if(edit)Canvas(Modifier.fillMaxSize().background(ink)){val step=18.dp.toPx()
            var pos=0f;while(pos<size.width){drawLine(edge.copy(alpha=.42f),Offset(pos,0f),Offset(pos,size.height),1f);pos+=step};pos=0f
            while(pos<size.height){drawLine(edge.copy(alpha=.42f),Offset(0f,pos),Offset(size.width,pos),1f);pos+=step}}
        items.forEach{item->key(item.id){
            val side=minOf(areaW,areaH)*item.size
            val w=(side*1.13f).coerceAtMost(areaW);val h=if(item.style==Style.BAR)side*.47f else side
            val dx=areaW-w;val dy=areaH-h
            val active=item.id==selected
            val c=if(item.color==0)LocalPalette.current.accent else choices[item.color]
            Box(Modifier.offset(dx*item.x,dy*item.y).size(w,h).testTag("widget_"+item.id)
                .then(if(edit)Modifier.border(1.dp,if(active)c else edge).clickable{choose(item.id)}
                    .pointerInput(item.id,dx,dy){detectDragGestures(onDragStart={onPick(item.id)}){evt,drag->
                        evt.consume();onMove(item.id,drag.x/with(density){dx.toPx()}.coerceAtLeast(1f),drag.y/with(density){dy.toPx()}.coerceAtLeast(1f))
                    }} else Modifier),contentAlignment=Alignment.Center){
                when(item.style){
                    Style.DIAL->Gauge(item.sensor,f,Modifier.fillMaxSize(),tint=c)
                    Style.DIGITAL->Column(horizontalAlignment=Alignment.CenterHorizontally){Txt(item.sensor.short,12,muted);Txt(item.sensor.format(f[item.sensor].value),28,c,true,true);Txt(item.sensor.unit,12)}
                    Style.BAR->Column(Modifier.fillMaxWidth().padding(4.dp)){Txt(item.sensor.short+"    "+item.sensor.format(f[item.sensor].value)+" "+item.sensor.unit,12,c,true)
                        LinearProgressIndicator(((f[item.sensor].value?:0.0)/item.sensor.max).toFloat().coerceIn(0f,1f),Modifier.fillMaxWidth(),color=c,backgroundColor=edge)}
                }
                if(edit && active)listOf(Alignment.TopStart,Alignment.TopEnd,Alignment.BottomStart,Alignment.BottomEnd).forEach{Spacer(Modifier.align(it).size(5.dp).background(c,CircleShape))}
            }
        }}
    }
}
@Composable fun Editor(vm:CockpitVM,go:(String)->Unit){
    val o by vm.options.collectAsStateWithLifecycle();val frame by vm.frame.collectAsStateWithLifecycle()
    var items by remember{mutableStateOf(o.widgets)};var selected by remember{mutableStateOf(items.firstOrNull()?.id)}
    var sensorMenu by remember{mutableStateOf(false)};val sel=items.find{it.id==selected}
    fun mutate(f:(Widget)->Widget){items=items.map{if(it.id==selected)f(it) else it}}
    fun add(s:Sensor){
        if(items.size>=12){vm.notice.value="Limite de 12 instrumentos";return}
        val id=(items.maxOfOrNull{it.id}?:0)+1
        items+=Widget(id,s,if(id%2==0).23f else .66f,.48f,.49f);selected=id
    }
    val library:@Composable ()->Unit={
        Column(Modifier.fillMaxSize().plate().verticalScroll(rememberScrollState()).padding(6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Txt("Adicionar sensor",14,white,true);selectable.forEach{s->
                Row(Modifier.fillMaxWidth().height(32.dp).testTag("add_"+s.name).clickable{add(s)},verticalAlignment=Alignment.CenterVertically){
                    Icon(sensorIcon(s),null,Modifier.size(15.dp),muted);Spacer(Modifier.width(5.dp));Txt(s.short,12)
                }
            }
        }
    }
    val props:@Composable ()->Unit={
        Column(Modifier.fillMaxSize().plate().verticalScroll(rememberScrollState()).padding(8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Txt("Propriedades",14,white,true)
            if(sel!=null){
                Box{Action(sel.sensor.short,mod=Modifier.fillMaxWidth()){sensorMenu=true}
                    DropdownMenu(sensorMenu,{sensorMenu=false}){selectable.forEach{s->DropdownMenuItem({mutate{it.copy(sensor=s)};sensorMenu=false}){Text(s.title)}}}}
                Txt("Tamanho",12,muted);Slider(sel.size,{v->mutate{it.copy(size=v)}},Modifier.height(26.dp),valueRange=.21f.. .82f)
                Txt("Cor do display",12,muted)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){choices.take(6).forEachIndexed{i,c->
                    Box(Modifier.size(17.dp).background(c,CircleShape).border(1.dp,if(sel.color==i)white else Color.Transparent,CircleShape).clickable{mutate{it.copy(color=i)}})}}
                Txt("Estilo",12,muted)
                listOf(Style.DIAL to "Circular",Style.DIGITAL to "Digital",Style.BAR to "Barra").forEach{(style,label)->Action(label,sel.style==style,mod=Modifier.fillMaxWidth()){mutate{it.copy(style=style)}}}
                Action("Remover",icon=Icons.Outlined.Delete){items=items.filter{it.id!=selected};selected=items.firstOrNull()?.id}
            }else Txt("Adicione um sensor",12,muted)
        }
    }
    val field:@Composable (Modifier)->Unit={m->WidgetField(items,frame,m,selected,true,{selected=it}){id,x,y->items=items.map{if(it.id==id)it.copy(x=(it.x+x).coerceIn(0f,1f),y=(it.y+y).coerceIn(0f,1f))else it}}}
    Column(Modifier.fillMaxSize().testTag("screen_editor").padding(9.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Txt("Editor de painel",20,white,true);Action("Temas e skins",icon=Icons.Outlined.Palette){go("themes")}}
        if(Portrait.current){
            field(Modifier.fillMaxWidth().weight(1.15f).border(1.dp,edge))
            Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp)){Box(Modifier.weight(1f)){library()};Box(Modifier.weight(1f)){props()}}
        }else Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Box(Modifier.width(150.dp)){library()};field(Modifier.weight(1f).fillMaxHeight().border(1.dp,edge));Box(Modifier.width(184.dp)){props()}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp,Alignment.CenterHorizontally)){
            Action("Restaurar"){items=defaultWidgets();selected=1}
            Action("Cancelar"){go("dash")}
            Action("Salvar",true,Icons.Outlined.Save,Modifier.testTag("save_layout")){vm.options{it.copy(widgets=items,overlay=items.isNotEmpty())};go("dash")}
        }
    }
}
@Composable fun Themes(vm:CockpitVM,go:(String)->Unit){
    val o by vm.options.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().testTag("screen_themes").verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Txt("Temas e skins",22,white,true);Action("Editor"){go("editor")}}
        Skin.values().toList().chunked(if(Portrait.current)2 else 5).forEach{row->Row(Modifier.fillMaxWidth().height(if(Portrait.current)104.dp else 154.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            row.forEach{s->val p=palette(s)
                Column(Modifier.weight(1f).fillMaxHeight().plate(9).border(1.dp,if(s==o.skin)p.accent else edge,RoundedCornerShape(9.dp)).testTag("skin_"+s.name).clickable{vm.options{it.copy(skin=s,accent=0)}}.padding(5.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Preview(if(s==Skin.RED || s==Skin.AMBER)Layout.RACE else Layout.M,Modifier.fillMaxWidth().weight(1f),s);Txt(s.title,14,if(s==o.skin)p.accent else white,true)
                }
            }
            repeat((if(Portrait.current)2 else 5)-row.size){Spacer(Modifier.weight(1f))}
        }}
        Divider(color=edge);Txt("Cor de destaque",15,white,true)
        Row(horizontalArrangement=Arrangement.spacedBy(11.dp)){choices.forEachIndexed{i,c->Box(Modifier.size(28.dp).background(if(i==0)palette(o.skin).accent else c,CircleShape)
            .border(2.dp,if(o.accent==i)white else Color.Transparent,CircleShape).clickable{vm.options{it.copy(accent=i)}})}}
    }
}
