fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android build_apk

```sh
[bundle exec] fastlane android build_apk
```

Build a release APK

### android build_aab

```sh
[bundle exec] fastlane android build_aab
```

Build a release App Bundle (AAB)

### android internal

```sh
[bundle exec] fastlane android internal
```

Deploy a new version to the Google Play Internal Track

### android beta

```sh
[bundle exec] fastlane android beta
```

Deploy a new version to the Google Play Beta Track

### android promote_to_production

```sh
[bundle exec] fastlane android promote_to_production
```

Promote from beta to production

### android production

```sh
[bundle exec] fastlane android production
```

Deploy a new version to Production

### android release

```sh
[bundle exec] fastlane android release
```

Submit a new build to Internal Testing and create a GitHub Release

### android bump_version_code

```sh
[bundle exec] fastlane android bump_version_code
```

Increment version code

### android bump_version_name

```sh
[bundle exec] fastlane android bump_version_name
```

Increment version name (patch)

### android test

```sh
[bundle exec] fastlane android test
```

Run tests

### android lint

```sh
[bundle exec] fastlane android lint
```

Run lint checks

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
