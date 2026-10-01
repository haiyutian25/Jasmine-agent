# Jasmine

A production-grade ultra-minimalist application with live CSS variable token theming. Built entirely with Jetpack Compose (no XML layouts), structured as a multi-module MVVM project.

## Architecture

```
jasmine/
├── app/                        # App main: single Activity + navigation assembly + theme
├── core/
│   ├── agent/                  # Rust-core bindings (UniFFI): AgentChat / ConversationStore / ProviderProbe facades
│   ├── data/                   # Data layer (Hilt): user preferences, custom fonts, model providers (Preferences DataStore)
│   ├── markdown/               # Incremental Markdown engine (native) + Compose block renderer
│   ├── navigation/             # Navigation 3 infrastructure (AppNavigator)
│   ├── network/                # Retrofit / OkHttp (Hilt-provided, placeholder service)
│   └── ui/                     # Design tokens, JasmineTheme, BaseViewModel / EffectRunner base
├── feature/
│   ├── provider/
│   │   ├── api/                # Provider nav contract (ProviderNavKey)
│   │   └── impl/               # Model-provider management (DeepSeek preset + custom OpenAI-protocol providers)
│   ├── settings/
│   │   ├── api/                # Settings nav contract (SettingsNavKey)
│   │   └── impl/               # Settings screens (menu / appearance / font / size / language)
│   └── main/
│       ├── api/                # Main nav contract (MainNavKey: Splash / Main)
│       └── impl/               # Splash + chat home + chrome, MainViewModel / ChatViewModel (MVVM)
└── gradle/libs.versions.toml   # Version catalog
```

- **MVVM**: `MainViewModel` owns all shell state (theme, typography, fonts, sidebar) as `StateFlow`s; user preferences (theme, color mode, typography, font scale, active custom font, active provider/model) are persisted through Preferences DataStore via the data layer. The sidebar state is session-transient and deliberately not persisted; the settings flow lives on the Navigation 3 back stack and is restored by the navigation library. There is no bottom navigation bar — the app has a single top-level surface (see `MainScreen`).
- **Navigation 3**: destinations are declared as a serializable `NavKey` contract in `feature:main:api`; the app main assembles them through `NavDisplay` + `entryProvider`.
- **Agent**: `core:agent` is a thin facade over the Rust core (UniFFI bindings) — `AgentChat` runs the turn loop, both OpenAI wire protocols and the built-in tools; `ConversationStore` reads the core's own session files; `ProviderProbe` checks connectivity. The core's types never leak out of the module.
- **DI**: Hilt 2.x wires the network, data, agent and ViewModel layers. (There is no database module: chat transcripts live in the Rust core's rollout files, provider/preferences in DataStore.)

## Tech Stack

| Item | Version |
| :--- | :--- |
| AGP | 9.4.1 (compileSdk 37) |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 (Compose 1.12.1 / Material 3 1.4.0) |
| Navigation 3 | 1.1.7 |
| Hilt | 2.60.1 |
| Lifecycle | 2.11.0 |
| minSdk / targetSdk | 26 / 37 |
| JDK | 21 (required by Robolectric SDK 36) |

## Run Locally

**Prerequisites:** JDK 21, Android SDK (platform 37 + build-tools), and Android Studio or command-line Gradle 9.7+.

1. Open the project in Android Studio (or run any Gradle task from the CLI).
2. Allow Gradle sync to finish.
3. Run the `app` configuration on an emulator or physical device.

> **Debug signing:** the `debug` build type uses a signing config pointing to `debug.keystore` in the project root (git-ignored). If you don't have it, generate a standard one:
>
> ```bash
> keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey \
>   -storepass android -keypass android -keyalg RSA -keysize 2048 \
>   -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
> ```
>
> …or remove `debug { signingConfig = signingConfigs.getByName("debugConfig") }` from `app/build.gradle.kts` to fall back to default debug signing.

## Testing

```bash
gradle :app:testDebugUnitTest                    # app-level Robolectric + Roborazzi
gradle :feature:main:impl:testDebugUnitTest      # chat state machine + the MVVM/UDF gate
gradle :core:ui:testDebugUnitTest                # BaseViewModel / EffectRunner / EventsEffect
gradle :core:agent:testDebugUnitTest             # event sink, coreEventFlow, boundary mappings
gradle :core:data:testDebugUnitTest              # provider-config read path
gradle :feature:provider:impl:testDebugUnitTest  # provider CRUD + optimistic-write rollback
gradle :feature:settings:impl:testDebugUnitTest  # language page
gradle :core:markdown:testDebugUnitTest          # incremental parsing / streaming document
gradle :app:testDebugUnitTest                    # shell, view models, screenshot, back handling
```

`gradle testDebugUnitTest` runs all of the above (133 JVM tests). Rust: `cargo test --workspace` (189 tests).

- Robolectric tests run against **SDK 36** (see `app/src/test/resources/robolectric.properties`), which requires Java 21.
- `MainScreenshotTest` renders the home chat surface via Roborazzi (`app/src/test/screenshots/chat.png`). To (re)generate the golden image, run once with `-Proborazzi.test.record=true`.
- `MvvmUdfGateTest` (`feature:main:impl`) is a source-level guard for the UDF contract: a single state
  mutation point, asynchronous results reflowing as `Internal` actions, shadow state written only from
  synchronous handlers, the conversation projection being derived rather than hand-copied, and view
  files staying free of platform calls.
- Chat transcripts live in the Rust core's own rollout files (`files/sessions/<y>/<m>/<d>/rollout-*.jsonl`,
  one append-only JSONL per conversation) and the most recent one is restored on launch; the model
  selection lives in Preferences DataStore.

## Release Build

```bash
gradle :app:assembleRelease     # → app/build/outputs/apk/release/app-release.apk
```

### Signing

The release keystore is resolved in this order:

1. `KEYSTORE_PATH` (environment variable) — used by CI and as an explicit override.
2. `${rootDir}/my-upload-key.jks` — the local default.

`STORE_PASSWORD` / `KEY_PASSWORD` supply the passwords, and the key alias is
always **`upload`**. A keystore without that alias will fail to sign.

> **Local test key:** this checkout contains `my-upload-key.jks`, a throwaway test
> key (`CN=Jasmine Test Release`, alias `upload`, git-ignored). It is fine for
> installing and testing release builds, but it is **not an upload key** — Google
> Play rejects debug-grade/test identities, and switching keys later requires
> uninstalling the app first. Replace it before publishing:
>
> ```bash
> keytool -genkeypair -v -keystore my-upload-key.jks -alias upload \
>   -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12 \
>   -dname "CN=Your Name,O=Your Org,C=Your Country"
> ```

Verify which key actually signed an APK (this is the only reliable answer — the
resolved keystore is not echoed by the build):

```bash
"$ANDROID_HOME/build-tools/<ver>/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```
