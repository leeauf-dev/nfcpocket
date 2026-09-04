# NFC Pocket

[![Android build](https://github.com/leeauf-dev/nfcpocket/actions/workflows/build-apk.yml/badge.svg)](https://github.com/leeauf-dev/nfcpocket/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/leeauf-dev/nfcpocket?include_prereleases)](https://github.com/leeauf-dev/nfcpocket/releases)
[![License](https://img.shields.io/github/license/leeauf-dev/nfcpocket)](LICENSE)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/10)

NFC Pocket turns an Android phone into a temporary NFC tag for sharing web links. Pick a saved URL, hold another phone nearby, and it receives a standard NDEF URI record. The receiving phone does not need NFC Pocket installed.

The app is small, offline, and deliberately focused on one job. There are no accounts, ads, analytics, network calls, or Internet permission.

## Download

Download the APK from the [Releases page](https://github.com/leeauf-dev/nfcpocket/releases). Android may ask you to allow installations from your browser or file manager.

Development debug and signed release APKs are also available from successful [GitHub Actions runs](https://github.com/leeauf-dev/nfcpocket/actions/workflows/build-apk.yml) under the `nfcpocket-apk` artifact.

> Releases from v0.2.0 onward use the project signing key and the Android application ID `com.leeauf.pocketnfc`. The earlier v0.1.0 preview used a different application ID and must be removed separately.

## Features

- Emulates a read-only NFC Forum Type 4 Tag through Android HCE
- Shares `http://` and `https://` links as standard NDEF URI records
- Opens directly in emulation mode from Android's Share menu
- Saves links locally with custom names, favorites, and last-used dates
- Keeps up to 100 non-favorite links; favorites are never removed automatically
- Material 3 interface with system light/dark theme and dynamic colors
- Clear NFC/HCE status, direct NFC settings shortcut, and read feedback
- No backend, account, telemetry, or runtime network access

## Requirements

The sending phone needs:

- Android 10 or newer
- NFC hardware
- Android Host Card Emulation (`FEATURE_NFC_HOST_CARD_EMULATION`)
- NFC enabled

NFC Pocket can be installed on unsupported devices so it can explain what is missing, but those devices cannot emulate a tag.

Android phones with NFC can usually receive the link. iPhone XS and newer can normally detect a compatible NDEF URL in the background while the display is on. NFC behavior still varies by device, OS version, antenna placement, lock state, and vendor firmware.

## Usage

1. Add a URL, or share a page from a browser to **NFC Pocket**.
2. Tap **Emulate**.
3. Keep NFC Pocket open and place the receiving phone near the sender's NFC antenna.
4. Tap the notification shown by the receiving phone.
5. Press **Stop** when finished.

The NFC antenna is often near the top or around the camera module. Moving the phones slowly is more reliable than tapping them together.

## How it works

`NdefHostApduService` implements the NFC Forum Type 4 Tag exchange directly:

- NDEF application AID: `D2760000850101`
- Capability Container file: `E103`
- NDEF file: `E104`
- Supported commands: application/file `SELECT` and chunked `READ BINARY`
- NDEF file access: read-only

The active NDEF message is replaced whenever a link is selected. Android routes APDUs to the service while NFC Pocket is the preferred foreground HCE service. No external NFC library is included.

HCE does not reproduce a physical tag UID and cannot emulate every NFC technology. Other HCE apps using the standard NDEF AID may also cause a routing conflict. Some manufacturers restrict HCE or background behavior more aggressively than stock Android.

## Privacy

Saved links remain in Android app storage. Backups are disabled, and the manifest does not request `INTERNET`. The only requested permission is NFC.

## Build

The repository uses Kotlin, Gradle Kotlin DSL, Jetpack Compose, and Material 3. Builds run on GitHub Actions with Java 17 and Android API 36.

```text
GitHub → Actions → Build APK → Artifacts → nfcpocket-apk
```

Releases are created manually from **Actions → Publish release → Run workflow**. Enter a semantic version such as `0.2.0` and choose whether it is a pre-release. The workflow builds a signed APK, verifies its signature, creates the `v0.2.0` tag, and attaches the APK plus a SHA-256 checksum to the GitHub Release. It requires these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow fails safely if any signing secret is missing; it never publishes a debug-signed release.

## Contributing

Bug reports and focused pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a change. Security issues should follow [SECURITY.md](SECURITY.md).

## Credits

- The HCE design was informed by [LuigiVampa92/ndef-emulator](https://github.com/LuigiVampa92/ndef-emulator) and [MichaelsPlayground/NfcHceNdefEmulator](https://github.com/MichaelsPlayground/NfcHceNdefEmulator), both available under Apache-2.0. NFC Pocket contains its own implementation rather than bundling either library.
- The NFC glyph comes from [Google Material Design Icons](https://github.com/google/material-design-icons/tree/master/src/device/nfc/materialicons), licensed under Apache-2.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## License

NFC Pocket is licensed under the [Apache License 2.0](LICENSE).
