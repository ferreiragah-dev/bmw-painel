/* Coordinates use pixels in the original nine landscape panels. Keep the bitmap
   and its controls in the same coordinate system on every head unit. */
(function () {
  'use strict';
  var pages = {
    performance:[493,271,'M Performance'], race:[497,271,'Race Dashboard'],
    telemetry:[497,271,'Gráficos'], layouts:[493,213,'Selecione um Painel'],
    editor:[496,213,'Editor de Painel'], diagnostics:[494,213,'Diagnóstico'],
    timing:[493,201,'Medições de Performance'], themes:[497,201,'Temas e Skins'],
    settings:[491,201,'Configurações']
  };
  var stage = document.getElementById('stage'), surface = document.getElementById('surface');
  var art = document.getElementById('art'), dialog = document.getElementById('dialog');
  var themeNames = ['M Performance','Race Red','Dark Carbon','Blue Ice','Amber'];
  var themeEffects = ['','hue-rotate(125deg)','saturate(.15)','hue-rotate(-20deg)','hue-rotate(185deg)'];
  var accents = ['#087cf6','#ed1522','#863adc','#159e36','#ffc52c','#ff812e','#fff'];
  var sensorNames = ['Velocidade','RPM','Turbo','Temp. Óleo','Temp. Água','Press. Óleo'];
  var values = ['112','4.200','1,24','112','95','4,8'], units = ['km/h','RPM','bar','°C','°C','bar'];
  var layouts = ['M Performance','Race Dashboard','GT Telemetry','Minimal'];
  var defaults = {layout:0,theme:0,accent:0,mode:0,connected:true,auto:true,reconnect:true,
    economy:false,faults:[true,true],measurements:[],quarter:[],laps:[],sensor:0,x:242,y:127,size:1,color:0,style:0,custom:false};
  function fresh() { return JSON.parse(JSON.stringify(defaults)); }
  function bounded(value,min,max,fallback) {
    return typeof value === 'number' && isFinite(value) && value >= min && value <= max ? value : fallback;
  }
  function preferences(input) {
    var out=fresh(), ranges={layout:3,theme:4,accent:6,mode:2,sensor:5,color:4,style:2};
    if (!input || typeof input !== 'object') return out;
    Object.keys(ranges).forEach(function(k){out[k]=Math.floor(bounded(input[k],0,ranges[k],0));});
    ['connected','auto','reconnect','economy','custom'].forEach(function(k){if(typeof input[k]==='boolean')out[k]=input[k];});
    out.x=bounded(input.x,113,366,out.x);out.y=bounded(input.y,70,185,out.y);out.size=bounded(input.size,.5,1.05,1);
    if(Array.isArray(input.faults)&&input.faults.length===2)out.faults=input.faults.map(function(v){return v!==false;});
    ['measurements','quarter','laps'].forEach(function(k){
      if(Array.isArray(input[k]))out[k]=input[k].filter(function(n){return bounded(n,0,999,0)>0;}).slice(0,3);
    });
    return out;
  }
  function load(){
    try {var saved=localStorage.getItem('cockpit.v3');
      if(saved)return preferences(JSON.parse(saved));
      var old=localStorage.getItem('theme'), p=fresh();p.theme=({red:1,ice:3,amber:4})[old]||0;return p;
    } catch(e){return fresh();}
  }
  var p=load(), draft, route='performance', trail=[], scale=1, sessionTimer=0;
  var measuring='', timingKind='0–100 km/h', lastFocus=null;
  function save(){try{localStorage.setItem('cockpit.v3',JSON.stringify(p));}catch(e){}}
  function px(n){return n+'px';}
  function box(el,x,y,w,h){el.style.left=px(x);el.style.top=px(y);el.style.width=px(w);el.style.height=px(h);return el;}
  function layer(cls,x,y,w,h){var el=document.createElement('div');el.className=cls;surface.appendChild(el);return box(el,x,y,w,h);}
  function source(el,page,sx,sy,factor){
    var url='url("screens/'+page+'.webp")';el.style.backgroundImage=url;
    el.style.backgroundSize=px(pages[page][0]*factor)+' '+px(pages[page][1]*factor);
    el.style.backgroundPosition=px(-sx*factor)+' '+px(-sy*factor);
  }
  function piece(pg,sx,sy,w,h,x,y){var el=layer('piece',x,y,w,h);source(el,pg,sx,sy,1);return el;}
  function button(label,x,y,w,h,action){var b=document.createElement('button');b.className='control';b.type='button';b.setAttribute('aria-label',label);b.title=label;box(b,x,y,w,h);b.onclick=action;surface.appendChild(b);return b;}
  function reading(text,x,y,w,h,size){var el=layer('reading',x,y,w,h);el.style.fontSize=px(size);el.textContent=text;return el;}
  function outline(x,y,w,h){var el=layer('selection',x,y,w,h);el.style.borderColor=accents[p.accent];return el;}
  function fit(){
    var dim=pages[route];scale=Math.min(window.innerWidth/dim[0],window.innerHeight/dim[1]);
    stage.style.width=px(dim[0]);stage.style.height=px(dim[1]);
    var t='scale('+scale+') translate(-50%,-50%)';stage.style.transform=t;stage.style.webkitTransform=t;
  }
  function dash(){return p.layout===1?'race':'performance';}
  function enter(id,remember){
    if(!Object.prototype.hasOwnProperty.call(pages,id))return;
    if(route!==id && remember!==false){trail.push(route);if(trail.length>24)trail.shift();}
    closeDialog();route=id;
    if(id==='editor')draft=preferences(p);
    draw();
  }
  function back(){
    if(!dialog.hidden){closeDialog();return true;}
    if(trail.length){enter(trail.pop(),false);return true;}
    if(route!==dash()){enter(dash(),false);return true;}
    return false;
  }
  window.cockpitBack=back;
  document.addEventListener('keydown',function(e){if(e.key==='Escape'||e.keyCode===27){if(back())e.preventDefault();}});
  function closeDialog(){if(dialog.hidden)return;dialog.hidden=true;if(lastFocus&&document.body.contains(lastFocus))lastFocus.focus();}
  document.getElementById('dismiss').onclick=closeDialog;
  function modal(title,lines,actions){
    lastFocus=document.activeElement;
    var body=document.getElementById('dialog-body');body.innerHTML='';
    document.getElementById('dialog-title').textContent=title;
    lines.forEach(function(line){var el=document.createElement('p');el.textContent=line;body.appendChild(el);});
    (actions||[]).forEach(function(a){var el=document.createElement('button');el.textContent=a[0];el.onclick=a[1];body.appendChild(el);});
    dialog.hidden=false;document.getElementById('dismiss').focus();
  }
  function topbar(){
    button('Painel',169,0,61,36,function(){enter(route==='performance'||route==='race'?'layouts':dash());});
    button('Telemetria',230,0,56,36,function(){enter('telemetry');});
    button('Personalizar',286,0,64,36,function(){enter(route==='editor'?'themes':'editor');});
    button('Configurações',350,0,66,36,function(){enter('settings');});
    if(!p.connected)reading('Offline',439,17,40,12,8).style.color='#afbbca';
    button('Vgate · conexão simulada',416,0,75,36,function(){enter('settings');});
  }
  function cycle(delta){p.layout=(p.layout+delta+4)%4;save();enter(dash());}
  function dashboard(){
    if(route==='performance'&&p.layout===2){art.style.filter='hue-rotate(-25deg) saturate(1.45)';}
    if(route==='performance'&&p.layout===3){
      var minimal=layer('reading',0,37,493,234);minimal.style.background='#03060b';
      reading('112',120,65,253,100,74);reading('km/h',196,164,100,28,17);
    }
    if(p.custom){var factor=p.size*.77;var left=p.x/496*pages[route][0]-54*factor,top=p.y/213*pages[route][1]-54*factor;
      paintGauge('piece',p,left,top,factor);}
    button('Painel anterior',0,42,29,155,function(){cycle(-1);});
    button('Próximo painel',pages[route][0]-30,42,30,155,function(){cycle(1);});
    button('Selecione um Painel',32,42,pages[route][0]-64,151,function(){enter('layouts');});
  }
  function chooser(){
    var xs=[34,158,277,394],ws=[117,110,108,77];
    if(p.layout!==0){piece('layouts',161,77,104,3,40,75);piece('layouts',163,80,16,18,128,78);
      outline(xs[p.layout],74,ws[p.layout],90);}
    layouts.forEach(function(name,i){button(name,xs[i],71,ws[i],98,function(){p.layout=i;save();enter(dash());});});
    button('Painel anterior',0,68,31,97,function(){cycle(-1);});
    button('Próximo painel',472,68,21,97,function(){cycle(1);});
  }
  function side(){
    var names=['Gráficos','Logs','0–100 km/h','1/4 de Milha','Voltas','Diagnóstico'];
    var ys=route==='telemetry'?[54,84,109,137,164,190]:route==='timing'?[42,67,91,116,139,161]:[43,68,90,112,136,158];
    names.forEach(function(name,i){
      button(name,5,ys[i],109,i===5?26:ys[i+1]-ys[i],function(){
        if(i===0)enter('telemetry');
        if(i===1)modal('Logs',['Sessão local de demonstração','Velocidade 112 km/h · RPM 4.200','Óleo 112 °C · Água 95 °C · IAT 38 °C']);
        if(i===2||i===3||i===4){timingKind=name;enter('timing');}
        if(i===5)enter('diagnostics');
      });
    });
  }
  // Five-minute traces sampled from the reference graph. Touch a point to
  // inspect the temperatures at that moment in the offline demonstration.
  var trace=[[95,95,95,96,97,98,97,97,97,98,99,101,102,103,103,101,101,101,100,99,99,100,101,101,102,103,105,105,105,104,103,102,103,101,100,101,102,104,104,105,105,106,105,104,105,107,109,108,110,110,109,109,107,106,106,107,107,108,107,106,105,105,106,107,108,109,111,112,112],[81,81,82,83,82,83,83,83,83,83,84,83,83,83,84,84,84,85,84,83,84,84,83,83,82,83,84,85,86,87,87,87,87,87,87,88,87,87,87,88,88,87,87,87,87,88,90,90,90,90,89,90,91,92,93,93,92,92,92,91,90,90,90,91,93,93,95,96,95],[34,34,33,34,33,34,33,34,33,34,35,35,36,36,36,35,35,35,34,34,33,34,34,34,35,35,36,37,37,36,35,35,35,34,34,34,35,36,36,35,35,36,35,34,35,36,36,35,35,35,34,35,35,35,36,36,35,35,34,34,33,33,33,35,35,36,38,38,38]];
  function telemetry(){
    side();
    var chart=button('Consultar temperaturas no gráfico',162,78,280,106,function(e){
      var rect=stage.getBoundingClientRect();
      var ratio=e.detail?Math.max(0,Math.min(1,((e.clientX-rect.left)/scale-165)/274)):1;
      var index=Math.round(ratio*(trace[0].length-1)), sec=Math.round((1-ratio)*300);
      var time=sec?'Há '+Math.floor(sec/60)+' min '+(sec%60)+' s':'Agora';
      modal('Temperaturas · '+time,['Óleo '+trace[0][index]+' °C · Água '+trace[1][index]+' °C · IAT '+trace[2][index]+' °C']);
    });
    chart.setAttribute('aria-description','Histórico de cinco minutos de demonstração');
  }
  function diagnostics(){
    side();
    var count=Number(p.faults[0])+Number(p.faults[1]);
    if(count!==2)reading('Códigos anteriores ('+count+')',128,74,135,20,11);
    p.faults.forEach(function(present,i){
      var y=99+i*28;
      if(!present){var mask=layer('piece',127,y-2,355,26);mask.style.background='#090f16';}
      else button('Excluir código '+(i?'P0171':'P0302'),451,y-2,29,23,function(){p.faults[i]=false;save();draw();});
    });
    button('Limpar códigos (simulação)',199,161,216,31,function(){modal('Limpar códigos da demonstração',
      ['Apenas o histórico simulado deste aparelho será apagado.'],[['Limpar',function(){p.faults=[false,false];save();closeDialog();draw();}]]);});
  }
  function timing(){
    side();
    var key=timingKind==='0–100 km/h'?'measurements':timingKind==='Voltas'?'laps':'quarter';
    var history=p[key], alternate=key!=='measurements';
    if(alternate){
      reading(timingKind,153,75,141,22,12);
      outline(12,key==='laps'?136:115,97,22);
      reading('—',151,97,147,44,33);
      var empty=layer('piece',329,94,146,77);empty.style.background='#090f16';
    }
    if(measuring===key)reading('Medindo…',153,98,144,43,23);
    else if(history.length)reading(history[0].toFixed(2).replace('.',',')+' s',151,97,147,44,33);
    history.forEach(function(v,i){reading(v.toFixed(2).replace('.',',')+' s      Demo',335,93+i*22,138,19,12);});
    button('Iniciar medição simulada',145,143,119,33,function(){
      if(measuring)return;measuring=key;draw();
      var seconds=key==='measurements'?5.62:key==='quarter'?13.84:62.40;
      sessionTimer=window.setTimeout(function(){measuring='';p[key].unshift(seconds);p[key]=p[key].slice(0,3);save();if(route==='timing')draw();},seconds*1000);
    });
    button('Reiniciar medição',270,142,35,34,function(){if(measuring===key){clearTimeout(sessionTimer);measuring='';}p[key]=[];save();draw();});
  }
  function paintGauge(cls,config,left,top,factor){
    var g=layer(cls,left,top,132*factor,114*factor);
    source(g,'editor',176,70,factor);
    if(config.color||config.style){g.style.filter='hue-rotate('+([0,125,220,180,65][config.color]+config.style*21)+'deg)';}
    if(config.sensor){
      var value=document.createElement('span');value.className='reading';
      value.textContent=values[config.sensor];box(value,28*factor,38*factor,77*factor,32*factor);value.style.fontSize=px(24*factor);g.appendChild(value);
      var unit=document.createElement('span');unit.className='reading';unit.textContent=units[config.sensor];box(unit,42*factor,70*factor,49*factor,12*factor);unit.style.fontSize=px(8*factor);g.appendChild(unit);
    }
    return g;
  }
  function editor(){
    if(!draft)draft=preferences(p);
    var modified=draft.x!==242||draft.y!==127||draft.size!==1||draft.sensor||draft.color||draft.style;
    if(modified){var grid=layer('piece',172,64,143,124);grid.style.backgroundImage='url("screens/grid.webp")';grid.style.backgroundRepeat='repeat';}
    var factor=draft.size,w=132*factor,h=114*factor;
    var g=modified?paintGauge('instrument',draft,draft.x-w/2,draft.y-h/2,factor):layer('instrument',176,70,w,h);
    g.setAttribute('aria-label','Mover indicador');g.setAttribute('role','slider');g.tabIndex=0;
    function move(cx,cy){
      draft.x=Math.max(114+w/2,Math.min(367-w/2,cx));draft.y=Math.max(69+h/2,Math.min(187-h/2,cy));
    }
    function drag(x,y){
      var rect=stage.getBoundingClientRect();move((x-rect.left)/scale,(y-rect.top)/scale);
    }
    function bindDrag(el,onMove,finish){
      function start(event){
        if(event.type==='mousedown'&&event.button!==0)return;
        event.preventDefault();var touch=event.type==='touchstart';
        var moveName=touch?'touchmove':'mousemove',endName=touch?'touchend':'mouseup';
        function update(e){if(e.cancelable)e.preventDefault();var pt=touch?e.touches[0]:e;if(pt)onMove(pt.clientX,pt.clientY);}
        function end(){window.removeEventListener(moveName,update,false);window.removeEventListener(endName,end,false);window.removeEventListener('touchcancel',end,false);finish();}
        window.addEventListener(moveName,update,{passive:false});window.addEventListener(endName,end,false);window.addEventListener('touchcancel',end,false);
        if(el!==g){var pt=touch?event.touches[0]:event;onMove(pt.clientX,pt.clientY);}
      }
      el.addEventListener('touchstart',start,{passive:false});el.addEventListener('mousedown',start);
    }
    bindDrag(g,function(x,y){drag(x,y);draw();},draw);
    g.onkeydown=function(e){var delta={37:[-4,0],38:[0,-4],39:[4,0],40:[0,4]}[e.keyCode];if(delta){e.preventDefault();move(draft.x+delta[0],draft.y+delta[1]);draw();surface.querySelector('.instrument').focus();}};
    sensorNames.forEach(function(s,i){button(s,8,67+i*20,96,20,function(){draft.sensor=i;draw();});});
    if(draft.size!==1)outline(385+(draft.size-.5)/.55*83,102,6,9);
    var slider=button('Tamanho do indicador',382,96,100,21,function(){});
    function setSize(x){var rect=stage.getBoundingClientRect();draft.size=Math.max(.5,Math.min(1.05,.5+((x-rect.left)/scale-385)*.55/92));move(draft.x,draft.y);draw();}
    bindDrag(slider,setSize,draw);
    slider.onkeydown=function(e){if(e.keyCode===37||e.keyCode===39){draft.size=Math.max(.5,Math.min(1.05,draft.size+(e.keyCode===37?-.05:.05)));move(draft.x,draft.y);draw();}};
    for(var i=0;i<5;i++)(function(n){button('Cor '+(n+1),382+n*20,130,19,21,function(){draft.color=n;draw();});})(i);
    for(var j=0;j<3;j++)(function(n){button('Estilo '+(n+1),382+n*33,168,31,27,function(){draft.style=n;draw();});})(j);
    button('Cancelar',154,187,94,25,function(){enter(dash());});
    button('Salvar',261,187,96,25,function(){
      ['sensor','x','y','size','color','style'].forEach(function(k){p[k]=draft[k];});
      p.custom=Boolean(modified);save();enter(dash());
    });
  }
  function themes(){
    var xs=[20,122,215,309,400], ws=[97,86,86,88,85];
    themeNames.forEach(function(n,i){button(n,xs[i],59,ws[i],80,function(){p.theme=i;save();draw();});});
    if(p.theme)outline(xs[p.theme],62,ws[p.theme],75);
    var ax=[39,71,102,125,149,180,213];
    ax.forEach(function(x,i){button('Cor de destaque '+(i+1),x-11,166,22,25,function(){p.accent=i;save();draw();});});
    if(p.accent)reading('✓',ax[p.accent]-6,173,12,12,9).style.background=accents[p.accent];
    ['Automático','Claro','Escuro'].forEach(function(n,i){button(n,[267,354,421][i],159,[87,67,65][i],34,function(){p.mode=i;save();draw();});});
    if(p.mode)outline(p.mode===1?354:421,163,63,27);
  }
  function settings(){
    function toggle(key){p[key]=!p[key];save();draw();}
    if(!p.connected){reading('Desconectado',194,89,69,16,10);reading('Conectar',396,77,75,27,11);}
    button(p.connected?'Desconectar adaptador simulado':'Conectar adaptador simulado',391,72,89,33,function(){toggle('connected');});
    var keys=['auto','reconnect','economy'],labels=['Conectar automaticamente ao iniciar','Reconectar em caso de queda','Modo de economia'];
    keys.forEach(function(k,i){
      if(p[k]!==defaults[k])piece('settings',446,p[k]?114:166,32,19,446,114+25*i);
      button(labels[i],145,109+i*25,337,25,function(){toggle(k);});
    });
    var sections=['Conexão OBD2','Geral','Unidades','Alarmes','Exportar/Importar','Sobre'];
    sections.forEach(function(s,i){button(s,8,51+22*i,122,22,function(){
      if(i===0)return;
      if(i===1)modal(s,['BMW Digital Cockpit · Multimídia Android','Exibição horizontal · Português (Brasil)']);
      if(i===2)modal(s,['Velocidade em km/h · Rotação em RPM','Temperaturas em °C · Pressão em bar · Tensão em V']);
      if(i===3)modal(s,['Nenhum alarme ativo na demonstração.']);
      if(i===4)transferPrefs();
      if(i===5)modal(s,['BMW Digital Cockpit v0.3 · Demonstração offline','O Vgate, as leituras, os códigos e os tempos são simulados.']);
    });});
  }
  function transferPrefs(){
    modal('Exportar/Importar',['Copie o conteúdo para exportar ou cole uma configuração e toque em Importar.'],[['Importar',function(){
      try{var data=JSON.parse(field.value);if(!data||data.format!=='bmw-cockpit-3'||!data.preferences)throw Error();p=preferences(data.preferences);save();closeDialog();draw();}
      catch(e){field.setCustomValidity('Configuração inválida');field.focus();if(field.reportValidity)field.reportValidity();}
    }]]);
    var field=document.createElement('textarea');field.setAttribute('aria-label','Configuração do cockpit');field.value=JSON.stringify({format:'bmw-cockpit-3',preferences:p});
    field.style.width='100%';field.rows=3;field.oninput=function(){field.setCustomValidity('');};document.getElementById('dialog-body').appendChild(field);
  }
  function draw(){
    var dim=pages[route];surface.innerHTML='';stage.setAttribute('data-screen',route);stage.setAttribute('aria-label',dim[2]+' · demonstração');
    var src='screens/'+route+'.webp';if(art.getAttribute('src')!==src)art.setAttribute('src',src);
    var effect=(route==='performance'||route==='race')?themeEffects[p.theme]:'';
    if(p.mode===1)effect+=' brightness(1.22)';if(p.mode===2)effect+=' brightness(.82)';
    art.style.filter=effect;fit();topbar();
    if(route==='performance'||route==='race')dashboard();
    if(route==='layouts')chooser();
    if(route==='editor')editor();
    if(route==='themes')themes();
    if(route==='settings')settings();
    if(route==='diagnostics')diagnostics();
    if(route==='timing')timing();
    if(route==='telemetry')telemetry();
  }
  var start=window.location.hash.slice(1);
  if(!Object.prototype.hasOwnProperty.call(pages,start))start=dash();
  enter(start,false);
  window.addEventListener('resize',fit);
  // Native Activity recreation restores the current route independently of settings.
  window.cockpitState=function(){return {route:route,trail:trail.slice(),draft:draft};};
  window.cockpitRestore=function(s){
    if(!s||!Object.prototype.hasOwnProperty.call(pages,s.route))return;
    trail=Array.isArray(s.trail)?s.trail.filter(function(k){return Object.prototype.hasOwnProperty.call(pages,k);}).slice(-24):[];
    enter(s.route,false);if(route==='editor'&&s.draft){draft=preferences(s.draft);draw();}
  };
  function checkpoint(){
    var json=JSON.stringify(window.cockpitState());
    try{sessionStorage.setItem('cockpit.session',json);}catch(e){}
    if(window.NativeScreen)window.NativeScreen.remember(json);
  }
  function restoreSession(){try{if(!window.location.hash)window.cockpitRestore(JSON.parse(sessionStorage.getItem('cockpit.session')));}catch(e){}}
  restoreSession();
  window.addEventListener('pagehide',checkpoint);
  document.addEventListener('visibilitychange',function(){if(document.hidden)checkpoint();});
  var drawScreen=draw;
  draw=function(){drawScreen();checkpoint();};
}());
