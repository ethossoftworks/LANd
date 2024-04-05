import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        loadKoinSwiftModules()
    }
    
    
	var body: some Scene {
		WindowGroup {
			ContentView()
		}
	}
}
