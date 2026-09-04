package io.dmcc.pttkeypress.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class PttButton(
    val address: String,
    val name: String,
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

    fun find(address: String): PttButton? = _buttons.value.firstOrNull { it.address == address }

    private fun save(buttons: List<PttButton>) {
        val arr = JSONArray()
        buttons.forEach {
            arr.put(JSONObject().apply {
                put("address", it.address)
                put("name", it.name)
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
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
