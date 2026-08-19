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
        const val MIN_VIRTUAL_H = 1872f // HUD 120 + grid 14*108 + panel 240
        const val FINAL_WAVE = 20
        const val START_MONEY = 350
        const val START_LIVES = 20
        const val SPLASH_FALLOFF = 0.6f
        const val EARLY_CALL_BONUS = 40
        const val NEST_MAX_HP = 4000f
    }

    enum class State { HOME, MAP_SELECT, PLAYING, GAME_OVER, VICTORY }
    enum class Mode(val label: String, val blurb: String) {
        CAMPAIGN("Campaign", "Survive all $FINAL_WAVE waves"),
        SURVIVAL("Survival", "Endless waves — how long can you last?"),
        CASTLE("Castle vs Nest", "Send troops to destroy the zombie nest")
    }

    var state = State.HOME
        private set
    private var mode = Mode.CAMPAIGN
    private var paused = false
    private var inSettings = false

    // session settings (pause menu > settings)
    private var bloodFx = true
    private var screenShake = true

    private var map = Maps.all[0]
    private var money = START_MONEY
    private var lives = START_LIVES
    private var speed = 1
    private var endlessMode = false
    private var waveActive = false
    private var shake = 0f
    private var nestHp = NEST_MAX_HP

    private val zombies = mutableListOf<Zombie>()
    private val towers = mutableListOf<Tower>()
    private val troops = mutableListOf<Troop>()
    private val projectiles = mutableListOf<Projectile>()
    private val effects = mutableListOf<Effect>()
    private val waves = WaveManager()

    private var selectedCell: Pair<Int, Int>? = null
    private var selectedTower: Tower? = null

    // ---- layout (recomputed when the surface size is known) ----
    var virtualH = MIN_VIRTUAL_H
        private set
    private val gridBottom = GameMap.TOP + GameMap.ROWS * GameMap.TILE

    private val pauseRect = RectF(VIRTUAL_W - 110f, 15f, VIRTUAL_W - 20f, 105f)
    private val speedRect = RectF(VIRTUAL_W - 220f, 15f, VIRTUAL_W - 130f, 105f)
    private val backRect = RectF(40f, 40f, 260f, 130f)

    private var panelTop = 0f
    private var startWaveRect = RectF()
    private var troopRect = RectF()
    private var buildPanelRect = RectF()
    private var towerPanelRect = RectF()
    private var sellRect = RectF()
    private var continueRect = RectF()
    private var menuButtonRect = RectF()
    private var resumeRect = RectF()
    private var settingsRect = RectF()
    private var pauseMenuRect = RectF()
    private var bloodRowRect = RectF()
    private var shakeRowRect = RectF()
    private var settingsBackRect = RectF()
    private var homeModeRects = listOf(RectF(), RectF(), RectF())

    init {
        recomputeLayout()
    }

    fun setVirtualHeight(vh: Float) {
        virtualH = vh.coerceAtLeast(MIN_VIRTUAL_H)
        recomputeLayout()
    }

    private fun recomputeLayout() {
        val vh = virtualH
        panelTop = vh - 240f
        startWaveRect = RectF(40f, panelTop + 35f, 540f, panelTop + 185f)
        troopRect = RectF(580f, panelTop + 35f, 850f, panelTop + 185f)
        buildPanelRect = RectF(10f, vh - 690f, 1070f, vh - 10f)
        towerPanelRect = RectF(10f, vh - 780f, 1070f, vh - 10f)
        sellRect = RectF(760f, towerPanelRect.top + 25f, 1040f, towerPanelRect.top + 105f)
        continueRect = RectF(140f, vh / 2f - 40f, 940f, vh / 2f + 100f)
        menuButtonRect = RectF(140f, vh / 2f + 160f, 940f, vh / 2f + 300f)
        resumeRect = RectF(240f, vh / 2f - 240f, 840f, vh / 2f - 100f)
        settingsRect = RectF(240f, vh / 2f - 40f, 840f, vh / 2f + 100f)
        pauseMenuRect = RectF(240f, vh / 2f + 160f, 840f, vh / 2f + 300f)
        bloodRowRect = RectF(140f, vh / 2f - 240f, 940f, vh / 2f - 110f)
        shakeRowRect = RectF(140f, vh / 2f - 70f, 940f, vh / 2f + 60f)
        settingsBackRect = RectF(240f, vh / 2f + 140f, 840f, vh / 2f + 280f)
        homeModeRects = listOf(
            RectF(140f, 660f, 940f, 830f),
            RectF(140f, 920f, 940f, 1090f),
            RectF(140f, 1180f, 940f, 1350f)
        )
    }

    private fun buildCardRect(i: Int): RectF {
        val col = i % 2
        val row = i / 2
        val top = buildPanelRect.top + 100f
        return RectF(30f + col * 520f, top + row * 290f, 30f + col * 520f + 500f, top + row * 290f + 260f)
    }

    private fun upgradeRowY(i: Int) = towerPanelRect.top + 152f + i * 118f
    private fun upgradeButtonRect(i: Int): RectF {
        val y = upgradeRowY(i)
        return RectF(770f, y + 12f, 1040f, y + 100f)
    }

    private fun mapCardRect(i: Int): RectF {
        val col = i % 2
        val row = i / 2
        return RectF(25f + col * 525f, 360f + row * 460f, 25f + col * 525f + 505f, 360f + row * 460f + 430f)
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

    private fun canManualWave() =
        mode != Mode.CASTLE && (mode == Mode.SURVIVAL || endlessMode || waves.wave < FINAL_WAVE)

    private fun step(dt: Float) {
        if (shake > 0f) shake -= dt

        // the nest never stops spawning
        if (mode == Mode.CASTLE && !waves.spawning && zombies.size < 8) {
            waves.startNextWave()
        }

        waves.update(dt) { type -> zombies.add(Zombie(type, map.paths.random())) }

        // melee engagements: troops block the first zombie they reach
        for (z in zombies) z.blocked = false
        for (t in troops) {
            t.engaged = false
            if (!t.alive) continue
            var target: Zombie? = null
            var bestD = Troop.MELEE_RANGE
            for (z in zombies) {
                if (!z.alive) continue
                val d = hypot(z.pos.x - t.pos.x, z.pos.y - t.pos.y)
                if (d < bestD) {
                    bestD = d
                    target = z
                }
            }
            if (target != null) {
                t.engaged = true
                t.face(target.pos.x, target.pos.y)
                target.blocked = true
                target.hp -= Troop.DPS * dt
                t.hp -= target.type.meleeDps * dt
            }
        }

        for (z in zombies) z.update(dt)
        for (t in troops) t.update(dt)

        val trIt = troops.iterator()
        while (trIt.hasNext()) {
            val t = trIt.next()
            if (t.hp <= 0f) {
                effects.add(Effect.splat(t.pos.x, t.pos.y, 30f))
                trIt.remove()
            } else if (t.atDestination && mode == Mode.CASTLE) {
                // satchel charge against the nest; elsewhere troops hold the entrance
                nestHp -= Troop.NEST_DAMAGE
                shake = 0.3f
                val nest = nestCenter()
                effects.add(Effect.splat(nest.x, nest.y, 80f))
                effects.add(Effect.text(nest.x, nest.y - 70f, "-${Troop.NEST_DAMAGE.roundToInt()}", 0xFFFF7043.toInt(), 52f))
                trIt.remove()
                if (nestHp <= 0f) {
                    nestHp = 0f
                    state = State.VICTORY
                    selectedCell = null
                    selectedTower = null
                }
            }
        }

        for (t in towers) t.update(dt, zombies, projectiles)

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

        if (mode != Mode.CASTLE && waveActive && !waves.spawning && zombies.isEmpty() && state == State.PLAYING) {
            waveActive = false
            val bonus = 60 + waves.wave * 8
            money += bonus
            effects.add(Effect.text(VIRTUAL_W / 2f, 760f, "Wave ${waves.wave} cleared!  +$$bonus", 0xFF81C784.toInt(), 56f))
            if (mode == Mode.CAMPAIGN && waves.wave >= FINAL_WAVE && !endlessMode) {
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

    private fun nestCenter() = map.entryCells.first().let { (c, r) -> GameMap.cellCenter(c, r) }

    // ================================================================== input

    fun onTap(x: Float, y: Float) {
        when (state) {
            State.HOME -> {
                for (i in Mode.entries.indices) {
                    if (homeModeRects[i].contains(x, y)) {
                        mode = Mode.entries[i]
                        state = State.MAP_SELECT
                        return
                    }
                }
            }
            State.MAP_SELECT -> {
                if (backRect.contains(x, y)) {
                    state = State.HOME
                    return
                }
                for (i in Maps.all.indices) {
                    if (mapCardRect(i).contains(x, y)) {
                        startNewGame(Maps.all[i])
                        return
                    }
                }
            }
            State.GAME_OVER -> state = State.HOME
            State.VICTORY -> when {
                mode == Mode.CAMPAIGN && continueRect.contains(x, y) -> {
                    endlessMode = true
                    state = State.PLAYING
                }
                menuButtonRect.contains(x, y) -> state = State.HOME
            }
            State.PLAYING -> handlePlayingTap(x, y)
        }
    }

    private fun handlePlayingTap(x: Float, y: Float) {
        if (paused) {
            if (inSettings) {
                when {
                    bloodRowRect.contains(x, y) -> bloodFx = !bloodFx
                    shakeRowRect.contains(x, y) -> shakeFxToggle()
                    settingsBackRect.contains(x, y) -> inSettings = false
                }
            } else {
                when {
                    resumeRect.contains(x, y) -> paused = false
                    settingsRect.contains(x, y) -> inSettings = true
                    pauseMenuRect.contains(x, y) -> {
                        paused = false
                        state = State.HOME
                    }
                }
            }
            return
        }
        if (pauseRect.contains(x, y)) {
            paused = true
            inSettings = false
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
            if (troopRect.contains(x, y)) {
                if (money >= Troop.COST && troops.count { it.alive } < Troop.MAX_ACTIVE) {
                    money -= Troop.COST
                    troops.add(Troop(map.paths.random()))
                }
                return
            }
            if (startWaveRect.contains(x, y) && canManualWave() && !waves.spawning) {
                if (waveActive && zombies.isNotEmpty()) {
                    money += EARLY_CALL_BONUS
                    effects.add(Effect.text(startWaveRect.centerX(), panelTop - 20f, "Early call! +$$EARLY_CALL_BONUS", 0xFF81C784.toInt()))
                }
                waves.startNextWave()
                waveActive = true
            }
            return
        }
        if (y < GameMap.TOP || y > gridBottom) return

        val c = (x / GameMap.TILE).toInt()
        val r = ((y - GameMap.TOP) / GameMap.TILE).toInt()
        val hit = towers.find { it.col == c && it.row == r }
        when {
            hit != null -> selectedTower = hit
            map.isBuildable(c, r) -> selectedCell = c to r
        }
    }

    private fun shakeFxToggle() {
        screenShake = !screenShake
        if (!screenShake) shake = 0f
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
                    effects.add(Effect.text(VIRTUAL_W / 2f, buildPanelRect.top + 60f, "Need $${type.cost}", 0xFFEF5350.toInt()))
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
        val next = tower.nextTier
        if (next != null && upgradeButtonRect(tower.tier).contains(x, y)) {
            if (money >= next.cost) {
                money -= next.cost
                tower.buyTier()
                effects.add(Effect.text(tower.pos.x, tower.pos.y - 50f, next.name, 0xFF81C784.toInt()))
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
        inSettings = false
        shake = 0f
        nestHp = NEST_MAX_HP
        zombies.clear()
        towers.clear()
        troops.clear()
        projectiles.clear()
        effects.clear()
        waves.reset()
        selectedCell = null
        selectedTower = null
        state = State.PLAYING
    }

    // ================================================================== draw

    fun draw(canvas: Canvas) {
        when (state) {
            State.HOME -> {
                drawHome(canvas)
                return
            }
            State.MAP_SELECT -> {
                drawMapSelect(canvas)
                return
            }
            else -> {}
        }

        canvas.save()
        if (shake > 0f && screenShake) {
            canvas.translate((Random.nextFloat() - 0.5f) * 16f, (Random.nextFloat() - 0.5f) * 16f)
        }
        drawMap(canvas)
        drawSplats(canvas)
        drawTowers(canvas)
        drawTroops(canvas)
        drawZombies(canvas)
        drawProjectiles(canvas)
        drawTexts(canvas)
        canvas.restore()

        drawHud(canvas)
        drawPanel(canvas)

        when (state) {
            State.GAME_OVER -> drawGameOverOverlay(canvas)
            State.VICTORY -> drawVictoryOverlay(canvas)
            State.PLAYING -> if (paused) {
                if (inSettings) drawSettingsOverlay(canvas) else drawPauseMenu(canvas)
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------- menus

    private fun drawHome(canvas: Canvas) {
        fillPaint.color = 0xFF10150F.toInt()
        canvas.drawRect(0f, 0f, VIRTUAL_W, virtualH, fillPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 104f
        textPaint.color = 0xFF7CB342.toInt()
        canvas.drawText("ZOMBIE DEFENSE", VIRTUAL_W / 2f, 330f, textPaint)
        textPaint.textSize = 40f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("SELECT MODE", VIRTUAL_W / 2f, 500f, textPaint)

        for (i in Mode.entries.indices) {
            val m = Mode.entries[i]
            val rect = homeModeRects[i]
            val enabled = true
            drawButton(canvas, rect, m.label.uppercase(), if (enabled) 0xFF2E7D32.toInt() else 0xFF37474F.toInt(), 48f)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 28f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText(m.blurb, VIRTUAL_W / 2f, rect.bottom + 45f, textPaint)
        }

        textPaint.textSize = 28f
        textPaint.color = 0xFF78909C.toInt()
        canvas.drawText("Build soldiers on grass  •  Deploy troops for melee", VIRTUAL_W / 2f, virtualH - 120f, textPaint)
        canvas.drawText("Don't let the horde reach your gate", VIRTUAL_W / 2f, virtualH - 70f, textPaint)
    }

    private fun drawMapSelect(canvas: Canvas) {
        fillPaint.color = 0xFF10150F.toInt()
        canvas.drawRect(0f, 0f, VIRTUAL_W, virtualH, fillPaint)

        drawButton(canvas, backRect, "< BACK", 0xFF455A64.toInt(), 34f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 48f
        textPaint.color = 0xFF7CB342.toInt()
        canvas.drawText(mode.label.uppercase(), VIRTUAL_W / 2f, 200f, textPaint)
        textPaint.textSize = 38f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("CHOOSE YOUR MAP", VIRTUAL_W / 2f, 300f, textPaint)

        for (i in Maps.all.indices) {
            drawMapCard(canvas, Maps.all[i], mapCardRect(i))
        }
    }

    private fun drawMapCard(canvas: Canvas, m: GameMap, rect: RectF) {
        fillPaint.color = 0xFF1C262B.toInt()
        canvas.drawRoundRect(rect, 20f, 20f, fillPaint)
        strokePaint.color = 0xFF37474F.toInt()
        strokePaint.strokeWidth = 4f
        canvas.drawRoundRect(rect, 20f, 20f, strokePaint)

        val cellSize = 24f
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
        // scenery behind everything (covers the strip between grid and panel on tall screens)
        fillPaint.color = 0xFF2C422D.toInt()
        canvas.drawRect(0f, 0f, VIRTUAL_W, virtualH, fillPaint)

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
        textPaint.textSize = 60f
        textPaint.color = 0xFFD7CCC8.toInt()
        if (mode == Mode.CASTLE) {
            // nest at the spawn, castle at the gate
            val nest = nestCenter()
            fillPaint.color = 0xFF4A2E4A.toInt()
            canvas.drawCircle(nest.x, nest.y, 52f, fillPaint)
            fillPaint.color = 0xFF6A1B9A.toInt()
            canvas.drawCircle(nest.x, nest.y, 38f, fillPaint)
            fillPaint.color = 0xFF9CCC65.toInt()
            canvas.drawCircle(nest.x - 15f, nest.y - 10f, 7f, fillPaint)
            canvas.drawCircle(nest.x + 12f, nest.y + 14f, 6f, fillPaint)
            canvas.drawCircle(nest.x + 8f, nest.y - 18f, 5f, fillPaint)
            // nest health bar
            val frac = (nestHp / NEST_MAX_HP).coerceIn(0f, 1f)
            fillPaint.color = 0xC0212121.toInt()
            canvas.drawRect(nest.x - 70f, nest.y - 78f, nest.x + 70f, nest.y - 62f, fillPaint)
            fillPaint.color = 0xFFAB47BC.toInt()
            canvas.drawRect(nest.x - 70f, nest.y - 78f, nest.x - 70f + 140f * frac, nest.y - 62f, fillPaint)
            for ((c, r) in map.exitCells) {
                val p = GameMap.cellCenter(c, r)
                canvas.drawText("🏰", p.x, p.y + 20f, textPaint)
            }
        } else {
            for ((c, r) in map.entryCells) {
                val p = GameMap.cellCenter(c, r)
                canvas.drawText("➡", p.x, p.y + 20f, textPaint)
            }
            for ((c, r) in map.exitCells) {
                val p = GameMap.cellCenter(c, r)
                canvas.drawText("☠", p.x, p.y + 20f, textPaint)
            }
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
        if (!bloodFx) return
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
            fillPaint.color = 0xFF37474F.toInt()
            canvas.drawCircle(t.pos.x, t.pos.y, 44f, fillPaint)
            strokePaint.color = t.type.color
            strokePaint.strokeWidth = 4f
            canvas.drawCircle(t.pos.x, t.pos.y, 44f, strokePaint)

            drawSoldier(canvas, t.pos.x, t.pos.y, t.angle, t.type, 0.84f)

            fillPaint.color = 0xFFFFD54F.toInt()
            for (i in 0 until min(t.tier, t.type.path.size)) {
                canvas.drawCircle(t.pos.x - 26f + i * 13f, t.pos.y + 42f, 4.5f, fillPaint)
            }
        }
    }

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

        fillPaint.color = 0xFF5F6E42.toInt()
        canvas.drawRoundRect(-34f, -8f, 34f, 30f, 16f, 16f, fillPaint)

        fillPaint.color = 0xFF4E5A36.toInt()
        canvas.drawRoundRect(-24f, 8f, 24f, 44f, 12f, 12f, fillPaint)
        fillPaint.color = 0xFF3E4A2C.toInt()
        canvas.drawRect(-16f, 8f, -10f, 44f, fillPaint)
        canvas.drawRect(10f, 8f, 16f, 44f, fillPaint)

        fillPaint.color = 0xFF4A5433.toInt()
        canvas.drawRoundRect(-40f, 2f, -27f, 20f, 5f, 5f, fillPaint)
        canvas.drawRoundRect(27f, 2f, 40f, 20f, 5f, 5f, fillPaint)

        when (type) {
            TowerType.ASSAULT -> {
                fillPaint.color = 0xFF263238.toInt()
                canvas.drawRoundRect(9f, -58f, 18f, -14f, 4f, 4f, fillPaint)
                canvas.drawRoundRect(4f, -34f, 10f, -22f, 3f, 3f, fillPaint)
                fillPaint.color = type.color
                canvas.drawRect(9f, -58f, 18f, -50f, fillPaint)
            }
            TowerType.SUPPORT -> {
                fillPaint.color = 0xFF263238.toInt()
                canvas.drawRoundRect(8f, -56f, 22f, -12f, 4f, 4f, fillPaint)
                fillPaint.color = 0xFF4A5433.toInt()
                canvas.drawRoundRect(22f, -32f, 36f, -14f, 4f, 4f, fillPaint)
                fillPaint.color = type.color
                canvas.drawRect(8f, -56f, 22f, -48f, fillPaint)
            }
            TowerType.ENGINEER -> {
                fillPaint.color = 0xFF37474F.toInt()
                canvas.drawRoundRect(5f, -52f, 25f, -12f, 6f, 6f, fillPaint)
                fillPaint.color = type.color
                canvas.drawRoundRect(5f, -52f, 25f, -42f, 6f, 6f, fillPaint)
                fillPaint.color = 0xFF546E7A.toInt()
                canvas.drawCircle(-36f, -26f, 7f, fillPaint)
                fillPaint.color = 0xFF90A4AE.toInt()
                for (dx in intArrayOf(-1, 1)) for (dy in intArrayOf(-1, 1)) {
                    canvas.drawCircle(-36f + dx * 9f, -26f + dy * 9f, 3f, fillPaint)
                }
            }
            TowerType.RECON -> {
                fillPaint.color = 0xFF263238.toInt()
                canvas.drawRoundRect(11f, -70f, 16f, -14f, 3f, 3f, fillPaint)
                canvas.drawRoundRect(9f, -84f, 18f, -70f, 4f, 4f, fillPaint)
                fillPaint.color = 0xFF37474F.toInt()
                canvas.drawCircle(13.5f, -36f, 6f, fillPaint)
                fillPaint.color = type.color
                canvas.drawCircle(13.5f, -36f, 3f, fillPaint)
            }
        }

        when (type) {
            TowerType.ENGINEER -> {
                fillPaint.color = 0xFF6E7B4A.toInt()
                canvas.drawCircle(0f, 0f, 24f, fillPaint)
                canvas.drawRoundRect(-14f, -36f, 14f, -20f, 6f, 6f, fillPaint)
                fillPaint.color = 0xFF5A6B3A.toInt()
                canvas.drawCircle(-6f, 4f, 8f, fillPaint)
                strokePaint.color = 0xFF3E4A2C.toInt()
                strokePaint.strokeWidth = 4f
                canvas.drawCircle(0f, 0f, 24f, strokePaint)
            }
            TowerType.RECON -> {
                fillPaint.color = 0xFF55663B.toInt()
                canvas.drawCircle(0f, 0f, 27f, fillPaint)
                fillPaint.color = 0xFF66784A.toInt()
                canvas.drawCircle(-9f, -6f, 16f, fillPaint)
                canvas.drawCircle(10f, -2f, 14f, fillPaint)
                canvas.drawCircle(0f, 10f, 15f, fillPaint)
                fillPaint.color = 0xFF4A5A32.toInt()
                canvas.drawCircle(6f, -12f, 9f, fillPaint)
                canvas.drawCircle(-10f, 10f, 8f, fillPaint)
                strokePaint.color = 0xFF3E4A2C.toInt()
                strokePaint.strokeWidth = 4f
                canvas.drawCircle(0f, 0f, 27f, strokePaint)
            }
            else -> {
                fillPaint.color = 0xFF77864C.toInt()
                canvas.drawCircle(0f, 0f, 26f, fillPaint)
                for (spot in camoSpots) {
                    fillPaint.color = if (spot[0] < 0f) 0xFF5A6B3A.toInt() else 0xFF6E5F3F.toInt()
                    canvas.drawCircle(spot[0] * 18f, spot[1] * 18f, spot[2] * 26f, fillPaint)
                }
                strokePaint.color = 0x553E4A2C
                strokePaint.strokeWidth = 3f
                canvas.drawLine(-9f, -22f, -9f, 22f, strokePaint)
                canvas.drawLine(9f, -22f, 9f, 22f, strokePaint)
                strokePaint.color = 0xFF3E4A2C.toInt()
                strokePaint.strokeWidth = 4f
                canvas.drawCircle(0f, 0f, 26f, strokePaint)
            }
        }

        canvas.restore()
    }

    private fun drawTroops(canvas: Canvas) {
        for (t in troops) {
            canvas.save()
            canvas.translate(t.pos.x, t.pos.y)
            canvas.rotate(Math.toDegrees(kotlin.math.atan2(t.dirY, t.dirX).toDouble()).toFloat() + 90f)

            // body, knife arm, helmet
            fillPaint.color = 0xFF5F6E42.toInt()
            canvas.drawRoundRect(-17f, -8f, 17f, 16f, 9f, 9f, fillPaint)
            fillPaint.color = 0xFFB0BEC5.toInt()
            canvas.drawRect(9f, -26f, 13f, -8f, fillPaint)
            fillPaint.color = 0xFF77864C.toInt()
            canvas.drawCircle(0f, -2f, 14f, fillPaint)
            strokePaint.color = 0xFF3E4A2C.toInt()
            strokePaint.strokeWidth = 3f
            canvas.drawCircle(0f, -2f, 14f, strokePaint)
            canvas.restore()

            if (t.hp < Troop.MAX_HP) {
                val top = t.pos.y - 32f
                fillPaint.color = 0xC0212121.toInt()
                canvas.drawRect(t.pos.x - 22f, top, t.pos.x + 22f, top + 7f, fillPaint)
                val frac = (t.hp / Troop.MAX_HP).coerceIn(0f, 1f)
                fillPaint.color = 0xFF4FC3F7.toInt()
                canvas.drawRect(t.pos.x - 22f, top, t.pos.x - 22f + 44f * frac, top + 7f, fillPaint)
            }
        }
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

            val armBase = kotlin.math.atan2(z.dirY, z.dirX)
            for (side in intArrayOf(-1, 1)) {
                val a = armBase + side * (0.55f + sin(z.wobble + side) * 0.25f)
                fillPaint.color = bodyColor
                canvas.drawCircle(z.pos.x + cos(a) * r * 1.1f, z.pos.y + sin(a) * r * 1.1f, r * 0.34f, fillPaint)
                fillPaint.color = 0xFFCBBFA0.toInt()
                canvas.drawCircle(z.pos.x + cos(a) * r * 1.42f, z.pos.y + sin(a) * r * 1.42f, r * 0.20f, fillPaint)
            }

            fillPaint.color = bodyColor
            canvas.drawCircle(z.pos.x, z.pos.y, r, fillPaint)
            strokePaint.color = 0x66000000
            strokePaint.strokeWidth = 4f
            canvas.drawCircle(z.pos.x, z.pos.y, r, strokePaint)

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
                TowerType.RECON -> 10f
                TowerType.ENGINEER -> 13f
                TowerType.SUPPORT -> 6f
                else -> 8f
            }
            canvas.drawCircle(p.x, p.y, radius, fillPaint)
        }
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
        textPaint.textSize = 50f
        textPaint.color = 0xFFEF9A9A.toInt()
        canvas.drawText("❤ $lives", 30f, 76f, textPaint)
        textPaint.color = 0xFFFFD54F.toInt()
        canvas.drawText("$ $money", 260f, 76f, textPaint)
        textPaint.color = 0xFFB0BEC5.toInt()
        val waveLabel = when {
            mode == Mode.CAMPAIGN && !endlessMode -> "Wave ${waves.wave}/$FINAL_WAVE"
            else -> "Wave ${waves.wave}"
        }
        canvas.drawText(waveLabel, 540f, 76f, textPaint)

        drawButton(canvas, speedRect, if (speed == 2) "2x" else "1x", 0xFF455A64.toInt(), 42f)
        drawPauseButton(canvas)
    }

    private fun drawPauseButton(canvas: Canvas) {
        fillPaint.color = 0xFF455A64.toInt()
        canvas.drawRoundRect(pauseRect, 16f, 16f, fillPaint)
        strokePaint.color = 0x33FFFFFF
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(pauseRect, 16f, 16f, strokePaint)

        fillPaint.color = Color.WHITE
        val cx = pauseRect.centerX()
        val cy = pauseRect.centerY()
        canvas.drawRoundRect(cx - 18f, cy - 21f, cx - 5f, cy + 21f, 5f, 5f, fillPaint)
        canvas.drawRoundRect(cx + 5f, cy - 21f, cx + 18f, cy + 21f, 5f, 5f, fillPaint)
    }

    private fun drawPanel(canvas: Canvas) {
        fillPaint.color = 0xFF1C262B.toInt()
        canvas.drawRect(0f, panelTop, VIRTUAL_W, virtualH, fillPaint)

        if (mode == Mode.CASTLE) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 38f
            textPaint.color = 0xFFCE93D8.toInt()
            canvas.drawText("Nest: ${nestHp.roundToInt()}/${NEST_MAX_HP.roundToInt()}", 40f, panelTop + 100f, textPaint)
            textPaint.textSize = 26f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText("Send troops to destroy it!", 40f, panelTop + 150f, textPaint)
        } else if (canManualWave() && !waves.spawning) {
            val hot = waveActive && zombies.isNotEmpty()
            val label = if (hot) "CALL WAVE ${waves.wave + 1}  +$$EARLY_CALL_BONUS"
            else "START WAVE ${waves.wave + 1}"
            drawButton(canvas, startWaveRect, label, 0xFF2E7D32.toInt(), 40f)
        } else {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 38f
            textPaint.color = 0xFFEF9A9A.toInt()
            canvas.drawText("Wave ${waves.wave}", 40f, panelTop + 90f, textPaint)
            textPaint.textSize = 28f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText("${waves.queued} incoming • ${zombies.size} on field", 40f, panelTop + 140f, textPaint)
        }

        val troopsActive = troops.count { it.alive }
        val canDeploy = money >= Troop.COST && troopsActive < Troop.MAX_ACTIVE
        drawButton(canvas, troopRect, "TROOP $${Troop.COST}", if (canDeploy) 0xFF00695C.toInt() else 0xFF37474F.toInt(), 34f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 24f
        textPaint.color = 0xFF90A4AE.toInt()
        canvas.drawText("$troopsActive/${Troop.MAX_ACTIVE} deployed", troopRect.centerX(), troopRect.bottom + 34f, textPaint)

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
        canvas.drawText("Build Soldier", 40f, buildPanelRect.top + 68f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 28f
        textPaint.color = 0xFF78909C.toInt()
        canvas.drawText("tap outside to cancel", 1040f, buildPanelRect.top + 64f, textPaint)

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
            canvas.drawCircle(rect.left + 90f, rect.centerY(), 56f, fillPaint)
            strokePaint.color = if (affordable) type.color else 0xFF607D8B.toInt()
            strokePaint.strokeWidth = 3f
            canvas.drawCircle(rect.left + 90f, rect.centerY(), 56f, strokePaint)
            drawSoldier(canvas, rect.left + 90f, rect.centerY(), (-Math.PI / 2).toFloat(), type, 0.8f)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 36f
            textPaint.color = if (affordable) Color.WHITE else 0xFF90A4AE.toInt()
            canvas.drawText(type.label, rect.left + 175f, rect.centerY() - 30f, textPaint)
            textPaint.textSize = 27f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText(type.blurb, rect.left + 175f, rect.centerY() + 12f, textPaint)
            textPaint.textSize = 32f
            textPaint.color = if (affordable) 0xFFFFD54F.toInt() else 0xFFEF5350.toInt()
            canvas.drawText("$${type.cost}", rect.left + 175f, rect.centerY() + 58f, textPaint)
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
        canvas.drawText(tower.type.label, 40f, towerPanelRect.top + 78f, textPaint)
        textPaint.textSize = 26f
        textPaint.color = 0xFF90A4AE.toInt()
        val stats = buildString {
            append("DMG ${tower.damage.roundToInt()}   RNG ${tower.range.roundToInt()}   ")
            append("ROF %.1f/s".format(tower.fireRate))
            if (tower.critChance > 0f) append("   CRIT ${(tower.critChance * 100).roundToInt()}%")
            if (tower.pierce > 0) append("   PIERCE ${tower.pierce}")
            if (tower.type.baseSlowFactor < 1f) append("   SLOW ${((1f - tower.slowFactor) * 100).roundToInt()}%")
            if (tower.splash > 0f) append("   BLAST ${tower.splash.roundToInt()}")
        }
        canvas.drawText(stats, 40f, towerPanelRect.top + 122f, textPaint)

        drawButton(canvas, sellRect, "SELL  $${tower.sellValue}", 0xFF8D6E63.toInt(), 34f)

        for (i in tower.type.path.indices) {
            val u = tower.type.path[i]
            val y = upgradeRowY(i)
            val bought = i < tower.tier
            val isNext = i == tower.tier

            if (bought) fillPaint.color = 0xFFFFD54F.toInt()
            else if (isNext) fillPaint.color = 0xFF546E7A.toInt()
            else fillPaint.color = 0xFF2C383E.toInt()
            canvas.drawCircle(58f, y + 56f, 22f, fillPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 26f
            textPaint.color = if (bought) 0xFF3E2E00.toInt() else Color.WHITE
            canvas.drawText("${i + 1}", 58f, y + 65f, textPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 32f
            textPaint.color = when {
                bought -> 0xFFFFD54F.toInt()
                isNext -> Color.WHITE
                else -> 0xFF607D8B.toInt()
            }
            canvas.drawText(u.name, 100f, y + 46f, textPaint)
            textPaint.textSize = 24f
            textPaint.color = if (i <= tower.tier) 0xFF90A4AE.toInt() else 0xFF546E7A.toInt()
            canvas.drawText(u.desc, 100f, y + 82f, textPaint)

            when {
                bought -> {
                    textPaint.textAlign = Paint.Align.RIGHT
                    textPaint.textSize = 32f
                    textPaint.color = 0xFF81C784.toInt()
                    canvas.drawText("OWNED", 1030f, y + 68f, textPaint)
                }
                isNext -> {
                    val affordable = money >= u.cost
                    drawButton(canvas, upgradeButtonRect(i), "$${u.cost}", if (affordable) 0xFF2E7D32.toInt() else 0xFF37474F.toInt(), 34f)
                }
                else -> {
                    textPaint.textAlign = Paint.Align.RIGHT
                    textPaint.textSize = 28f
                    textPaint.color = 0xFF546E7A.toInt()
                    canvas.drawText("LOCKED", 1030f, y + 66f, textPaint)
                }
            }
        }
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
        canvas.drawRect(0f, 0f, VIRTUAL_W, virtualH, fillPaint)
    }

    private fun drawGameOverOverlay(canvas: Canvas) {
        drawOverlayBackground(canvas)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 84f
        textPaint.color = 0xFFE53935.toInt()
        canvas.drawText("THE HORDE", VIRTUAL_W / 2f, virtualH / 2f - 260f, textPaint)
        canvas.drawText("GOT THROUGH", VIRTUAL_W / 2f, virtualH / 2f - 160f, textPaint)
        textPaint.textSize = 40f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("You survived ${waves.wave} wave${if (waves.wave == 1) "" else "s"} on ${map.name}", VIRTUAL_W / 2f, virtualH / 2f - 40f, textPaint)
        textPaint.textSize = 50f
        textPaint.color = Color.WHITE
        canvas.drawText("TAP FOR MAIN MENU", VIRTUAL_W / 2f, virtualH / 2f + 160f, textPaint)
    }

    private fun drawVictoryOverlay(canvas: Canvas) {
        drawOverlayBackground(canvas)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 92f
        textPaint.color = 0xFFFFD54F.toInt()
        if (mode == Mode.CASTLE) {
            canvas.drawText("NEST DESTROYED!", VIRTUAL_W / 2f, virtualH / 2f - 220f, textPaint)
            textPaint.textSize = 38f
            textPaint.color = 0xFFB0BEC5.toInt()
            canvas.drawText("Your troops wiped out the source of the horde.", VIRTUAL_W / 2f, virtualH / 2f - 110f, textPaint)
            drawButton(canvas, menuButtonRect, "MAIN MENU", 0xFF455A64.toInt(), 44f)
        } else {
            canvas.drawText("CITY SAVED!", VIRTUAL_W / 2f, virtualH / 2f - 220f, textPaint)
            textPaint.textSize = 38f
            textPaint.color = 0xFFB0BEC5.toInt()
            canvas.drawText("You held ${map.name} through all $FINAL_WAVE waves.", VIRTUAL_W / 2f, virtualH / 2f - 110f, textPaint)
            drawButton(canvas, continueRect, "KEEP PLAYING (ENDLESS)", 0xFF2E7D32.toInt(), 44f)
            drawButton(canvas, menuButtonRect, "MAIN MENU", 0xFF455A64.toInt(), 44f)
        }
    }

    private fun drawPauseMenu(canvas: Canvas) {
        drawOverlayBackground(canvas)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 76f
        textPaint.color = Color.WHITE
        canvas.drawText("PAUSED", VIRTUAL_W / 2f, virtualH / 2f - 320f, textPaint)

        drawButton(canvas, resumeRect, "RESUME", 0xFF2E7D32.toInt(), 44f)
        drawButton(canvas, settingsRect, "SETTINGS", 0xFF455A64.toInt(), 44f)
        drawButton(canvas, pauseMenuRect, "MAIN MENU", 0xFF8D6E63.toInt(), 44f)
    }

    private fun drawSettingsOverlay(canvas: Canvas) {
        drawOverlayBackground(canvas)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 64f
        textPaint.color = Color.WHITE
        canvas.drawText("SETTINGS", VIRTUAL_W / 2f, virtualH / 2f - 320f, textPaint)

        drawSettingRow(canvas, bloodRowRect, "Blood effects", bloodFx)
        drawSettingRow(canvas, shakeRowRect, "Screen shake", screenShake)
        drawButton(canvas, settingsBackRect, "BACK", 0xFF455A64.toInt(), 44f)
    }

    private fun drawSettingRow(canvas: Canvas, rect: RectF, label: String, on: Boolean) {
        fillPaint.color = 0xFF263238.toInt()
        canvas.drawRoundRect(rect, 16f, 16f, fillPaint)
        strokePaint.color = 0xFF455A64.toInt()
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(rect, 16f, 16f, strokePaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 38f
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.left + 40f, rect.centerY() + 13f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = if (on) 0xFF81C784.toInt() else 0xFF90A4AE.toInt()
        canvas.drawText(if (on) "ON" else "OFF", rect.right - 40f, rect.centerY() + 13f, textPaint)
    }
}
