package com.enache.zombietd.game

import kotlin.random.Random

/**
 * A patch of burning ground left by a fully upgraded Flametrooper. It damages
 * anything standing in it for as long as it lasts.
 */
class Fire(val x: Float, val y: Float, val radius: Float, val dps: Float, private val maxLife: Float) {
    var life = maxLife
        private set
    val seed = Random.nextInt()

    val done get() = life <= 0f
    /** 0..1, used to fade the patch out as it burns down. */
    val strength get() = (life / maxLife).coerceIn(0f, 1f)

    fun update(dt: Float) {
        life -= dt
    }
}
