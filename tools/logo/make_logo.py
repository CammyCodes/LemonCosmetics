"""LemonCosmetics' logo as 32x32 pixel art: the mod icon, the GitHub logo and the README banner.

A glossy lemon wearing a little gold crown, with a leaf and sparkles: "the
server's best looks, on you". LemonCloud's own colours
(lemon yellow on sky blue), drawn on a 32x32 grid from shapes, supersampled per
pixel and snapped to a small palette so every pixel is crisp, the same way
Nylah's face is drawn.

Outputs:
  src/main/resources/assets/lemoncosmetics/icon.png   128 x 128 (the mod icon)
  docs/logo.png                                       512 x 512 (GitHub / social)
  docs/banner.png                                     1280 x 400 (README header)
  docs/logo-32.png                                    the raw 32 x 32
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

C = {
    "K": (40, 34, 30),      # outline
    "Y": (255, 214, 64),    # lemon
    "y": (240, 178, 30),    # lemon shade
    "o": (214, 140, 22),    # lemon deep shade
    "H": (255, 246, 196),   # highlight
    "W": (255, 255, 255),   # sparkle
    "G": (110, 190, 70),    # leaf
    "g": (64, 140, 52),     # leaf dark
    "b": (120, 84, 50),     # stem / handle
    "B": (84, 58, 36),      # handle dark
    "D": (110, 226, 236),   # diamond head
    "d": (52, 160, 190),    # diamond dark
    "C": (255, 196, 40),    # crown gold
    "c": (214, 128, 20),    # crown dark
    "R": (232, 70, 96),     # crown jewel
    "S": (90, 180, 240),    # sky
}

N = 32


def ellipse(x, y, cx, cy, rx, ry):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def lemon_hh(x):
    """Half-height of the lemon at column x (0 outside): an oval with pointed nubs."""
    u = (x - 16.0) / 12.6
    if abs(u) > 1.06:
        return 0.0
    hh = 8.4 * max(0.0, 1 - abs(u) ** 2.3) ** 0.5
    return max(hh, 1.2 if abs(u) <= 1.06 else 0.0)


def in_lemon(x, y):
    hh = lemon_hh(x)
    return hh > 0 and abs(y - 19.5) <= hh


def sample(x, y):
    # Sparkles: four-point stars.
    for cx, cy, r in ((27.5, 4.5, 2.6), (4.5, 7.5, 1.9), (29.0, 27.5, 1.4)):
        if (abs(x - cx) < 0.5 and abs(y - cy) < r) or (abs(y - cy) < 0.5 and abs(x - cx) < r):
            return "W"
    if in_lemon(x, y):
        # Highlight upper left, shade toward the bottom and the right tip.
        if ellipse(x, y, 10.5, 15.6, 3.6, 1.5):
            return "H"
        dy = y - 19.5
        hh = lemon_hh(x)
        t = dy / max(hh, 1e-3)
        if t > 0.62 or (x > 25.5 and t > 0.1):
            return "o"
        if t > 0.18 or x > 26.5:
            return "y"
        return "Y"
    # Crown perched on top, left of centre.
    if 10.0 <= y < 12.0 and 8.0 <= x < 17.0:
        return "c" if y >= 11.0 else "C"
    if 6.0 <= y < 10.0:
        for cx in (8.5, 12.5, 16.5):
            if abs(x - cx) <= (y - 6.0) * 0.5 + 0.5:
                if abs(x - 12.5) < 0.6 and 7.6 < y < 8.8:
                    return "R"
                return "C"
    # Stem and leaf at the top right.
    if 20.0 <= x < 21.0 and 9.0 <= y < 12.0:
        return "b"
    a = math.radians(-28)
    lx, ly = x - 24.2, y - 8.6
    lu = lx * math.cos(a) - ly * math.sin(a)
    lv = lx * math.sin(a) + ly * math.cos(a)
    if (lu / 4.2) ** 2 + (lv / 1.9) ** 2 <= 1.0:
        return "g" if lv > 0.45 else "G"
    return "."


def pixel(px, py):
    """The palette letter for one pixel (a 4x4 supersample vote), or None for transparent."""
    votes = {}
    for sy in range(4):
        for sx in range(4):
            k = sample(px + (sx + 0.5) / 4, py + (sy + 0.5) / 4)
            votes[k] = votes.get(k, 0) + 1
    k = max(votes, key=votes.get)
    return None if k == "." else k


def grid():
    return [[pixel(x, y) for x in range(N)] for y in range(N)]


def outline(g):
    """A dark rim around every drawn shape (crown, pick, leaf) so it reads small."""
    out = [row[:] for row in g]
    for y in range(N):
        for x in range(N):
            if g[y][x] is None:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < N and 0 <= ny < N and g[ny][nx] not in (None, "W", "K"):
                        out[y][x] = "K"
                        break
    return out


def to_image(g, scale, bg=None):
    img = Image.new("RGBA", (N * scale, N * scale), bg or (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for y in range(N):
        for x in range(N):
            k = g[y][x]
            if k:
                d.rectangle([x * scale, y * scale, (x + 1) * scale - 1, (y + 1) * scale - 1], fill=C[k] + (255,))
    return img


# A 5x7 pixel font for the wordmark (just the letters needed).
FONT = {
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
}


def word(d, text, x, y, px, colour, shadow):
    for ch in text:
        rows = FONT[ch]
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == "1":
                    d.rectangle([x + rx * px + px // 3, y + ry * px + px // 3, x + (rx + 1) * px - 1 + px // 3,
                                 y + (ry + 1) * px - 1 + px // 3], fill=shadow)
                    d.rectangle([x + rx * px, y + ry * px, x + (rx + 1) * px - 1, y + (ry + 1) * px - 1], fill=colour)
        x += 6 * px
    return x


def sky(w, h):
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    top, bottom = (86, 176, 238), (150, 212, 250)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    # Soft pixel clouds.
    for cx, cy, s in ((0.12, 0.82, 1.0), (0.86, 0.2, 0.8), (0.62, 0.9, 1.2), (0.33, 0.12, 0.7)):
        bx, by = int(cx * w), int(cy * h)
        u = max(6, int(h / 40 * s))
        for ox, oy, rw in ((0, 0, 9), (2, -2, 5), (5, -1, 4)):
            d.rectangle([bx + ox * u, by + oy * u, bx + (ox + rw) * u, by + (oy + 2) * u], fill=(236, 246, 255, 120))
    return img


def main():
    g = outline(grid())
    os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
    icon_dir = os.path.join(ROOT, "src", "main", "resources", "assets", "lemoncosmetics")
    os.makedirs(icon_dir, exist_ok=True)

    to_image(g, 1).save(os.path.join(ROOT, "docs", "logo-32.png"))
    to_image(g, 4).save(os.path.join(icon_dir, "icon.png"))

    # GitHub logo: the lemon on a rounded sky tile.
    logo = sky(512, 512)
    mask = Image.new("L", (512, 512), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 511, 511], radius=96, fill=255)
    lemon_img = to_image(g, 14)
    logo.alpha_composite(lemon_img, ((512 - lemon_img.width) // 2, (512 - lemon_img.height) // 2 + 6))
    logo.putalpha(mask)
    logo.save(os.path.join(ROOT, "docs", "logo.png"))

    # Banner: lemon left, the wordmark right, a lemon underline, like Nylah's.
    banner = sky(1280, 400)
    big = to_image(g, 10)
    banner.alpha_composite(big, (90, (400 - big.height) // 2))
    d = ImageDraw.Draw(banner)
    shadow = (40, 34, 30, 255)
    x = word(d, "LEMON", 470, 92, 13, (255, 214, 64, 255), shadow)
    word(d, "COSMETICS", 470, 212, 13, (255, 255, 255, 255), shadow)
    d.rectangle([470, 330, 470 + 9 * 6 * 13 - 13, 339], fill=(255, 214, 64, 255))
    banner.save(os.path.join(ROOT, "docs", "banner.png"))
    print("wrote icon.png, logo.png, banner.png, logo-32.png")


if __name__ == "__main__":
    main()
