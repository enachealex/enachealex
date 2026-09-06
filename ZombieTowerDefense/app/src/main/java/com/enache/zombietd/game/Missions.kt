package com.enache.zombietd.game

/** One campaign mission: a map, how many waves to survive, and how big the horde is. */
class Mission(
    val index: Int,
    val title: String,
    val map: GameMap,
    val waves: Int,
    val hordeScale: Float
) {
    val number get() = index + 1
}

object Missions {
    val all = listOf(
        Mission(0, "First Contact", Maps.longRoad, 8, 1.0f),
        Mission(1, "Hold the Bridge", Maps.river, 10, 1.15f),
        Mission(2, "Two Fronts", Maps.fork, 10, 1.3f),
        Mission(3, "Into the Spiral", Maps.spiral, 12, 1.45f),
        Mission(4, "Road of Bones", Maps.longRoad, 14, 1.6f),
        Mission(5, "Flooded Out", Maps.river, 16, 1.8f),
        Mission(6, "Pincer", Maps.fork, 18, 2.0f),
        Mission(7, "Last Stand", Maps.spiral, 20, 2.3f)
    )
}

/** Persistent player progress. The app backs this with SharedPreferences; tests use memory. */
interface Progress {
    /** Number of campaign missions unlocked (at least 1). */
    fun unlockedMissions(): Int
    fun setUnlockedMissions(count: Int)
}

class MemoryProgress : Progress {
    private var unlocked = 1
    override fun unlockedMissions() = unlocked
    override fun setUnlockedMissions(count: Int) {
        unlocked = count.coerceIn(1, Missions.all.size)
    }
}
