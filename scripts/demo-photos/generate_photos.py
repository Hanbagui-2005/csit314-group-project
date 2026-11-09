"""Render before/after room illustrations used as demo portfolio photos (IDR-07, PM-04).

Usage: python scripts/demo-photos/generate_photos.py [output_dir]
Default output_dir: demo-photos/ at the repo root. Deterministic (fixed seeds).
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

W, H = 1200, 800
# back wall rectangle; everything else is drawn in one-point perspective toward VP
BX0, BY0, BX1, BY1 = 300, 170, 900, 560
VP = (600, 365)


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(int(lerp(a, b, t)) for a, b in zip(c1, c2))


def shell(d, wall, side, ceil, floor):
    d.polygon([(0, 0), (W, 0), (BX1, BY0), (BX0, BY0)], fill=ceil)
    d.polygon([(0, 0), (BX0, BY0), (BX0, BY1), (0, H)], fill=side)
    d.polygon([(W, 0), (BX1, BY0), (BX1, BY1), (W, H)], fill=mix(side, (0, 0, 0), 0.08))
    d.polygon([(0, H), (W, H), (BX1, BY1), (BX0, BY1)], fill=floor)
    d.rectangle([BX0, BY0, BX1, BY1], fill=wall)


def floor_planks(d, base, line):
    # boards converge on the vanishing point
    for i in range(-6, 19):
        x = lerp(0, W, i / 12)
        x_back = lerp(BX0, BX1, (x - 0) / W)
        d.line([(x_back, BY1), (x, H)], fill=line, width=2)
    rnd = random.Random(7)
    for k in range(1, 9):
        t = (k / 9) ** 1.6
        y = lerp(BY1, H, t)
        xl = lerp(BX0, 0, t)
        xr = lerp(BX1, W, t)
        for _ in range(5):
            x = rnd.uniform(xl, xr)
            d.line([(x, y), (x + 40 * t + 10, y)], fill=line, width=1)


def floor_tiles(d, line):
    for i in range(0, 13):
        x = lerp(0, W, i / 12)
        x_back = lerp(BX0, BX1, i / 12)
        d.line([(x_back, BY1), (x, H)], fill=line, width=2)
    for k in range(1, 7):
        t = (k / 7) ** 1.5
        y = lerp(BY1, H, t)
        d.line([(lerp(BX0, 0, t), y), (lerp(BX1, W, t), y)], fill=line, width=2)


def soft(img, draw_fn, blur=18):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(blur)))


def window(img, d, x0, y0, x1, y1, frame, glass=(196, 224, 245), light=True):
    if light:
        soft(img, lambda l: l.polygon([(x0, y1), (x1, y1), (x1 + 160, H), (x0 - 60, H)],
                                      fill=(255, 244, 214, 70)), 30)
    d.rectangle([x0, y0, x1, y1], fill=frame)
    d.rectangle([x0 + 8, y0 + 8, x1 - 8, y1 - 8], fill=glass)
    # sky gradient + far hills
    for y in range(y0 + 8, y1 - 8):
        t = (y - y0) / (y1 - y0)
        d.line([(x0 + 8, y), (x1 - 8, y)], fill=mix(glass, (236, 244, 250), t))
    d.polygon([(x0 + 8, y1 - 40), (x0 + 60, y1 - 70), (x1 - 40, y1 - 50), (x1 - 8, y1 - 60),
               (x1 - 8, y1 - 8), (x0 + 8, y1 - 8)], fill=(150, 184, 150))
    mx = (x0 + x1) // 2
    my = (y0 + y1) // 2
    d.line([(mx, y0), (mx, y1)], fill=frame, width=8)
    d.line([(x0, my), (x1, my)], fill=frame, width=8)


def grime(img, seed, cracks=6, stains=7):
    rnd = random.Random(seed)

    def stains_fn(l):
        for _ in range(stains):
            x, y = rnd.uniform(40, W - 40), rnd.uniform(20, BY1 + 40)
            r = rnd.uniform(30, 90)
            l.ellipse([x - r, y - r * 0.7, x + r, y + r * 0.7], fill=(90, 70, 40, rnd.randint(40, 80)))
    soft(img, stains_fn, 22)
    d = ImageDraw.Draw(img)
    for _ in range(cracks):
        x, y = rnd.uniform(BX0 + 20, BX1 - 20), rnd.uniform(BY0 + 10, BY1 - 60)
        pts = [(x, y)]
        for _ in range(rnd.randint(5, 9)):
            x += rnd.uniform(-18, 18)
            y += rnd.uniform(6, 22)
            pts.append((x, y))
        d.line(pts, fill=(70, 62, 52), width=2)
    # peeling paint flakes
    for _ in range(25):
        x, y = rnd.uniform(BX0, BX1), rnd.uniform(BY0, BY1 - 40)
        s = rnd.uniform(4, 14)
        d.polygon([(x, y), (x + s, y + s * 0.3), (x + s * 0.6, y + s)], fill=(214, 206, 186))


def bare_bulb(img, d, x=600):
    d.line([(x, 0), (x, 120)], fill=(40, 40, 40), width=3)
    soft(img, lambda l: l.ellipse([x - 90, 50, x + 90, 210], fill=(255, 236, 170, 70)), 25)
    d.ellipse([x - 14, 116, x + 14, 146], fill=(255, 240, 190))


def boxes(d, seed):
    rnd = random.Random(seed)
    for bx, by, bw, bh in [(140, 600, 170, 120), (250, 640, 130, 110), (820, 610, 160, 130)]:
        bx += rnd.randint(-10, 10)
        c = (178, 140, 92)
        d.rectangle([bx, by, bx + bw, by + bh], fill=c, outline=(120, 92, 58), width=3)
        d.polygon([(bx, by), (bx + 30, by - 22), (bx + bw + 30, by - 22), (bx + bw, by)], fill=mix(c, (255, 255, 255), 0.15))
        d.polygon([(bx + bw, by), (bx + bw + 30, by - 22), (bx + bw + 30, by + bh - 22), (bx + bw, by + bh)], fill=mix(c, (0, 0, 0), 0.2))
        d.line([(bx + bw / 2, by), (bx + bw / 2, by + bh)], fill=(214, 196, 150), width=6)


def vignette(img, strength):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([-200, -150, W + 200, H + 150], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(160))
    dark = Image.new("RGBA", img.size, (0, 0, 0, strength))
    dark.putalpha(Image.eval(mask, lambda v: int((255 - v) * strength / 255)))
    img.alpha_composite(dark)


def noise(img, amount, seed):
    rnd = random.Random(seed)
    px = img.load()
    for _ in range(W * H // 6):
        x, y = rnd.randrange(W), rnd.randrange(H)
        r, g, b, a = px[x, y]
        n = rnd.randint(-amount, amount)
        px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)), a)


def new_canvas():
    return Image.new("RGBA", (W, H), (0, 0, 0, 255))


def before_room(seed, wall=(176, 168, 146), floor=(128, 118, 98), worn_window=True):
    img = new_canvas()
    d = ImageDraw.Draw(img)
    shell(d, wall, mix(wall, (0, 0, 0), 0.12), mix(wall, (255, 255, 255), 0.1), floor)
    floor_tiles(d, mix(floor, (0, 0, 0), 0.25))
    if worn_window:
        window(img, d, 640, 230, 830, 420, (150, 140, 120), glass=(170, 180, 176), light=False)
        d = ImageDraw.Draw(img)
    grime(img, seed)
    d = ImageDraw.Draw(img)
    bare_bulb(img, d)
    return img, ImageDraw.Draw(img)


def finish_before(img, seed):
    vignette(img, 150)
    noise(img, 14, seed)
    return img


def finish_after(img, seed):
    soft(img, lambda l: l.ellipse([150, 80, 1050, 700], fill=(255, 240, 210, 40)), 120)
    vignette(img, 60)
    noise(img, 4, seed)
    return img


def shadow(img, box, alpha=70, blur=14):
    soft(img, lambda l: l.ellipse(box, fill=(0, 0, 0, alpha)), blur)


def plant(d, x, y, scale=1.0):
    d.polygon([(x - 26 * scale, y), (x + 26 * scale, y), (x + 20 * scale, y + 50 * scale), (x - 20 * scale, y + 50 * scale)], fill=(196, 120, 84))
    rnd = random.Random(int(x))
    for _ in range(14):
        ang = rnd.uniform(-1.3, 1.3)
        lx = x + ang * 60 * scale
        ly = y - rnd.uniform(40, 120) * scale
        d.ellipse([lx - 18 * scale, ly - 10 * scale, lx + 18 * scale, ly + 10 * scale], fill=rnd.choice([(74, 128, 78), (92, 150, 92), (60, 110, 66)]))
        d.line([(x, y), (lx, ly)], fill=(70, 110, 70), width=2)


def framed_art(d, x0, y0, x1, y1, palette):
    d.rectangle([x0, y0, x1, y1], fill=(60, 50, 40))
    d.rectangle([x0 + 10, y0 + 10, x1 - 10, y1 - 10], fill=(244, 240, 232))
    w = (x1 - x0 - 40)
    for i, c in enumerate(palette):
        d.ellipse([x0 + 20 + i * w / len(palette) * 0.8, y0 + 30 + i * 12,
                   x0 + 20 + i * w / len(palette) * 0.8 + w * 0.45, y0 + 30 + i * 12 + (y1 - y0) * 0.45], fill=c)


# ---------------------------------------------------------------- living room
def living_before():
    img, d = before_room(11)
    boxes(d, 11)
    # sagging old sofa
    d.rectangle([440, 470, 760, 560], fill=(110, 96, 70))
    d.rectangle([440, 430, 760, 480], fill=(98, 86, 62))
    d.line([(520, 480), (560, 520), (640, 500)], fill=(70, 60, 44), width=4)
    return finish_before(img, 11)


def living_after():
    img = new_canvas()
    d = ImageDraw.Draw(img)
    wall = (232, 226, 214)
    shell(d, wall, (222, 214, 200), (246, 243, 236), (186, 140, 96))
    floor_planks(d, (186, 140, 96), (158, 114, 74))
    d.rectangle([BX0, BY0, 470, BY1], fill=(124, 146, 132))  # sage accent wall
    window(img, d, 660, 210, 860, 430, (250, 250, 248))
    d = ImageDraw.Draw(img)
    framed_art(d, 330, 240, 450, 360, [(214, 150, 100), (120, 150, 136), (60, 80, 110)])
    shadow(img, [260, 690, 960, 790], 80, 20)
    d = ImageDraw.Draw(img)
    d.polygon([(250, 700), (950, 700), (1040, 790), (160, 790)], fill=(214, 196, 170))  # rug
    d.polygon([(280, 712), (920, 712), (1000, 778), (200, 778)], outline=(176, 150, 120), width=4)
    # sofa
    shadow(img, [300, 560, 820, 640], 90, 16)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([320, 450, 800, 520], 18, fill=(62, 84, 112))
    d.rounded_rectangle([300, 500, 820, 600], 18, fill=(72, 96, 126))
    d.rounded_rectangle([290, 470, 350, 610], 16, fill=(58, 80, 106))
    d.rounded_rectangle([770, 470, 830, 610], 16, fill=(58, 80, 106))
    for cx, col in [(400, (226, 168, 98)), (720, (236, 222, 196))]:
        d.rounded_rectangle([cx - 45, 460, cx + 45, 530], 14, fill=col)
    # coffee table
    shadow(img, [430, 690, 770, 740], 90, 10)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([420, 650, 780, 680], 10, fill=(120, 82, 52))
    d.rectangle([440, 680, 452, 725], fill=(90, 60, 40))
    d.rectangle([748, 680, 760, 725], fill=(90, 60, 40))
    d.ellipse([560, 630, 620, 656], fill=(240, 236, 228))
    # floor lamp
    d.line([(930, 420), (930, 640)], fill=(50, 50, 50), width=5)
    soft(img, lambda l: l.ellipse([860, 330, 1000, 470], fill=(255, 230, 170, 90)), 25)
    d = ImageDraw.Draw(img)
    d.polygon([(895, 380), (965, 380), (980, 430), (880, 430)], fill=(246, 236, 214))
    d.ellipse([905, 632, 955, 648], fill=(50, 50, 50))
    plant(d, 150, 620, 1.3)
    return finish_after(img, 12)


# ---------------------------------------------------------------- kitchen
def kitchen_before():
    img, d = before_room(21, wall=(184, 176, 150), floor=(120, 112, 92))
    # tired laminate cabinets, one door hanging off
    d.rectangle([BX0, 420, BX1, BY1], fill=(150, 120, 80))
    d.rectangle([BX0, 410, BX1, 425], fill=(170, 160, 140))
    for i in range(6):
        x = BX0 + 10 + i * 98
        d.rectangle([x, 435, x + 88, 550], outline=(110, 86, 56), width=3)
    d.polygon([(BX0 + 206, 438), (BX0 + 290, 448), (BX0 + 280, 560), (BX0 + 200, 552)], fill=(140, 110, 72), outline=(100, 76, 48))
    d.rectangle([BX0, 200, BX0 + 260, 320], fill=(150, 120, 80), outline=(110, 86, 56), width=3)
    d.rectangle([680, 440, 760, 470], fill=(130, 130, 128))  # old sink
    boxes(d, 21)
    return finish_before(img, 21)


def kitchen_after():
    img = new_canvas()
    d = ImageDraw.Draw(img)
    shell(d, (240, 238, 232), (230, 227, 220), (248, 247, 244), (210, 206, 198))
    floor_tiles(d, (188, 184, 176))
    # subway tile backsplash
    for y in range(330, 420, 18):
        off = 0 if (y // 18) % 2 else 22
        for x in range(BX0 - off, BX1, 44):
            d.rectangle([max(BX0, x), y, min(BX1, x + 42), y + 16], fill=(250, 250, 250), outline=(220, 220, 216))
    # upper + lower cabinets (deep green), brass handles
    green = (52, 86, 74)
    d.rectangle([BX0, 190, 560, 320], fill=green)
    d.rectangle([BX0, 420, BX1, BY1], fill=green)
    d.rectangle([BX0 - 4, 410, BX1 + 4, 426], fill=(236, 232, 224))  # stone counter
    for i in range(6):
        x = BX0 + 10 + i * 98
        d.rectangle([x, 436, x + 88, 548], outline=(40, 70, 60), width=3)
        d.rectangle([x + 36, 450, x + 52, 456], fill=(200, 164, 90))
    for i in range(3):
        x = BX0 + 10 + i * 86
        d.rectangle([x, 200, x + 78, 312], outline=(40, 70, 60), width=3)
    window(img, d, 620, 190, 860, 320, (250, 250, 248))
    d = ImageDraw.Draw(img)
    d.rectangle([690, 404, 790, 412], fill=(180, 180, 184))  # sink
    d.line([(740, 404), (740, 370), (760, 370)], fill=(200, 164, 90), width=5)
    plant(d, 600, 380, 0.5)
    # island with stools
    shadow(img, [220, 720, 980, 790], 90, 18)
    d = ImageDraw.Draw(img)
    d.rectangle([260, 600, 940, 740], fill=(240, 236, 228))
    d.rectangle([240, 580, 960, 606], fill=(226, 222, 212))
    for i in range(7):
        d.line([(260 + i * 97, 606), (260 + i * 97, 740)], fill=(220, 214, 204), width=2)
    for sx in (360, 600, 840):
        d.ellipse([sx - 40, 640, sx + 40, 664], fill=(150, 100, 64))
        d.line([(sx - 28, 660), (sx - 34, 780)], fill=(40, 40, 40), width=5)
        d.line([(sx + 28, 660), (sx + 34, 780)], fill=(40, 40, 40), width=5)
    # pendants
    for px in (420, 600, 780):
        d.line([(px, 0), (px, 200)], fill=(40, 40, 40), width=2)
        soft(img, lambda l, px=px: l.ellipse([px - 80, 180, px + 80, 320], fill=(255, 230, 170, 70)), 25)
        d = ImageDraw.Draw(img)
        d.polygon([(px - 30, 230), (px + 30, 230), (px + 14, 200), (px - 14, 200)], fill=(200, 164, 90))
    return finish_after(img, 22)


# ---------------------------------------------------------------- bedroom
def bedroom_before():
    img, d = before_room(31, wall=(170, 166, 150), floor=(110, 100, 86))
    # bare mattress on floor
    d.polygon([(380, 600), (820, 600), (900, 700), (300, 700)], fill=(206, 200, 180))
    d.polygon([(300, 700), (900, 700), (900, 730), (300, 730)], fill=(170, 164, 146))
    rnd = random.Random(31)
    for _ in range(4):
        x, y = rnd.uniform(380, 800), rnd.uniform(615, 690)
        d.ellipse([x - 30, y - 12, x + 30, y + 12], fill=(186, 170, 130))
    boxes(d, 31)
    return finish_before(img, 31)


def bedroom_after():
    img = new_canvas()
    d = ImageDraw.Draw(img)
    shell(d, (214, 200, 196), (206, 192, 188), (244, 240, 236), (176, 134, 94))
    floor_planks(d, (176, 134, 94), (150, 110, 74))
    window(img, d, 700, 210, 870, 420, (250, 250, 248))
    d = ImageDraw.Draw(img)
    # curtains
    d.polygon([(680, 190), (712, 190), (720, 440), (676, 440)], fill=(236, 226, 210))
    d.polygon([(858, 190), (890, 190), (896, 440), (852, 440)], fill=(236, 226, 210))
    framed_art(d, 400, 230, 560, 330, [(180, 120, 110), (230, 196, 150)])
    # rug
    d.polygon([(220, 680), (980, 680), (1080, 790), (120, 790)], fill=(232, 222, 206))
    # bed
    shadow(img, [260, 640, 900, 760], 90, 20)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([330, 370, 830, 470], 20, fill=(120, 86, 64))  # headboard
    d.rectangle([310, 470, 850, 560], fill=(248, 246, 242))
    d.polygon([(310, 560), (850, 560), (920, 700), (240, 700)], fill=(250, 248, 244))
    d.polygon([(260, 640), (900, 640), (920, 700), (240, 700)], fill=(146, 108, 112))  # throw
    for px in (430, 730):
        d.rounded_rectangle([px - 90, 440, px + 90, 510], 18, fill=(255, 255, 255), outline=(226, 222, 216), width=2)
    d.rounded_rectangle([520, 470, 640, 520], 14, fill=(146, 108, 112))
    # nightstands + lamps
    for nx in (230, 930):
        d.rectangle([nx - 50, 500, nx + 50, 600], fill=(120, 86, 64))
        d.line([(nx - 50, 550), (nx + 50, 550)], fill=(96, 68, 50), width=3)
        soft(img, lambda l, nx=nx: l.ellipse([nx - 90, 360, nx + 90, 520], fill=(255, 226, 160, 90)), 25)
        d = ImageDraw.Draw(img)
        d.line([(nx, 460), (nx, 500)], fill=(60, 60, 60), width=4)
        d.polygon([(nx - 26, 420), (nx + 26, 420), (nx + 36, 462), (nx - 36, 462)], fill=(246, 234, 210))
    plant(d, 1080, 640, 1.1)
    return finish_after(img, 32)


ROOMS = {
    "living-room": (living_before, living_after),
    "kitchen": (kitchen_before, kitchen_after),
    "bedroom": (bedroom_before, bedroom_after),
}


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "demo-photos"
    out.mkdir(parents=True, exist_ok=True)
    for name, (before, after) in ROOMS.items():
        for label, fn in (("before", before), ("after", after)):
            path = out / f"{name}-{label}.png"
            fn().convert("RGB").save(path, optimize=True)
            print(path, path.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    main()
