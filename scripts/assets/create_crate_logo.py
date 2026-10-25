#!/usr/bin/env python3
"""Draw an original FK-2000 shipping mark; no third-party art is used."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

size = 256
image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)
draw.rounded_rectangle((8, 8, 248, 248), radius=15, fill=(21, 29, 28, 255), outline=(247, 164, 58, 255), width=8)
ink = (247, 196, 105, 255)
# A purpose-drawn eight-wheel launcher silhouette.
draw.polygon(((34, 151), (34, 124), (54, 97), (89, 97), (95, 141), (220, 141), (220, 160), (34, 160)), fill=ink)
draw.polygon(((48, 121), (61, 106), (78, 106), (80, 121)), fill=(21, 29, 28, 255))
for x in (60, 99, 173, 210):
    draw.ellipse((x - 14, 149, x + 14, 177), fill=ink)
    draw.ellipse((x - 5, 158, x + 5, 168), fill=(21, 29, 28, 255))
for x in (121, 137, 153):
    draw.rounded_rectangle((x, 55, x + 12, 134), radius=4, fill=ink)
draw.rectangle((181, 82, 187, 140), fill=ink)
draw.rectangle((167, 69, 201, 91), fill=ink)
font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
font = ImageFont.truetype(font_path, 35)
draw.text((128, 208), "FK-2000", anchor="mm", font=font, fill=ink)
target = Path(__file__).resolve().parents[2] / "src/main/resources/assets/iruxfk2000/textures/gui/fk2000-crate-logo.png"
target.parent.mkdir(parents=True, exist_ok=True)
image.save(target)
print(target)
