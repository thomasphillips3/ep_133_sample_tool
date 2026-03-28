# Architecture Research

**Domain:** Native mobile apps — real-time USB MIDI control + file management (iOS SwiftUI + Android Compose)
**Researched:** 2026-03-27
**Confidence:** HIGH (based on existing Android codebase + verified Apple/Android documentation)

---

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────┐
│                      UI Layer                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │  Pads    │  │  Beats   │  │  Sounds  │  │ Projects │    │
│  │  Screen  │  │  Screen  │  │  Screen  │  │  Screen  │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       │  (observe StateFlow / @Observable)        │          │
│  ┌────┴─────┐  ┌────┴─────┐  ┌────┴─────┐  ┌────┴─────┐    │
│  │  Pads    │  │  Beats   │  │  Sounds  │  │ Projects │    │
│  │ ViewModel│  │ViewModel │  │ViewModel │  │ViewModel │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
├───────┴──────────────┴─────────────┴──────────────┴──────────┤
│                    Domain Layer                               │
│  ┌───────────────┐  ┌────────────────┐  ┌────────────────┐  │
│  │ MIDIRepository│  │SequencerEngine │  │ ProjectManager │  │
│  │ (typed MIDI   │  │ (step seq,     │  │ (save/load/    │  │
│  │  ops + state) │  │  drift-comp.)  │  │  share/backup) │  │
│  └───────┬───────┘  └────────────────┘  └───────┬────────┘  │
│          │                                       │           │
├──────────┴───────────────────────────────────────┴───────────┤
│                  Platform Abstraction Layer                   │
│  ┌─────────────────────────────┐  ┌─────────────────────┐   │
│  │     MIDIPort (interface)    │  │  FileStore           │   │
│  │  Android: MIDIManager       │  │  (internal sandbox   │   │
│  │  iOS: MIDIManager (CoreMIDI)│  │   + SAF/DocPicker)   │   │
│  └─────────────────────────────┘  └─────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                    Hardware / OS Layer                        │
│   USB MIDI device    |   Filesystem   |   Shared Files       │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Platform |
|-----------|----------------|----------|
| `{Feature}Screen` | Render state, dispatch user events — no logic | Android Compose / iOS SwiftUI |
| `{Feature}ViewModel` | Translate domain state to UI state; coordinate domain calls | Android (StateFlow) / iOS (@Observable @MainActor) |
| `MIDIRepository` | Typed MIDI API: noteOn/Off, CC, PC, loadSoundToPad; device state as stream | Both (separate implementations) |
| `SequencerEngine` | 16-step sequencer, BPM-based drift-compensated timing, LIVE capture | Both (separate implementations) |
| `ChordPlayer` | Chord progression playback over MIDI | Both (separate implementations) |
| `ProjectManager` | Save/load project state as files; enumerate project library; export/import | Both (separate implementations) |
| `MIDIPort` (interface) | Abstract USB MIDI send/receive from business logic for testability | Android (defined); iOS (define matching protocol) |
| `MIDIManager` | USB MIDI device discovery, hot-plug, send/receive via platform API | Android (`android.media.midi`); iOS (CoreMIDI) |
| `FileStore` | Abstract filesystem access — internal app storage + user-facing document access | Both (separate implementations) |
| `WebView fallback` | Host `data/index.html` for sample management operations not yet in native UI | Both (existing) |

---

## Recommended Project Structure

### iOS — New Native UI Structure (mirrors Android)

```
iOSApp/EP133SampleTool/
├── App/
│   ├── EP133SampleToolApp.swift    # @main, create dependencies, inject env
│   ├── ContentView.swift           # Root: TabView or NavigationStack
│   └── Assets.xcassets/
├── MIDI/
│   ├── MIDIPort.swift              # Protocol (mirrors Android MIDIPort interface)
│   └── MIDIManager.swift           # CoreMIDI implementation (existing, keep)
├── Domain/
│   ├── MIDIRepository.swift        # Typed MIDI ops; device state as AsyncStream
│   ├── SequencerEngine.swift       # Step sequencer with drift-compensated timing
│   ├── ChordPlayer.swift           # Chord progression playback
│   ├── ProjectManager.swift        # Save/load/export/import project files
│   └── Model/
│       ├── EP133.swift             # Pad, EP133Sound, Scale — loaded from shared/ JSON
│       ├── SeqState.swift          # Sequencer state model
│       └── ProjectFile.swift       # Project serialization model
├── UI/
│   ├── Pads/
│   │   ├── PadsScreen.swift
│   │   └── PadsViewModel.swift
│   ├── Beats/
│   │   ├── BeatsScreen.swift
│   │   └── BeatsViewModel.swift
│   ├── Sounds/
│   │   ├── SoundsScreen.swift
│   │   └── SoundsViewModel.swift
│   ├── Projects/
│   │   ├── ProjectsScreen.swift
│   │   └── ProjectsViewModel.swift
│   ├── Device/
│   │   ├── DeviceScreen.swift
│   │   └── DeviceViewModel.swift
│   └── Components/               # Shared composable UI atoms
├── WebView/
│   ├── EP133WebView.swift          # WKWebView + polyfill (existing, keep as fallback)
│   └── MIDIBridge.swift            # JS bridge (existing, keep for WebView path)
└── Resources/                     # Bundle resources, shared JSON
```

### Android — Additions to Existing Structure

```
AndroidApp/.../sampletool/
├── MainActivity.kt                 # Existing — add ProjectManager init
├── domain/
│   ├── midi/
│   │   ├── MIDIRepository.kt       # Existing
│   │   └── ChordPlayer.kt          # Existing
│   ├── sequencer/
│   │   └── SequencerEngine.kt      # Existing
│   ├── project/                    # NEW
│   │   ├── ProjectManager.kt       # Save/load/export/import project files
│   │   └── ProjectFile.kt          # Project serialization model
│   └── model/                      # Existing — add ProjectFile model
├── midi/
│   ├── MIDIPort.kt                 # Existing interface
│   └── MIDIManager.kt              # Existing implementation
├── ui/
│   ├── pads/                       # Existing
│   ├── beats/                      # Existing
│   ├── sounds/                     # Existing
│   ├── chords/                     # Existing
│   ├── device/                     # Existing
│   └── projects/                   # NEW
│       ├── ProjectsScreen.kt
│       └── ProjectsViewModel.kt
└── webview/                        # Existing — kept as fallback
```

---

## Architectural Patterns

### Pattern 1: MIDIPort Interface / Protocol Abstraction

**What:** Define a `MIDIPort` interface (Android) / `MIDIPort` protocol (iOS) that abstracts USB MIDI hardware from business logic. Domain layer depends only on this contract, never on `MIDIManager` directly.

**When to use:** Always. This already exists on Android and must be replicated on iOS.

**Trade-offs:** Small interface overhead; enormous testability benefit — `TestMIDIRepository` already proves this on Android.

**iOS example:**
```swift
// MIDIPort.swift — mirrors Android MIDIPort.kt exactly
protocol MIDIPort: AnyObject {
    var onMIDIReceived: ((String, [UInt8]) -> Void)? { get set }
    var onDevicesChanged: (() -> Void)? { get set }
    func getUSBDevices() -> DeviceList
    func sendMIDI(portId: String, data: [UInt8])
    func startListening(portId: String)
    func closeAllListeners()
    func prewarmSendPort(portId: String)
    func close()
}
// MIDIManager conforms to MIDIPort; a MockMIDIPort can stand in for tests
```

### Pattern 2: Domain State as Observable Streams

**What:** Domain objects expose state as reactive streams — Android uses `StateFlow`/`SharedFlow`; iOS uses `AsyncStream` fed into `@Observable` ViewModels on `@MainActor`. ViewModels transform domain state into UI-ready state; screens only render, never query domain directly.

**When to use:** Always — especially critical for real-time MIDI data where hardware events arrive on non-main threads.

**Trade-offs:** `AsyncStream` on iOS requires explicit continuation management, but it maps cleanly to CoreMIDI callbacks and avoids Combine's subscription lifecycle complexity.

**iOS ViewModel pattern:**
```swift
@Observable
@MainActor
final class PadsViewModel {
    var deviceState: DeviceState = DeviceState()
    private let midiRepo: MIDIRepository
    private var stateTask: Task<Void, Never>?

    init(midiRepo: MIDIRepository) {
        self.midiRepo = midiRepo
    }

    func onAppear() {
        stateTask = Task {
            for await state in midiRepo.deviceStateStream {
                self.deviceState = state
            }
        }
    }

    func onDisappear() {
        stateTask?.cancel()
        stateTask = nil
    }

    func padTapped(note: Int, velocity: Int) {
        midiRepo.noteOn(note: note, velocity: velocity)
    }
}
```

**Note:** Use `.task {}` modifier in SwiftUI views where possible — it auto-cancels when the view disappears, eliminating the need for `onDisappear` cleanup.

### Pattern 3: MIDI Device Lifecycle Ownership

**What:** A single owner (Activity on Android, `@main` App struct on iOS) holds the `MIDIManager` instance for the process lifetime. ViewModels receive `MIDIRepository` (which wraps `MIDIManager`) as a dependency — they never own the hardware connection. Hot-plug events from the OS are handled at the owner level, which calls `refreshDeviceState()` on `MIDIRepository`.

**When to use:** Always — multiple owners of `MIDIManager` cause duplicate listeners, double-firing callbacks, and port conflicts.

**Android (existing):** `MainActivity` owns `MIDIManager` + `MIDIRepository`. `BroadcastReceiver` for `USB_DEVICE_ATTACHED`/`DETACHED` is registered on the Activity.

**iOS:** `EP133SampleToolApp` (@main) creates `MIDIManager` and `MIDIRepository` once. CoreMIDI notification callbacks call `refreshDeviceState()` on the repository. `ScenePhase` observation handles foreground re-enumeration.

```swift
@main
struct EP133SampleToolApp: App {
    @State private var midiManager = MIDIManager()
    @State private var midiRepo: MIDIRepository
    // ... other domain objects

    init() {
        let manager = MIDIManager()
        let repo = MIDIRepository(midiPort: manager)
        manager.setup()
        _midiManager = State(initialValue: manager)
        _midiRepo = State(initialValue: repo)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(midiRepo)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { midiRepo.refreshDeviceState() }
        }
    }
}
```

### Pattern 4: File Management via ProjectManager

**What:** A dedicated `ProjectManager` domain object handles all project file I/O. It is the only component that touches the filesystem. It exposes a project library as a stream (`StateFlow<List<ProjectFile>>` on Android, `AsyncStream<[ProjectFile]>` on iOS) and provides imperative operations for save/load/delete/export/import.

**When to use:** Any time the app reads or writes project data. Screens never touch files directly.

**Trade-offs:** Extra indirection; worth it for testability and for hiding the platform-specific SAF / `UIDocumentPickerViewController` complexity from the UI layer.

**Project file model (both platforms):**
```
ProjectFile {
    id: UUID
    name: String
    createdAt: Date
    modifiedAt: Date
    payload: Data  // Opaque SysEx blob from EP-133 backup, or JSON state
}
```

**Android:** Files written to `context.filesDir` (internal, always accessible). SAF `ActivityResultContracts.CreateDocument` / `OpenDocument` for user-facing export/import to shared storage.

**iOS:** Files written to `FileManager.default.urls(for: .documentDirectory)`. `fileExporter` / `fileImporter` SwiftUI modifiers for user-facing export/import to Files app. Files in `documentDirectory` are automatically accessible in Files app under "On My iPhone".

---

## Data Flow

### Outbound MIDI (User → EP-133)

```
User taps pad (Screen)
    ↓ calls ViewModel.padTapped(note, velocity)
ViewModel
    ↓ calls midiRepo.noteOn(note, velocity, channel)
MIDIRepository
    ↓ constructs bytes [0x90|ch, note, velocity]
    ↓ calls midiPort.sendMIDI(portId, bytes)
MIDIManager
    ↓ writes to Android MidiInputPort / CoreMIDI MIDIOutputPort
EP-133 hardware
```

### Inbound MIDI (EP-133 → UI)

```
EP-133 hardware sends MIDI bytes
    ↓ Android: MidiReceiver.onSend() / iOS: CoreMIDI input callback
MIDIManager
    ↓ invokes onMIDIReceived(portId, bytes)
MIDIRepository
    ↓ parses bytes → typed MidiEvent
    ↓ emits to SharedFlow<MidiEvent> / AsyncStream<MidiEvent>
ViewModel (subscribed on coroutine / Task)
    ↓ updates @Observable state / StateFlow
Screen re-renders
```

### Real-time MIDI Threading Rules

**Android:**
- `MidiReceiver.onSend()` fires on a binder thread — do NOT update UI directly
- `MIDIRepository` emits to `MutableSharedFlow` (thread-safe); Compose observes via `collectAsState()` on main thread
- `SequencerEngine` runs on `Dispatchers.Default`; sends are thread-safe through `MIDIPort`

**iOS:**
- CoreMIDI input callback fires on an internal MIDI thread — do NOT update UI or call `evaluateJavaScript` directly
- Wrap callback with `AsyncStream.Continuation` and consume on `@MainActor` Task
- Or: dispatch to `DispatchQueue.main.async` before calling `onMIDIReceived` (simpler, matches existing `MIDIManager` pattern)
- SequencerEngine: use a `Task` with `Clock.sleep` on a background task group; send MIDI synchronously from that task

### Project Save/Load Flow

```
User taps "Save Project" (Screen)
    ↓
ProjectViewModel.saveProject(name)
    ↓
ProjectManager.save(name: name, data: currentState)
    ↓ serializes to JSON / SysEx bundle
    ↓ writes to documentDirectory/{name}.ep133project
    ↓ emits updated projectLibrary stream
ProjectsScreen updates project list
```

### State Management Summary

| Platform | State Type | Real-time MIDI | Device State | UI Binding |
|----------|------------|----------------|--------------|------------|
| Android | `StateFlow` | `SharedFlow<MidiEvent>` | `StateFlow<DeviceState>` | `collectAsState()` |
| iOS | `AsyncStream` / `@Observable` | `AsyncStream<MidiEvent>` | stored on `@Observable` ViewModel | automatic with `@Observable` |

---

## Scaling Considerations

This is a single-device, offline app — scaling concerns are device performance, not server load.

| Concern | Current Scale | Risk at Full Feature Parity |
|---------|--------------|----------------------------|
| MIDI throughput | ~4 notes/step @ 120 BPM | Low — EP-133 is limited MIDI device |
| UI re-render rate | Sequencer fires every ~125ms @ 120 BPM | Low — Compose/SwiftUI batch efficiently |
| Project file size | EP-133 full backup ~100-400KB SysEx | Low — filesystem is plenty fast |
| Asset loading | 999 sounds JSON parsed once at startup | Low once, bad if reloaded on each screen |

**First bottleneck:** JSON asset loading (ep133-sounds.json with 999 entries). Parse once into a shared singleton at app startup, not in ViewModel `init`. Already handled correctly on Android via `SoundsViewModel`.

**Second bottleneck (potential):** Sequencer timing accuracy on iOS. Android uses `System.nanoTime()` with drift compensation. iOS should use `ContinuousClock` (Swift 5.7+) for the same pattern — avoid `Thread.sleep` and `DispatchQueue.asyncAfter` for timing-critical loops.

---

## Anti-Patterns

### Anti-Pattern 1: Multiple MIDIManager Instances

**What people do:** Create a new `MIDIManager` / `MIDIRepository` in each ViewModel or screen.

**Why it's wrong:** CoreMIDI client setup is expensive. Multiple instances register duplicate listeners, causing double-fired callbacks, duplicate MIDI events in the UI, and port conflict errors. On Android, `android.media.midi` port handles are not safe to open multiple times.

**Do this instead:** Create exactly one `MIDIManager` and one `MIDIRepository` at app startup (in `@main` / `MainActivity`). Pass `MIDIRepository` as a dependency to all ViewModels — either via constructor injection or SwiftUI `Environment`.

### Anti-Pattern 2: Updating UI State on the MIDI Thread

**What people do:** Call `mutableState.value = newValue` directly inside the CoreMIDI input callback or `MidiReceiver.onSend()`.

**Why it's wrong:** These callbacks fire on non-main threads. Compose `StateFlow` collectors and `@Observable` property setters that trigger UI updates must run on the main thread. Doing otherwise causes threading crashes or silent data races in Swift 6 strict concurrency mode.

**Do this instead:** Android — use `MutableSharedFlow.tryEmit()` (thread-safe, non-suspending) inside the callback; let the collecting coroutine on main thread perform state updates. iOS — wrap the CoreMIDI callback with `AsyncStream.Continuation.yield()` and `await` the stream on a `@MainActor` Task.

### Anti-Pattern 3: Blocking the Sequencer Loop for MIDI Sends

**What people do:** Open MIDI ports or resolve device IDs inside the timing loop on each step.

**Why it's wrong:** Port lookup and open operations are slow (involve JNI / system calls). Even 2-5ms of jitter in the sequencer loop is audible as timing drift.

**Do this instead:** Pre-warm the output port before playback starts (Android `MIDIManager.prewarmSendPort()` already implements this). On iOS, resolve the `MIDIEndpointRef` once when the device connects and cache it. The sequencer loop then only calls the pre-resolved send path.

### Anti-Pattern 4: Hardcoding File Paths or Using External Storage Directly

**What people do:** Write project files to `Environment.getExternalStorageDirectory()` on Android or `NSTemporaryDirectory()` on iOS.

**Why it's wrong:** External storage requires runtime permissions (Android) or is inaccessible to the user (iOS temp dir). External storage is also not included in Android's backup system and is not private to the app.

**Do this instead:** Write to internal `filesDir` / `documentDirectory` as the primary store. Use Storage Access Framework (Android) / `fileExporter` + `fileImporter` (iOS) only when the user explicitly wants to export/import to a shared location. This keeps the project library always accessible without permissions.

### Anti-Pattern 5: Sharing Kotlin Domain Layer via KMP

**What people do:** Extract the Android domain layer into a KMP shared module to reuse on iOS.

**Why it's wrong for this project:** The domain logic is tightly coupled to platform MIDI APIs through the `MIDIPort` interface. The `SequencerEngine` uses `Dispatchers.Default`, `System.nanoTime()`, and Kotlin coroutine semantics that don't translate cleanly to Swift actors and `ContinuousClock`. KMP's Swift interop (via Objective-C headers today, Swift Export still experimental in 2026) adds a bridging layer over an already-bridged MIDI system. Setup cost exceeds benefit for a two-person scope project; the domain layer is small enough (~5 files) to maintain separately. **Verdict: keep iOS and Android domain layers separate; share protocol data via `shared/` JSON only.**

---

## Integration Points

### External: EP-133 Hardware

| Interface | Integration Pattern | Notes |
|-----------|---------------------|-------|
| MIDI Note On/Off | Direct USB MIDI bytes via `MIDIPort.sendMIDI()` | Channel 0-3 maps to pad banks |
| MIDI CC + PC | Combined byte arrays for sound loading | Must preserve ordering — send as single array |
| MIDI SysEx | Via WebView fallback (`data/index.js`) | Not replicated in native UI yet; backup/restore uses this |
| USB connection | OS-level hot-plug callbacks | Requires `USB_HOST` feature flag (Android), no entitlement needed (iOS) |

### Internal: Cross-Component Boundaries

| Boundary | Communication | Direction | Notes |
|----------|---------------|-----------|-------|
| Screen ↔ ViewModel | State observation + event callbacks | Screen reads VM state; calls VM methods | Screens hold no logic |
| ViewModel ↔ Domain | Direct method calls + stream subscription | VM calls domain; subscribes to domain state | VM does not hold domain state |
| Domain ↔ MIDIPort | Interface/protocol calls + callbacks | Bidirectional (send out, receive in) | Interface boundary enables testing |
| Domain ↔ FileStore | Direct calls (sync or async) | Domain calls FileStore | FileStore abstracts platform paths |
| Native UI ↔ WebView | Activity/Sheet launch (Android); NavigationLink/sheet (iOS) | One-way navigation | WebView is a separate screen, not embedded |

### Shared Data: `shared/` JSON Protocol Files

Both platforms load `ep133-pads.json`, `ep133-sounds.json`, `ep133-scales.json` from bundled assets at runtime. This is the single source of truth for EP-133 protocol data.

**Android:** Gradle `copyEP133Data` task copies files to `assets/` at build time. Read via `context.assets.open("ep133-sounds.json")`.

**iOS:** JSON files added as bundle resources in Xcode. Read via `Bundle.main.url(forResource: "ep133-sounds", withExtension: "json")`.

**Rule:** Never hardcode MIDI note numbers or sound names in application code — always derive from these JSON files.

---

## Build Order Implications

The layered architecture implies a strict build-from-bottom-up order:

1. **MIDI Layer (MIDIPort protocol + MIDIManager)** — Foundation everything else rests on. iOS needs `MIDIPort` Swift protocol defined before domain work begins.

2. **Domain Layer (MIDIRepository, SequencerEngine, ChordPlayer)** — Depends only on `MIDIPort`. Can be developed and unit-tested against a `MockMIDIPort` before real hardware is needed.

3. **Project Management (ProjectManager, FileStore)** — Independent of MIDI. Can be built and tested without a connected EP-133.

4. **UI Screens (one feature at a time)** — Each screen depends on its ViewModel; ViewModel depends on domain. Build order within UI layer: Pads (simplest MIDI) → Device (connection state) → Sounds (protocol data) → Beats (sequencer) → Projects (file I/O).

5. **WebView fallback** — Already exists; keep as is. Only integrate once native screens have parity with the specific feature.

---

## Sources

- Android `MIDIPort` interface: `/AndroidApp/app/src/main/java/com/ep133/sampletool/midi/MIDIPort.kt` (existing codebase)
- Android `MIDIRepository`: `/AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/MIDIRepository.kt` (existing codebase)
- Android `SequencerEngine`: `/AndroidApp/app/src/main/java/com/ep133/sampletool/domain/sequencer/SequencerEngine.kt` (existing codebase)
- iOS `MIDIManager.swift`: `/iOSApp/EP133SampleTool/MIDI/MIDIManager.swift` (existing codebase)
- Apple SwiftUI `@Observable` macro: [Migrating from ObservableObject to Observable macro](https://developer.apple.com/documentation/SwiftUI/Migrating-from-the-observable-object-protocol-to-the-observable-macro) — HIGH confidence
- `@Observable` macro performance vs `ObservableObject`: [Antoine van der Lee — @Observable Macro performance increase](https://www.avanderlee.com/swiftui/observable-macro-performance-increase-observableobject/) — HIGH confidence
- `AsyncStream` and Actors pattern: [Advanced Swift Concurrency: AsyncStream, Actor, async let](https://medium.com/@mrhotfix/advanced-swift-concurrency-combining-asyncstream-actor-async-let-in-real-time-swiftui-apps-b2bd5d123d6e) — MEDIUM confidence
- SwiftUI file export/import: [File importing and exporting in SwiftUI — Swift with Majid](https://swiftwithmajid.com/2023/05/10/file-importing-and-exporting-in-swiftui/) — HIGH confidence
- Android Storage Access Framework with Compose: [SAF in Jetpack Compose](https://hahouari.medium.com/storage-access-framework-saf-in-jetpack-compose-69b440cef8fa) — MEDIUM confidence
- KMP vs separate native: [KMP Architecture Best Practices — Carrion.dev](https://carrion.dev/en/posts/kmp-architecture/) — MEDIUM confidence; verdict to keep separate is HIGH confidence given the MIDI coupling
- `ScenePhase` lifecycle: [SwiftUI app lifecycle issues — Jesse Squires 2024](https://www.jessesquires.com/blog/2024/06/29/swiftui-scene-phase/) — HIGH confidence
- MIDIKit (reference, not adopted): [orchetect/MIDIKit on GitHub](https://github.com/orchetect/MIDIKit) — noted as an alternative iOS CoreMIDI wrapper; not recommended here because `MIDIManager.swift` already provides sufficient coverage and adding a dependency for a thin wrapper is unnecessary overhead

---

*Architecture research for: native mobile MIDI + file management (iOS SwiftUI + Android Compose)*
*Researched: 2026-03-27*
