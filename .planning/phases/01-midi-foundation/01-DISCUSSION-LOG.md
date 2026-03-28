# Phase 1: MIDI Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-28
**Phase:** 01-MIDI Foundation
**Areas discussed:** iOS MIDI library choice, Connection status UI, Permission & error UX, iOS deployment target

---

## iOS MIDI Library Choice

| Option | Description | Selected |
|--------|-------------|----------|
| Fix existing MIDIManager.swift | Surgical fixes to threading + SysEx buffer; no new dependency; Xcode 15 stays | ✓ |
| Adopt MIDIKit 0.11.0 via SPM | Modern Swift MIDI library; handles SysEx natively; requires Xcode 16 | |

**User's choice:** No preference — Claude's discretion
**Notes:** Fix existing file for Phase 1; MIDIKit evaluation deferred to Phase 3.

---

## iOS Deployment Target

| Option | Description | Selected |
|--------|-------------|----------|
| Stay iOS 16 (ObservableObject) | Backward compatible; more boilerplate | ✓ |
| Bump to iOS 17 (@Observable) | Cleaner ViewModels; breaks iOS 16 users | |

**User's choice:** No preference — Claude's discretion
**Notes:** Stay at iOS 16 for Phase 1. Decision revisited before Phase 3 begins.

---

## Connection Status UI

| Option | Description | Selected |
|--------|-------------|----------|
| Persistent global indicator | Small dot/label in tab bar visible from all screens | ✓ |
| DeviceScreen-only | Connection state only on Device tab | |
| Top banner | Full-width banner on connection change | |

**User's choice:** No preference — Claude's discretion
**Notes:** Non-intrusive persistent indicator; DeviceScreen shows detail; no auto-navigation on change.

---

## Permission & Error UX

| Option | Description | Selected |
|--------|-------------|----------|
| 3-state DeviceScreen (no device / awaiting / denied) | Explicit states with clear recovery actions | ✓ |
| Single generic error state | Simpler but less actionable | |
| Modal overlay | Blocks interaction until resolved | |

**User's choice:** No preference — Claude's discretion
**Notes:** Three states on DeviceScreen; other screens show disabled (not crash) when no device connected.

---

## Claude's Discretion

- Visual style of global connection indicator (color, size, placement)
- Progress indicator component choice (CircularProgressIndicator / ProgressView)
- Exact disabled-state treatment on Pads/Beats/Sounds when no device connected

## Deferred Ideas

- MIDIKit adoption — Phase 3 evaluation
- iOS 17 @Observable migration — Phase 3 planning
- Kotlin 2.0 + Hilt + Navigation 2.8 upgrade — post-Phase 2
- Deprecated Android MIDI API migration — future chore
