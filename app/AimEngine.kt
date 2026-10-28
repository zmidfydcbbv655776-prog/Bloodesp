package com.venice.bsaim

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class AimEngine(
    private val ctx: Context,
    private val onState: (active: Boolean, tx: Float, ty: Float, fps: Int) -> Unit
) {
    var screenW = 0; private set
    var screenH = 0; private set

    private var scale = 2
    private var capW = 0
    private var capH = 0

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null

    private val thread = HandlerThread("bsaim").apply { start() }
    private val handler = Handler(thread.looper)

    private var lastCX = -1
    private var lastCY = -1
    private var down = false
    private var ptrX = 0f
    private var ptrY = 0f
    private var anchorX = 0f
    private var anchorY = 0f
    private var fps = 0
    private var fpsN = 0
    private var fpsT = 0L

    @Volatile private var running = false

    fun start(p: MediaProjection) {
        projection = p
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        screenW = m.widthPixels
        screenH = m.heightPixels
        scale = if (max(screenW, screenH) > 1600) 3 else 2
        capW = screenW / scale
        capH = screenH / scale

        reader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 3)
        display = projection!!.createVirtualDisplay(
            "bsaim", capW, capH, m.densityDpi / scale,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )
        running = true
        handler.post(frame)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        TouchInjector.up()
        down = false
        try { display?.release() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        display = null; reader = null; projection = null
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            try { tick() } catch (_: Throwable) {}
            handler.postDelayed(this, 6)
        }
    }

    private fun tick() {
        val r = reader ?: return
        val img: Image = r.acquireLatestImage() ?: return
        try {
            if (Prefs.aimEnabled && TouchInjector.ready) {
                val hit = detect(img)
                if (hit != null) {
                    aim(hit[0], hit[1])
                    countFps()
                    onState(true, hit[0], hit[1], fps)
                } else {
                    releasePointer()
                    countFps()
                    onState(false, 0f, 0f, fps)
                }
            } else {
                releasePointer()
                onState(false, 0f, 0f, fps)
            }
        } finally {
            img.close()
        }
    }

    private fun countFps() {
        fpsN++
        val now = SystemClock.uptimeMillis()
        if (fpsT == 0L) fpsT = now
        if (now - fpsT >= 1000) {
            fps = fpsN; fpsN = 0; fpsT = now
        }
    }

    /** returns [aimX, aimY] in screen coords, or null */
    private fun detect(img: Image): FloatArray? {
        val plane = img.planes[0]
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixStride = plane.pixelStride

        val cx = capW / 2
        val cy = capH / 2
        val fovCap = (Prefs.fov / scale).toInt()
        val fovSq = fovCap * fovCap
        val thr = Prefs.redThreshold.toInt()
        val stride = Prefs.stride.coerceIn(1, 8)
        val cell = 12
        val cols = capW / cell + 1
        val rows = capH / cell + 1
        val counts = IntArray(cols * rows)

        var y = 0
        while (y < capH) {
            var x = 0
            val rowBase = y * rowStride
            while (x < capW) {
                val i = rowBase + x * pixStride
                if (i + 2 < buf.capacity()) {
                    val r = buf.get(i).toInt() and 0xFF
                    val g = buf.get(i + 1).toInt() and 0xFF
                    val b = buf.get(i + 2).toInt() and 0xFF
                    if (r > thr && r - g > 38 && r - b > 30) {
                        val dx = x - cx
                        val dy = y - cy
                        if (dx * dx + dy * dy <= fovSq) {
                            counts[(y / cell) * cols + (x / cell)]++
                        }
                    }
                }
                x += stride
            }
            y += stride
        }

        var best = -1
        var bestScore = 0f
        for (cyi in 0 until rows) {
            for (cxi in 0 until cols) {
                val c = counts[cyi * cols + cxi]
                if (c < 3) continue
                val px = (cxi * cell + cell / 2) - cx
                val py = (cyi * cell + cell / 2) - cy
                val dist = hypot(px.toFloat(), py.toFloat())
                var score = c * 1.0f - dist * 0.06f
                if (Prefs.sticky > 0f && abs(cxi - lastCX) <= 1 && abs(cyi - lastCY) <= 1) {
                    score += Prefs.sticky * 0.12f
                }
                if (score > bestScore) { bestScore = score; best = cyi * cols + cxi }
            }
        }
        if (best < 0) return null

        val bx = best % cols
        val by = best / cols
        lastCX = bx; lastCY = by

        // refine: average red pixels in 3x3 cell block
        var sx = 0L; var sy = 0L; var n = 0L
        for (yy in (by - 1) * cell..min(capH - 1, (by + 2) * cell)) {
            if (yy < 0) continue
            val rowBase = yy * rowStride
            for (xx in (bx - 1) * cell..min(capW - 1, (bx + 2) * cell)) {
                if (xx < 0) continue
                val i = rowBase + xx * pixStride
                if (i + 2 >= buf.capacity()) continue
                val r = buf.get(i).toInt() and 0xFF
                val g = buf.get(i + 1).toInt() and 0xFF
                val b = buf.get(i + 2).toInt() and 0xFF
                if (r > thr && r - g > 38 && r - b > 30) { sx += xx; sy += yy; n++ }
            }
        }
        if (n == 0L) return null

        val ax = (sx.toFloat() / n) * scale
        val ay = (sy.toFloat() / n) * scale + Prefs.aimOffsetY
        return floatArrayOf(ax, ay)
    }

    private fun aim(tx: Float, ty: Float) {
        val ex = tx - screenW / 2f
        val ey = ty - screenH / 2f
        if (abs(ex) < Prefs.deadzone && abs(ey) < Prefs.deadzone) return

        // exponential approach: speed up, smoothing down
        val k = (Prefs.aimSpeed / 100f) * 0.45f / (1f + Prefs.smoothing / 35f)
        var sx = ex * k
        var sy = ey * k
        val maxStep = 55f
        sx = sx.coerceIn(-maxStep, maxStep)
        sy = sy.coerceIn(-maxStep, maxStep)
        if (Prefs.invertX) sx = -sx
        if (Prefs.invertY) sy = -sy

        if (!down) {
            anchorX = screenW * 0.45f
            anchorY = screenH * Prefs.anchorYFrac
            ptrX = anchorX; ptrY = anchorY
            TouchInjector.down(ptrX, ptrY)
            down = true
        } else {
            ptrX += sx; ptrY += sy
            TouchInjector.moveTracked(ptrX, ptrY)
        }

        if (hypot(ptrX - anchorX, ptrY - anchorY) > screenH * 0.30f) {
            TouchInjector.up()
            down = false
        }
    }

    private fun releasePointer() {
        if (down) { TouchInjector.up(); down = false }
        lastCX = -1; lastCY = -1
    }
}
