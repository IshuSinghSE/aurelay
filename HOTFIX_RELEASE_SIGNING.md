Hotfix: Release signing & CI fixes

This branch contains a minimal commit to open a PR so we can iterate on release workflow fixes.

Planned fixes applied in this branch:

- Ensure the CI decodes the keystore into `app/aurelay-release.jks` so Gradle signing config can find it during CI.
- Update `key.properties` creation to point to `app/aurelay-release.jks`.
- Add mapping upload in Fastlane lanes so ProGuard mapping is uploaded automatically.

Notes:
- Repository secrets `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, and `PLAY_STORE_JSON_KEY` are present.
- After this PR is merged, trigger a tag push (or rerun the workflow) to run the release job again.
