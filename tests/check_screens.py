from pathlib import Path
from playwright.sync_api import sync_playwright
from PIL import Image,ImageChops
import io
root=Path(__file__).resolve().parents[1]/'app/src/main/assets'
url=(root/'index.html').as_uri()
with sync_playwright() as s:
 b=s.chromium.launch(channel='chrome',args=['--no-sandbox','--allow-file-access-from-files'])
 page=b.new_page(viewport={'width':1024,'height':600})
 errors=[]
 page.on('pageerror',lambda e:errors.append(str(e)))
 scenes=['performance','race','telemetry','layouts','editor','diagnostics','timing','themes','settings']
 for scene in scenes:
  with Image.open(root/'screens'/f'{scene}.webp') as reference:
   page.set_viewport_size(dict(zip(('width','height'),reference.size)))
   page.goto(url+'#'+scene)
   page.reload()
   page.wait_for_function("document.getElementById('art').complete && document.getElementById('art').naturalWidth > 0")
   screenshot=Image.open(io.BytesIO(page.screenshot())).convert('RGB')
   diff=ImageChops.difference(reference.convert('RGB'),screenshot)
   assert not diff.getbbox(), (scene,diff.getbbox())
 print('9 original panels match browser renders pixel for pixel at source resolution')
 page.set_viewport_size({'width':1280,'height':480})
 page.goto(url+'#performance');page.reload()
 click=lambda n:page.get_by_role('button',name=n,exact=True).click()
 scene=lambda:page.locator('#stage').get_attribute('data-screen')
 def check(s): assert scene()==s,(s,scene())
 click('Painel');check('layouts');click('Race Dashboard');check('race')
 click('Telemetria');click('Diagnóstico');click('Excluir código P0302')
 click('Limpar códigos (simulação)');click('Limpar')
 click('0–100 km/h');click('Iniciar medição simulada');page.wait_for_timeout(5800)
 assert '5,62' in page.locator('#surface').inner_text()
 click('Personalizar');check('editor');click('RPM')
 g=page.locator('.instrument').bounding_box();page.mouse.move(g['x']+g['width']/2,g['y']+g['height']/2);page.mouse.down();page.mouse.move(g['x']+g['width']/2+18,g['y']+g['height']/2);page.mouse.up()
 click('Salvar');check('race')
 click('Personalizar');click('Personalizar');check('themes');click('Blue Ice')
 click('Configurações');click('Desconectar adaptador simulado');click('Modo de economia')
 click('Sobre');page.evaluate('window.cockpitBack()');assert not page.locator('#dialog').is_visible()
 assert page.evaluate('window.cockpitBack()');check('themes')
 for _ in range(10):
  click('Configurações');click('Telemetria');click('Personalizar');click('Painel')
 page.evaluate("localStorage.setItem('cockpit.v3','{invalid')")
 page.reload();check('performance')
 page.evaluate("localStorage.setItem('cockpit.v3',JSON.stringify({theme:99,layout:-2,faults:'broken'}))")
 page.reload();click('Configurações');check('settings')
 for wh in [(800,480),(1920,720),(640,360)]:
  page.set_viewport_size(dict(zip(('width','height'),wh)))
  box=page.locator('#stage').bounding_box();assert box['x']>=-1 and box['y']>=-1 and box['width']<=wh[0]+1 and box['height']<=wh[1]+1
 assert not errors,errors
 print('Navigation, drag/save, diagnostic actions, timer, preference bounds, 40 transitions, dialog back and aspect ratios passed')
