"""Read-only UI smoke on an emulator: navigation, help, themes and text scale."""
import base64
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw

root = Path('ui-evidence'); root.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=30)

def nodes():
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/window.xml')).iter('node')

def tap(label, description=False):
    for attempt in range(8):
        for node in nodes():
            value = node.get('content-desc' if description else 'text','')
            if value == label or (description and value.startswith(label)):
                box = [int(n) for n in re.findall(r'\d+', node.get('bounds',''))]
                if len(box)==4 and box[2]>box[0] and box[3]>box[1]:
                    adb('shell','input','tap',str((box[0]+box[2])//2),str((box[1]+box[3])//2))
                    time.sleep(1); return
        time.sleep(1)
    raise AssertionError('UI control missing: '+label)

def launch():
    adb('shell','am','force-stop','app.pocketinstall')
    adb('shell','am','start','-n','app.pocketinstall/.MainActivity'); time.sleep(4)

def capture(name):
    (root/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    (root/(name+'.xml')).write_bytes(adb('exec-out','cat','/sdcard/window.xml'))
    assert b'app.pocketinstall' in adb('shell','dumpsys','activity','activities')

adb('shell','wm','size','750x1400'); adb('shell','wm','density','320')
adb('shell','cmd','uimode','night','no'); launch()
capture('01-prepare-light')
tap('Aide : Environnement PC',True); capture('02-context-help'); tap('Compris')
tap('Installer'); capture('03-install-light')
tap('Aide'); capture('04-help-light')
tap('Afficher les détails techniques'); capture('05-diagnostic')
adb('shell','cmd','uimode','night','yes'); launch(); capture('06-prepare-dark')
tap('Installer'); capture('07-install-dark')
adb('shell','settings','put','system','font_scale','2.0'); launch(); capture('08-large-text')
adb('shell','settings','put','system','font_scale','1.0')
adb('shell','wm','size','1400x750'); launch(); capture('09-landscape')
logs = adb('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace')
(root/'runtime.log').write_text(logs)
assert 'Process: app.pocketinstall' not in logs, logs
# Compact contact sheets for review; originals remain in the build artifact.
files=sorted(root.glob('*.png'))
for start in (0,5):
    selected=files[start:start+5]
    sheet=Image.new('RGB',(300*len(selected),610),'#111820')
    draw=ImageDraw.Draw(sheet)
    for i,path in enumerate(selected):
        im=Image.open(path).convert('RGB'); im.thumbnail((290,575))
        sheet.paste(im,(i*300+(300-im.width)//2,30))
        draw.text((i*300+8,8),path.stem,fill='white')
    path=root/('contact-'+str(start)+'.jpg'); sheet.save(path,quality=82)
    print('UI_REVIEW_IMAGE '+path.name+' '+base64.b64encode(path.read_bytes()).decode(),flush=True)
print('UI_SMOKE_OK: navigation, contextual help, light/dark, 200% text and landscape; no app runtime crash.')
