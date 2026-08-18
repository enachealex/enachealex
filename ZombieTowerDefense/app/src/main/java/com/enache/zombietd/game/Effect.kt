package com.enache.zombietd.game

/** Lightweight one-shot visual: a fading blood splat or a rising text popup. */
class Effect private constructor(
    val kind: Kind,
    var x: Float,
    var y: Float,
    val text: String,
    val color: Int,
    val size: Float,
    val maxLife: Float
) {
    enum class Kind { SPLAT, TEXT }

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
            Effect(Kind.SPLAT, x, y, "", 0xFF7B1216.toInt(), size, 2.2f)

        fun text(x: Float, y: Float, text: String, color: Int, size: Float = 44f) =
            Effect(Kind.TEXT, x, y, text, color, size, 1.1f)
    }
}
