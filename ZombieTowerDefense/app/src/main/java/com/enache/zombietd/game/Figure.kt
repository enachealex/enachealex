package com.enache.zombietd.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Procedural jointed stick figures. Every person and creature is a small
 * skeleton (hips, knees, shoulders, elbows, neck) animated with a walk cycle
 * so limbs bend like a human's. Drawn side-on, standing on (x, y).
 *
 * Local space: feet at y = 0, up is -y, forward is +x. Limb angles are
 * measured from straight-down, positive = swinging forward.
 */
object Figure {
    enum class Kind { SOLDIER, TROOP, ZOMBIE }
    enum class Weapon { NONE, KNIFE, RIFLE, LMG, LAUNCHER, SNIPER }

    private val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowRect = RectF()

    private const val HIP_Y = -24f
    private const val THIGH = 13f
    private const val SHIN = 13f
    private const val UPPER_ARM = 11f
    private const val FOREARM = 11f
    private const val HEAD_R = 8f

    fun darken(color: Int, f: Float): Int {
        val a = color ushr 24
        val r = ((color shr 16 and 0xFF) * f).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 0xFF) * f).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun ex(px: Float, angle: Float, len: Float) = px + sin(angle) * len
    private fun ey(py: Float, angle: Float, len: Float) = py + cos(angle) * len

    /** Two-segment limb with a visible joint. */
    private fun limb(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float) {
        limbPaint.color = color
        limbPaint.strokeWidth = w
        c.drawLine(x0, y0, x1, y1, limbPaint)
        c.drawLine(x1, y1, x2, y2, limbPaint)
        fillPaint.color = darken(color, 0.78f)
        c.drawCircle(x1, y1, w * 0.42f, fillPaint)
    }

    /**
     * @param phase walk-cycle clock (radians)
     * @param walking false = idle stance
     * @param aimAngle world-space angle the weapon should point at (soldiers)
     * @param attack 0..1 stab extension for melee fighters
     */
    fun draw(
        c: Canvas, x: Float, y: Float, scale: Float, facingRight: Boolean,
        phase: Float, walking: Boolean, kind: Kind,
        bodyColor: Int, accent: Int, skin: Int, thickness: Float = 4.5f,
        aimAngle: Float? = null, weapon: Weapon = Weapon.NONE, attack: Float = 0f
    ) {
        c.save()
        c.translate(x, y)
        c.scale(if (facingRight) scale else -scale, scale)

        fillPaint.color = 0x40000000
        shadowRect.set(-16f, -5f, 16f, 5f)
        c.drawOval(shadowRect, fillPaint)

        val zombie = kind == Kind.ZOMBIE
        val hipX = 0f
        val shX = if (zombie) 9f else 0f
        val shY = if (zombie) -40f else -46f
        val headX = if (zombie) 15f else 0f
        val headY = if (zombie) -47f else -58f
        val legColor = darken(bodyColor, 0.7f)
        val w = thickness

        fun leg(offset: Float, front: Boolean) {
            val s = sin(phase + offset)
            val thighA: Float
            val kneeBend: Float
            if (!walking) {
                thighA = if (front) 0.12f else -0.12f
                kneeBend = if (zombie) 0.35f else 0f
            } else if (zombie) {
                thighA = s * 0.35f
                kneeBend = 0.45f + max(0f, cos(phase + offset)) * 0.5f
            } else {
                thighA = s * 0.55f
                kneeBend = max(0f, cos(phase + offset)) * 0.9f
            }
            val kx = ex(hipX, thighA, THIGH)
            val ky = ey(HIP_Y, thighA, THIGH)
            val fx = ex(kx, thighA - kneeBend, SHIN)
            val fy = ey(ky, thighA - kneeBend, SHIN)
            limb(c, hipX, HIP_Y, kx, ky, fx, fy, legColor, w)
            limbPaint.color = darken(legColor, 0.75f)
            limbPaint.strokeWidth = w
            c.drawLine(fx, fy, fx + 6f, fy, limbPaint)
        }

        fun swingArm(offset: Float) {
            val upperA: Float
            val foreA: Float
            if (zombie) {
                upperA = 1.35f + sin(phase * 0.7f + offset) * 0.12f
                foreA = upperA - 0.35f
            } else if (!walking) {
                upperA = 0.1f
                foreA = 0.35f
            } else {
                upperA = -sin(phase + offset) * 0.5f
                foreA = upperA + 0.55f
            }
            val exx = ex(shX, upperA, UPPER_ARM)
            val eyy = ey(shY, upperA, UPPER_ARM)
            val hx = ex(exx, foreA, FOREARM)
            val hy = ey(eyy, foreA, FOREARM)
            limb(c, shX, shY, exx, eyy, hx, hy, bodyColor, w)
            if (zombie) {
                limbPaint.color = skin
                limbPaint.strokeWidth = w * 0.6f
                c.drawLine(hx, hy, hx + 4f, hy + 3f, limbPaint)
                c.drawLine(hx, hy, hx + 5f, hy - 1f, limbPaint)
            }
        }

        fun stabArm() {
            val upperA = 0.35f + 1.15f * attack
            val foreA = upperA + 0.45f * (1f - attack)
            val exx = ex(shX, upperA, UPPER_ARM)
            val eyy = ey(shY, upperA, UPPER_ARM)
            val hx = ex(exx, foreA, FOREARM)
            val hy = ey(eyy, foreA, FOREARM)
            limb(c, shX, shY, exx, eyy, hx, hy, bodyColor, w)
            limbPaint.color = 0xFFCFD8DC.toInt()
            limbPaint.strokeWidth = w * 0.7f
            c.drawLine(hx, hy, ex(hx, foreA, 12f), ey(hy, foreA, 12f), limbPaint)
        }

        fun aimArms(angle: Float) {
            val dx = if (facingRight) cos(angle) else -cos(angle)
            val dy = sin(angle)
            val px = -dy
            val py = dx
            val e1x = shX + dx * 9f + px * 4f
            val e1y = shY + dy * 9f + py * 4f
            val h1x = shX + dx * 18f
            val h1y = shY + dy * 18f
            val e2x = shX + dx * 14f - px * 3f
            val e2y = shY + dy * 14f - py * 3f
            val h2x = shX + dx * 27f
            val h2y = shY + dy * 27f
            limb(c, shX, shY, e2x, e2y, h2x, h2y, darken(bodyColor, 0.85f), w)
            val gx = shX + dx * 10f
            val gy = shY + dy * 10f
            val len: Float
            val gw: Float
            when (weapon) {
                Weapon.RIFLE -> { len = 30f; gw = w * 1.1f }
                Weapon.LMG -> { len = 34f; gw = w * 1.5f }
                Weapon.LAUNCHER -> { len = 30f; gw = w * 2f }
                Weapon.SNIPER -> { len = 44f; gw = w * 0.9f }
                else -> { len = 0f; gw = w }
            }
            if (len > 0f) {
                limbPaint.color = 0xFF263238.toInt()
                limbPaint.strokeWidth = gw
                c.drawLine(gx, gy, gx + dx * len, gy + dy * len, limbPaint)
                fillPaint.color = accent
                when (weapon) {
                    Weapon.LAUNCHER -> c.drawCircle(gx + dx * len, gy + dy * len, gw * 0.7f, fillPaint)
                    Weapon.SNIPER -> c.drawCircle(gx + dx * 16f - px * 4f, gy + dy * 16f - py * 4f, 3f, fillPaint)
                    Weapon.LMG -> c.drawRect(gx + dx * 12f + px * 3f, gy + dy * 12f + py * 3f, gx + dx * 20f + px * 9f, gy + dy * 20f + py * 9f, fillPaint)
                    else -> c.drawCircle(gx + dx * len, gy + dy * len, gw * 0.5f, fillPaint)
                }
            }
            limb(c, shX, shY, e1x, e1y, h1x, h1y, bodyColor, w)
        }

        // back limbs
        leg(PI.toFloat(), false)
        if (aimAngle == null) swingArm(PI.toFloat())

        // torso (and pack for soldiers)
        if (kind == Kind.SOLDIER) {
            fillPaint.color = darken(bodyColor, 0.6f)
            c.drawRoundRect(-11f, -46f, -3f, -30f, 3f, 3f, fillPaint)
        }
        limbPaint.color = bodyColor
        limbPaint.strokeWidth = w * 1.6f
        c.drawLine(hipX, HIP_Y, shX, shY, limbPaint)

        // front leg
        leg(0f, true)

        // head
        fillPaint.color = skin
        c.drawCircle(headX, headY, HEAD_R, fillPaint)
        strokePaint.color = darken(skin, 0.7f)
        strokePaint.strokeWidth = 1.5f
        c.drawCircle(headX, headY, HEAD_R, strokePaint)
        if (zombie) {
            fillPaint.color = 0xFF5D4037.toInt()
            c.drawCircle(headX - 3f, headY - 5f, 5f, fillPaint)
            fillPaint.color = 0xFFE53935.toInt()
            c.drawCircle(headX + 4f, headY - 1f, 1.8f, fillPaint)
            limbPaint.color = darken(skin, 0.6f)
            limbPaint.strokeWidth = 1.5f
            c.drawLine(headX + 2f, headY + 4f, headX + 7f, headY + 3f, limbPaint)
        } else {
            // helmet with a class-coloured band
            fillPaint.color = 0xFF5E6B3D.toInt()
            shadowRect.set(headX - HEAD_R - 2f, headY - HEAD_R - 3f, headX + HEAD_R + 2f, headY + HEAD_R - 1f)
            c.drawArc(shadowRect, 180f, 180f, true, fillPaint)
            fillPaint.color = accent
            c.drawRect(headX - HEAD_R - 2f, headY - 2f, headX + HEAD_R + 2f, headY, fillPaint)
        }

        // front arm / weapon
        when {
            aimAngle != null -> aimArms(aimAngle)
            weapon == Weapon.KNIFE -> stabArm()
            else -> swingArm(0f)
        }

        c.restore()
    }

    /** Facing for an entity that aims: face the side the target is on. */
    fun facingFromAngle(angle: Float) = cos(angle) >= 0f

    fun angleTo(fromX: Float, fromY: Float, toX: Float, toY: Float) = atan2(toY - fromY, toX - fromX)
}
