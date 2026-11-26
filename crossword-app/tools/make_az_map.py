"""Make ui/AzMapShape.kt: Azerbaijan's outline for the road map, from Natural Earth (public domain).

    python tools/make_az_map.py

Source: content/azerbaijan-ne10m.json, the AZE geometry of Natural Earth 1:10m Admin 0 countries
(naturalearthdata.com, public domain). The outline is simplified (Douglas-Peucker) to a few
hundred points so it draws instantly on weak phones. Tiny islands are left out.
"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', 'content', 'azerbaijan-ne10m.json')
OUT = os.path.join(HERE, '..', 'app', 'src', 'main', 'java', 'com', 'aaideas', 'krossvord', 'ui', 'AzMapShape.kt')
TOL = 0.012

def dp(pts, tol):
    if len(pts) < 3: return pts
    a, b = pts[0], pts[-1]
    def dist(p):
        (x1, y1), (x2, y2), (x, y) = a, b, p
        dx, dy = x2 - x1, y2 - y1
        if dx == dy == 0: return ((x - x1) ** 2 + (y - y1) ** 2) ** .5
        return abs(dy * x - dx * y + x2 * y1 - y2 * x1) / (dx * dx + dy * dy) ** .5
    i, m = max(((i, dist(p)) for i, p in enumerate(pts[1:-1], 1)), key=lambda t: t[1])
    if m <= tol: return [a, b]
    return dp(pts[:i + 1], tol)[:-1] + dp(pts[i:], tol)

g = json.load(open(SRC))
polys = []
for poly in g['coordinates']:
    ring = poly[0]
    if len(ring) < 20: continue
    # Split the closed ring in two so Douglas-Peucker has distinct ends.
    h = len(ring) // 2
    s = dp(ring[:h + 1], TOL)[:-1] + dp(ring[h:], TOL)
    polys.append(s)
lines = []
for s in polys:
    nums = ', '.join(f'{x:.3f}f, {y:.3f}f' for x, y in s)
    lines.append(f'        floatArrayOf({nums}),')
with open(OUT, 'w', encoding='utf-8') as f:
    f.write('''package com.aaideas.krossvord.ui

/**
 * Azerbaijan's outline (with Nakhchivan) as lon/lat pairs, made by tools/make_az_map.py from
 * Natural Earth 1:10m Admin 0 countries (public domain, naturalearthdata.com). Do not edit by hand.
 */
object AzMapShape {
    const val MIN_LON = 44.7f
    const val MAX_LON = 50.7f
    const val MIN_LAT = 38.35f
    const val MAX_LAT = 41.95f
    val POLYGONS: List<FloatArray> = listOf(
''' + '\n'.join(lines) + '''
    )
}
''')
print(OUT, [len(s) for s in polys])
