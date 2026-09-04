package io.dmcc.pttkeypress.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import io.dmcc.pttkeypress.data.PttRepository
import io.dmcc.pttkeypress.inject.ShizukuKeyInjector
import java.util.UUID

data class ScanDevice(val address: String, val name: String, val rssi: Int)

enum class PttDeviceState {
    Waiting,
    Connecting,
    Connected,
    Pressed,
    Error,
}

class PttBleManager(
    private val context: Context,
    private val repository: PttRepository,
    private val injector: ShizukuKeyInjector,
) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter
    private val handler = Handler(Looper.getMainLooper())

    private val _scanResults = MutableStateFlow<List<ScanDevice>>(emptyList())
    val scanResults: StateFlow<List<ScanDevice>> = _scanResults.asStateFlow()

    private val _states = MutableStateFlow<Map<String, PttDeviceState>>(emptyMap())
    val states: StateFlow<Map<String, PttDeviceState>> = _states.asStateFlow()

    private val connections = mutableMapOf<String, BluetoothGatt>()
    private val pressed = mutableSetOf<String>()
    private var scanCallback: ScanCallback? = null
    private var scanStartedNs = 0L

    @SuppressLint("MissingPermission")
    fun armAll() {
        repository.buttons.value.forEach { button ->
            if (!connections.containsKey(button.address)) {
                connect(button.address, autoConnect = true)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun pair(device: ScanDevice) {
        repository.add(device.address, device.name)
        stopPairingScan()
        disconnectInternal(device.address, rearm = false)
        connect(device.address, autoConnect = false)
    }

    fun forget(address: String) {
        releaseIfNeeded(address)
        disconnectInternal(address, rearm = false)
        repository.remove(address)
        _states.update { it - address }
    }

    @SuppressLint("MissingPermission")
    fun startPairingScan() {
        stopPairingScan()
        _scanResults.value = emptyList()
        val scanner = adapter?.bluetoothLeScanner ?: return
        scanStartedNs = SystemClock.elapsedRealtimeNanos()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = handleScan(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handleScan)
        }
        scanCallback = cb
        scanner.startScan(null, settings, cb)
    }

    @SuppressLint("MissingPermission")
    private fun handleScan(result: ScanResult) {
        if (result.timestampNanos < scanStartedNs) return
        val name = result.scanRecord?.deviceName
            ?: runCatching { result.device.name }.getOrNull()
            ?: "PTT button"
        val serviceMatch = result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_PTT } == true
        val nameMatch = name.startsWith("PTT", ignoreCase = true)
        if (!serviceMatch && !nameMatch) return
        if (repository.find(result.device.address) != null) return

        _scanResults.update { current ->
            val next = ScanDevice(result.device.address, name, result.rssi)
            current.filterNot { it.address == next.address } + next
        }
    }

    @SuppressLint("MissingPermission")
    fun stopPairingScan() {
        scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
        scanStartedNs = 0
        _scanResults.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun connect(address: String, autoConnect: Boolean) {
        if (repository.find(address) == null) return
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        _states.update { it + (address to PttDeviceState.Connecting) }

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connections[address] = gatt
                        _states.update { it + (address to PttDeviceState.Connected) }
                        @SuppressLint("MissingPermission")
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        releaseIfNeeded(address)
                        runCatching { gatt.close() }
                        if (connections[address] === gatt) connections.remove(address)
                        _states.update { it + (address to PttDeviceState.Waiting) }

                        // These peripherals sleep as soon as the physical PTT is released.
                        // Re-arm autoConnect so Android is waiting for the next brief advertisement.
                        if (repository.find(address) != null) {
                            handler.postDelayed({
                                if (repository.find(address) != null && !connections.containsKey(address)) {
                                    connect(address, autoConnect = true)
                                }
                            }, 350)
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _states.update { it + (address to PttDeviceState.Error) }
                    return
                }
                val characteristic = gatt.getService(SERVICE_PTT)?.getCharacteristic(CHAR_PTT)
                val cccd = characteristic?.getDescriptor(CCCD)
                if (characteristic == null || cccd == null) {
                    _states.update { it + (address to PttDeviceState.Error) }
                    return
                }

                @SuppressLint("MissingPermission")
                gatt.setCharacteristicNotification(characteristic, true)

                @SuppressLint("MissingPermission")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                handleValue(address, value)
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                handleValue(address, characteristic.value ?: return)
            }
        }

        @SuppressLint("MissingPermission")
        val gatt = device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt != null) connections[address] = gatt
    }

    private fun handleValue(address: String, value: ByteArray) {
        when (value.firstOrNull()) {
            0x01.toByte() -> {
                if (pressed.add(address)) {
                    repository.find(address)?.let { injector.inject(it.keyCode, true) }
                }
                _states.update { it + (address to PttDeviceState.Pressed) }
            }
            0x00.toByte() -> {
                releaseIfNeeded(address)
                _states.update { it + (address to PttDeviceState.Connected) }
            }
        }
    }

    private fun releaseIfNeeded(address: String) {
        if (!pressed.remove(address)) return
        repository.find(address)?.let { injector.inject(it.keyCode, false) }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal(address: String, rearm: Boolean) {
        val gatt = connections.remove(address) ?: return
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
        if (!rearm) _states.update { it + (address to PttDeviceState.Waiting) }
    }

    companion object {
        val SERVICE_PTT: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHAR_PTT: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
