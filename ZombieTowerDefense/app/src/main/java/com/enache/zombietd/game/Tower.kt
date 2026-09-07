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
    BURN_DPS, BURN_DURATION, BURN_ALT, SLOW_DURATION, TROOP_DPS, TROOP_HP, RESPAWN
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
    val baseBurnDps: Float = 0f,      // >0: hits set the target alight
    val baseBurnDuration: Float = 0f,
    val baseSquad: Int = 0,           // >0: barracks squad size
    val baseFreeze: Float = 0f,       // >0: seconds a hit freezes its target solid
    /** False for classes that can only be reached by promoting another class. */
    val buildable: Boolean = true,
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
    FLAME(
        "Flametrooper", "Burns", 200, 100, 140f, 6f, 4f, 0f, 0xFFFF3D00.toInt(),
        baseBurnDps = 10f, baseBurnDuration = 2f,
        stats = listOf(
            UpgradeStat("Burn Damage", "+8% burn damage", StatKind.BURN_DPS, 0.08f),
            UpgradeStat("Incendiary Fuel", "+6% burn time (L1/3/5), burn damage (L2/4)", StatKind.BURN_ALT, 0.06f),
            UpgradeStat("Range", "+15 range", StatKind.RANGE_FLAT, 15f)
        )
    ),
    FROST(
        "Frost Trooper", "Freezes", 600, 160, 260f, 18f, 1.6f, 1300f, 0xFF00E5FF.toInt(),
        baseSlowFactor = 0.55f, slowBaseDuration = 2.5f, baseSplash = 90f,
        baseFreeze = 0.5f, buildable = false,
        stats = listOf(
            UpgradeStat("Freeze Power", "+6% slow strength", StatKind.SUPPRESSION, 0.06f),
            UpgradeStat("Damage", "+8% damage", StatKind.DAMAGE, 0.08f),
            UpgradeStat("Chill Duration", "+10% slow duration", StatKind.SLOW_DURATION, 0.10f)
        )
    ),
    BARRACKS(
        "Barracks", "Melee squad", 200, 100, 170f, 0f, 0f, 0f, 0xFF8BC34A.toInt(),
        baseSquad = 3,
        stats = listOf(
            UpgradeStat("Soldier HP", "+8% soldier health", StatKind.TROOP_HP, 0.08f),
            UpgradeStat("Damage", "+5% soldier damage", StatKind.TROOP_DPS, 0.05f),
            UpgradeStat("Respawn Rate", "-0.5s respawn time", StatKind.RESPAWN, 0.5f)
        )
    );

    val isBarracks get() = baseSquad > 0
    val hasAccuracy get() = baseAccuracy > 0f
    /** Flame classes wash a cone of fire over the trail instead of firing shots. */
    val isFlame get() = baseBurnDps > 0f
    val canFreeze get() = baseFreeze > 0f

    /** The class this one becomes once it is fully upgraded, if any. */
    val promotesTo: TowerType? get() = promotionOf(this)

    companion object {
        val buildable get() = entries.filter { it.buildable }

        // Written as a lookup rather than a constructor argument: an enum constant
        // cannot reference a sibling constant while it is being constructed.
        fun promotionOf(type: TowerType): TowerType? = when (type) {
            SUPPORT -> FROST
            else -> null
        }
    }
}

class Tower(val type: TowerType, val col: Int, val row: Int) {
    companion object {
        const val MAX_LEVEL = 5
        /**
         * Global multiplier on every range value. Class stats below are written in
         * design units (Assault 120, Recon 250); this scales them to the board.
         */
        const val RANGE_SCALE = 1.5f
        const val CRIT_MULTIPLIER = 2.5f
        const val SQUAD_BASE_HP = 180f
        const val SQUAD_BASE_DPS = 35f
        const val SQUAD_RESPAWN = 6f
        /** Levels at which an accuracy class gains a piercing shot. */
        val PIERCE_LEVELS = listOf(2, 4)
        /** Reaching the final level adds a fourth soldier to a barracks squad. */
        val SQUAD_LEVELS = listOf(MAX_LEVEL)
        const val SQUAD_MIN_RESPAWN = 1.5f
        /** From this level a flamer washes everything in a cone, not just one target. */
        const val CONE_LEVEL = 3
        const val CONE_HALF_ANGLE = 0.62f // ~35 degrees either side
        /** From this level a frost trooper's hits freeze their target solid. */
        const val FREEZE_LEVEL = 3
        /** At the final level the freeze catches everything in the burst, not just the target. */
        const val FREEZE_BURST_LEVEL = MAX_LEVEL
        /** At the final level a flamer leaves burning ground behind. */
        const val NAPALM_LEVEL = MAX_LEVEL
        const val NAPALM_INTERVAL = 2f
        const val NAPALM_LIFE = 3f
        const val NAPALM_RADIUS = 55f
        const val NAPALM_DPS_SHARE = 0.6f
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

    // flame state: how long the jet stays lit, and the napalm drop timer
    var flameTimer = 0f
        private set
    private var napalmTimer = 0f

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

    /** Rolls the money sunk into a previous class into this one's sell value. */
    fun carryInvestment(amount: Int) {
        invested += amount
    }

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
    val range get() =
        (type.baseRange * (1f + total(StatKind.RANGE_PCT)) + total(StatKind.RANGE_FLAT)) * RANGE_SCALE
    val splash get() = if (type.baseSplash > 0f) type.baseSplash * (1f + total(StatKind.BLAST)) else 0f

    /** Suppression strength as a fraction of speed removed, grown by upgrades. */
    val suppression get() = (1f - type.baseSlowFactor) * (1f + total(StatKind.SUPPRESSION))
    val slowFactor get() = if (type.baseSlowFactor < 1f) (1f - suppression).coerceAtLeast(0.25f) else 1f
    val slowDuration get() = type.slowBaseDuration * (1f + total(StatKind.SLOW_DURATION))

    /** Seconds a hit stops its target dead; 0 until the freeze milestone. */
    val freezeTime get() = if (type.canFreeze && level >= FREEZE_LEVEL) type.baseFreeze else 0f
    val freezesBurst get() = type.canFreeze && level >= FREEZE_BURST_LEVEL

    /** Accuracy points; 100 means every shot connects, the excess becomes crit chance. */
    val accuracy get() = type.baseAccuracy * (1f + total(StatKind.ACCURACY))
    val hitChance get() = if (type.hasAccuracy) (accuracy / 100f).coerceAtMost(1f) else 1f
    val critChance get() = if (type.hasAccuracy) ((accuracy - 100f) / 100f).coerceIn(0f, 0.9f) else 0f

    /** Accuracy classes gain a piercing shot on the way into levels 2 and 4. */
    val pierce get() = if (type.hasAccuracy) PIERCE_LEVELS.count { level >= it } else 0

    val squadSize get() =
        if (type.isBarracks) type.baseSquad + SQUAD_LEVELS.count { level >= it } else type.baseSquad
    val squadHp get() = SQUAD_BASE_HP * (1f + total(StatKind.TROOP_HP))
    val squadDps get() = SQUAD_BASE_DPS * (1f + total(StatKind.TROOP_DPS))
    /**
     * The alternating fuel stat pays out differently depending on the level it was
     * bought at: odd levels lengthen the burn, even levels deepen it. Each stat is
     * bought exactly once per level, so the levels it covers are simply every level
     * already completed plus the current one if it has been taken.
     */
    private fun altPurchases(): Pair<Int, Int> {
        val idx = type.stats.indexOfFirst { it.kind == StatKind.BURN_ALT }
        if (idx < 0) return 0 to 0
        var longer = 0
        var hotter = 0
        for (lvl in 1..MAX_LEVEL) {
            val bought = lvl < level || (lvl == level && boughtThisLevel[idx])
            if (!bought) continue
            if (lvl % 2 == 1) longer++ else hotter++
        }
        return longer to hotter
    }

    private val altAmount get() = type.stats.firstOrNull { it.kind == StatKind.BURN_ALT }?.amount ?: 0f

    val burnDps get() =
        type.baseBurnDps * (1f + total(StatKind.BURN_DPS) + altAmount * altPurchases().second)
    val burnDuration get() =
        type.baseBurnDuration * (1f + total(StatKind.BURN_DURATION) + altAmount * altPurchases().first)
    val hasCone get() = type.isFlame && level >= CONE_LEVEL
    val hasNapalm get() = type.isFlame && level >= NAPALM_LEVEL

    val squadRespawn get() = (SQUAD_RESPAWN - total(StatKind.RESPAWN)).coerceAtLeast(SQUAD_MIN_RESPAWN)

    val sellValue get() = (invested * 0.7f).roundToInt()

    fun update(
        dt: Float,
        zombies: List<Zombie>,
        projectiles: MutableList<Projectile>,
        fires: MutableList<Fire>? = null
    ) {
        if (type.isBarracks) return
        cooldown -= dt
        if (flameTimer > 0f) flameTimer -= dt
        if (napalmTimer > 0f) napalmTimer -= dt

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

        if (type.isFlame) {
            flameTimer = 1f / fireRate
            val burn = burnDps
            val dur = burnDuration
            val dmg = damage
            for (z in zombies) {
                if (!z.alive) continue
                val dx = z.pos.x - pos.x
                val dy = z.pos.y - pos.y
                if (hypot(dx, dy) > r) continue
                // before the cone upgrade only the aimed-at zombie is hit
                if (z !== target) {
                    if (!hasCone) continue
                    var delta = atan2(dy, dx) - angle
                    while (delta > Math.PI) delta -= (2 * Math.PI).toFloat()
                    while (delta < -Math.PI) delta += (2 * Math.PI).toFloat()
                    if (kotlin.math.abs(delta) > CONE_HALF_ANGLE) continue
                }
                z.hp -= dmg
                z.applyBurn(burn, dur)
            }
            if (hasNapalm && fires != null && napalmTimer <= 0f) {
                napalmTimer = NAPALM_INTERVAL
                fires.add(Fire(target.pos.x, target.pos.y, NAPALM_RADIUS, burn * NAPALM_DPS_SHARE, NAPALM_LIFE))
            }
            return
        }

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
                splash = if (missed) 0f else splash,
                freeze = if (missed) 0f else freezeTime,
                freezeBurst = freezesBurst
            )
        )
    }
}
