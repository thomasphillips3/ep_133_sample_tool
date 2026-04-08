import Foundation

/// A device seen by the MIDI bridge (mirrors Kotlin MIDIPort.Device).
struct MIDIDevice {
    let id: String
    let name: String
}

/// Lists of available MIDI inputs and outputs (mirrors Kotlin MIDIPort.Devices).
struct MIDIDeviceList {
    let inputs: [MIDIDevice]
    let outputs: [MIDIDevice]
}

/// Contract for MIDI device access. Mirrors the Kotlin MIDIPort interface in
/// AndroidApp/app/src/main/java/com/ep133/sampletool/midi/MIDIPort.kt.
///
/// MIDIManager conforms to this protocol. Phase 3 ViewModels depend on this
/// type rather than the concrete MIDIManager class.
protocol MIDIPort: AnyObject {

    // MARK: - Callbacks

    /// Called on the main thread when MIDI data arrives from a device.
    /// Parameters: (portId, bytes)
    var onMIDIReceived: ((String, [UInt8]) -> Void)? { get set }

    /// Called on the main thread when the set of available MIDI devices changes.
    var onDevicesChanged: (() -> Void)? { get set }

    // MARK: - Lifecycle

    /// Set up the MIDI client and connect to existing sources.
    func setup()

    /// Tear down the MIDI client and release all resources.
    func close()

    // MARK: - Device Enumeration

    /// Returns the current list of available MIDI inputs and outputs.
    func getUSBDevices() -> MIDIDeviceList

    // MARK: - Send / Receive

    /// Send MIDI bytes to a destination identified by portId.
    func sendMIDI(to portId: String, data: [UInt8])

    /// Begin receiving MIDI from a source identified by portId.
    /// No-op in Phase 1 (MIDIManager auto-connects to all sources in setup()).
    func startListening(portId: String)

    /// Stop receiving MIDI from a source identified by portId.
    /// No-op in Phase 1.
    func stopListening(portId: String)
}
