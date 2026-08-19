package com.enache.zombietd.game

import android.graphics.PointF
import kotlin.math.hypot

/**
 * A melee soldier deployed by the player. Troops march from the exit back up
 * the path toward the spawn, block the first zombies they meet, and fight
 * hand-to-hand. In Castle vs Nest, a troop that reaches the nest detonates a
 * satchel charge against it.
 */
class Troop(lane: List<PointF>) {
    companion object {
        const val COST = 120
        const val MAX_ACTIVE = 8
        const val MAX_HP = 220f
        const val DPS = 45f
        const val SPEED = 85f
        const val MELEE_RANGE = 58f
        const val NEST_DAMAGE = 300f
    }

    private val path = lane.reversed()
    var hp = MAX_HP
    val pos = PointF(path[0].x, path[0].y)
    private var wpIndex = 1
    var dirX = -1f
        private set
    var dirY = 0f
        private set

    /** Set each tick by the game while this troop is trading blows with a zombie. */
    var engaged = false

    /** True once the troop has walked the whole path back to the spawn/nest. */
    var reachedNest = false
        private set

    val alive get() = hp > 0f && !reachedNest

    fun update(dt: Float) {
        if (engaged || !alive) return

        var remaining = SPEED * dt
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
        if (wpIndex >= path.size) reachedNest = true
    }

    fun face(x: Float, y: Float) {
        val dx = x - pos.x
        val dy = y - pos.y
        val d = hypot(dx, dy)
        if (d > 0.001f) {
            dirX = dx / d
            dirY = dy / d
        }
    }
}
