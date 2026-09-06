package com.enache.zombietd.game

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A melee soldier. Player-deployed troops march from the gate back up the
 * path; barracks squads walk to their rally point. Either way they block the
 * first zombies they meet, fight hand-to-hand, and at the end of their route
 * they hold position. In Castle vs Nest a deployed troop that reaches the nest
 * detonates a satchel charge against it.
 */
class Troop(
    waypoints: List<PointF>,
    val maxHp: Float = MAX_HP,
    val dps: Float = DPS,
    val owner: Tower? = null
) {
    companion object {
        const val COST = 120
        const val MAX_ACTIVE = 8
        const val MAX_HP = 220f
        const val DPS = 45f
        const val SPEED = 85f
        const val MELEE_RANGE = 58f
        const val NEST_DAMAGE = 300f

        /**
         * Zombie lanes begin and end off-screen; troops must stay on the field,
         * so lane waypoints are clamped to the playfield. Interior corners are
         * already inside, so only the endpoints move — onto the gate tile and
         * the entrance/nest tile.
         */
        fun clampToField(p: PointF) = PointF(
            p.x.coerceIn(GameMap.TILE / 2f, GameMap.COLS * GameMap.TILE - GameMap.TILE / 2f),
            p.y.coerceIn(GameMap.TOP + GameMap.TILE / 2f, GameMap.TOP + GameMap.ROWS * GameMap.TILE - GameMap.TILE / 2f)
        )

        /** A player-deployed troop walking a zombie lane in reverse, gate to spawn. */
        fun fromLane(lane: List<PointF>) = Troop(lane.reversed().map { clampToField(it) })
    }

    private val path: List<PointF> = waypoints
    var hp = maxHp
    val pos = PointF(path[0].x, path[0].y)
    private var wpIndex = 1
    var dirX = -1f
        private set
    var dirY = 0f
        private set
    var facingRight = false
        private set

    /** Walk-cycle clock, advanced only while moving. */
    var phase = 0f
        private set

    /** Set each tick by the game while this troop is trading blows with a zombie. */
    var engaged = false

    /** True once the troop has walked its whole route. */
    var atDestination = false
        private set

    val alive get() = hp > 0f
    val isDeployed get() = owner == null

    fun update(dt: Float) {
        if (engaged || atDestination || !alive) return

        var remaining = SPEED * dt
        phase += dt * 9f
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
            if (abs(dirX) > 0.05f) facingRight = dirX > 0f
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
            if (abs(dirX) > 0.05f) facingRight = dirX > 0f
        }
    }
}
