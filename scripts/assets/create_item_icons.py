#!/usr/bin/env python3
"""Original pixel-art inventory icons; does not touch the SBW crate or imported models."""
from pathlib import Path
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parents[2]/'src/main/resources/assets/iruxfk2000/textures/item'
ink='#111b20'; edge='#a9b6a0'; olive='#4c5c3c'; light='#87956a'
def canvas():
 im=Image.new('RGBA',(64,64));return im,ImageDraw.Draw(im)
def save(im,name): im.save(root/(name+'.png'))
im,d=canvas()
d.line((39,18,47,2),fill=ink,width=5);d.line((39,18,47,2),fill='#b9c1bb',width=2)
d.rounded_rectangle((14,14,49,60),radius=5,fill=ink)
d.rounded_rectangle((16,16,47,58),radius=4,fill=olive,outline=edge,width=1)
d.rectangle((20,20,43,38),fill='#172c2d',outline='#b7c4b1');d.line((22,34,41,34),fill='#315d4b');d.line((31,22,31,36),fill='#315d4b')
d.arc((23,22,40,36),0,359,fill='#3b8d62');d.line((31,30,39,24),fill='#9fffc1');d.rectangle((35,26,37,28),fill='#f4b65d')
for x,y,c in [(22,44,'#a7b6a5'),(31,44,'#a7b6a5'),(40,44,'#c75542'),(22,52,'#24312a'),(31,52,'#24312a'),(40,52,'#24312a')]:
 d.rectangle((x-2,y-2,x+2,y+2),fill=ink);d.rectangle((x-1,y-1,x+1,y),fill=c)
d.line((17,18,17,54),fill=light);save(im,'remote_controller')
im,d=canvas()
d.polygon([(8,58),(12,42),(20,38),(40,13),(51,5),(58,4),(57,13),(50,24),(28,46),(25,55)],fill=ink)
d.polygon([(13,51),(20,39),(43,15),(53,7),(55,8),(48,23),(26,46)],fill='#b6bdb8')
d.polygon([(20,40),(44,15),(50,12),(27,42)],fill='#e0e4d9')
d.polygon([(43,15),(53,7),(55,8),(49,20)],fill='#525f58')
d.line((34,25,40,31),fill='#8f4034',width=3)
d.polygon([(23,39),(13,40),(8,49),(18,47)],fill='#66775b',outline=ink)
d.polygon([(28,44),(29,54),(19,59),(20,49)],fill='#53634d',outline=ink)
d.line((17,48,21,52),fill='#293732',width=3);save(im,'missile')
im,d=canvas()
# Isometric launcher truck, eight wheels and six tube mouths.
d.polygon([(4,38),(22,27),(59,37),(43,52)],fill=ink)
for x,y in [(12,43),(20,46),(36,51),(47,45)]:
 d.ellipse((x-4,y-2,x+4,y+7),fill=ink);d.ellipse((x-1,y+1,x+2,y+4),fill='#778177')
d.polygon([(5,31),(18,24),(29,28),(29,43),(17,48),(5,42)],fill=olive,outline=ink)
d.polygon([(5,31),(18,24),(29,28),(17,36)],fill=light,outline=ink)
d.polygon([(7,33),(16,37),(16,42),(7,39)],fill='#263e43',outline='#a8b9af')
d.polygon([(19,35),(26,31),(26,37),(19,41)],fill='#203b40',outline='#9baa98')
d.polygon([(28,36),(44,29),(59,35),(44,47),(28,43)],fill='#586b47',outline=ink)
for x,y in [(30,21),(35,23),(40,25),(30,27),(35,29),(40,31)]:
 d.polygon([(x,y),(x+4,y-2),(x+17,y+7),(x+13,y+10)],fill=olive,outline=ink)
 d.line((x+3,y,x+15,y+8),fill='#9aa984')
 d.ellipse((x+12,y+6,x+17,y+11),fill='#1a2521',outline='#879675')
d.rectangle((43,13,45,30),fill=ink);d.polygon([(39,5),(51,9),(51,21),(39,17)],fill='#334237',outline=ink)
for y in (9,13,17):d.line((40,y,50,y+3),fill='#6e8059')
d.rectangle((7,40,9,41),fill='#e5d399');d.rectangle((13,43,15,44),fill='#e5d399');save(im,'fk2000_deployment')
