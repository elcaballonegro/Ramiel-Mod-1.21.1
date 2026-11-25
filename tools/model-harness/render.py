import json, math, sys
from PIL import Image, ImageDraw, ImageFont

def qrot(q, v):
    x, y, z, w = q
    vx, vy, vz = v
    # v' = v + 2w(q x v) + 2 q x (q x v)
    tx = 2 * (y * vz - z * vy); ty = 2 * (z * vx - x * vz); tz = 2 * (x * vy - y * vx)
    return (vx + w * tx + (y * tz - z * ty), vy + w * ty + (z * tx - x * tz), vz + w * tz + (x * ty - y * tx))

def brick_faces(b):
    x, y, z, qx, qy, qz, qw, h, bw, bd, tw, td, r, g, bl, glow, alpha = b
    hy = h / 2
    loc = [(-bw, -hy, -bd), (bw, -hy, -bd), (bw, -hy, bd), (-bw, -hy, bd),
           (-tw, hy, -td), (tw, hy, -td), (tw, hy, td), (-tw, hy, td)]
    pts = []
    for v in loc:
        rx, ry, rz = qrot((qx, qy, qz, qw), v)
        pts.append((rx + x, ry + y, rz + z))
    idx = [(0, 1, 2, 3), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    return [[pts[i] for i in f] for f in idx], (r, g, bl, glow, alpha)

def normal(poly):
    ax, ay, az = [poly[1][i] - poly[0][i] for i in range(3)]
    bx, by, bz = [poly[2][i] - poly[0][i] for i in range(3)]
    n = (ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
    l = math.sqrt(sum(c * c for c in n)) or 1
    return tuple(c / l for c in n)

def render(bricks, size=360, yaw=25, pitch=12, dist=5.2, fov=38, bg=(6, 10, 26), label=None, scale_to=None):
    img = Image.new("RGB", (size, size), bg)
    # soft vertical gradient so it reads like a night sky
    d0 = ImageDraw.Draw(img)
    for yy in range(size):
        k = yy / size
        d0.line([(0, yy), (size, yy)], fill=(int(bg[0] + 8 * k), int(bg[1] + 18 * k), int(bg[2] + 22 * k)))
    d = ImageDraw.Draw(img, "RGBA")
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    f = 1 / math.tan(math.radians(fov) / 2)
    light = (0.4, 0.8, 0.45)
    ll = math.sqrt(sum(c * c for c in light)); light = tuple(c / ll for c in light)

    def cam(p):
        x, y, z = p
        x1, z1 = x * cy + z * sy, -x * sy + z * cy          # yaw
        y2, z2 = y * cp - z1 * sp, y * sp + z1 * cp          # pitch
        return (x1, y2, z2 + dist)
    def proj(c):
        zz = max(c[2], 0.1)
        return (size / 2 + c[0] / zz * f * size / 2, size / 2 - c[1] / zz * f * size / 2)

    polys = []
    for b in bricks:
        faces, (r, g, bl, glow, alpha) = brick_faces(b)
        for poly in faces:
            n = normal(poly)
            nc = normal([cam(p) for p in poly])
            cpts = [cam(p) for p in poly]
            depth = sum(c[2] for c in cpts) / 4
            polys.append((depth, poly, cpts, n, (r, g, bl, glow, alpha)))
    polys.sort(key=lambda t: -t[0])
    for depth, poly, cpts, n, (r, g, bl, glow, alpha) in polys:
        # shading uses the world normal; flip so it faces the viewer-ish side for two-sided look
        lam = abs(n[0] * light[0] + n[1] * light[1] + n[2] * light[2])
        k = 0.30 + 0.70 * lam
        col = [min(1.0, c * k + glow * 0.55 * c + 0.06) for c in (r, g, bl)]
        a = int(255 * min(1.0, alpha * 0.93))
        pts2 = [proj(c) for c in cpts]
        fill = (int(col[0] * 255), int(col[1] * 255), int(col[2] * 255), a)
        edge = (min(255, fill[0] + 60), min(255, fill[1] + 60), min(255, fill[2] + 60), 150)
        d.polygon(pts2, fill=fill, outline=edge)
    if label:
        d.text((6, 4), label, fill=(255, 190, 90, 255))
    return img

def sheet(path_in, path_out, cols=4, size=300, **kw):
    rows = [json.loads(l) for l in open(path_in)]
    n = len(rows)
    rws = math.ceil(n / cols)
    out = Image.new("RGB", (cols * size, rws * size), (0, 0, 0))
    for i, row in enumerate(rows):
        im = render(row["bricks"], size=size, label=row["tag"], **kw)
        out.paste(im, ((i % cols) * size, (i // cols) * size))
    out.save(path_out)
    print("wrote", path_out, n, "frames")

if __name__ == "__main__":
    kw = {}
    for a in sys.argv[4:]:
        k, v = a.split("=")
        kw[k] = float(v)
    if "size" in kw: kw["size"] = int(kw["size"])
    sheet(sys.argv[1], sys.argv[2], cols=int(sys.argv[3]), **kw)
