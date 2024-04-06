import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        loadKoinSwiftModules(
            nsdService: IOSNSDService()
        )
    }
    
    
	var body: some Scene {
		WindowGroup {
			ContentView()
		}
	}
}
