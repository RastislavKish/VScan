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
./gradlew :app:testDebugUnitTest --tests "com.rastislavkish.vscan.ExampleUnitTest"  # single test class
```

`local.properties` must point `sdk.dir` at the Android SDK. The only module is `:app`. Dependency versions are centralized in `gradle/libs.versions.toml`. Note the JitPack dependency `RtkKotlinAndroid` (author's own library, provides `Sound`, `TouchWrapper`, gesture helpers).

The current test classes are the default Android template stubs — there is no real test suite yet.

## Architecture

### The config-centric model

The central domain concept is a **Config** (`core/Config.kt`) — the README calls these "cognitive tools." A Config bundles everything needed for one scanning purpose: camera choice, resolution, flashlight mode, the system/user prompts, the model, max completion tokens, and reasoning effort. The app ships two immutable built-in configs created in code by `ConfigManager`:

- **Base** (id `-1`): general "What's in the image?" using `vscan-gpt-4o`.
- **File description** (id `-3`): generates filenames for saved images, using `vscan-gpt-4o-mini`.

These two cannot be edited; users derive their own configs from them. `ConfigManager.loadImplementation()` always re-injects fresh copies of both on load, so editing their definitions in code is how you change the built-in behavior.

`Config` is an immutable `@Serializable` data class with hand-written `withX()` copy methods (one per field). When adding a field to Config you must update the constructor, every `withX()` method, and any `Conversation`/`TabAdapter` construction sites that copy config fields across.

### The `vscan-` model abstraction

Models are referenced throughout by VScan-internal identifiers like `vscan-gpt-4o`, never by raw provider model IDs. `core/Model.kt` holds the static catalogue of known models (`Model.presets`) mapping these identifiers to display names. A `Provider` (`core/Provider.kt`) holds a `models: Map<vscanId, providerModelId>` translation table; `Provider.getModelId()` resolves a `vscan-` id to the provider's actual id. If a model id does not start with `vscan-` it is passed through verbatim (supports self-hosted / fine-tuned models). At request time, `Conversation.generateResponse()` throws if the resolved id still starts with `vscan-` (meaning the provider has no mapping for it). When adding new models, update both `Model.presets` and the relevant provider presets in `ui/providerpresetselectionactivity/ProviderParams.kt`.

### Three independent persistence stores

State lives in three separate `SharedPreferences` files, each owned by a singleton manager accessed via `getInstance(context)`:

- **`Settings`** (`VScanSettings`) — global toggles and the default/share/file-description config ids, plus the gesture/volume-key `Action` bindings.
- **`ConfigManager`** (`VScanConfigurations`) — the list of configs, JSON-serialized.
- **`ProvidersManager`** (`VScanProviders`) — providers, the default provider, and per-model→provider mappings.

All three serialize with `kotlinx.serialization` JSON and call `.commit()` on every mutation. `ConfigManager.fixBackwardCompatibility()` shows the pattern for migrating old serialized formats (string-replace on the raw JSON before decoding) — follow it when changing serialized shapes.

### Request flow

`core/openai/` is a hand-rolled OpenAI chat-completions client built on Ktor CIO. A **`Conversation`** holds the message list and talks to the provider:

1. Resolves provider + model id via `ProvidersManager.getProviderForModel()` (falls back to the default provider when a model has no explicit mapping).
2. Builds a `requests/Request` and serializes with `Json { explicitNulls=false }` so null fields are omitted.
3. POSTs to `$baseUrl/chat/completions` with bearer auth, then manually walks the response `JsonObject` (`error`, `usage`, `choices`) rather than deserializing into a typed model.

The `Message` hierarchy (`Message`, `TextMessage`, `ImageMessage`, `SystemMessage`, `AssistantMessage`) each `render()` into a `requests/Message`. Images are base64-encoded and sent as `LocalImage`. Reasoning effort maps to a string field (`none`/`minimal`/`low`/`medium`/`high`/`xhigh`); a `finish_reason` of `"length"` with empty content is surfaced to the user as "Reasoning exceeded the token limit".

### UI structure

`MainActivity` hosts a single-activity nav-graph with a `BottomNavigationView` and four fragments: **ScanFragment** (the heart — camera binding, capture, action dispatch), **ConfigListFragment**, **OptionsFragment** (full editor for the active config), **ConversationFragment**. Swipes navigate between tabs (handled by `RtkKotlinAndroid`'s `TouchWrapper`). All other screens (`ui/<name>activity/`) are separate `Activity`s launched for results, each with paired `...ActivityInput`/`...ActivityOutput` data classes for typed intent extras.

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
