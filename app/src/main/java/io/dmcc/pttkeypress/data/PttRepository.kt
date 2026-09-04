package io.dmcc.pttkeypress.data

import android.content.Context
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class PttButton(
    val address: String,
    val name: String,
    val keyCode: Int = KeyEvent.KEYCODE_SHIFT_LEFT,
    val keyLabel: String = "Left Shift",
)

data class KeyPreset(val label: String, val keyCode: Int, val recommended: Boolean = false)

val keyPresets = listOf(
    KeyPreset("Left Shift", KeyEvent.KEYCODE_SHIFT_LEFT, true),
    KeyPreset("Right Shift", KeyEvent.KEYCODE_SHIFT_RIGHT),
    KeyPreset("Left Ctrl", KeyEvent.KEYCODE_CTRL_LEFT),
    KeyPreset("Left Alt", KeyEvent.KEYCODE_ALT_LEFT),
    KeyPreset("F1", KeyEvent.KEYCODE_F1),
    KeyPreset("F2", KeyEvent.KEYCODE_F2),
    KeyPreset("F3", KeyEvent.KEYCODE_F3),
    KeyPreset("F4", KeyEvent.KEYCODE_F4),
    KeyPreset("Play / Pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
)

class PttRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ptt_keypress", Context.MODE_PRIVATE)

    private val _buttons = MutableStateFlow(load())
    val buttons: StateFlow<List<PttButton>> = _buttons.asStateFlow()

    fun add(address: String, name: String) {
        if (_buttons.value.any { it.address == address }) return
        save(_buttons.value + PttButton(address = address, name = name))
    }

    fun remove(address: String) {
        save(_buttons.value.filterNot { it.address == address })
    }

    fun mapKey(address: String, preset: KeyPreset) {
        save(_buttons.value.map {
            if (it.address == address) it.copy(keyCode = preset.keyCode, keyLabel = preset.label) else it
        })
    }

    fun find(address: String): PttButton? = _buttons.value.firstOrNull { it.address == address }

    private fun save(buttons: List<PttButton>) {
        val arr = JSONArray()
        buttons.forEach {
            arr.put(JSONObject().apply {
                put("address", it.address)
                put("name", it.name)
                put("keyCode", it.keyCode)
                put("keyLabel", it.keyLabel)
            })
        }
        prefs.edit().putString("buttons", arr.toString()).apply()
        _buttons.value = buttons
    }

    private fun load(): List<PttButton> {
        return runCatching {
            val raw = prefs.getString("buttons", "[]") ?: "[]"
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PttButton(
                            address = o.getString("address"),
                            name = o.optString("name", "PTT button"),
                            keyCode = o.optInt("keyCode", KeyEvent.KEYCODE_SHIFT_LEFT),
                            keyLabel = o.optString("keyLabel", "Left Shift"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
