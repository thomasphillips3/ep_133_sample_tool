# Web App (`data/`)

This directory contains the compiled EP-133 Sample Tool web application. It is shared across all platform targets — Electron, Android, iOS, and the JUCE plugin all load `index.html` from this directory.

## Important

**Do not edit `index.js` directly.** It is a minified ~1.75MB compiled React bundle. The source is not in this repository.

The only file intended for user customization is `custom.js`.

## Run Standalone

No Electron or native wrapper required:

```bash
cd data
python3 -m http.server
```

Visit `http://localhost:8000` in a Chromium-based browser (Chrome or Edge — required for Web MIDI API support).

Keep the server running while using the tool. Stop with `Ctrl+C`.

## Directory Contents

| File / Pattern | Purpose |
|----------------|---------|
| `index.html` | Entry point (16 lines) |
| `index.js` | Compiled React app — all UI and SysEx logic (~1.75MB) |
| `index.css` | Compiled app styles |
| `custom.js` | **Editable** — color schemes, group names |
| `libsamplerate.wasm` | Audio sample rate conversion |
| `libsndfile.wasm` | WAV/audio file read/write |
| `libtag.wasm` | Audio metadata tagging |
| `resample.wasm` | Additional resampling |
| `*.pak` | EP-133 factory sound pack (~27MB) |
| `*.hmls` | EP-133 hardware sounds base (~5.5MB) |
| `*.woff`, `*.otf` | TE custom fonts |
| `favicon.ico` | App icon |
| `bg.png`, `bg_x2.png` | Background images (EP-133 + EP-1320 variants) |

## Customization

Edit `custom.js` to:
- Change UI colors (`EP133_CUSTOM_COLORS`)
- Rename sample groups (`EP133_CUSTOM_GROUP_NAMES`)

See comments in `custom.js` for available options.
