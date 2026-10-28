package com.venice.bsaim

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var status: TextView
    private val SZ = 1001
    private val MP = 1002

    private val permListener = Shizuku.OnRequestPermissionResultListener { code, res ->
        if (code == SZ) {
            if (res == PackageManager.PERMISSION_GRANTED) {
                TouchInjector.bind()
                refresh()
            } else status.text = "اجازه Shizuku داده نشد"
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refresh() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Prefs.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 90, 48, 48)
        }

        val title = TextView(this).apply {
            text = "BS Aim"
            textSize = 26f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 13f
            setPadding(0, 30, 0, 30)
        }

        fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { onClick() }
        }

        root.addView(title)
        root.addView(status)
        root.addView(btn("۱. اجازهی نمایش روی برنامهها") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(btn("۲. نصب / استارت Shizuku") {
            val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (i != null) startActivity(i) else startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
            )
        })
        root.addView(btn("۳. اجازهی Shizuku به این اپ") { askShizuku() })
        root.addView(btn("۴. شروع (با اجازهی ضبط صفحه)") { askProjection() })
        root.addView(btn("خاموش کردن سرویس") {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "سرویس خاموش شد", Toast.LENGTH_SHORT).show()
        })

        setContentView(root)

        try {
            Shizuku.addRequestPermissionResultListener(permListener)
            Shizuku.addBinderReceivedListenerSticky(binderListener)
        } catch (_: Throwable) {}
    }

    private fun askShizuku() {
        try {
            if (Shizuku.isPreV11()) { status.text = "نسخهی Shizuku قدیمی است"; return }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                TouchInjector.bind(); refresh()
            } else Shizuku.requestPermission(SZ)
        } catch (t: Throwable) {
            status.text = "Shizuku اجرا نیست. اول خودش را استارت کن."
        }
    }

    private fun askProjection() {
        if (!Settings.canDrawOverlays(this)) { status.text = "اول اجازهی نمایش روی برنامهها را بده"; return }
        if (!TouchInjector.ready) { status.text = "اول اجازهی Shizuku را بده"; return }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), MP)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == MP && res == RESULT_OK && data != null) {
            val i = Intent(this, OverlayService::class.java)
                .putExtra("code", res)
                .putExtra("data", data)
            startForegroundService(i)
            Toast.makeText(this, "سرویس روشن شد. حالا برو داخل بازی.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
    }

    private fun refresh() {
        status.text = when {
            !Settings.canDrawOverlays(this) -> "وضعیت: اوورلی ❌   Shizuku: ${shizukuText()}"
            else -> "وضعیت: اوورلی ✅   Shizuku: ${shizukuText()}"
        }
    }

    private fun shizukuText(): String = try {
        if (Shizuku.isPreV11()) "قدیمی"
        else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
            if (TouchInjector.ready || TouchInjector.bind()) "آماده ✅" else "وصل نشد ❌"
        else "اجازه نداده ❌"
    } catch (t: Throwable) { "اجرا نیست ❌" }

    override fun onResume() { super.onResume(); refresh() }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(permListener)
            Shizuku.removeBinderReceivedListener(binderListener)
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
