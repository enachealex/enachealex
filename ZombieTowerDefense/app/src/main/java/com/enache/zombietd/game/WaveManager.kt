package com.enache.zombietd.game

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Waves scale by quantity, not health: every zombie has the same HP all game,
 * later waves just send a lot more of them, faster. [hordeScale] multiplies
 * every count so later campaign missions field bigger hordes from wave 1.
 */
class WaveManager {
    var wave = 0
        private set
    var hordeScale = 1f

    private val queue = ArrayDeque<ZombieType>()
    private var spawnTimer = 0f
    private var interval = 0.9f

    val queued get() = queue.size
    val spawning get() = queue.isNotEmpty()

    fun reset() {
        wave = 0
        queue.clear()
        spawnTimer = 0f
    }

    private fun scaled(n: Int) = (n * hordeScale).roundToInt()

    fun startNextWave() {
        wave++
        interval = max(0.30f, 0.9f - wave * 0.03f) / hordeScale.coerceAtLeast(1f)

        val list = mutableListOf<ZombieType>()
        repeat(scaled(6 + wave * 3)) { list.add(ZombieType.WALKER) }
        if (wave >= 3) repeat(scaled((wave - 2) * 2)) { list.add(ZombieType.RUNNER) }
        list.shuffle()

        if (wave >= 5) {
            val brutes = scaled(wave - 4)
            repeat(brutes) { i ->
                val index = ((i + 1) * list.size / (brutes + 1)).coerceAtMost(list.size)
                list.add(index, ZombieType.BRUTE)
            }
        }
        if (wave % 5 == 0) repeat(wave / 5) { list.add(ZombieType.BOSS) }

        queue.addAll(list)
        spawnTimer = 0.5f
    }

    fun update(dt: Float, spawn: (ZombieType) -> Unit) {
        if (queue.isEmpty()) return
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            val type = queue.removeFirst()
            spawn(type)
            spawnTimer = interval * if (type == ZombieType.BOSS || queue.firstOrNull() == ZombieType.BOSS) 2.5f else 1f
        }
    }
}
