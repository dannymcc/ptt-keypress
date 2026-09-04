package io.dmcc.pttkeypress.inject

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.annotation.Keep

class KeyInjectorUserService @Keep constructor(context: Context) : IKeyInjector.Stub() {

    override fun injectKey(keyCode: Int, down: Boolean) {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(
            now,
            now,
            if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            keyCode,
            0,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            KeyEvent.FLAG_FROM_SYSTEM,
            InputDevice.SOURCE_KEYBOARD,
        )

        val clazz = Class.forName("android.hardware.input.InputManager")
        val instance = clazz.getDeclaredMethod("getInstance").apply { isAccessible = true }.invoke(null)
        val method = clazz.getDeclaredMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        method.invoke(instance, event, 0)
    }

    fun destroy() {
        System.exit(0)
    }
}
