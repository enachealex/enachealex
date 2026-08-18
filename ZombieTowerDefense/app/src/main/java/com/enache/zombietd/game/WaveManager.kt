package com.enache.zombietd.game

import kotlin.math.max

/**
 * Waves scale by quantity, not health: every zombie has the same HP all game,
 * later waves just send a lot more of them, faster.
 */
class WaveManager {
    var wave = 0
        private set

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

    fun startNextWave() {
        wave++
        interval = max(0.30f, 0.9f - wave * 0.03f)

        val list = mutableListOf<ZombieType>()
        repeat(6 + wave * 3) { list.add(ZombieType.WALKER) }
        if (wave >= 3) repeat((wave - 2) * 2) { list.add(ZombieType.RUNNER) }
        list.shuffle()

        if (wave >= 5) {
            val brutes = wave - 4
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
