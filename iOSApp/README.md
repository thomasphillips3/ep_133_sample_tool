# EP-133 Sample Tool — iOS

Native Swift/SwiftUI app for managing the Teenage Engineering EP-133 K.O. II from iPhone/iPad. Connects via USB (Lightning-to-USB or USB-C).

## Requirements

| Tool | Version |
|------|---------|
| Xcode | 15+ |
| iOS Deployment Target | 16+ |
| Apple Developer Account | Required for device builds (free tier works) |

## Build & Run

1. Open `EP133SampleTool.xcodeproj` in Xcode
2. Select your target device or simulator in the toolbar
3. Set your development team: **Signing & Capabilities** → **Team**
4. Press **⌘R** to build and run

No external package managers (CocoaPods, SPM, Carthage). Uses system frameworks only.

## Connecting to EP-133

1. Connect EP-133 to your iPhone/iPad:
   - **Lightning**: Lightning-to-USB Camera Adapter + USB-A cable
   - **USB-C**: USB-C to USB-A adapter or direct USB-C cable
2. Launch the app — CoreMIDI will detect the device automatically
3. The web app UI loads and MIDI is available immediately

## Architecture

The iOS app embeds the web app (`data/`) in a full-screen `WKWebView`:

```
EP133SampleToolApp
  └── ContentView
        └── EP133WebView (WKWebView)
              ├── Loads data/index.html from app bundle
              ├── Injects MIDIBridgePolyfill.js as WKUserScript
              └── JS ↔ Swift bridge
                    ├── JS → Swift: WKScriptMessageHandler (getMidiDevices, sendMidi)
                    └── Swift → JS: evaluateJavaScript (onMidiReceived, onDevicesChanged)
```

### Key Files

```
iOSApp/EP133SampleTool/
├── App/
│   ├── EP133SampleToolApp.swift   # App entry point
│   └── ContentView.swift          # Root SwiftUI view
├── WebView/
│   └── EP133WebView.swift         # WKWebView setup, polyfill injection, asset loading
├── MIDI/
│   ├── MIDIBridge.swift           # WKScriptMessageHandler — getMidiDevices, sendMidi
│   └── MIDIManager.swift          # CoreMIDI USB device discovery, send/receive
└── Resources/
    └── Info.plist                 # App configuration
```

## Notes

- The web app bundle (`data/`) is included as a folder reference in the Xcode project
- The MIDI polyfill is loaded from `shared/MIDIBridgePolyfill.js` (copied into the bundle)
- All JS evaluation is dispatched to the main thread via `DispatchQueue.main.async`
