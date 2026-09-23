# Jasmine

A production-grade ultra-minimalist application with live CSS variable token theming. Built entirely with Jetpack Compose (no XML layouts), structured as a multi-module MVVM project.

## Architecture

```
jasmine/
├── app/                        # App main: single Activity + navigation assembly + theme
├── core/
│   ├── agent/                  # Google ADK integration: OpenAI-protocol Model adapters (Chat Completions + Responses), ProviderProbe, AgentChat
│   ├── data/                   # Data layer (Hilt): UserPreferencesRepository, CustomFontRepository, ProviderRepository
│   ├── database/               # Room: chat transcript (conversations + messages, v5)
│   ├── navigation/             # Navigation 3 infrastructure (AppNavigator)
│   ├── network/                # Retrofit / OkHttp (Hilt-provided, placeholder service)
│   └── ui/                     # Design tokens, JasmineTheme, shared utilities
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

- **MVVM**: `MainViewModel` owns all feature state (theme, typography, fonts, tab, sidebar) as `StateFlow`s; user preferences (theme, color mode, typography, font scale, active custom font, active provider/model) are persisted through Preferences DataStore via the data layer. Navigation chrome state (tab/sidebar) is session-transient and deliberately not persisted; the settings flow lives on the Navigation 3 back stack and is restored by the navigation library.
- **Navigation 3**: destinations are declared as a serializable `NavKey` contract in `feature:main:api`; the app main assembles them through `NavDisplay` + `entryProvider`.
- **Agent**: `core:agent` adapts any OpenAI-protocol provider to Google ADK's `Model` contract (both Chat Completions and the Responses API) and exposes two narrow, ADK-free facades — `ProviderProbe` (connectivity check) and `AgentChat` (`LlmAgent` + `InMemoryRunner` + in-memory session service). ADK types never leak out of the module.
- **DI**: Hilt 2.x wires the database, network, data, agent and ViewModel layers.

## Tech Stack

| Item | Version |
| :--- | :--- |
| AGP | 9.4.1 (compileSdk 37) |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 (Compose 1.12.1 / Material 3 1.4.0) |
| Navigation 3 | 1.1.7 |
| Hilt | 2.60.1 |
| Lifecycle | 2.11.0 |
| Room | 2.7.0 |
| Google ADK for Kotlin | 1.1.0 (`google-adk-kotlin-core`) |
| minSdk / targetSdk | 26 / 37 |
| JDK | 21 (required by Robolectric SDK 36) |

> `minSdk 26` is not a free choice: `google-adk-kotlin-core` resolves to its Android
> variant, whose AAR declares `minSdkVersion=26` and `minCompileSdk=37`. API 24–25 is
> therefore out of reach while ADK is a dependency.

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
gradle :app:testDebugUnitTest                # app-level Robolectric + Roborazzi
gradle :core:agent:testDebugUnitTest         # OpenAI wire formats + ADK session/replay
gradle :core:database:testDebugUnitTest      # migration DDL vs Room's exported schema
gradle :feature:main:impl:testDebugUnitTest  # chat state machine
```

- Robolectric tests run against **SDK 36** (see `app/src/test/resources/robolectric.properties`), which requires Java 21.
- `MainScreenshotTest` renders the home chat surface via Roborazzi (`app/src/test/screenshots/chat.png`). To (re)generate the golden image, run once with `-Proborazzi.test.record=true`.
- `OpenAiWireTest` (`core:agent`) asserts the exact JSON sent to providers and the exact ADK
  types parsed back, for both Chat Completions and the Responses API. `AdkAgentChatTest` drives
  the real ADK runner with a recording `Model` to prove a restored transcript actually reaches
  the model. Neither needs a network or an API key.
- `MigrationDdlTest` (`core:database`) pins the hand-written v4→v5 migration SQL to Room's
  exported schema, so a schema drift fails the build instead of crashing on open.
- Chat transcripts are persisted in Room (`conversations` + `messages`) and the most recent one
  is restored on launch; the model selection lives in Preferences DataStore.

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
