# Phase 2: Android Device Management - Context

**Gathered:** 2026-03-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Android users can view real device stats queried live from the EP-133 via SysEx, configure MIDI channel and scale/root note from DeviceScreen, and save or restore a full EP-133 backup to/from phone storage using the OS file picker. Phase also closes PERF-01..04: multi-touch+velocity on pads, sound preview before assignment, 16-step beat sequencer synced to EP-133 hardware transport, and scale-lock pad highlighting.

This phase is Android-only. iOS device management and iOS native screens are Phase 3/4.

Critical prerequisite: EP-133 SysEx protocol for device stat query, backup dump, and restore is not publicly documented — it must be reverse-engineered from `data/index.js` as the first plan before any device management work begins.

</domain>

<decisions>
## Implementation Decisions

### Backup File Format (DEV-03, DEV-04)

- **D-01:** Save backups as raw SysEx bytes in `.syx` format — industry standard, compatible with other EP-133 tools (TE's official app, third-party tools). No proprietary wrapper.
- **D-02:** Filename auto-generated from timestamp and device name: `EP133-{YYYY-MM-DD}-{HHmm}.syx`. User is NOT prompted to type a name — auto-naming removes friction. The OS file picker (SAF `CreateDocument`) shows the suggested name and user can rename there if needed.
- **D-03:** Phase 4 adds a backup library UI. Phase 2 backup/restore is raw file I/O only — no in-app library, no list of past backups. If user wants to see their backups they open the Files app.

### Backup/Restore UX (DEV-03, DEV-04)

- **D-04:** Backup and Restore buttons live on DeviceScreen — it is already the device management hub per Phase 1 decisions (D-16, D-17). No separate Backup screen in Phase 2.
- **D-05:** Backup flow: tap "Backup" → SAF `CreateDocument` picker opens with suggested filename → user confirms location → full SysEx dump begins → LinearProgressIndicator shows transfer progress → "Backup complete" snackbar on success.
- **D-06:** Restore flow: tap "Restore" → SAF `OpenDocument` picker opens (filter `.syx`) → user picks file → `AlertDialog` confirmation: "This will overwrite all content on your EP-133. This cannot be undone." with Cancel / Restore buttons → if confirmed, send SysEx restore → LinearProgressIndicator → "Restore complete. Your EP-133 will restart." snackbar.
- **D-07:** During backup/restore, disable other DeviceScreen interactions (channel selector, backup/restore buttons) to prevent concurrent operations. Re-enable on completion or failure.
- **D-08:** SAF contracts: use `ActivityResultContracts.CreateDocument("application/octet-stream")` for backup, `ActivityResultContracts.OpenDocument(arrayOf("*/*"))` for restore (filter `.syx` in UI label but accept `*/*` for compatibility with Files apps that misidentify MIME type).

### SysEx Accumulation (prerequisite for all device stats and backup)

- **D-09:** SysEx messages from the EP-133 are delivered in fragments over `MidiReceiver.onSend()`. An accumulation buffer in `MIDIRepository` (or a new `SysExAccumulator` utility) must collect fragments starting with `0xF0` until the terminating `0xF7` before dispatching to consumers. This is a blocker for device stat queries and backup — without it, large SysEx messages are silently dropped.
- **D-10:** The accumulation buffer belongs in `MIDIRepository` as a private implementation detail — callers receive complete SysEx messages only, never fragments.

### Real Device Stats (DEV-01)

- **D-11:** `DeviceState` model (in `EP133.kt`) gains new fields: `sampleCount: Int? = null`, `storageUsedBytes: Long? = null`, `storageTotalBytes: Long? = null`, `firmwareVersion: String? = null`. Null means "not yet queried" — distinct from 0 or empty string.
- **D-12:** `MIDIRepository` gains a `queryDeviceStats()` suspend function that sends the SysEx stat-query message (discovered via reverse-engineering in Plan 1) and awaits the response via a `CompletableDeferred<DeviceState>` or `Flow` collector, with a 5-second timeout.
- **D-13:** Stats are queried automatically on device connect (when `deviceState.connected` becomes true) — no user action required. The DeviceScreen shows `--` placeholders with a `CircularProgressIndicator` while the query is pending, then switches to real values.
- **D-14:** `StatsRow` in DeviceScreen removes the hardcoded "128", "8", "v1.3.2" values and reads from `DeviceState.sampleCount`, `storageUsedBytes/storageTotalBytes`, `firmwareVersion`. Null → `--` placeholder.

### Device Configuration (DEV-02)

- **D-15:** MIDI channel selector on DeviceScreen sets the app's send channel, not the EP-133's internal channel. The EP-133's internal MIDI channel is configured via its own hardware menu — the app cannot change it via MIDI. The app's channel selector must match whatever the EP-133 is set to.
- **D-16:** Channel selection is already present on DeviceScreen and wired to `PadsViewModel.selectedChannel`. The DeviceScreen ViewModel must share this state with PadsViewModel via the shared `MIDIRepository` (or a shared ViewModel at activity scope). No duplication — single source of truth.
- **D-17:** Scale/root note selection is local app state only — it is NOT sent to the EP-133. The EP-133 does not have an addressable scale parameter via MIDI. The scale selector updates local state used for PERF-04 scale-lock pad highlighting. Changing scale does not trigger any MIDI output.

### Multi-Touch + Velocity on Pads (PERF-01)

- **D-18:** Replace the current `pointerInteropFilter` with `ACTION_DOWN/UP` single-touch approach with a multi-touch implementation using `pointerInteropFilter` that handles `ACTION_DOWN`, `ACTION_POINTER_DOWN`, `ACTION_POINTER_UP`, and `ACTION_UP` — tracking each pointer ID to its pad index.
- **D-19:** Velocity is derived from touch pressure: `(event.pressure.coerceIn(0f, 1f) * 127).toInt().coerceAtLeast(1)`. If pressure is unavailable or always reports 1.0 (common on Android), fall back to a fixed velocity of 100.
- **D-20:** Multi-touch is handled at the grid level (the `LazyVerticalGrid` or parent container), not per-pad. Each `ACTION_POINTER_DOWN` event identifies which pad cell the touch falls in by converting the pointer coordinates to a grid cell index.

### Sound Preview Before Assignment (PERF-02)

- **D-21:** In `SoundsScreen`, tapping a sound row triggers a note-on (channel 10, note = sound's default pad note at velocity 100) followed by a note-off after 500ms. This previews the sound on the connected EP-133 before the user decides to assign it to a pad.
- **D-22:** The existing "assign to pad" flow (long-press → PadPickerSheet) is preserved unchanged. Short tap = preview. Long tap / tap on assign icon = open PadPickerSheet.
- **D-23:** Preview note-off is scheduled via `viewModelScope.launch { delay(500); midi.noteOff(...) }`. If the user taps another sound before 500ms, cancel the pending note-off job and start a new preview.

### 16-Step Beats Synced to EP-133 Hardware Transport (PERF-03)

- **D-24:** EP-133 hardware transport is controlled by MIDI Start (`0xFA`), Stop (`0xFC`), and MIDI Clock (`0xF8`) messages. The SequencerEngine must send these on play/stop. MIDI Clock requires 24 pulses per quarter note at the current BPM.
- **D-25:** When the user taps Play on BeatsScreen, the app sends MIDI Start + begins sending MIDI Clock ticks at 24 PPQN. The EP-133 will follow the app's tempo. The existing `SequencerEngine` step loop is the authoritative clock source — MIDI Clock ticks are sent from the same timing loop.
- **D-26:** Tempo sync is one-directional: app → EP-133. The app does not follow EP-133 clock input (that would require a full MIDI clock follower, out of scope). The BPM slider on BeatsScreen sets the app tempo which drives both the SequencerEngine and the MIDI Clock output.

### Scale Lock Pad Highlighting (PERF-04)

- **D-27:** In `PadsScreen`, each pad button receives an `isInScale: Boolean` flag. A pad is in-scale if its MIDI note number is in the set of notes generated by the selected scale + root note.
- **D-28:** Scale note calculation: `(rootNote + scaleIntervals.map { it % 12 }).toSet()` — then check `(pad.note % 12) in inScaleSet`. The existing `Scale` data class in `EP133.kt` already has an `intervals` list — use it.
- **D-29:** In-scale pads get a subtle teal (`TEColors.Teal`) border or overlay. Out-of-scale pads show normal styling. When no scale is selected (default), all pads show normal styling. The visual treatment is light — not the orange press glow (which is preserved for all pads).

### Claude's Discretion

- Exact visual treatment of the storage bar in DeviceScreen (current `LinearProgressIndicator` — keep or replace)
- Whether `queryDeviceStats()` uses a `CompletableDeferred` or a dedicated `StateFlow` slot with timeout
- Exact coroutine scope for SAF launcher registration (Activity scope vs viewModelScope)
- Loading shimmer or `CircularProgressIndicator` while stats load
- Whether backup/restore progress shows byte count or just indeterminate spinner (depends on what SysEx dump messages make available)
- Error snackbar duration and text for backup/restore failure cases

</decisions>

<specifics>
## Specific Ideas

- The EP-133 SysEx protocol must be reverse-engineered from `data/index.js` (1.75MB compiled React bundle). The researcher should look for patterns like `0xF0 0x00 0x21 0x7B` (Teenage Engineering manufacturer ID) followed by device-specific commands. The web app uses this protocol already — the researcher can grep the compiled bundle for `0xF0` byte arrays.
- Multi-touch tracking at the grid container level (not per-pad) is the established Android pattern for drum pads — similar to how Beat Maker GO and GarageBand handle it.
- `.syx` files saved to phone storage should appear in Android Files app under the app's documents folder via SAF. Using `CreateDocument` with suggested name gives the user the OS file picker UX they already know.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 1 foundation (patterns to follow)
- `.planning/phases/01-midi-foundation/01-CONTEXT.md` — Locked decisions D-01..D-20 from Phase 1 (threading, connection UI, MIDI dispatch patterns)
- `.planning/phases/01-midi-foundation/01-RESEARCH.md` — Architecture patterns: mainHandler.post, MutableStateFlow thread safety, SequencerEngine scope

### Domain models and MIDI layer
- `AndroidApp/app/src/main/java/com/ep133/sampletool/domain/model/EP133.kt` — `DeviceState`, `Pad`, `EP133Sound`, `Scale`, `PermissionState` — models to extend
- `AndroidApp/app/src/main/java/com/ep133/sampletool/domain/midi/MIDIRepository.kt` — MIDI API to extend with stats query and backup/restore
- `AndroidApp/app/src/main/java/com/ep133/sampletool/midi/MIDIPort.kt` — `MIDIPort` interface; any new MIDI methods must be added here first

### UI screens to modify
- `AndroidApp/app/src/main/java/com/ep133/sampletool/ui/device/DeviceScreen.kt` — Current hardcoded stats, existing ChannelSelector/ScaleModeSelector, location for backup/restore buttons
- `AndroidApp/app/src/main/java/com/ep133/sampletool/ui/pads/PadsScreen.kt` — Current single-touch pointerInteropFilter; multi-touch + scale highlight changes here
- `AndroidApp/app/src/main/java/com/ep133/sampletool/ui/sounds/SoundsScreen.kt` — Add sound preview (tap) alongside existing load-to-pad (long press/assign)
- `AndroidApp/app/src/main/java/com/ep133/sampletool/ui/beats/BeatsScreen.kt` — Wire MIDI Start/Stop/Clock from SequencerEngine play/stop

### Sequencer
- `AndroidApp/app/src/main/java/com/ep133/sampletool/domain/sequencer/SequencerEngine.kt` — Add MIDI Clock output from the timing loop; existing play/stop to also send MIDI Start/Stop

### SysEx protocol source (reverse-engineering target)
- `data/index.js` — Compiled web app (~1.75MB). Contains all EP-133 SysEx message construction. Researcher must search for byte patterns: `0xF0`, `0x00 0x21 0x7B` (TE manufacturer ID), `0xF7`. Device stats query, backup dump trigger, and restore sequence are all in this file.

### Requirements
- `.planning/REQUIREMENTS.md` §DEV-01..DEV-04 — Device management acceptance criteria
- `.planning/REQUIREMENTS.md` §PERF-01..PERF-04 — Performance screen acceptance criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `StatCard` composable (DeviceScreen.kt) — already styled for stats display; just needs real data
- `LinearProgressIndicator` (DeviceScreen.kt) — already in StatsRow for storage bar; wire to real bytes
- `ChannelSelector` composable (DeviceScreen.kt) — already exists and is wired; needs shared state with PadsViewModel
- `ScaleModeSelector` composable (DeviceScreen.kt) — already exists; scale selection becomes the source of truth for PERF-04
- `Scale.intervals` list (EP133.kt line 42) — already has scale intervals; use directly for PERF-04 note membership check
- `MIDIRepository.noteOn/noteOff` (MIDIRepository.kt) — already exist; PERF-02 preview and PERF-03 clock use these
- `viewModelScope` (PadsViewModel, SoundsViewModel) — standard scope for preview note-off cancellation

### Established Patterns
- `mainHandler.post { ... }` for all MIDI callbacks → must be preserved in any new MidiReceiver callbacks (SysEx accumulator)
- `StateFlow<DeviceState>` in MIDIRepository → extend DeviceState rather than adding a parallel state object
- `ActivityResultContracts` SAF pattern — no existing usage, but standard Android pattern; register in Activity or Fragment, not in Composable
- Conventional commit format: `feat(02-android-device-management-XX): ...`

### Integration Points
- `MIDIRepository.deviceState: StateFlow<DeviceState>` — add stats fields to `DeviceState`; all screens consuming this flow automatically see updates
- `SequencerEngine.playLoop()` — MIDI Clock ticks must be emitted from this loop at 24 PPQN relative to current BPM; `midiRepo.sendRawMidi(byteArrayOf(0xF8.toByte()))` per tick
- `MainActivity` — SAF `ActivityResultLauncher` registration must happen here (before `onStart`); results forwarded to DeviceScreen ViewModel

</code_context>

<deferred>
## Deferred Ideas

- Backup library / in-app backup history list — Phase 4 (PROJ-03)
- Per-project backup (single project slot, not full device) — Phase 4 (PROJ-02)
- Share backup via Android share intent — Phase 4 (PROJ-04)
- iCloud/Google Drive sync of backups — explicitly out of scope (REQUIREMENTS.md Out of Scope)
- iOS device management (stats, backup/restore) — Phase 4
- BPM tap tempo (PERF-V2-01) — v2 requirement, not Phase 2
- MIDIKit adoption for iOS — evaluate at Phase 3 kickoff

</deferred>

---

*Phase: 02-android-device-management*
*Context gathered: 2026-03-29*
