# Phase 1: MIDI Foundation - Context

**Gathered:** 2026-03-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix two latent threading bugs on both platforms, harden the Android USB permission/reconnect flow, define the iOS Swift MIDI protocol layer, and add connection status UI and actionable error states on both platforms.

This phase is infrastructure — no new features, no new screens. Its output is a stable MIDI foundation that Phase 2 (Android device management) and Phase 3 (iOS native UI) can build on without crashing.

</domain>

<decisions>
## Implementation Decisions

### iOS MIDI Library

- **D-01:** Fix the existing `MIDIManager.swift` in place — do NOT adopt MIDIKit 0.11.0 for Phase 1. The two bugs are surgical fixes: (1) dispatch `onMIDIReceived?` to main thread, (2) fix `sendRawBytes` buffer allocation. MIDIKit evaluation deferred to Phase 3 kickoff.
- **D-02:** Adding MIDIKit requires Xcode 16 (current requirement is Xcode 15+). Do not change the Xcode minimum requirement in Phase 1.

### iOS Deployment Target

- **D-03:** Stay at iOS 16 deployment target for Phase 1. Use `ObservableObject` + `@Published` if any observable class is needed. The `@Observable` (iOS 17) vs `ObservableObject` (iOS 16) decision is deferred to Phase 3 planning — Phase 1 establishes the Swift `MIDIPort` protocol layer only, not SwiftUI state.
- **D-04:** Do NOT raise the minimum deployment target as part of Phase 1. That decision requires explicit evaluation before Phase 3 begins.

### Android Threading Fixes

- **D-05:** In `MIDIManager.kt` `startListening()`, the `MidiReceiver.onSend()` callback fires on the MIDI thread. `onMidiReceived?.invoke()` is called directly from there. The fix is to post to `mainHandler` before invoking — consistent with how `notifyDevicesChanged()` already works.
- **D-06:** In `MIDIRepository.kt`, `_incomingMidi.tryEmit()` is already thread-safe for `SharedFlow`. No change needed there. The real risk is `_deviceState.value = ...` mutations in `refreshDeviceState()` — this must be confirmed to run on the main thread or guarded appropriately.

### Android Coroutine Scope Fixes

- **D-07:** `SequencerEngine` must get a `SupervisorJob()` added to its scope and a `close()` method that cancels the scope. `MainActivity.onDestroy()` must call `sequencerEngine.close()`. This is a Phase 1 prerequisite — the scope leak will cause increasingly bad behavior as users navigate between screens.
- **D-08:** `MainActivity`'s manual `CoroutineScope(Dispatchers.Main)` should be replaced with `lifecycleScope`. This is a prerequisite fix, not a refactor.

### Android USB Permission Race

- **D-09:** Remove the 500ms hardcoded delay in `usbPermissionReceiver`. Replace with `MidiManager.DeviceCallback.onDeviceAdded` as the trigger for device enumeration after permission grant. The `DeviceCallback` is already registered (`deviceCallback`) — use it as the authoritative signal.
- **D-10:** USB permission should be requested once and never re-requested for the same device. Cache the fact that permission was granted (the system caches it, but the app should not call `requestPermission` again for already-permitted devices). The current flow already skips devices with `usbManager.hasPermission(device)` — preserve this.

### iOS Threading Fix (CoreMIDI)

- **D-11:** In `MIDIManager.swift` `handleMIDIInput()`, wrap the `onMIDIReceived?` call in `DispatchQueue.main.async { [weak self] in ... }`. This matches how `handleMIDINotification` already dispatches `onDevicesChanged`.
- **D-12:** In `sendRawBytes()`, replace `UnsafeMutablePointer<MIDIPacketList>.allocate(capacity: 1)` with `UnsafeMutableRawPointer.allocate(byteCount: packetListSize, alignment: MemoryLayout<MIDIPacketList>.alignment)` then cast appropriately. The current allocation is too small for any SysEx payload.

### iOS Swift MIDI Protocol Layer

- **D-13:** Define a `MIDIPort` Swift `protocol` in `iOSApp/EP133SampleTool/MIDI/MIDIPort.swift` mirroring the Kotlin `MIDIPort` interface. Methods: `setup()`, `getUSBDevices() -> DeviceList`, `sendMIDI(to:data:)`, `startListening(portId:)`, `stopListening(portId:)`, `onMIDIReceived`, `onDevicesChanged`, `close()`.
- **D-14:** `MIDIManager` should conform to `MIDIPort`. This makes the layer testable and mirrors Android's `MIDIPort` interface pattern exactly.
- **D-15:** `MIDIManager` must be created once at app root (`EP133SampleToolApp` @main struct) and injected into the SwiftUI environment. Not instantiated per-view.

### Connection Status UI

- **D-16:** A persistent connection status indicator is shown at app level, not only on DeviceScreen. The exact widget is Claude's discretion — a small dot or label in the navigation bar / tab bar footer is appropriate. Should be non-intrusive (not a banner that obscures content).
- **D-17:** DeviceScreen remains the primary screen for connection detail (device name, port info). Other screens show the minimal indicator only.
- **D-18:** No automatic navigation when connection state changes. The user is not redirected to DeviceScreen when the EP-133 disconnects.

### Error and Permission UX

- **D-19:** DeviceScreen must handle three explicit empty/error states:
  1. **No device found:** "Connect your EP-133 via USB" with an illustration or icon, plus a "Grant Permission" button that calls `requestUSBPermissions()`.
  2. **Awaiting permission:** "Waiting for USB permission…" with a progress indicator. Shown after the permission dialog is presented.
  3. **Permission denied:** "USB permission required. Go to Settings to allow USB access." with a Settings deep-link button.
- **D-20:** Other screens (Pads, Beats, Sounds) must show a disabled/empty state when no device is connected — they must NOT crash or show stale data. The exact disabled treatment is Claude's discretion (e.g., greyed-out pads, a non-intrusive "Connect EP-133 to use pads" overlay).

### Claude's Discretion

- Exact visual style of the global connection indicator (dot color, placement, size) — use the existing `TEColors` palette from `ui/theme/TEColors`
- Whether "Awaiting permission" uses `CircularProgressIndicator` (Android) or `ProgressView` (iOS)
- Whether disabled pad/beats state is a full overlay or per-component dimming

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing MIDI Implementation
- `AndroidApp/app/src/main/java/com/ep133/sampletool/midi/MIDIManager.kt` — Android MIDI layer; threading bug is in `startListening()` MidiReceiver callback (line 253)
- `AndroidApp/app/src/main/java/com/ep133/sampletool/midi/MIDIPort.kt` — Kotlin interface to mirror in Swift
- `AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/MIDIRepository.kt` — Domain layer; `_deviceState.value` mutation threading to audit
- `AndroidApp/app/src/main/java/com/ep133/sampletool/domain/sequencer/SequencerEngine.kt` — Unscoped coroutine leak to fix
- `AndroidApp/app/src/main/java/com/ep133/sampletool/MainActivity.kt` — Manual DI construction and unscoped `CoroutineScope` to fix
- `iOSApp/EP133SampleTool/MIDI/MIDIManager.swift` — iOS MIDI layer; threading bug at line 177, SysEx buffer bug at line 137

### UI / DeviceScreen
- `AndroidApp/app/src/main/java/com/ep133/sampletool/ui/device/DeviceScreen.kt` — Existing device screen with hardcoded stats; connection status indicator to be enhanced
- `AndroidApp/app/src/main/java/com/ep133/sampletool/ui/theme/TEColors.kt` — Existing color tokens; use for connection indicator styling

### Codebase Maps
- `.planning/codebase/CONCERNS.md` — Full list of tech debt; Phase 1 addresses items: SequencerEngine scope, MainActivity scope, MIDI threading (Android + iOS)
- `.planning/codebase/ARCHITECTURE.md` — Layer structure; iOS must mirror Android's MIDI → Domain → UI layering
- `.planning/research/PITFALLS.md` — Pitfalls 1 (Android MIDI threading), 2 (iOS CoreMIDI threading), 3 (USB permission race) are the primary targets of this phase

### Requirements
- `.planning/REQUIREMENTS.md` §Connection — CONN-01..04 are the acceptance criteria for this phase

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MIDIPort.kt` interface: defines the contract that `MIDIManager.kt` already implements — Swift `MIDIPort` protocol mirrors this exactly
- `DeviceState` data class: already used by `MIDIRepository` as a `StateFlow` — connection status can be read from `deviceState.connected`
- `TEColors` object: existing Material 3 color tokens — use `TEColors.green`/`TEColors.red` (or equivalent) for connection dot
- `mainHandler` in `MIDIManager.kt`: already used for dispatching callbacks — use same pattern for the MIDI receive callback fix

### Established Patterns
- State flow pattern: `private val _X = MutableStateFlow(...)` backing field, `val x = _X.asStateFlow()` — use this for any new state
- Screen + co-located ViewModel: all existing screens (`BeatsScreen.kt`, `SoundsScreen.kt`) include their ViewModel in the same file — follow this for any new screen additions
- `mainHandler.post {}` dispatch: already used in `notifyDevicesChanged()` — use identical pattern for MIDI receive dispatch in `MIDIManager.kt`
- `@Suppress("DEPRECATION")` for `MidiManager.registerDeviceCallback()` and `openDevice()` — these suppressions are expected and intentional until the API migration to executor-based overloads

### Integration Points
- `MainActivity.onCreate()` (lines 55-69): all ViewModels and domain objects constructed here — `SequencerEngine.close()` call goes in `onDestroy()`
- `EP133SampleToolApp.swift` (app root): `MIDIManager` initialization belongs here, injected via SwiftUI `Environment`
- `ContentView.swift`: current iOS entry point; no changes in Phase 1 (WKWebView stays; Phase 3 replaces it)

</code_context>

<specifics>
## Specific Ideas

- User gave "No preference" on all gray areas — all implementation decisions are Claude's discretion within the constraints documented above.
- The threading fixes are surgical: minimal diffs, no architectural changes. Do not refactor beyond what's needed to fix the bug.
- The iOS `MIDIPort` Swift protocol definition should be a near-literal translation of `MIDIPort.kt` to establish the parallel architecture early.

</specifics>

<deferred>
## Deferred Ideas

- **MIDIKit 0.11.0 adoption** — evaluate at Phase 3 kickoff after auditing whether existing `MIDIManager.swift` is sufficient for SwiftUI integration
- **`@Observable` (iOS 17) vs `ObservableObject` (iOS 16)** — deployment target decision deferred to Phase 3 planning; Phase 1 establishes the MIDI layer only
- **Kotlin 2.0.21 upgrade + Hilt DI + Navigation 2.8** — explicitly deferred per STATE.md until after Phase 2 (highest-risk SysEx phases)
- **Deprecated Android MIDI API migration** (executor-based overloads) — deferred; current `@Suppress` annotations are acceptable for now
- **Version number drift fix** — chore; not Phase 1 scope

</deferred>

---

*Phase: 01-midi-foundation*
*Context gathered: 2026-03-28*
