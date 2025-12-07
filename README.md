# Aurelay Android App — Minimal README

## What this is
- Small Android app that receives a raw PCM audio stream (s16le, 44.1 kHz, stereo) over TCP or TLS and plays it to the device audio output.

Install
- APK is attached to this repository's GitHub Releases for the AudioRelay app. Download the latest release and install the APK on your Android device.
  - If installing outside Play Store, enable "Install unknown apps" for your installer (e.g., browser or file manager).

## Basic usage
1. Start the app on the Android device. It will listen on a configured port (default: 5000).
2. On your desktop, run the sender (example sender included in the repo under `temp/audioRelay/send_audio.py`):

```sh
# plain TCP
python3 send_audio.py 192.168.1.34 5000 --device <your_monitor_name>

# TLS (recommended for untrusted networks)
python3 send_audio.py 192.168.1.34 5000 --tls --certfile cert.pem --keyfile key.pem --cafile ca.pem --device <your_monitor_name>
```

- Replace `192.168.1.34` and `5000` with the Android device IP and port shown in the app.
- For PulseAudio / PipeWire monitor names, list sources with `pactl list short sources`.

TLS and security note
- TLS is supported. For a simple setup you can use a self-signed cert + CA and supply `--cafile` to the sender and configure the receiving app to trust that CA.
- If you run over a trusted LAN only, plain TCP works but is unencrypted.

Build from source (Android)
- The Android project is a typical Gradle project. To build a release APK locally:

```sh
cd temp/audioRelay
./gradlew assembleRelease
```

(You will need JDK, Android SDK and configured signing config to create a signed release.)

Troubleshooting
- No audio / silence: verify sender is using the correct PulseAudio/ PipeWire monitor and ffmpeg is running.
- Connection refused: ensure device IP/port are correct, and any firewall on the Android device allows the listening port.
- TLS errors: double-check certificate files and CA trust.

## License & Contact
- This app is an open source project. See the repository LICENSE for terms.
- For issues or questions, open an issue in the repository.
