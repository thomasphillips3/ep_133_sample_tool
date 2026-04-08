import SwiftUI

@main
struct EP133SampleToolApp: App {

    @StateObject private var midiManager = MIDIManagerObservable()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
                .environmentObject(midiManager)
        }
    }
}
