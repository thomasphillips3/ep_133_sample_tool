# EP-133 Sample Tool — JUCE Plugin

AU and VST3 plugin for macOS DAWs. Wraps the web app (`data/`) in a JUCE `WebBrowserComponent` and routes MIDI through JUCE's native MIDI APIs.

## Requirements

| Tool | Version |
|------|---------|
| macOS | 11+ (Big Sur) |
| Xcode | 15+ |
| CMake | 3.22+ |
| JUCE | 8.0.4 (fetched automatically via CMake FetchContent) |

## Build

```bash
cd JucePlugin

# Configure (downloads JUCE 8.0.4 on first run)
cmake -B build -DCMAKE_BUILD_TYPE=Release

# Build
cmake --build build --config Release
```

### Output Paths

| Format | Path |
|--------|------|
| AU | `build/EP133SampleTool_artefacts/Release/AU/EP-133 Sample Tool.component` |
| VST3 | `build/EP133SampleTool_artefacts/Release/VST3/EP-133 Sample Tool.vst3` |

To install, copy to the system plugin folder:
```bash
# AU
cp -r build/.../AU/"EP-133 Sample Tool.component" ~/Library/Audio/Plug-Ins/Components/

# VST3
cp -r build/.../VST3/"EP-133 Sample Tool.vst3" ~/Library/Audio/Plug-Ins/VST3/
```

## Architecture

The plugin is a thin JUCE shell around the web app:

```
PluginEditor.cpp
  └── WebBrowserComponent
        ├── ResourceProvider serves data/ from plugin bundle
        ├── Injects ~80-line JS MIDI polyfill at load time
        └── Routes MIDI through window.__JUCE__ ↔ JUCE MidiInput/MidiOutput
PluginProcessor.cpp
  └── Stub AudioProcessor (accepts MIDI, no audio processing)
```

The `data/` directory is copied into the plugin bundle's `Contents/Resources/` as a CMake post-build step (defined in `CMakeLists.txt`).

## Status

`Source/` is currently a placeholder. Plugin source files are on the `copilot/convert-to-juce-plugin` branch.
