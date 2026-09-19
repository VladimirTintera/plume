"""Návrh ikony (pírko) pro záznam v Play: play/graphics/icon/icon.png. Spuštění: python3 play/tools/icon.py (Pillow)."""
import sys
from PIL import Image, ImageDraw
N=2048
def lerp(a,b,t): return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
def bez(p0,p1,p2,p3,n=120):
    return [tuple((1-t)**3*p0[i]+3*(1-t)**2*t*p1[i]+3*(1-t)*t**2*p2[i]+t**3*p3[i] for i in range(2)) for t in (k/n for k in range(n+1))]
def feather_mask(s, ox=0, oy=0, scale=1.0):
    m=Image.new("L",(s,s),0); d=ImageDraw.Draw(m)
    P=lambda x,y:(ox+x*s*scale, oy+y*s*scale)
    tip=P(0.78,0.15); base=P(0.34,0.68)
    left=bez(base,P(0.16,0.50),P(0.46,0.20),tip)
    right=bez(tip,P(0.80,0.40),P(0.58,0.60),base)
    d.polygon(left+right,fill=255)
    # klínové zářezy od okraje směrem k ostnu
    for tri in [[P(0.62,0.535),P(0.745,0.50),P(0.60,0.50)],
                [P(0.24,0.44),P(0.21,0.37),P(0.33,0.43)],
                [P(0.735,0.36),P(0.80,0.335),P(0.70,0.345)]]:
        d.polygon(tri,fill=0)
    # osten: tenká linka uvnitř, stopka ven
    shaft=bez(P(0.34,0.68),P(0.45,0.52),P(0.60,0.34),P(0.72,0.22),40)
    q=0.007*s*scale
    for e in bez(P(0.34,0.68),P(0.45,0.52),P(0.60,0.34),P(0.72,0.22),400):
        d.ellipse([e[0]-q,e[1]-q,e[0]+q,e[1]+q],fill=0)
    stem=bez(P(0.345,0.67),P(0.30,0.74),P(0.25,0.80),P(0.19,0.85),40)
    r=0.015*s*scale
    for e in bez(P(0.345,0.67),P(0.30,0.74),P(0.25,0.80),P(0.19,0.85),400):
        d.ellipse([e[0]-r,e[1]-r,e[0]+r,e[1]+r],fill=255)
    return m
def gradient(w,h,c1,c2):
    im=Image.new("RGB",(w,h)); d=ImageDraw.Draw(im)
    for y in range(h): d.line([(0,y),(w,y)],fill=lerp(c1,c2,y/h))
    return im
if __name__=="__main__":
    ic=gradient(N,N,(92,64,190),(44,30,118))
    ic.paste(Image.new("RGB",(N,N),(255,243,232)),(0,0),feather_mask(N))
    root=__import__("pathlib").Path(__file__).resolve().parents[1]
    ic.resize((512,512),Image.LANCZOS).save(root/"graphics/icon/icon.png")
