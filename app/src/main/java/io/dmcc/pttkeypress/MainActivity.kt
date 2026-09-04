package io.dmcc.pttkeypress

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.dmcc.pttkeypress.ble.PttDeviceState
import io.dmcc.pttkeypress.data.PttButton
import io.dmcc.pttkeypress.output.VoxDmrBridge
import io.dmcc.pttkeypress.service.PttForegroundService
import io.dmcc.pttkeypress.ui.PttKeypressTheme

class MainActivity : ComponentActivity() {
    private var permissionsGranted by mutableStateOf(false)
    private val app get() = application as PttKeypressApp

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsGranted = hasBlePermissions()
            if (permissionsGranted) startPttService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionsGranted = hasBlePermissions()
        if (permissionsGranted) startPttService()

        setContent {
            PttKeypressTheme {
                PttKeypressScreen(
                    app = app,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions()) },
                    onOpenVoxDmr = { openVoxDmr() },
                    onServiceNeeded = { startPttService() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionsGranted = hasBlePermissions()
        app.voxDmrBridge.refresh()
        if (permissionsGranted) {
            startPttService()
            app.bleManager.armAll()
        }
    }

    private fun openVoxDmr() {
        val launch = packageManager.getLaunchIntentForPackage(VoxDmrBridge.VOXDMR_PACKAGE)
        if (launch != null) {
            startActivity(launch)
        } else {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + VoxDmrBridge.VOXDMR_PACKAGE),
                )
            )
        }
    }

    private fun startPttService() {
        runCatching { PttForegroundService.start(this) }
    }

    private fun hasBlePermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}

@Composable
private fun PttKeypressScreen(
    app: PttKeypressApp,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenVoxDmr: () -> Unit,
    onServiceNeeded: () -> Unit,
) {
    val buttons by app.repository.buttons.collectAsStateWithLifecycle()
    val states by app.bleManager.states.collectAsStateWithLifecycle()
    val scanResults by app.bleManager.scanResults.collectAsStateWithLifecycle()
    val voxDmrInstalled by app.voxDmrBridge.installed.collectAsStateWithLifecycle()
    var showPairing by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (permissionsGranted) {
                ExtendedFloatingActionButton(
                    onClick = {
                        showPairing = true
                        app.bleManager.startPairingScan()
                    },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("Pair PTT") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text("PTT Keypress", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Bluetooth PTT bridge for VoxDMR.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!permissionsGranted) {
                item {
                    SetupCard(
                        icon = { Icon(Icons.Outlined.Bluetooth, null) },
                        title = "Bluetooth access",
                        body = "Allow nearby-device access so the bridge can find and reconnect to your PTT button.",
                        button = "Allow access",
                        onClick = onRequestPermissions,
                    )
                }
            }

            item {
                SetupCard(
                    icon = { Icon(Icons.Outlined.Radio, null) },
                    title = if (voxDmrInstalled) "VoxDMR ready" else "Install VoxDMR",
                    body = if (voxDmrInstalled)
                        "Ready to send PTT presses."
                    else
                        "Install VoxDMR to use your paired PTT button.",
                    button = if (voxDmrInstalled) "Open VoxDMR" else "Get VoxDMR",
                    onClick = onOpenVoxDmr,
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Paired buttons", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(buttons.size.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (buttons.isEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(18.dp)) {
                            Icon(Icons.Outlined.Bluetooth, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("No PTT buttons yet", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap Pair PTT, then press and hold your PTT button until it appears.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(buttons, key = { it.address }) { button ->
                    ButtonCard(
                        button = button,
                        state = states[button.address] ?: PttDeviceState.Waiting,
                        onForget = { app.bleManager.forget(button.address) },
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showPairing) {
        AlertDialog(
            onDismissRequest = {
                showPairing = false
                app.bleManager.stopPairingScan()
            },
            title = { Text("Pair a PTT button") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Press and hold your PTT button to wake it. Keep holding until it appears below.")
                    if (scanResults.isEmpty()) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Searching for an awake PTT button…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        scanResults.forEach { device ->
                            Card(onClick = {
                                app.bleManager.pair(device)
                                onServiceNeeded()
                                showPairing = false
                            }) {
                                Row(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Bluetooth, null)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(device.name, fontWeight = FontWeight.SemiBold)
                                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(device.rssi.toString() + " dBm", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showPairing = false
                    app.bleManager.stopPairingScan()
                }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SetupCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    button: String?,
    onClick: () -> Unit,
) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.padding(10.dp)) { icon() }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (button != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(button) }
                }
            }
        }
    }
}

@Composable
private fun ButtonCard(
    button: PttButton,
    state: PttDeviceState,
    onForget: () -> Unit,
) {
    val status = when (state) {
        PttDeviceState.Waiting -> "Ready — press PTT"
        PttDeviceState.Connecting -> "Armed for next press"
        PttDeviceState.Connected -> "Awake"
        PttDeviceState.Pressed -> "PTT held → VoxDMR"
        PttDeviceState.Error -> "Connection error"
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(button.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        status,
                        color = if (state == PttDeviceState.Pressed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onForget) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Forget button")
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Target: VoxDMR",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "The button sleeps when released. Ready means Android is armed for its next wake-up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
