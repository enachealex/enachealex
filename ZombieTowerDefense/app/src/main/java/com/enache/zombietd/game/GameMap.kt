package com.enache.zombietd.game

import android.graphics.PointF
import kotlin.random.Random

/** A piece of scenery occupying one grid cell. Scenery cells are never build pads. */
class Decor(val kind: Kind, val c: Int, val r: Int, val seed: Int) {
    enum class Kind { TREE, PINE, ROCK, HOUSE }
}

/** A prepared emplacement: the only place a tower may be built. */
class Pad(val c: Int, val r: Int) {
    val pos: PointF = GameMap.cellCenter(c, r)
}

/**
 * A playable map: one or more zombie lanes over a fixed grid, plus optional
 * water tiles and scenery. Where a lane crosses water it is drawn as a bridge.
 * Everything that is neither path, water, nor scenery is buildable ground.
 */
class GameMap(
    val name: String,
    val difficulty: String,
    pathsCells: List<List<Pair<Int, Int>>>,
    val water: Set<Pair<Int, Int>> = emptySet()
) {
    companion object {
        const val COLS = 10
        const val ROWS = 14
        const val TILE = 108f
        const val TOP = 120f // height of the HUD bar above the play field
        const val DECOR_COUNT = 9
        const val MAX_PADS = 12

        fun cellCenter(c: Int, r: Int) = PointF((c + 0.5f) * TILE, TOP + (r + 0.5f) * TILE)

        /** Turns a corner list into the ordered list of every cell the lane passes through. */
        private fun expand(corners: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val out = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until corners.size - 1) {
                var (c, r) = corners[i]
                val (c1, r1) = corners[i + 1]
                val dc = Integer.signum(c1 - c)
                val dr = Integer.signum(r1 - r)
                if (out.isEmpty()) out.add(c to r)
                while (c != c1 || r != r1) {
                    c += dc
                    r += dr
                    out.add(c to r)
                }
            }
            return out
        }
    }

    /** Pixel waypoints for each lane a zombie can walk. */
    val paths: List<List<PointF>> = pathsCells.map { lane -> lane.map { (c, r) -> cellCenter(c, r) } }

    private val expandedLanes = pathsCells.map { expand(it) }

    val pathCells: Set<Pair<Int, Int>> = expandedLanes.flatten().toSet()
    val bridgeCells: Set<Pair<Int, Int>> = pathCells intersect water
    val entryCells: Set<Pair<Int, Int>> =
        expandedLanes.mapNotNull { lane -> lane.firstOrNull { (c, r) -> isInside(c, r) } }.toSet()
    val exitCells: Set<Pair<Int, Int>> =
        expandedLanes.mapNotNull { lane -> lane.lastOrNull { (c, r) -> isInside(c, r) } }.toSet()

    /**
     * Prepared emplacements. Towers can only be built here, so they are placed
     * deterministically: cells that touch the trail (so they can actually shoot),
     * never on it, and never adjacent to each other so each pad reads clearly.
     */
    val pads: List<Pad> = buildList {
        val taken = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            if (size >= MAX_PADS) break
            val key = c to r
            if (key in pathCells || key in water) continue
            // must touch the trail (8-neighbourhood)
            var touchesTrail = false
            for (dr in -1..1) for (dc in -1..1) {
                if (dc == 0 && dr == 0) continue
                if ((c + dc to r + dr) in pathCells) touchesTrail = true
            }
            if (!touchesTrail) continue
            // keep pads apart so each one is its own decision
            if (taken.any { (tc, tr) -> maxOf(kotlin.math.abs(tc - c), kotlin.math.abs(tr - r)) < 2 }) continue
            taken.add(key)
            add(Pad(c, r))
        }
    }

    val padCells: Set<Pair<Int, Int>> = pads.map { it.c to it.r }.toSet()

    /** Trees, rocks and buildings scattered deterministically over free ground. */
    val decor: List<Decor> = buildList {
        val rnd = Random(name.hashCode())
        val free = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val key = c to r
            if (key !in pathCells && key !in water && key !in padCells) free.add(key)
        }
        free.shuffle(rnd)
        val kinds = listOf(
            Decor.Kind.TREE, Decor.Kind.TREE, Decor.Kind.ROCK, Decor.Kind.PINE, Decor.Kind.TREE,
            Decor.Kind.HOUSE, Decor.Kind.PINE, Decor.Kind.ROCK, Decor.Kind.TREE
        )
        for (i in 0 until minOf(DECOR_COUNT, free.size)) {
            val (c, r) = free[i]
            add(Decor(kinds[i % kinds.size], c, r, rnd.nextInt()))
        }
    }

    val blocked: Set<Pair<Int, Int>> = decor.map { it.c to it.r }.toSet()

    fun isInside(c: Int, r: Int) = c in 0 until COLS && r in 0 until ROWS

    /** Towers may only stand on a prepared pad. */
    fun padAt(c: Int, r: Int): Pad? = pads.firstOrNull { it.c == c && it.r == r }

    /** Centre of the trail cell nearest to a point (used as a barracks rally point). */
    fun nearestPathPoint(x: Float, y: Float): PointF {
        var best: PointF? = null
        var bestD = Float.MAX_VALUE
        for ((c, r) in pathCells) {
            if (!isInside(c, r)) continue
            val p = cellCenter(c, r)
            val d = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y)
            if (d < bestD) {
                bestD = d
                best = p
            }
        }
        return best ?: paths[0][1]
    }
}

object Maps {
    val longRoad = GameMap(
        "The Long Road", "Normal",
        listOf(listOf(-1 to 1, 8 to 1, 8 to 4, 1 to 4, 1 to 7, 8 to 7, 8 to 10, 1 to 10, 1 to 12, 5 to 12, 5 to 14))
    )
    val river = GameMap(
        "River Crossing", "Normal",
        listOf(listOf(-1 to 2, 7 to 2, 7 to 4, 4 to 4, 4 to 9, 2 to 9, 2 to 11, 7 to 11, 7 to 14)),
        water = buildSet {
            for (c in 0 until GameMap.COLS) {
                add(c to 6)
                add(c to 7)
            }
        }
    )
    val fork = GameMap(
        "The Fork", "Hard",
        listOf(
            listOf(-1 to 7, 2 to 7, 2 to 2, 7 to 2, 7 to 14),
            listOf(-1 to 7, 2 to 7, 2 to 11, 7 to 11, 7 to 14)
        )
    )
    val spiral = GameMap(
        "Death Spiral", "Easy",
        listOf(listOf(-1 to 0, 8 to 0, 8 to 13, 1 to 13, 1 to 2, 6 to 2, 6 to 11, 3 to 11, 3 to 4, 4 to 4, 4 to 8))
    )

    val all = listOf(longRoad, river, fork, spiral)
}
