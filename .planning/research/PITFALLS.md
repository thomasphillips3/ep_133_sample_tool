# Pitfalls Research

**Domain:** Native mobile music device management — iOS (CoreMIDI + SwiftUI) and Android (android.media.midi + Jetpack Compose) wrapping a USB MIDI hardware controller (Teenage Engineering EP-133 K.O. II)
**Researched:** 2026-03-27
**Confidence:** HIGH (codebase-grounded) / MEDIUM (EP-133 protocol specifics — no public SysEx spec)

---

## Critical Pitfalls

### Pitfall 1: MIDI Callbacks Mutating Compose State Directly

**What goes wrong:**
The Android `MidiReceiver.onSend()` callback fires on an internal MIDI reader thread — not the main thread. If that callback (or anything it calls) writes to a `mutableStateOf` or directly mutates a `MutableStateFlow` value while a Compose snapshot is being applied, Android throws `IllegalStateException: Reading a state that was created after the snapshot was taken or in a snapshot that has not yet been applied`. The app crashes, often non-deterministically, because it depends on race timing.

**Why it happens:**
`MutableStateFlow.value = ...` is thread-safe for emission. But `mutableStateOf` is NOT safe to write from background threads, and it is easy to mix the two patterns in a growing codebase. The existing `MIDIRepository` uses `MutableStateFlow` (correct), but ViewModel state that reaches Compose via `collectAsState()` must still originate from the main-thread-dispatched flow. As features expand and ViewModels handle more MIDI-driven state, developers add direct `mutableStateOf` assignments in ViewModel coroutines launched on `Dispatchers.Default` or `Dispatchers.IO` — this is where the crash appears.

The existing codebase already has one instance: `SequencerEngine` creates `CoroutineScope(Dispatchers.Default)` (flagged in CONCERNS.md). Any state mutation inside that scope that reaches Compose is at risk.

**How to avoid:**
- Keep all `MutableStateFlow` and `mutableStateOf` mutations on `Dispatchers.Main`.
- In MIDI callback chains: receive on background thread → process → `withContext(Dispatchers.Main)` before updating any state.
- Never use `mutableStateOf` in a ViewModel — use `MutableStateFlow` exclusively, which is thread-safe for emission. Collect with `collectAsState()` or `collectAsStateWithLifecycle()` in composables.
- The fix for `SequencerEngine`: add `SupervisorJob()` to the scope and ensure any state exposed to Compose is updated via `withContext(Dispatchers.Main)`.

**Warning signs:**
- Non-deterministic `IllegalStateException` crashes in logcat with stack traces through `SnapshotKt` or `Snapshot.takeMutableSnapshot`.
- Crashes that only appear under load (fast MIDI clock, rapid pad tapping, quick pattern switching).
- Compose state appearing stale or updating in bursts rather than immediately.

**Phase to address:**
Any phase building new MIDI-driven Compose screens. Establish the pattern in the first new screen built and enforce it. The `SequencerEngine` scope leak (CONCERNS.md) should be fixed in the same phase that builds the Beats/sequencer feature.

---

### Pitfall 2: iOS `evaluateJavaScript` Called from CoreMIDI Callback Thread

**What goes wrong:**
The iOS `MIDIBridge` pushes incoming MIDI to the web layer by calling `webView.evaluateJavaScript(...)`. CoreMIDI delivers `MIDIReadProc` / `MIDIInputPortCreateWithProtocol` callbacks on a private CoreMIDI thread — not the main thread. `WKWebView.evaluateJavaScript` must be called on the main thread. Calling it from a CoreMIDI callback causes a `WKWebView.evaluateJavaScript(_:completionHandler:) must be used from main thread only` crash, enforced by Xcode's Main Thread Checker and triggered at runtime on device.

**Why it happens:**
The current iOS `MIDIManager` correctly dispatches `onDevicesChanged` to `DispatchQueue.main` (line 192 of `MIDIManager.swift`). However, the `onMIDIReceived` callback does NOT dispatch to main — it fires directly from the CoreMIDI thread (line 177: `onMIDIReceived?(portId, bytes)`). When `MIDIBridge` receives this and calls `evaluateJavaScript`, it is still on the CoreMIDI thread. This is a latent crash that has not yet manifested because iOS's WKWebView wrapper is only used for the fallback web layer; it will become critical when the iOS native SwiftUI build begins forwarding MIDI events to the UI.

**How to avoid:**
Dispatch `onMIDIReceived` to the main queue before forwarding to any WKWebView or SwiftUI `@Published`/`@Observable` state:

```swift
// In MIDIManager.handleMIDIInput:
DispatchQueue.main.async { [weak self] in
    self?.onMIDIReceived?(portId, bytes)
}
```

For SwiftUI state updates, use `@MainActor` on the bridge class or `await MainActor.run { }` inside async contexts. Do not rely on `@MainActor` annotation alone if the callback path goes through non-async C function callbacks (CoreMIDI uses C APIs with no Swift concurrency awareness).

**Warning signs:**
- Xcode Main Thread Checker purple runtime warning: "UIKit API called on a background thread" or "WKWebView ... must be used from main thread".
- Crashes only during active MIDI data flow (not idle), especially during rapid SysEx transfers.
- SwiftUI state updates arriving out of order or causing `@Published` publisher errors.

**Phase to address:**
iOS native UI build phase — before any SwiftUI screen subscribes to MIDI events. Fix `onMIDIReceived` dispatch before wiring it to SwiftUI.

---

### Pitfall 3: Android USB Permission Race Condition on Device Attach

**What goes wrong:**
The `android.media.midi` API will not enumerate a USB MIDI device until the app has been granted USB permission via `UsbManager.requestPermission()`. There is a race condition in the current code: `getUSBDevices()` returns empty, calls `requestUSBPermissions()`, which shows the system dialog. The user taps "Allow." The `usbPermissionReceiver` fires and calls `notifyDevicesChanged()` after a hardcoded 500ms delay (line 66-68, `MIDIManager.kt`). If 500ms is not enough for the MIDI service to enumerate the device, the second call to `getUSBDevices()` also returns empty and the UI shows "No device found." The user sees a connected device that the app cannot find.

The delay approach is fragile: on slower devices or when multiple USB devices are attached simultaneously, 500ms is insufficient.

**Why it happens:**
Android's USB permission grant and MIDI device enumeration are asynchronous and loosely coupled. `UsbManager` permission and `MidiManager` device availability are managed by different system services. There is no callback for "MIDI device is now ready after permission grant" — only the `DeviceCallback.onDeviceAdded` from `MidiManager`, which fires when the MIDI service independently discovers the device. This can happen before or after the `ACTION_USB_PERMISSION` broadcast, and with variable delay.

**How to avoid:**
- Remove the hardcoded 500ms delay and instead rely exclusively on `MidiManager.DeviceCallback.onDeviceAdded` to trigger re-enumeration. If permission is granted but `onDeviceAdded` never fires (a known issue on some Android versions), implement an exponential-backoff retry that polls `midiManager.devices` — max 3 retries, 200ms apart — before giving up and showing an error.
- When upgrading to the non-deprecated API (API 33+): `registerDeviceCallback(transport, executor, callback)` requires specifying a transport type. Use `MidiDeviceInfo.TRANSPORT_USB` to filter correctly.
- Add explicit UI state for "Waiting for device permission..." distinct from "No device connected."

**Warning signs:**
- Users reporting "device not found" while device is physically connected.
- Logcat showing `MIDI device added` from `MidiManager` arriving 600ms+ after the permission broadcast.
- Tests on older/slower Android devices failing to find device on first connection.

**Phase to address:**
Connection robustness phase — any phase that addresses the "robust USB connection handling" requirement from PROJECT.md.

---

### Pitfall 4: SysEx Messages Split Across `onSend` Calls (Android)

**What goes wrong:**
`MidiReceiver.onSend()` does not guarantee that a complete SysEx message (`F0 ... F7`) arrives in a single call. The Android MIDI subsystem can fragment large SysEx messages across multiple `onSend()` invocations: the first call delivers `F0 [data...]`, subsequent calls deliver continuation bytes, and the final call delivers `[...data] F7`. If the app treats each `onSend()` call as a complete message, SysEx parsing will silently fail for any transfer larger than one USB packet (~64 bytes for full-speed USB).

For the EP-133, backup/restore operations transfer entire project SysEx dumps — these are kilobytes to megabytes in size and will definitely be fragmented. A silent failure here means corrupted backups with no error reported to the user.

**Why it happens:**
The EP-133 SysEx protocol encodes sample data and project state as long SysEx messages. USB MIDI framing splits MIDI data into packets at the transport layer. The Android MIDI stack reassembles within its pipe but does not guarantee atomic delivery of the entire SysEx to a single `onSend()` call. The current `MIDIManager.kt` passes `onSend` bytes directly to the callback without any SysEx reassembly logic.

**How to avoid:**
Implement a SysEx accumulation buffer in `MIDIManager`:
- On `onSend()`, append incoming bytes to a buffer.
- If a `0xF0` status byte is seen, start accumulating.
- When `0xF7` is received, deliver the complete buffer to the callback and reset.
- Add a size guard: if the buffer exceeds a reasonable limit (e.g. 4MB) without `0xF7`, emit an error.
- Handle the "SysEx continuation" case: some MIDI implementations send F7 only on the final fragment, not between fragments.

**Warning signs:**
- Backup/restore works for short patterns but fails (silent corruption or timeout) for large projects.
- `onMidiReceived` callback occasionally receives byte arrays starting mid-message (no `0xF0`).
- EP-133 showing a transfer progress bar that stalls partway through and then resets.

**Phase to address:**
Project management phase — any phase implementing backup/restore, project save/load, or sample upload/download to the EP-133.

---

### Pitfall 5: iOS SysEx Buffer Overflow in `sendRawBytes`

**What goes wrong:**
`MIDIManager.sendRawBytes()` (line 136-143, `MIDIManager.swift`) allocates a `MIDIPacketList` with a fixed size of `MemoryLayout<MIDIPacketList>.size + data.count`. `MIDIPacketList` is declared with `capacity: 1` (one packet), and `MIDIPacketListAdd` is called once to add the entire payload. For SysEx messages larger than approximately 65,535 bytes (the maximum `MIDIPacketList` data size, bounded by the 16-bit length field in `MIDIPacket`), this silently truncates or corrupts the transfer. EP-133 project dumps can be larger than this limit.

Additionally, the legacy `MIDIPacketList` API is deprecated in iOS 11+ in favor of `MIDIEventList`. The `sendRawBytes` path is only reached for SysEx — the same path that handles the highest-volume data transfers.

**Why it happens:**
The existing `sendRawBytes` implementation was written for correctness on short SysEx, not for large transfers. The `capacity: 1` in `UnsafeMutablePointer<MIDIPacketList>.allocate(capacity: 1)` refers to allocating one `MIDIPacketList` struct, not one packet of data — but the underlying `MIDIPacketListAdd` must stay within the allocated memory, which is bounded by `packetListSize`. For very large SysEx, the `MIDIPacketListAdd` call silently clips data when the packet size exceeds the allocated buffer.

**How to avoid:**
For iOS 11+ (which this project targets at iOS 16+), replace `sendRawBytes` with a proper `MIDIEventList`-based SysEx sender using `MIDIEventListAdd` in a loop, or use the `MIDISendSysex()` convenience function which handles chunking automatically. If using `MIDISendSysex()`, note it is asynchronous — the `MIDISysexSendRequest` struct must remain valid until the completion callback fires (do not stack-allocate).

**Warning signs:**
- SysEx transfers that appear to succeed (no error returned) but the EP-133 rejects the data or shows a corrupted project.
- Transfers work correctly for small projects (<64KB) but fail silently for full backups.
- No crash, no error log — the failure is invisible at the API level.

**Phase to address:**
iOS project management phase — before implementing any backup/restore or sample transfer features on iOS.

---

### Pitfall 6: Android Scoped Storage — Direct File Paths Break on API 29+

**What goes wrong:**
Android 10 (API 29, the minimum SDK for this project) enforces scoped storage. Apps cannot use `java.io.File` to access paths outside their app-specific directories (`Context.getFilesDir()`, `Context.getCacheDir()`) or the app's external storage directory. Specifically: you cannot browse or save to `Environment.getExternalStorageDirectory()` or construct a `File("/sdcard/...")` path. The `listFiles()` method on external paths returns `null` without throwing an exception, silently failing.

For this app, this becomes critical during:
1. Project export — saving a backup `.pak` file to a user-visible location.
2. Project import — letting the user pick a previously-exported file.
3. Sample import — loading `.wav`/`.aiff` files from the user's Downloads or music library.

**Why it happens:**
The existing app bundles `data/ep-133-factory-content-DRyE_DHC.pak` internally and loads it from assets — this works fine. But when the app needs to write a user-facing project file or read a user-provided sample, it must go through the Storage Access Framework (SAF) or `MediaStore`. The intuitive approach — constructing a `File` path — compiles and runs without error but silently returns empty results on API 29+.

**How to avoid:**
- For file picks (import): use `ActivityResultContracts.OpenDocument` with appropriate MIME types. Never construct `File` paths from the result URI — use `ContentResolver.openInputStream(uri)` to read.
- For file saves (export): use `ActivityResultContracts.CreateDocument`. Write via `ContentResolver.openOutputStream(uri)`.
- For reading from `MediaStore` (audio files for sample import): use `MediaStore.Audio.Media` query API, not `File` paths.
- App-private files (session state, cached data): `Context.getFilesDir()` — no restrictions.
- Never use `requestLegacyExternalStorage` in the manifest as a workaround; it is ignored on API 30+ and is the wrong solution.

**Warning signs:**
- `file.listFiles()` returning `null` with no exception.
- `file.exists()` returning `false` for a file the user just selected.
- Export appearing to succeed (no exception thrown) but file not visible in Files app.
- Crash with `FileUriExposedException` when passing a `file://` URI to another app.

**Phase to address:**
Project management phase (save/load/export/import). Establish the SAF pattern in the first file I/O feature built and reuse it.

---

### Pitfall 7: iOS Security-Scoped Resource Access Not Released

**What goes wrong:**
When the user picks a file via `fileImporter` (SwiftUI) or `UIDocumentPickerViewController`, the returned URL is a security-scoped bookmark that requires an explicit `url.startAccessingSecurityScopedResource()` call before reading, and a matching `url.stopAccessingSecurityScopedResource()` after. Forgetting the `stop` call leaks a kernel resource. Leaking enough of these causes the OS to revoke all security-scoped resource access for the app — subsequent picks silently fail, even for files the user just selected. The leak does not crash the app and is invisible until the quota is exhausted.

**Why it happens:**
SwiftUI's `fileImporter` modifier makes picking easy but does not handle the security scope lifecycle — that responsibility remains with the developer. The iOS 17 `ShareLink` / `Transferable` path has an additional undocumented change: sharing a file directly to the system Files app from an internal URL fails silently on iOS 17 (returns an empty Files view). The workaround is to copy the file to a temporary location before sharing.

**How to avoid:**
- Always use `defer { url.stopAccessingSecurityScopedResource() }` immediately after the `startAccessing...` call, or copy the file to `FileManager.default.temporaryDirectory` within the scoped block and then work with the copy.
- For export to Files app on iOS 17+: copy to `FileManager.default.temporaryDirectory` first, then share the temp URL.
- Test on iOS 17 specifically — behavior diverged from iOS 16 without documentation.

**Warning signs:**
- File import works for the first N picks in a session, then silently fails.
- `startAccessingSecurityScopedResource()` returning `false` for a URL that was just granted by the picker.
- "No valid file provider found from URL" console warning.

**Phase to address:**
iOS project management phase (save/load/export). Establish the correct security-scoped resource pattern before building any file I/O.

---

### Pitfall 8: `MidiManager.registerDeviceCallback` Deprecated API — Silent Failure Path on Future Android

**What goes wrong:**
`MIDIManager.kt` calls the deprecated `midiManager.registerDeviceCallback(deviceCallback, mainHandler)` (line 76, with `@Suppress("DEPRECATION")`). The replacement API introduced in API 33 is `registerDeviceCallback(transport, executor, callback)` and adds a required `transport` parameter. If the deprecated overload is eventually removed (possible in a future API level), the app fails to receive device connection/disconnection notifications — MIDI devices appear permanently stuck in their last-known state. There is no crash; the UI simply never updates.

The current `@Suppress("DEPRECATION")` suppresses the warning and makes this invisible during development. The same applies to `midiManager.openDevice()` (deprecated in API 33) and `UsbDevice.getParcelableExtra()` (deprecated in API 33).

**How to avoid:**
Migrate to the API 33 overloads with a runtime version check:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    midiManager.registerDeviceCallback(
        MidiDeviceInfo.TRANSPORT_USB,
        mainExecutor,
        deviceCallback
    )
} else {
    @Suppress("DEPRECATION")
    midiManager.registerDeviceCallback(deviceCallback, mainHandler)
}
```
Do this migration before targeting SDK 35+ (the project currently targets SDK 34).

**Warning signs:**
- `@Suppress("DEPRECATION")` annotations on MIDI API calls (already present in codebase).
- Build warnings increasing as min/target SDK is raised.
- Device connection events not firing on Android 13+ test devices in the future.

**Phase to address:**
Any phase upgrading targetSdk to 35 (referenced in CONCERNS.md as needed for Play Store). Do not ship to Play Store before migrating.

---

### Pitfall 9: `connectAllSources()` Reconnects All Sources on Every Device Change (iOS)

**What goes wrong:**
`MIDIManager.connectAllSources()` (line 206-223, `MIDIManager.swift`) disconnects ALL sources and reconnects ALL sources every time a device is added or removed. If the EP-133 is one of multiple MIDI sources, every device change event briefly drops all connections (including the EP-133) and reestablishes them. Any MIDI data in-flight during this window is lost. More critically, `connectAllSources()` is called from `handleMIDINotification`, which fires on the CoreMIDI notification thread — not the main thread. The `connectedSources` Set is mutated from this thread, which is not thread-safe.

**Why it happens:**
The implementation is the simplest correct approach for a single-device scenario. It works for a single EP-133 but will cause dropped messages in a multi-device setup and has a data race if `connectAllSources()` is called concurrently from multiple notifications.

**How to avoid:**
- Maintain a dictionary mapping `MIDIEndpointRef` to connection state. On `msgObjectAdded`, connect only the new source. On `msgObjectRemoved`, disconnect only the removed source.
- Protect mutations to `connectedSources` with a serial dispatch queue or an actor.
- The notification callback already exists — the fix is additive and does not require architectural change.

**Warning signs:**
- Occasional "dropped" MIDI messages during device plug/unplug events.
- Race condition crashes (EXC_BAD_ACCESS) in `connectAllSources()` under concurrent notification delivery.
- Thread Sanitizer reporting data races on `connectedSources`.

**Phase to address:**
iOS native UI build phase — fix before wiring MIDI to any SwiftUI screen, since this will expose the thread safety issue.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Hardcoded 500ms delay after USB permission grant | Avoids race condition complexity | Silently fails on slow devices; not reliable | Never — replace with `onDeviceAdded` callback |
| Port ID as `"${deviceId}_in_${portNumber}"` string | Simple serialization | Breaks silently if device ID contains underscore; no type safety | Replace with a `data class PortId` before project management phase |
| `CoroutineScope(Dispatchers.Default)` in `SequencerEngine` | Simple to write | Scope never cancelled; note-off coroutines accumulate | Replace with `lifecycleScope` or properly scoped `SupervisorJob` |
| Manual DI wiring in `MainActivity.onCreate()` | No framework dependency | Every new feature requires editing `MainActivity`; ViewModels recreated on config change | Acceptable until second platform feature is added, then migrate to Hilt |
| `@Suppress("DEPRECATION")` on all MIDI APIs | Eliminates build warnings | Masks actual migration work; breaks when APIs are removed | Acceptable only until targetSdk 35 upgrade |
| Bare `WebView` in `SampleManagerPanel` without MIDI bridge | Fast to wire up | MIDI operations silently fail for users in that panel | Never ship — fix before any Backup/Restore feature |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Android MIDI device enumeration | Calling `getUSBDevices()` before USB permission is granted | Check `usbManager.hasPermission()` first; show "Tap to connect" UI state until `onDeviceAdded` fires |
| iOS CoreMIDI + SwiftUI | Updating `@Published` / `@Observable` from CoreMIDI callback thread | Always dispatch to `DispatchQueue.main` or `MainActor` before any state mutation |
| Android SAF file picker | Using `file://` URIs returned from intent results | Always use `ContentResolver.openInputStream(uri)`; never convert to `File` |
| iOS file sharing (iOS 17+) | Sharing internal app bundle URLs directly to Files app | Copy to `FileManager.default.temporaryDirectory` before passing to `UIActivityViewController` |
| EP-133 SysEx transfers | Treating each MIDI callback as a complete message | Accumulate bytes until `0xF7` terminator; buffer across multiple callbacks |
| Android MIDI send | Calling `MidiInputPort.send()` from main thread for large SysEx | Send from a background coroutine; `send()` can block briefly for large payloads |
| WKWebView + CoreMIDI bridge | Calling `evaluateJavaScript` from MIDI thread | Always dispatch `evaluateJavaScript` to `DispatchQueue.main` |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Iterating all MIDI destinations on every `sendMIDI` call (iOS) | MIDI note latency increases as more MIDI devices are connected | Cache destination `MIDIEndpointRef` by port ID; invalidate on `onDevicesChanged` | Noticeable at 3+ MIDI devices; real-time pad use (fast tapping) |
| Re-enumerating MIDI devices synchronously on every `getUSBDevices()` call | UI jank when MIDI screen is opened | Cache device list; only refresh on `onDevicesChanged` / `onDeviceAdded` | Any time device list is rendered in a LazyColumn or List |
| 36MB asset bundle in APK | Long cold-start load; Play Store download size | Separate native-UI assets from WebView assets; defer `.pak` loading | Every install; no scale threshold — it's a constant cost |
| SysEx buffer growing unbounded without timeout | Memory exhaustion during failed transfers | Add max buffer size guard + timeout (30s) that cancels partial SysEx and reports error | Any EP-133 project dump transfer that is interrupted mid-stream |
| `connectAllSources()` called on every device notification (iOS) | Brief MIDI drop during USB plug/unplug | Incremental connect/disconnect instead of reconnect-all | Noticeable on first hot-plug; severe with 2+ MIDI sources |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| `allowFileAccessFromFileURLs = true` in WebView (Android) | JavaScript in WebView can read arbitrary files on device | Remove both `allowFile*` flags; `WebViewAssetLoader` does not need them. Test that web app still loads from `https://appassets.androidplatform.net` |
| Manual JS string escaping in `MIDIBridge.swift` (`escapeJS()`) | MIDI device names with unusual characters could inject JavaScript into `evaluateJavaScript` | Replace manual escaping with `JSONSerialization` to produce a properly-encoded JSON string, then pass the JSON string to JS |
| WKWebView polyfill injected by URL path match only | If a loaded page spoofs the path, it receives the MIDI bridge | Low risk given bundle-only asset loading; add `Content-Security-Policy` meta tag to `index.html` as defense-in-depth |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No distinct "waiting for USB permission" state | User sees "No device found" after granting permission while app re-enumerates | Three-state device UI: "Not connected", "Awaiting permission", "Connected" |
| Hardcoded device statistics in `DeviceScreen` (storage %, sample count, firmware) | User sees fabricated data; loses trust in app | Either query via SysEx or remove the stats fields entirely until real data is available |
| Silent failure when MIDI send port is closed | User taps a pad, nothing happens — no error visible | Show connection error in the pad/beat UI when `outputPortId` is nil; toast or status indicator |
| No visual feedback during SysEx transfers | Long backup/restore operations look frozen | Show a progress indicator during any transfer expected to take >500ms; EP-133 transfers can take 10-30 seconds |
| MIDI device confusion due to inverted Android naming convention | "Input" and "Output" labels are backwards relative to user expectation | In UI, use "From EP-133" / "To EP-133" labels, not the API's `MidiInputPort`/`MidiOutputPort` terminology |

---

## "Looks Done But Isn't" Checklist

- [ ] **USB MIDI Connection:** Works on developer device, but test that device appears correctly after: (1) fresh install, (2) hot-plug after app launch, (3) USB cable swap, (4) device power cycle while connected.
- [ ] **SysEx Transfers:** Small patterns transfer OK, but verify with a full project backup (all banks populated) — this exercises the fragmentation path.
- [ ] **File Export:** File appears in app's confirmation UI, but verify it is visible and openable in the system Files app on both platforms.
- [ ] **Background Behaviour:** MIDI works while app is foregrounded. Verify what happens when the user receives a phone call, switches apps briefly, and returns — the EP-133 connection should recover automatically.
- [ ] **iOS `evaluateJavaScript` thread safety:** The WKWebView bridge works in the simulator (single-threaded), but test on physical device with active MIDI data flow to surface the Main Thread Checker violation.
- [ ] **Android deprecated MIDI API:** All `@Suppress("DEPRECATION")` annotations compile and pass tests today, but verify the app correctly enumerates devices on API 33+ (Android 13) where the new executor-based API is preferred.
- [ ] **Compose state threading:** Pad tapping and MIDI output work, but run the app under Android's threading strictmode and check for `IllegalStateException` in the MIDI → ViewModel → Compose state path.
- [ ] **iOS security-scoped resources:** First file import works, but verify that importing 10+ files in one session does not exhaust the scoped resource quota.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Compose state mutation from MIDI thread | MEDIUM | Add `withContext(Dispatchers.Main)` at the ViewModel boundary; no architectural change required |
| iOS `evaluateJavaScript` from wrong thread | LOW | Wrap all `evaluateJavaScript` calls in `DispatchQueue.main.async`; 1-line fix per call site |
| USB permission race / hardcoded delay | MEDIUM | Replace with `onDeviceAdded` callback logic; requires refactoring `notifyDevicesChanged` flow |
| SysEx fragmentation unhandled | HIGH | Retrofit a SysEx accumulation buffer into `MIDIManager`; requires auditing all `onMidiReceived` consumers to handle complete-message semantics |
| iOS `sendRawBytes` overflow | HIGH | Requires replacing the SysEx send path with chunked `MIDIEventList` API; test against real EP-133 SysEx dumps |
| Scoped storage File API usage | MEDIUM | Replace `File` path operations with SAF `ActivityResultContracts`; may require UI changes for file picker flows |
| iOS security-scoped resource leak | LOW–MEDIUM | Add `defer { url.stopAccessingSecurityScopedResource() }` at all pick call sites; audit with Instruments for resource leaks |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Compose state mutation from MIDI thread | Any phase building new MIDI-driven screens | Android ThreadStrictMode passes; no `IllegalStateException` in logcat under load |
| iOS `evaluateJavaScript` from CoreMIDI thread | iOS native UI build phase (before first SwiftUI screen) | Xcode Main Thread Checker clean during active MIDI flow on physical device |
| USB permission race condition | Connection robustness phase | Device correctly found on hot-plug, permission grant, and cable swap without hardcoded delays |
| SysEx fragmentation (Android) | Project management phase (backup/restore) | Full project backup completes correctly for a fully-populated EP-133 |
| iOS `sendRawBytes` overflow (SysEx) | iOS project management phase | Large SysEx transfer (>64KB) completes without silent truncation; verify with SysEx dump diff |
| Android scoped storage / File API | Project management phase (save/load) | File is visible in system Files app after export; import works on API 29–35 |
| iOS security-scoped resource lifecycle | iOS project management phase | 20 consecutive file imports in one session all succeed |
| `connectAllSources()` thread safety (iOS) | iOS native UI build phase | Thread Sanitizer clean on device-connect/disconnect during active data flow |
| Deprecated Android MIDI API | targetSdk 35 upgrade phase | App enumerates devices correctly on API 33+ without `@Suppress("DEPRECATION")` |
| Hardcoded 500ms permission delay | Connection robustness phase | Device found reliably on slow Android devices after permission grant |
| Port ID string parsing | Domain layer hardening phase | Unit tests cover port IDs with non-standard characters; `data class` replaces string format |

---

## Sources

- Android Developer Documentation: [Foreground Service Types (Android 14)](https://developer.android.com/develop/background-work/services/fgs/service-types), [MidiManager API](https://developer.android.com/reference/android/media/midi/MidiManager), [Scoped Storage](https://developer.android.com/training/data-storage/)
- AOSP: [android.media.midi MidiOutputPort source (API 29)](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-29/+/refs/heads/androidx-browser-release/android/media/midi/MidiOutputPort.java)
- Apple Developer Documentation: [CoreMIDI](https://developer.apple.com/documentation/coremidi/), [Handling Audio Interruptions](https://developer.apple.com/documentation/avfaudio/handling-audio-interruptions)
- Jetpack Compose threading: [Google Issue Tracker #237985810 — IllegalStateException from background thread mutableStateOf](https://issuetracker.google.com/issues/237985810), [Kotlin Slack — Snapshot state from background thread](https://slack-chats.kotlinlang.org/t/505816/why-changing-snapshot-state-from-background-thread-during-co)
- iOS WKWebView threading: [WKWebView.evaluateJavaScript must be called on main thread — Cap-go issue](https://github.com/Cap-go/capacitor-inappbrowser/issues/181), [Apple Developer Forum — evaluateJavaScript threading](https://developer.apple.com/forums/thread/701553)
- iOS security-scoped resources: [Hacking with Swift — stopAccessingSecurityScopedResource](https://www.hackingwithswift.com/forums/ios/uidocumentviewcontroller-stopaccessingsecurityscopedresource/1250), [Apple Developer Forums — startAccessingSecurityScopedResource](https://developer.apple.com/forums/thread/741560)
- iOS 17 file sharing pitfall: [Addressing Transferable Protocol Issues in iOS 17](https://juniperphoton.substack.com/p/addressing-and-solving-transferable)
- EP-133 firmware changelog: [EP-133 What's New](https://teenage.engineering/guides/ep-133/whats-new) — "android USB MIDI connectivity resolved" (v1.2.2, 2024-04-23)
- Codebase: `/Users/thomasphillips/workspace/ep_133_sample_tool/.planning/codebase/CONCERNS.md` (2026-03-27), `AndroidApp/.../MIDIManager.kt`, `iOSApp/.../MIDIManager.swift`

---
*Pitfalls research for: native mobile USB MIDI device management (EP-133 iOS + Android)*
*Researched: 2026-03-27*
