package io.dmcc.pttkeypress.output

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoxDmrBridge(private val context: Context) {
    private val _installed = MutableStateFlow(isInstalled())
    val installed: StateFlow<Boolean> = _installed.asStateFlow()

    fun refresh() {
        _installed.value = isInstalled()
    }

    fun pttDown() {
        send(ACTION_PTT_DOWN)
    }

    fun pttUp() {
        send(ACTION_PTT_UP)
    }

    private fun send(action: String) {
        context.sendBroadcast(
            Intent(action).apply {
                setPackage(VOXDMR_PACKAGE)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra("source", "ptt-keypress")
            }
        )
    }

    private fun isInstalled(): Boolean =
        try {
            context.packageManager.getPackageInfo(VOXDMR_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    companion object {
        const val VOXDMR_PACKAGE = "com.jcalado.voxdmr"
        const val ACTION_PTT_DOWN = "android.intent.action.PTT_DOWN"
        const val ACTION_PTT_UP = "android.intent.action.PTT_UP"
    }
}
