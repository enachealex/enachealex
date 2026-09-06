package com.enache.zombietd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Paints a map as a smooth landscape (no visible tiles) into a cached bitmap:
 * mottled grass, a rounded dirt trail with wheel ruts, a river with a plank
 * bridge, trees, pines, rocks and buildings, and scenery in any spare strip
 * below the playfield.
 */
class Terrain(val map: GameMap, val width: Int, val height: Int) {
    val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val tile = GameMap.TILE
    private val gridBottom = GameMap.TOP + GameMap.ROWS * tile

    init {
        render(Canvas(bitmap))
    }

    private fun render(c: Canvas) {
        val rnd = Random(map.name.hashCode() + 17)

        // --- grass: base plus soft mottling and tufts ---
        c.drawColor(0xFF4F6E3C.toInt())
        val patch = intArrayOf(0xFF587A44.toInt(), 0xFF486637.toInt(), 0xFF5D8149.toInt(), 0xFF446033.toInt())
        repeat(260) {
            val px = rnd.nextFloat() * width
            val py = rnd.nextFloat() * height
            val rx = 30f + rnd.nextFloat() * 90f
            val ry = rx * (0.6f + rnd.nextFloat() * 0.6f)
            fill.color = patch[rnd.nextInt(patch.size)]
            fill.alpha = 70 + rnd.nextInt(60)
            rect.set(px - rx, py - ry, px + rx, py + ry)
            c.drawOval(rect, fill)
        }
        fill.alpha = 255
        stroke.color = 0x66324B25
        stroke.strokeWidth = 2.5f
        repeat(420) {
            val px = rnd.nextFloat() * width
            val py = rnd.nextFloat() * height
            for (i in -1..1) {
                c.drawLine(px + i * 3f, py, px + i * 5f, py - 7f - rnd.nextFloat() * 5f, stroke)
            }
        }

        drawWater(c)
        for (lane in map.paths) drawTrail(c, lane)
        drawBridges(c)
        drawGate(c)
        for (d in map.decor) drawDecor(c, d)
        drawStrip(c, rnd)
    }

    // ---------------------------------------------------------------- water

    private fun drawWater(c: Canvas) {
        if (map.water.isEmpty()) return
        fill.color = 0xFF3A6E8F.toInt()
        for ((col, row) in map.water) {
            c.drawRect(col * tile, GameMap.TOP + row * tile, (col + 1) * tile, GameMap.TOP + (row + 1) * tile, fill)
        }
        // soft shorelines where water meets land
        for ((col, row) in map.water) {
            val left = col * tile
            val top = GameMap.TOP + row * tile
            if ((col to row - 1) !in map.water) {
                fill.color = 0xFF6F9BB5.toInt()
                c.drawRect(left, top, left + tile, top + 7f, fill)
                fill.color = 0xFFB9A57A.toInt()
                c.drawRect(left, top - 6f, left + tile, top, fill)
            }
            if ((col to row + 1) !in map.water) {
                fill.color = 0xFF2E5A76.toInt()
                c.drawRect(left, top + tile - 7f, left + tile, top + tile, fill)
                fill.color = 0xFFB9A57A.toInt()
                c.drawRect(left, top + tile, left + tile, top + tile + 6f, fill)
            }
        }
        // ripples
        stroke.color = 0x55FFFFFF
        stroke.strokeWidth = 3f
        val rows = map.water.map { it.second }.toSet()
        for (row in rows) {
            val cols = map.water.filter { it.second == row }.map { it.first }
            val x0 = cols.min() * tile
            val x1 = (cols.max() + 1) * tile
            for (k in 0 until 3) {
                val baseY = GameMap.TOP + row * tile + 20f + k * 32f
                val path = Path()
                var x = x0 + 8f
                path.moveTo(x, baseY)
                while (x < x1 - 8f) {
                    path.quadTo(x + 10f, baseY - 6f, x + 20f, baseY)
                    path.quadTo(x + 30f, baseY + 6f, x + 40f, baseY)
                    x += 40f
                }
                c.drawPath(path, stroke)
            }
        }
    }

    // ---------------------------------------------------------------- trail

    private fun trailPath(lane: List<PointF>): Path {
        val p = Path()
        p.moveTo(lane[0].x, lane[0].y)
        for (i in 1 until lane.size - 1) {
            val prev = lane[i - 1]
            val cur = lane[i]
            val next = lane[i + 1]
            val inLen = hypot(cur.x - prev.x, cur.y - prev.y)
            val outLen = hypot(next.x - cur.x, next.y - cur.y)
            val r = min(tile * 0.5f, min(inLen, outLen) / 2f)
            val bx = cur.x - (cur.x - prev.x) / inLen * r
            val by = cur.y - (cur.y - prev.y) / inLen * r
            val ax = cur.x + (next.x - cur.x) / outLen * r
            val ay = cur.y + (next.y - cur.y) / outLen * r
            p.lineTo(bx, by)
            p.quadTo(cur.x, cur.y, ax, ay)
        }
        p.lineTo(lane.last().x, lane.last().y)
        return p
    }

    private fun drawTrail(c: Canvas, lane: List<PointF>) {
        val path = trailPath(lane)
        stroke.pathEffect = null
        stroke.color = 0xFF5B4A38.toInt()
        stroke.strokeWidth = tile * 0.9f
        c.drawPath(path, stroke)
        stroke.color = 0xFF8C7457.toInt()
        stroke.strokeWidth = tile * 0.74f
        c.drawPath(path, stroke)
        stroke.color = 0xFF9A8264.toInt()
        stroke.strokeWidth = tile * 0.4f
        c.drawPath(path, stroke)
        // wheel ruts
        stroke.color = 0x55604C39
        stroke.strokeWidth = 5f
        stroke.pathEffect = DashPathEffect(floatArrayOf(26f, 30f), 0f)
        c.drawPath(path, stroke)
        stroke.pathEffect = null
        // pebbles
        val rnd = Random(lane.size * 31 + map.name.length)
        fill.color = 0xFF6E5A45.toInt()
        for ((col, row) in map.pathCells) {
            if (!map.isInside(col, row) || (col to row) in map.bridgeCells) continue
            repeat(3) {
                val px = (col + 0.25f + rnd.nextFloat() * 0.5f) * tile
                val py = GameMap.TOP + (row + 0.25f + rnd.nextFloat() * 0.5f) * tile
                c.drawCircle(px, py, 2f + rnd.nextFloat() * 2f, fill)
            }
        }
    }

    private fun drawBridges(c: Canvas) {
        for ((col, row) in map.bridgeCells) {
            val left = col * tile
            val top = GameMap.TOP + row * tile
            val vertical = (col to row - 1) in map.pathCells || (col to row + 1) in map.pathCells
            fill.color = 0xFF7A5A3C.toInt()
            if (vertical) {
                c.drawRect(left + 12f, top - 4f, left + tile - 12f, top + tile + 4f, fill)
                fill.color = 0xFF5D4630.toInt()
                var y = top + 6f
                while (y < top + tile) {
                    c.drawRect(left + 12f, y, left + tile - 12f, y + 3f, fill)
                    y += 14f
                }
                fill.color = 0xFF4A3826.toInt()
                c.drawRect(left + 8f, top - 4f, left + 14f, top + tile + 4f, fill)
                c.drawRect(left + tile - 14f, top - 4f, left + tile - 8f, top + tile + 4f, fill)
            } else {
                c.drawRect(left - 4f, top + 12f, left + tile + 4f, top + tile - 12f, fill)
                fill.color = 0xFF5D4630.toInt()
                var x = left + 6f
                while (x < left + tile) {
                    c.drawRect(x, top + 12f, x + 3f, top + tile - 12f, fill)
                    x += 14f
                }
                fill.color = 0xFF4A3826.toInt()
                c.drawRect(left - 4f, top + 8f, left + tile + 4f, top + 14f, fill)
                c.drawRect(left - 4f, top + tile - 14f, left + tile + 4f, top + tile - 8f, fill)
            }
        }
    }

    private fun drawGate(c: Canvas) {
        // sandbag line just before the gate on every exit
        for ((col, row) in map.exitCells) {
            val p = GameMap.cellCenter(col, row)
            for (i in -2..2) {
                fill.color = 0xFFB9A57A.toInt()
                c.drawCircle(p.x + i * 16f, p.y - 34f + abs(i) * 2f, 9f, fill)
                fill.color = 0xFF9C8A62.toInt()
                c.drawCircle(p.x + i * 16f + 2f, p.y - 32f + abs(i) * 2f, 5f, fill)
            }
        }
    }

    // ---------------------------------------------------------------- scenery

    private fun drawDecor(c: Canvas, d: Decor) {
        val center = GameMap.cellCenter(d.c, d.r)
        val rnd = Random(d.seed)
        val x = center.x + (rnd.nextFloat() - 0.5f) * 14f
        val y = center.y + (rnd.nextFloat() - 0.5f) * 14f
        when (d.kind) {
            Decor.Kind.TREE -> drawTree(c, x, y, 0.85f + rnd.nextFloat() * 0.3f)
            Decor.Kind.PINE -> drawPine(c, x, y, 0.85f + rnd.nextFloat() * 0.3f)
            Decor.Kind.ROCK -> drawRock(c, x, y, rnd)
            Decor.Kind.HOUSE -> drawHouse(c, x, y, rnd)
        }
    }

    private fun drawTree(c: Canvas, x: Float, y: Float, s: Float) {
        fill.color = 0x55000000
        rect.set(x - 24f * s + 8f, y - 20f * s + 10f, x + 24f * s + 8f, y + 20f * s + 10f)
        c.drawOval(rect, fill)
        fill.color = 0xFF5D4037.toInt()
        c.drawCircle(x, y + 4f, 6f * s, fill)
        fill.color = 0xFF2F5E2A.toInt()
        c.drawCircle(x, y, 24f * s, fill)
        c.drawCircle(x - 12f * s, y + 6f * s, 16f * s, fill)
        c.drawCircle(x + 13f * s, y + 4f * s, 15f * s, fill)
        fill.color = 0xFF3F7A35.toInt()
        c.drawCircle(x - 4f * s, y - 6f * s, 15f * s, fill)
        c.drawCircle(x + 8f * s, y - 2f * s, 10f * s, fill)
        fill.color = 0xFF5A9A48.toInt()
        c.drawCircle(x - 8f * s, y - 10f * s, 6f * s, fill)
    }

    private fun drawPine(c: Canvas, x: Float, y: Float, s: Float) {
        fill.color = 0x55000000
        rect.set(x - 22f * s + 8f, y - 18f * s + 10f, x + 22f * s + 8f, y + 18f * s + 10f)
        c.drawOval(rect, fill)
        val layers = intArrayOf(0xFF244D28.toInt(), 0xFF2F6631.toInt(), 0xFF3E7F3C.toInt(), 0xFF5A9A48.toInt())
        val radii = floatArrayOf(26f, 19f, 12f, 5f)
        for (i in layers.indices) {
            fill.color = layers[i]
            val path = Path()
            val r = radii[i] * s
            for (k in 0 until 16) {
                val a = (k * PI / 8).toFloat()
                val rr = if (k % 2 == 0) r else r * 0.72f
                val px = x + cos(a) * rr
                val py = y + sin(a) * rr
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            c.drawPath(path, fill)
        }
    }

    private fun drawRock(c: Canvas, x: Float, y: Float, rnd: Random) {
        fill.color = 0x55000000
        rect.set(x - 22f + 6f, y - 16f + 8f, x + 22f + 6f, y + 16f + 8f)
        c.drawOval(rect, fill)
        val n = 7
        val radii = FloatArray(n) { 15f + rnd.nextFloat() * 9f }
        fun poly(scale: Float, ox: Float, oy: Float): Path {
            val p = Path()
            for (k in 0 until n) {
                val a = (k * 2 * PI / n).toFloat()
                val px = x + ox + cos(a) * radii[k] * scale
                val py = y + oy + sin(a) * radii[k] * scale * 0.8f
                if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            p.close()
            return p
        }
        fill.color = 0xFF6F6F6A.toInt()
        c.drawPath(poly(1f, 0f, 0f), fill)
        fill.color = 0xFF8E8E88.toInt()
        c.drawPath(poly(0.62f, -3f, -4f), fill)
        fill.color = 0xFFA6A69F.toInt()
        c.drawPath(poly(0.3f, -6f, -7f), fill)
    }

    private fun drawHouse(c: Canvas, x: Float, y: Float, rnd: Random) {
        val w = 74f
        val h = 58f
        fill.color = 0x66000000
        c.drawRoundRect(x - w / 2 + 8f, y - h / 2 + 10f, x + w / 2 + 8f, y + h / 2 + 10f, 6f, 6f, fill)
        fill.color = 0xFF8D8271.toInt()
        c.drawRoundRect(x - w / 2, y - h / 2, x + w / 2, y + h / 2, 4f, 4f, fill)
        // pitched roof, two tones with a ridge
        fill.color = 0xFF6E4A3A.toInt()
        c.drawRect(x - w / 2 + 3f, y - h / 2 + 3f, x + w / 2 - 3f, y, fill)
        fill.color = 0xFF5A3B2E.toInt()
        c.drawRect(x - w / 2 + 3f, y, x + w / 2 - 3f, y + h / 2 - 3f, fill)
        fill.color = 0xFF3E2A22.toInt()
        c.drawRect(x - w / 2 + 3f, y - 1.5f, x + w / 2 - 3f, y + 1.5f, fill)
        // chimney and a skylight
        fill.color = 0xFF55555A.toInt()
        c.drawRect(x + w / 2 - 20f, y - h / 2 + 8f, x + w / 2 - 10f, y - h / 2 + 18f, fill)
        fill.color = 0xFF9FC9E0.toInt()
        c.drawRect(x - w / 2 + 12f, y + 8f, x - w / 2 + 24f, y + 18f, fill)
        if (rnd.nextBoolean()) {
            fill.color = 0xFF4E6B3C.toInt()
            c.drawCircle(x - w / 2 - 10f, y + h / 2 - 6f, 10f, fill)
        }
    }

    /** Scenery in the spare strip between the playfield and the control panel. */
    private fun drawStrip(c: Canvas, rnd: Random) {
        val stripTop = gridBottom + 30f
        val stripBottom = height - 240f - 30f
        if (stripBottom - stripTop < 40f) return
        val n = ((stripBottom - stripTop) / 40f).toInt() + 6
        repeat(n) {
            val x = 40f + rnd.nextFloat() * (width - 80f)
            val y = stripTop + rnd.nextFloat() * (stripBottom - stripTop)
            when (rnd.nextInt(4)) {
                0 -> drawPine(c, x, y, 0.8f + rnd.nextFloat() * 0.4f)
                1 -> drawRock(c, x, y, rnd)
                else -> drawTree(c, x, y, 0.8f + rnd.nextFloat() * 0.4f)
            }
        }
    }
}
