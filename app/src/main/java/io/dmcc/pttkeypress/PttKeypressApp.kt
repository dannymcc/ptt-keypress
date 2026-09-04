package io.dmcc.pttkeypress

import android.app.Application
import io.dmcc.pttkeypress.ble.PttBleManager
import io.dmcc.pttkeypress.data.PttRepository
import io.dmcc.pttkeypress.inject.ShizukuKeyInjector

class PttKeypressApp : Application() {
    val repository by lazy { PttRepository(this) }
    val injector by lazy { ShizukuKeyInjector(this) }
    val bleManager by lazy { PttBleManager(this, repository, injector) }
}
