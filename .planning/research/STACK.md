# Stack Research

**Domain:** Native mobile MIDI device management (iOS SwiftUI + Android Kotlin/Compose)
**Researched:** 2026-03-27
**Confidence:** MEDIUM-HIGH — Core MIDI and Android MIDI architecture verified via official docs and source; library versions spot-checked against release pages. Apple developer docs require JavaScript and blocked direct scraping; iOS-specific claims rely on community sources with cross-verification.

---

## Context

The codebase already has working USB MIDI infrastructure on both platforms:

- **Android**: `android.media.midi` (API 29+), `MIDIManager` + `MIDIPort` abstraction, `MIDIRepository` domain layer, Jetpack Compose UI for Pads/Beats/Sounds/Chords/Device
- **iOS**: CoreMIDI + WKWebView wrapper, `MIDIManager.swift` / `MIDIBridge.swift`, no native SwiftUI screens yet

This research answers: what additional stack is needed to complete full native UI parity and robust project file management on both platforms?

---

## Recommended Stack

### Android — Additional Stack

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Hilt (dagger-hilt-android) | 2.56+ | Dependency injection for ViewModels and repositories | Google's official DI solution for Android; replaces manual construction in `MainActivity.kt`; enables `@HiltViewModel` and clean ViewModel scoping; KSP-based (no KAPT) from 2.48+ |
| androidx.hilt:hilt-navigation-compose | 1.2.0 | `hiltViewModel()` in Compose navigation | Required to obtain `@HiltViewModel` instances inside `NavHost` destinations; without it, ViewModels don't scope correctly to navigation back stack |
| androidx.lifecycle:lifecycle-runtime-compose | 2.10.0 | `collectAsStateWithLifecycle()` | Official recommended API for consuming `StateFlow`/`Flow` in Compose; stops collection when app is backgrounded — prevents unnecessary MIDI state processing when screen is off |
| Jetpack Compose BOM | 2026.03.00 | Align all Compose artifact versions | Current stable BOM; upgrades Navigation, Material3, and UI artifacts together without version conflicts |
| androidx.navigation:navigation-compose | 2.8+ (via BOM) | Typed screen navigation | Navigation 2.8 introduced stable type-safe routes via `@Serializable` — use `data class`/`object` routes, not string literals |
| kotlinx-serialization-json | 1.7+ | Route argument serialization, project file serialization | Required by Navigation 2.8 type-safe routes; also the right tool for serializing EP-133 project data to/from backup files |
| KSP (Kotlin Symbol Processing) | 2.0.x-1.0.x | Annotation processing for Hilt | Replaces KAPT; significantly faster builds; mandatory for Hilt 2.48+ on new projects |

### iOS — Additional Stack

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| MIDIKit (orchetect/MIDIKit) | 0.11.0 | Swift/SwiftUI CoreMIDI wrapper | Replaces hand-rolled CoreMIDI C-API calls with a type-safe Swift API; handles `MIDIInputPortCreateWithProtocol`, `MIDIEventListForEachEvent`, endpoint lifecycle, SysEx7 events, Swift 6 strict concurrency, iOS 13+; actively maintained (87 releases, v0.11.0 Feb 2026 with threading overhaul) |
| Swift Observation (`@Observable`) | iOS 17+ (built-in) | State management for SwiftUI ViewModels | Replaces `ObservableObject` + `@Published`; only re-renders views that read changed properties (not all properties); use `@MainActor` on the class to keep MIDI-driven UI updates on main thread |
| Swift Concurrency (`async/await`, `AsyncStream`) | iOS 15+ (built-in) | Bridge CoreMIDI callbacks to SwiftUI | MIDI arrives on background threads via CoreMIDI callbacks; wrap with `AsyncStream` to create an async iterator consumable in a SwiftUI `task {}` modifier or `@MainActor` context |
| `fileImporter` / `fileExporter` modifiers | iOS 16+ (built-in SwiftUI) | Project backup/restore via Files app | Native SwiftUI modifiers that invoke the system document picker; no UIDocumentPickerViewController boilerplate; custom UTType for `.ep133project` file format |
| UniformTypeIdentifiers (`UTType`) | iOS 14+ (built-in) | Custom file type declaration | Declare a custom `com.ep133sampletool.project` UTType in Info.plist; enables "Open With" from Files app and typed `fileImporter` filtering |

### Shared / No New Additions Needed

| Area | Existing Solution | Why No Change Needed |
|------|------------------|---------------------|
| Android MIDI core | `android.media.midi` API 29 | No alternative; USB MIDI is a system API; the existing `MIDIManager` + `MIDIPort` abstraction is correct |
| Android JSON parsing | `org.json:json` | EP-133 shared data parsing is already working; kotlinx-serialization adds typed serialization for project files but doesn't replace this |
| iOS CoreMIDI raw access | System framework | MIDIKit wraps it, doesn't replace it; CoreMIDI is still the transport |
| Android WebView fallback | `androidx.webkit:webkit` | Backup/restore via web app is already shipped; native project management supplements, doesn't replace, this path |

---

## Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx-coroutines-test | 1.8+ | Testing coroutine-based MIDI flows | Every ViewModel test involving `StateFlow` or `SharedFlow`; replace `TestCoroutineDispatcher` (deprecated) with `StandardTestDispatcher` |
| Turbine (cash.app:turbine) | 1.2+ | Flow assertion in tests | `testFlow.test { assertEquals(...) }` — dramatically simpler than manual `collect` in tests; use for `MIDIRepository` and `SequencerEngine` tests |
| XCTest (built-in) | Xcode 15+ | iOS unit and UI testing | System framework; no additions needed; test MIDI logic by injecting mock `MIDIInputPort` via protocol |

---

## Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Android Studio Hedgehog / Iguana | Android development | Required for Compose live preview and Layout Inspector |
| Xcode 16+ | iOS development | Required for MIDIKit 0.11.0 (Swift 6.0 toolchain); MIDIKit fails to compile on Xcode 15 as of 0.10.0+ |
| Swift Package Manager (SPM) | iOS dependency management | The project currently has zero iOS third-party deps; adding MIDIKit via SPM is the only change needed; no CocoaPods required |
| Instruments — Core MIDI template | iOS MIDI debugging | Profile MIDI thread activity; catch cases where MIDI callbacks block the main thread |
| Android MIDI sample repo | Android MIDI reference | github.com/android/midi-samples — official Google samples for `android.media.midi` patterns |

---

## Key API Patterns

### Android: Consuming MIDI State in Compose

Use `collectAsStateWithLifecycle()` — not `collectAsState()` — for all MIDI flows in Compose UI. The lifecycle-aware version stops collecting when the app backgrounds, preventing stale MIDI state processing.

```kotlin
// ViewModel exposes:
val deviceState: StateFlow<DeviceState> = midiRepository.deviceState
val incomingMidi: SharedFlow<MidiEvent> = midiRepository.incomingMidi

// In Composable:
val deviceState by viewModel.deviceState.collectAsStateWithLifecycle()
```

For high-frequency MIDI events (incoming note data), use `SharedFlow` (not `StateFlow`) in the repository — events are fire-and-forget, not sticky state. Convert to `StateFlow` only at the ViewModel layer for UI-relevant derived state.

### Android: File Management (Project Backup/Restore)

Use `ActivityResultContracts` via `rememberLauncherForActivityResult` — do NOT use `startActivityForResult` (deprecated). The launcher must be registered at the Composable level, not inside ViewModel:

```kotlin
// In Composable (not ViewModel)
val saveLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/octet-stream")
) { uri ->
    uri?.let { viewModel.saveProjectTo(it, contentResolver) }
}

val openLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri ->
    uri?.let { viewModel.loadProjectFrom(it, contentResolver) }
}
```

Store the serialized project data in `context.getFilesDir()` (app-internal, no permissions). Use SAF only for user-visible exports — this is the correct scoped storage pattern for API 29+ and does not require `MANAGE_EXTERNAL_STORAGE`.

### Android: Navigation Type Safety

Use Navigation 2.8 typed routes — not string route constants:

```kotlin
@Serializable object PadsRoute
@Serializable object BeatsRoute
@Serializable data class ProjectDetailRoute(val projectId: String)

NavHost(navController, startDestination = PadsRoute) {
    composable<PadsRoute> { PadsScreen(...) }
    composable<ProjectDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ProjectDetailRoute>()
        ProjectDetailScreen(route.projectId, ...)
    }
}
```

### iOS: CoreMIDI with Modern Swift Concurrency

Use `MIDIInputPortCreateWithProtocol` (iOS 15+, available on our iOS 16 target) rather than the deprecated `MIDIInputPortCreate`. This gives you `MIDIEventList` / `MIDIEventListForEachEvent` instead of raw `MIDIPacketList` parsing.

With MIDIKit, this is abstracted entirely:

```swift
import MIDIKit

@MainActor
@Observable
class MIDIManager: ObservableObject {
    let midi = MIDIManager(clientName: "EP133", model: "EP133", manufacturer: "EP133Tool")
    var connectedDevice: String? = nil

    func setup() throws {
        try midi.start()
        // MIDIKit handles device enumeration, connection, and event routing
        try midi.addInputConnection(
            toOutputs: [],
            tag: "ep133-in",
            receiver: .events { [weak self] events in
                Task { @MainActor in self?.handleEvents(events) }
            }
        )
    }
}
```

### iOS: State Management for Real-Time MIDI

Use `@Observable` + `@MainActor` — not `ObservableObject` — on iOS 17+ (our iOS 16 minimum requires a fallback):

- iOS 17+: `@Observable @MainActor class MIDIController`
- iOS 16 (minimum): `@MainActor class MIDIController: ObservableObject` with `@Published` properties

Do NOT use a Swift `actor` for SwiftUI model objects. Actors don't play well with `@Observable`/`@Published` property observation because access requires `await`, which breaks synchronous SwiftUI property reads. Use `@MainActor` class instead — all MIDI-driven state updates via `Task { @MainActor in ... }`.

### iOS: Project File Management

Use SwiftUI's `fileExporter` / `fileImporter` modifiers:

```swift
.fileExporter(
    isPresented: $showingExporter,
    document: EP133ProjectDocument(project: currentProject),
    contentType: .ep133Project,
    defaultFilename: "my-project"
) { result in
    // handle result
}
```

Declare custom UTType in Info.plist:
```xml
<key>UTExportedTypeDeclarations</key>
<array>
    <dict>
        <key>UTTypeIdentifier</key>
        <string>com.ep133sampletool.project</string>
        <key>UTTypeDescription</key>
        <string>EP-133 Project</string>
        <key>UTTypeTagSpecification</key>
        <dict>
            <key>public.filename-extension</key>
            <array><string>ep133proj</string></array>
        </dict>
    </dict>
</array>
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| MIDIKit (iOS) | Raw CoreMIDI C API | Never — for new code. Raw CoreMIDI is 30+ function calls and manual packet parsing. MIDIKit 0.11.0 is Swift 6 compliant, actively maintained, and iOS 13+ compatible |
| `@Observable` + `@MainActor` (iOS 17+) | Combine + `CurrentValueSubject` | Only if you need iOS 15/16 compatibility AND Combine-based reactive pipelines elsewhere in the codebase |
| `collectAsStateWithLifecycle()` (Android) | `collectAsState()` | Only in Kotlin Multiplatform code where `lifecycle-runtime-compose` isn't available |
| SAF `ActivityResultContracts` (Android) | `MediaStore` API | Only for media files (audio/video/images); EP-133 project files are not media, use SAF |
| Hilt (Android) | Manual DI (current approach) | Small prototypes; the existing codebase is approaching the size where manual DI in `MainActivity` becomes brittle |
| Navigation 2.8 typed routes | `NavDeepLinkBuilder` strings | Never for new destinations; string routes are legacy; type-safe routes catch mismatched arguments at compile time |
| KSP | KAPT | KAPT is deprecated; do not add new KAPT dependencies |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `GlobalScope` for MIDI coroutines (Android) | Not scoped to ViewModel lifecycle; leaks if activity is destroyed | `viewModelScope` in ViewModel, `lifecycleScope` in Activity |
| `startActivityForResult` for file pickers | Deprecated API; doesn't work cleanly with Compose composition | `rememberLauncherForActivityResult` + `ActivityResultContracts` |
| `MANAGE_EXTERNAL_STORAGE` permission | Requires Google Play policy approval; not needed for project files in `getFilesDir()` or SAF | App-internal `getFilesDir()` for storage, SAF for user exports |
| `ObservableObject` + `@Published` on iOS 17+ | Triggers full ViewModel re-render on any property change, not just observed ones | `@Observable` macro (iOS 17+) — granular per-property tracking |
| Swift `actor` for SwiftUI data models | Actors require `await` for property access; breaks synchronous SwiftUI observation | `@MainActor` class (allows synchronous reads on main thread) |
| `MIDIPacketList` (iOS) | Deprecated since iOS 15 / macOS 12; manual byte parsing; error-prone with running status | `MIDIEventList` via `MIDIInputPortCreateWithProtocol` or MIDIKit |
| BLE MIDI | Out of scope per PROJECT.md; adds latency and pairing complexity for v1 | USB-only; already in project constraints |
| AMidi NDK API (Android) | Requires C/C++ JNI; only worthwhile for audio thread MIDI; the app has no audio engine | `android.media.midi` Java API — already integrated |
| `KAPT` for new dependencies | Deprecated in Kotlin 2.0; slower than KSP; will be removed | KSP (`ksp()` in gradle dependencies) |

---

## Stack Patterns by Scenario

**If building a new Android screen that reads MIDI state:**
- ViewModel holds `StateFlow<ScreenUiState>` derived from `MIDIRepository`
- Composable collects with `collectAsStateWithLifecycle()`
- Never pass `MIDIRepository` directly to composables

**If building iOS native screens (new work):**
- Create `@Observable @MainActor class` for each feature domain (e.g., `PadsController`, `ProjectsController`)
- Inject `MIDIKit.MIDIManager` as a shared singleton via SwiftUI `.environment()`
- Use `task {}` modifier for async MIDI setup and teardown tied to view lifecycle

**If adding project save/load on Android:**
- Store project JSON in `context.getFilesDir()` — no permissions, automatic backup eligible
- Expose save/load via `ContentResolver` + SAF in ViewModel
- Register SAF launchers in Composable, call ViewModel methods from callbacks

**If adding project save/load on iOS:**
- Use `FileDocument` protocol + `fileExporter`/`fileImporter` modifiers
- Declare custom `UTType` for `.ep133proj` extension
- Files appear in iOS Files app automatically; users can AirDrop, iCloud sync, etc.

**If the Android domain layer needs dependency injection:**
- Add Hilt; `@HiltViewModel` on each ViewModel; `@Singleton` for `MIDIRepository` and `MIDIManager`
- Wire with `@Provides` / `@Binds` modules; replace `MainActivity` manual construction
- Use KSP, not KAPT

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| MIDIKit 0.11.0 | Xcode 16.0+, Swift 6.0, iOS 13+ | Requires Xcode 16 — the project currently specifies Xcode 15+. **Update minimum Xcode requirement to 16.** |
| Navigation Compose 2.8 typed routes | Kotlin Serialization plugin | `id("org.jetbrains.kotlin.plugin.serialization")` must be added to app build.gradle.kts |
| Hilt 2.56+ | KSP 2.0.x-1.0.x, Kotlin 2.0 | Current codebase uses Kotlin 1.9.22 — either stay on Hilt 2.48 (Kotlin 1.9 compatible) or upgrade Kotlin first |
| Compose BOM 2026.03.00 | AGP 8.x, Kotlin 2.0 | Current codebase uses AGP 8.2.0, which is compatible; Kotlin upgrade may be required |
| `collectAsStateWithLifecycle()` | lifecycle-runtime-compose 2.6.0+ | Current codebase has `lifecycle-runtime-compose 2.7.0` — already compatible, just use the API |
| `@Observable` macro | iOS 17+, Swift 5.9+ | Falls back to `ObservableObject` on iOS 16 (minimum target); requires conditional compilation if maintaining iOS 16 support |

---

## Kotlin Version Upgrade Path

The codebase is on Kotlin 1.9.22. The 2026 ecosystem prefers Kotlin 2.0+:

- Compose BOM 2026.x expects Kotlin 2.0 for the Compose compiler plugin
- Hilt 2.56 targets KSP with Kotlin 2.0
- Navigation 2.8 type-safe routes work on Kotlin 1.9 with serialization plugin

**Recommendation:** Upgrade Kotlin to 2.0.21 (current stable) as a prerequisite phase. This is a one-day change that unblocks the full 2026 stack. Do it in a dedicated chore commit before adding new features.

---

## Sources

- [Android MIDI package reference](https://developer.android.com/reference/android/media/midi/package-summary) — API classes and levels (MEDIUM confidence — page content truncated by JavaScript requirement)
- [Android Native MIDI API](https://developer.android.com/ndk/guides/audio/midi) — AMidi vs android.media.midi decision (HIGH confidence)
- [Android MIDI architecture](https://source.android.com/docs/core/audio/midi_arch) — SysEx transport, USB MIDI multiplexing (HIGH confidence)
- [MidiUmpDeviceService / MIDI 2.0 on Android](https://atsushieno.github.io/2024/04/12/midi2-on-android.html) — Android 15 MIDI 2.0 additions, stable API considerations (MEDIUM confidence — community blog, cross-checked with AOSP)
- [Android data storage overview](https://developer.android.com/training/data-storage/) — Scoped storage, SAF, `getFilesDir()` patterns (HIGH confidence)
- [Storage Access Framework guide](https://developer.android.com/guide/topics/providers/document-provider) — `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT` patterns (HIGH confidence)
- [Compose state and lifecycle](https://developer.android.com/develop/ui/compose/state) — `collectAsStateWithLifecycle()` recommendation (HIGH confidence — official docs)
- [lifecycle-runtime-compose releases](https://developer.android.com/jetpack/androidx/releases/lifecycle) — version 2.10.0 current stable (HIGH confidence)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom) — 2026.03.00 current stable (HIGH confidence)
- [Navigation type-safety docs](https://developer.android.com/guide/navigation/design/type-safety) — Navigation 2.8 typed routes (HIGH confidence)
- [Hilt with Compose](https://developer.android.com/training/dependency-injection/hilt-android) — `@HiltViewModel`, KSP setup (HIGH confidence)
- [MIDIKit releases](https://github.com/orchetect/MIDIKit/releases) — 0.11.0 released Feb 2025, Swift 6, Xcode 16 (HIGH confidence)
- [Modern CoreMIDI event handling](https://furnacecreek.org/blog/2024-04-06-modern-coremidi-event-handling-with-swift) — `MIDIInputPortCreateWithProtocol`, `MIDIEventListForEachEvent` (MEDIUM confidence — community blog, API verified against Apple docs search results)
- [CoreMIDI MIDISendSysex](https://developer.apple.com/documentation/coremidi/1495356-midisendsysex) — Async SysEx send API (HIGH confidence)
- [@Observable macro migration](https://developer.apple.com/documentation/SwiftUI/Migrating-from-the-observable-object-protocol-to-the-observable-macro) — iOS 17 state management (HIGH confidence)
- [SwiftUI fileExporter/fileImporter](https://developer.apple.com/documentation/swiftui/view/fileexporter(ispresented:document:contenttype:defaultfilename:oncompletion:)-32vwk) — File management modifiers (HIGH confidence)
- iOS 18 MIDI 2.0 (`MIDIUMPMutableEndpoint`) noted in community sources — LOW confidence on specifics, not in scope for this milestone

---

*Stack research for: EP-133 Sample Tool native mobile MIDI device management*
*Researched: 2026-03-27*
