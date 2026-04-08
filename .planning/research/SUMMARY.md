# Project Research Summary

**Project:** EP-133 Sample Tool — Native Mobile Apps
**Domain:** Native mobile companion app for USB MIDI hardware groovebox (iOS SwiftUI + Android Kotlin/Compose)
**Researched:** 2026-03-27
**Confidence:** HIGH (architecture + pitfalls grounded in existing codebase); MEDIUM-HIGH (stack additions, feature landscape)

## Executive Summary

The EP-133 Sample Tool mobile apps are hardware companion apps — a well-understood product category with clear precedents (Cornerman for K.O. II, TE's OP-Z app, Patch Base). The recommended architecture is a layered system: a platform-agnostic domain layer (MIDIRepository, SequencerEngine, ProjectManager) backed by platform-specific MIDI and file storage implementations, topped by thin Compose/SwiftUI screens that only render and dispatch events. Android already has this architecture working for five screens; the gap is (1) Android project/file management, (2) all iOS native screens, and (3) SysEx-level features (backup/restore, real device stats) that currently fall through to a WebView. The recommended approach is to harden what exists before building new features: USB connection reliability and correct MIDI threading must be fixed first, because every interactive feature depends on a stable connection.

The biggest risk is threading. Both platforms have latent bugs where MIDI callbacks mutate UI state from non-main threads. On Android this produces non-deterministic `IllegalStateException` crashes under load; on iOS it causes WKWebView crashes and Swift 6 strict-concurrency violations. These bugs are silent in simulators and on lightly-loaded developer hardware, but surface in production with real EP-133 data flow. Fixing them before any new screen is wired to MIDI is the highest-leverage early action.

iOS native UI is the largest single workstream — all primary screens (Pads, Beats, Sounds, Device) need to be built from scratch in SwiftUI, mirroring the Android screens but with CoreMIDI and `@Observable`/`@MainActor` state management. The iOS scope alone is roughly equivalent to the entire Android Compose UI build. Project-level backup (not just full-device) is the flagship differentiator of this app over competitors; it requires understanding the EP-133 SysEx protocol at project granularity and should be tackled in a dedicated phase after core MIDI and UI are stable.

## Key Findings

### Recommended Stack

The Android stack is largely complete for the next milestone. The meaningful additions are: `lifecycle-runtime-compose` for `collectAsStateWithLifecycle()` (already at 2.7.0 in the codebase — just use the API), Navigation 2.8 typed routes, and Hilt for dependency injection once manual wiring in `MainActivity` becomes brittle. A Kotlin upgrade from 1.9.22 to 2.0.21 is a one-day prerequisite that unblocks the full 2026 BOM stack and should happen in a dedicated chore commit before feature work.

For iOS, the single new addition is MIDIKit 0.11.0 (via SPM) — it replaces hand-rolled CoreMIDI C-API calls with type-safe Swift and handles `MIDIEventList`, SysEx7, and Swift 6 strict concurrency correctly. MIDIKit requires Xcode 16 (current CLAUDE.md says 15+; this must be updated). State management uses `@Observable` + `@MainActor` on iOS 17+, with `ObservableObject` fallback for the iOS 16 minimum target. File management uses SwiftUI's `fileExporter`/`fileImporter` modifiers with a custom `UTType` for `.ep133proj` files.

**Core technologies:**
- `collectAsStateWithLifecycle()` (Android, already available): lifecycle-safe Flow collection — prevents stale MIDI processing when backgrounded
- Navigation 2.8 typed routes (Android): compile-time safety for screen navigation arguments
- Hilt 2.48/KSP (Android): clean ViewModel scoping; prerequisite for Kotlin 2.0 upgrade to land Hilt 2.56
- MIDIKit 0.11.0 (iOS, new dependency): Swift-native CoreMIDI wrapper; Swift 6 compliant; Xcode 16 required
- `@Observable` + `@MainActor` (iOS 17+): granular per-property observation for MIDI-driven UI; avoids full ViewModel re-render
- `fileExporter`/`fileImporter` + custom UTType (iOS): native project file management without UIDocumentPickerViewController boilerplate
- `kotlinx-serialization-json 1.7+` (Android): typed serialization for project files and Navigation 2.8 type-safe routes

See `/Users/thomasphillips/workspace/ep_133_sample_tool/.planning/research/STACK.md` for full detail.

### Expected Features

The Android Compose UI has five working screens (Pads, Beats, Sounds, Chords, Device) but all stat fields in Device are hardcoded, backup/restore falls through to WebView, and there is no project browser. iOS has no native screens — only WKWebView. The feature gap this milestone closes is: real device data, working backup/restore natively, iOS native UI parity, and hardened USB connection handling.

**Must have (table stakes — v1 launch):**
- Robust USB connect/disconnect/permission flow — root dependency for every interactive feature; current 500ms hardcoded delay is unreliable
- Pad performance (touch → MIDI to EP-133) — core is built on Android; needs threading validation and iOS build
- Sound assignment from factory library — SoundsScreen exists on Android; sound preview (tap to audition before assigning) is low-cost and high-reward
- 16-step beat sequencer — BeatsScreen exists; needs device transport sync
- Full backup to phone storage — currently WebView fallback on both platforms; high value, high complexity
- Restore from backup — paired with backup
- Real device stats (sample count, storage, firmware) — currently hardcoded; must be replaced with SysEx query before shipping
- iOS native UI (Pads, Beats, Sounds, Device) — SwiftUI build, largest single workstream
- Error states with recovery guidance — currently no error states shown in native UI

**Should have (competitive differentiators — v1.x after core is stable):**
- Project browser (enumerate 9 project slots) — enables project-level backup
- Pattern-level backup — backup one project without full device dump; flagship differentiator over Cornerman
- Backup library with timestamps and named files — dramatically better UX once multiple backups exist
- Share backup via OS share sheet — low cost, high value; users want to move files to desktop app
- Scale lock on pads — no competitor in this device category does this; differentiates performance use case
- BPM tap tempo — low cost additive to Beats screen

**Defer (v2+):**
- Pattern steps per track / polyrhythm — requires SequencerEngine architectural change
- Song mode / scene arrangement UI — EP-133 OS 2.0 feature; mobile UI is complex
- Deep CC parameter editing (Patch Base-style) — deferred until EP-133 SysEx protocol is better mapped
- BLE MIDI — excluded by project constraints; latency unacceptable for performance use case

See `/Users/thomasphillips/workspace/ep_133_sample_tool/.planning/research/FEATURES.md` for full prioritization matrix and competitor analysis.

### Architecture Approach

Both platforms share the same four-layer architecture: UI screens (render-only) over feature ViewModels (domain state → UI state) over a domain layer (MIDIRepository, SequencerEngine, ProjectManager) over platform abstractions (MIDIPort interface/protocol, FileStore). The single most important architectural rule is that `MIDIManager` is created exactly once at app startup (`MainActivity`/`@main` App struct) and injected as a dependency everywhere — never instantiated per-screen. All MIDI-driven state transitions happen through the domain stream (SharedFlow on Android, AsyncStream on iOS), never directly in callbacks. Kotlin Multiplatform is explicitly not recommended: the MIDI domain layer is tightly coupled to platform APIs through `MIDIPort`, and the 5-file domain layer is small enough to maintain separately on each platform. Shared data (MIDI note mappings, sound names, scales) lives in `shared/` JSON files loaded once at startup.

**Major components:**
1. `MIDIPort` interface/protocol — abstracts USB MIDI hardware; enables unit testing with `MockMIDIPort`; already exists on Android, must be defined on iOS
2. `MIDIRepository` — typed MIDI operations (noteOn/Off, CC, PC, loadSoundToPad) and device state as reactive streams; the boundary between hardware and business logic
3. `SequencerEngine` — 16-step sequencer with drift-compensated timing; Android uses `System.nanoTime()`; iOS should use `ContinuousClock`
4. `ProjectManager` — all project file I/O; the only component that touches the filesystem; exposes project library as a stream; new on both platforms
5. `{Feature}ViewModel` per screen — translates domain state to UI-ready state; receives MIDIRepository by injection; never holds hardware references

Build order follows the layers: MIDI protocol + MIDIManager first, then domain layer (testable against MockMIDIPort), then ProjectManager, then UI screens one at a time starting with Pads (simplest MIDI) through Device, Sounds, Beats, to Projects.

See `/Users/thomasphillips/workspace/ep_133_sample_tool/.planning/research/ARCHITECTURE.md` for full component diagram and data flow.

### Critical Pitfalls

1. **MIDI callbacks mutating Compose state directly (Android)** — `MidiReceiver.onSend()` fires on a MIDI thread; writing to `mutableStateOf` from there causes non-deterministic `IllegalStateException` crashes. Prevention: use only `MutableStateFlow` in ViewModels; emit with `tryEmit()` on background thread; collect on main. The `SequencerEngine` already has a `CoroutineScope(Dispatchers.Default)` scope leak that exposes this risk — fix in the same phase that builds the sequencer.

2. **iOS `evaluateJavaScript` called from CoreMIDI callback thread** — `MIDIManager.swift` dispatches `onDevicesChanged` to main but NOT `onMIDIReceived`; this is a latent crash waiting to surface when iOS native SwiftUI screens start receiving MIDI events. Fix: add `DispatchQueue.main.async` wrap to `onMIDIReceived` before wiring it to any SwiftUI state.

3. **Android USB permission race condition** — hardcoded 500ms delay after permission grant is unreliable on slow devices; the correct fix is to rely exclusively on `MidiManager.DeviceCallback.onDeviceAdded` for re-enumeration, with exponential-backoff retry, and to add a distinct "Awaiting permission" UI state.

4. **SysEx fragmentation across `onSend` calls (Android) and buffer overflow in `sendRawBytes` (iOS)** — EP-133 backup/restore transfers kilobytes to megabytes of SysEx; Android's `onSend()` does not guarantee atomic delivery of a complete SysEx message; iOS's `sendRawBytes` silently truncates messages larger than ~64KB. Both must be addressed before any backup/restore feature is implemented. Recovery cost is HIGH if these are retrofitted post-feature.

5. **Android scoped storage / iOS security-scoped resources** — API 29+ blocks direct `File` path access outside `filesDir`; use SAF `ActivityResultContracts` for all user-facing file I/O. iOS security-scoped resources must always be released via `defer { url.stopAccessingSecurityScopedResource() }` or the OS silently revokes all file access after quota exhaustion.

See `/Users/thomasphillips/workspace/ep_133_sample_tool/.planning/research/PITFALLS.md` for the full pitfall catalog including threading, deprecated APIs, and security notes.

## Implications for Roadmap

Based on the dependency structure from FEATURES.md and the build order from ARCHITECTURE.md, the natural phase structure is foundation-first: harden the MIDI layer before building features on top of it, build iOS parallel to Android v1.x features rather than after them.

### Phase 1: Foundation — MIDI Reliability and Threading

**Rationale:** USB connection handling is the root dependency in FEATURES.md. Every interactive feature requires a live, stable connection. Two latent bugs (Android MIDI state on wrong thread, iOS `onMIDIReceived` thread dispatch) will surface as crashes the moment new MIDI-driven screens are built — fixing them after the fact is HIGH recovery cost. This phase has no user-facing deliverables but unblocks everything else.

**Delivers:** Stable USB connect/disconnect/permission flow on Android; correct CoreMIDI threading on iOS; `MIDIPort` Swift protocol defined; `SequencerEngine` scope leak fixed; "Awaiting permission" UI state added to DeviceScreen.

**Addresses:** "Robust USB connect/disconnect handling" (P1 table stakes), "Error states with recovery guidance" (P1)

**Avoids:** Pitfalls 1 (Compose state mutation), 2 (iOS evaluateJavaScript thread), 3 (USB permission race), 9 (connectAllSources thread safety on iOS)

**Research flag:** Standard patterns — no additional research needed; fixes are clearly defined in PITFALLS.md.

---

### Phase 2: Android Project Management and Real Device Data

**Rationale:** The Android native UI is already feature-complete for interaction (Pads, Beats, Sounds) but ships fabricated data (hardcoded stats, no backup). This phase closes those gaps on the platform where the most code already exists, de-risking the SysEx protocol work before it is replicated on iOS.

**Delivers:** Real device stats on DeviceScreen (sample count, storage used, firmware version via SysEx query); full backup to phone storage via SAF; restore from backup; `ProjectManager` domain component; SAF file I/O pattern established.

**Addresses:** "Real device stats" (P1), "Full backup to phone" (P1), "Restore from backup" (P1)

**Uses:** `kotlinx-serialization-json` for project file model; `ActivityResultContracts.CreateDocument`/`OpenDocument` for SAF

**Avoids:** Pitfall 4 (SysEx fragmentation — implement accumulation buffer before any backup work), Pitfall 6 (scoped storage — establish SAF pattern from the first file I/O feature)

**Research flag:** Needs deeper research — EP-133 SysEx protocol for device stats query and backup format is not publicly documented. Research should map the specific SysEx messages used by the existing `data/index.js` web app for backup/restore and device enumeration before implementation begins.

---

### Phase 3: iOS Native UI — Core Screens

**Rationale:** FEATURES.md rates iOS native UI as HIGH value and HIGH cost; it is a full parallel build. Architecture research is clear about the build order: MIDI protocol → domain → screens. Phase 1 establishes the `MIDIPort` Swift protocol; this phase builds the iOS domain layer and all four primary screens against it. The WebView fallback remains for backup/restore, which is acceptable at iOS launch per the MVP definition.

**Delivers:** SwiftUI native screens for Pads, Beats, Sounds, and Device (with real data where available); `MIDIRepository.swift` and `SequencerEngine.swift`; `@Observable`/`@MainActor` ViewModel pattern established; iOS 16/17 fallback handling for `@Observable`.

**Addresses:** "iOS native UI parity" (P1), "Live pad performance on iOS" (P1), "Sound preview via MIDI" (P1)

**Uses:** MIDIKit 0.11.0 (via SPM); `@Observable` + `@MainActor`; `ContinuousClock` for sequencer timing; Xcode 16 (CLAUDE.md must be updated)

**Avoids:** Pitfall 2 (evaluateJavaScript threading — fixed in Phase 1), Pitfall 5 (iOS sendRawBytes overflow — fix before any SysEx in this phase), Pitfall 9 (connectAllSources thread safety — fixed in Phase 1)

**Research flag:** Standard patterns — MIDIKit 0.11.0 is well-documented; SwiftUI `@Observable` migration is well-documented. The MIDIKit vs raw CoreMIDI decision should be confirmed with the existing `MIDIManager.swift` maintainer before the phase starts; ARCHITECTURE.md notes that the existing code may be sufficient without MIDIKit.

---

### Phase 4: iOS Project Management and Cross-Platform Polish

**Rationale:** Once iOS native UI is stable and the Android backup/restore SysEx protocol is mapped (Phase 2), implementing iOS backup/restore is lower-risk code reuse. This phase also adds the v1.x differentiators (project browser, pattern-level backup, backup library, share sheet) on both platforms.

**Delivers:** iOS backup/restore (native, not WebView); `ProjectManager.swift`; project browser on both platforms; backup library with timestamps; share backup via OS share sheet; `fileExporter`/`fileImporter` with custom UTType.

**Addresses:** "Project browser" (P2), "Pattern-level backup" (P2 differentiator), "Backup library with timestamps" (P2), "Share backup via Share Sheet" (P2)

**Uses:** `fileExporter`/`fileImporter` SwiftUI modifiers; custom `UTType` (com.ep133sampletool.project); `MIDISendSysex()` for large iOS SysEx transfers

**Avoids:** Pitfall 5 (iOS sendRawBytes overflow — replace with MIDIEventList/MIDISendSysex), Pitfall 7 (iOS security-scoped resource lifecycle — establish `defer` pattern from first file pick)

**Research flag:** Needs research — EP-133 SysEx protocol at project granularity (project-level vs full-device backup boundary) is the key unknown. This is the flagship differentiator; getting the protocol boundary wrong here is HIGH recovery cost.

---

### Phase 5: Performance Polish and v1.x Features

**Rationale:** Once backup/restore is proven on real hardware, the remaining v1.x performance features (scale lock, BPM tap tempo, sequencer device transport sync) are lower-risk additions to existing screens.

**Delivers:** Scale lock on pads (visual in-scale highlighting); BPM tap tempo (additive to Beats screen); device transport sync (MIDI Start/Stop/Continue); Kotlin upgrade to 2.0.21; Hilt dependency injection; Navigation 2.8 typed routes; targetSdk 35 migration with API 33 MIDI API update.

**Addresses:** "Scale lock on pads" (P2), "BPM tap tempo" (P2), "Pattern playback synced to device" (P1)

**Uses:** Hilt 2.56 + KSP; Navigation 2.8 typed routes; Compose BOM 2026.03.00; Kotlin 2.0.21 (prerequisite for Hilt 2.56 and BOM 2026)

**Avoids:** Pitfall 8 (deprecated Android MIDI API — migrate `registerDeviceCallback` before targetSdk 35 upgrade)

**Research flag:** Standard patterns — scale lock, tap tempo, and dependency injection are all well-documented patterns; no research phase needed.

---

### Phase Ordering Rationale

- **Foundation before features** — the two MIDI threading bugs (Pitfalls 1 and 2) will make every new screen unreliable if not fixed first. Establishing correct patterns before building on them is cheaper than retrofitting.
- **Android project management before iOS** — the EP-133 SysEx protocol for backup is undocumented; discovering and mapping it on Android (where more infrastructure exists) de-risks the iOS implementation.
- **iOS native UI as a full parallel track** — it does not depend on Android completion; only on Phase 1 (MIDI threading fixes and MIDIPort protocol). Building it in Phase 3 rather than last ensures iOS users get feature parity before v1.x features ship on Android.
- **Project browser enables pattern-level backup** — per the feature dependency tree in FEATURES.md, project enumeration is a prerequisite for the flagship project-level backup differentiator.
- **Stack modernization deferred to Phase 5** — Kotlin upgrade and Hilt migration are important but do not block features; deferring them prevents introducing build churn during the highest-risk implementation phases.

### Research Flags

Phases needing `/gsd:research-phase` during planning:

- **Phase 2 (Android Project Management):** EP-133 SysEx protocol for device stat queries and backup format is not publicly documented. Must reverse-engineer from `data/index.js` before implementation. High impact if misunderstood.
- **Phase 4 (iOS Project Management):** EP-133 SysEx project-level backup boundary — what constitutes "one project" in the dump format, and whether the protocol supports partial restores. This is the defining question for the flagship differentiator.

Phases with standard patterns (skip research-phase):
- **Phase 1 (Foundation):** Threading fixes and USB permission handling are clearly specified in PITFALLS.md with exact fixes. No unknowns.
- **Phase 3 (iOS Native UI):** MIDIKit, `@Observable`, and SwiftUI screen patterns are well-documented. Confirm MIDIKit vs. raw CoreMIDI decision with maintainer before starting; otherwise no research needed.
- **Phase 5 (Polish):** Hilt, Navigation 2.8, tap tempo, and scale lock are all standard patterns. SDK migration steps are documented in PITFALLS.md.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Android additions verified against official Jetpack docs; MIDIKit verified against GitHub releases; iOS patterns verified against Apple docs. One open question: MIDIKit vs. existing MIDIManager.swift — architecture research says existing code may be sufficient. |
| Features | MEDIUM-HIGH | Core feature set is well-grounded in competitor analysis and community sources. EP-133 SysEx protocol specifics (backup format, project enumeration) are inferred from the existing web app behavior rather than an official spec — this is the primary gap. |
| Architecture | HIGH | Grounded in existing Android codebase (five working screens); patterns verified against official Android and Apple documentation. iOS structure mirrors Android with high confidence; KMP exclusion is well-reasoned. |
| Pitfalls | HIGH (threading, storage) / MEDIUM (SysEx protocol) | Threading and platform storage pitfalls are grounded in the existing codebase (CONCERNS.md cross-references) and official documentation. EP-133 SysEx fragmentation behavior on large transfers is inferred from the protocol design and general USB MIDI behavior — not verified with actual EP-133 hardware transfers. |

**Overall confidence:** HIGH for architecture and platform patterns; MEDIUM for EP-133 SysEx protocol specifics (the critical unknown).

### Gaps to Address

- **EP-133 SysEx protocol map:** The backup format, device stat query messages, and project enumeration protocol are not publicly documented. Before Phase 2, reverse-engineer these from `data/index.js` (the working web app). Document findings as a protocol reference in `.planning/` before implementation begins. This gap affects both Phase 2 and Phase 4 scope estimates.
- **MIDIKit vs. existing MIDIManager.swift decision:** STACK.md recommends MIDIKit; ARCHITECTURE.md notes the existing `MIDIManager.swift` may already provide sufficient coverage. This should be decided at Phase 3 kickoff by auditing what `MIDIManager.swift` does versus what the domain layer needs — adding an SPM dependency only if the existing code has meaningful gaps.
- **Xcode 16 requirement:** MIDIKit 0.11.0 requires Xcode 16; CLAUDE.md documents Xcode 15+ as the requirement. CLAUDE.md must be updated before Phase 3 begins to avoid CI build failures.
- **iOS 16 vs. iOS 17 `@Observable` fallback:** The minimum iOS deployment target is 16; `@Observable` requires iOS 17. All iOS ViewModels need conditional compilation or a `ObservableObject` fallback strategy. The dual-path approach adds boilerplate; raising the minimum target to iOS 17 would simplify significantly and is worth evaluating before Phase 3.

## Sources

### Primary (HIGH confidence)
- Android Jetpack official docs — Compose BOM, Navigation 2.8 typed routes, lifecycle-runtime-compose, Hilt + KSP, SAF patterns
- Apple Developer Documentation — CoreMIDI, `@Observable` macro migration, SwiftUI fileExporter/fileImporter, security-scoped resources
- MIDIKit GitHub releases (orchetect/MIDIKit) — version 0.11.0 changelog and Xcode 16 requirement
- Android MIDI architecture (source.android.com) — SysEx transport, USB MIDI multiplexing, AMidi vs java API decision
- Existing codebase — `MIDIManager.kt`, `MIDIPort.kt`, `MIDIRepository.kt`, `SequencerEngine.kt`, `MIDIManager.swift`, `MIDIBridge.swift`, `.planning/codebase/CONCERNS.md`

### Secondary (MEDIUM confidence)
- OP Forums EP-133 community wish list — feature prioritization and user pain points
- Cornerman for K.O. II App Store listing — competitor feature set
- Modern CoreMIDI event handling blog (furnacecreek.org) — `MIDIInputPortCreateWithProtocol` patterns
- iOS 17 file sharing pitfall (juniperphoton.substack.com) — `Transferable` protocol and temporary directory workaround
- Android MIDI + USB permission race discussion — community-documented timing behavior

### Tertiary (LOW confidence)
- EP-133 SysEx backup protocol — inferred from `data/index.js` behavior and community forum observations; no official spec exists
- iOS 18 MIDI 2.0 (`MIDIUMPMutableEndpoint`) — noted as future consideration; not relevant to current milestone

---
*Research completed: 2026-03-27*
*Ready for roadmap: yes*
