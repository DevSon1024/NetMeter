# NetMeter

NetMeter is a modern Android network traffic monitor and utility application built with Kotlin, Jetpack Compose, and Material 3.

---

## Technical Stack & Architecture

- **Language**: Kotlin 2.2.10
- **UI Toolkit**: Jetpack Compose with Material 3 (Compose BOM 2026.02.01)
- **Minimum SDK**: 26 (Android 8.0 Oreo)
- **Compile / Target SDK**: 37 (Android 15+)
- **Build System**: Android Gradle Plugin 9.4.1 + Gradle 9.6.0
- **Version Catalog**: `gradle/libs.versions.toml`
- **Architecture**: Single `:app` module with feature-based architecture and lifecycle-aware `ViewModel`s

---

## Project Structure

```
app/src/main/java/com/devson/netmeter/
├── MainActivity.kt                # Hosts NetMeterApp and manages theme mode lifecycle
├── NetMeterApp.kt                 # Top-level scaffold, top app bar, and bottom navigation
├── core/
│   ├── navigation/
│   │   ├── AppNavigation.kt       # NavHost configuration
│   │   └── Screen.kt              # Navigation destination definitions (routes, icons, titles)
│   └── theme/
│       ├── Color.kt               # NetMeter Material 3 color system
│       ├── Theme.kt               # NetMeterTheme with Light, Dark, and System default modes
│       ├── ThemeMode.kt           # ThemeMode enum (SYSTEM, LIGHT, DARK)
│       └── Type.kt                # Typography styles
└── features/
    ├── dashboard/                 # Real-time speed & daily usage overview
    │   ├── DashboardScreen.kt
    │   └── DashboardViewModel.kt
    ├── appusage/                  # App breakdown & period filtering
    │   ├── AppUsageScreen.kt
    │   └── AppUsageViewModel.kt
    ├── history/                   # Historical network traffic records & logs
    │   ├── HistoryScreen.kt
    │   └── HistoryViewModel.kt
    ├── speedtest/                 # Connection speed & latency testing
    │   ├── SpeedTestScreen.kt
    │   └── SpeedTestViewModel.kt
    └── settings/                  # Theme selection & app diagnostics
        ├── SettingsScreen.kt
        └── SettingsViewModel.kt
```

---

## Build Variants & Dual Installation

The project is structured to allow side-by-side installation of Debug and Release builds on the same device without conflicts:

| Build Type | Package Name (`applicationId`) | App Label | Version Name |
| :--- | :--- | :--- | :--- |
| **Debug** | `com.devson.netmeter.debug` | `NetMeter Beta` | `1.0.0-beta` |
| **Release** | `com.devson.netmeter` | `NetMeter` | `1.0.0` |

---

## Keystore & Signing Configuration

Release APKs and Bundles are signed via [`keystore.properties`](keystore.properties) (kept out of version control):

```properties
storeFile=../netmeter-keystore.jks
storePassword=your_password
keyAlias=netmeter-key0
keyPassword=your_password
```

Credentials are dynamically consumed in `app/build.gradle.kts` without exposing secret literals in code.

---

## Getting Started & Build Commands

### Prerequisites
- JDK 17+ (or JDK 23)
- Android SDK with Platforms 26 through 37

### 1. Build Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Build Release APK
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

### 3. Run Unit Tests
```bash
./gradlew testDebugUnitTest
```
Runs unit tests verifying ViewModels, Theme states, and Navigation routes.

### 4. Install onto Device or Emulator
```bash
# Debug version (NetMeter Beta)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release version (NetMeter)
adb install -r app/build/outputs/apk/release/app-release.apk
```
