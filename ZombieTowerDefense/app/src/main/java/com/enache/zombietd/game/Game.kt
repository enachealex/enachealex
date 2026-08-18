package com.enache.zombietd.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
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
    }

    enum class State { MENU, PLAYING, GAME_OVER, VICTORY }

    var state = State.MENU
        private set
    private var paused = false

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
    private val upgradeRect = RectF(40f, panelTop + 60f, 520f, panelTop + 200f)
    private val sellRect = RectF(560f, panelTop + 60f, 1040f, panelTop + 200f)
    private val continueRect = RectF(140f, 1050f, 940f, 1190f)
    private val restartRect = RectF(140f, 1240f, 940f, 1380f)

    private fun cardRect(i: Int) = RectF(15f + i * 265f, panelTop + 18f, 15f + i * 265f + 250f, panelTop + 222f)

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

        waves.update(dt) { type -> zombies.add(Zombie(type, waves.hpMul())) }

        for (z in zombies) z.update(dt)
        for (t in towers) t.update(dt, zombies, projectiles)

        val pIt = projectiles.iterator()
        while (pIt.hasNext()) {
            val p = pIt.next()
            if (p.update(dt)) {
                if (p.target.alive) hitZombie(p.target, p)
                pIt.remove()
            }
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

    private fun hitZombie(z: Zombie, p: Projectile) {
        z.hp -= p.damage
        when (p.kind) {
            TowerType.FROST -> z.applySlow(0.55f, 2f)
            TowerType.FLAME -> z.applyBurn(12f, 2f)
            else -> {}
        }
    }

    // ================================================================== input

    fun onTap(x: Float, y: Float) {
        when (state) {
            State.MENU -> startNewGame()
            State.GAME_OVER -> startNewGame()
            State.VICTORY -> when {
                continueRect.contains(x, y) -> {
                    endlessMode = true
                    state = State.PLAYING
                }
                restartRect.contains(x, y) -> startNewGame()
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
        if (y >= panelTop) {
            handlePanelTap(x, y)
            return
        }
        if (y < GameMap.TOP) return

        val c = (x / GameMap.TILE).toInt()
        val r = ((y - GameMap.TOP) / GameMap.TILE).toInt()
        val tower = towers.find { it.col == c && it.row == r }
        when {
            tower != null -> {
                selectedTower = tower
                selectedCell = null
            }
            GameMap.isBuildable(c, r) -> {
                selectedCell = c to r
                selectedTower = null
            }
            else -> {
                selectedCell = null
                selectedTower = null
            }
        }
    }

    private fun handlePanelTap(x: Float, y: Float) {
        val cell = selectedCell
        val tower = selectedTower
        when {
            cell != null -> {
                for (i in TowerType.entries.indices) {
                    if (cardRect(i).contains(x, y)) {
                        val type = TowerType.entries[i]
                        if (money >= type.cost) {
                            money -= type.cost
                            towers.add(Tower(type, cell.first, cell.second))
                            selectedCell = null
                        } else {
                            val p = GameMap.cellCenter(cell.first, cell.second)
                            effects.add(Effect.text(p.x, p.y, "Need $${type.cost}", 0xFFEF5350.toInt()))
                        }
                        return
                    }
                }
                selectedCell = null
            }
            tower != null -> {
                if (upgradeRect.contains(x, y)) {
                    if (!tower.isMaxLevel && money >= tower.upgradeCost) {
                        money -= tower.upgradeCost
                        tower.upgrade()
                        effects.add(Effect.text(tower.pos.x, tower.pos.y - 50f, "Level ${tower.level}!", 0xFF81C784.toInt()))
                    }
                    return
                }
                if (sellRect.contains(x, y)) {
                    money += tower.sellValue
                    towers.remove(tower)
                    effects.add(Effect.text(tower.pos.x, tower.pos.y - 50f, "+$${tower.sellValue}", 0xFFFFD54F.toInt()))
                    selectedTower = null
                    return
                }
                selectedTower = null
            }
            else -> {
                if (startWaveRect.contains(x, y) && !waveActive && waves.wave < waveCap()) {
                    waves.startNextWave()
                    waveActive = true
                }
            }
        }
    }

    private fun waveCap() = if (endlessMode) Int.MAX_VALUE else FINAL_WAVE

    private fun startNewGame() {
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
        canvas.save()
        if (shake > 0f) {
            canvas.translate((Random.nextFloat() - 0.5f) * 16f, (Random.nextFloat() - 0.5f) * 16f)
        }
        drawMap(canvas)
        drawSplats(canvas)
        drawTowers(canvas)
        drawZombies(canvas)
        drawProjectiles(canvas)
        drawTexts(canvas)
        canvas.restore()

        drawHud(canvas)
        drawPanel(canvas)

        when (state) {
            State.MENU -> drawMenuOverlay(canvas)
            State.GAME_OVER -> drawGameOverOverlay(canvas)
            State.VICTORY -> drawVictoryOverlay(canvas)
            State.PLAYING -> if (paused) drawPausedOverlay(canvas)
        }
    }

    private fun drawMap(canvas: Canvas) {
        for (r in 0 until GameMap.ROWS) {
            for (c in 0 until GameMap.COLS) {
                val left = c * GameMap.TILE
                val top = GameMap.TOP + r * GameMap.TILE
                val onPath = (c to r) in GameMap.pathCells
                fillPaint.color = when {
                    onPath -> if ((c + r) % 2 == 0) 0xFF6D5B45.toInt() else 0xFF66553F.toInt()
                    else -> if ((c + r) % 2 == 0) 0xFF39543A.toInt() else 0xFF344E35.toInt()
                }
                canvas.drawRect(left, top, left + GameMap.TILE, top + GameMap.TILE, fillPaint)
            }
        }

        // spawn arrow and exit marker
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 64f
        textPaint.color = 0xFFD7CCC8.toInt()
        val entry = GameMap.cellCenter(0, 1)
        canvas.drawText("➡", entry.x, entry.y + 22f, textPaint)
        val exit = GameMap.cellCenter(1, 12)
        canvas.drawText("☠", exit.x, exit.y + 22f, textPaint)

        // selection highlight + range preview
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
            fillPaint.color = 0xFF37474F.toInt()
            canvas.drawCircle(t.pos.x, t.pos.y, 46f, fillPaint)

            // barrel
            canvas.save()
            canvas.translate(t.pos.x, t.pos.y)
            canvas.rotate(Math.toDegrees(t.angle.toDouble()).toFloat())
            fillPaint.color = 0xFF263238.toInt()
            canvas.drawRect(0f, -10f, 54f, 10f, fillPaint)
            canvas.restore()

            fillPaint.color = t.type.color
            canvas.drawCircle(t.pos.x, t.pos.y, 30f, fillPaint)
            fillPaint.color = 0xFF263238.toInt()
            canvas.drawCircle(t.pos.x, t.pos.y, 12f, fillPaint)

            // level pips
            fillPaint.color = 0xFFFFD54F.toInt()
            for (i in 0 until t.level) {
                canvas.drawCircle(t.pos.x - 14f + i * 14f, t.pos.y + 44f, 5f, fillPaint)
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

            // reaching arms
            val armBase = kotlin.math.atan2(z.dirY, z.dirX)
            for (side in intArrayOf(-1, 1)) {
                val a = armBase + side * (0.55f + sin(z.wobble + side) * 0.25f)
                fillPaint.color = bodyColor
                canvas.drawCircle(z.pos.x + cos(a) * r * 1.15f, z.pos.y + sin(a) * r * 1.15f, r * 0.32f, fillPaint)
            }

            // body
            fillPaint.color = bodyColor
            canvas.drawCircle(z.pos.x, z.pos.y, r, fillPaint)
            strokePaint.color = 0x66000000
            strokePaint.strokeWidth = 4f
            canvas.drawCircle(z.pos.x, z.pos.y, r, strokePaint)

            // eyes
            fillPaint.color = 0xFFD32F2F.toInt()
            val px = -z.dirY
            val py = z.dirX
            val ex = z.pos.x + z.dirX * r * 0.45f
            val ey = z.pos.y + z.dirY * r * 0.45f
            canvas.drawCircle(ex + px * r * 0.3f, ey + py * r * 0.3f, r * 0.12f, fillPaint)
            canvas.drawCircle(ex - px * r * 0.3f, ey - py * r * 0.3f, r * 0.12f, fillPaint)

            // status rings
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

            // health bar
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
                TowerType.FLAME -> 7f
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

        val cell = selectedCell
        val tower = selectedTower
        when {
            cell != null -> drawBuildCards(canvas)
            tower != null -> drawTowerPanel(canvas, tower)
            else -> drawDefaultPanel(canvas)
        }
    }

    private fun drawBuildCards(canvas: Canvas) {
        for (i in TowerType.entries.indices) {
            val type = TowerType.entries[i]
            val rect = cardRect(i)
            val affordable = money >= type.cost

            fillPaint.color = if (affordable) 0xFF2C3B42.toInt() else 0xFF232B30.toInt()
            canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
            strokePaint.color = if (affordable) type.color else 0xFF4A555B.toInt()
            strokePaint.strokeWidth = 4f
            canvas.drawRoundRect(rect, 18f, 18f, strokePaint)

            fillPaint.color = if (affordable) type.color else 0xFF607D8B.toInt()
            canvas.drawCircle(rect.centerX(), rect.top + 62f, 32f, fillPaint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 36f
            textPaint.color = if (affordable) Color.WHITE else 0xFF90A4AE.toInt()
            canvas.drawText(type.label, rect.centerX(), rect.top + 128f, textPaint)
            textPaint.textSize = 28f
            textPaint.color = 0xFF90A4AE.toInt()
            canvas.drawText(type.blurb, rect.centerX(), rect.top + 162f, textPaint)
            textPaint.textSize = 32f
            textPaint.color = if (affordable) 0xFFFFD54F.toInt() else 0xFFEF5350.toInt()
            canvas.drawText("$${type.cost}", rect.centerX(), rect.top + 198f, textPaint)
        }
    }

    private fun drawTowerPanel(canvas: Canvas, tower: Tower) {
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 40f
        textPaint.color = Color.WHITE
        canvas.drawText("${tower.type.label}  •  Level ${tower.level}", 40f, panelTop + 45f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 30f
        textPaint.color = 0xFF90A4AE.toInt()
        canvas.drawText("DMG ${tower.damage.roundToInt()}   RNG ${tower.range.roundToInt()}", VIRTUAL_W - 40f, panelTop + 45f, textPaint)

        val upgradeLabel = if (tower.isMaxLevel) "MAX LEVEL" else "UPGRADE  $${tower.upgradeCost}"
        val canUpgrade = !tower.isMaxLevel && money >= tower.upgradeCost
        drawButton(canvas, upgradeRect, upgradeLabel, if (canUpgrade) 0xFF2E7D32.toInt() else 0xFF37474F.toInt(), 40f)
        drawButton(canvas, sellRect, "SELL  $${tower.sellValue}", 0xFF8D6E63.toInt(), 40f)
    }

    private fun drawDefaultPanel(canvas: Canvas) {
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

    private fun drawOverlayBackground(canvas: Canvas) {
        fillPaint.color = 0xCC000000.toInt()
        canvas.drawRect(0f, 0f, VIRTUAL_W, VIRTUAL_H, fillPaint)
    }

    private fun drawMenuOverlay(canvas: Canvas) {
        drawOverlayBackground(canvas)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 110f
        textPaint.color = 0xFF7CB342.toInt()
        canvas.drawText("ZOMBIE", VIRTUAL_W / 2f, 620f, textPaint)
        canvas.drawText("DEFENSE", VIRTUAL_W / 2f, 750f, textPaint)

        textPaint.textSize = 34f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("The horde is coming. Hold the line for $FINAL_WAVE waves.", VIRTUAL_W / 2f, 900f, textPaint)
        canvas.drawText("Build towers on grass  •  Earn cash for kills", VIRTUAL_W / 2f, 960f, textPaint)
        canvas.drawText("Don't let zombies reach the ☠", VIRTUAL_W / 2f, 1020f, textPaint)

        textPaint.textSize = 54f
        textPaint.color = Color.WHITE
        canvas.drawText("TAP TO START", VIRTUAL_W / 2f, 1250f, textPaint)
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
        canvas.drawText("You survived ${waves.wave} wave${if (waves.wave == 1) "" else "s"}", VIRTUAL_W / 2f, 920f, textPaint)
        textPaint.textSize = 50f
        textPaint.color = Color.WHITE
        canvas.drawText("TAP TO RETRY", VIRTUAL_W / 2f, 1150f, textPaint)
    }

    private fun drawVictoryOverlay(canvas: Canvas) {
        drawOverlayBackground(canvas)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 96f
        textPaint.color = 0xFFFFD54F.toInt()
        canvas.drawText("CITY SAVED!", VIRTUAL_W / 2f, 700f, textPaint)
        textPaint.textSize = 38f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("You held the line through all $FINAL_WAVE waves.", VIRTUAL_W / 2f, 830f, textPaint)

        drawButton(canvas, continueRect, "KEEP PLAYING (ENDLESS)", 0xFF2E7D32.toInt(), 44f)
        drawButton(canvas, restartRect, "PLAY AGAIN", 0xFF455A64.toInt(), 44f)
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
