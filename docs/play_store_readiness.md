# CampusVista Play Store Readiness

## Release Artifact

Build the Google Play upload artifact with:

```bash
cd android-app
./gradlew :app:bundleRelease
```

The generated App Bundle is:

```text
android-app/app/build/outputs/bundle/release/app-release.aab
```

## Offline Runtime

The release Android app is designed to run fully offline after installation:

- no `INTERNET` permission
- no localhost or laptop backend URL
- packaged SQLite campus data
- packaged map and panorama assets
- packaged OpenCLIP ONNX image encoder and matching recognition index

## Signing

Google Play requires an upload key. Do not commit keystores or passwords.

1. Copy `android-app/keystore.properties.example` to `android-app/keystore.properties`.
2. Generate or provide an upload key `.jks`.
3. Fill in the local `keystore.properties` values.
4. Re-run `./gradlew :app:bundleRelease`.

Both `*.jks` and `keystore.properties` are gitignored.

## Play Console Notes

- Package id: `com.campusvista.app`
- App category: Maps & Navigation or Education
- Data safety: app is offline and does not transmit user data.
- Permissions: camera is used only for on-device photo recognition.
- Privacy policy draft: `docs/privacy_policy.md`
