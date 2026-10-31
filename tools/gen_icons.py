#!/usr/bin/env python3
"""Генератор иконок Moon/MoonMC без внешних зависимостей.

Читает PNG-исходник (RGBA), вырезает белый фон по заливке от краёв,
подгоняет арт под безопасную зону adaptive-иконки и раскладывает mipmap-*.

  python3 gen_icons.py --src icon.png --out app/src/main/res \
      --monochrome-res drawable/ic_launcher_monochrome [--legacy-bg white|none]
"""
import argparse
import os
import struct
import sys
import zlib
from collections import deque

# ------------------------------------------------------------------ PNG чтение


def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "не PNG"
    pos, idat = 8, b""
    w = h = bit = ctype = inter = None
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b"IHDR":
            w, h, bit, ctype, _comp, _filt, inter = struct.unpack(">IIBBBBB", body)
        elif typ == b"IDAT":
            idat += body
        elif typ == b"IEND":
            break
    assert bit == 8 and inter == 0 and ctype in (2, 6), (bit, ctype, inter)
    ch = 4 if ctype == 6 else 3
    raw = zlib.decompress(idat)
    stride = w * ch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y * stride:(y + 1) * stride] = line
        prev = line
    if ch == 3:
        rgba = bytearray(w * h * 4)
        for i in range(w * h):
            rgba[i * 4:i * 4 + 3] = out[i * 3:i * 3 + 3]
            rgba[i * 4 + 3] = 255
        out = rgba
    return w, h, out


def write_png(path, w, h, rgba):
    raw = bytearray()
    stride = w * 4
    for y in range(h):
        raw.append(0)
        raw += rgba[y * stride:(y + 1) * stride]
    def chunk(typ, body):
        return (struct.pack(">I", len(body)) + typ + body
                + struct.pack(">I", zlib.crc32(typ + body) & 0xFFFFFFFF))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    open(path, "wb").write(png)


# ------------------------------------------------------------------ обработка


def drop_white_bg(w, h, px, tol=6):
    """Убирает белый фон заливкой от краёв: внутренние белые области не трогаем."""
    limit = 255 - tol
    seen = bytearray(w * h)
    q = deque()

    def is_white(i):
        o = i * 4
        return px[o] >= limit and px[o + 1] >= limit and px[o + 2] >= limit and px[o + 3] > 0

    for x in range(w):
        for y in (0, h - 1):
            i = y * w + x
            if is_white(i) and not seen[i]:
                seen[i] = 1
                q.append(i)
    for y in range(h):
        for x in (0, w - 1):
            i = y * w + x
            if is_white(i) and not seen[i]:
                seen[i] = 1
                q.append(i)
    while q:
        i = q.popleft()
        px[i * 4 + 3] = 0
        x, y = i % w, i // w
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < w and 0 <= ny < h:
                j = ny * w + nx
                if not seen[j] and is_white(j):
                    seen[j] = 1
                    q.append(j)


def bbox(w, h, px, min_alpha=24):
    x0, y0, x1, y1 = w, h, -1, -1
    for y in range(h):
        row = y * w
        for x in range(w):
            if px[(row + x) * 4 + 3] >= min_alpha:
                if x < x0:
                    x0 = x
                if x > x1:
                    x1 = x
                if y < y0:
                    y0 = y
                if y > y1:
                    y1 = y
    return x0, y0, x1 + 1, y1 + 1


def resize_area(w, h, px, nw, nh):
    """Уменьшение усреднением по площади (premultiplied alpha)."""
    if nw == w and nh == h:
        return w, h, px
    out = bytearray(nw * nh * 4)
    sx = w / nw
    sy = h / nh
    for y in range(nh):
        y0, y1 = int(y * sy), max(int(y * sy) + 1, int((y + 1) * sy))
        for x in range(nw):
            x0, x1 = int(x * sx), max(int(x * sx) + 1, int((x + 1) * sx))
            sa = sr = sg = sb = 0
            n = 0
            for yy in range(y0, min(y1, h)):
                base = yy * w
                for xx in range(x0, min(x1, w)):
                    o = (base + xx) * 4
                    a = px[o + 3]
                    sa += a
                    sr += px[o] * a
                    sg += px[o + 1] * a
                    sb += px[o + 2] * a
                    n += 1
            if not n:
                continue
            o = (y * nw + x) * 4
            if sa:
                out[o] = min(255, sr // sa)
                out[o + 1] = min(255, sg // sa)
                out[o + 2] = min(255, sb // sa)
            out[o + 3] = min(255, sa // n)
    return nw, nh, out


def crop(w, h, px, x0, y0, x1, y1):
    cw, chh = x1 - x0, y1 - y0
    out = bytearray(cw * chh * 4)
    for y in range(chh):
        src = ((y + y0) * w + x0) * 4
        out[y * cw * 4:(y + 1) * cw * 4] = px[src:src + cw * 4]
    return cw, chh, out


def cover(nw, nh, src_w, src_h, src, bg=None, scale=0.72, colorize=None):
    """Вставляет арт по центру канвы с заданной долей заполнения."""
    out = bytearray(nw * nh * 4)
    if bg:
        r, g, b, a = bg
        for i in range(nw * nh):
            out[i * 4] = r
            out[i * 4 + 1] = g
            out[i * 4 + 2] = b
            out[i * 4 + 3] = a
    side = int(min(nw, nh) * scale)
    sw = side if src_w >= src_h else max(1, int(side * src_w / src_h))
    sh = side if src_h >= src_w else max(1, int(side * src_h / src_w))
    tw, th, art = resize_area(src_w, src_h, src, sw, sh)
    ox = (nw - tw) // 2
    oy = (nh - th) // 2
    for y in range(th):
        for x in range(tw):
            so = (y * tw + x) * 4
            a = art[so + 3]
            if not a:
                continue
            do = ((y + oy) * nw + (x + ox)) * 4
            if colorize is not None:
                cr, cg, cb = colorize
                out[do] = cr
                out[do + 1] = cg
                out[do + 2] = cb
                out[do + 3] = a
            else:
                if a == 255:
                    out[do] = art[so]
                    out[do + 1] = art[so + 1]
                    out[do + 2] = art[so + 2]
                    out[do + 3] = 255
                else:
                    # alpha-over поверх фона
                    for k in range(3):
                        base = out[do + k]
                        out[do + k] = (art[so + k] * a + base * (255 - a) + 127) // 255
                    out[do + 3] = 255 if out[do + 3] == 255 else a
    return out


def apply_mask(nw, nh, out, kind, radius_ratio=0.22):
    """Скругляет или обрезает кругом готовую иконку (антиалиасинг 3x3)."""
    ss = 3
    for y in range(nh):
        for x in range(nw):
            hit = 0
            for sy in range(ss):
                for sx in range(ss):
                    px = x + (sx + 0.5) / ss
                    py = y + (sy + 0.5) / ss
                    if kind == "circle":
                        cx = cy = nw / 2.0
                        r = nw / 2.0
                        inside = (px - cx) ** 2 + (py - cy) ** 2 <= r * r
                    else:
                        rr = nw * radius_ratio
                        dx = max(rr - px, px - (nw - rr), 0)
                        dy = max(rr - py, py - (nh - rr), 0)
                        inside = dx * dx + dy * dy <= rr * rr
                    if inside:
                        hit += 1
            o = (y * nw + x) * 4
            out[o + 3] = out[o + 3] * hit // (ss * ss)


DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def build(src_path, res_dir, legacy_bg=(255, 255, 255, 255), prefix="ic_launcher", layout="gradle",
          scale_fg=0.42, scale_legacy=0.52):
    w, h, px = read_png(src_path)
    drop_white_bg(w, h, px)
    x0, y0, x1, y1 = bbox(w, h, px)
    art = crop(w, h, px, x0, y0, x1, y1)
    aw, ah, apx = art
    print(f"исходник {w}x{h}, арт {aw}x{ah}")

    for dens, mult in DENSITIES.items():
        d = os.path.join(res_dir, f"mipmap-{dens}")
        fg = int(round(108 * mult))
        out = cover(fg, fg, aw, ah, apx, bg=None, scale=scale_fg)
        mono = cover(fg, fg, aw, ah, apx, bg=None, scale=scale_fg, colorize=(255, 255, 255))
        if layout == "gradle":
            write_png(os.path.join(d, f"{prefix}_foreground.png"), fg, fg, out)
            write_png(os.path.join(d, f"{prefix}_monochrome.png"), fg, fg, mono)
        else:
            # плоская раскладка (android/res как в MargyMC): foreground в drawable-*
            dd = os.path.join(res_dir, f"drawable-{dens}")
            write_png(os.path.join(dd, f"{prefix}_foreground.png"), fg, fg, out)
            write_png(os.path.join(dd, f"{prefix}_monochrome.png"), fg, fg, mono)
        lg = int(round(48 * mult))
        leg = cover(lg, lg, aw, ah, apx, bg=legacy_bg, scale=scale_legacy)
        apply_mask(lg, lg, leg, "round")
        write_png(os.path.join(d, f"{prefix}.png"), lg, lg, leg)
        if layout == "gradle":
            rnd = cover(lg, lg, aw, ah, apx, bg=legacy_bg, scale=scale_legacy - 0.04)
            apply_mask(lg, lg, rnd, "circle")
            write_png(os.path.join(d, f"{prefix}_round.png"), lg, lg, rnd)
        print(f"  {dens}: {fg}px adaptive, {lg}px legacy")


def write_adaptive_xml(res_dir, prefix="ic_launcher", bg="@color/ic_launcher_background", layout="gradle"):
    if layout == "gradle":
        fg_ref = f"@mipmap/{prefix}_foreground"
        mono_ref = f"@mipmap/{prefix}_monochrome"
        names = [prefix, f"{prefix}_round"]
    else:
        fg_ref = f"@drawable/{prefix}_foreground"
        mono_ref = f"@drawable/{prefix}_monochrome"
        names = [prefix]
    v26 = os.path.join(res_dir, "mipmap-anydpi-v26")
    v33 = os.path.join(res_dir, "mipmap-anydpi-v33")
    os.makedirs(v26, exist_ok=True)
    os.makedirs(v33, exist_ok=True)
    body = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="{bg}" />\n'
            f'    <foreground android:drawable="{fg_ref}" />\n')
    for name in names:
        open(os.path.join(v26, f"{name}.xml"), "w").write(body + "</adaptive-icon>\n")
        open(os.path.join(v33, f"{name}.xml"), "w").write(
            body + f'    <monochrome android:drawable="{mono_ref}" />\n</adaptive-icon>\n'
        )
    print(f"  adaptive xml: v26 + v33 (monochrome, {layout})")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True, help="каталог res")
    ap.add_argument("--prefix", default="ic_launcher")
    ap.add_argument("--layout", choices=["gradle", "flat"], default="gradle")
    ap.add_argument("--scale-fg", type=float, default=0.42, help="доля канвы 108dp под арт")
    ap.add_argument("--scale-legacy", type=float, default=0.52)
    ap.add_argument("--no-legacy-bg", action="store_true")
    args = ap.parse_args()
    bg = (0, 0, 0, 0) if args.no_legacy_bg else (255, 255, 255, 255)
    build(args.src, args.out, legacy_bg=bg, prefix=args.prefix, layout=args.layout,
          scale_fg=args.scale_fg, scale_legacy=args.scale_legacy)
    write_adaptive_xml(args.out, args.prefix, layout=args.layout)


if __name__ == "__main__":
    sys.exit(main())
