import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        IosEntryKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
