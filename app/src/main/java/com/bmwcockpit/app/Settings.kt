package com.bmwcockpit.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun SimControls(vm:CockpitVM){
    val running by vm.playing.collectAsStateWithLifecycle();val rate by vm.speedFactor.collectAsStateWithLifecycle()
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Action(if(running)"Pausar simulação" else "Iniciar simulação",true,if(running)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,Modifier.testTag("sim_play")){vm.playing.value=!running}
            Action("Partir do zero"){vm.demoRest()}
        }
        Txt("Ritmo da simulação   %.1f×".format(rate),13,muted)
        Slider(rate,{vm.speedFactor.value=it},valueRange=.5f..3f,steps=4)
    }
}
@Composable fun Settings(vm:CockpitVM){
    val o by vm.options.collectAsStateWithLifecycle();val connection by vm.connection.collectAsStateWithLifecycle()
    val devs by vm.devices.collectAsStateWithLifecycle();val scanning by vm.scanning.collectAsStateWithLifecycle()
    val ctx=LocalContext.current
    val perms=remember{if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)}
    val ask=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(it.values.all{v->v})vm.scan()else vm.notice.value="Permita o Bluetooth nas configurações do Android para buscar adaptadores."}
    val sections=listOf("Conexão OBD2","Simulador","Sensores","Sobre")
    var active by rememberSaveable{mutableStateOf(sections.first())}
    Box(Modifier.fillMaxSize().testTag("screen_settings")){
        WithRail({Rail(sections,active){active=it}}){
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(3.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                Txt("Configurações",22,white,true)
                when(active){
                    "Conexão OBD2"->{
                        Row(Modifier.fillMaxWidth().plate().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Bluetooth,null,Modifier.size(29.dp),LocalPalette.current.accent);Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)){Txt("Vgate (Bluetooth)",17,white,true);Text(if(o.demo)"Demonstração · adaptador offline" else connection,color=muted,fontSize=13.sp)}
                            if(!o.demo && o.address.isNotEmpty())Action("Desconectar"){vm.disconnect()}
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){Action("Demonstração",o.demo,mod=Modifier.testTag("mode_demo")){vm.mode(true)}
                            Action("Veículo",!o.demo,mod=Modifier.testTag("mode_live")){vm.mode(false)}}
                        Row(Modifier.fillMaxWidth().plate().padding(8.dp),verticalAlignment=Alignment.CenterVertically){Txt("Reconectar em caso de queda",14,mod=Modifier.weight(1f));Switch(o.reconnect,{v->vm.options{it.copy(reconnect=v)}})}
                        if(!o.demo){
                            Action(if(scanning)"Buscando…" else "Buscar adaptadores",icon=Icons.Outlined.Search){if(!scanning){if(perms.all{ctx.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED})vm.scan()else ask.launch(perms)}}
                            devs.forEach{d->Action(d.name+"  ·  "+(if(d.ble)"BLE" else "Classic")+"  ·  "+d.address){vm.connect(d)}}
                            if(!scanning && devs.isEmpty())Txt("Selecione o seu adaptador após a busca.",13,muted)
                        }
                    }
                    "Simulador"->{if(o.demo)SimControls(vm)else Action("Ativar modo demonstração"){vm.mode(true)}}
                    "Sensores"->{Txt("Barra inferior · até 6 sensores",15,muted)
                        selectable.forEach{s->Action(s.title,s in o.strip,sensorIcon(s),Modifier.fillMaxWidth()){
                            val list=if(s in o.strip){if(o.strip.size>1)o.strip-s else o.strip}else if(o.strip.size<6)o.strip+s else o.strip
                            if(list==o.strip && s !in o.strip)vm.notice.value="Remova um sensor antes de adicionar outro."
                            vm.options{it.copy(strip=list)}
                        }}}
                    else->{Txt("BMW Digital Cockpit  0.4.0",19,white,true);Txt("BMW 320i · F30 ActiveFlex",15,muted)
                        Text("Modo veículo somente leitura. O adaptador precisa oferecer SPP Classic ou um perfil UART BLE reconhecido e uma ECU compatível com OBD2 CAN.",color=muted)}
                }
            }
        }
    }
}
