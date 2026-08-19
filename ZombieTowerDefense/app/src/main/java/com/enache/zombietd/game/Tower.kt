package com.enache.zombietd.game

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class UpgradeEffect { DAMAGE, FIRE_RATE, RANGE, CRIT, PIERCE, SLOW_POWER, SLOW_DURATION, BURN_DPS, SPLASH }

/** One step in a class's ordered upgrade path. Tiers must be bought in order. */
class UpgradeTier(
    val name: String,
    val desc: String,
    val cost: Int,
    val effect: UpgradeEffect,
    val amount: Float
)

/**
 * Soldier classes. Each class has its own ordered upgrade path (tier 1 first,
 * then tier 2, ...); more classes and deeper paths can be added as data only.
 */
enum class TowerType(
    val label: String,
    val blurb: String,
    val cost: Int,
    val baseRange: Float,
    val baseDamage: Float,
    val baseFireRate: Float,   // shots per second
    val projectileSpeed: Float,
    val color: Int,
    val baseSlowFactor: Float = 1f,   // <1 means hits slow the target
    val slowBaseDuration: Float = 0f,
    val baseBurnDps: Float = 0f,
    val baseSplash: Float = 0f,       // splash radius in px
    val path: List<UpgradeTier>
) {
    ASSAULT(
        "Assault", "Fast rifle", 100, 270f, 20f, 2.4f, 1100f, 0xFFFBC02D.toInt(),
        path = listOf(
            UpgradeTier("Rapid Fire", "+25% fire rate", 80, UpgradeEffect.FIRE_RATE, 0.25f),
            UpgradeTier("Hollow Points", "+35% damage", 120, UpgradeEffect.DAMAGE, 0.35f),
            UpgradeTier("Long Barrel", "+20% range", 150, UpgradeEffect.RANGE, 0.20f),
            UpgradeTier("Rapid Fire II", "+35% fire rate", 220, UpgradeEffect.FIRE_RATE, 0.35f),
            UpgradeTier("Hollow Points II", "+50% damage", 300, UpgradeEffect.DAMAGE, 0.50f)
        )
    ),
    SUPPORT(
        "Support", "Suppresses", 180, 240f, 9f, 5.0f, 1000f, 0xFF4FC3F7.toInt(),
        baseSlowFactor = 0.85f, slowBaseDuration = 1.2f,
        path = listOf(
            UpgradeTier("Ammo Belt", "+25% fire rate", 100, UpgradeEffect.FIRE_RATE, 0.25f),
            UpgradeTier("Suppressing Fire", "Stronger slow", 140, UpgradeEffect.SLOW_POWER, 0.12f),
            UpgradeTier("AP Rounds", "+40% damage", 180, UpgradeEffect.DAMAGE, 0.40f),
            UpgradeTier("Suppressing Fire II", "Even stronger slow", 240, UpgradeEffect.SLOW_POWER, 0.12f),
            UpgradeTier("Ammo Belt II", "+40% fire rate", 320, UpgradeEffect.FIRE_RATE, 0.40f)
        )
    ),
    ENGINEER(
        "Engineer", "Splash", 220, 400f, 40f, 0.45f, 550f, 0xFFFF7043.toInt(),
        baseSplash = 110f,
        path = listOf(
            UpgradeTier("Big Payload", "+35% damage", 130, UpgradeEffect.DAMAGE, 0.35f),
            UpgradeTier("Frag Radius", "+35px blast radius", 170, UpgradeEffect.SPLASH, 35f),
            UpgradeTier("Auto Loader", "+30% fire rate", 220, UpgradeEffect.FIRE_RATE, 0.30f),
            UpgradeTier("Big Payload II", "+50% damage", 280, UpgradeEffect.DAMAGE, 0.50f),
            UpgradeTier("Frag Radius II", "+45px blast radius", 360, UpgradeEffect.SPLASH, 45f)
        )
    ),
    RECON(
        "Recon", "Long range", 250, 540f, 85f, 0.55f, 1700f, 0xFF90A4AE.toInt(),
        path = listOf(
            UpgradeTier("Heavy Rounds", "+40% damage", 140, UpgradeEffect.DAMAGE, 0.40f),
            UpgradeTier("Piercing Shot", "Shots pierce +1 enemy", 200, UpgradeEffect.PIERCE, 1f),
            UpgradeTier("Heavy Rounds II", "+50% damage", 260, UpgradeEffect.DAMAGE, 0.50f),
            UpgradeTier("Deadeye", "+25% crit chance (2.5x)", 320, UpgradeEffect.CRIT, 0.25f),
            UpgradeTier("Piercing Shot II", "Shots pierce +1 more", 400, UpgradeEffect.PIERCE, 1f)
        )
    )
}

class Tower(val type: TowerType, val col: Int, val row: Int) {
    companion object {
        const val CRIT_MULTIPLIER = 2.5f
    }

    val pos: PointF = GameMap.cellCenter(col, row)
    var tier = 0
        private set
    var invested = type.cost
        private set
    var angle = (-Math.PI / 2).toFloat()
        private set
    private var cooldown = 0f

    /** The next tier available to buy, or null when the path is complete. */
    val nextTier get() = type.path.getOrNull(tier)
    val isMaxed get() = tier >= type.path.size

    private fun bonus(effect: UpgradeEffect): Float {
        var b = 0f
        for (i in 0 until tier) {
            if (type.path[i].effect == effect) b += type.path[i].amount
        }
        return b
    }

    val damage get() = type.baseDamage * (1f + bonus(UpgradeEffect.DAMAGE))
    val fireRate get() = type.baseFireRate * (1f + bonus(UpgradeEffect.FIRE_RATE))
    val range get() = type.baseRange * (1f + bonus(UpgradeEffect.RANGE))
    val critChance get() = bonus(UpgradeEffect.CRIT)
    val pierce get() = bonus(UpgradeEffect.PIERCE).roundToInt()
    val slowFactor get() = (type.baseSlowFactor - bonus(UpgradeEffect.SLOW_POWER)).coerceAtLeast(0.25f)
    val slowDuration get() = type.slowBaseDuration + bonus(UpgradeEffect.SLOW_DURATION)
    val burnDps get() = type.baseBurnDps + bonus(UpgradeEffect.BURN_DPS)
    val splash get() = if (type.baseSplash > 0f) type.baseSplash + bonus(UpgradeEffect.SPLASH) else 0f
    val sellValue get() = (invested * 0.7f).roundToInt()

    fun buyTier() {
        val next = nextTier ?: return
        invested += next.cost
        tier++
    }

    fun update(dt: Float, zombies: List<Zombie>, projectiles: MutableList<Projectile>) {
        cooldown -= dt

        var best: Zombie? = null
        var bestProgress = -Float.MAX_VALUE
        val r = range
        for (z in zombies) {
            if (!z.alive) continue
            val d = hypot(z.pos.x - pos.x, z.pos.y - pos.y)
            if (d <= r) {
                val p = z.progress()
                if (p > bestProgress) {
                    bestProgress = p
                    best = z
                }
            }
        }
        val target = best ?: return

        angle = atan2(target.pos.y - pos.y, target.pos.x - pos.x)
        if (cooldown > 0f) return
        cooldown = 1f / fireRate

        val isCrit = critChance > 0f && Random.nextFloat() < critChance
        val dmg = if (isCrit) damage * CRIT_MULTIPLIER else damage
        val muzzleX = pos.x + cos(angle) * 34f
        val muzzleY = pos.y + sin(angle) * 34f
        projectiles.add(
            Projectile(
                muzzleX, muzzleY, target, type.projectileSpeed, dmg, type,
                isCrit = isCrit,
                pierceLeft = pierce,
                slowFactor = slowFactor,
                slowDuration = slowDuration,
                burnDps = burnDps,
                splash = splash
            )
        )
    }
}
