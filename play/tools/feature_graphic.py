"""Feature graphic 1024 × 500 pro každý jazyk. Spuštění: python3 play/tools/feature_graphic.py (Pillow)."""
import sys
sys.path.insert(0, str(__import__("pathlib").Path(__file__).parent))
from icon import feather_mask, gradient
from PIL import Image, ImageDraw, ImageFont, ImageFilter
R=str(__import__("pathlib").Path(__file__).resolve().parents[2])
F=R+"/client/resources/src/commonMain/composeResources/font/"
texts={"cs-CZ":"Neoficiální klient pro Telegram","en-US":"An unofficial Telegram client"}
for loc,tag in texts.items():
    W,H=2048,1000
    im=gradient(W,H)
    fm=feather_mask(1000,0,0,1.0)
    im.paste(Image.new("RGB",(1000,1000),(255,243,232)),(40,20),fm)
    d=ImageDraw.Draw(im)
    d.text((840,300),"Plume",font=ImageFont.truetype(F+"bricolage_grotesque_bold.ttf",230),fill=(255,243,232))
    d.text((850,580),tag,font=ImageFont.truetype(F+"figtree_medium.ttf",70),fill=(214,204,255))
    d.text((850,680),"Material 3 Expressive",font=ImageFont.truetype(F+"figtree_regular.ttf",56),fill=(180,168,235))
    im.resize((1024,500),Image.LANCZOS).save(f"{R}/play/graphics/{loc}/featureGraphic/feature.png")
