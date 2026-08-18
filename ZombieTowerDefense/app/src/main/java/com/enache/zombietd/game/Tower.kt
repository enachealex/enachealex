package com.enache.zombietd.game

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TowerType(
    val label: String,
    val blurb: String,
    val cost: Int,
    val baseRange: Float,
    val baseDamage: Float,
    val baseFireRate: Float,   // shots per second
    val projectileSpeed: Float,
    val color: Int
) {
    RIFLE("Rifle", "Fast", 100, 270f, 22f, 2.4f, 1100f, 0xFFFBC02D.toInt()),
    SNIPER("Sniper", "Long range", 250, 540f, 95f, 0.55f, 1700f, 0xFF90A4AE.toInt()),
    FROST("Frost", "Slows", 150, 230f, 8f, 1.1f, 900f, 0xFF4FC3F7.toInt()),
    FLAME("Flame", "Burns", 200, 190f, 7f, 7f, 850f, 0xFFFF7043.toInt())
}

class Tower(val type: TowerType, val col: Int, val row: Int) {
    companion object {
        const val MAX_LEVEL = 3
    }

    val pos: PointF = GameMap.cellCenter(col, row)
    var level = 1
        private set
    var invested = type.cost
        private set
    var angle = (-Math.PI / 2).toFloat()
        private set
    private var cooldown = 0f

    val damage get() = type.baseDamage * 1.35f.pow(level - 1)
    val range get() = type.baseRange * 1.12f.pow(level - 1)
    val fireRate get() = type.baseFireRate * 1.15f.pow(level - 1)
    val upgradeCost get() = (type.cost * 0.8f * level).roundToInt()
    val sellValue get() = (invested * 0.7f).roundToInt()
    val isMaxLevel get() = level >= MAX_LEVEL

    fun upgrade() {
        invested += upgradeCost
        level++
    }

    fun update(dt: Float, zombies: List<Zombie>, projectiles: MutableList<Projectile>) {
        cooldown -= dt

        var best: Zombie? = null
        var bestProgress = -Float.MAX_VALUE
        val r = range
        for (z in zombies) {
            if (!z.alive) continue
            val d = hypot(z.pos.x - pos.x, z.pos.y - pos.y)
            if (d <= r) {
                val p = z.progress()
                if (p > bestProgress) {
                    bestProgress = p
                    best = z
                }
            }
        }
        val target = best ?: return

        angle = atan2(target.pos.y - pos.y, target.pos.x - pos.x)
        if (cooldown <= 0f) {
            cooldown = 1f / fireRate
            val muzzleX = pos.x + cos(angle) * 34f
            val muzzleY = pos.y + sin(angle) * 34f
            projectiles.add(Projectile(muzzleX, muzzleY, target, type.projectileSpeed, damage, type))
        }
    }
}
