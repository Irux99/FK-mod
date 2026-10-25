"""Create an original radar material atlas; no imported texture or AI generator."""
from PIL import Image,ImageChops,ImageDraw
from pathlib import Path
import random
random_source=random.Random(2000);width,height=2048,1024
noise=Image.frombytes('L',(width,height),random_source.randbytes(width*height)).point(lambda n:n//32)
image=ImageChops.add(Image.new('RGB',(width,height),(48,59,36)),noise.convert('RGB'))
draw=ImageDraw.Draw(image)
# Faces of the 26 x 18 x 4 panel in the virtual 512 x 256 UV atlas.
for u in [4,34]:
 x0,y0,x1,y1=u*4,16,(u+26)*4,88
 draw.rectangle((x0+2,y0+2,x1-2,y1-2),outline=(29,36,24),width=2)
 draw.rectangle((x0+4,y0+4,x1-4,y1-4),outline=(79,87,61),width=1)
 for x in [x0+9,x1-9]:
  for y in [y0+9,y1-9]:
   draw.ellipse((x-2,y-2,x+2,y+2),fill=(112,118,100));draw.line((x-1,y,x+1,y),fill=(35,39,31))
# Dark antenna face and metal protective grid.
draw.rectangle((320,320,440,400),fill=(20,32,31))
draw.rectangle((352,128,520,248),fill=(83,90,77))
for _ in range(600):
 x=random_source.randrange(width);y=random_source.randrange(height)
 draw.line((x,y,x+random_source.randrange(1,5),y),fill=(65,72,50))
path=Path(__file__).resolve().parents[2]/'src/main/resources/assets/iruxfk2000/textures/entity/radar.png'
image.save(path)
print(path)
