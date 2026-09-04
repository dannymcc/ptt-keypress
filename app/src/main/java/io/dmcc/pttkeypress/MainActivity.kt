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
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.dmcc.pttkeypress.ble.PttDeviceState
import io.dmcc.pttkeypress.data.KeyPreset
import io.dmcc.pttkeypress.data.PttButton
import io.dmcc.pttkeypress.data.keyPresets
import io.dmcc.pttkeypress.inject.InjectorStatus
import io.dmcc.pttkeypress.service.PttForegroundService
import io.dmcc.pttkeypress.ui.PttKeypressTheme

class MainActivity : ComponentActivity() {
    private var permissionsGranted by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsGranted = hasBlePermissions()
            if (permissionsGranted) startPttService()
        }

    private val app get() = application as PttKeypressApp

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
                    onOpenShizuku = {
                        val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (launch != null) startActivity(launch)
                        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
                    },
                    onServiceNeeded = { startPttService() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionsGranted = hasBlePermissions()
        app.injector.refresh()
        if (permissionsGranted) {
            startPttService()
            app.bleManager.armAll()
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
    onOpenShizuku: () -> Unit,
    onServiceNeeded: () -> Unit,
) {
    val buttons by app.repository.buttons.collectAsStateWithLifecycle()
    val deviceStates by app.bleManager.states.collectAsStateWithLifecycle()
    val injectorStatus by app.injector.status.collectAsStateWithLifecycle()
    val scanResults by app.bleManager.scanResults.collectAsStateWithLifecycle()
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("PTT Keypress", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Turn a sleeping Bluetooth PTT button into a real Android key press.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!permissionsGranted) {
                item {
                    SetupCard(
                        icon = { Icon(Icons.Outlined.Bluetooth, null) },
                        title = "Bluetooth access",
                        body = "Allow nearby-device access so PTT Keypress can find and reconnect to your button.",
                        button = "Allow access",
                        onClick = onRequestPermissions,
                    )
                }
            }

            item {
                val (title, body, action) = when (injectorStatus) {
                    InjectorStatus.Ready -> Triple("Key injection ready", "Shizuku is connected. PTT presses can be sent as real Android key events.", null)
                    InjectorStatus.PermissionRequired -> Triple("Allow Shizuku", "One-time permission is required for global key injection.", "Grant permission")
                    InjectorStatus.Connecting -> Triple("Connecting to Shizuku", "Preparing the key injection service…", null)
                    InjectorStatus.Error -> Triple("Shizuku needs attention", "The key injection service could not start. Re-open Shizuku and try again.", "Try again")
                    InjectorStatus.Unavailable -> Triple("Start Shizuku", "Shizuku must be running for global key injection. Wireless debugging is enough; root is not required.", "Open Shizuku")
                }
                SetupCard(
                    icon = { Icon(Icons.Outlined.Security, null) },
                    title = title,
                    body = body,
                    button = action,
                    onClick = {
                        when (injectorStatus) {
                            InjectorStatus.PermissionRequired -> app.injector.requestPermission()
                            InjectorStatus.Unavailable -> onOpenShizuku()
                            else -> app.injector.refresh()
                        }
                    },
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
                        Column(Modifier.padding(20.dp)) {
                            Icon(Icons.Outlined.Keyboard, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("No PTT buttons yet", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap Pair PTT, then press and hold the physical PTT button so it wakes and starts advertising.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(buttons, key = { it.address }) { button ->
                    ButtonCard(
                        button = button,
                        state = deviceStates[button.address] ?: PttDeviceState.Waiting,
                        onMap = { preset -> app.repository.mapKey(button.address, preset) },
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
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.padding(10.dp)) { icon() }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (button != null) {
                    Spacer(Modifier.height(10.dp))
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
    onMap: (KeyPreset) -> Unit,
    onForget: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val status = when (state) {
        PttDeviceState.Waiting -> "Ready — press PTT"
        PttDeviceState.Connecting -> "Armed for next press"
        PttDeviceState.Connected -> "Awake"
        PttDeviceState.Pressed -> "PTT held"
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

            Spacer(Modifier.height(16.dp))
            Text("Mapped key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                OutlinedButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.Keyboard, null)
                    Spacer(Modifier.width(8.dp))
                    Text(button.keyLabel)
                    if (button.keyLabel == "Left Shift") {
                        Spacer(Modifier.width(8.dp))
                        Text("Recommended", style = MaterialTheme.typography.labelSmall)
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    keyPresets.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(preset.label)
                                    if (preset.recommended) {
                                        Text("Recommended", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            onClick = {
                                onMap(preset)
                                menuOpen = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "This button sleeps when released. “Ready” means Android is armed for its next wake-up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
