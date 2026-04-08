# Architecture

## Overview

EP-133 Sample Tool is a **web-app-first monorepo**. A single compiled React application (`data/`) is the source of truth for all sample management logic, SysEx protocol, and audio processing. Each platform target wraps this web app in a native shell and provides MIDI connectivity via a shared JavaScript polyfill.

```
┌─────────────────────────────────────────────────┐
│              Web App  (data/)                    │
│  React UI · SysEx protocol · WASM audio libs    │
└─────────────────────┬───────────────────────────┘
                       │  Web MIDI API
                       │  (native or polyfilled)
         ┌─────────────┴──────────────┐
         │   MIDI Polyfill            │
         │   shared/MIDIBridgePolyfill.js │
         │   Auto-detects host platform   │
         └──┬──────────┬──────────┬───┘
            │          │          │
     ┌──────▼──┐  ┌────▼────┐  ┌─▼──────────┐
     │Electron │  │ Android │  │    iOS      │
     │(Chrome) │  │WebView  │  │  WKWebView  │
     │Native   │  │+Compose │  │  +SwiftUI   │
     │Web MIDI │  │  MIDI   │  │  CoreMIDI   │
     └─────────┘  └─────────┘  └────────────┘
                                       and
                               ┌───────────────┐
                               │  JUCE Plugin  │
                               │WebBrowserComp.│
                               │  JUCE MIDI    │
                               └───────────────┘
```

---

## Layers

### Web App (`data/`)

The core application. **Do not edit `index.js` directly** — it is a compiled ~1.75MB minified bundle.

| File | Purpose |
|------|---------|
| `index.js` | Compiled React app — all UI, SysEx, sample management |
| `index.html` | Entry point (16 lines) |
| `custom.js` | User-editable: color schemes, group names |
| `*.wasm` | libsamplerate, libsndfile, libtag — offline audio processing |
| `*.pak` / `*.hmls` | Factory sound pack (~27MB), bundled offline |

### MIDI Polyfill (`shared/MIDIBridgePolyfill.js`)

A single ES5 IIFE that overrides `navigator.requestMIDIAccess()`. Auto-detects the host:

| Signal | Platform |
|--------|----------|
| `window.__JUCE__` | JUCE plugin |
| `window.EP133Bridge` | Android WebView |
| `window.webkit.messageHandlers.midibridge` | iOS WKWebView |
| _(none)_ | Native browser (Electron) |

Incoming MIDI arrives via `window.__ep133_onMidiIn(portId, [bytes])`. All four platforms push to this callback.

### Shared Data (`shared/`)

JSON definitions loaded at runtime by native apps:

| File | Contents |
|------|---------|
| `ep133-pads.json` | Pad-to-MIDI-note map (4 groups × 12 pads) |
| `ep133-sounds.json` | 999 factory sound definitions with categories |
| `ep133-scales.json` | 11 musical scale definitions |

---

## Platform Wrappers

### Electron (`main.js`, `preload.js`, `renderer.js`)

Thin shell — creates a `BrowserWindow`, loads `data/index.html`, grants MIDI/SysEx permissions. Uses Chrome's native Web MIDI API — no polyfill needed.

### Android (`AndroidApp/`)

Two-layer architecture:

**Native Compose UI** (primary) — 5 screens (Pads, Beats, Sounds, Chords, Device) talk directly to the MIDI layer via `MIDIRepository`. No WebView for primary screens.

**WebView fallback** — `SampleManagerActivity` hosts the web app for backup/restore/sync/format operations via `EP133WebViewSetup` + polyfill injection.

```
MainActivity
  └── NavHost (Compose)
        ├── PadsScreen ──┐
        ├── BeatsScreen  ├── MIDIRepository ── MIDIManager ── android.media.midi
        ├── SoundsScreen ┤
        ├── ChordsScreen ┘
        └── DeviceScreen
              └── "Open Sample Manager" → SampleManagerActivity (WebView)
```

Key domain classes:
- `MIDIRepository` — typed MIDI API (noteOn, noteOff, programChange, loadSoundToPad, SysEx)
- `SequencerEngine` — 16-step sequencer, drift-compensated coroutine loop
- `ChordPlayer` — routes to EP-133 when connected, `LocalSynth` (AudioTrack) when offline
- `BackupManager` — PAK backup/restore via EP-133 SysEx FILE_LIST + FILE_GET + FILE_PUT

### iOS (`iOSApp/`)

Swift/SwiftUI app. Full-screen `WKWebView` with polyfill injected as `WKUserScript`. JS↔Swift bridge via `WKScriptMessageHandler` / `evaluateJavaScript`. CoreMIDI handles USB device discovery and send/receive.

### JUCE Plugin (`JucePlugin/`)

macOS AU/VST3. `WebBrowserComponent` hosts the web app. A ~80-line JS polyfill is injected at load time, routing MIDI through JUCE's `MidiInput`/`MidiOutput` APIs via `window.__JUCE__`.

---

## MIDI Data Flow (All Native Wrappers)

```
1. WebView loads data/index.html from app bundle
2. Polyfill intercepts navigator.requestMIDIAccess()
3. Web app calls polyfill MIDI output methods
4. Polyfill calls native bridge (platform-specific)
5. Native code sends bytes to EP-133 via USB MIDI
6. EP-133 sends response bytes → native receives
7. Native calls window.__ep133_onMidiIn(portId, bytes)
8. Polyfill dispatches MIDIMessageEvent to web app
```

---

## EP-133 SysEx Protocol

Manufacturer ID: `0x00 0x20 0x76` (Teenage Engineering)

Key operations used for backup/restore:
- `FILE_LIST` — enumerate files on device
- `FILE_GET` — read a file (chunked, 7-bit encoded)
- `FILE_PUT` — write a file (chunked, 7-bit encoded)

Backup format: ZIP archive (`.pak`) of WAV samples + JSON metadata. The archive is assembled entirely in-app — no external tooling required.
