package com.enache.zombietd.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class Game(private val progress: Progress = MemoryProgress()) {

    companion object {
        const val VIRTUAL_W = 1080f
        const val MIN_VIRTUAL_H = 1872f // HUD 120 + grid 14*108 + panel 240
        const val START_MONEY = 350
        const val START_LIVES = 20
        const val SPLASH_FALLOFF = 0.6f
        const val SKIP_BONUS = 40
        const val PREP_TIME = 25f       // build time before wave 1
        const val AUTO_WAVE_GAP = 14f   // seconds between automatic waves
        const val NEST_MAX_HP = 4000f
    }

    enum class State { HOME, MISSION_SELECT, MAP_SELECT, PLAYING, GAME_OVER, VICTORY }
    enum class Mode(val label: String, val blurb: String) {
        CAMPAIGN("Campaign", "Missions that unlock as you win"),
        SURVIVAL("Survival", "Endless waves — how long can you last?"),
        CASTLE("Castle vs Nest", "Send troops to destroy the zombie nest")
    }

    var state = State.HOME
        private set
    private var mode = Mode.CAMPAIGN
    private var mission: Mission? = null
    private var targetWaves = 20
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
    private var clock = 0f
    private var waveTimer = PREP_TIME

    private val zombies = mutableListOf<Zombie>()
    private val towers = mutableListOf<Tower>()
    private val troops = mutableListOf<Troop>()
    private val projectiles = mutableListOf<Projectile>()
    private val effects = mutableListOf<Effect>()
    private val waves = WaveManager()

    private var selectedPad: Pad? = null
    private var selectedTower: Tower? = null
    private var terrain: Terrain? = null

    // ---- layout (recomputed when the surface size is known) ----
    var virtualH = MIN_VIRTUAL_H
        private set
    private val gridBottom = GameMap.TOP + GameMap.ROWS * GameMap.TILE

    private val pauseRect = RectF(VIRTUAL_W - 110f, 15f, VIRTUAL_W - 20f, 105f)
    private val speedRect = RectF(VIRTUAL_W - 220f, 15f, VIRTUAL_W - 130f, 105f)
    private val backRect = RectF(40f, 40f, 260f, 130f)

    private var panelTop = 0f
    private var skipWaveRect = RectF()
    private var troopRect = RectF()
    private var buildPanelRect = RectF()
    private var towerPanelRect = RectF()
    private var sellRect = RectF()
    private var nextMissionRect = RectF()
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
        skipWaveRect = RectF(40f, panelTop + 35f, 540f, panelTop + 185f)
        troopRect = RectF(580f, panelTop + 35f, 850f, panelTop + 185f)
        buildPanelRect = RectF(10f, vh - 800f, 1070f, vh - 10f)
        towerPanelRect = RectF(10f, vh - 780f, 1070f, vh - 10f)
        sellRect = RectF(760f, towerPanelRect.top + 25f, 1040f, towerPanelRect.top + 105f)
        nextMissionRect = RectF(140f, vh / 2f - 70f, 940f, vh / 2f + 70f)
        continueRect = RectF(140f, vh / 2f + 100f, 940f, vh / 2f + 240f)
        menuButtonRect = RectF(140f, vh / 2f + 270f, 940f, vh / 2f + 410f)
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
        val top = buildPanelRect.top + 90f
        return RectF(30f + col * 520f, top + row * 225f, 30f + col * 520f + 500f, top + row * 225f + 205f)
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

    private fun missionCardRect(i: Int): RectF {
        val col = i % 2
        val row = i / 2
        return RectF(25f + col * 525f, 300f + row * 330f, 25f + col * 525f + 505f, 300f + row * 330f + 300f)
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
        clock += dt
        for (e in effects) e.update(dt)
        effects.removeAll { it.done }
        if (state != State.PLAYING || paused) return
        repeat(speed) { step(dt) }
    }

    /** True while more waves are still due in this run. */
    private fun wavesRemain() =
        mode != Mode.CASTLE && (mode == Mode.SURVIVAL || endlessMode || waves.wave < targetWaves)

    /** The next wave may be skipped to only once the current one has fully spawned. */
    private fun canSkipWave() = wavesRemain() && !waves.spawning

    private fun step(dt: Float) {
        if (shake > 0f) shake -= dt

        // the nest never stops spawning
        if (mode == Mode.CASTLE && !waves.spawning && zombies.size < 8) {
            waves.startNextWave()
        }

        // waves arrive on their own schedule; the player can only skip ahead
        if (wavesRemain() && !waves.spawning) {
            waveTimer -= dt
            if (waveTimer <= 0f) startWave(bonus = false)
        }

        waves.update(dt) { type -> zombies.add(Zombie(type, map.paths.random())) }

        // barracks train and replace their squads
        for (t in towers) {
            if (!t.type.isBarracks) continue
            t.soldiers.removeAll { !it.alive }
            t.respawnTimer -= dt
            val rally = t.rallyPoint ?: continue
            if (t.soldiers.size < t.squadSize && t.respawnTimer <= 0f) {
                val slot = t.soldiers.size
                val a = slot * 2.1f
                val dest = PointF(rally.x + cos(a) * 18f, rally.y + sin(a) * 18f)
                val s = Troop(listOf(PointF(t.pos.x, t.pos.y), dest), t.squadHp, t.squadDps, owner = t)
                t.soldiers.add(s)
                troops.add(s)
                t.respawnTimer = Tower.SQUAD_RESPAWN
            }
        }

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
                target.hp -= t.dps * dt
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
            } else if (t.atDestination && t.isDeployed && mode == Mode.CASTLE) {
                // satchel charge against the nest; elsewhere troops hold the entrance
                nestHp -= Troop.NEST_DAMAGE
                shake = 0.3f
                val nest = nestCenter()
                effects.add(Effect.splat(nest.x, nest.y, 80f))
                effects.add(Effect.text(nest.x, nest.y - 70f, "-${Troop.NEST_DAMAGE.roundToInt()}", 0xFFFF7043.toInt(), 52f))
                trIt.remove()
                if (nestHp <= 0f) {
                    nestHp = 0f
                    win()
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
                    selectedPad = null
                    selectedTower = null
                }
            }
        }

        if (mode != Mode.CASTLE && waveActive && !waves.spawning && zombies.isEmpty() && state == State.PLAYING) {
            waveActive = false
            waveTimer = minOf(waveTimer, AUTO_WAVE_GAP)
            val bonus = 60 + waves.wave * 8
            money += bonus
            effects.add(Effect.text(VIRTUAL_W / 2f, 760f, "Wave ${waves.wave} cleared!  +$$bonus", 0xFF81C784.toInt(), 56f))
            if (mode == Mode.CAMPAIGN && waves.wave >= targetWaves && !endlessMode) win()
        }
    }

    private fun startWave(bonus: Boolean) {
        if (bonus) {
            money += SKIP_BONUS
            effects.add(Effect.text(skipWaveRect.centerX(), panelTop - 20f, "Skipped! +$$SKIP_BONUS", 0xFF81C784.toInt()))
        }
        waves.startNextWave()
        waveActive = true
        waveTimer = AUTO_WAVE_GAP
    }

    private fun win() {
        state = State.VICTORY
        selectedPad = null
        selectedTower = null
        val m = mission
        if (mode == Mode.CAMPAIGN && m != null && progress.unlockedMissions() < m.index + 2) {
            progress.setUnlockedMissions(m.index + 2)
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
                        state = if (mode == Mode.CAMPAIGN) State.MISSION_SELECT else State.MAP_SELECT
                        return
                    }
                }
            }
            State.MISSION_SELECT -> {
                if (backRect.contains(x, y)) {
                    state = State.HOME
                    return
                }
                val unlocked = progress.unlockedMissions()
                for (i in Missions.all.indices) {
                    if (missionCardRect(i).contains(x, y) && i < unlocked) {
                        startMission(Missions.all[i])
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
                        mission = null
                        waves.hordeScale = 1f
                        startNewGame(Maps.all[i])
                        return
                    }
                }
            }
            State.GAME_OVER -> state = State.HOME
            State.VICTORY -> {
                val m = mission
                when {
                    mode == Mode.CAMPAIGN && m != null && m.index + 1 < Missions.all.size && nextMissionRect.contains(x, y) ->
                        startMission(Missions.all[m.index + 1])
                    mode == Mode.CAMPAIGN && continueRect.contains(x, y) -> {
                        endlessMode = true
                        state = State.PLAYING
                    }
                    menuButtonRect.contains(x, y) -> state = State.HOME
                }
            }
            State.PLAYING -> handlePlayingTap(x, y)
        }
    }

    private fun handlePlayingTap(x: Float, y: Float) {
        if (paused) {
            if (inSettings) {
                when {
                    bloodRowRect.contains(x, y) -> bloodFx = !bloodFx
                    shakeRowRect.contains(x, y) -> {
                        screenShake = !screenShake
                        if (!screenShake) shake = 0f
                    }
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

        val pad = selectedPad
        if (pad != null) {
            handleBuildPanelTap(x, y, pad)
            return
        }
        val tower = selectedTower
        if (tower != null) {
            handleTowerPanelTap(x, y, tower)
            return
        }

        if (y >= panelTop) {
            if (troopRect.contains(x, y)) {
                if (money >= Troop.COST && deployedCount() < Troop.MAX_ACTIVE) {
                    money -= Troop.COST
                    troops.add(Troop.fromLane(map.paths.random()))
                }
                return
            }
            if (skipWaveRect.contains(x, y) && canSkipWave()) startWave(bonus = true)
            return
        }
        if (y < GameMap.TOP || y > gridBottom) return

        val c = (x / GameMap.TILE).toInt()
        val r = ((y - GameMap.TOP) / GameMap.TILE).toInt()
        val hit = towers.find { it.col == c && it.row == r }
        val padHere = map.padAt(c, r)
        when {
            hit != null -> selectedTower = hit
            padHere != null -> selectedPad = padHere
        }
    }

    private fun deployedCount() = troops.count { it.alive && it.isDeployed }

    private fun handleBuildPanelTap(x: Float, y: Float, pad: Pad) {
        if (!buildPanelRect.contains(x, y)) {
            selectedPad = null
            return
        }
        for (i in TowerType.entries.indices) {
            if (buildCardRect(i).contains(x, y)) {
                val type = TowerType.entries[i]
                if (money >= type.cost) {
                    money -= type.cost
                    val t = Tower(type, pad.c, pad.r)
                    if (type.isBarracks) t.rallyPoint = map.nearestPathPoint(t.pos.x, t.pos.y)
                    towers.add(t)
                    selectedPad = null
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
            troops.removeAll { it.owner === tower }
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

    private fun startMission(m: Mission) {
        mission = m
        targetWaves = m.waves
        waves.hordeScale = m.hordeScale
        startNewGame(m.map)
    }

    private fun startNewGame(selected: GameMap) {
        map = selected
        money = START_MONEY
        lives = START_LIVES
        speed = 1
        endlessMode = false
        waveActive = false
        waveTimer = PREP_TIME
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
        selectedPad = null
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
            State.MISSION_SELECT -> {
                drawMissionSelect(canvas)
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

    private fun drawMenuBackground(canvas: Canvas) {
        fillPaint.color = 0xFF10150F.toInt()
        canvas.drawRect(0f, 0f, VIRTUAL_W, virtualH, fillPaint)
    }

    private fun drawHome(canvas: Canvas) {
        drawMenuBackground(canvas)
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
            drawButton(canvas, rect, m.label.uppercase(), 0xFF2E7D32.toInt(), 48f)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 28f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText(m.blurb, VIRTUAL_W / 2f, rect.bottom + 45f, textPaint)
        }

        val done = progress.unlockedMissions() - 1
        textPaint.textSize = 28f
        textPaint.color = 0xFF78909C.toInt()
        canvas.drawText("Campaign progress: $done / ${Missions.all.size} missions complete", VIRTUAL_W / 2f, virtualH - 120f, textPaint)
        canvas.drawText("Build soldiers on open ground  •  Deploy troops for melee", VIRTUAL_W / 2f, virtualH - 70f, textPaint)
    }

    private fun drawMissionSelect(canvas: Canvas) {
        drawMenuBackground(canvas)
        drawButton(canvas, backRect, "< BACK", 0xFF455A64.toInt(), 34f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 48f
        textPaint.color = 0xFF7CB342.toInt()
        canvas.drawText("CAMPAIGN", VIRTUAL_W / 2f, 200f, textPaint)
        textPaint.textSize = 30f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("Finish a mission to unlock the next", VIRTUAL_W / 2f, 255f, textPaint)

        val unlocked = progress.unlockedMissions()
        for (i in Missions.all.indices) {
            val m = Missions.all[i]
            val rect = missionCardRect(i)
            val locked = i >= unlocked
            val complete = i < unlocked - 1

            fillPaint.color = if (locked) 0xFF151C20.toInt() else 0xFF1C262B.toInt()
            canvas.drawRoundRect(rect, 20f, 20f, fillPaint)
            strokePaint.color = when {
                complete -> 0xFFFFD54F.toInt()
                locked -> 0xFF2C383E.toInt()
                else -> 0xFF7CB342.toInt()
            }
            strokePaint.strokeWidth = 4f
            canvas.drawRoundRect(rect, 20f, 20f, strokePaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 30f
            textPaint.color = if (locked) 0xFF546E7A.toInt() else 0xFF90A4AE.toInt()
            canvas.drawText("MISSION ${m.number}", rect.left + 28f, rect.top + 52f, textPaint)
            textPaint.textSize = 38f
            textPaint.color = if (locked) 0xFF607D8B.toInt() else Color.WHITE
            canvas.drawText(m.title, rect.left + 28f, rect.top + 104f, textPaint)
            textPaint.textSize = 27f
            textPaint.color = if (locked) 0xFF455A64.toInt() else 0xFFB0BEC5.toInt()
            canvas.drawText(m.map.name, rect.left + 28f, rect.top + 150f, textPaint)
            canvas.drawText("${m.waves} waves  •  horde x${"%.1f".format(m.hordeScale)}", rect.left + 28f, rect.top + 188f, textPaint)

            textPaint.textSize = 34f
            when {
                complete -> {
                    textPaint.color = 0xFFFFD54F.toInt()
                    canvas.drawText("COMPLETE  ✓", rect.left + 28f, rect.bottom - 34f, textPaint)
                }
                locked -> {
                    textPaint.color = 0xFF546E7A.toInt()
                    canvas.drawText("LOCKED", rect.left + 28f, rect.bottom - 34f, textPaint)
                }
                else -> {
                    textPaint.color = 0xFF81C784.toInt()
                    canvas.drawText("PLAY  >", rect.left + 28f, rect.bottom - 34f, textPaint)
                }
            }
        }
    }

    private fun drawMapSelect(canvas: Canvas) {
        drawMenuBackground(canvas)
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
                    key in m.water -> 0xFF3A6E8F.toInt()
                    key in m.pathCells -> 0xFF8C7457.toInt()
                    key in m.padCells -> 0xFF8B877D.toInt()
                    key in m.blocked -> 0xFF2F5E2A.toInt()
                    else -> 0xFF4F6E3C.toInt()
                }
                canvas.drawRect(
                    px + c * cellSize, py + r * cellSize,
                    px + (c + 1) * cellSize, py + (r + 1) * cellSize, fillPaint
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
        var t = terrain
        if (t == null || t.map !== map || t.height != virtualH.toInt()) {
            t = Terrain(map, VIRTUAL_W.toInt(), virtualH.toInt())
            terrain = t
        }
        canvas.drawBitmap(t.bitmap, 0f, 0f, null)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 60f
        if (mode == Mode.CASTLE) {
            val nest = nestCenter()
            fillPaint.color = 0xFF3A2440.toInt()
            canvas.drawCircle(nest.x, nest.y, 52f, fillPaint)
            fillPaint.color = 0xFF6A1B9A.toInt()
            canvas.drawCircle(nest.x, nest.y, 38f, fillPaint)
            fillPaint.color = 0xFF9CCC65.toInt()
            canvas.drawCircle(nest.x - 15f, nest.y - 10f, 7f, fillPaint)
            canvas.drawCircle(nest.x + 12f, nest.y + 14f, 6f, fillPaint)
            canvas.drawCircle(nest.x + 8f, nest.y - 18f, 5f, fillPaint)
            val frac = (nestHp / NEST_MAX_HP).coerceIn(0f, 1f)
            fillPaint.color = 0xC0212121.toInt()
            canvas.drawRect(nest.x - 70f, nest.y - 78f, nest.x + 70f, nest.y - 62f, fillPaint)
            fillPaint.color = 0xFFAB47BC.toInt()
            canvas.drawRect(nest.x - 70f, nest.y - 78f, nest.x - 70f + 140f * frac, nest.y - 62f, fillPaint)
            textPaint.color = 0xFFD7CCC8.toInt()
            for ((c, r) in map.exitCells) {
                val p = GameMap.cellCenter(c, r)
                canvas.drawText("🏰", p.x, p.y + 20f, textPaint)
            }
        }

        drawPads(canvas)

        val tower = selectedTower
        if (tower != null) {
            fillPaint.color = 0x2264B5F6
            canvas.drawCircle(tower.pos.x, tower.pos.y, tower.range, fillPaint)
            strokePaint.color = 0xAA64B5F6.toInt()
            strokePaint.strokeWidth = 3f
            canvas.drawCircle(tower.pos.x, tower.pos.y, tower.range, strokePaint)
            val rally = tower.rallyPoint
            if (rally != null) {
                strokePaint.color = 0xAAFFD54F.toInt()
                canvas.drawCircle(rally.x, rally.y, 30f, strokePaint)
            }
        }
    }

    /**
     * Vacant emplacements: a stone platform with a hammer marker, pulsing so
     * open build spots are easy to find. Occupied pads are hidden by the tower.
     */
    private fun drawPads(canvas: Canvas) {
        val pulse = (sin(clock * 2.2f) + 1f) / 2f
        for (pad in map.pads) {
            if (towers.any { it.col == pad.c && it.row == pad.r }) continue
            val x = pad.pos.x
            val y = pad.pos.y
            val selected = pad === selectedPad

            fillPaint.color = 0x44000000
            canvas.drawCircle(x, y + 6f, 38f, fillPaint)
            fillPaint.color = 0xFF6E6A62.toInt()
            canvas.drawCircle(x, y, 36f, fillPaint)
            fillPaint.color = 0xFF8B877D.toInt()
            canvas.drawCircle(x, y - 2f, 31f, fillPaint)
            // cobbles around the rim
            fillPaint.color = 0xFF7A766D.toInt()
            for (i in 0 until 8) {
                val a = i * 0.785f + 0.4f
                canvas.drawCircle(x + cos(a) * 26f, y - 2f + sin(a) * 26f, 6.5f, fillPaint)
            }
            // sunken socket
            fillPaint.color = 0xFF5C594F.toInt()
            canvas.drawCircle(x, y - 2f, 17f, fillPaint)
            fillPaint.color = 0xFF6B6759.toInt()
            canvas.drawCircle(x, y - 4f, 15f, fillPaint)

            // hammer marker
            fillPaint.color = if (selected) 0xFFFFE082.toInt() else 0xFFCFD8DC.toInt()
            canvas.drawRoundRect(x - 3f, y - 14f, x + 3f, y + 8f, 2f, 2f, fillPaint)
            canvas.drawRoundRect(x - 12f, y - 20f, x + 12f, y - 10f, 3f, 3f, fillPaint)

            strokePaint.color = if (selected) Color.WHITE else (0x55FFFFFF + (60 * pulse).toInt().shl(24))
            strokePaint.strokeWidth = if (selected) 5f else 3f
            canvas.drawCircle(x, y - 2f, 34f, strokePaint)
            if (selected) {
                fillPaint.color = 0x33FFFFFF
                canvas.drawCircle(x, y - 2f, 34f, fillPaint)
            }
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

    private fun weaponFor(type: TowerType) = when (type) {
        TowerType.ASSAULT -> Figure.Weapon.RIFLE
        TowerType.SUPPORT -> Figure.Weapon.LMG
        TowerType.ENGINEER -> Figure.Weapon.LAUNCHER
        TowerType.RECON -> Figure.Weapon.SNIPER
        TowerType.BARRACKS -> Figure.Weapon.NONE
    }

    private fun drawSoldier(canvas: Canvas, x: Float, y: Float, type: TowerType, aim: Float, scale: Float) {
        Figure.draw(
            canvas, x, y + 28f * scale, scale, Figure.facingFromAngle(aim), 0f, false, Figure.Kind.SOLDIER,
            bodyColor = 0xFF5F6E42.toInt(), accent = type.color, skin = 0xFFE0B894.toInt(),
            aimAngle = aim, weapon = weaponFor(type)
        )
    }

    private fun drawBarracks(canvas: Canvas, x: Float, y: Float, accent: Int, scale: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        fillPaint.color = 0x55000000
        canvas.drawRoundRect(-38f, -22f, 46f, 36f, 8f, 8f, fillPaint)
        fillPaint.color = 0xFF4E5A36.toInt()
        canvas.drawRoundRect(-42f, -30f, 42f, 30f, 8f, 8f, fillPaint)
        fillPaint.color = 0xFF5F6E42.toInt()
        canvas.drawRect(-42f, -30f, 42f, 0f, fillPaint)
        fillPaint.color = 0xFF3E4A2C.toInt()
        canvas.drawRect(-42f, -2f, 42f, 2f, fillPaint)
        fillPaint.color = 0xFF2C3320.toInt()
        canvas.drawRoundRect(-10f, 8f, 10f, 30f, 3f, 3f, fillPaint)
        // flag
        fillPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawRect(30f, -58f, 34f, -28f, fillPaint)
        fillPaint.color = accent
        canvas.drawRect(34f, -58f, 54f, -46f, fillPaint)
        canvas.restore()
    }

    private fun drawTowers(canvas: Canvas) {
        for (t in towers) {
            fillPaint.color = 0x44000000
            canvas.drawCircle(t.pos.x, t.pos.y + 6f, 40f, fillPaint)
            fillPaint.color = 0xFF6B6B66.toInt()
            canvas.drawCircle(t.pos.x, t.pos.y, 38f, fillPaint)
            strokePaint.color = t.type.color
            strokePaint.strokeWidth = 3f
            canvas.drawCircle(t.pos.x, t.pos.y, 38f, strokePaint)

            if (t.type.isBarracks) drawBarracks(canvas, t.pos.x, t.pos.y, t.type.color, 0.9f)
            else drawSoldier(canvas, t.pos.x, t.pos.y, t.type, t.angle, 1f)

            fillPaint.color = 0xFFFFD54F.toInt()
            for (i in 0 until min(t.tier, t.type.path.size)) {
                canvas.drawCircle(t.pos.x - 26f + i * 13f, t.pos.y + 46f, 4.5f, fillPaint)
            }
        }
    }

    private fun drawTroops(canvas: Canvas) {
        for (t in troops) {
            val body = if (t.isDeployed) 0xFF4E6B3A.toInt() else 0xFF556B2F.toInt()
            val attack = if (t.engaged) (sin(clock * 12f) + 1f) / 2f else 0f
            Figure.draw(
                canvas, t.pos.x, t.pos.y + 24f, 0.85f, t.facingRight, t.phase,
                !t.engaged && !t.atDestination, Figure.Kind.TROOP,
                bodyColor = body, accent = 0xFF9E9D24.toInt(), skin = 0xFFE0B894.toInt(),
                weapon = Figure.Weapon.KNIFE, attack = attack
            )
            if (t.hp < t.maxHp) {
                val top = t.pos.y - 44f
                fillPaint.color = 0xC0212121.toInt()
                canvas.drawRect(t.pos.x - 22f, top, t.pos.x + 22f, top + 7f, fillPaint)
                val frac = (t.hp / t.maxHp).coerceIn(0f, 1f)
                fillPaint.color = 0xFF4FC3F7.toInt()
                canvas.drawRect(t.pos.x - 22f, top, t.pos.x - 22f + 44f * frac, top + 7f, fillPaint)
            }
        }
    }

    private fun drawZombies(canvas: Canvas) {
        for (z in zombies) {
            val jacket = when (z.type) {
                ZombieType.WALKER -> 0xFF6D7B8A.toInt()
                ZombieType.RUNNER -> 0xFF8D6E63.toInt()
                ZombieType.BRUTE -> 0xFF4E5A63.toInt()
                ZombieType.BOSS -> 0xFF5E2B7A.toInt()
            }
            val scale = when (z.type) {
                ZombieType.WALKER -> 0.95f
                ZombieType.RUNNER -> 0.85f
                ZombieType.BRUTE -> 1.25f
                ZombieType.BOSS -> 1.75f
            }
            val thick = when (z.type) {
                ZombieType.WALKER -> 4.5f
                ZombieType.RUNNER -> 3.6f
                ZombieType.BRUTE -> 6.5f
                ZombieType.BOSS -> 8.5f
            }
            Figure.draw(
                canvas, z.pos.x, z.pos.y + 28f * scale, scale, z.facingRight, z.wobble, true, Figure.Kind.ZOMBIE,
                bodyColor = jacket, accent = 0, skin = 0xFF9CB86B.toInt(), thickness = thick
            )

            val r = z.type.radius
            if (z.isSlowed) {
                strokePaint.color = 0xCC81D4FA.toInt()
                strokePaint.strokeWidth = 4f
                canvas.drawCircle(z.pos.x, z.pos.y + 28f * scale, 18f * scale, strokePaint)
            }
            if (z.isBurning) {
                strokePaint.color = 0xCCFF7043.toInt()
                strokePaint.strokeWidth = 4f
                canvas.drawCircle(z.pos.x, z.pos.y + 28f * scale, 24f * scale, strokePaint)
            }
            if (z.hp < z.maxHp) {
                val w = r * 2f
                val top = z.pos.y - 40f * scale
                fillPaint.color = 0xC0212121.toInt()
                canvas.drawRect(z.pos.x - w / 2, top, z.pos.x + w / 2, top + 9f, fillPaint)
                val frac = (z.hp / z.maxHp).coerceIn(0f, 1f)
                fillPaint.color = if (frac > 0.4f) 0xFF66BB6A.toInt() else 0xFFEF5350.toInt()
                canvas.drawRect(z.pos.x - w / 2, top, z.pos.x - w / 2 + w * frac, top + 9f, fillPaint)
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
            mode == Mode.CAMPAIGN && !endlessMode -> "Wave ${waves.wave}/$targetWaves"
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
        } else if (canSkipWave()) {
            // the next wave is already on a timer; skipping ahead pays a bonus
            val secs = kotlin.math.ceil(waveTimer.toDouble()).toInt().coerceAtLeast(0)
            drawButton(canvas, skipWaveRect, "SKIP TO WAVE ${waves.wave + 1}  +$$SKIP_BONUS", 0xFF2E7D32.toInt(), 34f)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 24f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText("arrives in ${secs}s", skipWaveRect.centerX(), skipWaveRect.bottom + 34f, textPaint)
        } else {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 38f
            textPaint.color = 0xFFEF9A9A.toInt()
            canvas.drawText("Wave ${waves.wave} incoming", 40f, panelTop + 90f, textPaint)
            textPaint.textSize = 28f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText("${waves.queued} still spawning • ${zombies.size} on field", 40f, panelTop + 140f, textPaint)
        }

        val deployed = deployedCount()
        val canDeploy = money >= Troop.COST && deployed < Troop.MAX_ACTIVE
        drawButton(canvas, troopRect, "TROOP $${Troop.COST}", if (canDeploy) 0xFF00695C.toInt() else 0xFF37474F.toInt(), 34f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 24f
        textPaint.color = 0xFF90A4AE.toInt()
        canvas.drawText("$deployed/${Troop.MAX_ACTIVE} deployed", troopRect.centerX(), troopRect.bottom + 34f, textPaint)

        if (selectedPad != null) drawBuildPanel(canvas)
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
        canvas.drawText("Build", 40f, buildPanelRect.top + 62f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 28f
        textPaint.color = 0xFF78909C.toInt()
        canvas.drawText("tap outside to cancel", 1040f, buildPanelRect.top + 58f, textPaint)

        for (i in TowerType.entries.indices) {
            val type = TowerType.entries[i]
            val rect = buildCardRect(i)
            val affordable = money >= type.cost

            fillPaint.color = if (affordable) 0xFF2C3B42.toInt() else 0xFF232B30.toInt()
            canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
            strokePaint.color = if (affordable) type.color else 0xFF4A555B.toInt()
            strokePaint.strokeWidth = 4f
            canvas.drawRoundRect(rect, 18f, 18f, strokePaint)

            val ix = rect.left + 72f
            val iy = rect.centerY()
            fillPaint.color = 0xFF37474F.toInt()
            canvas.drawCircle(ix, iy, 50f, fillPaint)
            if (type.isBarracks) drawBarracks(canvas, ix, iy + 6f, type.color, 0.7f)
            else drawSoldier(canvas, ix, iy - 4f, type, (-Math.PI / 2).toFloat(), 0.95f)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 34f
            textPaint.color = if (affordable) Color.WHITE else 0xFF90A4AE.toInt()
            canvas.drawText(type.label, rect.left + 140f, iy - 28f, textPaint)
            textPaint.textSize = 26f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText(type.blurb, rect.left + 140f, iy + 10f, textPaint)
            textPaint.textSize = 30f
            textPaint.color = if (affordable) 0xFFFFD54F.toInt() else 0xFFEF5350.toInt()
            canvas.drawText("$${type.cost}", rect.left + 140f, iy + 52f, textPaint)
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
        val stats = if (tower.type.isBarracks) {
            "SQUAD ${tower.soldiers.count { it.alive }}/${tower.squadSize}   HP ${tower.squadHp.roundToInt()}   DPS ${tower.squadDps.roundToInt()}"
        } else buildString {
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
        val where = mission?.let { "Mission ${it.number}: ${it.title}" } ?: map.name
        canvas.drawText("You survived ${waves.wave} wave${if (waves.wave == 1) "" else "s"} — $where", VIRTUAL_W / 2f, virtualH / 2f - 40f, textPaint)
        textPaint.textSize = 50f
        textPaint.color = Color.WHITE
        canvas.drawText("TAP FOR MAIN MENU", VIRTUAL_W / 2f, virtualH / 2f + 160f, textPaint)
    }

    private fun drawVictoryOverlay(canvas: Canvas) {
        drawOverlayBackground(canvas)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 92f
        textPaint.color = 0xFFFFD54F.toInt()
        val m = mission
        when {
            mode == Mode.CASTLE -> {
                canvas.drawText("NEST DESTROYED!", VIRTUAL_W / 2f, virtualH / 2f - 220f, textPaint)
                textPaint.textSize = 38f
                textPaint.color = 0xFFB0BEC5.toInt()
                canvas.drawText("Your troops wiped out the source of the horde.", VIRTUAL_W / 2f, virtualH / 2f - 110f, textPaint)
                drawButton(canvas, menuButtonRect, "MAIN MENU", 0xFF455A64.toInt(), 44f)
            }
            m != null -> {
                canvas.drawText("MISSION COMPLETE", VIRTUAL_W / 2f, virtualH / 2f - 220f, textPaint)
                textPaint.textSize = 38f
                textPaint.color = 0xFFB0BEC5.toInt()
                canvas.drawText("Mission ${m.number}: ${m.title} — ${m.waves} waves held.", VIRTUAL_W / 2f, virtualH / 2f - 130f, textPaint)
                if (m.index + 1 < Missions.all.size) {
                    drawButton(canvas, nextMissionRect, "NEXT MISSION  >", 0xFF2E7D32.toInt(), 44f)
                } else {
                    canvas.drawText("Campaign complete — the city is yours.", VIRTUAL_W / 2f, virtualH / 2f, textPaint)
                }
                drawButton(canvas, continueRect, "KEEP PLAYING (ENDLESS)", 0xFF455A64.toInt(), 40f)
                drawButton(canvas, menuButtonRect, "MAIN MENU", 0xFF455A64.toInt(), 40f)
            }
            else -> {
                canvas.drawText("CITY SAVED!", VIRTUAL_W / 2f, virtualH / 2f - 220f, textPaint)
                drawButton(canvas, continueRect, "KEEP PLAYING (ENDLESS)", 0xFF2E7D32.toInt(), 44f)
                drawButton(canvas, menuButtonRect, "MAIN MENU", 0xFF455A64.toInt(), 44f)
            }
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
