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
    val livesCost: Int,    // lives lost if it reaches the exit
    val meleeDps: Float    // damage per second dealt to a blocking troop
) {
    WALKER("Walker", 70f, 95f, 10, 34f, 1, 14f),
    RUNNER("Runner", 45f, 175f, 8, 27f, 1, 12f),
    BRUTE("Brute", 340f, 55f, 30, 46f, 2, 30f),
    BOSS("Abomination", 2000f, 48f, 150, 60f, 5, 70f)
}

/** Health is constant across the whole game — later waves send more zombies, not tougher ones. */
class Zombie(val type: ZombieType, private val path: List<PointF>) {
    val maxHp = type.baseHp
    var hp = maxHp
    var rewarded = false

    /** Set each tick by the game when a melee troop is blocking this zombie. */
    var blocked = false

    val pos = PointF(path[0].x, path[0].y)
    private var wpIndex = 1
    var dirX = 1f
        private set
    var dirY = 0f
        private set
    var facingRight = true
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

        if (blocked) return // fighting a troop: stand and swing

        var remaining = type.speed * (if (isSlowed) slowFactor else 1f) * dt
        while (remaining > 0f && wpIndex < path.size) {
            val target = path[wpIndex]
            val dx = target.x - pos.x
            val dy = target.y - pos.y
            val dist = hypot(dx, dy)
            if (dist < 0.001f) {
                wpIndex++
                continue
            }
            dirX = dx / dist
            dirY = dy / dist
            if (kotlin.math.abs(dirX) > 0.05f) facingRight = dirX > 0f
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
        if (wpIndex >= path.size) reachedEnd = true
    }

    /** Higher value = further along the path. Used so towers shoot the front-most zombie. */
    fun progress(): Float {
        if (wpIndex >= path.size) return Float.MAX_VALUE
        val t = path[wpIndex]
        return wpIndex - hypot(t.x - pos.x, t.y - pos.y) / 1000f
    }
}
