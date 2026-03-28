# Phase 1: MIDI Foundation - Research

**Researched:** 2026-03-28
**Domain:** Android Kotlin MIDI threading + coroutine scopes, iOS CoreMIDI threading + SysEx buffer allocation, Swift protocol definition, Compose/SwiftUI connection status UI
**Confidence:** HIGH (codebase-grounded; all findings derived from reading actual source lines)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Fix `MIDIManager.swift` in place — do NOT adopt MIDIKit 0.11.0 for Phase 1. Two surgical fixes: (1) dispatch `onMIDIReceived?` to main thread, (2) fix `sendRawBytes` buffer allocation.
- **D-02:** Adding MIDIKit requires Xcode 16 (current requirement is Xcode 15+). Do not change the Xcode minimum requirement in Phase 1.
- **D-03:** Stay at iOS 16 deployment target for Phase 1. Use `ObservableObject` + `@Published` if any observable class is needed. `@Observable` (iOS 17) decision deferred to Phase 3.
- **D-04:** Do NOT raise the minimum deployment target as part of Phase 1.
- **D-05:** In `MIDIManager.kt` `startListening()`, the `MidiReceiver.onSend()` callback fires on the MIDI thread. Fix is to post to `mainHandler` before invoking `onMidiReceived?.invoke()`, consistent with how `notifyDevicesChanged()` already works.
- **D-06:** In `MIDIRepository.kt`, `_incomingMidi.tryEmit()` is already thread-safe — no change needed. The real risk is `_deviceState.value = ...` mutations in `refreshDeviceState()` — must be confirmed to run on the main thread or guarded.
- **D-07:** `SequencerEngine` must get a `SupervisorJob()` added to its scope and a `close()` method cancelling the scope. `MainActivity.onDestroy()` must call `sequencerEngine.close()`.
- **D-08:** `MainActivity`'s manual `CoroutineScope(Dispatchers.Main)` must be replaced with `lifecycleScope`.
- **D-09:** Remove the 500ms hardcoded delay in `usbPermissionReceiver`. Replace with `MidiManager.DeviceCallback.onDeviceAdded` as the trigger.
- **D-10:** USB permission requested once; skip devices where `usbManager.hasPermission(device)` already returns true — current flow already does this; preserve it.
- **D-11:** In `MIDIManager.swift` `handleMIDIInput()`, wrap the `onMIDIReceived?` call in `DispatchQueue.main.async { [weak self] in ... }`.
- **D-12:** In `sendRawBytes()`, replace `UnsafeMutablePointer<MIDIPacketList>.allocate(capacity: 1)` with `UnsafeMutableRawPointer.allocate(byteCount: packetListSize, alignment: MemoryLayout<MIDIPacketList>.alignment)` then cast appropriately.
- **D-13:** Define a `MIDIPort` Swift `protocol` in `iOSApp/EP133SampleTool/MIDI/MIDIPort.swift` mirroring the Kotlin `MIDIPort` interface. Methods: `setup()`, `getUSBDevices() -> DeviceList`, `sendMIDI(to:data:)`, `startListening(portId:)`, `stopListening(portId:)`, `onMIDIReceived`, `onDevicesChanged`, `close()`.
- **D-14:** `MIDIManager` must conform to `MIDIPort`.
- **D-15:** `MIDIManager` must be created once at `EP133SampleToolApp` @main struct and injected into the SwiftUI environment.
- **D-16:** A persistent connection status indicator is shown at app level, not only on DeviceScreen. Exact widget is Claude's discretion — a small dot or label in the navigation bar / tab bar footer is appropriate; non-intrusive.
- **D-17:** DeviceScreen remains the primary screen for connection detail. Other screens show the minimal indicator only.
- **D-18:** No automatic navigation when connection state changes.
- **D-19:** DeviceScreen must handle three explicit empty/error states: (1) No device found, (2) Awaiting permission, (3) Permission denied.
- **D-20:** Other screens (Pads, Beats, Sounds) must show a disabled/empty state when no device is connected — must NOT crash or show stale data.

### Claude's Discretion

- Exact visual style of the global connection indicator (dot color, placement, size) — use the existing `TEColors` palette from `ui/theme/TEColors`
- Whether "Awaiting permission" uses `CircularProgressIndicator` (Android) or `ProgressView` (iOS)
- Whether disabled pad/beats state is a full overlay or per-component dimming

### Deferred Ideas (OUT OF SCOPE)

- MIDIKit 0.11.0 adoption — evaluate at Phase 3 kickoff
- `@Observable` (iOS 17) vs `ObservableObject` (iOS 16) — deferred to Phase 3 planning
- Kotlin 2.0.21 upgrade + Hilt DI + Navigation 2.8 — deferred until after Phase 2
- Deprecated Android MIDI API migration (executor-based overloads) — deferred
- Version number drift fix — chore, not Phase 1 scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONN-01 | User can see live USB connection status (connected/disconnected) persistently across all screens | Connection indicator added to `NavigationBar` in `EP133App.kt`; reads `deviceState.connected` from `MIDIRepository` |
| CONN-02 | App automatically reconnects to EP-133 after cable replug without requiring manual action | `DeviceCallback.onDeviceAdded` triggers re-enumeration; `usbReceiver` already handles `ACTION_USB_DEVICE_ATTACHED` in `MainActivity` |
| CONN-03 | App requests USB permission once and caches it; does not re-prompt on every launch | `usbManager.hasPermission()` check already gates `requestPermission()` calls; preserve this — no new work needed |
| CONN-04 | User sees an actionable error state when EP-133 is not found, permission is denied, or firmware is incompatible | Three-state enum added to `DeviceState`; `DeviceScreen` renders each state with appropriate CTA |
</phase_requirements>

---

## Summary

Phase 1 is a surgical stabilization pass — fixing four pre-existing bugs and adding the connection-state UI infrastructure. No new features are introduced. The codebase already has the correct structural foundations (interface abstraction `MIDIPort`, `StateFlow` pattern, `mainHandler`, `deviceCallback`) but has not connected them consistently. All fixes build on already-established patterns in the same files.

The Android threading fix is a one-line change in `MIDIManager.kt` (line 253): wrap `onMidiReceived?.invoke(portId, bytes)` in `mainHandler.post { ... }`. The iOS threading fix is the same structure: wrap `onMIDIReceived?(portId, bytes)` in `DispatchQueue.main.async`. Both fixes mirror patterns that already exist in the same file (`notifyDevicesChanged()` on Android, `handleMIDINotification` on iOS).

The iOS `sendRawBytes` allocation fix is the most technical change: the current `capacity: 1` pointer allocation is not large enough to hold the packet list for any payload larger than the base struct size. The correct approach allocates raw bytes sized to the full packet list. The `SequencerEngine` scope fix and `MainActivity` scope fix are mechanical: add `SupervisorJob`, add `close()`, and swap `CoroutineScope(Dispatchers.Main)` for `lifecycleScope`.

**Primary recommendation:** Apply fixes in order of crash risk: (1) Android MIDI thread dispatch, (2) iOS MIDI thread dispatch, (3) iOS sendRawBytes buffer, (4) SequencerEngine scope, (5) MainActivity lifecycleScope, (6) USB permission race, (7) connection status UI + error states.

---

## Standard Stack

### Core (already in project — no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `kotlinx-coroutines-android` | 1.7.x (transitive) | `lifecycleScope`, `Dispatchers.Main` | Android idiomatic async; already used in project |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.7.0 (transitive via `lifecycle-runtime-compose`) | `lifecycleScope` extension on `ComponentActivity` | Required for D-08; already on classpath |
| `kotlinx-coroutines-test` | 1.7.3 (already in `testImplementation`) | `TestCoroutineScheduler`, `runTest`, `advanceTimeBy` | Unit-testing coroutine state flow emissions |
| CoreMIDI | iOS 16+ system framework | MIDI I/O on iOS | Already in use; no MIDIKit added per D-01/D-02 |
| `android.media.midi` | API 29+ system API | Android USB MIDI | Already in use |

### New dependency required: none

`lifecycleScope` is available via `androidx.lifecycle:lifecycle-runtime-compose:2.7.0` which is already declared in `app/build.gradle.kts`. No new `build.gradle.kts` entry is needed. Confirmed via Gradle dependency tree: `lifecycle-runtime-ktx:2.7.0` is on the transitive runtime classpath.

### Installation
```bash
# No new dependencies — all required libraries are already on the classpath
```

---

## Architecture Patterns

### Android: Thread Dispatch for MIDI Callbacks

The `mainHandler` pattern is already established in `MIDIManager.kt` line 275:

```kotlin
// ESTABLISHED PATTERN — already in MIDIManager.kt notifyDevicesChanged()
private fun notifyDevicesChanged() {
    mainHandler.post {
        onDevicesChanged?.invoke()
    }
}
```

The `startListening()` fix (D-05) applies the identical pattern to `onMidiReceived`:

```kotlin
// FIX: MIDIManager.kt startListening() — line 252-253 becomes:
override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
    val bytes = data.copyOfRange(offset, offset + count)
    mainHandler.post {
        onMidiReceived?.invoke(portId, bytes)
    }
}
```

**Why `mainHandler.post` not `withContext(Dispatchers.Main)`:** The `onSend` callback is a non-suspend Java callback. It cannot use `withContext`. `mainHandler.post` is the correct mechanism for dispatching from a Java callback to the main thread. `tryEmit()` on `MutableSharedFlow` is already thread-safe for the `_incomingMidi` case — that call site needs no change.

### Android: MutableStateFlow thread safety audit

After the `mainHandler.post` fix in `MIDIManager`, the full call chain for `_deviceState.value = ...` in `MIDIRepository` is:

1. `onDevicesChanged` fires → dispatched via `mainHandler.post` (after fix) → `updateDeviceStateOnly()` runs on main thread → `_deviceState.value = DeviceState(...)` — **safe**
2. `refreshDeviceState()` in `MIDIRepository` (lines 87-93) — called from `MainActivity.usbReceiver.onReceive` via `mainHandler.postDelayed` (line 46-47 in `MainActivity`) — **already on main thread; safe**
3. The 2000ms post at `MainActivity.onCreate()` line 88 also runs on main thread — **safe**

Conclusion (D-06 confirmed): No changes to `_deviceState.value` mutation sites in `MIDIRepository` beyond confirming all callers dispatch via `mainHandler`. The `refreshDeviceState()` function does not need `withContext(Dispatchers.Main)` wrapping because all call sites are already main-thread-dispatched.

### Android: SequencerEngine Scope (D-07)

```kotlin
// BEFORE (line 77 SequencerEngine.kt):
private val scope = CoroutineScope(Dispatchers.Default)

// AFTER:
private val job = SupervisorJob()
private val scope = CoroutineScope(Dispatchers.Default + job)

fun close() {
    job.cancel()
}
```

`SupervisorJob` is needed so that individual child coroutine failures (e.g., a note-off coroutine throwing) do not cancel the entire scope. The `close()` method cancels the `SupervisorJob`, which propagates cancellation to all children including the fire-and-forget note-off launches inside `playLoop()`.

In `MainActivity.onDestroy()` (after line 112):
```kotlin
override fun onDestroy() {
    super.onDestroy()
    screenOnJob?.cancel()
    sequencer.close()          // ADD THIS
    try { unregisterReceiver(usbReceiver) } catch (_: IllegalArgumentException) {}
    midiRepo.close()
}
```

### Android: Replace Manual CoroutineScope with lifecycleScope (D-08)

```kotlin
// BEFORE (MainActivity line 39):
private val scope = CoroutineScope(Dispatchers.Main)
private var screenOnJob: Job? = null

// AFTER:
// Remove the `scope` field entirely.
// Remove the `screenOnJob` field.
// observeScreenOnState() uses lifecycleScope directly.

private fun observeScreenOnState() {
    lifecycleScope.launch {
        sequencer.state.collectLatest { state ->
            if (state.playing) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}
```

`lifecycleScope` is automatically cancelled in `onDestroy()` by the lifecycle framework — no manual cancellation needed. The `screenOnJob?.cancel()` call in `onDestroy()` can be removed.

**Import required:** `androidx.lifecycle.lifecycleScope` — available transitively at no extra cost.

### Android: USB Permission Race Fix (D-09)

The current flow in `usbPermissionReceiver` (line 66-68) has a hardcoded 500ms delay:

```kotlin
// BEFORE:
mainHandler.postDelayed({ notifyDevicesChanged() }, 500)

// AFTER: Remove the delayed call entirely.
// When granted == true, do nothing here. The authoritative trigger is:
//   deviceCallback.onDeviceAdded() — already registered at line 74-77
// That callback fires when the MIDI service actually enumerates the device,
// which may be 0ms or 800ms after permission grant depending on the device.
```

The `deviceCallback` is already registered with `mainHandler` at line 76. When `onDeviceAdded` fires, it calls `notifyDevicesChanged()`. This is the correct authoritative trigger.

**Edge case (D-09 exponential backoff):** On some Android versions `onDeviceAdded` fires before the MIDI stack is ready to open the device. The PITFALLS.md specifies: max 3 retries at 200ms apart. This is handled inside `openOrGetDevice()` — if `midiManager.devices.firstOrNull { it.id == deviceId }` returns null immediately after `onDeviceAdded`, the current code returns `callback(null)`. A retry mechanism is needed only if real hardware testing reveals this issue; the planner should note this as a conditional task.

### Android: "Awaiting Permission" UI State (D-19)

`DeviceState` data class needs an additional field to represent the permission-awaiting state. The current `DeviceState` only has `connected: Boolean`:

```kotlin
// DeviceState model — extend with connection phase:
data class DeviceState(
    val connected: Boolean = false,
    val deviceName: String = "",
    val outputPortId: String? = null,
    val inputPorts: List<MidiPort> = emptyList(),
    val outputPorts: List<MidiPort> = emptyList(),
    val permissionState: PermissionState = PermissionState.UNKNOWN,
)

enum class PermissionState { UNKNOWN, AWAITING, DENIED, GRANTED }
```

`MIDIManager.kt` sets `permissionState = PermissionState.AWAITING` when `requestPermission()` is called. When `granted == true` in `usbPermissionReceiver`, it sets `permissionState = PermissionState.GRANTED`. When `granted == false`, it sets `PermissionState.DENIED`.

**Note:** The `DeviceState` class is in `AndroidApp/app/src/main/java/com/ep133/sampletool/domain/model/`. The planner needs to include a task that modifies this model file.

### iOS: CoreMIDI Thread Dispatch (D-11)

Exact location: `MIDIManager.swift` line 177.

```swift
// BEFORE (line 177):
onMIDIReceived?(portId, bytes)

// AFTER:
let capturedPortId = portId
let capturedBytes = bytes
DispatchQueue.main.async { [weak self] in
    self?.onMIDIReceived?(capturedPortId, capturedBytes)
}
```

The `[weak self]` capture is mandatory — the `MIDIManager` could be deallocated between the CoreMIDI callback and main thread execution. Capture `portId` and `bytes` as local constants before the async block to avoid capturing the loop variable.

This exactly mirrors `handleMIDINotification` (lines 192-194) which already uses `DispatchQueue.main.async { [weak self] in self?.onDevicesChanged?() }`.

### iOS: sendRawBytes Allocation Fix (D-12)

The bug (line 137): `UnsafeMutablePointer<MIDIPacketList>.allocate(capacity: 1)` allocates space for one `MIDIPacketList` struct instance. `MIDIPacketList` contains a `packet` field which holds only the first `MIDIPacket` header — it does not include the packet data bytes. For any payload beyond the empty struct, the `MIDIPacketListAdd` call writes beyond the allocated buffer.

```swift
// BEFORE (lines 136-143):
private func sendRawBytes(to destination: MIDIEndpointRef, data: [UInt8]) {
    let packetListSize = MemoryLayout<MIDIPacketList>.size + data.count
    let packetListPtr = UnsafeMutablePointer<MIDIPacketList>.allocate(capacity: 1)
    defer { packetListPtr.deallocate() }
    var packet = MIDIPacketListInit(packetListPtr)
    packet = MIDIPacketListAdd(packetListPtr, packetListSize, packet, 0, data.count, data)
    MIDISend(outputPort, destination, packetListPtr)
}

// AFTER:
private func sendRawBytes(to destination: MIDIEndpointRef, data: [UInt8]) {
    let packetListSize = MemoryLayout<MIDIPacketList>.size + MemoryLayout<MIDIPacket>.size + data.count
    let rawPtr = UnsafeMutableRawPointer.allocate(
        byteCount: packetListSize,
        alignment: MemoryLayout<MIDIPacketList>.alignment
    )
    defer { rawPtr.deallocate() }
    let packetListPtr = rawPtr.bindMemory(to: MIDIPacketList.self, capacity: 1)
    var packet = MIDIPacketListInit(packetListPtr)
    packet = MIDIPacketListAdd(packetListPtr, packetListSize, packet, 0, data.count, data)
    MIDISend(outputPort, destination, packetListPtr)
}
```

**Why `UnsafeMutableRawPointer`:** The `capacity` parameter in `UnsafeMutablePointer.allocate(capacity:)` means "number of instances of the pointed-to type," not "number of bytes." `UnsafeMutableRawPointer.allocate(byteCount:alignment:)` explicitly allocates bytes. The total needed is: `MIDIPacketList` header + `MIDIPacket` header + data bytes.

**Phase 1 scope note (D-01):** The SysEx-chunking concern (Pitfall 5 — messages >65535 bytes) is out of scope for Phase 1. This fix addresses the buffer size mismatch for any payload; the 65535-byte limit is a Phase 4 concern (iOS project management).

### iOS: MIDIPort Swift Protocol (D-13, D-14)

The protocol mirrors `MIDIPort.kt` exactly. Kotlin callbacks (`var onMidiReceived: ((String, ByteArray) -> Unit)?`) become Swift optional closures (`var onMIDIReceived: ((String, [UInt8]) -> Void)?`).

```swift
// New file: iOSApp/EP133SampleTool/MIDI/MIDIPort.swift

protocol MIDIPort: AnyObject {
    struct Device {
        let id: String
        let name: String
    }

    struct DeviceList {
        let inputs: [Device]
        let outputs: [Device]
    }

    // Callbacks — set by consumer before setup()
    var onMIDIReceived: ((String, [UInt8]) -> Void)? { get set }
    var onDevicesChanged: (() -> Void)? { get set }

    // Lifecycle
    func setup()
    func close()

    // Device enumeration
    func getUSBDevices() -> DeviceList

    // Send
    func sendMIDI(to portId: String, data: [UInt8])

    // Receive
    func startListening(portId: String)
    func stopListening(portId: String)
}
```

Note: `MIDIManager.swift` already has `MIDIDevice` and `DeviceList` structs as inner types. The protocol defines these as nested types; `MIDIManager` must either use the protocol's types or declare a typealias. The cleanest approach: move `MIDIDevice`/`DeviceList` out of `MIDIManager` into the `MIDIPort` protocol (or a companion file) and update `MIDIManager` to use them.

`MIDIManager` currently has no `startListening` / `stopListening` methods (it uses `connectAllSources()` implicitly). Phase 1 adds stub implementations:
- `startListening(portId:)` — no-op in Phase 1 (iOS connection management is incremental; full per-port listening is Phase 3)
- `stopListening(portId:)` — no-op in Phase 1

### iOS: MIDIManager Singleton Injection (D-15)

`EP133SampleToolApp.swift` currently has a 10-line `App` struct with no state. The change is:

```swift
@main
struct EP133SampleToolApp: App {
    // Created once at app root — injected into the environment
    @StateObject private var midiManager = MIDIManagerObservable()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(midiManager)
                .ignoresSafeArea()
        }
    }
}
```

`MIDIManagerObservable` is an `ObservableObject` wrapper (per D-03: iOS 16 target requires `ObservableObject`, not `@Observable`) that holds the `MIDIManager` instance and publishes connection state:

```swift
final class MIDIManagerObservable: ObservableObject {
    let midi = MIDIManager()
    @Published var isConnected: Bool = false

    init() {
        midi.onDevicesChanged = { [weak self] in
            self?.isConnected = !self!.midi.getUSBDevices().inputs.isEmpty
        }
        midi.setup()
    }
}
```

`ContentView.swift` currently wraps `EP133WebView()` unchanged — no changes to `ContentView` in Phase 1 per D-03 (Phase 3 replaces the WKWebView).

### Connection Status Indicator (D-16, D-17)

The Android `EP133App.kt` `NavigationBar` composable receives a `deviceState` parameter and renders a small colored dot. The indicator is placed in the `NavigationBar`'s trailing area or as a badge on the DEVICE tab icon — a `BadgedBox` with a colored dot.

**Recommended approach:** `BadgedBox` on the DEVICE nav item, showing `TEColors.Teal` dot when connected, nothing when disconnected. This follows Material 3 guidance and is non-intrusive per D-16.

```kotlin
// EP133App.kt — NavigationBarItem for DEVICE route gets a badge
BadgedBox(
    badge = {
        if (connected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(TEColors.Teal, CircleShape)
            )
        }
    }
) {
    Icon(imageVector = item.icon, contentDescription = item.label)
}
```

`EP133App` needs to receive `deviceState: StateFlow<DeviceState>` or `isConnected: Boolean` as a parameter, collected once at the `EP133App` level.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Thread dispatch from Java callback | Custom thread-switching mechanism | `mainHandler.post { }` | Already in the file at `notifyDevicesChanged()` |
| Coroutine scope lifecycle | Manual `Job` tracking in Activity | `lifecycleScope` | Automatically cancelled; prevents the leak being fixed |
| iOS main thread dispatch | Custom queue mechanism | `DispatchQueue.main.async { [weak self] in }` | Already in `handleMIDINotification`; same pattern |
| iOS raw memory allocation for MIDI | Typed pointer with wrong capacity | `UnsafeMutableRawPointer.allocate(byteCount:alignment:)` | Avoids the capacity-vs-bytes confusion |
| Connection state tracking | Polling timer | `DeviceCallback.onDeviceAdded/onDeviceRemoved` | System callback is authoritative; no polling needed |
| Disabled state for Pads/Beats when disconnected | Navigator redirect to DeviceScreen | Overlay composable / dimming | D-18 explicitly forbids auto-navigation |

---

## Runtime State Inventory

> This phase is not a rename/refactor/migration phase. No runtime state audit is required.

None — this phase modifies existing source files only. No stored data, live service config, OS-registered state, secrets, or build artifacts are renamed or migrated.

---

## Common Pitfalls

### Pitfall A: Capturing Loop Variables in DispatchQueue.main.async

**What goes wrong:** In the iOS `handleMIDIInput` loop, `portId` and `bytes` are computed inside a `for` loop body. If captured directly in the `DispatchQueue.main.async` closure, the closure captures the variable binding, not the value at time of capture — by the time the closure executes on main, the loop may have advanced and `portId`/`bytes` may have changed.

**How to avoid:** Capture as named local constants before the async block:
```swift
let capturedPortId = portId
let capturedBytes = bytes
DispatchQueue.main.async { [weak self] in
    self?.onMIDIReceived?(capturedPortId, capturedBytes)
}
```

**Warning signs:** MIDI events arriving with the wrong `portId` or byte sequences shifted by one event.

---

### Pitfall B: `_deviceState.value` Called from `SequencerEngine.scope` (Dispatchers.Default)

**What goes wrong:** `SequencerEngine` fires `_state.update { ... }` from `Dispatchers.Default`. This is safe for `MutableStateFlow` (which is thread-safe), but any ViewModel that collects `sequencer.state` and then calls `_deviceState.value = ...` from the same collected callback would push a state mutation onto a non-main thread.

**How to avoid:** The fix in D-07 (adding `SupervisorJob`) does not change the dispatcher. `MutableStateFlow.update` and `.value` assignment are thread-safe per Kotlin coroutines design. The rule: `MutableStateFlow` is safe from any thread; `mutableStateOf` (Compose snapshot state) is NOT. Ensure no code added in Phase 1 uses `mutableStateOf` outside of composable scope.

**Warning signs:** `IllegalStateException: Reading a state that was created after the snapshot was taken` in logcat.

---

### Pitfall C: MIDIPacketList Size Calculation Off-By-One

**What goes wrong:** The corrected iOS `sendRawBytes` uses `MemoryLayout<MIDIPacketList>.size + MemoryLayout<MIDIPacket>.size + data.count`. Some implementations omit the `MIDIPacket` header size, allocating only `MIDIPacketList.size + data.count`. This still under-allocates because `MIDIPacket` has a header (timestamp + length fields) before the data bytes.

**How to avoid:** Always include both headers:
- `MemoryLayout<MIDIPacketList>.size` — list header (numPackets field)
- `MemoryLayout<MIDIPacket>.size` — packet header (timeStamp + length fields, no data bytes)
- `data.count` — the actual payload bytes

**Warning signs:** Crash or silent corruption on SysEx messages longer than ~8 bytes.

---

### Pitfall D: `lifecycleScope` Not Available Without Import

**What goes wrong:** `lifecycleScope` is an extension property on `LifecycleOwner` / `ComponentActivity` defined in `androidx.lifecycle:lifecycle-runtime-ktx`. It IS on the classpath transitively but the import `import androidx.lifecycle.lifecycleScope` must be explicit. Without the import, the compiler sees `lifecycleScope` as undefined.

**How to avoid:** Add explicit import. The `lifecycle-runtime-ktx:2.7.0` dependency is confirmed available via transitive dependency from `lifecycle-runtime-compose:2.7.0`.

---

### Pitfall E: `DeviceCallback.onDeviceAdded` Fires Before Device Is Openable

**What goes wrong:** On some Android versions and USB controllers, `onDeviceAdded` fires from the MIDI service while the device is not yet ready to be opened (e.g., `midiManager.openDevice()` returns `null` in the callback). The current `openOrGetDevice()` pattern returns `callback(null)` silently.

**How to avoid:** When `openDevice` returns null immediately after an `onDeviceAdded` event, the `startListening()` call will silently fail. The `notifyDevicesChanged()` → `updateDeviceStateOnly()` → `startListening()` chain will update `deviceState.connected = true` (because `getUSBDevices()` sees the device in `midiManager.devices`) but the listener port will not be open. This is a known edge case on Android. The fix is to retry `openOrGetDevice` with a 200ms delay if the first call returns null within the `onDeviceAdded` handler path. The planner should add a conditional task: "add 200ms retry in openOrGetDevice if called from onDeviceAdded path."

---

### Pitfall F: DeviceState.permissionState Field Must Update Before UI Reads It

**What goes wrong:** `requestUSBPermissions()` calls `usbManager.requestPermission()` asynchronously (shows system dialog), but `_deviceState.value` is updated synchronously before returning. If the ViewModel reads `deviceState` while the dialog is showing, it must already reflect `AWAITING`. If the state update happens after the dialog is dismissed, the "Awaiting" flash is never seen.

**How to avoid:** Update `_deviceState.value.permissionState = PermissionState.AWAITING` inside `requestUSBPermissions()` before the `usbManager.requestPermission()` call. The `usbPermissionReceiver` then sets `GRANTED` or `DENIED` in response.

---

## Code Examples

### Android: Full startListening fix (verified from source)

Current code at `MIDIManager.kt` lines 250-255:
```kotlin
outputPort.connect(object : MidiReceiver() {
    override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
        val bytes = data.copyOfRange(offset, offset + count)
        onMidiReceived?.invoke(portId, bytes)  // BUG: fires on MIDI thread
    }
})
```

Fixed:
```kotlin
outputPort.connect(object : MidiReceiver() {
    override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
        val bytes = data.copyOfRange(offset, offset + count)
        mainHandler.post {
            onMidiReceived?.invoke(portId, bytes)
        }
    }
})
```

Source: Verified from `MIDIManager.kt` lines 250-255 + `notifyDevicesChanged()` pattern at lines 275-279.

---

### iOS: Full handleMIDIInput dispatch fix (verified from source)

Current code at `MIDIManager.swift` line 177:
```swift
onMIDIReceived?(portId, bytes)  // BUG: fires on CoreMIDI thread
```

Fixed (insert before the `withUnsafePointer` call at line 179):
```swift
let capturedPortId = portId
let capturedBytes = bytes
DispatchQueue.main.async { [weak self] in
    self?.onMIDIReceived?(capturedPortId, capturedBytes)
}
```

Source: Verified from `MIDIManager.swift` lines 174-184 + `handleMIDINotification` dispatch pattern at lines 192-194.

---

### Android: DeviceScreen three-state empty state pattern

```kotlin
@Composable
private fun DeviceConnectionState(
    permissionState: PermissionState,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (permissionState) {
        PermissionState.UNKNOWN, PermissionState.GRANTED -> {
            // No device found state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            ) {
                Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(64.dp),
                    tint = TEColors.InkTertiary)
                Spacer(Modifier.height(16.dp))
                Text("Connect your EP-133 via USB", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onGrantPermission) { Text("Grant Permission") }
            }
        }
        PermissionState.AWAITING -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Waiting for USB permission…")
            }
        }
        PermissionState.DENIED -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("USB permission required. Go to Settings to allow USB access.")
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
        }
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `CoroutineScope(Dispatchers.Main)` in Activity | `lifecycleScope` | Introduced in `lifecycle-runtime-ktx` 2.2.0 (2020); now idiomatic | Automatic cancellation in `onDestroy` |
| `MutableSharedFlow` requires main-thread emitters | `tryEmit()` is thread-safe on `SharedFlow` | Since kotlinx-coroutines 1.4.x | `tryEmit` is safe from any thread; no dispatch needed |
| `MutableStateFlow.value = ...` | Thread-safe from any thread | Since initial release | No dispatch needed for `StateFlow` mutation |
| `GlobalScope` | Project-scoped `SupervisorJob` + lifecycle | Kotlin coroutines 1.0+ | `GlobalScope` is an anti-pattern; `SupervisorJob` isolates failures |
| iOS `DispatchQueue.main.sync` from callback | `DispatchQueue.main.async` | Always; `sync` from CoreMIDI thread deadlocks | `async` is always correct in callback contexts |

**Important clarification on thread-safety of `MutableStateFlow.value`:**
`MutableStateFlow.value = ...` is thread-safe — it can be written from any thread without dispatching to main. The crash risk with Compose is specifically about `mutableStateOf` (Compose snapshot state), NOT `MutableStateFlow`. This project correctly uses `MutableStateFlow` everywhere in ViewModels. The MIDI thread fix in `MIDIManager.kt` is needed not because `StateFlow` is unsafe from background threads, but because `onMidiReceived` is a raw lambda callback that consumers (like `MIDIBridge.kt` in the WebView path) may call `evaluateJavaScript` from, which DOES require main thread.

---

## Open Questions

1. **onDeviceAdded timing on older Android hardware**
   - What we know: `DeviceCallback.onDeviceAdded` is the correct replacement for the 500ms delay
   - What's unclear: Whether any supported Android version (API 29-34) has documented cases where `onDeviceAdded` fires before the device is openable
   - Recommendation: Implement the fix as specified in D-09. Add the 200ms retry as a conditional task gated on physical-device testing. If CI runs on emulator only, this path cannot be tested automatically.

2. **iOS `startListening(portId:)` behavior in Phase 1**
   - What we know: The `MIDIPort` protocol requires `startListening` / `stopListening`. Android uses these to open output ports per-device. iOS uses `connectAllSources()` which connects all at once.
   - What's unclear: Whether `startListening(portId:)` on iOS should be a no-op (D-13 specifies the protocol; Phase 3 implements per-port listening) or should trigger selective source connection
   - Recommendation: Phase 1 implements `startListening` / `stopListening` as no-ops on iOS per D-13. Full per-port listening is a Phase 3 concern.

3. **DeviceState model location**
   - What we know: `DeviceState` is used by `MIDIRepository` (domain layer) and `DeviceScreen` (UI layer)
   - What's unclear: The exact file path for `DeviceState` was not read. Based on imports in `MIDIRepository.kt`: `import com.ep133.sampletool.domain.model.DeviceState`
   - Recommendation: The planner should include a task to read `domain/model/DeviceState.kt` before modifying it.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android Studio + Gradle | Android build | Assumed | SDK 34 confirmed in build.gradle.kts | — |
| `lifecycle-runtime-ktx` | D-08: `lifecycleScope` | YES (transitive) | 2.7.0 | — |
| `kotlinx-coroutines-test` | Unit tests for threading | YES (explicit `testImplementation`) | 1.7.3 | — |
| Xcode 15+ | iOS build | Assumed per CLAUDE.md | 15+ | — |
| Physical EP-133 device | USB reconnect testing | UNKNOWN — cannot verify | — | Use Android emulator + mock MIDI for unit tests; reconnect test requires hardware |

**Missing dependencies with no fallback:**
- Physical EP-133 device for CONN-02 acceptance testing (reconnect after cable replug). Emulator cannot simulate USB attach/detach events reliably.

**Missing dependencies with fallback:**
- USB attach/detach simulation: Android emulator does not support USB MIDI. Use `FakeMIDIPort` / `TestMIDIRepository` for unit tests. CONN-02 acceptance test is a manual checklist item.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 + `kotlinx-coroutines-test` 1.7.3 (unit); Compose UI test + `createComposeRule` (instrumented) |
| Config file | None explicitly — standard Gradle test runner |
| Quick run command | `./gradlew :app:test` (unit tests, JVM-only, no device needed) |
| Full suite command | `./gradlew :app:connectedAndroidTest` (requires connected device or emulator) |
| iOS | Xcode Test Navigator → run all (no separate CLI command without Fastlane) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CONN-01 | Connection indicator visible on all screens | Instrumented Compose | `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ep133.sampletool.NavigationTest` | Partial — `NavigationTest.kt` exists; needs connection indicator assertion |
| CONN-01 | StateFlow emits connected=true when device added | Unit | `./gradlew :app:test --tests "*.MIDIRepositoryTest"` | No — Wave 0 gap |
| CONN-02 | onDeviceAdded triggers device re-enumeration | Unit | `./gradlew :app:test --tests "*.MIDIManagerTest"` | No — Wave 0 gap |
| CONN-02 | No stale state after cable replug | Manual (requires hardware) | — | Manual checklist |
| CONN-03 | hasPermission() gates requestPermission() | Unit | `./gradlew :app:test --tests "*.MIDIManagerTest"` | No — Wave 0 gap |
| CONN-04 | DeviceScreen shows "Awaiting" state | Instrumented Compose | `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ep133.sampletool.DeviceScreenTest` | Partial — `DeviceScreenTest.kt` exists; needs awaiting/denied state tests |
| CONN-04 | DeviceScreen shows "Permission denied" state | Instrumented Compose | Same as above | Partial — needs new test case |

### Threading-Specific Tests

The most critical Phase 1 tests validate the threading fixes. These are unit tests that do not require a device:

**Test 1: MIDI callback dispatches to main thread (Android)**
```kotlin
// AndroidApp/app/src/test/java/com/ep133/sampletool/MIDIManagerThreadingTest.kt
@Test
fun `onMidiReceived callback is invoked on main thread`() {
    val mainLooper = Looper.getMainLooper()
    var callbackThreadId: Long? = null

    val fakePort = FakeMIDIPort()
    fakePort.onMidiReceived = { _, _ ->
        callbackThreadId = Thread.currentThread().id
    }
    // Simulate MIDI thread callback
    fakePort.simulateMidiReceive("port1", byteArrayOf(0x90.toByte(), 60, 100))

    // Shadow test: verify callback thread matches main looper thread
    assertThat(callbackThreadId).isEqualTo(mainLooper.thread.id)
}
```

**Test 2: SequencerEngine scope cancels on close()**
```kotlin
@Test
fun `SequencerEngine close cancels all running jobs`() = runTest {
    val sequencer = SequencerEngine(TestMIDIRepository())
    sequencer.play()
    assertThat(sequencer.state.value.playing).isTrue()

    sequencer.close()
    // After close, no more state updates should arrive
    // Verify job is cancelled by checking scope isCancelled
}
```

**Test 3: StateFlow threading (verifies no IllegalStateException)**
```kotlin
@Test
fun `MIDIRepository deviceState updates from background thread do not throw`() = runTest {
    val repo = MIDIRepository(TestMIDIPort())
    val results = mutableListOf<DeviceState>()

    backgroundScope.launch { repo.deviceState.collect { results.add(it) } }

    // Simulate device change from non-main thread
    withContext(Dispatchers.IO) { repo.refreshDeviceState() }

    advanceUntilIdle()
    assertThat(results).isNotEmpty()
}
```

### Wave 0 Gaps

- [ ] `AndroidApp/app/src/test/java/com/ep133/sampletool/MIDIManagerThreadingTest.kt` — covers CONN-01, CONN-02 threading correctness
- [ ] `AndroidApp/app/src/test/java/com/ep133/sampletool/MIDIRepositoryTest.kt` — covers CONN-01 StateFlow emissions, CONN-03 permission gating
- [ ] `AndroidApp/app/src/test/java/com/ep133/sampletool/SequencerEngineScopeTest.kt` — covers scope cancellation after `close()`
- [ ] New test cases in `DeviceScreenTest.kt` — covers CONN-04 awaiting/denied states
- [ ] Framework install: `src/test/` directory does not exist — must be created. JUnit 4 + `kotlinx-coroutines-test` are already in `testImplementation` dependencies.

### Sampling Rate

- **Per task commit:** `./gradlew :app:test` (unit tests, JVM, ~30s)
- **Per wave merge:** `./gradlew :app:connectedAndroidTest` (requires emulator or device, ~3-5 min)
- **Phase gate:** Full unit suite green + DeviceScreen instrumented tests green before `/gsd:verify-work`. Physical EP-133 reconnect test is a manual checklist item outside of automated CI.

---

## Project Constraints (from CLAUDE.md)

- **Kotlin:** Prefer `val` over `var`; use `when` expressions; no `GlobalScope`; always `viewModelScope`/`lifecycleScope`/supervised scope
- **Coroutines:** `withContext(Dispatchers.IO)` for disk/network; `Flow` over callbacks; always rethrow `CancellationException`
- **Compose:** State hoisting; `remember` for expensive computations; `LaunchedEffect` for coroutines; `DisposableEffect` for cleanup
- **Error handling Kotlin:** Never swallow exceptions with empty catch; `Result<T>` or sealed classes for expected errors; `Log.e(TAG, message, exception)` always includes throwable
- **Python standards:** Not applicable to this phase
- **Git:** Conventional commits (`feat`, `fix`, `refactor`); never commit `.env`/secrets/APKs; run test suite before pushing

---

## Sources

### Primary (HIGH confidence — direct source code reading)

- `/Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp/app/src/main/java/com/ep133/sampletool/midi/MIDIManager.kt` — Line-level analysis of threading bug (line 253), `mainHandler` pattern (line 275), 500ms delay (lines 66-68), `openOrGetDevice` (lines 304-323)
- `/Users/thomasphillips/workspace/ep_133_sample_tool/iOSApp/EP133SampleTool/MIDI/MIDIManager.swift` — Line-level analysis of `onMIDIReceived` bug (line 177), `sendRawBytes` allocation bug (lines 136-143), `handleMIDINotification` dispatch pattern (lines 192-194)
- `/Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp/app/src/main/java/com/ep133/sampletool/domain/sequencer/SequencerEngine.kt` — Scope declaration (line 77), note-off fire-and-forget launches (line 191)
- `/Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp/app/src/main/java/com/ep133/sampletool/MainActivity.kt` — Manual `CoroutineScope` (line 39), `onDestroy` cleanup (lines 105-113)
- `/Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/MIDIRepository.kt` — `_deviceState.value` mutation pattern (lines 45-51, 87-93), call chain thread audit
- `/Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp/app/src/main/java/com/ep133/sampletool/midi/MIDIPort.kt` — Kotlin interface definition (all methods)
- `/Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp/app/src/main/java/com/ep133/sampletool/ui/EP133App.kt` — `NavigationBar` structure; connection indicator insertion point
- `/Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp/app/src/main/java/com/ep133/sampletool/ui/theme/Color.kt` — `TEColors` palette verified: `TEColors.Teal = Color(0xFF00A69C)`
- `.planning/phases/01-midi-foundation/01-CONTEXT.md` — All locked decisions D-01 through D-20
- `.planning/codebase/CONCERNS.md` — Tech debt audit with line references
- `.planning/research/PITFALLS.md` — Pitfalls 1-3 (directly addressed by this phase)
- `AndroidApp/app/build.gradle.kts` — Dependency version confirmation; `lifecycle-runtime-ktx:2.7.0` transitive availability confirmed via Gradle dependency tree

### Secondary (MEDIUM confidence)

- `kotlinx-coroutines` documentation: `MutableStateFlow.value` is documented as thread-safe; `MutableSharedFlow.tryEmit()` is thread-safe from any thread — consistent with Pitfalls research citations
- Android `MidiManager.DeviceCallback` API: device callback contract confirmed via codebase usage patterns at MIDIManager.kt lines 44-55

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all dependencies verified present in build.gradle.kts or as confirmed transitives
- Architecture patterns: HIGH — all patterns verified from actual source lines with line number references
- Threading fixes: HIGH — root cause confirmed by reading exact bug locations; fix pattern confirmed from existing correct code in same files
- iOS protocol design: HIGH — verified against Kotlin MIDIPort.kt; iOS 16 constraint from CONTEXT.md D-03
- Pitfalls: HIGH — derived from codebase analysis and PITFALLS.md (prior research)
- Test infrastructure: MEDIUM — test files confirmed present; coroutines-test confirmed; iOS test infrastructure entirely absent

**Research date:** 2026-03-28
**Valid until:** 2026-04-28 (stable domain — no fast-moving external dependencies)
