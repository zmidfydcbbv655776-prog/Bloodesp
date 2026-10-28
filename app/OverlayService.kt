package com.venice.bsaim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var menu: LinearLayout? = null
    private var dbg: DebugView? = null
    private var engine: AimEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        channel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()

        if (engine == null) {
            val code = intent?.getIntExtra("code", 0) ?: 0
            val data = intent?.getParcelableExtra<Intent>("data")
            if (data != null) {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mp: MediaProjection = mpm.getMediaProjection(code, data)
                engine = AimEngine(this) { a, x, y, f ->
                    dbg?.postInvalidate()
                    dbg?.setState(a, x, y, f)
                }.also { it.start(mp) }
            }
            addDebug()
            addBubble()
        }
        return START_NOT_STICKY
    }

    private fun startFg() {
        val n = Notification.Builder(this, CH)
            .setContentTitle("BS Aim")
            .setContentText("aimbot فعال است")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(7, n)
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH, "BS Aim", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    // ---------- windows ----------

    private fun lp(w: Int, h: Int, flags: Int, gx: Int = Gravity.TOP or Gravity.START): WindowManager.LayoutParams {
        val t = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(w, h, t, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = gx
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun addDebug() {
        val v = DebugView(this)
        dbg = v
        wm.addView(
            v,
            lp(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        )
    }

    private fun addBubble() {
        val tv = TextView(this).apply {
            text = "MOD"
            setTextColor(Color.parseColor("#04120C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E5A0"))
            }
        }
        var dx = 0f; var dy = 0f
        var sx = 0; var sy = 0
        var moved = false
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX; dy = e.rawY
                    val p = tv.layoutParams as WindowManager.LayoutParams
                    sx = p.x; sy = p.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val p = tv.layoutParams as WindowManager.LayoutParams
                    p.x = sx + (e.rawX - dx).toInt()
                    p.y = sy + (e.rawY - dy).toInt()
                    if (Math.abs(e.rawX - dx) > 12 || Math.abs(e.rawY - dy) > 12) moved = true
                    wm.updateViewLayout(tv, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleMenu(tv)
                    true
                }
                else -> false
            }
        }
        bubble = tv
        val p = lp(dp(52), dp(52), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        p.x = dp(8); p.y = dp(140)
        wm.addView(tv, p)
    }

    private fun toggleMenu(anchor: View) {
        menu?.let { wm.removeView(it); menu = null; return }
        val m = buildMenu()
        val p = lp(dp(250), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        p.x = dp(8); p.y = dp(200)
        menu = m
        wm.addView(m, p)
    }

    // ---------- menu ----------

    private fun buildMenu(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#E00A0F16"))
                setStroke(dp(1), Color.parseColor("#4400E5A0"))
            }
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }

        root.addView(label("BLOOD STRIKE AIM", "#00E5A0", 12f, true))

        root.addView(swRow("Aimbot", Prefs.aimEnabled) { Prefs.aimEnabled = it; Prefs.save() })
        root.addView(swRow("Debug view", Prefs.showDebug) { Prefs.showDebug = it; Prefs.save(); dbg?.invalidate() })

        root.addView(slider("Aim speed", 1, 100, Prefs.aimSpeed.toInt(), { Prefs.aimSpeed = it.toFloat() }))
        root.addView(slider("Smoothing", 0, 100, Prefs.smoothing.toInt(), { Prefs.smoothing = it.toFloat() }))
        root.addView(slider("FOV", 60, 500, Prefs.fov.toInt(), { Prefs.fov = it.toFloat(); dbg?.invalidate() }))
        root.addView(slider("Sticky", 0, 100, Prefs.sticky.toInt(), { Prefs.sticky = it.toFloat() }))
        root.addView(slider("Aim offset Y", -20, 80, Prefs.aimOffsetY.toInt(), { Prefs.aimOffsetY = it.toFloat() }))
        root.addView(slider("Red threshold", 60, 190, Prefs.redThreshold.toInt(), { Prefs.redThreshold = it.toFloat() }))
        root.addView(slider("Skip pixels", 1, 6, Prefs.stride, { Prefs.stride = it }))

        root.addView(swRow("Invert X", Prefs.invertX) { Prefs.invertX = it; Prefs.save() })
        root.addView(swRow("Invert Y", Prefs.invertY) { Prefs.invertY = it; Prefs.save() })

        val close = label("بستن منو", "#8AFFD4", 11f, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            setOnClickListener { menu?.let { wm.removeView(it) }; menu = null }
        }
        root.addView(close)
        return root
    }

    private fun label(t: String, color: String, size: Float, bold: Boolean): TextView =
        TextView(this).apply {
            text = t
            setTextColor(Color.parseColor(color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun swRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val t = label(title, "#CFD8E3", 11f, false)
        val s = Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(s)
        return row
    }

    private fun slider(title: String, min: Int, max: Int, initial: Int, onChange: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val t = label("$title  $initial", "#9AA7B6", 10.5f, false)
        val sb = SeekBar(this).apply {
            this.max = max - min
            progress = (initial - min).coerceIn(0, max - min)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + min
                    t.text = "$title  $v"
                    onChange(v)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { Prefs.save() }
            })
        }
        box.addView(t)
        box.addView(sb)
        return box
    }

    // ---------- debug canvas ----------

    inner class DebugView(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var active = false
        private var tx = 0f
        private var ty = 0f
        private var fps = 0

        fun setState(a: Boolean, x: Float, y: Float, f: Int) {
            active = a; tx = x; ty = y; fps = f
        }

        override fun onDraw(canvas: Canvas) {
            if (!Prefs.showDebug) return
            val cx = width / 2f
            val cy = height / 2f

            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = Color.parseColor("#8800E5A0")
            canvas.drawCircle(cx, cy, Prefs.fov, p)

            p.color = Color.parseColor("#CC00E5A0")
            canvas.drawLine(cx - 12, cy, cx + 12, cy, p)
            canvas.drawLine(cx, cy - 12, cx, cy + 12, p)

            if (active) {
                p.color = Color.parseColor("#FFFF4D6A")
                canvas.drawCircle(tx, ty, 14f, p)
                canvas.drawLine(cx, cy, tx, ty, p)
            }

            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#9900E5A0")
            p.textSize = 28f
            canvas.drawText("fps $fps", 24f, 60f, p)
        }
    }

    override fun onDestroy() {
        engine?.stop()
        bubble?.let { runCatching { wm.removeView(it) } }
        menu?.let { runCatching { wm.removeView(it) } }
        dbg?.let { runCatching { wm.removeView(it) } }
        TouchInjector.unbind()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object { const val CH = "bsaim" }
}
