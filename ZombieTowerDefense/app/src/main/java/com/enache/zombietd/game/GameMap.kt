package com.enache.zombietd.game

import android.graphics.PointF

/**
 * A playable map: one or more zombie lanes over a fixed grid, plus optional
 * water tiles. Where a lane crosses water it is drawn as a bridge. Everything
 * that is neither path nor water is buildable grass.
 */
class GameMap(
    val name: String,
    val difficulty: String,
    pathsCells: List<List<Pair<Int, Int>>>,
    val water: Set<Pair<Int, Int>> = emptySet()
) {
    companion object {
        const val COLS = 9
        const val ROWS = 13
        const val TILE = 120f
        const val TOP = 120f // height of the HUD bar above the play field

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

    fun isInside(c: Int, r: Int) = c in 0 until COLS && r in 0 until ROWS

    fun isBuildable(c: Int, r: Int) =
        isInside(c, r) && (c to r) !in pathCells && (c to r) !in water
}

object Maps {
    val all = listOf(
        GameMap(
            "The Long Road", "Normal",
            listOf(listOf(-1 to 1, 7 to 1, 7 to 4, 1 to 4, 1 to 7, 7 to 7, 7 to 10, 1 to 10, 1 to 13))
        ),
        GameMap(
            "River Crossing", "Normal",
            listOf(listOf(-1 to 2, 6 to 2, 6 to 4, 4 to 4, 4 to 9, 2 to 9, 2 to 11, 6 to 11, 6 to 13)),
            water = buildSet {
                for (c in 0 until GameMap.COLS) {
                    add(c to 6)
                    add(c to 7)
                }
            }
        ),
        GameMap(
            "The Fork", "Hard",
            listOf(
                listOf(-1 to 6, 2 to 6, 2 to 2, 6 to 2, 6 to 13),
                listOf(-1 to 6, 2 to 6, 2 to 10, 6 to 10, 6 to 13)
            )
        ),
        GameMap(
            "Death Spiral", "Easy",
            listOf(listOf(-1 to 0, 7 to 0, 7 to 12, 1 to 12, 1 to 2, 5 to 2, 5 to 10, 3 to 10, 3 to 4, 4 to 4, 4 to 7))
        )
    )
}
