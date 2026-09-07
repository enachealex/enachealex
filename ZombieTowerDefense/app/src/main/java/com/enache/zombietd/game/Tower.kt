package com.enache.zombietd.game

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class StatKind {
    DAMAGE, RANGE_PCT, RANGE_FLAT, FIRE_RATE, SUPPRESSION, BLAST, ACCURACY,
    TROOP_DPS, TROOP_HP, SQUAD
}

/**
 * One of a class's three upgradeable stats. [amount] is a fraction of the base
 * value per purchase, except for RANGE_FLAT (pixels) and SQUAD (members).
 */
class UpgradeStat(val name: String, val desc: String, val kind: StatKind, val amount: Float)

/**
 * Soldier classes. Every class has exactly three upgradeable stats. Each level
 * offers one purchase of each; buying all three promotes the tower to the next
 * level, where the same three stats are offered again at a higher price.
 */
enum class TowerType(
    val label: String,
    val blurb: String,
    val cost: Int,
    val upgradeCost: Int,      // price of one stat at level 1; scales with level
    val baseRange: Float,
    val baseDamage: Float,
    val baseFireRate: Float,   // shots per second
    val projectileSpeed: Float,
    val color: Int,
    val baseSlowFactor: Float = 1f,   // <1 means hits slow the target
    val slowBaseDuration: Float = 0f,
    val baseSplash: Float = 0f,       // splash radius in px
    val baseAccuracy: Float = 0f,     // >0: shots can miss, and excess becomes crit
    val baseSquad: Int = 0,           // >0: barracks squad size
    val stats: List<UpgradeStat>
) {
    ASSAULT(
        "Assault", "Fast rifle", 100, 60, 120f, 20f, 2.4f, 1100f, 0xFFFBC02D.toInt(),
        stats = listOf(
            UpgradeStat("Damage", "+5% damage", StatKind.DAMAGE, 0.05f),
            UpgradeStat("Range", "+20 range", StatKind.RANGE_FLAT, 20f),
            UpgradeStat("Fire Rate", "+10% fire rate", StatKind.FIRE_RATE, 0.10f)
        )
    ),
    SUPPORT(
        "Support", "Suppresses", 180, 90, 240f, 9f, 5.0f, 1000f, 0xFF4FC3F7.toInt(),
        baseSlowFactor = 0.85f, slowBaseDuration = 1.2f,
        stats = listOf(
            UpgradeStat("Fire Rate", "+8% fire rate", StatKind.FIRE_RATE, 0.08f),
            UpgradeStat("Suppression", "+5% suppression", StatKind.SUPPRESSION, 0.05f),
            UpgradeStat("Damage", "+5% damage", StatKind.DAMAGE, 0.05f)
        )
    ),
    ENGINEER(
        "Engineer", "Splash", 220, 110, 400f, 40f, 0.45f, 550f, 0xFFFF7043.toInt(),
        baseSplash = 110f,
        stats = listOf(
            UpgradeStat("Damage", "+7% damage", StatKind.DAMAGE, 0.07f),
            UpgradeStat("Blast Radius", "+2% blast radius", StatKind.BLAST, 0.02f),
            UpgradeStat("Fire Rate", "+5% fire rate", StatKind.FIRE_RATE, 0.05f)
        )
    ),
    RECON(
        "Recon", "Long range", 250, 120, 250f, 85f, 0.55f, 1700f, 0xFF90A4AE.toInt(),
        baseAccuracy = 70f,
        stats = listOf(
            UpgradeStat("Range", "+20% range", StatKind.RANGE_PCT, 0.20f),
            UpgradeStat("Damage", "+10% damage", StatKind.DAMAGE, 0.10f),
            UpgradeStat("Accuracy", "+10% accuracy", StatKind.ACCURACY, 0.10f)
        )
    ),
    BARRACKS(
        "Barracks", "Melee squad", 200, 100, 170f, 0f, 0f, 0f, 0xFF8BC34A.toInt(),
        baseSquad = 2,
        stats = listOf(
            UpgradeStat("Combat Training", "+8% squad damage", StatKind.TROOP_DPS, 0.08f),
            UpgradeStat("Body Armor", "+8% squad health", StatKind.TROOP_HP, 0.08f),
            UpgradeStat("Recruiting", "+5% squad HP, +1 soldier at L3/L5", StatKind.SQUAD, 0.05f)
        )
    );

    val isBarracks get() = baseSquad > 0
    val hasAccuracy get() = baseAccuracy > 0f
}

class Tower(val type: TowerType, val col: Int, val row: Int) {
    companion object {
        const val MAX_LEVEL = 5
        const val CRIT_MULTIPLIER = 2.5f
        const val SQUAD_BASE_HP = 180f
        const val SQUAD_BASE_DPS = 35f
        const val SQUAD_RESPAWN = 6f
        /** Levels at which an accuracy class gains a piercing shot. */
        val PIERCE_LEVELS = listOf(2, 4)
        /** Levels at which a barracks squad gains another soldier. */
        val SQUAD_LEVELS = listOf(3, 5)
    }

    val pos: PointF = GameMap.cellCenter(col, row)

    /** 1..MAX_LEVEL while upgradeable; MAX_LEVEL + 1 once fully upgraded. */
    var level = 1
        private set
    private val boughtThisLevel = BooleanArray(type.stats.size)

    var invested = type.cost
        private set
    var angle = (-Math.PI / 2).toFloat()
        private set
    private var cooldown = 0f

    // barracks state
    val soldiers = mutableListOf<Troop>()
    var respawnTimer = 0f
    var rallyPoint: PointF? = null

    val isMaxed get() = level > MAX_LEVEL
    val displayLevel get() = level.coerceAtMost(MAX_LEVEL)

    /** How many of this level's three upgrades have been bought. */
    val boughtCount get() = boughtThisLevel.count { it }

    fun isBought(i: Int) = boughtThisLevel[i]
    fun canBuy(i: Int) = !isMaxed && !boughtThisLevel[i]

    /** Every upgrade at level L costs the class's base price times L. */
    fun costOf(i: Int) = type.upgradeCost * level

    /** Total times stat [i] has been purchased across all levels so far. */
    private fun purchases(i: Int) = (level - 1) + if (boughtThisLevel[i]) 1 else 0

    fun buy(i: Int) {
        if (!canBuy(i)) return
        invested += costOf(i)
        boughtThisLevel[i] = true
        if (boughtThisLevel.all { it }) {
            // all three bought: promote, and offer a fresh set of three
            level++
            boughtThisLevel.fill(false)
        }
    }

    private fun total(kind: StatKind): Float {
        var t = 0f
        for (i in type.stats.indices) {
            if (type.stats[i].kind == kind) t += type.stats[i].amount * purchases(i)
        }
        return t
    }

    val damage get() = type.baseDamage * (1f + total(StatKind.DAMAGE))
    val fireRate get() = type.baseFireRate * (1f + total(StatKind.FIRE_RATE))
    val range get() = type.baseRange * (1f + total(StatKind.RANGE_PCT)) + total(StatKind.RANGE_FLAT)
    val splash get() = if (type.baseSplash > 0f) type.baseSplash * (1f + total(StatKind.BLAST)) else 0f

    /** Suppression strength as a fraction of speed removed, grown by upgrades. */
    val suppression get() = (1f - type.baseSlowFactor) * (1f + total(StatKind.SUPPRESSION))
    val slowFactor get() = if (type.baseSlowFactor < 1f) (1f - suppression).coerceAtLeast(0.25f) else 1f
    val slowDuration get() = type.slowBaseDuration

    /** Accuracy points; 100 means every shot connects, the excess becomes crit chance. */
    val accuracy get() = type.baseAccuracy * (1f + total(StatKind.ACCURACY))
    val hitChance get() = if (type.hasAccuracy) (accuracy / 100f).coerceAtMost(1f) else 1f
    val critChance get() = if (type.hasAccuracy) ((accuracy - 100f) / 100f).coerceIn(0f, 0.9f) else 0f

    /** Accuracy classes gain a piercing shot on the way into levels 2 and 4. */
    val pierce get() = if (type.hasAccuracy) PIERCE_LEVELS.count { level >= it } else 0

    val squadSize get() =
        if (type.isBarracks) type.baseSquad + SQUAD_LEVELS.count { level >= it } else type.baseSquad
    val squadHp get() = SQUAD_BASE_HP * (1f + total(StatKind.TROOP_HP) + total(StatKind.SQUAD))
    val squadDps get() = SQUAD_BASE_DPS * (1f + total(StatKind.TROOP_DPS))

    val sellValue get() = (invested * 0.7f).roundToInt()

    fun update(dt: Float, zombies: List<Zombie>, projectiles: MutableList<Projectile>) {
        if (type.isBarracks) return
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

        val missed = Random.nextFloat() > hitChance
        val isCrit = !missed && critChance > 0f && Random.nextFloat() < critChance
        val dmg = when {
            missed -> 0f
            isCrit -> damage * CRIT_MULTIPLIER
            else -> damage
        }
        val muzzleX = pos.x + cos(angle) * 34f
        val muzzleY = pos.y + sin(angle) * 34f
        projectiles.add(
            Projectile(
                muzzleX, muzzleY, target, type.projectileSpeed, dmg, type,
                isCrit = isCrit,
                isMiss = missed,
                pierceLeft = if (missed) 0 else pierce,
                slowFactor = if (missed) 1f else slowFactor,
                slowDuration = slowDuration,
                splash = if (missed) 0f else splash
            )
        )
    }
}
