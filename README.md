# ChargeTrack

A native Android battery-tracking starter project built with Kotlin and Jetpack Compose.

## Included
- Six sections: Tracker, Speed, Predictor, Capacity, Live, Reports
- Local persistent session history
- Foreground service for observing battery percentage events while tracking is enabled
- Configurable rated capacity (default 5000 mAh)
- Estimated mAh and charging-time calculations
- GitHub Actions workflow for building a debug APK

## Build
Open the project in Android Studio with JDK 17 and Android SDK 35 installed, then sync Gradle and run the `app` configuration.

Or use a local Gradle 8.9 installation:
```bash
gradle assembleDebug
```
The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions
Push this project to a GitHub repository. The workflow named `Android APK` builds the debug APK and uploads it as an artifact.

## Important limitations
- This is a first functional starter, not a guarantee of bug-free operation on every Android device.
- mAh values are estimates based on battery percentage and the configured rated capacity.
- Current/voltage readings depend on what the device exposes.
- Android and manufacturer battery restrictions may interrupt background tracking.
- The initial implementation does not provide USB charger-output measurements, exact true battery capacity, or CSV export.
- Test on the target phone and review build logs before relying on long-term reports.
