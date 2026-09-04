package io.dmcc.pttkeypress

import android.app.Application
import io.dmcc.pttkeypress.ble.PttBleManager
import io.dmcc.pttkeypress.data.PttRepository
import io.dmcc.pttkeypress.output.VoxDmrBridge

class PttKeypressApp : Application() {
    val repository by lazy { PttRepository(this) }
    val voxDmrBridge by lazy { VoxDmrBridge(this) }
    val bleManager by lazy { PttBleManager(this, repository, voxDmrBridge) }
}
