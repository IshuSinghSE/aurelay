# Aurelay Shared Module (KMP)

This is the **Kotlin Multiplatform (KMP)** shared module for Aurelay, containing common UI and business logic shared between Android and Desktop platforms.

## Architecture Overview

```
/shared
  /build.gradle.kts  ← KMP configuration with Compose Multiplatform
  /src
    /commonMain      ← 99% OF YOUR CODE LIVES HERE
      /kotlin
        /com/aurelay
          /ui
            - MainScreen.kt    (The main UI with power button, device list, receivers)
            - Theme.kt         (Material 3 colors, typography)
            - Components.kt    (PowerButton, DeviceCard, ReceiverCard, etc.)
          /engine
            - AudioEngine.kt   (Platform-agnostic interface)
          - App.kt             (Entry point composable)

    /androidMain     ← ANDROID SPECIFIC (Tiny)
      /kotlin
        /com/aurelay/engine
          - AndroidAudioEngine.kt  (Implements AudioEngine using Android Service)

    /desktopMain     ← DESKTOP SPECIFIC (Tiny)
      /kotlin
        /com/aurelay/engine
          - DesktopAudioEngine.kt  (Implements AudioEngine using Rust/CPAL)
```

## Key Components

### Common Main (Platform-Agnostic)

#### UI Layer
- **`App.kt`**: Main entry point composable that wraps the UI with the Aurelay theme
- **`MainScreen.kt`**: The main screen layout with:
  - Responsive design (wide screen = 2-column, narrow = single-column)
  - Power button (start/stop streaming)
  - Device selection
  - Receiver list
  - Status indicators
- **`Theme.kt`**: Material 3 theme with dark/light color schemes
- **`Components.kt`**: Reusable UI components:
  - `PowerButton`: Large circular start/stop button with animations
  - `DeviceCard`: Audio device selection cards
  - `ReceiverCard`: Discovered receiver cards
  - `StatusIndicator`: Current streaming status
  - `SmartAdBanner`: Ad placeholder

#### Engine Layer
- **`AudioEngine.kt`**: Platform-agnostic interface defining:
  - `startStreaming()`, `stopStreaming()`
  - `refreshDevices()`, `startDiscovery()`, `stopDiscovery()`
  - State flows for: `streamState`, `availableDevices`, `selectedDevice`, `discoveredReceivers`, `logs`
  - Data classes: `AudioDevice`, `Receiver`, `StreamState`, `TransportMode`

### Android Main (Platform-Specific)

- **`AndroidAudioEngine.kt`**: Android implementation of `AudioEngine`
  - Integrates with `AudioCaptureService` (to be connected)
  - Uses `NsdManager` for mDNS discovery
  - Manages Android-specific audio APIs

### Desktop Main (Platform-Specific)

- **`DesktopAudioEngine.kt`**: Desktop implementation of `AudioEngine`
  - Uses Rust audio engine via JNA bindings (`uniffi.rust_engine.*`)
  - CPAL audio capture
  - mDNS discovery
  - Supports multiple transport modes (TCP, UDP, authenticated)

## How to Use

### Android App Integration

```kotlin
// In your MainActivity.kt
class MainActivity : ComponentActivity() {
    private lateinit var audioEngine: AndroidAudioEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        audioEngine = AndroidAudioEngine(this)
        
        setContent {
            App(audioEngine = audioEngine)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        audioEngine.dispose()
    }
}
```

### Desktop App Integration

```kotlin
// In your Main.kt
fun main() = application {
    // Load native library first
    loadNativeLibrary()
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Aurelay Sender"
    ) {
        val audioEngine = remember { DesktopAudioEngine() }
        App(audioEngine = audioEngine)
    }
}
```

## Dependencies

The shared module uses:
- **Compose Multiplatform**: For cross-platform UI
- **Material 3**: Design system and components
- **Kotlin Coroutines**: For async operations
- **StateFlow**: For reactive state management

### Android-specific:
- Android core libraries
- NsdManager for discovery
- AudioCaptureService integration

### Desktop-specific:
- JNA for native library bindings
- Rust engine (CPAL) for audio capture

## Build Configuration

### Targets
- `androidTarget`: Android applications (minSdk 26)
- `jvm("desktop")`: Desktop JVM applications

### Gradle Tasks
```bash
# Build the shared module
./gradlew :shared:build

# Build and run Android app with shared module
./gradlew :app:assembleDebug

# Build and run Desktop app with shared module
./gradlew :desktopApp:cargoBuild :desktopApp:run
```

## Design Principles

1. **Platform Independence**: 99% of code lives in `commonMain`
2. **Dependency Injection**: Platform-specific implementations injected via `AudioEngine`
3. **Reactive State**: All state managed via `StateFlow` for reactive UI updates
4. **Material 3**: Consistent design language across platforms
5. **Responsive Layout**: Adapts to screen size (mobile portrait, desktop landscape)

## Transport Modes

The `AudioEngine` interface supports multiple transport modes:
- `TcpOnly`: Pure TCP streaming (default, Android compatible)
- `TcpUdp`: TCP handshake + UDP streaming
- `TcpUdpAuth`: TCP handshake + authenticated UDP
- `TlsUdpAuth`: TLS handshake + authenticated UDP (future)

## Audio Format

All platforms standardize on:
- Format: PCM_S16LE (signed 16-bit little-endian)
- Sample Rate: 44100 Hz or 48000 Hz
- Channels: Stereo (2) or Mono (1)

## TODOs

### Android
- [ ] Connect `AndroidAudioEngine` to existing `AudioCaptureService`
- [ ] Implement `NsdManager` for mDNS discovery
- [ ] Add proper service binding and lifecycle management
- [ ] Integrate Google Mobile Ads (replace `SmartAdBanner` placeholder)

### Desktop
- [ ] Test with actual Rust engine builds
- [ ] Add support for all transport modes
- [ ] Improve device selection UI (map CPAL ↔ pactl names)
- [ ] Add settings/preferences screen

### Common
- [ ] Add error handling dialogs
- [ ] Add connection approval UI
- [ ] Add transport mode selector
- [ ] Add audio quality settings
- [ ] Add persistent preferences

## Migration Guide

If you have existing Android or Desktop code, here's how to migrate:

1. **Move common UI to `commonMain`**: Extract your Compose UI to `shared/src/commonMain/kotlin/com/aurelay/ui/`
2. **Implement `AudioEngine`**: Create platform-specific implementations in `androidMain` and `desktopMain`
3. **Update build.gradle.kts**: Add `implementation(project(":shared"))` to both app modules
4. **Update entry points**: Use `App(audioEngine)` composable instead of custom UI

## Testing

```bash
# Test shared module
./gradlew :shared:test

# Test Android integration
./gradlew :app:connectedAndroidTest

# Test Desktop integration
./gradlew :desktopApp:test
```

## Contributing

When adding new features:
1. Start with the `commonMain` interface/UI if possible
2. Add platform-specific code only when necessary
3. Keep the `AudioEngine` interface stable
4. Follow Material 3 design guidelines
5. Maintain responsive layout support

## License

Same as the parent Aurelay project.
