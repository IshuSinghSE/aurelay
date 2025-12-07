# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog (https://keepachangelog.com/en/1.0.0/)
and this project adheres to Semantic Versioning (https://semver.org/).

## [v1.2.0-rc1] - 2025-12-07

### Added
- CI: improved release workflow with non-interactive SDK installation and signing.

### Changed
- Release process: use GitHub-generated release notes and upload assets on tag.

### Fixed
- Robust APK verification and build-tools selection in CI.

## [v1.2.0] - 2025-12-07

### Added
- Rebrand changes and package updates.
- In-app runtime permission checks (POST_NOTIFICATIONS) and related lint fixes.
- Fastlane lanes for uploading mapping files to Play Store.

### Changed
- Signing: added keystore handling and `key.properties` usage for CI builds.
- CI/CD: tag-triggered release workflow, Android cmdline-tools/build-tools installation, and apksigner usage.

### Fixed
- Multiple Android Lint issues uncovered during CI (MissingPermission, NewApi guards, null-safety fixes).
- Release signing race conditions and `apksigner` availability in GitHub Actions.
## [v1.1.0] - 2025-06-01

### Added
- **Device Pairing**: Remember trusted devices with one tap; separate paired and nearby device lists; unpair button; auto-connect to saved devices.

### Changed
- **Audio Streaming**: Eliminated audio pauses from packet loss and lowered latency with optimized buffers; improved error recovery and smoother playback.
- **UX Improvements**: Cleaner settings, enhanced device name display, improved about dialog and connection management.

### Fixed
- Various minor fixes and polish.

## [v1.0.0] - 2025-12-03

### Added
- **Docs & Desktop Script** - Added documentation and a Python desktop script.
- **Secure TCP over TLS** - Implemented secure TCP connections over TLS for transport security.
- **Audio Improvements (Refactor)** - Improved audio sync and streaming internals.
- **Initial Commit** - Project initial import and base setup.



<!--
Guidance for maintainers:
## [vX.Y.Z] - YYYY-MM-DD
- Add new release sections at the top under "Unreleased" when preparing work.
- When cutting a release, move the Unreleased changes into a new versioned section and add the release date.
- Keep entries concise and grouped by Added/Changed/Fixed/Deprecated/Removed.
-->