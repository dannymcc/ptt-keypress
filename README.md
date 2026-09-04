# PTT Keypress

PTT Keypress is a small Android middleware app that turns supported Bluetooth LE push-to-talk buttons into configurable Android key events.

**Sleeping BLE PTT button → PTT Keypress → Android key event → your existing key-mapper app**

The first supported hardware family is the Zello-style HM-10 / TI CC254x profile already proven in [android-ble-ptt](https://github.com/dannymcc/android-ble-ptt):

- service `FFE0`
- characteristic `FFE1`
- `0x01` = PTT pressed
- `0x00` = PTT released

## Important hardware behaviour

These PTT buttons sleep when released and only wake/advertise while the physical button is held.

PTT Keypress is designed around that behaviour:

- Pairing tells the user to **press and hold** the button so it wakes.
- Paired buttons are kept **armed with Android BLE autoConnect**, rather than continuously scanned.
- A healthy sleeping button is shown as **Ready — press PTT**, not Disconnected.
- If the device sleeps before a release notification arrives, PTT Keypress sends a fail-safe **KEY_UP** so the mapped key cannot remain stuck.

## Key mapping

Each paired PTT button has its own mapping.

Default:

- **Left Shift — Recommended**

Other presets:

- Right Shift
- Left Ctrl
- Left Alt
- F1
- F2
- F3
- F4
- Media Play / Pause

Pressing PTT sends a real `KEY_DOWN`; releasing it sends `KEY_UP`.

Left Shift is the default because a lone Shift press normally has no visible side effect, making it a useful trigger for a downstream key-mapper app.

## Shizuku

Arbitrary global key injection is performed by a Shizuku user service running with shell/root identity.

1. Install Shizuku.
2. Start Shizuku using wireless debugging or root.
3. Open PTT Keypress.
4. Grant the one-time Shizuku permission.
5. Pair a PTT button and choose its mapped key.

## APK downloads

GitHub Actions builds the app on every push to `main`.

The workflow uploads:

- `ptt-keypress.apk`
- `ptt-keypress.apk.sha256`

Main builds are published to the rolling **latest-main** prerelease. Tags matching `v*` create normal GitHub Releases.

The APK is signed with the same stable development key used by `android-ble-ptt`, so successive CI builds can be installed as updates instead of requiring an uninstall between builds.

## Build locally

Requirements:

- JDK 17
- Gradle 8.10.2
- Android SDK 35

For the same stable debug signature used by CI, place the development keystore at:

`signing/debug.keystore`

Then run:

```bash
gradle :app:assembleDebug
```

Without that file, Android Gradle Plugin can use your local debug signing setup instead.

## Package

`io.dmcc.pttkeypress`

## Status

Early MVP. The BLE lifecycle is intentionally tailored to wake-on-hold PTT buttons and should be tested on the target Android handset and actual PTT hardware before depending on it.
