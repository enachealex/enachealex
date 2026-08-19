package com.enache.zombietd.game

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class UpgradeEffect { DAMAGE, FIRE_RATE, RANGE, CRIT, PIERCE, SLOW_POWER, SLOW_DURATION, BURN_DPS, SPLASH }

class UpgradeDef(
    val name: String,
    val desc: String,
    val maxRank: Int,
    val baseCost: Int,      // rank N costs baseCost * N
    val effect: UpgradeEffect,
    val perRank: Float
)

/**
 * Soldier classes. Each class has its own three upgrade tracks; more classes
 * can be added as new enum entries without touching the combat code.
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
    val upgrades: List<UpgradeDef>
) {
    ASSAULT(
        "Assault", "Fast rifle", 100, 270f, 20f, 2.4f, 1100f, 0xFFFBC02D.toInt(),
        upgrades = listOf(
            UpgradeDef("Rapid Fire", "+20% fire rate", 3, 70, UpgradeEffect.FIRE_RATE, 0.20f),
            UpgradeDef("Hollow Points", "+30% damage", 3, 80, UpgradeEffect.DAMAGE, 0.30f),
            UpgradeDef("Long Barrel", "+15% range", 3, 60, UpgradeEffect.RANGE, 0.15f)
        )
    ),
    SUPPORT(
        "Support", "Suppresses", 180, 240f, 9f, 5.0f, 1000f, 0xFF4FC3F7.toInt(),
        baseSlowFactor = 0.85f, slowBaseDuration = 1.2f,
        upgrades = listOf(
            UpgradeDef("Ammo Belt", "+20% fire rate", 3, 90, UpgradeEffect.FIRE_RATE, 0.20f),
            UpgradeDef("AP Rounds", "+30% damage", 3, 100, UpgradeEffect.DAMAGE, 0.30f),
            UpgradeDef("Suppressing Fire", "Stronger slow", 3, 110, UpgradeEffect.SLOW_POWER, 0.10f)
        )
    ),
    ENGINEER(
        "Engineer", "Splash", 220, 400f, 40f, 0.45f, 550f, 0xFFFF7043.toInt(),
        baseSplash = 110f,
        upgrades = listOf(
            UpgradeDef("Big Payload", "+35% damage", 3, 120, UpgradeEffect.DAMAGE, 0.35f),
            UpgradeDef("Frag Radius", "+30px blast radius", 3, 100, UpgradeEffect.SPLASH, 30f),
            UpgradeDef("Auto Loader", "+20% fire rate", 3, 110, UpgradeEffect.FIRE_RATE, 0.20f)
        )
    ),
    RECON(
        "Recon", "Long range", 250, 540f, 85f, 0.55f, 1700f, 0xFF90A4AE.toInt(),
        upgrades = listOf(
            UpgradeDef("Deadeye", "+15% crit chance (2.5x)", 3, 110, UpgradeEffect.CRIT, 0.15f),
            UpgradeDef("Heavy Rounds", "+35% damage", 3, 120, UpgradeEffect.DAMAGE, 0.35f),
            UpgradeDef("Piercing Shot", "Shots pierce +1 enemy", 2, 150, UpgradeEffect.PIERCE, 1f)
        )
    )
}

class Tower(val type: TowerType, val col: Int, val row: Int) {
    companion object {
        const val CRIT_MULTIPLIER = 2.5f
    }

    val pos: PointF = GameMap.cellCenter(col, row)
    val ranks = IntArray(type.upgrades.size)
    var invested = type.cost
        private set
    var angle = (-Math.PI / 2).toFloat()
        private set
    private var cooldown = 0f

    val totalRanks get() = ranks.sum()

    private fun bonus(effect: UpgradeEffect): Float {
        var b = 0f
        for (i in type.upgrades.indices) {
            if (type.upgrades[i].effect == effect) b += type.upgrades[i].perRank * ranks[i]
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

    fun upgradeCost(i: Int) = type.upgrades[i].baseCost * (ranks[i] + 1)
    fun canRankUp(i: Int) = ranks[i] < type.upgrades[i].maxRank

    fun rankUp(i: Int) {
        invested += upgradeCost(i)
        ranks[i]++
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
