package com.bmwcockpit.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*

@Suppress("DEPRECATION")
class MainActivity:ComponentActivity(){
    val vm:CockpitVM by viewModels()
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        setContent{Cockpit(vm)}
    }
    override fun onStart(){super.onStart();vm.foreground(true)}
    override fun onStop(){vm.foreground(false);super.onStop()}
}
val Portrait=staticCompositionLocalOf{false}
@Composable fun Cockpit(vm:CockpitVM){
    val opt by vm.options.collectAsStateWithLifecycle()
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route?:"dash"
    val go:(String)->Unit={if(it!=route)nav.navigate(it){launchSingleTop=true;popUpTo("dash"){inclusive=false}}}
    val notice by vm.notice.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val pal=palette(opt.skin,opt.accent)
    BoxWithConstraints(Modifier.fillMaxSize().background(ink)){
        val portrait=maxHeight>maxWidth
        val density=LocalDensity.current
        val scale=if(portrait)(maxWidth.value/410f).coerceIn(.76f,1.35f) else minOf(maxWidth.value/940f,maxHeight.value/458f).coerceIn(.50f,1.75f)
        CompositionLocalProvider(LocalDensity provides Density(density.density*scale,1f),LocalPalette provides pal,Portrait provides portrait){
            MaterialTheme(colors=darkColors(primary=pal.accent,background=ink,surface=panel,onSurface=white)){
                if(ready)Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xff0c1622),ink,Color(0xff050b13))))){
                    Header(route,opt,vm,go)
                    NavHost(nav,"dash",Modifier.fillMaxWidth().weight(1f)){
                        composable("dash"){Dashboard(vm,go)}
                        composable("layouts"){Selector(vm){go("dash")}}
                        composable("telemetry"){Telemetry(vm,go,"telemetry")}
                        composable("logs"){Telemetry(vm,go,"logs")}
                        composable("timing"){Telemetry(vm,go,"timing")}
                        composable("diagnostics"){Telemetry(vm,go,"diagnostics")}
                        composable("editor"){Editor(vm,go)}
                        composable("themes"){Themes(vm,go)}
                        composable("settings"){Settings(vm)}
                    }
                }
                if(notice.isNotBlank())AlertDialog(onDismissRequest={vm.notice.value=""},title={Text("BMW Digital Cockpit")},text={Text(notice)},confirmButton={TextButtonLocal("OK"){vm.notice.value=""}})
            }
        }
    }
}
@Composable fun TextButtonLocal(text:String,click:()->Unit){androidx.compose.material.TextButton(onClick=click){Text(text)}}
@Composable fun Header(route:String,o:Options,vm:CockpitVM,go:(String)->Unit){
    val portrait=Portrait.current
    val live by vm.connected.collectAsStateWithLifecycle()
    val tabs=listOf(Triple("Painel",Icons.Outlined.Speed,"dash"),Triple("Telemetria",Icons.Outlined.ShowChart,"telemetry"),Triple("Personalizar",Icons.Outlined.DashboardCustomize,"editor"),Triple("Configurações",Icons.Outlined.Settings,"settings"))
    val group=when(route){"layouts"->"dash";"diagnostics","timing","logs"->"telemetry";"themes"->"editor";else->route}
    val brand:@Composable ()->Unit={Row(verticalAlignment=Alignment.CenterVertically){
        Roundel(Modifier.size(32.dp));Spacer(Modifier.width(8.dp));MMark(Modifier.size(37.dp,17.dp));Txt("Digital Cockpit",16,white,true,true)
    }}
    val badge:@Composable ()->Unit={Row(Modifier.plate().clickable{go("settings")}.padding(horizontal=7.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(if(o.demo)Icons.Outlined.Sensors else Icons.Outlined.Bluetooth,null,Modifier.size(17.dp),if(o.demo)cyan else LocalPalette.current.accent)
        Spacer(Modifier.width(4.dp));Column{Txt(if(o.demo)"MODO DEMONSTRAÇÃO" else "Vgate OBD2",if(o.demo)10 else 12,white,true)
            if(!o.demo)Txt(if(live)"Conectado" else "Desconectado",10,if(live)Color.Green else muted)}
    }}
    val bar:@Composable RowScope.()->Unit={
        tabs.forEach{(name,icon,dst)->val chosen=group==dst;val c=LocalPalette.current.accent
            Column(Modifier.weight(1f).fillMaxHeight().testTag("tab_"+dst).background(if(chosen)Brush.verticalGradient(listOf(c.copy(alpha=.24f),c.copy(alpha=.07f))) else Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent))).clickable{
                go(when{dst=="dash" && route=="dash"->"layouts";dst=="editor" && route=="editor"->"themes";else->dst})
            },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                Icon(icon,null,Modifier.size(18.dp),if(chosen)c else muted);Txt(name,11,if(chosen)white else muted,chosen)
                if(chosen)Spacer(Modifier.padding(top=2.dp).width(30.dp).height(2.dp).background(c))
            }
        }
    }
    if(portrait){
        Row(Modifier.fillMaxWidth().height(44.dp).background(ink).padding(horizontal=10.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){brand();badge()}
        Row(Modifier.fillMaxWidth().height(45.dp).background(ink),content=bar)
    }else Row(Modifier.fillMaxWidth().height(54.dp).background(ink).padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically){
        brand();Spacer(Modifier.weight(.48f));Row(Modifier.weight(4.4f).fillMaxHeight(),content=bar);Spacer(Modifier.width(12.dp));badge()
    }
    Divider(color=edge,thickness=1.dp)
}
