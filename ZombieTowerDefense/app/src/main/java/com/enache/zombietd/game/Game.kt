package com.enache.zombietd.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class Game {

    companion object {
        const val VIRTUAL_W = 1080f
        const val VIRTUAL_H = 1920f
        const val FINAL_WAVE = 20
        const val START_MONEY = 350
        const val START_LIVES = 20
        const val SPLASH_FALLOFF = 0.6f
    }

    enum class State { MENU, PLAYING, GAME_OVER, VICTORY }

    var state = State.MENU
        private set
    private var paused = false

    private var map = Maps.all[0]
    private var money = START_MONEY
    private var lives = START_LIVES
    private var speed = 1
    private var endlessMode = false
    private var waveActive = false
    private var shake = 0f

    private val zombies = mutableListOf<Zombie>()
    private val towers = mutableListOf<Tower>()
    private val projectiles = mutableListOf<Projectile>()
    private val effects = mutableListOf<Effect>()
    private val waves = WaveManager()

    private var selectedCell: Pair<Int, Int>? = null
    private var selectedTower: Tower? = null

    // ---- static layout (virtual coordinates) ----
    private val panelTop = GameMap.TOP + GameMap.ROWS * GameMap.TILE // 1680
    private val pauseRect = RectF(VIRTUAL_W - 110f, 15f, VIRTUAL_W - 20f, 105f)
    private val speedRect = RectF(VIRTUAL_W - 220f, 15f, VIRTUAL_W - 130f, 105f)
    private val startWaveRect = RectF(40f, panelTop + 40f, 620f, panelTop + 200f)
    private val continueRect = RectF(140f, 1050f, 940f, 1190f)
    private val menuButtonRect = RectF(140f, 1240f, 940f, 1380f)

    private val buildPanelRect = RectF(10f, 1230f, 1070f, 1910f)
    private val towerPanelRect = RectF(10f, 1140f, 1070f, 1910f)
    private val sellRect = RectF(760f, 1165f, 1040f, 1245f)

    private fun buildCardRect(i: Int): RectF {
        val col = i % 3
        val row = i / 3
        return RectF(30f + col * 350f, 1330f + row * 290f, 30f + col * 350f + 330f, 1330f + row * 290f + 260f)
    }

    private fun upgradeRowY(i: Int) = 1350f + i * 180f
    private fun upgradeButtonRect(i: Int): RectF {
        val y = upgradeRowY(i)
        return RectF(750f, y + 25f, 1040f, y + 130f)
    }

    private fun mapCardRect(i: Int): RectF {
        val col = i % 2
        val row = i / 2
        return RectF(25f + col * 525f, 570f + row * 460f, 25f + col * 525f + 505f, 570f + row * 460f + 430f)
    }

    // ---- paints ----
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }

    fun onAppPause() {
        if (state == State.PLAYING) paused = true
    }

    // ================================================================== update

    fun update(dt: Float) {
        for (e in effects) e.update(dt)
        effects.removeAll { it.done }
        if (state != State.PLAYING || paused) return
        repeat(speed) { step(dt) }
    }

    private fun step(dt: Float) {
        if (shake > 0f) shake -= dt

        waves.update(dt) { type -> zombies.add(Zombie(type, map.paths.random())) }

        for (z in zombies) z.update(dt)

        for (t in towers) {
            t.update(dt, zombies, projectiles) { targets, dmg ->
                var sx = t.pos.x
                var sy = t.pos.y
                var d = dmg
                for (z in targets) {
                    effects.add(Effect.bolt(sx, sy, z.pos.x, z.pos.y))
                    z.hp -= d
                    sx = z.pos.x
                    sy = z.pos.y
                    d *= Tower.CHAIN_FALLOFF
                }
            }
        }

        val pIt = projectiles.iterator()
        while (pIt.hasNext()) {
            val p = pIt.next()
            if (!p.update(dt)) continue

            val z = p.target
            if (z.alive && z !in p.hitAlready) {
                applyHit(z, p)
                p.hitAlready.add(z)
            }
            if (p.splash > 0f) {
                for (other in zombies) {
                    if (other === z || !other.alive) continue
                    if (hypot(other.pos.x - p.x, other.pos.y - p.y) <= p.splash) {
                        other.hp -= p.damage * SPLASH_FALLOFF
                    }
                }
                effects.add(Effect.splat(p.x, p.y, p.splash * 0.7f))
            }

            var remove = true
            if (p.pierceLeft > 0) {
                var next: Zombie? = null
                var bestD = 280f
                for (cand in zombies) {
                    if (!cand.alive || cand in p.hitAlready) continue
                    val d = hypot(cand.pos.x - p.x, cand.pos.y - p.y)
                    if (d < bestD) {
                        bestD = d
                        next = cand
                    }
                }
                if (next != null) {
                    p.pierceLeft--
                    p.retarget(next)
                    remove = false
                }
            }
            if (remove) pIt.remove()
        }

        val zIt = zombies.iterator()
        while (zIt.hasNext()) {
            val z = zIt.next()
            if (z.hp <= 0f) {
                if (!z.rewarded) {
                    z.rewarded = true
                    money += z.type.reward
                    effects.add(Effect.splat(z.pos.x, z.pos.y, z.type.radius * 1.2f))
                    effects.add(Effect.text(z.pos.x, z.pos.y - 40f, "+$${z.type.reward}", 0xFFFFD54F.toInt()))
                }
                zIt.remove()
            } else if (z.reachedEnd) {
                lives -= z.type.livesCost
                shake = 0.35f
                zIt.remove()
                if (lives <= 0) {
                    lives = 0
                    state = State.GAME_OVER
                    selectedCell = null
                    selectedTower = null
                }
            }
        }

        if (waveActive && !waves.spawning && zombies.isEmpty() && state == State.PLAYING) {
            waveActive = false
            val bonus = 60 + waves.wave * 8
            money += bonus
            effects.add(Effect.text(VIRTUAL_W / 2f, 760f, "Wave ${waves.wave} cleared!  +$$bonus", 0xFF81C784.toInt(), 56f))
            if (waves.wave >= FINAL_WAVE && !endlessMode) {
                state = State.VICTORY
                selectedCell = null
                selectedTower = null
            }
        }
    }

    private fun applyHit(z: Zombie, p: Projectile) {
        z.hp -= p.damage
        if (p.slowFactor < 1f) z.applySlow(p.slowFactor, p.slowDuration)
        if (p.burnDps > 0f) z.applyBurn(p.burnDps, 2f)
        if (p.isCrit) {
            effects.add(Effect.text(z.pos.x, z.pos.y - 60f, "CRIT!", 0xFFFF9100.toInt(), 40f))
        }
    }

    // ================================================================== input

    fun onTap(x: Float, y: Float) {
        when (state) {
            State.MENU -> {
                for (i in Maps.all.indices) {
                    if (mapCardRect(i).contains(x, y)) {
                        startNewGame(Maps.all[i])
                        return
                    }
                }
            }
            State.GAME_OVER -> state = State.MENU
            State.VICTORY -> when {
                continueRect.contains(x, y) -> {
                    endlessMode = true
                    state = State.PLAYING
                }
                menuButtonRect.contains(x, y) -> state = State.MENU
            }
            State.PLAYING -> handlePlayingTap(x, y)
        }
    }

    private fun handlePlayingTap(x: Float, y: Float) {
        if (pauseRect.contains(x, y)) {
            paused = !paused
            return
        }
        if (paused) {
            paused = false
            return
        }
        if (speedRect.contains(x, y)) {
            speed = if (speed == 1) 2 else 1
            return
        }

        val cell = selectedCell
        if (cell != null) {
            handleBuildPanelTap(x, y, cell)
            return
        }
        val tower = selectedTower
        if (tower != null) {
            handleTowerPanelTap(x, y, tower)
            return
        }

        if (y >= panelTop) {
            if (startWaveRect.contains(x, y) && !waveActive && (endlessMode || waves.wave < FINAL_WAVE)) {
                waves.startNextWave()
                waveActive = true
            }
            return
        }
        if (y < GameMap.TOP) return

        val c = (x / GameMap.TILE).toInt()
        val r = ((y - GameMap.TOP) / GameMap.TILE).toInt()
        val hit = towers.find { it.col == c && it.row == r }
        when {
            hit != null -> selectedTower = hit
            map.isBuildable(c, r) -> selectedCell = c to r
        }
    }

    private fun handleBuildPanelTap(x: Float, y: Float, cell: Pair<Int, Int>) {
        if (!buildPanelRect.contains(x, y)) {
            selectedCell = null
            return
        }
        for (i in TowerType.entries.indices) {
            if (buildCardRect(i).contains(x, y)) {
                val type = TowerType.entries[i]
                if (money >= type.cost) {
                    money -= type.cost
                    towers.add(Tower(type, cell.first, cell.second))
                    selectedCell = null
                } else {
                    effects.add(Effect.text(VIRTUAL_W / 2f, 1290f, "Need $${type.cost}", 0xFFEF5350.toInt()))
                }
                return
            }
        }
    }

    private fun handleTowerPanelTap(x: Float, y: Float, tower: Tower) {
        if (!towerPanelRect.contains(x, y)) {
            selectedTower = null
            return
        }
        if (sellRect.contains(x, y)) {
            money += tower.sellValue
            towers.remove(tower)
            effects.add(Effect.text(tower.pos.x, tower.pos.y - 50f, "+$${tower.sellValue}", 0xFFFFD54F.toInt()))
            selectedTower = null
            return
        }
        for (i in tower.type.upgrades.indices) {
            if (upgradeButtonRect(i).contains(x, y)) {
                if (tower.canRankUp(i) && money >= tower.upgradeCost(i)) {
                    money -= tower.upgradeCost(i)
                    tower.rankUp(i)
                    effects.add(Effect.text(tower.pos.x, tower.pos.y - 50f, tower.type.upgrades[i].name, 0xFF81C784.toInt()))
                }
                return
            }
        }
    }

    private fun startNewGame(selected: GameMap) {
        map = selected
        money = START_MONEY
        lives = START_LIVES
        speed = 1
        endlessMode = false
        waveActive = false
        paused = false
        shake = 0f
        zombies.clear()
        towers.clear()
        projectiles.clear()
        effects.clear()
        waves.reset()
        selectedCell = null
        selectedTower = null
        state = State.PLAYING
    }

    // ================================================================== draw

    fun draw(canvas: Canvas) {
        if (state == State.MENU) {
            drawMenu(canvas)
            return
        }

        canvas.save()
        if (shake > 0f) {
            canvas.translate((Random.nextFloat() - 0.5f) * 16f, (Random.nextFloat() - 0.5f) * 16f)
        }
        drawMap(canvas)
        drawSplats(canvas)
        drawTowers(canvas)
        drawZombies(canvas)
        drawProjectiles(canvas)
        drawBolts(canvas)
        drawTexts(canvas)
        canvas.restore()

        drawHud(canvas)
        drawPanel(canvas)

        when (state) {
            State.GAME_OVER -> drawGameOverOverlay(canvas)
            State.VICTORY -> drawVictoryOverlay(canvas)
            State.PLAYING -> if (paused) drawPausedOverlay(canvas)
            else -> {}
        }
    }

    // ---------------------------------------------------------------- menu

    private fun drawMenu(canvas: Canvas) {
        fillPaint.color = 0xFF10150F.toInt()
        canvas.drawRect(0f, 0f, VIRTUAL_W, VIRTUAL_H, fillPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 96f
        textPaint.color = 0xFF7CB342.toInt()
        canvas.drawText("ZOMBIE DEFENSE", VIRTUAL_W / 2f, 300f, textPaint)
        textPaint.textSize = 42f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("CHOOSE YOUR MAP", VIRTUAL_W / 2f, 460f, textPaint)

        for (i in Maps.all.indices) {
            drawMapCard(canvas, Maps.all[i], mapCardRect(i))
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 30f
        textPaint.color = 0xFF78909C.toInt()
        canvas.drawText("Build towers on grass  •  Earn cash for kills", VIRTUAL_W / 2f, 1620f, textPaint)
        canvas.drawText("Each tower has its own upgrade paths", VIRTUAL_W / 2f, 1675f, textPaint)
        canvas.drawText("Survive all $FINAL_WAVE waves to save the city", VIRTUAL_W / 2f, 1730f, textPaint)
    }

    private fun drawMapCard(canvas: Canvas, m: GameMap, rect: RectF) {
        fillPaint.color = 0xFF1C262B.toInt()
        canvas.drawRoundRect(rect, 20f, 20f, fillPaint)
        strokePaint.color = 0xFF37474F.toInt()
        strokePaint.strokeWidth = 4f
        canvas.drawRoundRect(rect, 20f, 20f, strokePaint)

        // mini preview of the board
        val cellSize = 26f
        val px = rect.left + 24f
        val py = rect.top + (rect.height() - GameMap.ROWS * cellSize) / 2f
        for (r in 0 until GameMap.ROWS) {
            for (c in 0 until GameMap.COLS) {
                val key = c to r
                fillPaint.color = when {
                    key in m.bridgeCells -> 0xFF7A5C3E.toInt()
                    key in m.water -> 0xFF2A5A74.toInt()
                    key in m.pathCells -> 0xFF6D5B45.toInt()
                    else -> 0xFF39543A.toInt()
                }
                canvas.drawRect(
                    px + c * cellSize, py + r * cellSize,
                    px + (c + 1) * cellSize - 1f, py + (r + 1) * cellSize - 1f, fillPaint
                )
            }
        }
        for ((c, r) in m.entryCells) {
            fillPaint.color = 0xFF81C784.toInt()
            canvas.drawCircle(px + (c + 0.5f) * cellSize, py + (r + 0.5f) * cellSize, 7f, fillPaint)
        }
        for ((c, r) in m.exitCells) {
            fillPaint.color = 0xFFE53935.toInt()
            canvas.drawCircle(px + (c + 0.5f) * cellSize, py + (r + 0.5f) * cellSize, 7f, fillPaint)
        }

        // name and difficulty to the right of the preview
        val tx = px + GameMap.COLS * cellSize + 28f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 36f
        textPaint.color = Color.WHITE
        var ty = rect.top + 90f
        for (word in m.name.split(" ")) {
            canvas.drawText(word, tx, ty, textPaint)
            ty += 46f
        }
        textPaint.textSize = 30f
        textPaint.color = when (m.difficulty) {
            "Easy" -> 0xFF81C784.toInt()
            "Hard" -> 0xFFEF5350.toInt()
            else -> 0xFFFFD54F.toInt()
        }
        canvas.drawText(m.difficulty, tx, ty + 16f, textPaint)
    }

    // ---------------------------------------------------------------- world

    private fun drawMap(canvas: Canvas) {
        for (r in 0 until GameMap.ROWS) {
            for (c in 0 until GameMap.COLS) {
                val left = c * GameMap.TILE
                val top = GameMap.TOP + r * GameMap.TILE
                val key = c to r
                val even = (c + r) % 2 == 0
                fillPaint.color = when {
                    key in map.bridgeCells -> 0xFF7A5C3E.toInt()
                    key in map.water -> if (even) 0xFF2A5A74.toInt() else 0xFF275470.toInt()
                    key in map.pathCells -> if (even) 0xFF6D5B45.toInt() else 0xFF66553F.toInt()
                    else -> if (even) 0xFF39543A.toInt() else 0xFF344E35.toInt()
                }
                canvas.drawRect(left, top, left + GameMap.TILE, top + GameMap.TILE, fillPaint)

                if (key in map.bridgeCells) {
                    // plank lines and side rails
                    fillPaint.color = 0xFF5D4630.toInt()
                    for (i in 1..3) {
                        val y = top + i * GameMap.TILE / 4f
                        canvas.drawRect(left + 6f, y - 4f, left + GameMap.TILE - 6f, y + 4f, fillPaint)
                    }
                    fillPaint.color = 0xFF4A3826.toInt()
                    canvas.drawRect(left, top, left + 8f, top + GameMap.TILE, fillPaint)
                    canvas.drawRect(left + GameMap.TILE - 8f, top, left + GameMap.TILE, top + GameMap.TILE, fillPaint)
                }
            }
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 64f
        textPaint.color = 0xFFD7CCC8.toInt()
        for ((c, r) in map.entryCells) {
            val p = GameMap.cellCenter(c, r)
            canvas.drawText("➡", p.x, p.y + 22f, textPaint)
        }
        for ((c, r) in map.exitCells) {
            val p = GameMap.cellCenter(c, r)
            canvas.drawText("☠", p.x, p.y + 22f, textPaint)
        }

        val cell = selectedCell
        if (cell != null) {
            val (c, r) = cell
            val left = c * GameMap.TILE
            val top = GameMap.TOP + r * GameMap.TILE
            fillPaint.color = 0x5AFFFFFF
            canvas.drawRect(left, top, left + GameMap.TILE, top + GameMap.TILE, fillPaint)
            strokePaint.color = Color.WHITE
            strokePaint.strokeWidth = 4f
            canvas.drawRect(left, top, left + GameMap.TILE, top + GameMap.TILE, strokePaint)
        }
        val tower = selectedTower
        if (tower != null) {
            fillPaint.color = 0x2264B5F6
            canvas.drawCircle(tower.pos.x, tower.pos.y, tower.range, fillPaint)
            strokePaint.color = 0xAA64B5F6.toInt()
            strokePaint.strokeWidth = 3f
            canvas.drawCircle(tower.pos.x, tower.pos.y, tower.range, strokePaint)
        }
    }

    private fun drawSplats(canvas: Canvas) {
        for (e in effects) {
            if (e.kind != Effect.Kind.SPLAT) continue
            fillPaint.color = e.color
            fillPaint.alpha = e.alpha / 2
            canvas.drawCircle(e.x, e.y, e.size, fillPaint)
        }
        fillPaint.alpha = 255
    }

    private fun drawTowers(canvas: Canvas) {
        for (t in towers) {
            // emplacement pad with a type-colored rim
            fillPaint.color = 0xFF37474F.toInt()
            canvas.drawCircle(t.pos.x, t.pos.y, 48f, fillPaint)
            strokePaint.color = t.type.color
            strokePaint.strokeWidth = 4f
            canvas.drawCircle(t.pos.x, t.pos.y, 48f, strokePaint)

            drawSoldier(canvas, t.pos.x, t.pos.y, t.angle, t.type, 0.92f)

            // one pip per purchased upgrade rank
            fillPaint.color = 0xFFFFD54F.toInt()
            val pips = min(t.totalRanks, 9)
            for (i in 0 until pips) {
                val row = i / 3
                val col = i % 3
                canvas.drawCircle(t.pos.x - 14f + col * 14f, t.pos.y + 46f + row * 13f, 5f, fillPaint)
            }
        }
    }

    // camo blotches as (x, y, radius) fractions of the helmet radius — fixed so they never flicker
    private val camoSpots = arrayOf(
        floatArrayOf(-0.35f, -0.25f, 0.30f),
        floatArrayOf(0.32f, -0.38f, 0.26f),
        floatArrayOf(0.05f, 0.34f, 0.30f),
        floatArrayOf(-0.48f, 0.30f, 0.22f),
        floatArrayOf(0.48f, 0.12f, 0.20f)
    )

    /**
     * Top-down cartoon soldier (helmet, backpack, pouches, weapon) drawn facing
     * [angleRad]. Local space faces up (-y): the backpack sits behind at +y and
     * the weapon points forward at -y.
     */
    private fun drawSoldier(canvas: Canvas, x: Float, y: Float, angleRad: Float, type: TowerType, scale: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(Math.toDegrees(angleRad.toDouble()).toFloat() + 90f)
        canvas.scale(scale, scale)

        // shoulders / torso
        fillPaint.color = 0xFF5F6E42.toInt()
        canvas.drawRoundRect(-34f, -8f, 34f, 30f, 16f, 16f, fillPaint)

        // backpack behind, with straps
        fillPaint.color = 0xFF4E5A36.toInt()
        canvas.drawRoundRect(-24f, 8f, 24f, 44f, 12f, 12f, fillPaint)
        fillPaint.color = 0xFF3E4A2C.toInt()
        canvas.drawRect(-16f, 8f, -10f, 44f, fillPaint)
        canvas.drawRect(10f, 8f, 16f, 44f, fillPaint)

        // side pouches
        fillPaint.color = 0xFF4A5433.toInt()
        canvas.drawRoundRect(-40f, 2f, -27f, 20f, 5f, 5f, fillPaint)
        canvas.drawRoundRect(27f, 2f, 40f, 20f, 5f, 5f, fillPaint)

        // weapon (forward = -y), styled per tower type
        when (type) {
            TowerType.RIFLE -> {
                fillPaint.color = 0xFF263238.toInt()
                canvas.drawRoundRect(9f, -58f, 18f, -14f, 4f, 4f, fillPaint)
                fillPaint.color = type.color
                canvas.drawRect(9f, -58f, 18f, -50f, fillPaint)
            }
            TowerType.SNIPER -> {
                fillPaint.color = 0xFF263238.toInt()
                canvas.drawRoundRect(10f, -76f, 17f, -14f, 3f, 3f, fillPaint)
                fillPaint.color = type.color
                canvas.drawRect(10f, -76f, 17f, -68f, fillPaint)
            }
            TowerType.FROST -> {
                fillPaint.color = 0xFF263238.toInt()
                canvas.drawRoundRect(7f, -46f, 21f, -14f, 5f, 5f, fillPaint)
                fillPaint.color = type.color
                canvas.drawCircle(14f, -48f, 9f, fillPaint)
            }
            TowerType.FLAME -> {
                fillPaint.color = 0xFF263238.toInt()
                canvas.drawRoundRect(6f, -42f, 22f, -14f, 5f, 5f, fillPaint)
                fillPaint.color = type.color
                canvas.drawCircle(-10f, 26f, 9f, fillPaint) // fuel tanks on the pack
                canvas.drawCircle(10f, 26f, 9f, fillPaint)
            }
            TowerType.MORTAR -> {
                fillPaint.color = 0xFF263238.toInt()
                canvas.drawCircle(0f, -30f, 15f, fillPaint)
                fillPaint.color = type.color
                canvas.drawCircle(0f, -30f, 8f, fillPaint)
            }
            TowerType.TESLA -> {
                fillPaint.color = 0xFF263238.toInt()
                canvas.drawRect(-3f, -38f, 3f, -14f, fillPaint)
                fillPaint.color = type.color
                canvas.drawCircle(-12f, -36f, 6f, fillPaint)
                canvas.drawCircle(12f, -36f, 6f, fillPaint)
            }
        }

        // camo helmet on top
        fillPaint.color = 0xFF77864C.toInt()
        canvas.drawCircle(0f, 0f, 26f, fillPaint)
        for (spot in camoSpots) {
            fillPaint.color = if (spot[0] < 0f) 0xFF5A6B3A.toInt() else 0xFF6E5F3F.toInt()
            canvas.drawCircle(spot[0] * 18f, spot[1] * 18f, spot[2] * 26f, fillPaint)
        }
        // helmet seams and rim
        strokePaint.color = 0x553E4A2C
        strokePaint.strokeWidth = 3f
        canvas.drawLine(-9f, -22f, -9f, 22f, strokePaint)
        canvas.drawLine(9f, -22f, 9f, 22f, strokePaint)
        strokePaint.color = 0xFF3E4A2C.toInt()
        strokePaint.strokeWidth = 4f
        canvas.drawCircle(0f, 0f, 26f, strokePaint)

        canvas.restore()
    }

    private fun drawZombies(canvas: Canvas) {
        for (z in zombies) {
            val bodyColor = when (z.type) {
                ZombieType.WALKER -> 0xFF7CB342.toInt()
                ZombieType.RUNNER -> 0xFFAED581.toInt()
                ZombieType.BRUTE -> 0xFF558B2F.toInt()
                ZombieType.BOSS -> 0xFF6A1B9A.toInt()
            }
            val r = z.type.radius

            // reaching arms: jacket sleeves with pale hands
            val armBase = kotlin.math.atan2(z.dirY, z.dirX)
            for (side in intArrayOf(-1, 1)) {
                val a = armBase + side * (0.55f + sin(z.wobble + side) * 0.25f)
                fillPaint.color = bodyColor
                canvas.drawCircle(z.pos.x + cos(a) * r * 1.1f, z.pos.y + sin(a) * r * 1.1f, r * 0.34f, fillPaint)
                fillPaint.color = 0xFFCBBFA0.toInt()
                canvas.drawCircle(z.pos.x + cos(a) * r * 1.42f, z.pos.y + sin(a) * r * 1.42f, r * 0.20f, fillPaint)
            }

            // torso / hooded jacket
            fillPaint.color = bodyColor
            canvas.drawCircle(z.pos.x, z.pos.y, r, fillPaint)
            strokePaint.color = 0x66000000
            strokePaint.strokeWidth = 4f
            canvas.drawCircle(z.pos.x, z.pos.y, r, strokePaint)

            // head leaning forward: hair crescent behind pale scalp, exposed brain, eyes
            val px = -z.dirY
            val py = z.dirX
            val hx = z.pos.x + z.dirX * r * 0.30f
            val hy = z.pos.y + z.dirY * r * 0.30f
            fillPaint.color = 0xFF8D6E63.toInt()
            canvas.drawCircle(hx - z.dirX * r * 0.10f, hy - z.dirY * r * 0.10f, r * 0.60f, fillPaint)
            fillPaint.color = 0xFFD3C6A8.toInt()
            canvas.drawCircle(hx + z.dirX * r * 0.06f, hy + z.dirY * r * 0.06f, r * 0.56f, fillPaint)
            fillPaint.color = 0xFFE57373.toInt()
            canvas.drawCircle(hx + px * r * 0.26f - z.dirX * r * 0.08f, hy + py * r * 0.26f - z.dirY * r * 0.08f, r * 0.22f, fillPaint)
            fillPaint.color = 0xFFC62828.toInt()
            canvas.drawCircle(hx + px * r * 0.26f - z.dirX * r * 0.08f, hy + py * r * 0.26f - z.dirY * r * 0.08f, r * 0.11f, fillPaint)

            fillPaint.color = 0xFF8B1E1E.toInt()
            val ex = hx + z.dirX * r * 0.38f
            val ey = hy + z.dirY * r * 0.38f
            canvas.drawCircle(ex + px * r * 0.22f, ey + py * r * 0.22f, r * 0.09f, fillPaint)
            canvas.drawCircle(ex - px * r * 0.22f, ey - py * r * 0.22f, r * 0.09f, fillPaint)

            if (z.isSlowed) {
                strokePaint.color = 0xCC81D4FA.toInt()
                strokePaint.strokeWidth = 5f
                canvas.drawCircle(z.pos.x, z.pos.y, r + 7f, strokePaint)
            }
            if (z.isBurning) {
                strokePaint.color = 0xCCFF7043.toInt()
                strokePaint.strokeWidth = 5f
                canvas.drawCircle(z.pos.x, z.pos.y, r + 13f, strokePaint)
            }

            if (z.hp < z.maxHp) {
                val w = r * 2f
                val top = z.pos.y - r - 22f
                fillPaint.color = 0xC0212121.toInt()
                canvas.drawRect(z.pos.x - w / 2, top, z.pos.x + w / 2, top + 10f, fillPaint)
                val frac = (z.hp / z.maxHp).coerceIn(0f, 1f)
                fillPaint.color = if (frac > 0.4f) 0xFF66BB6A.toInt() else 0xFFEF5350.toInt()
                canvas.drawRect(z.pos.x - w / 2, top, z.pos.x - w / 2 + w * frac, top + 10f, fillPaint)
            }
        }
    }

    private fun drawProjectiles(canvas: Canvas) {
        for (p in projectiles) {
            fillPaint.color = p.kind.color
            val radius = when (p.kind) {
                TowerType.SNIPER -> 10f
                TowerType.MORTAR -> 13f
                TowerType.FLAME -> 7f
                else -> 8f
            }
            canvas.drawCircle(p.x, p.y, radius, fillPaint)
        }
    }

    private fun drawBolts(canvas: Canvas) {
        for (e in effects) {
            if (e.kind != Effect.Kind.BOLT) continue
            strokePaint.color = e.color
            strokePaint.alpha = e.alpha
            strokePaint.strokeWidth = e.size
            val mx = (e.x + e.x2) / 2f + (Random.nextFloat() - 0.5f) * 30f
            val my = (e.y + e.y2) / 2f + (Random.nextFloat() - 0.5f) * 30f
            canvas.drawLine(e.x, e.y, mx, my, strokePaint)
            canvas.drawLine(mx, my, e.x2, e.y2, strokePaint)
        }
        strokePaint.alpha = 255
    }

    private fun drawTexts(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        for (e in effects) {
            if (e.kind != Effect.Kind.TEXT) continue
            textPaint.textSize = e.size
            textPaint.color = e.color
            textPaint.alpha = e.alpha
            canvas.drawText(e.text, e.x, e.y, textPaint)
        }
        textPaint.alpha = 255
    }

    // ---------------------------------------------------------------- hud & panels

    private fun drawHud(canvas: Canvas) {
        fillPaint.color = 0xFF141814.toInt()
        canvas.drawRect(0f, 0f, VIRTUAL_W, GameMap.TOP, fillPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 52f
        textPaint.color = 0xFFEF9A9A.toInt()
        canvas.drawText("❤ $lives", 30f, 78f, textPaint)
        textPaint.color = 0xFFFFD54F.toInt()
        canvas.drawText("$ $money", 280f, 78f, textPaint)
        textPaint.color = 0xFFB0BEC5.toInt()
        val waveLabel = if (endlessMode) "Wave ${waves.wave}" else "Wave ${waves.wave}/$FINAL_WAVE"
        canvas.drawText(waveLabel, 560f, 78f, textPaint)

        drawButton(canvas, speedRect, if (speed == 2) "2x" else "1x", 0xFF455A64.toInt(), 44f)
        drawButton(canvas, pauseRect, if (paused) "▶" else "‖", 0xFF455A64.toInt(), 44f)
    }

    private fun drawPanel(canvas: Canvas) {
        fillPaint.color = 0xFF1C262B.toInt()
        canvas.drawRect(0f, panelTop, VIRTUAL_W, VIRTUAL_H, fillPaint)

        if (waveActive) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 44f
            textPaint.color = 0xFFEF9A9A.toInt()
            val remaining = zombies.size + waves.queued
            canvas.drawText("Wave ${waves.wave} — $remaining zombies left", 40f, panelTop + 135f, textPaint)
        } else {
            val label = if (waves.wave == 0) "START WAVE 1" else "START WAVE ${waves.wave + 1}"
            drawButton(canvas, startWaveRect, label, 0xFF2E7D32.toInt(), 46f)
        }
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 28f
        textPaint.color = 0xFF78909C.toInt()
        canvas.drawText("Tap a grass tile to build a tower", 660f, panelTop + 110f, textPaint)
        canvas.drawText("Tap a tower to upgrade or sell", 660f, panelTop + 150f, textPaint)

        val cell = selectedCell
        if (cell != null) drawBuildPanel(canvas)
        val tower = selectedTower
        if (tower != null) drawTowerPanel(canvas, tower)
    }

    private fun drawBuildPanel(canvas: Canvas) {
        fillPaint.color = 0xF2202D34.toInt()
        canvas.drawRoundRect(buildPanelRect, 24f, 24f, fillPaint)
        strokePaint.color = 0xFF455A64.toInt()
        strokePaint.strokeWidth = 4f
        canvas.drawRoundRect(buildPanelRect, 24f, 24f, strokePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 40f
        textPaint.color = Color.WHITE
        canvas.drawText("Build Tower", 40f, 1300f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 28f
        textPaint.color = 0xFF78909C.toInt()
        canvas.drawText("tap outside to cancel", 1040f, 1295f, textPaint)

        for (i in TowerType.entries.indices) {
            val type = TowerType.entries[i]
            val rect = buildCardRect(i)
            val affordable = money >= type.cost

            fillPaint.color = if (affordable) 0xFF2C3B42.toInt() else 0xFF232B30.toInt()
            canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
            strokePaint.color = if (affordable) type.color else 0xFF4A555B.toInt()
            strokePaint.strokeWidth = 4f
            canvas.drawRoundRect(rect, 18f, 18f, strokePaint)

            fillPaint.color = 0xFF37474F.toInt()
            canvas.drawCircle(rect.centerX(), rect.top + 58f, 36f, fillPaint)
            strokePaint.color = if (affordable) type.color else 0xFF607D8B.toInt()
            strokePaint.strokeWidth = 3f
            canvas.drawCircle(rect.centerX(), rect.top + 58f, 36f, strokePaint)
            drawSoldier(canvas, rect.centerX(), rect.top + 58f, (-Math.PI / 2).toFloat(), type, 0.55f)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 34f
            textPaint.color = if (affordable) Color.WHITE else 0xFF90A4AE.toInt()
            canvas.drawText(type.label, rect.centerX(), rect.top + 132f, textPaint)
            textPaint.textSize = 26f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText(type.blurb, rect.centerX(), rect.top + 168f, textPaint)
            textPaint.textSize = 30f
            textPaint.color = if (affordable) 0xFFFFD54F.toInt() else 0xFFEF5350.toInt()
            canvas.drawText("$${type.cost}", rect.centerX(), rect.top + 210f, textPaint)
        }
    }

    private fun drawTowerPanel(canvas: Canvas, tower: Tower) {
        fillPaint.color = 0xF2202D34.toInt()
        canvas.drawRoundRect(towerPanelRect, 24f, 24f, fillPaint)
        strokePaint.color = 0xFF455A64.toInt()
        strokePaint.strokeWidth = 4f
        canvas.drawRoundRect(towerPanelRect, 24f, 24f, strokePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 42f
        textPaint.color = Color.WHITE
        canvas.drawText(tower.type.label, 40f, 1218f, textPaint)
        textPaint.textSize = 26f
        textPaint.color = 0xFF90A4AE.toInt()
        val stats = buildString {
            append("DMG ${tower.damage.roundToInt()}   RNG ${tower.range.roundToInt()}   ")
            append("ROF %.1f/s".format(tower.fireRate))
            if (tower.critChance > 0f) append("   CRIT ${(tower.critChance * 100).roundToInt()}%")
            if (tower.pierce > 0) append("   PIERCE ${tower.pierce}")
            if (tower.type.baseSlowFactor < 1f) append("   SLOW ${((1f - tower.slowFactor) * 100).roundToInt()}%")
            if (tower.burnDps > 0f) append("   BURN ${tower.burnDps.roundToInt()}/s")
            if (tower.splash > 0f) append("   BLAST ${tower.splash.roundToInt()}")
            if (tower.type.baseChain > 0) append("   CHAIN ${tower.chain}")
        }
        canvas.drawText(stats, 40f, 1262f, textPaint)

        drawButton(canvas, sellRect, "SELL  $${tower.sellValue}", 0xFF8D6E63.toInt(), 34f)

        for (i in tower.type.upgrades.indices) {
            val u = tower.type.upgrades[i]
            val y = upgradeRowY(i)
            val rank = tower.ranks[i]

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 34f
            textPaint.color = Color.WHITE
            canvas.drawText(u.name, 40f, y + 52f, textPaint)
            textPaint.textSize = 26f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText(u.desc, 40f, y + 92f, textPaint)

            for (k in 0 until u.maxRank) {
                if (k < rank) {
                    fillPaint.color = 0xFFFFD54F.toInt()
                    canvas.drawCircle(52f + k * 36f, y + 126f, 11f, fillPaint)
                } else {
                    strokePaint.color = 0xFF607D8B.toInt()
                    strokePaint.strokeWidth = 3f
                    canvas.drawCircle(52f + k * 36f, y + 126f, 11f, strokePaint)
                }
            }

            val btn = upgradeButtonRect(i)
            if (tower.canRankUp(i)) {
                val cost = tower.upgradeCost(i)
                val affordable = money >= cost
                drawButton(canvas, btn, "$$cost", if (affordable) 0xFF2E7D32.toInt() else 0xFF37474F.toInt(), 36f)
            } else {
                drawButton(canvas, btn, "MAX", 0xFF37474F.toInt(), 36f)
            }
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 26f
        textPaint.color = 0xFF78909C.toInt()
        canvas.drawText("tap outside to close", VIRTUAL_W / 2f, 1890f, textPaint)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, color: Int, textSize: Float) {
        fillPaint.color = color
        canvas.drawRoundRect(rect, 16f, 16f, fillPaint)
        strokePaint.color = 0x33FFFFFF
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(rect, 16f, 16f, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = textSize
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + textSize * 0.35f, textPaint)
    }

    // ---------------------------------------------------------------- overlays

    private fun drawOverlayBackground(canvas: Canvas) {
        fillPaint.color = 0xCC000000.toInt()
        canvas.drawRect(0f, 0f, VIRTUAL_W, VIRTUAL_H, fillPaint)
    }

    private fun drawGameOverOverlay(canvas: Canvas) {
        drawOverlayBackground(canvas)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 84f
        textPaint.color = 0xFFE53935.toInt()
        canvas.drawText("THE HORDE", VIRTUAL_W / 2f, 700f, textPaint)
        canvas.drawText("GOT THROUGH", VIRTUAL_W / 2f, 800f, textPaint)
        textPaint.textSize = 40f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("You survived ${waves.wave} wave${if (waves.wave == 1) "" else "s"} on ${map.name}", VIRTUAL_W / 2f, 920f, textPaint)
        textPaint.textSize = 50f
        textPaint.color = Color.WHITE
        canvas.drawText("TAP FOR MAIN MENU", VIRTUAL_W / 2f, 1150f, textPaint)
    }

    private fun drawVictoryOverlay(canvas: Canvas) {
        drawOverlayBackground(canvas)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 96f
        textPaint.color = 0xFFFFD54F.toInt()
        canvas.drawText("CITY SAVED!", VIRTUAL_W / 2f, 700f, textPaint)
        textPaint.textSize = 38f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("You held ${map.name} through all $FINAL_WAVE waves.", VIRTUAL_W / 2f, 830f, textPaint)

        drawButton(canvas, continueRect, "KEEP PLAYING (ENDLESS)", 0xFF2E7D32.toInt(), 44f)
        drawButton(canvas, menuButtonRect, "MAIN MENU", 0xFF455A64.toInt(), 44f)
    }

    private fun drawPausedOverlay(canvas: Canvas) {
        drawOverlayBackground(canvas)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 80f
        textPaint.color = Color.WHITE
        canvas.drawText("PAUSED", VIRTUAL_W / 2f, 900f, textPaint)
        textPaint.textSize = 36f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("Tap anywhere to resume", VIRTUAL_W / 2f, 990f, textPaint)
    }
}
