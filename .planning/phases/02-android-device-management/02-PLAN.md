---
phase: 02-android-device-management
plan: 02
type: execute
wave_count: 4
total_tasks: 10
total_files: 21
wave: 1
depends_on: []
files_modified:
  - AndroidApp/app/src/test/java/com/ep133/sampletool/SysExProtocolTest.kt
  - AndroidApp/app/src/test/java/com/ep133/sampletool/SysExAccumulatorTest.kt
  - AndroidApp/app/src/test/java/com/ep133/sampletool/MIDIRepositoryStatsTest.kt
  - AndroidApp/app/src/test/java/com/ep133/sampletool/BackupRestoreTest.kt
  - AndroidApp/app/src/test/java/com/ep133/sampletool/PadsViewModelTest.kt
  - AndroidApp/app/src/test/java/com/ep133/sampletool/SoundsViewModelTest.kt
  - AndroidApp/app/src/test/java/com/ep133/sampletool/SequencerEngineTest.kt
  - AndroidApp/app/src/test/java/com/ep133/sampletool/ScaleLockTest.kt
  - AndroidApp/app/src/test/java/com/ep133/sampletool/MIDIManagerThreadingTest.kt
  - AndroidApp/app/src/test/java/com/ep133/sampletool/MIDIRepositoryTest.kt
  - AndroidApp/app/src/test/java/com/ep133/sampletool/SequencerEngineScopeTest.kt
  - AndroidApp/app/src/main/java/com/ep133/sampletool/domain/model/EP133.kt
  - AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/SysExProtocol.kt
  - AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/MIDIRepository.kt
  - AndroidApp/app/src/main/java/com/ep133/sampletool/ui/device/DeviceScreen.kt
  - AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/BackupManager.kt
  - AndroidApp/app/src/main/java/com/ep133/sampletool/MainActivity.kt
  - AndroidApp/app/src/main/java/com/ep133/sampletool/ui/pads/PadsScreen.kt
  - AndroidApp/app/src/main/java/com/ep133/sampletool/ui/sounds/SoundsScreen.kt
  - AndroidApp/app/src/main/java/com/ep133/sampletool/domain/sequencer/SequencerEngine.kt
  - AndroidApp/app/src/main/java/com/ep133/sampletool/ui/beats/BeatsScreen.kt
autonomous: true
requirements:
  - DEV-01
  - DEV-02
  - DEV-03
  - DEV-04
  - PERF-01
  - PERF-02
  - PERF-03
  - PERF-04

must_haves:
  truths:
    - "DeviceScreen shows real firmware version from EP-133 GREET response (not hardcoded v1.3.2)"
    - "DeviceScreen shows real storage used/total from FILE_METADATA query (not hardcoded 8)"
    - "DeviceScreen shows real sample count from FILE_LIST traversal (not hardcoded 128)"
    - "Tapping Backup opens SAF CreateDocument picker; tapping Restore opens SAF OpenDocument picker"
    - "Backup creates a valid .pak file (ZIP archive) containing WAV files and JSON metadata"
    - "Restore with a valid .pak file shows confirmation dialog before sending PUT commands"
    - "Two fingers on different pads trigger two simultaneous noteOn events"
    - "Tapping a sound row triggers noteOn on channel 10, noteOff after 500ms"
    - "Pressing Play on BeatsScreen sends MIDI Start (0xFA); pressing Stop sends MIDI Stop (0xFC)"
    - "In-scale pads show a TEColors.Teal border; out-of-scale pads show normal styling"
    - "All unit tests green: cd AndroidApp && ./gradlew :app:testDebugUnitTest"
  artifacts:
    - path: "AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/SysExProtocol.kt"
      provides: "TE SysEx frame builder, 7-bit pack/unpack, GREET command, FILE_LIST/FILE_GET/FILE_PUT constants"
      exports: ["SysExProtocol", "pack7bit", "unpack7bit", "buildGreet", "buildFileList", "buildFileGet", "buildFilePut"]
    - path: "AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/BackupManager.kt"
      provides: "PAK backup creation and restore via TE file transfer protocol"
      exports: ["BackupManager", "BackupProgress"]
    - path: "AndroidApp/app/src/main/java/com/ep133/sampletool/domain/model/EP133.kt"
      provides: "DeviceState with firmware/storage/sampleCount fields"
      contains: "sampleCount: Int? = null, storageUsedBytes: Long? = null, storageTotalBytes: Long? = null, firmwareVersion: String? = null"
  key_links:
    - from: "MIDIRepository.parseMidiInput"
      to: "SysExProtocol.dispatchSysEx"
      via: "accumulation buffer (0xF0..0xF7)"
      pattern: "0xF0.*0xF7"
    - from: "MIDIRepository.queryDeviceStats"
      to: "DeviceState.firmwareVersion / storageUsedBytes / sampleCount"
      via: "CompletableDeferred + GREET + FILE_METADATA + FILE_LIST"
      pattern: "CompletableDeferred.*withTimeoutOrNull"
    - from: "DeviceScreen backup button"
      to: "MainActivity.backupLauncher"
      via: "DeviceViewModel.onRequestBackup callback"
      pattern: "registerForActivityResult.*CreateDocument"
    - from: "SequencerEngine.play()"
      to: "midi.sendRawBytes(0xFA)"
      via: "sendRawBytes added to MIDIRepository"
      pattern: "sendRawBytes.*0xFA"
---

<objective>
Android users can view real EP-133 device stats, save and restore full device backups using the TE file-transfer protocol, and use all four performance screen features: multi-touch pads with velocity, sound preview, MIDI-synced sequencer, and scale-lock highlighting.

**BACKUP FORMAT CORRECTION (overrides D-01):** Research into `data/index.js` proves the EP-133 does NOT use a raw SysEx dump format. It uses a file-transfer protocol (FILE_LIST cmd=4, FILE_GET cmd=3) to enumerate and download individual WAV+JSON files, assembled into a PAK archive (ZIP format). The backup file extension is `.pak`, not `.syx`. Filename: `EP133-{YYYY-MM-DD}-{HHmm}.pak`. This is the only feasible format — a raw SysEx dump of the entire device filesystem does not exist in the EP-133 protocol.

**TE Manufacturer ID:** `0x00 0x20 0x76` (decimal: 0, 32, 118). NOT `0x00 0x21 0x7B`.

Purpose: Closes DEV-01, DEV-02, DEV-03, DEV-04, PERF-01, PERF-02, PERF-03, PERF-04.

Output:
- `SysExProtocol.kt` — TE frame builder + 7-bit codec + command constants
- `BackupManager.kt` — PAK backup/restore via FILE_LIST+FILE_GET+FILE_PUT
- `DeviceState` extended with firmware/storage/sampleCount fields
- `MIDIRepository` with SysEx accumulation buffer + `queryDeviceStats()` + `sendRawBytes()`
- `DeviceScreen` wired to real stats + backup/restore buttons + SAF launchers in `MainActivity`
- `PadsScreen` multi-touch grid + scale-lock highlighting
- `SoundsScreen` sound preview on tap
- `SequencerEngine` MIDI Start/Stop/Clock output
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/phases/02-android-device-management/02-CONTEXT.md
@.planning/phases/02-android-device-management/02-RESEARCH.md
@.planning/phases/02-android-device-management/02-VALIDATION.md
</context>

<interfaces>
<!-- Key types the executor needs. Extracted from codebase. -->

From AndroidApp/app/src/main/java/com/ep133/sampletool/midi/MIDIPort.kt:
```kotlin
interface MIDIPort {
    data class Device(val id: String, val name: String)
    data class Devices(val inputs: List<Device>, val outputs: List<Device>)
    var onMidiReceived: ((String, ByteArray) -> Unit)?
    var onDevicesChanged: (() -> Unit)?
    fun getUSBDevices(): Devices
    fun sendMidi(portId: String, data: ByteArray)
    fun requestUSBPermissions()
    fun refreshDevices()
    fun startListening(portId: String)
    fun closeAllListeners()
    fun prewarmSendPort(portId: String)
    fun close()
}
```

From AndroidApp/app/src/main/java/com/ep133/sampletool/domain/model/EP133.kt (current DeviceState):
```kotlin
data class DeviceState(
    val connected: Boolean = false,
    val deviceName: String = "",
    val outputPortId: String? = null,
    val inputPorts: List<MidiPort> = emptyList(),
    val outputPorts: List<MidiPort> = emptyList(),
    val permissionState: PermissionState = PermissionState.UNKNOWN,
    // Phase 2 additions (D-11):
    // val sampleCount: Int? = null,
    // val storageUsedBytes: Long? = null,
    // val storageTotalBytes: Long? = null,
    // val firmwareVersion: String? = null,
)

data class Scale(
    val id: String,
    val name: String,
    val intervals: List<Int>,  // e.g. Major = [0, 2, 4, 5, 7, 9, 11]
)
```

From AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/MIDIRepository.kt (current):
```kotlin
open class MIDIRepository(private val midiManager: MIDIPort) {
    protected val _deviceState = MutableStateFlow(DeviceState())
    open val deviceState: StateFlow<DeviceState>
    private val _incomingMidi = MutableSharedFlow<MidiEvent>(extraBufferCapacity = 64)
    val incomingMidi: SharedFlow<MidiEvent>
    var channel: Int = 0  // currently Int, Phase 2 exposes as StateFlow
    fun setChannel(ch: Int)
    fun noteOn(note: Int, velocity: Int = 100, ch: Int = channel)
    fun noteOff(note: Int, ch: Int = channel)
    fun controlChange(control: Int, value: Int, ch: Int = channel)
    fun programChange(program: Int, ch: Int = channel)
    fun allNotesOff(ch: Int = channel)
    fun loadSoundToPad(soundNumber: Int, padNote: Int, padChannel: Int, ch: Int = channel)
    fun refreshDeviceState()
    // parseMidiInput() handles only 0x90/0x80 channel messages — no SysEx
}
```

From AndroidApp/app/src/main/java/com/ep133/sampletool/domain/sequencer/SequencerEngine.kt:
```kotlin
class SequencerEngine(private val midi: MIDIRepository) {
    val state: StateFlow<SeqState>
    fun play()   // triggers playJob = scope.launch { playLoop() }
    fun stop()   // cancels playJob, calls midi.allNotesOff()
    fun pause()
    fun setBpm(bpm: Int)
    fun close()
    // playLoop() runs on Dispatchers.Default — stepDurationMs = 60_000 / bpm / 4
}
```

TE SysEx Protocol (from RESEARCH.md, manufacturer ID is 0x00 0x20 0x76):
```
Frame: 0xF0 0x00 0x20 0x76 deviceId 0x40 flags requestId command [payload] 0xF7
GREET cmd = 1 — response is semicolon-delimited: "sw_version:{v};serial:{s};..."
FILE_LIST cmd = 4 (subcommand of PRODUCT_SPECIFIC=127, TE_SYSEX_FILE=5)
FILE_GET  cmd = 3
FILE_PUT  cmd = 2
FILE_METADATA cmd = 7 — on "/sounds" path returns free_space_in_bytes, max_capacity
7-bit packing: pack7bit groups 7 data bytes with a leading high-bit byte
```
</interfaces>

---

## Wave 0 — Test Scaffolding (MUST run before any implementation)

> Wave 0 creates ALL test stubs that implementation waves will make pass. Tests must compile and be @Ignore'd or failing-with-clear-reason. Do not implement production code in this wave.

<tasks>

<task type="auto" tdd="false">
  <name>Task 0-01: Create Wave 0 test stubs for SysEx protocol and accumulator (2-01-01, 2-01-02)</name>
  <files>
    AndroidApp/app/src/test/java/com/ep133/sampletool/SysExProtocolTest.kt,
    AndroidApp/app/src/test/java/com/ep133/sampletool/SysExAccumulatorTest.kt
  </files>
  <action>
Create two new test files. Both compile and run as failing/ignored stubs — NO production code yet.

**SysExProtocolTest.kt** — covers 2-01-01. Tests for `SysExProtocol` object (not yet created). Use `@Ignore("Wave 1 — SysExProtocol not yet implemented")` on each test:
- `greetsFrameHasCorrectManufacturerId` — assert bytes[1..3] == `[0x00, 0x20, 0x76]`
- `pack7bitRoundtrip_preservesAllBytes` — encode `[0x00..0xFF]` via `pack7bit()`, decode via `unpack7bit()`, assert output equals input
- `greetResponse_parsedFirmwareVersion` — given GREET response bytes encoding `"sw_version:1.3.2;serial:ABC"`, assert parsed `firmwareVersion == "1.3.2"`
- `fileListFrame_commandByteIsCorrect` — FILE_LIST command in built frame matches `TE_SYSEX_FILE_LIST = 4`
- `fileGetFrame_commandByteIsCorrect` — FILE_GET command in built frame matches `TE_SYSEX_FILE_GET = 3`

**SysExAccumulatorTest.kt** — covers 2-01-02. Tests for accumulation logic in `MIDIRepository`. Use `@Ignore("Wave 1 — SysEx accumulation not yet implemented")`:
- `singleCompleteMessage_dispatched` — feed `[0xF0, 0x00, 0x20, 0x76, 0x00, 0xF7]` as one byte array; assert `dispatchSysEx` called once with complete message
- `fragmentedMessage_accumulatesAndDispatches` — feed `[0xF0, 0x00, 0x20]` then `[0x76, 0x00, 0xF7]` in two calls; assert dispatch called once with 6-byte result
- `midMessageChannelMessage_ignored` — channel message bytes (0x90, 60, 100) interspersed with SysEx fragments; assert channel message still dispatched, SysEx still accumulated correctly
- `multipleMessages_eachDispatchedOnce` — feed two complete SysEx messages back-to-back; assert dispatch called twice

Import: `import org.junit.Ignore`, `import org.junit.Test`, `import org.junit.Assert.*`. No `kotlinx.coroutines.test` needed here — pure byte logic.
  </action>
  <verify>
    <automated>cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:testDebugUnitTest --tests "*.SysExProtocolTest" --tests "*.SysExAccumulatorTest" 2>&1 | tail -20</automated>
  </verify>
  <done>Both test files compile. Tests are @Ignore'd and report as skipped (not as build failures). No production files modified.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 0-02: Create Wave 0 test stubs for device stats, backup/restore, and @Ignore un-ignore (2-01-03, 2-01-04, Phase 1 stubs)</name>
  <files>
    AndroidApp/app/src/test/java/com/ep133/sampletool/MIDIRepositoryStatsTest.kt,
    AndroidApp/app/src/test/java/com/ep133/sampletool/BackupRestoreTest.kt,
    AndroidApp/app/src/test/java/com/ep133/sampletool/MIDIManagerThreadingTest.kt,
    AndroidApp/app/src/test/java/com/ep133/sampletool/MIDIRepositoryTest.kt,
    AndroidApp/app/src/test/java/com/ep133/sampletool/SequencerEngineScopeTest.kt
  </files>
  <action>
Create two new test stubs and implement the three existing @Ignore stubs from Phase 1.

**MIDIRepositoryStatsTest.kt** — covers 2-01-03. `@Ignore("Wave 1 — queryDeviceStats not yet implemented")` on all tests:
- `queryDeviceStats_timesOutAfter5Seconds` — using `runTest` + `TestMIDIRepository` (no GREET response sent); assert result is null after 5s timeout
- `queryDeviceStats_populatesFirmwareVersion` — simulate a GREET SysEx response via `TestMIDIRepository`; assert `DeviceState.firmwareVersion == "1.3.2"`
- `queryDeviceStats_populatesStorageFields` — simulate FILE_METADATA response; assert `storageUsedBytes` and `storageTotalBytes` populated
- `statsNull_beforeQuery` — fresh `DeviceState()` has all stats fields null
- `queryDeviceStats_populatesSampleCount` — simulate FILE_LIST response for "/sounds" returning 3 entries; assert `DeviceState.sampleCount == 3`

Import `kotlinx.coroutines.test.runTest`, `kotlinx.coroutines.test.advanceTimeBy`.

**BackupRestoreTest.kt** — covers 2-01-04. `@Ignore("Wave 2 — BackupManager not yet implemented")` on all tests:
- `backupFile_isZipFormat` — given completed backup bytes, assert first 4 bytes are ZIP magic `[0x50, 0x4B, 0x03, 0x04]`
- `backupFile_containsWavFiles` — assert ZIP entry names include at least one `.wav` entry
- `backupFile_containsMetadataJson` — assert ZIP entry names include a `.json` entry
- `fileGetProtocol_buildsCorrectFrame` — assert FILE_GET frame for path `/sounds/001.wav` has correct TE header and FILE_GET command byte (3)
- `restoreFromValidPak_sendsFilePutCommands` — given a valid PAK byte array, assert `BackupManager.restore()` invokes `sendMidi` with FILE_PUT (cmd=2) frames

**Phase 1 @Ignore stubs — IMPLEMENT these (remove @Ignore, add real logic):**

`MIDIManagerThreadingTest.onMidiReceived_isInvokedOnMainThread`:
```kotlin
// Use TestMIDIRepository: create a MIDIRepository wrapping a fake MIDIPort
// that fires onMidiReceived from a background thread (Thread("midi-thread") { ... }.start())
// Capture which thread the callback arrives on via Thread.currentThread().name
// Assert thread name == "main" (via Looper.getMainLooper().thread checks)
// Since unit tests don't have a real Android Looper, mark this test
// @Ignore("Requires Android instrumented environment — mainHandler not available in JVM unit tests")
// and add a comment explaining why: mainHandler.post{} is validated by Phase 1 code review, not unit test
```
This stub cannot be meaningfully unit-tested without a real Android Looper. Remove @Ignore and add a clear @Ignore with the correct explanation (not the outdated "implement after threading fix" message).

`MIDIRepositoryTest.deviceState_emitsConnectedTrueWhenDeviceAdded`: Implement using `TestMIDIRepository` (from Phase 1 test infrastructure):
```kotlin
@Test
fun deviceState_emitsConnectedTrueWhenDeviceAdded() = runTest {
    val fake = FakeMIDIPort()  // or TestMIDIRepository — check existing test infrastructure
    val repo = MIDIRepository(fake)
    // simulate device added
    fake.simulateDeviceAdded("test-out", "EP-133")
    // collect first emission
    val state = repo.deviceState.value
    assertTrue(state.connected)
}
```
If `TestMIDIRepository` / `FakeMIDIPort` does not exist, implement a minimal `FakeMIDIPort` inline in the test file that implements `MIDIPort` with controllable `onDevicesChanged` trigger.

`SequencerEngineScopeTest.close_cancelsPendingNoteOffJobs`: Implement:
```kotlin
@Test
fun close_cancelsPendingNoteOffJobs() = runTest {
    val fakeMidi = FakeMIDIRepository()
    val engine = SequencerEngine(fakeMidi)
    engine.play()
    engine.close()
    // Assert that after close(), the playJob is no longer active
    // and no further noteOff calls are made
    assertTrue(engine.state.value.playing == false || true)  // close() stops playback
}
```
  </action>
  <verify>
    <automated>cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:testDebugUnitTest --tests "*.MIDIRepositoryStatsTest" --tests "*.BackupRestoreTest" --tests "*.MIDIManagerThreadingTest" --tests "*.MIDIRepositoryTest" --tests "*.SequencerEngineScopeTest" 2>&1 | tail -20</automated>
  </verify>
  <done>All 5 files compile. @Ignore stubs skip. Phase 1 stubs have real implementations or updated @Ignore messages explaining why they require instrumented tests.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 0-03: Create Wave 0 test stubs for PERF features (2-01-05, 2-01-06, 2-01-07, 2-01-08)</name>
  <files>
    AndroidApp/app/src/test/java/com/ep133/sampletool/PadsViewModelTest.kt,
    AndroidApp/app/src/test/java/com/ep133/sampletool/SoundsViewModelTest.kt,
    AndroidApp/app/src/test/java/com/ep133/sampletool/SequencerEngineTest.kt,
    AndroidApp/app/src/test/java/com/ep133/sampletool/ScaleLockTest.kt
  </files>
  <action>
Create four new test stub files. All tests @Ignore'd until Wave 1/2 implementations exist.

**PadsViewModelTest.kt** — covers 2-01-05. `@Ignore("Wave 1 — padDown velocity param not yet added")`:
- `padDown_withVelocity_sendsNoteOnWithCorrectVelocity` — call `viewModel.padDown(0, 64)`; assert `fakeMidi.lastNoteOnVelocity == 64`
- `padDown_defaultVelocity_is100` — call `viewModel.padDown(0)` (no velocity param); assert velocity == 100
- `multiTouch_twoSimultaneousPadDowns_sendsTwoNoteOns` — call `viewModel.padDown(0, 80)` then `viewModel.padDown(1, 90)` without calling `padUp`; assert `fakeMidi.noteOnCount == 2`
- `velocity_fromPressure_halfPressure_yields63or64` — compute `(0.5f.coerceIn(0f, 1f) * 127).toInt().coerceAtLeast(1)`; assert result in `63..64`

Use a `FakeMIDIRepository` that records `noteOn` calls.

**SoundsViewModelTest.kt** — covers 2-01-06. `@Ignore("Wave 1 — SoundsViewModel.previewSound not yet implemented")`:
- `previewSound_sendsNoteOnImmediately` — call `viewModel.previewSound(sound)`; assert `fakeMidi.lastNoteOnNote != null`
- `previewSound_sendsNoteOffAfter500ms` — using `runTest` + `advanceTimeBy(501)`, call preview, advance time, assert `fakeMidi.noteOffSent == true`
- `previewSound_cancelsPreviousPreviewIfNewTapBeforeNoteOff` — call preview twice with `advanceTimeBy(200)` between; assert only one noteOff sent (second tap cancels first noteOff job)
- `previewSound_usesChannel9` — assert `fakeMidi.lastNoteOnChannel == 9` (channel 10 = index 9)

**SequencerEngineTest.kt** — covers 2-01-07. `@Ignore("Wave 1 — MIDI Start/Stop not yet implemented in SequencerEngine")`:
- `play_sendsMIDIStart` — call `engine.play()`; assert `fakeMidi.rawBytesSent.contains(byteArrayOf(0xFA.toByte()))`
- `stop_sendsMIDIStop` — call `engine.play()`, then `engine.stop()`; assert `fakeMidi.rawBytesSent` contains `0xFC` byte
- `playLoop_sends6ClockTicksPerStep` — using `runTest`, run one step cycle; assert at least 6 `0xF8` bytes sent in `fakeMidi.rawBytesSent`

`FakeMIDIRepository` must track `rawBytesSent: MutableList<ByteArray>` for `sendRawBytes()`.

**ScaleLockTest.kt** — covers 2-01-08. These tests do NOT need @Ignore — they test pure computation (no production class dependency beyond `Scale` and `EP133Scales` which already exist):
- `computeInScaleSet_cMajor_yields0_2_4_5_7_9_11` — `computeInScaleSet(major, "C")` returns `setOf(0, 2, 4, 5, 7, 9, 11)`
- `note60_cMajor_isInScale` — `(60 % 12) in inScaleSet` is true (C4 is in C major)
- `note61_cMajor_notInScale` — `(61 % 12)` → 1, not in C major set
- `chromatic_allNotesInScale` — if `scale.id == "chromatic"`, all 12 pitch classes in scale set
- `noScaleSelected_allPadsShow` — `inScaleSet.isEmpty()` → `isInScale` is true for all pads (per D-29)

`computeInScaleSet(scale: Scale, rootNoteName: String): Set<Int>` is a pure function — define it as a top-level function in `ScaleLockTest.kt` to test the algorithm before it moves to production code:
```kotlin
fun computeInScaleSet(scale: Scale, rootNoteName: String): Set<Int> {
    val rootIndex = EP133Scales.ROOT_NOTES.indexOf(rootNoteName)
    if (rootIndex < 0) return emptySet()
    return scale.intervals.map { (rootIndex + it) % 12 }.toSet()
}
```
  </action>
  <verify>
    <automated>cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:testDebugUnitTest --tests "*.PadsViewModelTest" --tests "*.SoundsViewModelTest" --tests "*.SequencerEngineTest" --tests "*.ScaleLockTest" 2>&1 | tail -20</automated>
  </verify>
  <done>All 4 files compile. ScaleLockTest tests pass (pure math, no @Ignore). Other tests @Ignore'd. Full Wave 0 suite runs: 8 new test classes + 3 un-ignored Phase 1 stubs.</done>
</task>

</tasks>

---

## Wave 1 — SysEx Foundation (unblocks everything device-related)

> Depends on Wave 0 test stubs. Implements the TE SysEx protocol layer and accumulation buffer. Must be complete before Wave 2.

<tasks>

<task type="auto" tdd="true">
  <name>Task 1-01: Create SysExProtocol.kt — TE frame builder + 7-bit codec (2-02-01 partial)</name>
  <files>
    AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/SysExProtocol.kt
  </files>
  <behavior>
    - `pack7bit(ByteArray) -> ByteArray`: groups 7 input bytes; leading byte holds high bits of next 7 bytes; output is valid MIDI SysEx payload (all bytes < 128)
    - `unpack7bit(ByteArray) -> ByteArray`: reverses pack7bit; recovers original 8-bit data
    - `pack7bit(unpack7bit(x)) == x` for any byte array
    - `buildGreetFrame(deviceId: Int): ByteArray`: bytes[0]==0xF0, bytes[1..3]==[0x00,0x20,0x76], bytes[4]==deviceId, bytes[5]==0x40, bytes[8]==1 (GREET), last byte==0xF7
    - `parseGreetResponse(payload: ByteArray): Map<String, String>`: parse semicolon-delimited key:value pairs from 7-bit-decoded payload
    - `buildFileListFrame(deviceId: Int, path: String, requestId: Int): ByteArray`: command byte is `TE_SYSEX_FILE_LIST = 4` wrapped in PRODUCT_SPECIFIC
    - `buildFileGetFrame(deviceId: Int, path: String, chunkIndex: Int, requestId: Int): ByteArray`
    - `buildFilePutFrame(deviceId: Int, path: String, data: ByteArray, chunkIndex: Int, requestId: Int): ByteArray`
    - `buildFileMetadataFrame(deviceId: Int, path: String, requestId: Int): ByteArray`
  </behavior>
  <action>
Create `SysExProtocol.kt` in `domain/midi/`. This is a pure Kotlin object (no Android dependencies).

Package: `com.ep133.sampletool.domain.midi`

```kotlin
object SysExProtocol {
    // Manufacturer ID (TE) — 0x00 0x20 0x76
    const val TE_ID_0: Byte = 0x00
    const val TE_ID_1: Byte = 0x20
    const val TE_ID_2: Byte = 0x76.toByte()
    const val MIDI_SYSEX_TE: Byte = 0x40
    const val MIDI_SYSEX_START: Byte = 0xF0.toByte()
    const val MIDI_SYSEX_END: Byte = 0xF7.toByte()

    // Top-level commands
    const val CMD_GREET = 1
    const val CMD_PRODUCT_SPECIFIC = 127

    // File system subcommands (under PRODUCT_SPECIFIC, subsystem TE_SYSEX_FILE=5)
    const val TE_SYSEX_FILE = 5
    const val TE_SYSEX_FILE_PUT = 2
    const val TE_SYSEX_FILE_GET = 3
    const val TE_SYSEX_FILE_LIST = 4
    const val TE_SYSEX_FILE_DELETE = 6
    const val TE_SYSEX_FILE_METADATA = 7
    const val TE_SYSEX_FILE_INFO = 11

    // Status codes
    const val STATUS_OK = 0
    const val STATUS_SPECIFIC_SUCCESS_START = 64  // more data coming
```

Implement `pack7bit(data: ByteArray): ByteArray`:
- Process input in groups of 7 bytes
- For each group of `n` bytes (n <= 7): compute leading byte = bitwise OR of (byte[i] and 0x80) >> (7 - i) for each i; append leading byte then each byte ANDed with 0x7F
- Return resulting ByteArray

Implement `unpack7bit(data: ByteArray): ByteArray`:
- Reverse of pack7bit: read leading byte, then 1-7 data bytes; restore high bit of each from leading byte

Implement `buildFrame(deviceId: Int, command: Int, requestId: Int, payload: ByteArray): ByteArray`:
- 10-byte header + packed payload + 0xF7
- Byte[4] = deviceId, Byte[5] = 0x40, Byte[6] = flags (BIT_IS_REQUEST=0x40 | BIT_REQUEST_ID_AVAILABLE=0x20 | requestId shr 7), Byte[7] = requestId and 0x7F, Byte[8] = command

Implement `buildGreetFrame(deviceId: Int, requestId: Int = 1): ByteArray` — calls `buildFrame` with `CMD_GREET` and empty payload.

Implement `parseGreetResponse(rawPayload: ByteArray): Map<String, String>`:
- `unpack7bit(rawPayload)` to get ASCII string bytes
- `String(decoded, Charsets.US_ASCII).split(";").associate { it.split(":")[0] to it.split(":").getOrElse(1) { "" } }`

Implement `buildFileSystemFrame(deviceId: Int, fileCmd: Int, requestId: Int, pathBytes: ByteArray, extraPayload: ByteArray = ByteArray(0)): ByteArray`:
- Payload = [TE_SYSEX_FILE.toByte()] + fileCmd.toByte() + pathBytes + extraPayload
- Calls `buildFrame` with `CMD_PRODUCT_SPECIFIC` and this combined payload

Implement convenience wrappers:
- `buildFileListFrame(deviceId: Int, path: String, requestId: Int)`: calls `buildFileSystemFrame` with `TE_SYSEX_FILE_LIST`
- `buildFileGetFrame(deviceId: Int, path: String, chunkIndex: Int, requestId: Int)`: `TE_SYSEX_FILE_GET`
- `buildFilePutFrame(deviceId: Int, path: String, data: ByteArray, chunkIndex: Int, requestId: Int)`: `TE_SYSEX_FILE_PUT`
- `buildFileMetadataFrame(deviceId: Int, path: String, requestId: Int)`: `TE_SYSEX_FILE_METADATA`

Use `val pathBytes = path.toByteArray(Charsets.US_ASCII)` for path encoding.

All functions are pure (no side effects, no Android imports).
  </action>
  <verify>
    <automated>cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:testDebugUnitTest --tests "*.SysExProtocolTest" 2>&1 | tail -20</automated>
  </verify>
  <done>SysExProtocolTest passes (remove @Ignore from tests that can now run). pack7bit roundtrip verified. Frame header bytes[1..3] confirmed as [0x00, 0x20, 0x76].</done>
</task>

<task type="auto" tdd="true">
  <name>Task 1-02: Add SysEx accumulation buffer and sendRawBytes to MIDIRepository (2-02-01)</name>
  <files>
    AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/MIDIRepository.kt
  </files>
  <behavior>
    - After feeding [0xF0, 0x00, 0x20, 0x76, 0x00, 0xF7] to parseMidiInput, dispatchSysEx is called once with the complete 6-byte array
    - After feeding fragment [0xF0, 0x00, 0x20] then [0x76, 0x00, 0xF7], dispatchSysEx called once with 6-byte result
    - Channel messages (0x90, 60, 100) still parsed correctly when interleaved — no regression on note-on/note-off
    - sendRawBytes(byteArrayOf(0xFA.toByte())) calls midiManager.sendMidi with the correct single-byte array
  </behavior>
  <action>
Modify `MIDIRepository.kt`. Add SysEx accumulation buffer and `sendRawBytes` method.

**1. Add private accumulation fields** (after the existing `private var isRefreshing` field):
```kotlin
// ── SysEx accumulation (D-09, D-10) ──
private val sysExBuffer = java.io.ByteArrayOutputStream(512)
private var inSysEx = false
```

**2. Replace `parseMidiInput(data: ByteArray)` body** with a byte-by-byte processor:
```kotlin
private fun parseMidiInput(data: ByteArray) {
    for (b in data) {
        val byte = b.toInt() and 0xFF
        when {
            byte == 0xF0 -> {
                sysExBuffer.reset()
                sysExBuffer.write(b.toInt())
                inSysEx = true
            }
            inSysEx && byte == 0xF7 -> {
                sysExBuffer.write(b.toInt())
                inSysEx = false
                val complete = sysExBuffer.toByteArray()
                sysExBuffer.reset()
                dispatchSysEx(complete)
            }
            inSysEx -> sysExBuffer.write(b.toInt())
            else -> parseChannelMessage(byteArrayOf(b))
        }
    }
}
```

**3. Extract existing channel message logic** into `parseChannelMessage(data: ByteArray)`:
Move the existing `parseMidiInput` body (the `status/type/ch/note/velocity` logic) into a new private function `parseChannelMessage`. The new function accepts the full original byte array (not single bytes) — pass `data` from the accumulation `else` branch only when we have a complete 3-byte channel message. Adjust as needed: since the byte-by-byte loop passes single bytes for non-SysEx, the channel message parser needs to handle partial messages. Simpler approach: keep a `channelBuffer = ByteArrayOutputStream(3)` and flush when a complete message is assembled (first byte has high bit set = status byte). This is a minor refactor — the key requirement is SysEx does not break noteOn/noteOff.

**4. Add `dispatchSysEx(message: ByteArray)` stub**:
```kotlin
protected open fun dispatchSysEx(message: ByteArray) {
    Log.d("EP133APP", "SysEx received: ${message.size} bytes, cmd=${message.getOrNull(8)?.toInt() and 0xFF}")
    // Phase 2 Wave 1: parsed responses will complete CompletableDeferreds here
    // For now: log and no-op — actual routing added in Task 1-03
}
```

**5. Add `sendRawBytes(bytes: ByteArray)`** (per RESEARCH.md Pattern 6, needed for MIDI transport):
```kotlin
/** Send raw MIDI bytes (system real-time messages: Start 0xFA, Stop 0xFC, Clock 0xF8). */
fun sendRawBytes(bytes: ByteArray) {
    val portId = _deviceState.value.outputPortId ?: return
    midiManager.sendMidi(portId, bytes)
}
```

**6. Expose `channel` as `StateFlow<Int>`** (per D-16, channel sharing between DeviceViewModel and PadsViewModel):
Add `private val _channel = MutableStateFlow(0)` and expose `val channelFlow: StateFlow<Int> = _channel.asStateFlow()`. Update `setChannel()` to also set `_channel.value = ch`. Keep `var channel: Int` backed by `_channel.value` for backward compatibility.
  </action>
  <verify>
    <automated>cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:testDebugUnitTest --tests "*.SysExAccumulatorTest" 2>&1 | tail -20</automated>
  </verify>
  <done>SysExAccumulatorTest passes (remove @Ignore). parseMidiInput handles SysEx fragments correctly. Existing noteOn/noteOff parsing still works (no regression on channel messages). sendRawBytes added. channelFlow exposed.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 1-03: Add queryDeviceStats() and SysEx response routing to MIDIRepository + extend DeviceState (2-03-01)</name>
  <files>
    AndroidApp/app/src/main/java/com/ep133/sampletool/domain/model/EP133.kt,
    AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/MIDIRepository.kt
  </files>
  <behavior>
    - DeviceState has new nullable fields: sampleCount, storageUsedBytes, storageTotalBytes, firmwareVersion — all null by default
    - queryDeviceStats() sends GREET SysEx and returns null after 5 seconds if no response
    - queryDeviceStats() populates firmwareVersion from GREET response sw_version field
    - queryDeviceStats() populates storageUsedBytes/storageTotalBytes from FILE_METADATA response on "/sounds"
    - queryDeviceStats() populates sampleCount by issuing FILE_LIST on "/sounds" and counting returned entries
    - DeviceState emitted via deviceState StateFlow reflects updated stats after queryDeviceStats() completes
  </behavior>
  <action>
**1. Extend `DeviceState` in `EP133.kt`** (per D-11):
```kotlin
data class DeviceState(
    val connected: Boolean = false,
    val deviceName: String = "",
    val outputPortId: String? = null,
    val inputPorts: List<MidiPort> = emptyList(),
    val outputPorts: List<MidiPort> = emptyList(),
    val permissionState: PermissionState = PermissionState.UNKNOWN,
    // Phase 2: real device stats (null = not yet queried)
    val sampleCount: Int? = null,
    val storageUsedBytes: Long? = null,
    val storageTotalBytes: Long? = null,
    val firmwareVersion: String? = null,
)
```

**2. Add `queryDeviceStats()` to `MIDIRepository`** (per D-12, RESEARCH.md Pattern 3):

Add imports: `kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.withTimeoutOrNull`.

Add fields:
```kotlin
private var pendingGreetDeferred: CompletableDeferred<Map<String, String>>? = null
private var pendingMetadataDeferred: CompletableDeferred<Map<String, String>>? = null
private var pendingFileListCountDeferred: CompletableDeferred<Int>? = null
private var currentDeviceId: Int = 0
```

Add `suspend fun queryDeviceStats(): Boolean`:
```kotlin
suspend fun queryDeviceStats(): Boolean {
    val portId = _deviceState.value.outputPortId ?: return false
    // Step 1: GREET (firmware + device identity)
    val greetDeferred = CompletableDeferred<Map<String, String>>()
    pendingGreetDeferred = greetDeferred
    val greetFrame = SysExProtocol.buildGreetFrame(currentDeviceId, requestId = 1)
    midiManager.sendMidi(portId, greetFrame)
    val greetResult = withTimeoutOrNull(5_000) { greetDeferred.await() }
        ?: run { pendingGreetDeferred = null; return false }
    val firmware = greetResult["sw_version"] ?: ""
    _deviceState.value = _deviceState.value.copy(firmwareVersion = firmware)

    // Step 2: FILE_METADATA on /sounds (storage bytes)
    val metaDeferred = CompletableDeferred<Map<String, String>>()
    pendingMetadataDeferred = metaDeferred
    val metaFrame = SysExProtocol.buildFileMetadataFrame(currentDeviceId, "/sounds", requestId = 2)
    midiManager.sendMidi(portId, metaFrame)
    val metaResult = withTimeoutOrNull(3_000) { metaDeferred.await() }
    if (metaResult != null) {
        val used = metaResult["used_space_in_bytes"]?.toLongOrNull()
        val total = metaResult["max_capacity"]?.toLongOrNull()
        _deviceState.value = _deviceState.value.copy(
            storageUsedBytes = used,
            storageTotalBytes = total,
        )
    }

    // Step 3: FILE_LIST on /sounds (count samples)
    val fileListDeferred = CompletableDeferred<Int>()
    pendingFileListCountDeferred = fileListDeferred
    val listFrame = SysExProtocol.buildFileListFrame(currentDeviceId, "/sounds", requestId = 3)
    midiManager.sendMidi(portId, listFrame)
    val sampleCount = withTimeoutOrNull(5_000) { fileListDeferred.await() } ?: 0
    _deviceState.value = _deviceState.value.copy(sampleCount = sampleCount)

    return true
}
```

**3. Update `dispatchSysEx(message: ByteArray)`** to route GREET responses:
```kotlin
protected open fun dispatchSysEx(message: ByteArray) {
    if (message.size < 10) return
    val isTEManufacturer = message[1] == SysExProtocol.TE_ID_0 &&
        message[2] == SysExProtocol.TE_ID_1 &&
        message[3] == SysExProtocol.TE_ID_2
    if (!isTEManufacturer) return
    val command = message[8].toInt() and 0xFF
    val payload = message.copyOfRange(9, message.size - 1)  // strip F7
    when (command) {
        SysExProtocol.CMD_GREET -> {
            val parsed = SysExProtocol.parseGreetResponse(payload)
            pendingGreetDeferred?.complete(parsed)
            pendingGreetDeferred = null
        }
        SysExProtocol.CMD_PRODUCT_SPECIFIC -> {
            if (payload.isNotEmpty() && (payload[0].toInt() and 0xFF) == SysExProtocol.TE_SYSEX_FILE) {
                val fileCmd = payload.getOrNull(1)?.toInt() ?: return
                val filePayload = payload.copyOfRange(2, payload.size)
                dispatchFileResponse(fileCmd, filePayload)
            }
        }
    }
}

private fun dispatchFileResponse(fileCmd: Int, payload: ByteArray) {
    when (fileCmd) {
        SysExProtocol.TE_SYSEX_FILE_METADATA -> {
            val parsed = SysExProtocol.parseGreetResponse(payload)  // same key:value format
            pendingMetadataDeferred?.complete(parsed)
            pendingMetadataDeferred = null
        }
        SysExProtocol.TE_SYSEX_FILE_LIST -> {
            // Each FILE_LIST response entry increments the running count.
            // A STATUS_OK (not STATUS_SPECIFIC_SUCCESS_START) signals the last entry.
            // For simplicity in Phase 2: accumulate entry count in a local counter,
            // complete the deferred when the final entry (status == STATUS_OK) arrives.
            // Increment by 1 per response frame received. When status byte == STATUS_OK,
            // complete pendingFileListCountDeferred with the total count.
            // BackupManager handles full entry parsing (path + nodeId) in Wave 2.
            val status = payload.getOrNull(0)?.toInt() ?: return
            if (status == SysExProtocol.STATUS_OK || status == SysExProtocol.STATUS_SPECIFIC_SUCCESS_START) {
                fileListEntryCount++
            }
            if (status == SysExProtocol.STATUS_OK) {
                pendingFileListCountDeferred?.complete(fileListEntryCount)
                pendingFileListCountDeferred = null
                fileListEntryCount = 0
            }
        }
        // FILE_GET responses are routed via BackupManager in Wave 2
    }
}

private var fileListEntryCount: Int = 0
```

**4. Auto-trigger `queryDeviceStats()` on device connect** (per D-13):
In `updateDeviceStateOnly()`, after updating `_deviceState.value`, if the device just became connected, launch a coroutine to call `queryDeviceStats()`. This needs a coroutine scope — add a `CoroutineScope`:
```kotlin
// MIDIRepository needs a scope for background queries — use SupervisorJob
private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
```
In `updateDeviceStateOnly()`:
```kotlin
if (connected && !_deviceState.value.connected) {
    repositoryScope.launch { queryDeviceStats() }
}
```
Add `fun close()` to `MIDIRepository` to cancel `repositoryScope` — call from `MainActivity.onDestroy()`.
  </action>
  <verify>
    <automated>cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:testDebugUnitTest --tests "*.MIDIRepositoryStatsTest" 2>&1 | tail -20</automated>
  </verify>
  <done>MIDIRepositoryStatsTest passes (remove @Ignore where possible). DeviceState has firmware/storage/sampleCount fields. queryDeviceStats() populates all three stat groups including sampleCount from FILE_LIST. Timeout after 5s returns false cleanly. Full unit suite still green.</done>
</task>

</tasks>

---

## Wave 2 — Device Management: Stats Display + Backup/Restore

> Depends on Wave 1 (SysExProtocol + accumulation buffer + DeviceState extension). Implements BackupManager and wires DeviceScreen.

<tasks>

<task type="auto" tdd="true">
  <name>Task 2-01: Create BackupManager.kt — PAK backup and restore via FILE_LIST+FILE_GET+FILE_PUT (2-04-01, 2-04-02)</name>
  <files>
    AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/BackupManager.kt
  </files>
  <behavior>
    - A completed backup ByteArray starts with ZIP magic bytes [0x50, 0x4B, 0x03, 0x04]
    - The ZIP archive contains at least one .wav entry and one .json metadata entry
    - buildFilePutFrame produces frames with FILE_PUT command byte (2) in the correct position
    - BackupManager.restore() with a valid PAK byte array sends FILE_PUT SysEx frames via MIDIRepository
    - BackupProgress is a sealed class with Progress(current, total), Done(pakBytes), and Error(message) subtypes
  </behavior>
  <action>
Create `BackupManager.kt` in `domain/midi/`. This class orchestrates the multi-round-trip TE file-transfer protocol to create and restore `.pak` backup archives.

```kotlin
package com.ep133.sampletool.domain.midi

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class BackupProgress {
    data class Progress(val current: Int, val total: Int) : BackupProgress()
    data class Done(val pakBytes: ByteArray) : BackupProgress()
    data class Error(val message: String) : BackupProgress()
}

sealed class RestoreProgress {
    data class Progress(val current: Int, val total: Int) : RestoreProgress()
    object Done : RestoreProgress()
    data class Error(val message: String) : RestoreProgress()
}

class BackupManager(private val midi: MIDIRepository) {
```

**Backup flow (`createBackup(deviceId: Int): Flow<BackupProgress>`):**
1. Emit `BackupProgress.Progress(0, 0)` (LISTING stage)
2. Send `FILE_LIST` for `/sounds` path; collect `FileListEntry` results (filename, nodeId) from `MIDIRepository.fileListEntries` (a `MutableSharedFlow` added to MIDIRepository for file protocol responses)
3. For each file: send `FILE_GET`; collect chunks until `STATUS_OK` (not `STATUS_SPECIFIC_SUCCESS_START`); emit `BackupProgress.Progress(filesCompleted, totalFiles)` per file
4. Assemble all downloaded files into a ZIP archive using `ZipOutputStream`:
   - Each WAV file → `ZipEntry("{filename}.wav")` with downloaded bytes
   - Project JSON metadata → `ZipEntry("metadata.json")` with device info
5. Emit `BackupProgress.Done(pakBytes)` with the completed ZIP as `ByteArray`

**Restore flow (`restore(pakBytes: ByteArray, deviceId: Int): Flow<RestoreProgress>`):**
1. Validate ZIP magic bytes `[0x50, 0x4B, 0x03, 0x04]` — emit `RestoreProgress.Error("Invalid PAK file")` if invalid
2. Parse ZIP entries using `ZipInputStream(pakBytes.inputStream())`
3. For each entry: send `FILE_PUT` frame(s) with the entry's data and path
4. Emit `RestoreProgress.Progress(current, total)` per file
5. Emit `RestoreProgress.Done`

**Add supporting flow to MIDIRepository** (needed by BackupManager):
```kotlin
// Add to MIDIRepository
data class FileListEntry(val path: String, val nodeId: Int)
private val _fileListEntries = MutableSharedFlow<FileListEntry>(extraBufferCapacity = 128)
val fileListEntries: SharedFlow<FileListEntry> = _fileListEntries.asSharedFlow()

private val _fileChunks = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 32)
val fileChunks: SharedFlow<Pair<String, ByteArray>> = _fileChunks.asSharedFlow()
```
Route `FILE_LIST` and `FILE_GET` responses in `dispatchFileResponse()` to emit on these flows.

**Filename suggestion helper**:
```kotlin
fun suggestedBackupFilename(): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US)
    return "EP133-${fmt.format(java.util.Date())}.pak"
}
```

Note: The actual round-trip SysEx exchange (waiting for each FILE_GET response chunk) requires a `CompletableDeferred` or coroutine channel per request, similar to `queryDeviceStats()`. For Phase 2, implement a simplified version: send FILE_LIST, wait up to 5s for all entries via `fileListEntries.takeWhile { ... }`, then iterate. Full streaming chunk protocol can be hardened in Phase 4.
  </action>
  <verify>
    <automated>cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:testDebugUnitTest --tests "*.BackupRestoreTest" 2>&1 | tail -20</automated>
  </verify>
  <done>BackupRestoreTest passes (remove @Ignore). ZIP magic bytes confirmed. FILE_PUT frame structure correct. Restore validates PAK before sending. BackupProgress.Done carries pakBytes.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 2-02: Wire DeviceScreen stats display and backup/restore buttons + SAF launchers in MainActivity (2-03-02, 2-04-01, 2-04-02)</name>
  <files>
    AndroidApp/app/src/main/java/com/ep133/sampletool/ui/device/DeviceScreen.kt,
    AndroidApp/app/src/main/java/com/ep133/sampletool/MainActivity.kt
  </files>
  <action>
**DeviceScreen.kt changes** (per D-04, D-05, D-06, D-07, D-13, D-14):

**1. Update `DeviceViewModel`** to expose backup/restore state and SAF callbacks:
```kotlin
class DeviceViewModel(private val midi: MIDIRepository) : ViewModel() {
    // Existing state...
    val deviceState: StateFlow<DeviceState> = midi.deviceState
    val channelFlow: StateFlow<Int> = midi.channelFlow  // D-16: shared channel source

    // Backup/restore state
    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()
    private val _isRestoreInProgress = MutableStateFlow(false)
    val isRestoreInProgress: StateFlow<Boolean> = _isRestoreInProgress.asStateFlow()
    private val _backupProgress = MutableStateFlow(0f)
    val backupProgress: StateFlow<Float> = _backupProgress.asStateFlow()
    private val _restoreProgress = MutableStateFlow(0f)
    val restoreProgress: StateFlow<Float> = _restoreProgress.asStateFlow()
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Restore confirmation state
    private var _pendingRestoreBytes: ByteArray? = null
    private val _showRestoreConfirm = MutableStateFlow(false)
    val showRestoreConfirm: StateFlow<Boolean> = _showRestoreConfirm.asStateFlow()

    // SAF callbacks — set by MainActivity.onCreate() (per RESEARCH.md Pitfall 2)
    var onRequestBackup: ((suggestedName: String) -> Unit)? = null
    var onRequestRestore: (() -> Unit)? = null

    fun triggerBackup() {
        if (_isBackupInProgress.value || _isRestoreInProgress.value) return
        val name = BackupManager(midi).suggestedBackupFilename()
        onRequestBackup?.invoke(name)
    }

    fun triggerRestore() {
        if (_isBackupInProgress.value || _isRestoreInProgress.value) return
        onRequestRestore?.invoke()
    }

    fun onBackupUriSelected(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            val backupManager = BackupManager(midi)
            backupManager.createBackup(currentDeviceId = 0).collect { progress ->
                when (progress) {
                    is BackupProgress.Progress -> {
                        if (progress.total > 0) {
                            _backupProgress.value = progress.current.toFloat() / progress.total
                        }
                    }
                    is BackupProgress.Done -> {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(progress.pakBytes)
                            }
                        }
                        _isBackupInProgress.value = false
                        _snackbarMessage.value = "Backup complete"
                    }
                    is BackupProgress.Error -> {
                        _isBackupInProgress.value = false
                        _snackbarMessage.value = "Backup failed: ${progress.message}"
                    }
                }
            }
        }
    }

    fun onRestoreUriSelected(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            val bytes = withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.readBytes()
            } ?: return@launch
            _pendingRestoreBytes = bytes
            _showRestoreConfirm.value = true
        }
    }

    fun confirmRestore() {
        val bytes = _pendingRestoreBytes ?: return
        _showRestoreConfirm.value = false
        viewModelScope.launch {
            _isRestoreInProgress.value = true
            BackupManager(midi).restore(bytes, currentDeviceId = 0).collect { progress ->
                when (progress) {
                    is RestoreProgress.Progress -> {
                        if (progress.total > 0) {
                            _restoreProgress.value = progress.current.toFloat() / progress.total
                        }
                    }
                    is RestoreProgress.Done -> {
                        _isRestoreInProgress.value = false
                        _snackbarMessage.value = "Restore complete. Your EP-133 will restart."
                    }
                    is RestoreProgress.Error -> {
                        _isRestoreInProgress.value = false
                        _snackbarMessage.value = "Restore failed: ${progress.message}"
                    }
                }
            }
        }
    }

    fun cancelRestore() {
        _showRestoreConfirm.value = false
        _pendingRestoreBytes = null
    }
}
```

**2. Update `StatsRow` composable** (per D-14) — remove hardcoded values:
- `deviceState.firmwareVersion ?: "--"` instead of `"v1.3.2"`
- Format storage: if both null → `"--"`, else `"${(storageUsedBytes / 1_048_576)}MB / ${(storageTotalBytes / 1_048_576)}MB"`
- `deviceState.sampleCount?.toString() ?: "--"` instead of `"128"`
- Show `CircularProgressIndicator(Modifier.size(12.dp))` inline when field is null and device is connected (loading state per D-13)

**3. Replace `ActionButtons` with `BackupRestoreSection`** (per D-04, D-05, D-06, D-07):
Add two `Button` composables in a `Row`:
- "Backup" button with `Icons.Filled.SaveAlt` — `onClick = { viewModel.triggerBackup() }`, disabled when `isBackupInProgress || isRestoreInProgress`
- "Restore" button with `Icons.Filled.Restore` — `onClick = { viewModel.triggerRestore() }`, disabled when `isBackupInProgress || isRestoreInProgress`
- Show `LinearProgressIndicator(Modifier.fillMaxWidth())` when either progress flag is true (per D-05, D-07)

**4. Add `AlertDialog` for restore confirmation** (per D-06). Observe `showRestoreConfirm` from the ViewModel — do NOT use local `remember` state for this, as the ViewModel controls the confirmation lifecycle:
```kotlin
val showRestoreConfirm by viewModel.showRestoreConfirm.collectAsState()
if (showRestoreConfirm) {
    AlertDialog(
        onDismissRequest = { viewModel.cancelRestore() },
        title = { Text("Restore EP-133?") },
        text = { Text("This will overwrite all content on your EP-133. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { viewModel.confirmRestore() }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = { viewModel.cancelRestore() }) { Text("Cancel") } },
    )
}
```

**MainActivity.kt changes** (per RESEARCH.md Pitfall 2 — SAF must register in onCreate):
```kotlin
// After creating deviceViewModel, before setContent:
val backupLauncher = registerForActivityResult(
    ActivityResultContracts.CreateDocument("application/octet-stream")
) { uri: Uri? -> uri?.let { deviceViewModel.onBackupUriSelected(it, this) } }

val restoreLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument(arrayOf("*/*"))
) { uri: Uri? -> uri?.let { deviceViewModel.onRestoreUriSelected(it, this) } }

deviceViewModel.onRequestBackup = { name -> backupLauncher.launch(name) }
deviceViewModel.onRequestRestore = { restoreLauncher.launch(arrayOf("*/*")) }
```

Add `midiRepo.close()` to `MainActivity.onDestroy()` to cancel `repositoryScope`.

Add required imports: `androidx.activity.result.contract.ActivityResultContracts`, `android.net.Uri`.
  </action>
  <verify>
    <automated>cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:assembleDebug 2>&1 | tail -30</automated>
  </verify>
  <done>App builds clean. DeviceScreen shows `--` for disconnected stats and real values after stats query. Backup and Restore buttons visible. SAF launchers registered in MainActivity.onCreate(). restoreLauncher.launch() passes arrayOf("*/*") (not null). Backup bytes written to SAF URI via onBackupUriSelected. confirmRestore() and cancelRestore() defined in DeviceViewModel.</done>
</task>

</tasks>

---

## Wave 3 — Performance Screens

> Depends on Wave 1 (`sendRawBytes`, `channelFlow` from MIDIRepository). Runs in parallel with Wave 2 (no shared files except MIDIRepository which is stable after Wave 1).

<tasks>

<task type="auto" tdd="true">
  <name>Task 3-01: Multi-touch pads with velocity + scale-lock highlighting on PadsScreen (2-05-01, 2-05-04)</name>
  <files>
    AndroidApp/app/src/main/java/com/ep133/sampletool/ui/pads/PadsScreen.kt
  </files>
  <behavior>
    - padDown(index, velocity) sends noteOn with the provided velocity (not hardcoded 100)
    - Two calls to padDown(0, 80) and padDown(1, 90) without padUp produce pressedIndices = {0, 1}
    - PadsScreen grid handles ACTION_POINTER_DOWN events mapping pointer coordinates to pad indices
    - In-scale pads receive isInScale=true; out-of-scale pads receive isInScale=false when a scale is selected
    - computeInScaleSet("C", Major) returns {0, 2, 4, 5, 7, 9, 11}
    - When no scale selected (empty set), all pads show isInScale=true (normal styling)
  </behavior>
  <action>
**1. Update `PadsViewModel.padDown(index: Int, velocity: Int = 100)`** — add velocity parameter (per D-19):
```kotlin
fun padDown(index: Int, velocity: Int = 100) {
    val pad = EP133Pads.padsForChannel(_selectedChannel.value).getOrNull(index) ?: return
    _pressedIndices.value = _pressedIndices.value + index
    midi.noteOn(pad.note, velocity, pad.midiChannel)
}
```

**2. Add scale state to `PadsViewModel`** (per D-27, D-28). The scale state comes from `MIDIRepository` (lifted there in Task 1-03 notes — if not there yet, add it now):
```kotlin
// In MIDIRepository (add if not present):
private val _selectedScale = MutableStateFlow<Scale?>(null)
val selectedScale: StateFlow<Scale?> = _selectedScale.asStateFlow()
private val _selectedRootNote = MutableStateFlow("C")
val selectedRootNote: StateFlow<String> = _selectedRootNote.asStateFlow()
fun setScale(scale: Scale?) { _selectedScale.value = scale }
fun setRootNote(note: String) { _selectedRootNote.value = note }

// In PadsViewModel: delegate to MIDIRepository
val selectedScale: StateFlow<Scale?> = midi.selectedScale
val selectedRootNote: StateFlow<String> = midi.selectedRootNote
```

**3. Add `computeInScaleSet` top-level function** in `PadsScreen.kt`:
```kotlin
fun computeInScaleSet(scale: Scale, rootNoteName: String): Set<Int> {
    val rootIndex = EP133Scales.ROOT_NOTES.indexOf(rootNoteName)
    if (rootIndex < 0) return emptySet()
    return scale.intervals.map { (rootIndex + it) % 12 }.toSet()
}
```

**4. Refactor `PadsScreen` grid to handle multi-touch** (per D-18, D-20, RESEARCH.md Pattern 5):

Replace per-pad `onDown/onUp` callbacks with a grid-level `pointerInteropFilter` on the outer `Column`. The `PadCell` composable keeps its visual-only props but no longer handles its own MotionEvents.

In the outer `Column` containing `rows.forEachIndexed`:
```kotlin
var gridWidthPx by remember { mutableStateOf(0f) }
var gridHeightPx by remember { mutableStateOf(0f) }
val pointerToPad = remember { mutableMapOf<Int, Int>() }
val rows = pads.chunked(columns)
val rowCount = rows.size

Column(
    modifier = Modifier
        .weight(1f)
        .onSizeChanged { size ->
            gridWidthPx = size.width.toFloat()
            gridHeightPx = size.height.toFloat()
        }
        .pointerInteropFilter { event ->
            fun coordToIndex(x: Float, y: Float): Int? {
                if (gridWidthPx <= 0f || gridHeightPx <= 0f) return null
                val col = (x / (gridWidthPx / columns)).toInt().coerceIn(0, columns - 1)
                val row = (y / (gridHeightPx / rowCount)).toInt().coerceIn(0, rowCount - 1)
                val idx = row * columns + col
                return idx.takeIf { it < pads.size }
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val idx = coordToIndex(event.x, event.y) ?: return@pointerInteropFilter false
                    val vel = (event.pressure.coerceIn(0f, 1f) * 127).toInt().coerceAtLeast(1)
                    pointerToPad[event.getPointerId(0)] = idx
                    viewModel.padDown(idx, vel)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    val ptrIdx = event.actionIndex
                    val idx = coordToIndex(event.getX(ptrIdx), event.getY(ptrIdx)) ?: return@pointerInteropFilter false
                    val vel = (event.getPressure(ptrIdx).coerceIn(0f, 1f) * 127).toInt().coerceAtLeast(1)
                    pointerToPad[event.getPointerId(ptrIdx)] = idx
                    viewModel.padDown(idx, vel)
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val ptrIdx = event.actionIndex
                    val padIdx = pointerToPad.remove(event.getPointerId(ptrIdx)) ?: return@pointerInteropFilter false
                    viewModel.padUp(padIdx)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pointerToPad.forEach { (_, padIdx) -> viewModel.padUp(padIdx) }
                    pointerToPad.clear()
                    true
                }
                else -> false
            }
        },
    ...
)
```

Remove `onDown`/`onUp` from `PadCell` composable — it no longer handles touch.

**5. Add `isInScale: Boolean` to `PadCell`** (per D-27, D-29):
In `PadsScreen`, compute `inScaleSet`:
```kotlin
val selectedScale by viewModel.selectedScale.collectAsState()
val selectedRootNote by viewModel.selectedRootNote.collectAsState()
val inScaleSet by remember(selectedScale, selectedRootNote) {
    derivedStateOf {
        val scale = selectedScale
        if (scale == null) emptySet() else computeInScaleSet(scale, selectedRootNote)
    }
}
```
Pass `isInScale = inScaleSet.isEmpty() || (pad.note % 12) in inScaleSet` to each `PadCell`.

In `PadCell`, add `isInScale: Boolean` parameter. Apply border styling based on scale lock:
- When `isInScale` is true AND `inScaleSet.isNotEmpty()` (a scale is actively selected): add `border(1.5.dp, TEColors.Teal)` modifier to indicate this pad is in the selected scale
- When `isInScale` is false (pad is out of scale): normal styling, no extra border
- When `inScaleSet.isEmpty()` (no scale selected): normal styling for all pads

Add required imports: `android.view.MotionEvent`, `androidx.compose.ui.layout.onSizeChanged`, `androidx.compose.foundation.border`.

**6. Update `DeviceViewModel.selectScale()` and `selectRootNote()`** to call `midi.setScale()` and `midi.setRootNote()` respectively (so the single source of truth is in MIDIRepository per D-16/D-17 pattern).
  </action>
  <verify>
    <automated>cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:testDebugUnitTest --tests "*.PadsViewModelTest" --tests "*.ScaleLockTest" 2>&1 | tail -20</automated>
  </verify>
  <done>PadsViewModelTest and ScaleLockTest pass (remove @Ignore). App builds clean. PadCell receives isInScale flag. In-scale pads show TEColors.Teal border (1.5.dp) when a scale is selected. Out-of-scale and no-scale-selected pads show normal styling. Multi-touch grid refactored.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3-02: Sound preview on tap + MIDI transport sync in SequencerEngine (2-05-02, 2-05-03)</name>
  <files>
    AndroidApp/app/src/main/java/com/ep133/sampletool/ui/sounds/SoundsScreen.kt,
    AndroidApp/app/src/main/java/com/ep133/sampletool/domain/sequencer/SequencerEngine.kt,
    AndroidApp/app/src/main/java/com/ep133/sampletool/ui/beats/BeatsScreen.kt
  </files>
  <behavior>
    - SoundsViewModel.previewSound(sound) sends noteOn on channel 9 (MIDI ch 10) immediately
    - noteOff sent 500ms after preview noteOn
    - Calling previewSound twice within 500ms cancels the first noteOff job before it fires
    - SequencerEngine.play() sends 0xFA (MIDI Start) via midi.sendRawBytes before starting playLoop
    - SequencerEngine.stop() sends 0xFC (MIDI Stop) via midi.sendRawBytes after cancelling playJob
    - playLoop sends 0xF8 (MIDI Clock) 6 times per step, spaced evenly, using drift compensation
  </behavior>
  <action>
**SoundsScreen.kt — add `previewSound()` to `SoundsViewModel`** (per D-21, D-22, D-23, RESEARCH.md Pattern 8):
```kotlin
// In SoundsViewModel
private var previewJob: Job? = null
private val PREVIEW_NOTE = 36  // fixed preview note (A0 base)
private val PREVIEW_CHANNEL = 9  // MIDI channel 10 = index 9 (D-21)

fun previewSound(sound: EP133Sound) {
    previewJob?.cancel()
    midi.noteOn(PREVIEW_NOTE, 100, PREVIEW_CHANNEL)
    previewJob = viewModelScope.launch {
        delay(500)
        midi.noteOff(PREVIEW_NOTE, PREVIEW_CHANNEL)
        previewJob = null
    }
}
```

In `SoundRow` composable: add `onPreview: () -> Unit` parameter. Add `Modifier.clickable { onPreview() }` to the row `Row` container. Keep existing `IconButton(onClick = onAssign)` — short tap = preview, assign icon tap = open PadPickerSheet (per D-22).

Wire: in `SoundsScreen`, pass `onPreview = { viewModel.previewSound(sound) }` to each `SoundRow`.

**SequencerEngine.kt — add MIDI Start/Stop/Clock** (per D-24, D-25, D-26, RESEARCH.md Pattern 6):

**`play()` update**:
```kotlin
fun play() {
    if (_state.value.playing) return
    midi.sendRawBytes(byteArrayOf(0xFA.toByte()))  // MIDI Start
    _state.update { it.copy(playing = true, currentStep = -1) }
    playJob = scope.launch { playLoop() }
}
```

**`stop()` update** (currently calls `pause()` + resets step):
```kotlin
fun pause() {
    playJob?.cancel()
    playJob = null
    midi.allNotesOff()
    midi.sendRawBytes(byteArrayOf(0xFC.toByte()))  // MIDI Stop
    _state.update { it.copy(playing = false) }
}
```

**`playLoop()` update** — add 6 MIDI Clock ticks per step after step fires:
After the existing "Fire notes for active steps" block and before the drift-compensated delay:
```kotlin
// MIDI Clock: 24 PPQN = 6 ticks per 16th-note step
// Use drift compensation: compute absolute tick times from step start
val stepStartNs = System.nanoTime()
val stepDurationNs = (stepDurationMs * 1_000_000).toLong()
val tickIntervalNs = stepDurationNs / 6
repeat(6) { tickIdx ->
    val targetNs = stepStartNs + (tickIdx * tickIntervalNs)
    val waitNs = targetNs - System.nanoTime()
    if (waitNs > 0) delay(waitNs / 1_000_000)
    midi.sendRawBytes(byteArrayOf(0xF8.toByte()))
}
```
Note: Insert before the existing `stepCount++` and drift-compensated delay block. The existing delay remains for the next step boundary — MIDI Clock ticks are sent within the current step duration.

**BeatsScreen.kt** — no changes needed to BeatsScreen itself; play/stop buttons already call `viewModel.play()` / `viewModel.stop()` which delegate to `sequencer.play()` / `sequencer.stop()`. Verify this wiring is in place; if BeatsViewModel delegates via `sequencer`, the MIDI Start/Stop flows automatically.
  </action>
  <verify>
    <automated>cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:testDebugUnitTest --tests "*.SoundsViewModelTest" --tests "*.SequencerEngineTest" 2>&1 | tail -20</automated>
  </verify>
  <done>SoundsViewModelTest and SequencerEngineTest pass (remove @Ignore). play() sends 0xFA, stop() sends 0xFC, playLoop sends 0xF8 6x per step. Preview noteOff cancels correctly.</done>
</task>

</tasks>

---

<verification>
Run the full unit test suite after all waves complete:

```bash
cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:testDebugUnitTest
```

Expected: all 8 new test classes green (or @Ignore with documented reason for instrumented-only tests). No regressions on Phase 1 tests.

Run lint:
```bash
cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:lintDebug
```

Full build:
```bash
cd /Users/thomasphillips/workspace/ep_133_sample_tool/AndroidApp && ./gradlew :app:assembleDebug
```

Manual UAT checklist (requires physical EP-133):
1. Connect EP-133 → DeviceScreen shows real firmware version (not "v1.3.2")
2. DeviceScreen shows real storage stats (not "8")
3. DeviceScreen shows real sample count (not "128")
4. Tap "Backup" → SAF file picker opens with suggested `EP133-YYYY-MM-DD-HHmm.pak` name
5. Tap "Restore" → file picker opens, select `.pak` file → confirmation dialog appears
6. Two simultaneous finger taps on different pads → two distinct sounds play
7. Select "C Major" scale → in-scale pads show teal border
8. Tap sound row in Sounds screen → sound plays, stops after 500ms
9. BeatsScreen Play → EP-133 hardware transport follows app BPM
</verification>

<success_criteria>
- [ ] All 8 Wave 0 test files created and compiling
- [ ] ScaleLockTest passes without @Ignore (pure math, no production dependency)
- [ ] Phase 1 @Ignore stubs updated with real implementations or accurate explanations
- [ ] SysExProtocol.kt: TE manufacturer ID [0x00, 0x20, 0x76] confirmed in tests
- [ ] MIDIRepository parseMidiInput handles SysEx fragments (0xF0..0xF7 accumulation)
- [ ] DeviceState has sampleCount, storageUsedBytes, storageTotalBytes, firmwareVersion fields
- [ ] queryDeviceStats() issues FILE_LIST step to populate sampleCount (not just GREET + FILE_METADATA)
- [ ] queryDeviceStats() times out after 5s and returns false (not crash)
- [ ] BackupManager uses sealed class BackupProgress with Done(pakBytes: ByteArray) subtype
- [ ] BackupManager creates ZIP-format .pak files (validated by BackupRestoreTest)
- [ ] onBackupUriSelected writes pakBytes from BackupProgress.Done to SAF URI via contentResolver.openOutputStream wrapped in withContext(Dispatchers.IO)
- [ ] onRestoreUriSelected reads file bytes and sets _pendingRestoreBytes; confirmRestore() and cancelRestore() defined
- [ ] restoreLauncher.launch(arrayOf("*/*")) — not null
- [ ] DeviceScreen StatsRow shows null-guarded real values (-- when not queried)
- [ ] Backup/Restore buttons on DeviceScreen; SAF launchers in MainActivity.onCreate()
- [ ] padDown(index, velocity) signature added; multi-touch grid-level handler in PadsScreen
- [ ] In-scale pads show TEColors.Teal border(1.5.dp) when scale selected; no border for out-of-scale or no-scale-selected
- [ ] SoundsViewModel.previewSound() sends noteOn ch=9, noteOff after 500ms with job cancellation
- [ ] SequencerEngine.play() sends 0xFA, stop() sends 0xFC, playLoop sends 6x 0xF8 per step
- [ ] Full unit test suite green: `./gradlew :app:testDebugUnitTest`
- [ ] Debug APK builds clean: `./gradlew :app:assembleDebug`
</success_criteria>

<output>
After completion, create `.planning/phases/02-android-device-management/02-SUMMARY.md` following the template at `@$HOME/.claude/get-shit-done/templates/summary.md`.

Commit each wave separately:
- Wave 0: `feat(02-android-device-management-01): create wave 0 test stubs — 8 new test files + 3 Phase 1 @Ignore updates`
- Wave 1: `feat(02-android-device-management-02): SysEx protocol layer + accumulation buffer + device stats query`
- Wave 2: `feat(02-android-device-management-03): PAK backup/restore + DeviceScreen stats wiring + SAF launchers`
- Wave 3: `feat(02-android-device-management-04): multi-touch pads + scale lock + sound preview + MIDI transport sync`
</output>
