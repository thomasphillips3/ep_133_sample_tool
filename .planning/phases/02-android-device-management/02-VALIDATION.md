---
phase: 2
slug: android-device-management
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-29
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + `kotlinx-coroutines-test` 1.7.3 (unit); Compose UI test (instrumented) |
| **Config file** | None — standard Android unit test runner |
| **Quick run command** | `cd AndroidApp && ./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `cd AndroidApp && ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest` |
| **Estimated runtime** | ~45 seconds (quick unit); ~5-8 minutes (full instrumented) |

---

## Sampling Rate

- **After every task commit:** Run `cd AndroidApp && ./gradlew :app:testDebugUnitTest`
- **After every plan wave:** Run `cd AndroidApp && ./gradlew :app:testDebugUnitTest :app:lintDebug`
- **Before `/gsd:verify-work`:** Full unit suite green + manual UAT on physical hardware
- **Max feedback latency:** 45 seconds (unit)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 2-01-01 | SysEx protocol | 0 | DEV-01/03/04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.SysExProtocolTest"` | ❌ W0 | ⬜ pending |
| 2-01-02 | SysEx accumulator | 0 | DEV-01/03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.SysExAccumulatorTest"` | ❌ W0 | ⬜ pending |
| 2-01-03 | Device stats | 0 | DEV-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.MIDIRepositoryStatsTest"` | ❌ W0 | ⬜ pending |
| 2-01-04 | Backup/restore | 0 | DEV-03/04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.BackupRestoreTest"` | ❌ W0 | ⬜ pending |
| 2-01-05 | Multi-touch pads | 0 | PERF-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.PadsViewModelTest"` | ❌ W0 | ⬜ pending |
| 2-01-06 | Sound preview | 0 | PERF-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.SoundsViewModelTest"` | ❌ W0 | ⬜ pending |
| 2-01-07 | Sequencer transport | 0 | PERF-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.SequencerEngineTest"` | ❌ W0 | ⬜ pending |
| 2-01-08 | Scale lock | 0 | PERF-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ScaleLockTest"` | ❌ W0 | ⬜ pending |
| 2-02-01 | SysEx accumulator impl | 1 | DEV-01/03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.SysExAccumulatorTest"` | ❌ W0 | ⬜ pending |
| 2-03-01 | Device stats wire-up | 1 | DEV-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.MIDIRepositoryStatsTest"` | ❌ W0 | ⬜ pending |
| 2-03-02 | DeviceScreen stats display | 1 | DEV-01 | manual | Verify real values on DeviceScreen | N/A | ⬜ pending |
| 2-04-01 | SAF backup | 1 | DEV-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.BackupRestoreTest"` | ❌ W0 | ⬜ pending |
| 2-04-02 | SAF restore | 1 | DEV-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.BackupRestoreTest"` | ❌ W0 | ⬜ pending |
| 2-05-01 | Multi-touch grid | 1 | PERF-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.PadsViewModelTest"` | ❌ W0 | ⬜ pending |
| 2-05-02 | Sound preview impl | 1 | PERF-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.SoundsViewModelTest"` | ❌ W0 | ⬜ pending |
| 2-05-03 | MIDI transport sync | 1 | PERF-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.SequencerEngineTest"` | ❌ W0 | ⬜ pending |
| 2-05-04 | Scale pad highlighting | 1 | PERF-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ScaleLockTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Create `AndroidApp/app/src/test/java/com/ep133/sampletool/SysExProtocolTest.kt` — 7-bit encoding roundtrip, frame building, GREET response parsing
- [ ] Create `AndroidApp/app/src/test/java/com/ep133/sampletool/SysExAccumulatorTest.kt` — fragment accumulation, multi-message stream, boundary cases
- [ ] Create `AndroidApp/app/src/test/java/com/ep133/sampletool/MIDIRepositoryStatsTest.kt` — `queryDeviceStats()` timeout (5s), DeviceState field population
- [ ] Create `AndroidApp/app/src/test/java/com/ep133/sampletool/BackupRestoreTest.kt` — file format validation, byte boundary checks
- [ ] Create `AndroidApp/app/src/test/java/com/ep133/sampletool/PadsViewModelTest.kt` — multi-touch simultaneous noteOn, velocity from pressure (0.5f → ~63)
- [ ] Create `AndroidApp/app/src/test/java/com/ep133/sampletool/SoundsViewModelTest.kt` — preview noteOn/noteOff timing, cancellation of previous preview (use `runTest` + `advanceTimeBy(500)`)
- [ ] Create `AndroidApp/app/src/test/java/com/ep133/sampletool/SequencerEngineTest.kt` — MIDI Start (`0xFA`) on play, MIDI Stop (`0xFC`) on stop, Clock ticks at 24 PPQN
- [ ] Create `AndroidApp/app/src/test/java/com/ep133/sampletool/ScaleLockTest.kt` — `computeInScaleSet("C", Major)` → `{0,2,4,5,7,9,11}`, note 60 in C major, note 61 not in C major, chromatic = all notes
- [ ] Implement (un-ignore) existing `@Ignore` stubs from Phase 1:
  - `MIDIManagerThreadingTest.onMidiReceived_isInvokedOnMainThread`
  - `MIDIRepositoryTest.deviceState_emitsConnectedTrueWhenDeviceAdded`
  - `SequencerEngineScopeTest.close_cancelsPendingNoteOffJobs`

*Framework install: Not required — all dependencies already in `testImplementation` (`JUnit 4.13.2`, `kotlinx-coroutines-test:1.7.3`).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real firmware version shown on DeviceScreen | DEV-01 | Requires physical EP-133 with known firmware | 1. Connect EP-133. 2. Open DeviceScreen. 3. Verify FIRMWARE stat shows actual version (e.g. "v1.3.2"), not "--" or hardcoded value. |
| Backup file is valid and opens on desktop | DEV-03 | Requires saving file and loading it in TE Sample Manager | 1. Connect EP-133. 2. Tap "Backup". 3. Save file. 4. Transfer to Mac. 5. Verify TE Sample Manager or the Electron app can read it. |
| Restore actually restores device state | DEV-04 | Requires hardware verify before and after | 1. Note current pad assignments. 2. Restore an older backup. 3. Verify device state changes to match backup content. |
| Multi-touch triggers two pads simultaneously | PERF-01 | Android emulator does not support multi-touch | 1. On physical device, place two fingers on different pads. 2. Verify two distinct sounds trigger simultaneously. |
| EP-133 transport follows app play/stop | PERF-03 | Requires hardware with EP-133 | 1. Connect EP-133. 2. Start BeatsScreen sequencer. 3. Verify EP-133 lights/metronome follows the app's BPM. 4. Tap Stop. 5. Verify EP-133 transport also stops. |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (8 new files + 3 un-ignored stubs)
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s (unit) / 8min (full)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
