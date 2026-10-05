# ETHnym

Native Android Ethereum wallet.

## Stack

- Kotlin 2.4 + Jetpack Compose (Material 3), single `Activity`, edge-to-edge
- AGP 9 with built-in Kotlin, Gradle version catalog (`gradle/libs.versions.toml`)
- Navigation 3 (`NavDisplay` + `@Serializable` `NavKey`s)
- ViewModel + `StateFlow` UI state, collected with `collectAsStateWithLifecycle`
- Hilt for DI (KSP), DataStore for non-sensitive preferences
- minSdk 28, targetSdk/compileSdk 37

## Layout

```
app/src/main/java/com/ethnym/
├── EthnymApplication.kt     @HiltAndroidApp
├── MainActivity.kt          Compose entry point
├── ui/                      EthnymApp (NavDisplay), navigation keys, theme
├── feature/<name>/          Screen (stateless) + Route (wires ViewModel) + ViewModel
├── data/<area>/             Repositories (interface + implementation)
└── di/                      Hilt modules
```

## Build

```sh
./gradlew assembleDebug          # build
./gradlew installDebug           # install on a running device/emulator
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew connectedDebugAndroidTest  # instrumented/Compose UI tests
./gradlew lint
```

Open the folder in Android Studio to run from the IDE.

## Publish

[Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher) uploads release builds to Play Console:

```sh
./gradlew publishReleaseBundle                    # build + upload to the internal testing track
./gradlew publishReleaseBundle --track alpha      # closed testing instead
./gradlew publishReleaseBundle --release-name "0.2.0 – Send fixes"
```

- Needs the Play service-account key at `play-service-account.json` (gitignored), or its JSON contents in `ANDROID_PUBLISHER_CREDENTIALS`.
- `versionCode` is bumped automatically past the highest one on Play; bump `versionName` yourself.
- Release notes come from `app/src/main/play/release-notes/en-US/default.txt` (max 500 characters). Edit it before each upload.

## Security defaults

- Cloud backup and device-to-device transfer are disabled (`allowBackup=false`, `data_extraction_rules.xml`).
- DataStore is unencrypted, so keys and seeds must never go there. Use Android Keystore-backed storage.
- R8 minification and resource shrinking are on for release builds.
