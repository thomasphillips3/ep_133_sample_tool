# Requirements: EP-133 Sample Tool — Mobile

**Defined:** 2026-03-28
**Core Value:** A connected EP-133 user can do everything on their phone that they can do on their desktop — no laptop required.

## v1 Requirements

### Connection

- [ ] **CONN-01**: User can see live USB connection status (connected/disconnected) persistently across all screens
- [ ] **CONN-02**: App automatically reconnects to EP-133 after cable replug without requiring manual action from the user
- [ ] **CONN-03**: App requests USB permission once and caches it; does not re-prompt on every launch
- [ ] **CONN-04**: User sees an actionable error state when the EP-133 is not found, permission is denied, or firmware is incompatible — with clear recovery guidance

### Performance

- [ ] **PERF-01**: User can trigger EP-133 sounds via touch pads with multi-touch and velocity sensitivity
- [ ] **PERF-02**: User can preview a factory sound on the EP-133 by tapping it in the sounds browser before assigning it to a pad
- [ ] **PERF-03**: User can program a 16-step beat sequence and play it synced to EP-133 hardware transport (start/stop/BPM)
- [ ] **PERF-04**: Pads visually highlight in-scale vs out-of-scale notes based on the selected scale and root note (scale lock)

### Device Management

- [ ] **DEV-01**: User can view real-time device stats (sample count, storage used, firmware version) queried live from the EP-133 via SysEx
- [ ] **DEV-02**: User can configure EP-133 MIDI channel and scale/root note from the Device screen
- [ ] **DEV-03**: User can save a full EP-133 backup to a named file on phone storage
- [ ] **DEV-04**: User can restore the EP-133 from a backup file stored on phone storage

### Projects

- [ ] **PROJ-01**: User can browse all 9 EP-133 project slots with project names and content overview
- [ ] **PROJ-02**: User can backup a single project (not full device) to phone storage as a named file
- [ ] **PROJ-03**: Backup library shows all saved backups as a scrollable list with timestamps and file names
- [ ] **PROJ-04**: User can share any backup file via iOS Share Sheet or Android share intent (AirDrop, Files, etc.)

### iOS Native UI

- [ ] **IOS-01**: iOS app has a native SwiftUI Pads screen that triggers EP-133 sounds via CoreMIDI over USB
- [ ] **IOS-02**: iOS app has a native SwiftUI Beats screen with 16-step sequencer and device transport sync
- [ ] **IOS-03**: iOS app has a native SwiftUI Sounds screen with factory sound browser and pad assignment
- [ ] **IOS-04**: iOS app has a native SwiftUI Device screen with connection status, real device stats, and settings configuration

## v2 Requirements

Deferred to next milestone once core native UI is validated with real hardware.

### Performance

- **PERF-V2-01**: BPM tap tempo — tap to set sequencer BPM (averaging last 4 taps)
- **PERF-V2-02**: Group/bank A/B/C/D switching during live pad performance without dropping notes

### Sequencer

- **SEQ-V2-01**: Per-track step length (1-16) for polyrhythmic patterns
- **SEQ-V2-02**: Song mode / scene arrangement — chain patterns into a song

### Projects

- **PROJ-V2-01**: Chord screen polish with scale-aware chord suggestions
- **PROJ-V2-02**: Deep EP-133 parameter editing (CC knobs for all device params)

## Out of Scope

Explicitly excluded to prevent scope creep. These will not be built in this milestone.

| Feature | Reason |
|---------|--------|
| BLE MIDI | 10-15ms latency breaks real-time performance; USB is sufficient and already implemented |
| Cloud backup sync | Server infrastructure, auth, and storage costs out of scope for a local device management tool; OS iCloud/Google Drive handles this for free |
| Audio recording on phone | EP-133 outputs audio over 3.5mm jack, not USB — capturing requires an audio interface; out of hardware scope |
| Sample editing (trim, pitch, normalize) | WASM-based in desktop app (libsamplerate/libsndfile); rebuilding on mobile is major scope; use WebView Sample Manager |
| Pattern import from other devices | EP-133 uses proprietary SysEx; cross-device compatibility requires months of reverse-engineering |
| Multiple EP-133 device support | Extremely niche; adds significant complexity to every connection flow |
| Social sharing / community pattern library | Requires server, moderation, discovery — a separate product |
| Real-time deep parameter CC editing (all params) | EP-133 has limited CC support vs. dedicated synths; device UI is faster for most params |

## Traceability

Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CONN-01 | — | Pending |
| CONN-02 | — | Pending |
| CONN-03 | — | Pending |
| CONN-04 | — | Pending |
| PERF-01 | — | Pending |
| PERF-02 | — | Pending |
| PERF-03 | — | Pending |
| PERF-04 | — | Pending |
| DEV-01 | — | Pending |
| DEV-02 | — | Pending |
| DEV-03 | — | Pending |
| DEV-04 | — | Pending |
| PROJ-01 | — | Pending |
| PROJ-02 | — | Pending |
| PROJ-03 | — | Pending |
| PROJ-04 | — | Pending |
| IOS-01 | — | Pending |
| IOS-02 | — | Pending |
| IOS-03 | — | Pending |
| IOS-04 | — | Pending |

**Coverage:**
- v1 requirements: 20 total
- Mapped to phases: 0 (roadmap pending)
- Unmapped: 20 ⚠️

---
*Requirements defined: 2026-03-28*
*Last updated: 2026-03-28 after initialization*
