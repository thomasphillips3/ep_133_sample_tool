# Feature Research

**Domain:** Native mobile companion app for hardware music sampler/groovebox (Teenage Engineering EP-133 K.O. II)
**Researched:** 2026-03-27
**Confidence:** MEDIUM-HIGH — EP-133 hardware/community well-documented; mobile companion app pattern derived from comparable products (OP-Z app, Patch Base, Novation Circuit editors, Cornerman for K.O. II)

---

## Context: What Already Exists

The Android app has a working native Compose UI layer. Before categorizing features, it is important to be explicit about what is already built vs. what is missing.

**Already built (Android):**
- Pads screen — 12 touch pads per group (A/B/C/D), MIDI note-on/off via USB, haptic feedback, velocity, landscape/portrait layouts
- Beats screen — 16-step sequencer grid, play/pause/stop, BPM control, EDIT and LIVE (record-from-pads) modes
- Sounds screen — Browse all 999 factory sounds, search by name/number, filter by category, assign a sound to a pad via bottom sheet
- Chords screen — chord builder UI
- Device screen — connection status, storage indicator (hardcoded), MIDI channel selector, scale/root note selectors, action buttons (Backup, Sync Samples, Sample Manager, Restore Factory, Format Device) — all currently open the WebView fallback
- WebView fallback — opens `data/index.html` for backup/restore/sync/format operations

**Already built (iOS):**
- WKWebView wrapper (full web app, not native UI yet)
- CoreMIDI USB MIDI bridge
- Polyfill injection

**Not yet built (the gap this milestone closes):**
- Android: project browser, pattern management, real device storage data (all stats are hardcoded), working backup/restore without WebView fallback
- iOS: any native UI screens (currently WebView-only)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features EP-133 users will assume exist. Missing these = app feels broken or unfinished.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| USB connection status — live, persistent | Every hardware companion app shows connected/disconnected state clearly. Users need to know if their device is actually talking. | LOW | Android DeviceScreen already shows ONLINE/OFFLINE dot, but stats are hardcoded — real device state not wired up yet. |
| Device storage indicator (real data) | Users need to know how much of the 999 sample slots and ~128MB are used before pushing samples. Cornerman for K.O. II shows this. | MEDIUM | Currently hardcoded at 42% / 128 samples. Requires SysEx query to EP-133. |
| Project browser — list all 9 projects | EP-133 holds 9 projects. Users managing content need to see what exists on-device before backup or pattern work. | MEDIUM | No project browser exists in native UI yet. Requires SysEx to enumerate project slots. |
| Full backup to phone storage | The #1 companion app use case across all hardware: back up before experimenting. Cornerman for K.O. II does this. The existing desktop EP Sample Tool does this. | HIGH | Currently falls through to WebView. Native implementation requires SysEx backup protocol matching what the web app does. |
| Restore project/full backup from phone | Paired with backup — only useful if you can get data back. | HIGH | Same as backup: currently WebView fallback only. |
| Sample assignment to pads (factory sounds) | Sounds screen + PadPickerSheet already implement browsing and triggering `loadSoundToPad`. Users expect this to actually change what they hear. | MEDIUM | The MIDI `loadSoundToPad` call exists in the domain layer — needs verification it correctly maps to EP-133 Program Change SysEx. |
| Pattern playback — start/stop synced to device | EP-133 users use the device as the performance instrument; the app triggering transport on the device (or reflecting device transport state) is expected. | MEDIUM | BeatsScreen sequencer runs in-app only today. Device transport sync via MIDI Start/Stop/Continue would close the gap. |
| Robust USB connect/disconnect handling | Users plug/unplug constantly. The app must not crash, must prompt for permissions exactly once, and must reconnect automatically on replug. Poorly handled USB is the #1 negative review trigger for Android music apps. | MEDIUM | MIDIManager handles BroadcastReceiver and DeviceCallback, but permission UX and reconnect flows need hardening. |
| iOS: parity with Android native UI | iOS users will compare feature sets. Launching with WKWebView on iOS while Android has native screens creates a two-tier product. | HIGH | iOS native UI (SwiftUI) for all primary screens is a full build — the largest single lift of this milestone. |
| Error states with recovery guidance | "Something went wrong" with no action is a dead end. Users need to know: is the EP-133 not connected, not recognized, USB permission denied, wrong firmware? | LOW | Currently: no error states shown in native UI, just empty data. |

### Differentiators (Competitive Advantage)

Features that set this app apart from simply using the desktop web app or the official TE sample tool in a browser.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Live pad performance from phone | Play the EP-133 from your phone screen without touching the hardware — especially useful when the device is in a rack, on a desk, or recording. No competing mobile app does this cleanly. PadsScreen already exists but needs polish (velocity curves, multi-touch hold detection). | MEDIUM | Core is already built. Differentiator comes from polish: pressure-to-velocity mapping, stable multi-touch, landscape-optimized layout. |
| In-app sound preview (trigger via MIDI) | Tap a sound in the browser and hear it play on the EP-133 immediately, without assigning to a pad first. Patch Base does this for synths. This is the most-requested workflow improvement in the OP-forums community. | LOW | Sounds are loaded via Program Change. A "preview" tap could send a noteOn to a spare pad on a muted channel. Very little extra code. |
| Pattern-level backup (not just full device) | Desktop EP Sample Tool only does full-device backup (~1-3 minutes). Project-only backup (under a minute) is the primary differentiator cited in this project's own README. Users with 9 projects want to save one project without waiting. | HIGH | Requires understanding EP-133 SysEx protocol at project granularity. This is the flagship feature of the garrettjwilke fork vs. the official tool. |
| Backup library with timestamps and names | "backup_2026-03-27_project3.bin" in a scrollable list with dates is dramatically better than one-file-at-a-time. Every hardware musician loses content; versioned backups are table stakes once backup exists at all. | MEDIUM | iOS Files app and Android SAF both support named files. Just requires a list UI and a naming flow. |
| Share backup files (AirDrop/Share Sheet) | A user who wants to share a project with a friend, or move it to their desktop for the PC sample tool, needs share sheet integration. iOS share sheet and Android ACTION_SEND are native OS primitives. | LOW | Pure platform integration — no protocol work. High value, low cost. |
| Scale lock on pad screen | DeviceScreen already has scale/root selection dropdowns, but they don't constrain which pads are active on the Pads screen. Making pads visually indicate "in scale" or "out of scale" — as Novation Groovebox and iMaschine do — reduces wrong-note errors during performance. | MEDIUM | Requires mapping scale intervals to pad notes and altering pad highlight colors in PadsScreen. |
| Group/bank switching while playing | Switching between pad groups A/B/C/D during playback (like switching between drum tracks on a groovebox) without transport stopping is a performance-focused feature that mobile companion apps for Novation Circuit and OP-Z support. | LOW | FilterChip group selector is already in PadsScreen — just needs verification it doesn't drop MIDI notes in flight. |
| BPM tap tempo | Users setting up a live jam want to tap-lock the sequencer BPM rather than nudging up/down buttons. A large tap button with debounced BPM averaging is standard in every beat app. | LOW | BeatsScreen has +/- BPM buttons. Tap tempo is additive — a long-press or double-tap on the BPM display, averaging last 4 taps. |
| Pattern steps configurable per track (1-16) | Elektron-style polyrhythm support — track A plays 16 steps while track B plays 8. BeatsScreen currently shows a fixed 16-step grid. Not essential for v1 but strongly differentiating for the core audience. | HIGH | Requires SequencerEngine changes and a per-track length UI. Defer to v1.x. |

### Anti-Features (Deliberately NOT Build in v1)

Features that seem reasonable but create cost, complexity, or support burden that outweighs their value at this stage.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| BLE MIDI (wireless) | Users want to control EP-133 without a cable | BLE latency (~10-15ms roundtrip) breaks real-time performance feel; pairing UI complexity; battery drain; already excluded in PROJECT.md | Stay USB-only; the cable is fine for the use case |
| Cloud backup sync | Users want backups "everywhere" | Server infrastructure, auth, storage costs, privacy concerns — all out of scope for a local device management tool. Official PROJECT.md explicitly excludes this. | iOS Files + iCloud Drive handles this automatically for free once backups are saved to the Files app |
| Audio recording / capture on phone | Users may want to record what the EP-133 plays | EP-133 outputs audio over its 3.5mm jack, not USB. Capturing it would require an audio interface. The app is a MIDI/SysEx controller, not a DAW. | Document the limitation; recommend Audioshare or just recording to a DAW on desktop |
| Sample editing (trim, pitch shift, normalize) | Power users want to prep samples before sending them | The desktop EP Sample Tool (WASM-based libsamplerate/libsndfile) already handles this. Rebuilding audio processing on mobile is massive scope. | Launch WebView Sample Manager for sample operations; build native when desktop source is available |
| Pattern import from other devices | Users with Elektron/Roland devices want to import patterns | EP-133 has a proprietary SysEx format. Reverse-engineering cross-device compatibility is months of work. | Out of scope entirely — EP-133 only |
| Multiple device support | Users with two EP-133s | Extremely niche; adds complexity to every connection UI flow, and UsbManager permission must be re-requested per device | Single device focus; revisit if community requests it |
| Social sharing / community patterns | Users want to share EP-133 patches with the community | Requires server, moderation, discovery — a separate product | Share backup files via OS share sheet is sufficient for v1 |
| Real-time parameter knob editing (all params) | Users want a full soft synth editor on their phone like Patch Base | EP-133 has limited real-time MIDI CC parameter support vs. deep synths like Minilogue. The device UI is fast enough for most parameter changes. | Handle the high-value device settings (scale, channel) natively; defer deep parameter editing |

---

## Feature Dependencies

```
[USB Connection & Permission Handling]
    └──required by──> [Pad Performance]
    └──required by──> [Pattern Playback (device sync)]
    └──required by──> [Sound Preview via MIDI]
    └──required by──> [Full Backup to Phone]
    └──required by──> [Project Browser]
    └──required by──> [Sample Assignment to Pads]

[Project Browser]
    └──required by──> [Pattern-Level Backup]
    └──enhances──> [Full Backup to Phone]

[Full Backup to Phone]
    └──required by──> [Restore Backup]
    └──required by──> [Backup Library with Timestamps]
    └──enables──> [Share Backup via Share Sheet]

[iOS Native UI (SwiftUI screens)]
    └──required by──> [iOS pad performance]
    └──required by──> [iOS pattern playback]
    └──required by──> [iOS project browser]

[Sounds Screen + PadPickerSheet] (exists)
    └──enhances──> [Sound Preview via MIDI] (tapping plays before assigning)

[Scale Lock on Pads] ──depends on──> [Scale/Root Selection in DeviceScreen] (exists)

[BPM Tap Tempo] ──enhances──> [Beats/Sequencer Screen] (exists)

[Pattern-Level Backup] ──conflicts with scope of──> [WebView Fallback backup]
    (native implementation would supersede the WebView route)
```

### Dependency Notes

- **USB handling is the root dependency:** Every interactive feature requires a live, stable USB connection. This must be hardened before features built on top of it can be validated.
- **Project browser enables selective backup:** Without knowing what projects exist on device, users can only do full-device backup (the brute-force path). Project enumeration unlocks the differentiating project-level granularity.
- **iOS native UI is a full parallel track:** It doesn't depend on Android completion, but it mirrors all Android features. Phases should plan both platforms in parallel where possible.
- **Sound preview is low-cost, high-reward:** It requires no new infrastructure — just triggering a noteOn on the currently selected sound number before final assignment. Should be bundled with Sounds screen work, not deferred.

---

## MVP Definition

### Launch With (v1 — this milestone)

Minimum viable product for the mobile native UI milestone. The goal is feature parity with the desktop app's core workflows, on mobile, with native UX.

- [ ] **Robust USB connect/disconnect/permission flow** — without this, nothing else is reliable. Must handle: first-time permission dialog, automatic reconnect on replug, clear error states when EP-133 is not found.
- [ ] **Live pad performance** — touch pads trigger EP-133 sounds over USB. Already mostly working; needs polish and validation with real hardware.
- [ ] **Sound assignment from factory library** — browse 999 sounds by category/search, tap to preview on EP-133, assign to pad. SoundsScreen + PadPickerSheet exists; preview and actual SysEx correctness need validation.
- [ ] **16-step beat sequencer** — program patterns, play/stop/BPM control. BeatsScreen exists; needs device transport sync.
- [ ] **Full backup to phone** — save complete EP-133 state to a named file on phone storage. Currently WebView fallback; at minimum the WebView must work correctly end-to-end.
- [ ] **Restore from backup** — load a saved backup file back to the device. Paired with backup.
- [ ] **Real device stats on Device screen** — actual sample count, storage used, firmware version via SysEx query. Currently hardcoded.
- [ ] **iOS native UI — Pads + Beats + Sounds + Device** — SwiftUI equivalents of the Android screens. WKWebView fallback for Backup/Restore is acceptable at launch.

### Add After Validation (v1.x)

Features to add once core native UI is working and hardware compatibility is proven.

- [ ] **Project browser** — enumerate 9 project slots, show names/content; trigger: backup is working and users want selective save
- [ ] **Pattern-level backup** — backup individual projects rather than full device; trigger: user demand, project browser is working
- [ ] **Backup library with timestamps** — named backup list with dates; trigger: users doing multiple backups and losing track
- [ ] **Share backup via Share Sheet** — share .bin backup files via iOS share sheet / Android ACTION_SEND; trigger: user-requested, low cost
- [ ] **Scale lock on pads** — highlight in-scale pads; trigger: performance feedback from launch users
- [ ] **BPM tap tempo** — additive to Beats screen; trigger: performance feedback

### Future Consideration (v2+)

Features to defer until product-market fit is established.

- [ ] **Pattern steps per track (polyrhythm)** — per-track length 1-16; defer until sequencer is well-validated
- [ ] **Chord screen polish + scale-aware chord suggestions** — ChordBuilderScreen exists but needs depth
- [ ] **Pattern chaining / song mode UI** — arrange scenes into a song; the EP-133 has song mode in OS 2.0 but mobile UI for it is complex
- [ ] **Deep parameter editing (CC knobs)** — Patch Base-style parameter editing; defer until EP-133 SysEx protocol is better mapped

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| USB connection handling (robust) | HIGH | MEDIUM | P1 |
| Pad performance (touch → MIDI) | HIGH | LOW (mostly exists) | P1 |
| Sound assignment — factory library | HIGH | LOW (mostly exists) | P1 |
| 16-step beat sequencer | HIGH | LOW (mostly exists) | P1 |
| Full backup to phone | HIGH | HIGH | P1 |
| Restore from backup | HIGH | HIGH | P1 |
| Real device stats | MEDIUM | MEDIUM | P1 |
| iOS native UI (Pads/Beats/Sounds/Device) | HIGH | HIGH | P1 |
| Sound preview via MIDI (before assign) | HIGH | LOW | P1 |
| Error states with recovery guidance | MEDIUM | LOW | P1 |
| Project browser | MEDIUM | MEDIUM | P2 |
| Pattern-level backup | HIGH | HIGH | P2 |
| Backup library + timestamps | MEDIUM | LOW | P2 |
| Share backup via Share Sheet | MEDIUM | LOW | P2 |
| Scale lock on pads | MEDIUM | MEDIUM | P2 |
| BPM tap tempo | LOW | LOW | P2 |
| Pattern steps per track | MEDIUM | HIGH | P3 |
| Song mode / scene arrangement UI | MEDIUM | HIGH | P3 |
| Deep parameter CC editing | LOW | HIGH | P3 |

**Priority key:**
- P1: Must have for this milestone launch
- P2: Should have, add when core is stable
- P3: Future milestone

---

## Competitor Feature Analysis

| Feature | Cornerman for K.O. II (iOS) | TE OP-Z App | Patch Base (iPad) | This App |
|---------|----------------------------|--------------|--------------------|----------|
| Full device backup | YES — USB-C only | N/A | YES (patch banks) | YES (via WebView today, native P1) |
| Project-level backup | Unknown (likely no) | N/A | YES (per bank) | YES (P2 differentiator) |
| Live pad performance | NO | YES (remote screen) | NO | YES (P1, already built) |
| Sound browser / assign | Unknown | Via Configurator | YES (patch lists) | YES (P1, mostly built) |
| Real-time parameter edit | NO | Partial (mixer/FX sliders) | YES (full CC editing) | NO (v2+) |
| Pattern sequencer in-app | NO | YES (remote display) | NO | YES (P1, mostly built) |
| Share project files | YES (organize + share) | Via iTunes/Files | iCloud Drive | YES (P2) |
| Scale lock / musical constraints | NO | NO | NO | YES (P2 differentiator) |
| Android support | NO (USB-C iOS only) | YES | NO (iPad/Mac only) | YES (both platforms) |
| Offline / no account required | YES | YES | YES | YES |

**Key competitive gaps this app can own:**
- The only app with live pad performance AND backup AND Android support
- Project-level (not just full-device) backup is currently unique in this ecosystem
- Scale lock during pad performance has no competition in this device category

---

## Sources

- [Cornerman for K.O. II — App Store listing](https://apps.apple.com/us/app/cornerman-for-k-o-ii/id6499280264) — direct competitor, iOS only, backup/organize focus
- [EP-133 community wish list — OP Forums](https://op-forums.com/t/ep-133-wish-list/25907) — primary user community feature requests
- [EP-133 workflow guide — Teenage Engineering](https://teenage.engineering/guides/ep-133/workflow) — canonical project/group/pattern structure
- [OP-Z App guide — Teenage Engineering](https://teenage.engineering/guides/op-z/app) — TE's own mobile companion app pattern: remote screen, configurator, MIDI setup, file transfer
- [Patch Base — Coffeeshopped](https://coffeeshopped.com/patch-base) — gold standard for hardware synth mobile editors: real-time param editing, patch librarian, iCloud backup
- [NC Editor for Novation Circuit — Deepsounds (2025)](https://www.synthtopia.com/content/2025/01/16/novation-circuit-patch-editor-now-available-for-android-ios-mobile-devices/) — patch manager + generator pattern for similar groovebox category
- [ep_133_sample_tool GitHub README — garrettjwilke](https://github.com/garrettjwilke/ep_133_sample_tool) — this project's desktop app; project-only backup, privacy features, MIDI debugging
- EP-133 K.O. II hardware structure: 9 projects × 4 groups × 99 patterns × 12 pads, 999 sample slots — [confirmed via EP-133 guides](https://teenage.engineering/guides/ep-133/get-started)
- Android USB MIDI latency and permission UX patterns — [Android Developers docs](https://developer.android.com/develop/connectivity/usb/host)

---
*Feature research for: native mobile companion app — hardware music sampler (EP-133 K.O. II)*
*Researched: 2026-03-27*
