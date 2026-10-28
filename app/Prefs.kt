package com.venice.bsaim

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private var sp: SharedPreferences? = null

    @Volatile var aimEnabled = false
    @Volatile var aimSpeed = 55f      // 1..100
    @Volatile var smoothing = 45f     // 0..100
    @Volatile var fov = 220f          // شعاع دایره، پیکسل
    @Volatile var sticky = 30f        // 0..100
    @Volatile var deadzone = 3f       // پیکسل
    @Volatile var aimOffsetY = 16f    // پیکسل، پایین‌تر از تگ
    @Volatile var redThreshold = 105  // 60..180
    @Volatile var stride = 3          // 2..6
    @Volatile var invertX = false
    @Volatile var invertY = false
    @Volatile var anchorYFrac = 0.55f
    @Volatile var showDebug = true

    fun init(c: Context) {
        val p = c.getSharedPreferences("bsaim", Context.MODE_PRIVATE)
        sp = p
        aimEnabled = p.getBoolean("aimEnabled", false)
        aimSpeed = p.getFloat("aimSpeed", 55f)
        smoothing = p.getFloat("smoothing", 45f)
        fov = p.getFloat("fov", 220f)
        sticky = p.getFloat("sticky", 30f)
        deadzone = p.getFloat("deadzone", 3f)
        aimOffsetY = p.getFloat("aimOffsetY", 16f)
        redThreshold = p.getInt("redThreshold", 105).toFloat()
        stride = p.getInt("stride", 3)
        invertX = p.getBoolean("invertX", false)
        invertY = p.getBoolean("invertY", false)
        anchorYFrac = p.getFloat("anchorYFrac", 0.55f)
        showDebug = p.getBoolean("showDebug", true)
    }

    fun save() {
        val p = sp ?: return
        p.edit()
            .putBoolean("aimEnabled", aimEnabled)
            .putFloat("aimSpeed", aimSpeed)
            .putFloat("smoothing", smoothing)
            .putFloat("fov", fov)
            .putFloat("sticky", sticky)
            .putFloat("deadzone", deadzone)
            .putFloat("aimOffsetY", aimOffsetY)
            .putInt("redThreshold", redThreshold.toInt())
            .putInt("stride", stride)
            .putBoolean("invertX", invertX)
            .putBoolean("invertY", invertY)
            .putFloat("anchorYFrac", anchorYFrac)
            .putBoolean("showDebug", showDebug)
            .apply()
    }
}
