# Sentry RF 0.1

Passive Android BLE + Wi-Fi observer for field security work.

## Current build target

- Android 17 / API 37 (`compileSdk 37`, `targetSdk 37`)
- Minimum Android 10 / API 29
- Android Gradle Plugin 9.4.0
- Java 17
- No third-party runtime libraries; platform Android APIs only

## v0.1 functions

- Continuous BLE scan while the app is in the foreground
- Periodic Wi-Fi access-point scan (subject to Android scan throttling)
- Local, offline signature engine loaded from `assets/signatures.json`
- Initial signatures for DULT-compatible trackers, Remote ID broadcasts, drone brands, body cameras, smart glasses, wireless microphones and camera families
- Risk score with explicit confidence level
- Baseline: save currently observed radio IDs as known; future/new IDs are marked NEW
- Filtering by category
- Raw BLE/Wi-Fi details on row tap
- CSV export through Android's Storage Access Framework
- No backend and no network permission

## Important Android behavior

Wi-Fi `startScan()` and `getScanResults()` are still gated by precise Location on current Android. The app requests it solely to access those framework scan APIs; v0.1 does not read or store GPS coordinates.

Bluetooth permissions follow the Android 12+ Nearby Devices model. The app does not connect to discovered devices.

## Build in Android Studio

1. Open this folder in a current Android Studio version with Android 17 SDK installed.
2. Ensure Android SDK Platform 37 and Build Tools 36.0.0+ are installed.
3. Sync Gradle and run the `app` configuration.

The project intentionally has no Gradle wrapper JAR in this generated package. Android Studio can use its configured Gradle, or use Gradle 9.6.0 from the command line.

## GitHub build

`.github/workflows/build.yml` builds a debug APK on Ubuntu using Gradle 9.6.0 and Android SDK 37.

## Scope

Classification is based on radio-advertisement patterns visible to the Android handset. A match is a hypothesis, not proof of device identity. Radios that are silent, cellular-only, sleeping, randomized, wired, or filtered by Android will not appear.

## Next logical modules

- ASTM/OpenDroneID payload decoder and map view
- persistence/co-travel scoring across sessions
- import/export of signature catalogs
- vehicle sweep and room sweep profiles
- foreground service for controlled long-duration sessions
- signed release build and in-app catalog updater
