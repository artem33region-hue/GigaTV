# GigaTV

Android TV media player and its companion phone remote.

- `apk/`: television application, catalog, TorrServe playback and updates.
- `remote/`: phone remote with automatic LAN discovery.
- [Android TV setup and usage](apk/README.md).

## Build

Use JDK 17 and Android SDK 34. Create an untracked `local.properties` in each
project with `sdk.dir` for your machine, then run:

```powershell
cd apk
.\gradlew.bat testDebugUnitTest assembleDebug
```

Build `remote/` the same way. Build outputs are under each project's
`app/build/outputs/apk/` directory.

## Updates on the television

GigaTV checks for updates when it starts. When a newer version is available,
it offers to download the APK and open Android's installer. A manual check is
also available under Settings → Application update.

The default update manifest is:

`https://github.com/artem33region-hue/GigaTV/releases/latest/download/update.json`

Downloads use HTTPS and are checked against the APK hash, package name and
installed application's signing certificate. Installation requires Android's
confirmation and permission to install applications from GigaTV.

The `updateRepository` Gradle property can override the GitHub repository.
An address entered in Settings overrides the built-in address.

## Publish a version

The CI workflow tests and builds both applications on pushes and pull requests.
The release workflow runs on tags beginning with `v`.

Before the first release, configure these GitHub Actions repository secrets:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded existing signing keystore |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Signing key alias |
| `SIGNING_KEY_PASSWORD` | Signing key password |

Use the same signing key as the installed TV and phone applications. A different
key cannot update them in place. Never commit the keystore or its passwords.

Increase `versionCode` and `versionName` in `apk/app/build.gradle.kts`; increase
the remote's version independently when it changes. Commit and push the changes,
then push a tag matching the television version, for example `v0.4.6`.
The workflow validates the tag, runs tests, signs the APKs and publishes:

- `GigaTV.apk`
- `GigaTV-Remote.apk`
- `update.json`, with the version extracted from the signed TV APK and its SHA-256

The phone APK is distributed alongside the TV APK; automatic in-app updating
currently applies to the television application.

Keep releases public so the television can download them without a GitHub login.
The first installed build with the GitHub update address must be installed once
through ADB or Android's package installer.
