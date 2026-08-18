package com.enache.zombietd.game

import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class ZombieType(
    val label: String,
    val baseHp: Float,
    val speed: Float,      // pixels per second
    val reward: Int,       // money for a kill
    val radius: Float,
    val livesCost: Int     // lives lost if it reaches the exit
) {
    WALKER("Walker", 90f, 95f, 12, 34f, 1),
    RUNNER("Runner", 55f, 175f, 10, 27f, 1),
    BRUTE("Brute", 420f, 55f, 35, 46f, 2),
    BOSS("Abomination", 2600f, 48f, 200, 60f, 5)
}

class Zombie(val type: ZombieType, hpMul: Float) {
    val maxHp = type.baseHp * hpMul
    var hp = maxHp
    var rewarded = false

    val pos = PointF(GameMap.waypoints[0].x, GameMap.waypoints[0].y)
    private var wpIndex = 1
    var dirX = 1f
        private set
    var dirY = 0f
        private set

    var reachedEnd = false
        private set

    private var slowTimer = 0f
    private var slowFactor = 1f
    private var burnTimer = 0f
    private var burnDps = 0f

    var wobble = Random.nextFloat() * 6.28f
        private set

    val alive get() = hp > 0f && !reachedEnd
    val isSlowed get() = slowTimer > 0f
    val isBurning get() = burnTimer > 0f

    fun applySlow(factor: Float, duration: Float) {
        slowFactor = if (isSlowed) min(slowFactor, factor) else factor
        slowTimer = max(slowTimer, duration)
    }

    fun applyBurn(dps: Float, duration: Float) {
        burnDps = max(burnDps, dps)
        burnTimer = max(burnTimer, duration)
    }

    fun update(dt: Float) {
        if (slowTimer > 0f) slowTimer -= dt
        if (burnTimer > 0f) {
            burnTimer -= dt
            hp -= burnDps * dt
        }
        wobble += dt * 9f

        var remaining = type.speed * (if (isSlowed) slowFactor else 1f) * dt
        while (remaining > 0f && wpIndex < GameMap.waypoints.size) {
            val target = GameMap.waypoints[wpIndex]
            val dx = target.x - pos.x
            val dy = target.y - pos.y
            val dist = hypot(dx, dy)
            if (dist < 0.001f) {
                wpIndex++
                continue
            }
            dirX = dx / dist
            dirY = dy / dist
            if (dist <= remaining) {
                pos.set(target.x, target.y)
                wpIndex++
                remaining -= dist
            } else {
                pos.x += dirX * remaining
                pos.y += dirY * remaining
                remaining = 0f
            }
        }
        if (wpIndex >= GameMap.waypoints.size) reachedEnd = true
    }

    /** Higher value = further along the path. Used so towers shoot the front-most zombie. */
    fun progress(): Float {
        if (wpIndex >= GameMap.waypoints.size) return Float.MAX_VALUE
        val t = GameMap.waypoints[wpIndex]
        return wpIndex - hypot(t.x - pos.x, t.y - pos.y) / 1000f
    }
}
