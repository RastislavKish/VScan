# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

VScan is an Android app (Kotlin) that turns a phone camera into a visual perception aid for blind users. The user captures an image, and an OpenAI-protocol-compatible vision LLM interprets it according to a configurable prompt. It is published on F-Droid and Google Play. License is GPLv3.

## Build and test commands

Standard Gradle Android project. Use the wrapper:

```bash
./gradlew assembleDebug          # build debug APK
./gradlew assembleRelease        # build release APK
./gradlew installDebug           # build + install on a connected device/emulator
./gradlew test                   # run JVM unit tests (app/src/test)
./gradlew connectedAndroidTest   # run instrumented tests (needs device/emulator)
./gradlew lint                   # Android lint
./gradlew :app:testDebugUnitTest --tests "com.rastislavkish.vscan.core.ProviderTest"  # single test class
```

`local.properties` must point `sdk.dir` at the Android SDK. The only module is `:app`. Dependency versions are centralized in `gradle/libs.versions.toml`. Note the JitPack dependency `RtkKotlinAndroid` (author's own library, provides `Sound`, `TouchWrapper`, gesture helpers).

JVM unit tests live in `app/src/test`, mirroring the main package layout. Pure-Kotlin logic is tested with plain JUnit; classes that need Android (e.g. the SharedPreferences-backed managers, which take a `SharedPreferences` in their constructor) run under Robolectric (`@RunWith(RobolectricTestRunner::class)`, context via `ApplicationProvider`). minSdk is 26.

## Architecture

### The config-centric model

The central domain concept is a **Config** (`core/Config.kt`) — the README calls these "cognitive tools." A Config bundles everything needed for one scanning purpose: camera choice, flashlight mode, the system/user prompts, the model, max completion tokens, and reasoning effort. The app ships two immutable built-in configs created in code by `ConfigManager`:

- **Base** (id `base`): general "What's in the image?" using `vscan-gpt-4o`.
- **File description** (id `file-description`): generates filenames for saved images, using `vscan-gpt-4o-mini`.

These two cannot be edited; users derive their own configs from them. `ConfigManager.loadImplementation()` always re-injects fresh copies of both on load, so editing their definitions in code is how you change the built-in behavior.

Configs and providers have human-readable string ids derived from their name by `Ids.generate()` (`"Bus lines"` → `bus-lines`, deduplicated with `-2`, `-3`, …, never purely numeric). The id is assigned on creation and does not change on rename. `ConfigManager.isBuiltInConfigId()` identifies the built-ins.

`Config` is an immutable `@Serializable` data class with hand-written `withX()` copy methods (one per field). When adding a field to Config you must update the constructor, every `withX()` method, and any `Conversation`/`TabAdapter` construction sites that copy config fields across.

### The `vscan-` model abstraction

Models are referenced throughout by VScan-internal identifiers like `vscan-gpt-4o`, never by raw provider model IDs. `core/Model.kt` holds the static catalogue of known models (`Model.presets`) with display metadata (name, author, family, size).

Translation to provider model IDs happens in two layers:

- **Provider presets** (`core/ProviderParams.kt`, `ProviderParams.presets`) — built-in `vscan-id → provider-model-id` maps for known providers (OpenAI, Anthropic, Google, OpenRouter, NanoGPT, Cerebras, …). These live only in code, never in user config.
- **`Provider.models`** (`core/Provider.kt`) — user-defined overrides/additions only. A `Provider` finds its preset at runtime by matching `baseUrl` (`@Transient val preset`).

`Provider.getModelId()` resolves user mapping → preset mapping → the id verbatim (so non-`vscan-` ids pass through, supporting self-hosted / fine-tuned models). `getExtendedModels()`/`getBaseModels()` merge and split the two layers for the editing UI. `Conversation.generateResponse()` throws if the resolved id still starts with `vscan-` (provider has no mapping for it).

When adding a new model, update both `Model.presets` and the relevant provider entries in `ProviderParams.presets`. Because presets are not persisted, updating a preset reaches existing users automatically.

### Three independent persistence stores

State lives in three separate `SharedPreferences` files, each owned by a singleton manager accessed via `getInstance(context)`:

- **`Settings`** (`VScanSettings`) — global toggles and the default/share/file-description config ids, plus the gesture/volume-key `Action` bindings.
- **`ConfigManager`** (`VScanConfigurations`) — the list of configs, JSON-serialized.
- **`ProvidersManager`** (`VScanProviders`) — providers, the default provider, and per-model→provider mappings. In `getProviderForModel()`, a model with no mapping falls back to the default provider, while a model explicitly mapped to `null` has no provider.

All three serialize with `kotlinx.serialization` JSON and call `.commit()` on every mutation. Migrations of old persisted data run on load: `ConfigManager.fixBackwardCompatibility()` string-replaces the raw JSON before decoding, and `ProvidersManager.cleanRedundantProviderModels()` strips preset mappings that older versions copied into `Provider.models`. `StringIdMigration` (run once per process from every manager's `getInstance()`) converts the integer ids of versions ≤ 0.2.x across all stores, backing up replaced values under `<key>_v1` keys. `ConfigManager` decodes with `ignoreUnknownKeys`, so removing a `Config` field needs no migration. Follow these patterns when changing serialized shapes, and note that `Json` omits default values, so legacy data may lack fields entirely.

### Request flow

`core/openai/` is a hand-rolled OpenAI chat-completions client built on Ktor CIO. A **`Conversation`** holds the message list and talks to the provider:

1. Resolves provider via `ProvidersManager.getProviderForModel()` and the model id via `Provider.getModelId()`.
2. Builds a `requests/Request` and serializes with `Json { explicitNulls=false }` so null fields are omitted.
3. POSTs to `$baseUrl/chat/completions` with bearer auth, then manually walks the response `JsonObject` (`error`, `usage`, `choices`) rather than deserializing into a typed model.

The `Message` hierarchy (`Message`, `TextMessage`, `ImageMessage`, `SystemMessage`, `AssistantMessage`) each `render()` into a `requests/Message`. Images are sent as `LocalImage`, which wraps an `ImageData`. Reasoning effort maps to a string field (`none`/`minimal`/`low`/`medium`/`high`/`xhigh`); a `finish_reason` of `"length"` with empty content is surfaced to the user as "Reasoning exceeded the token limit".

### Images

`core/image/ImageData` is the app's single internal image type. It holds either the original encoded bytes (camera JPEGs, shared files) or a bitmap produced by a transformation, and derives everything lazily: format (`EncodedFormat.detect()` by magic bytes), EXIF `Orientation`, dimensions (upright, without decoding), and the JPEG / base64 encodings. `encoded`/`encodedFormat` pass the original bytes through when providers accept the format (JPEG, PNG, WebP, GIF) and re-encode to JPEG otherwise. `crop()` (region decoding via `BitmapRegionDecoder`, with a full-decode fallback) and `scaled()`/`decode(maxDimension)` (subsampled decoding) work in upright coordinates and never upscale. Avoid holding full-resolution bitmaps around; a 12 MP one takes ~48 MB.

The smartphone camera is owned by the app-wide singleton `core/camera/PhoneCamera`, not by any screen. It captures at the highest available 4:3 resolution, binds CameraX to `ProcessLifecycleOwner` (so it is available while the app is in the foreground), tracks the device orientation itself, and exposes `suspend fun capture(CaptureSettings): ImageData`, rebinding whenever the requested camera differs from the current binding. Until lenses handle downscaling, `LocalImage` caps sent images at a 2048 px longer side; `Conversation` renders requests on `Dispatchers.Default` since that may decode images. Failures surface as `CaptureException` with a user-facing message.

Under Robolectric, image tests need `@GraphicsMode(GraphicsMode.Mode.NATIVE)`. Robolectric doesn't implement `BitmapRegionDecoder`, so that path is covered by the instrumented `ImageDataInstrumentedTest`.

### UI structure

`MainActivity` hosts a single-activity nav-graph with a `BottomNavigationView` and four fragments: **ScanFragment** (the heart — capture, action dispatch), **ConfigListFragment**, **OptionsFragment** (full editor for the active config), **ConversationFragment**. Swipes navigate between tabs (handled by `RtkKotlinAndroid`'s `TouchWrapper`). All other screens (`ui/<name>activity/`) are separate `Activity`s launched for results, each with paired `...ActivityInput`/`...ActivityOutput` data classes for typed intent extras.

**`TabAdapter`** (`ui/mainactivity/TabAdapter.kt`) is the shared in-memory state across fragments — a singleton holding `activeConfig`, the current `Conversation`, and the last captured image. **Everything in TabAdapter must be accessed while holding its `mutex`** (a coroutine `Mutex`); the codebase wraps every access in `adapter.mutex.withLock { ... }`. Coroutines use `Dispatchers.Main` + a per-fragment `Job` cancelled in `onDestroy`.

Device input (volume keys) is delivered from `MainActivity.onKeyDown` to `ScanFragment` via **EventBus** (`DeviceInputEvent`). `ScanFragment.performAction()` maps an `Action` (sealed class in `core/Action.kt`) to a behavior, letting users bind hardware buttons / shake to scan/ask/consult. Sharing an image into the app (`ACTION_SEND`) routes through `ShareBox` (a singleton hand-off buffer) and is auto-consulted with the share config on resume.

The app is built accessibility-first (TalkBack): results are announced via `Toast`, prompts can be set by voice via `STT` (long-press the prompt buttons), and screens are locked to portrait.

## Code style conventions

This codebase has a distinctive, consistent style — match it:

- **Closing braces are indented to the body level**, not aligned with the opening statement. E.g.:
  ```kotlin
  fun addMessage(message: Message) {
      messages.add(message)
      }
  ```
- **No spaces around `=`** in assignments/declarations or around `==`: `val id=getFreeId()`, `if (instance==null)`.
- Imports are grouped with blank lines by origin (androidx, kotlinx, project), not alphabetized.
- Singletons use the `companion object { private var instance; fun getInstance(context) }` pattern backed by SharedPreferences.
- Prefer immutable `@Serializable` data classes with explicit `withX()` copy methods over mutation.

Source files carry a GPLv3 header; keep it on new files.
