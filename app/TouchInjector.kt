package com.venice.bsaim

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

object TouchInjector {

    private var inputManager: Any? = null
    private var inject: Method? = null

    @Volatile var ready = false
        private set

    private var downTime = 0L
    private var isDown = false

    fun bind(): Boolean {
        return try {
            val binder: IBinder = SystemServiceHelper.getSystemService("input")
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            inputManager = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            val iface = Class.forName("android.hardware.input.IInputManager")
            inject = iface.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            ready = true
            true
        } catch (t: Throwable) {
            ready = false
            false
        }
    }

    fun unbind() {
        up()
        ready = false
        inject = null
        inputManager = null
    }

    private fun send(action: Int, x: Float, y: Float) {
        val m = inject ?: return
        val im = inputManager ?: return
        try {
            val now = SystemClock.uptimeMillis()
            val e = MotionEvent.obtain(downTime, now, action, x, y, 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            m.invoke(im, e, 0) // INJECT_INPUT_EVENT_MODE_ASYNC
            e.recycle()
        } catch (_: Throwable) {
        }
    }

    fun down(x: Float, y: Float) {
        if (isDown) return
        downTime = SystemClock.uptimeMillis()
        isDown = true
        send(MotionEvent.ACTION_DOWN, x, y)
    }

    fun move(x: Float, y: Float) {
        if (!isDown) return
        send(MotionEvent.ACTION_MOVE, x, y)
    }

    fun up() {
        if (!isDown) return
        send(MotionEvent.ACTION_UP, lastX, lastY)
        isDown = false
    }

    var lastX = 0f
        private set
    var lastY = 0f
        private set

    fun moveTracked(x: Float, y: Float) {
        lastX = x; lastY = y
        move(x, y)
    }
}
