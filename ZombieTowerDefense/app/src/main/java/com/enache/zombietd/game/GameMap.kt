package com.enache.zombietd.game

import android.graphics.PointF

/**
 * Fixed grid map. The zombie path snakes down the board following
 * [waypointCells]; every other tile is buildable grass.
 */
object GameMap {
    const val COLS = 9
    const val ROWS = 13
    const val TILE = 120f
    const val TOP = 120f // height of the HUD bar above the play field

    /** Path corners in (col, row) grid coordinates. First cell is off-screen left, last off-screen bottom. */
    val waypointCells = listOf(
        -1 to 1, 7 to 1, 7 to 4, 1 to 4, 1 to 7, 7 to 7, 7 to 10, 1 to 10, 1 to 13
    )

    /** Path corners in virtual pixel coordinates. */
    val waypoints: List<PointF> = waypointCells.map { (c, r) -> cellCenter(c, r) }

    /** Every grid cell the path passes through. */
    val pathCells: Set<Pair<Int, Int>> = buildSet {
        for (i in 0 until waypointCells.size - 1) {
            var (c, r) = waypointCells[i]
            val (c1, r1) = waypointCells[i + 1]
            val dc = Integer.signum(c1 - c)
            val dr = Integer.signum(r1 - r)
            add(c to r)
            while (c != c1 || r != r1) {
                c += dc
                r += dr
                add(c to r)
            }
        }
    }

    fun cellCenter(c: Int, r: Int) = PointF((c + 0.5f) * TILE, TOP + (r + 0.5f) * TILE)

    fun isInside(c: Int, r: Int) = c in 0 until COLS && r in 0 until ROWS

    fun isBuildable(c: Int, r: Int) = isInside(c, r) && (c to r) !in pathCells
}
