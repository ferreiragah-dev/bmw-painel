package com.bmwcockpit.app

import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlin.math.*

val white=Color(0xfff0f5fc)
val muted=Color(0xff91a0b3)
val ink=Color(0xff03060a)
val panel=Color(0xff0b121b)
val red=Color(0xfffa283c)
val blue=Color(0xff168dff)
val cyan=Color(0xff48c6ff)
val edge=Color(0xff304052)
val choices=listOf(blue,red,Color(0xffab56ff),Color(0xff35d58f),Color(0xffffb826),Color(0xfff17229),white)
data class Palette(val accent:Color,val needle:Color,val rim:Color,val digits:Color)
fun palette(s:Skin,a:Int=0):Palette {
    val p=when(s){
        Skin.M->Palette(blue,red,Color(0xff8aaccb),white)
        Skin.RED->Palette(red,Color(0xffff753e),Color(0xffab6068),Color(0xffffe6e3))
        Skin.CARBON->Palette(Color(0xff9cafbf),white,Color(0xff687887),Color(0xffd0dce7))
        Skin.ICE->Palette(cyan,Color(0xff80dfff),Color(0xff739bbf),Color(0xffdbf6ff))
        Skin.AMBER->Palette(Color(0xffffb623),Color(0xffff8d2e),Color(0xffb09562),Color(0xffffe9b7))
    }
    return if(a==0)p else p.copy(accent=choices[a],needle=choices[a])
}
val LocalPalette=staticCompositionLocalOf{palette(Skin.M)}
@Composable fun Txt(s:String,size:Int=14,color:Color=white,bold:Boolean=false,italic:Boolean=false,mod:Modifier=Modifier){
    Text(s,modifier=mod,color=color,fontSize=size.sp,lineHeight=(size+3).sp,fontFamily=FontFamily.SansSerif,
        fontWeight=if(bold)FontWeight.Bold else FontWeight.Medium,fontStyle=if(italic)FontStyle.Italic else FontStyle.Normal,
        maxLines=1,overflow=TextOverflow.Ellipsis)
}
fun Modifier.plate(radius:Int=6)=this.clip(RoundedCornerShape(radius.dp)).background(Brush.verticalGradient(listOf(Color(0xff14202c),Color(0xff060b11)))).border(1.dp,edge,RoundedCornerShape(radius.dp))
@Composable fun Action(s:String,selected:Boolean=false,icon:ImageVector?=null,mod:Modifier=Modifier,onClick:()->Unit){
    val c=LocalPalette.current.accent
    Row(mod.clip(RoundedCornerShape(6.dp)).background(if(selected)c.copy(alpha=.27f) else panel).border(1.dp,if(selected)c else edge,RoundedCornerShape(6.dp)).clickable(onClick=onClick).padding(horizontal=10.dp,vertical=7.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
        if(icon!=null){Icon(icon,null,Modifier.size(17.dp),if(selected)c else white);Spacer(Modifier.width(5.dp))}
        Txt(s,13,if(selected)white else muted,true)
    }
}
private val font=Typeface.create("sans-serif-condensed",Typeface.BOLD_ITALIC)
private val normal=Typeface.create("sans-serif-condensed",Typeface.NORMAL)
private fun DrawScope.letter(s:String,x:Float,y:Float,h:Float,c:Color=white,strong:Boolean=true){
    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=c.toArgb();typeface=if(strong)font else normal;textSize=h;textAlign=android.graphics.Paint.Align.CENTER}
    drawContext.canvas.nativeCanvas.drawText(s,x,y,paint)
}
private fun DrawScope.virtual(w:Float,h:Float,block:DrawScope.()->Unit){
    val k=min(size.width/w,size.height/h)
    if(k>0f)withTransform({translate((size.width-w*k)/2,(size.height-h*k)/2);scale(k,k,Offset.Zero)}){block()}
}
private fun pt(cx:Float,cy:Float,r:Float,angle:Float):Offset{
    val a=Math.toRadians(angle.toDouble());return Offset(cx+r*cos(a).toFloat(),cy+r*sin(a).toFloat())
}
private fun DrawScope.arc(cx:Float,cy:Float,r:Float,begin:Float,sweep:Float,color:Color,width:Float){
    drawArc(color,begin,sweep,false,Offset(cx-r,cy-r),Size(2*r,2*r),style=Stroke(width))
}
private fun poly(vararg coords:Float):Path=Path().apply{moveTo(coords[0],coords[1]);for(i in 2 until coords.size step 2)lineTo(coords[i],coords[i+1]);close()}
private fun DrawScope.stripes(x:Float,y:Float,k:Float){
    listOf(cyan,Color(0xff224abb),red).forEachIndexed{i,c->val l=x+i*5*k;drawPath(poly(l,y+9*k,l+5*k,y,l+10*k,y,l+5*k,y+9*k),c)}
    letter("M",x+24*k,y+9*k,14*k)
}
@Composable fun MMark(mod:Modifier=Modifier){Canvas(mod){virtual(36f,15f){stripes(2f,2f,1.05f)}}}
@Composable fun Roundel(mod:Modifier=Modifier){Canvas(mod){virtual(32f,32f){
    val o=Offset(16f,16f);drawCircle(Color(0xffb6c7d5),15.5f,o);drawCircle(ink,14.3f,o)
    for(i in 0..3)drawArc(if(i%2==0)white else blue,i*90f,90f,true,Offset(7f,7f),Size(18f,18f))
    letter("B M W",16f,6.7f,5.1f);drawCircle(Color(0xffb8cadb),9f,o,style=Stroke(.6f))
}}}
@Composable fun Gauge(s:Sensor,f:Frame,mod:Modifier=Modifier,tint:Color?=null,race:Boolean=false){
    val p=LocalPalette.current
    val v=f[s].value
    val ratio by animateFloatAsState(((v?:0.0)/s.max).toFloat().coerceIn(0f,1f),tween(130,easing=LinearEasing),label="ponteiro")
    val mark=tint?:p.needle
    Canvas(mod.semantics{contentDescription="${s.title}: ${s.format(v)} ${s.unit} · ${f[s].source.title}"}){
        virtual(280f,241f){
            val cx=140f;val cy=136f;val o=Offset(cx,cy);val r=127f;val start=136f;val sweep=268f
            drawCircle(Brush.radialGradient(listOf(Color(0xff172533),Color(0xff05080d),Color(0xff101720)),o,r),r,o)
            arc(cx,cy,r+5,start,sweep,p.accent.copy(alpha=.12f),4f)
            drawArc(Brush.linearGradient(listOf(Color(0xff293442),white,p.rim,Color(0xff424c59),Color(0xffdce5ee)),Offset(45f,20f),Offset(240f,210f)),
                start,sweep,false,Offset(cx-r,cy-r),Size(r*2,r*2),style=Stroke(3.5f))
            arc(cx,cy,r-3,start,sweep,Color(0xff222a34),2.3f)
            arc(cx,cy,r-9,start,sweep,Color(0xff58626c),.8f)
            arc(cx,cy,r-12,start+if(s==Sensor.RPM)211f else 239f,if(s==Sensor.RPM)57f else 29f,mark.copy(alpha=.8f),7f)
            val major=when(s){Sensor.RPM->8;Sensor.SPEED->10;else->5}
            for(i in 0..major*5){
                val big=i%5==0;val a=start+i*sweep/(major*5)
                drawLine(if(big)white else muted,pt(cx,cy,r-6,a),pt(cx,cy,r-(if(big)17 else 11),a),if(big)2.2f else .85f)
                if(big){val pos=pt(cx,cy,r-32,a);val num=if(s==Sensor.RPM)(i/5).toString() else if(s.max<10)"%.1f".format(java.util.Locale.US,s.max*i/(major*5)) else (s.max*i/(major*5)).roundToInt().toString()
                    letter(num,pos.x,pos.y+5,if(s==Sensor.RPM)18f else 14f)}
            }
            arc(cx,cy,r-20,start,sweep*ratio,p.accent.copy(alpha=.34f),1.35f)
            val a=start+sweep*ratio;val tail=pt(cx,cy,51f,a);val tip=pt(cx,cy,r-4,a)
            drawLine(mark.copy(alpha=.22f),tail,tip,8f);drawLine(mark,tail,tip,3.7f);drawLine(white.copy(alpha=.66f),pt(cx,cy,r-17,a),tip,1.4f)
            if(s==Sensor.SPEED)stripes(cx-15,cy-37,1.1f)
            letter(s.format(v),cx,cy+18,if(s==Sensor.RPM)43f else 49f,p.digits)
            letter(s.unit,cx,cy+36,13f,muted)
            if(race){letter(f.gear?.let{"D"+it}?:"—",cx,cy+76,29f);letter(if(f.gear!=null)"SPORT+  ·  DEMO" else "MARCHA",cx,cy+91,10f,mark)}
            else if(s==Sensor.RPM){stripes(cx-21,cy+58,.7f);letter("Performance",cx+15,cy+65,9f)}
        }
    }
}
/** Original front elevation drawn from bezier contours, grille slats and headlight elements. */
@Composable fun F30(mod:Modifier=Modifier){
    Canvas(mod){virtual(220f,116f){
        val shell=Path().apply{moveTo(14f,61f);quadraticBezierTo(20f,49f,36f,47f);lineTo(52f,23f);quadraticBezierTo(57f,12f,75f,11f);quadraticBezierTo(111f,6f,147f,11f);quadraticBezierTo(161f,12f,167f,23f);lineTo(183f,47f);quadraticBezierTo(201f,49f,206f,62f);lineTo(207f,86f);quadraticBezierTo(205f,99f,186f,100f);lineTo(34f,100f);quadraticBezierTo(13f,98f,13f,86f);close()}
        drawOval(Color(0xff061629),Offset(2f,91f),Size(216f,15f))
        drawRoundRect(Color(0xff151c24),Offset(16f,73f),Size(18f,34f),CornerRadius(5f));drawRoundRect(Color(0xff151c24),Offset(187f,73f),Size(18f,34f),CornerRadius(5f))
        drawPath(shell,Brush.verticalGradient(listOf(Color(0xff8494a5),Color(0xff1d2b3a),Color(0xff435469),Color(0xff101922),Color(0xff43546b)),8f,103f))
        drawPath(shell,Color(0xff8498ae),style=Stroke(1f))
        drawPath(poly(42f,46f,59f,20f,75f,15f,146f,15f,160f,20f,178f,46f),Brush.linearGradient(listOf(Color(0xff263b4c),Color(0xff060b13),Color(0xff182734)),Offset(50f,12f),Offset(167f,45f)))
        drawPath(poly(41f,48f,75f,46f,97f,58f,31f,53f),Color(0xff52657b));drawPath(poly(179f,48f,145f,46f,123f,58f,189f,53f),Color(0xff52657b))
        for(side in 0..1)withTransform({if(side==1){translate(220f,0f);scale(-1f,1f,Offset.Zero)}}){
            drawPath(poly(19f,51f,8f,48f,5f,42f,16f,42f,28f,47f),Color(0xff35475a))
            drawPath(poly(81f,59f,106f,58f,108f,62f,104f,74f,87f,73f,77f,65f),Color(0xff05080c))
            drawPath(poly(81f,59f,106f,58f,108f,62f,104f,74f,87f,73f,77f,65f),Color(0xffb2c4d4),style=Stroke(1.2f))
            for(i in 0..6)drawLine(Color(0xff596a77),Offset(82f+i*3.2f,61f),Offset(86f+i*2.7f,71f),.9f)
            val lamp=poly(20f,54f,56f,56f,76f,61f,66f,69f,32f,67f,22f,62f)
            drawPath(lamp,Color(0xff081a2c));drawPath(lamp,cyan,style=Stroke(.8f))
            for(x in listOf(35f,55f)){arc(x,60f,5.8f,4f,170f,cyan.copy(alpha=.24f),5f);arc(x,60f,5.8f,4f,170f,Color(0xffc4f4ff),2.1f)}
            drawPath(poly(22f,80f,59f,83f,54f,92f,26f,90f),ink);drawLine(Color(0xff8298a9),Offset(27f,88f),Offset(51f,90f),1f)
        }
        drawPath(poly(65f,88f,155f,88f,162f,96f,58f,96f),ink);drawCircle(blue,2.5f,Offset(110f,55f))
    }}
}
fun sensorIcon(s:Sensor):ImageVector=when(s){Sensor.SPEED,Sensor.RPM->Icons.Outlined.Speed;Sensor.BOOST,Sensor.LOAD->Icons.Outlined.Tune
    Sensor.OIL,Sensor.OIL_P->Icons.Outlined.InvertColors;Sensor.VOLTS->Icons.Outlined.BatteryChargingFull
    Sensor.IAT->Icons.Outlined.Air;Sensor.WATER->Icons.Outlined.Thermostat;Sensor.POWER->Icons.Outlined.Bolt;else->Icons.Outlined.ShowChart}
fun sensorColor(s:Sensor)=when(s){Sensor.BOOST->red;Sensor.OIL->Color(0xffff982e);Sensor.WATER->cyan;Sensor.OIL_P->Color(0xff48da79);Sensor.IAT->Color(0xffb360ff);else->blue}
@Composable fun Tile(s:Sensor,f:Frame,mod:Modifier=Modifier,onClick:(()->Unit)?=null){
    val r=f[s];val fraction by animateFloatAsState(((r.value?:0.0)/s.max).toFloat().coerceIn(0f,1f),label="escala")
    Column(mod.plate().then(if(onClick!=null)Modifier.clickable{onClick()} else Modifier).padding(horizontal=8.dp,vertical=5.dp),verticalArrangement=Arrangement.SpaceEvenly){
        Row(verticalAlignment=Alignment.CenterVertically){Icon(sensorIcon(s),null,Modifier.size(14.dp),muted);Spacer(Modifier.width(4.dp));Txt(s.short + if(r.source==Source.ESTIMATE)" ≈" else "",11,muted)}
        Row(verticalAlignment=Alignment.Bottom){Txt(s.format(r.value),21,LocalPalette.current.digits,true,true);Spacer(Modifier.width(3.dp));Txt(s.unit,11,muted)}
        Canvas(Modifier.fillMaxWidth().height(3.dp)){drawLine(edge,Offset.Zero,Offset(size.width,0f),size.height*2);drawLine(sensorColor(s),Offset.Zero,Offset(size.width*fraction,0f),size.height*2)}
    }
}
