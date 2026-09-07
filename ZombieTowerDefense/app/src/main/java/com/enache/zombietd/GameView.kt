package com.enache.zombietd

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.enache.zombietd.game.Game
import com.enache.zombietd.game.Missions
import com.enache.zombietd.game.Progress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

/** The installed app's versionName, shown on the home screen. */
private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (_: Exception) {
    ""
}

/** Campaign progress saved on the device so unlocked missions survive restarts. */
private class PrefsProgress(context: Context) : Progress {
    private val prefs = context.getSharedPreferences("zombietd", Context.MODE_PRIVATE)
    override fun unlockedMissions() = prefs.getInt("unlocked", 1).coerceIn(1, Missions.all.size)
    override fun setUnlockedMissions(count: Int) {
        prefs.edit().putInt("unlocked", count.coerceIn(1, Missions.all.size)).apply()
    }
}

/**
 * Hosts the game loop thread. The game simulates and draws on a fixed
 * 1080x1920 virtual canvas which is scaled (letterboxed) to the real screen.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val game = Game(PrefsProgress(context), appVersion(context))
    private val taps = ConcurrentLinkedQueue<PointF>()

    @Volatile
    private var running = false
    private var thread: Thread? = null

    private var scale = 0f
    private var offX = 0f
    private var offY = 0f

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    fun onAppPause() {
        game.onAppPause()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(::gameLoop, "GameLoop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Fill the whole screen: fix the virtual width and let the virtual
        // height follow the device aspect ratio. Only very squat screens
        // (shorter than the minimum layout) fall back to letterboxing.
        val fillScale = width / Game.VIRTUAL_W
        val vh = height / fillScale
        if (vh >= Game.MIN_VIRTUAL_H) {
            scale = fillScale
            offX = 0f
            offY = 0f
            game.setVirtualHeight(vh)
        } else {
            scale = min(width / Game.VIRTUAL_W, height / Game.MIN_VIRTUAL_H)
            offX = (width - Game.VIRTUAL_W * scale) / 2f
            offY = (height - Game.MIN_VIRTUAL_H * scale) / 2f
            game.setVirtualHeight(Game.MIN_VIRTUAL_H)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try {
            thread?.join(1000)
        } catch (_: InterruptedException) {
        }
        thread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (scale > 0f) {
                    taps.add(PointF((event.x - offX) / scale, (event.y - offY) / scale))
                }
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun gameLoop() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1_000_000_000f
            last = now
            if (dt > 0.05f) dt = 0.05f

            while (true) {
                val tap = taps.poll() ?: break
                game.onTap(tap.x, tap.y)
            }
            game.update(dt)

            val canvas = holder.lockCanvas() ?: continue
            try {
                canvas.drawColor(Color.BLACK)
                canvas.save()
                canvas.translate(offX, offY)
                canvas.scale(scale, scale)
                canvas.clipRect(0f, 0f, Game.VIRTUAL_W, game.virtualH)
                game.draw(canvas)
                canvas.restore()
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }

            val frameMs = (System.nanoTime() - now) / 1_000_000
            if (frameMs < 16) {
                try {
                    Thread.sleep(16 - frameMs)
                } catch (_: InterruptedException) {
                }
            }
        }
    }
}
