# GitHub Copilot / AI agent instructions for Aurelay

Short, targeted guidance to help an AI coding assistant be productive in this repository.

Overview
- Purpose: Desktop audio sender app with a Rust audio engine (cpal) and a Compose Desktop UI. Primary audio capture lives in Rust; Kotlin calls native functions via JNA (UniFFI-generation is planned but often missing in the env).

Big picture
- `rust_engine/` contains the audio engine (cpal), multiple transport implementations (TCP, UDP, authenticated UDP), and an FFmpeg fallback implemented in Rust. The engine uses a modular architecture with swappable transport layers.
- `desktopApp/` is the Compose Desktop UI (Kotlin). It calls the native lib via `desktopApp/src/jvmMain/kotlin/uniffi/rust_engine.kt` and wires UI controls in `desktopApp/src/jvmMain/kotlin/com/devindeed/aurelay/desktop/Main.kt`.

Module Structure (Rust):
```
rust_engine/src/
├── lib.rs              // Thin FFI bridge with C wrappers
├── models.rs           // TransportMode, SessionState, DeviceInfo structs
├── audio/
│   ├── mod.rs
│   ├── capture.rs      // CPAL audio capture with PCM_S16LE conversion
│   ├── device.rs       // Device enumeration and filtering
│   └── playback.rs     // Placeholder for receiver playback
├── network/
│   ├── mod.rs
│   ├── transport.rs    // AudioTransport trait, TcpTransport, UdpTransport, AuthenticatedUdpTransport
│   └── session.rs      // TCP handshake, AUTH_REQUEST/AUTH_OK protocol
├── discovery.rs        // mDNS device discovery
├── logs.rs            // NATIVE_LOGS buffer
└── ffmpeg.rs          // FFmpeg fallback (TCP streaming)
```

Key files to read first
- `rust_engine/src/models.rs` — Core data structures: TransportMode enum (TcpOnly/TcpUdp/TcpUdpAuth/TlsUdpAuth), SessionState, StreamConfig
- `rust_engine/src/network/transport.rs` — Transport layer: AudioTransport trait with TcpTransport, UdpTransport, and AuthenticatedUdpTransport implementations. Factory function `create_transport()` builds the right transport based on mode.
- `rust_engine/src/network/session.rs` — Handshake protocol: `handshake()` performs AUTH_REQUEST/AUTH_OK exchange for authenticated modes
- `rust_engine/src/audio/capture.rs` — CPAL capture with format conversion to PCM_S16LE (signed 16-bit little-endian at 44.1kHz/48kHz stereo)
- `rust_engine/src/lib.rs` — FFI bridge with C wrappers: `start_stream_c`, `start_stream_with_device_c`, `start_stream_with_mode_c` (new), `stop_stream_c`
- `desktopApp/src/jvmMain/kotlin/uniffi/rust_engine.kt` — JNA bindings and thin Kotlin wrappers used across the UI.
- `desktopApp/src/jvmMain/kotlin/com/devindeed/aurelay/desktop/Main.kt` — UI wiring: Start/Stop, backend selector (Native/FFmpeg), device listing (`pactl` + CPAL), and logs dialog.
- Root Gradle files & `desktopApp` build scripts — examine tasks that build the Rust native lib (cargo) and copy the produced `librust_engine.so` into app resources.

Build / run / debug (practical commands)
- Build and run the desktop app (recommended):
```
./gradlew :desktopApp:cargoBuild :desktopApp:run --warning-mode=all
```
- To build the Rust lib alone (release):
```
cd rust_engine
cargo build --release
```
- If UniFFI bindings are required, `uniffi-bindgen` must be available in PATH; the Gradle build currently falls back to JNA when it's absent.

Project-specific patterns & conventions
- **Modular Transport System**: Audio engine uses swappable transports via the `AudioTransport` trait. Current implementations:
  - `TcpTransport`: Pure TCP streaming (current Android receiver uses this)
  - `UdpTransport`: Low-latency UDP streaming (no authentication)
  - `AuthenticatedUdpTransport`: UDP with 8-byte session token prepended to each packet
- **Transport Modes** (see `TransportMode` enum in models.rs):
  - `TcpOnly`: Pure TCP streaming (default, compatible with current Android)
  - `TcpUdp`: TCP handshake + UDP streaming (no auth)
  - `TcpUdpAuth`: TCP handshake + authenticated UDP with token
  - `TlsUdpAuth`: TLS handshake + authenticated UDP (future enhancement)
- **Audio Format**: All CPAL formats (F32, I16, U16) are converted to PCM_S16LE (signed 16-bit little-endian) to match Android AudioTrack expectations
- **Session Management**: `handshake()` performs AUTH_REQUEST/AUTH_OK exchange for authenticated modes. Returns `SessionState` with optional token.
- **FFI surface**: C-compatible wrapper functions are used instead of direct JNI. Kotlin uses a JNA shim in `uniffi/rust_engine.kt`. Expect `start_stream_c`, `start_stream_with_device_c`, `start_stream_with_mode_c`, `stop_*`, `get_native_logs_c`, and `list_cpal_input_devices_c` to exist.
- **FFmpeg fallback**: implemented in Rust (spawned with `Command::new("ffmpeg")`) and controlled via an internal channel — UI calls a C wrapper which spawns the process in native code. FFmpeg also uses TCP streaming.
- **Device discovery**: UI uses `pactl list short sources` for PulseAudio/PipeWire monitor names and also surfaces CPAL device names via `list_cpal_input_devices_c`. ALSA hardware devices (hw:, plughw:, sysdefault:, front:) are filtered out to reduce confusion.

Integration points & external dependencies
- System dependencies: `ffmpeg`, and Linux audio stacks (PulseAudio/PipeWire/ALSA). `pactl` is used by the UI to enumerate monitor sources.
- Native lib placement: Gradle build copies the built native library into the desktop app resources; check `desktopApp` build scripts if paths need adjustment.

Common pitfalls for automation
- UniFFI generation may fail if `uniffi-bindgen` isn't installed; the Gradle script currently falls back to the JNA stub. To produce JNI bindings, install `uniffi-bindgen` on the runner.
- CPAL device names vs `pactl` names differ — mapping is manual: provide the exact monitor/device string from `pactl` or the CPAL list.

When editing native code
- **Keep C wrappers stable**: UI expects the C symbols mentioned above. If you add new exported functions, also update `desktopApp/src/jvmMain/kotlin/uniffi/rust_engine.kt`.
- **Transport modes**: Use `start_stream_with_mode_c` to select transport. Default is "tcp_only" for compatibility.
- **Audio format**: CPAL captures in native format (F32/I16/U16), but all formats are converted to PCM_S16LE before sending over network (see `sample_to_bytes_*` functions in capture.rs)
- **Avoid blocking**: The Rust code spawns threads for ffmpeg/streaming and exposes control via channels. Audio callbacks run on high-priority threads and must be non-blocking.
- **Thread safety**: `EngineState` is protected by `Lazy<Mutex<>>`. Transport implementations must be `Send + Sync`.

If you change build or CI
- Ensure the Gradle `cargo` tasks still copy the produced native artifact into the desktop resources. CI runners must install Rust toolchain, `cargo`, and system `ffmpeg` for end-to-end tests that exercise streaming.

Where to ask questions
- Look for context in `rust_engine/src/lib.rs` and the Compose UI file `desktopApp/src/jvmMain/kotlin/com/devindeed/aurelay/desktop/Main.kt` before requesting more information.

Feedback
- If anything in this guide is unclear or missing, tell me which area (build, native bindings, device mapping, or UI) to expand.

**Final / multi-platform architecture (summary)**
- High level flow (applies to Android, iOS, Desktop, others):
	1. **Auto-discovery (mDNS / multicast)**: Sender advertises on the local network (example: service `aurelay._tcp` on port 5000). Receivers discover available senders.
	2. **Secure handshake (TCP)**: Receiver establishes a TCP connection to the Sender and performs an authentication handshake (user consent on receiver). On success the Receiver receives an auth token.
	3. **Low-latency streaming (flexible transport)**:
	   - **Current Implementation (TcpOnly)**: Sender streams PCM_S16LE audio over TCP to the Receiver. Android uses `Socket` + `AudioTrack`.
	   - **Future: Authenticated UDP**: Sender streams audio over UDP to the Receiver's address/port. Each UDP packet includes the authentication token and the audio payload (token verification is performed by the receiver before playback).

- **Transport Modes** (swappable via `TransportMode` enum):
	- `TcpOnly`: Pure TCP streaming (current default, Android compatible)
	- `TcpUdp`: TCP handshake + UDP streaming (no authentication)
	- `TcpUdpAuth`: TCP handshake + authenticated UDP (8-byte token prepended to each packet)
	- `TlsUdpAuth`: TLS handshake + authenticated UDP (future enhancement)

- Default ports & tokens used in the reference implementation:
	- Discovery / control: 5000 (mDNS/TCP examples in the desktop app)
	- Streaming: 5000 (TCP or UDP depending on mode)
	- Auth token: opaque 64-bit token returned by the Receiver during the TCP handshake and included in subsequent UDP packets (when using authenticated modes).

- **Audio Format Standardization**:
	- Desktop sender: CPAL captures in native format → converts all to PCM_S16LE (signed 16-bit little-endian)
	- Sample rate: 44100 Hz or 48000 Hz (device-dependent)
	- Channels: Stereo (2) or Mono (1) depending on device
	- Android receiver: Expects PCM_S16LE via `AudioFormat.ENCODING_PCM_16BIT`

- Platform notes & guidance for AI agents
	- **Android (Receiver)**: uses a Foreground Service + `NsdManager` or socket-based mDNS for discovery. **Current implementation uses TCP Socket (`Socket()`) to receive PCM_S16LE audio** and plays via `AudioTrack`. Future: Add support for authenticated UDP reception. See `app/src/main/java/com/devindeed/aurelay/AudioCaptureService.kt` for the Android implementation of the receiver service and UI flow.
	- **Desktop (Sender)**: primary engine is Rust (`rust_engine/`) using `cpal` for capture. The desktop UI (Compose) calls native C wrappers exposed from Rust (`uniffi/rust_engine.kt`). The Rust sender implements discovery, TCP/UDP transports (swappable via `TransportMode`), and session handshake. **Default mode is TcpOnly** for compatibility with current Android receiver.
	- **iOS (Receiver)**: should mirror the Android receiver design — discovery, TCP handshake with user consent, and authenticated UDP playback. Implementation specifics (Swift/Obj-C) are outside this repo; coordinate FFI or KMP glue for shared logic.
	- **Web**: browsers cannot run native Rust `cpal` directly. For web targets consider a browser-specific capture pipeline (Web Audio / getUserMedia) and a gateway/adapter that implements the same handshake and authenticated UDP (or WebRTC) transport to match the other platforms.

	**Design & UI guidelines**
	- Use Kotlin Multiplatform (KMP) to share business logic; keep UI code in platform-specific Compose modules (Compose for Desktop, Compose for Android, SwiftUI/UIKit for iOS where needed).
	- Use Material 3 theming and Compose Material3 components in `desktopApp` and mobile modules — prefer built-in M3 components, colors, elevation, and motion system.
	- Follow Apple design philosophy: clarity, deference, and depth. Examples from the mockups:
		- Large, prominent circular Start/Stop control centered on small screens.
		- Left navigation + detail panes on wide screens (desktop landscape) that reflow to stacked panels on narrow screens (portrait/mobile).
		- Subtle motion/transitions for state changes (start/stop, device list updates).
	- Responsive layout rules:
		- Use Window/Device size classes and Compose layout primitives (ConstraintLayout, Box, Row/Column with weights) to adapt automatically to portrait vs. landscape and to resizable desktop windows.
		- On wide screens show a 2- or 3-column layout (controls, device list, diagnostics); on narrow screens switch to single-column stacked views.
		- Respect safe-area / display cutouts and window insets. Ensure the main action control is visible and centered across sizes.
	- Accessibility & platform polish:
		- Support dynamic type (font scaling), high-contrast themes, and keyboard navigation on desktop.
		- Follow platform conventions for motion and haptics where available.
	- Implementation notes for AI agents:
		- Inspect `desktopApp/src/jvmMain/kotlin/com/devindeed/aurelay/desktop/Main.kt` for examples of Compose layout and the Start/Stop control. Mirror the responsive pattern when adding screens.
		- Keep UI thin: call shared logic via `desktopApp/src/jvmMain/kotlin/uniffi/rust_engine.kt`. Do not perform heavy work on the UI thread; use coroutines or background threads for native calls.


- Security and interoperability reminders
	- Tokens must be treated as secrets; do not expose them in logs.
	- The UDP stream is authenticated per-packet by including the token; ensure the receiver verifies the token before decoding audio.
	- Keep the control channel (TCP handshake) separate from the high-volume UDP stream; maintain explicit state transitions (discovered -> handshaking -> streaming).
