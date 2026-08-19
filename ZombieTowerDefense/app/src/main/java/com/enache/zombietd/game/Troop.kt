package com.enache.zombietd.game

import android.graphics.PointF
import kotlin.math.hypot

/**
 * A melee soldier deployed by the player. Troops march from the gate back up
 * the path toward the spawn, block the first zombies they meet, and fight
 * hand-to-hand. At the far end of the path they stop ON the field: in Castle
 * vs Nest they detonate a satchel charge against the nest; in other modes they
 * hold the entrance as a guard until they fall.
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

        /**
         * Zombie lanes begin and end off-screen; troops must stay on the
         * field, so their waypoints are clamped to the playfield. Interior
         * corners are already inside, so only the two endpoints move — onto
         * the gate tile and the entrance/nest tile.
         */
        private fun clampToField(p: PointF) = PointF(
            p.x.coerceIn(GameMap.TILE / 2f, GameMap.COLS * GameMap.TILE - GameMap.TILE / 2f),
            p.y.coerceIn(GameMap.TOP + GameMap.TILE / 2f, GameMap.TOP + GameMap.ROWS * GameMap.TILE - GameMap.TILE / 2f)
        )
    }

    private val path: List<PointF> = lane.reversed().map { clampToField(it) }
    var hp = MAX_HP
    val pos = PointF(path[0].x, path[0].y)
    private var wpIndex = 1
    var dirX = -1f
        private set
    var dirY = 0f
        private set

    /** Set each tick by the game while this troop is trading blows with a zombie. */
    var engaged = false

    /** True once the troop has walked the whole path to the spawn/nest tile. */
    var atDestination = false
        private set

    val alive get() = hp > 0f

    fun update(dt: Float) {
        if (engaged || atDestination || !alive) return

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
        if (wpIndex >= path.size) atDestination = true
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
