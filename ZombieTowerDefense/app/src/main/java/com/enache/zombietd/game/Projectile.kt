package com.enache.zombietd.game

import kotlin.math.hypot

class Projectile(
    var x: Float,
    var y: Float,
    target: Zombie,
    private val speed: Float,
    val damage: Float,
    val kind: TowerType,
    val isCrit: Boolean = false,
    val isMiss: Boolean = false,
    var pierceLeft: Int = 0,
    val slowFactor: Float = 1f,
    val slowDuration: Float = 0f,
    val burnDps: Float = 0f,
    val splash: Float = 0f
) {
    var target = target
        private set
    val hitAlready = mutableSetOf<Zombie>()

    private var tx = target.pos.x
    private var ty = target.pos.y

    fun retarget(z: Zombie) {
        target = z
        tx = z.pos.x
        ty = z.pos.y
    }

    /** Moves the projectile; returns true when it arrives at its target point. */
    fun update(dt: Float): Boolean {
        if (target.alive) {
            tx = target.pos.x
            ty = target.pos.y
        }
        val dx = tx - x
        val dy = ty - y
        val dist = hypot(dx, dy)
        val step = speed * dt
        return if (dist <= step || dist < 1f) {
            x = tx
            y = ty
            true
        } else {
            x += dx / dist * step
            y += dy / dist * step
            false
        }
    }
}
