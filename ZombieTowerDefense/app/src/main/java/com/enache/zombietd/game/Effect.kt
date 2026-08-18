package com.enache.zombietd.game

/** Lightweight one-shot visual: a fading blood splat, a rising text popup, or a lightning bolt. */
class Effect private constructor(
    val kind: Kind,
    var x: Float,
    var y: Float,
    val x2: Float,
    val y2: Float,
    val text: String,
    val color: Int,
    val size: Float,
    val maxLife: Float
) {
    enum class Kind { SPLAT, TEXT, BOLT }

    var life = maxLife
        private set

    val done get() = life <= 0f
    val alpha get() = (255 * (life / maxLife)).toInt().coerceIn(0, 255)

    fun update(dt: Float) {
        life -= dt
        if (kind == Kind.TEXT) y -= 70f * dt
    }

    companion object {
        fun splat(x: Float, y: Float, size: Float) =
            Effect(Kind.SPLAT, x, y, 0f, 0f, "", 0xFF7B1216.toInt(), size, 2.2f)

        fun text(x: Float, y: Float, text: String, color: Int, size: Float = 44f) =
            Effect(Kind.TEXT, x, y, 0f, 0f, text, color, size, 1.1f)

        fun bolt(x: Float, y: Float, x2: Float, y2: Float) =
            Effect(Kind.BOLT, x, y, x2, y2, "", 0xFFB3E5FC.toInt(), 5f, 0.18f)
    }
}
