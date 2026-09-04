package io.dmcc.pttkeypress.inject

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class InjectorStatus {
    Unavailable,
    PermissionRequired,
    Connecting,
    Ready,
    Error,
}

class ShizukuKeyInjector(private val context: Context) {
    private val _status = MutableStateFlow(InjectorStatus.Unavailable)
    val status: StateFlow<InjectorStatus> = _status.asStateFlow()

    @Volatile
    private var service: IKeyInjector? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IKeyInjector.Stub.asInterface(binder)
            bound = service != null
            _status.value = if (bound) InjectorStatus.Ready else InjectorStatus.Error
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            refresh()
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val deadListener = Shizuku.OnBinderDeadListener {
        service = null
        bound = false
        _status.value = InjectorStatus.Unavailable
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
                bind()
            } else {
                refresh()
            }
        }

    init {
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
    }

    fun refresh() {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            _status.value = InjectorStatus.Unavailable
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            _status.value = InjectorStatus.PermissionRequired
            return
        }
        if (!bound) bind()
    }

    fun requestPermission() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            _status.value = InjectorStatus.Unavailable
            return
        }
        Shizuku.requestPermission(REQUEST_CODE)
    }

    private fun bind() {
        if (bound || _status.value == InjectorStatus.Connecting) return
        _status.value = InjectorStatus.Connecting
        runCatching {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, KeyInjectorUserService::class.java),
            )
                .processNameSuffix("keypress")
                .daemon(false)
                .version(1)
            Shizuku.bindUserService(args, connection)
        }.onFailure {
            _status.value = InjectorStatus.Error
        }
    }

    fun inject(keyCode: Int, down: Boolean): Boolean {
        val target = service ?: return false
        return runCatching {
            target.injectKey(keyCode, down)
            true
        }.getOrElse {
            service = null
            bound = false
            _status.value = InjectorStatus.Error
            false
        }
    }

    companion object {
        private const val REQUEST_CODE = 4182
    }
}
