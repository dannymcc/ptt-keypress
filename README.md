# PTT Keypress

PTT Keypress bridges supported Bluetooth LE push-to-talk buttons directly into **VoxDMR** on Android.

**Sleeping BLE PTT button → PTT Keypress → VoxDMR**

No root. No Shizuku. No ADB setup after each reboot. No synthetic keyboard event.

## Supported BLE buttons

The initial hardware family is the Zello-style HM-10 / TI CC254x profile already proven in [android-ble-ptt](https://github.com/dannymcc/android-ble-ptt):

- service `FFE0`
- characteristic `FFE1`
- `0x01` = PTT pressed
- `0x00` = PTT released

These buttons sleep when released and only wake/advertise while the physical button is held.

PTT Keypress is designed around that behaviour:

- Pairing tells the user to **press and hold** the button so it wakes.
- Paired buttons are kept armed using Android BLE `autoConnect`.
- A healthy sleeping button is shown as **Ready — press PTT**, not Disconnected.
- A successful wake/subscription is also treated as a press fallback for very short-lived peripherals.
- If the peripheral sleeps before sending its release notification, disconnect produces the matching PTT-up.
- Pairing holds are suppressed so adding a button does not key VoxDMR.
- Multiple paired buttons are supported; overlapping holds keep VoxDMR keyed until the final button is released.

## VoxDMR integration

VoxDMR's Android package is:

`com.jcalado.voxdmr`

PTT Keypress sends VoxDMR its own external PTT broadcasts:

- `com.voxdmr.ptt.DOWN`
- `com.voxdmr.ptt.UP`

These are the actions VoxDMR declares in its manifest for its `PttBroadcastReceiver`, so they are picked up whether or not VoxDMR happens to be in the foreground and without the accessibility service or any key binding. The generic `android.intent.action.PTT_DOWN` / `PTT_UP` actions are only handled by a receiver VoxDMR registers at runtime, so PTT Keypress no longer uses them. No privileged input injection is involved.

Leave VoxDMR's *Talk button* binding as "Not set". PTT Keypress never sends a key event, so there is nothing to bind.

## Reboot behaviour

Once at least one button is paired, PTT Keypress registers for Android boot completion and automatically restarts its connected-device foreground service after reboot, provided the Bluetooth permission previously granted to the app is still present.

The persistent low-priority notification means the bridge is armed in the background.

## APK downloads

GitHub Actions builds the app on every push to `main`.

The workflow publishes:

- `ptt-keypress.apk`
- `ptt-keypress.apk.sha256`

Main builds update the rolling **latest-main** prerelease. Tags matching `v*` create normal GitHub Releases.

## First setup

1. Install VoxDMR.
2. Install PTT Keypress.
3. Grant Bluetooth / nearby-device permission.
4. Tap **Pair PTT**.
5. Press and hold the physical PTT button until it appears.
6. Select it and release the button.
7. Open VoxDMR and connect normally.
8. Press the BLE PTT button — PTT Keypress bridges the hold directly to VoxDMR.

### If VoxDMR does nothing

- Leave VoxDMR's **Talk button** binding as "Not set". PTT Keypress never sends a keyboard key, so there is nothing to bind.
- Restart the phone once. On every handset reported so far a full restart got VoxDMR responding, and it only needs doing the first time.

## Package

`io.dmcc.pttkeypress`

## Current version

`0.2.2`

## Status

Early hardware-test MVP. The Android build is CI-validated, but BLE wake timing and VoxDMR's external broadcast path should still be verified on the target handset and physical PTT hardware.
