import Foundation
import Network
import ComposeApp


class IOSNSDService : INSDService {
    
    func __doInit() async throws {}
           
    func __getLocalIpAddress() async throws -> String {
        return ""
    }
 
    func __observeServiceTypes() async throws -> SkieSwiftFlow<NSDServiceType> {
        
        class _Flow : SwiftFlow<NSDServiceType> {
            override init() {}
            
            override func __produce() async throws {
            }
        }
        
        return SkieSwiftFlow(_Flow().unwrap())
    }
    
    func __observeServices(type: String) async throws -> SkieSwiftFlow<NSDServiceEvent> {
        class _Flow : SwiftFlow<NSDServiceEvent> {
            let browser: NWBrowser
            
            init(type: String) {
                let formattedType = type.replacingOccurrences(of: "local.", with: "")
                self.browser = NWBrowser(for: NWBrowser.Descriptor.bonjour(type: formattedType, domain: nil), using: NWParameters.tcp)
            }
            
            override func __produce() async throws {
                browser.browseResultsChangedHandler = { results, changes in
                    results.forEach { service in
                        let resolved = NSDService(
                            type: "Blah",
                            name: "Blah",
                            port: 123,
                            iPv4Addresses: [""],
                            iPv6Addresses: [""],
                            props: ["": KotlinByteArray(size: 0)]
                        )
                        self.tryEmit(value: NSDServiceEvent.ServiceResolved(service: resolved))
                        
                        print(service)
                    }
                }
                
                browser.start(queue: DispatchQueue.main)
                
                try await __awaitClose {
                    self.browser.cancel()
                }
            }
        }
        return SkieSwiftFlow(_Flow(type: type).unwrap())
    }
    
    func __registerService(
        type: String,
        name: String,
        port: Int32,
        properties: [String : Any]
    ) async throws -> Outcome<KotlinUnit, AnyObject> {
        return SwiftOutcomeOk(value: KotlinUnit()).unwrap()
    }
    
    func __unregisterService(
        type: String,
        name: String,
        port: Int32
    ) async throws -> Outcome<KotlinUnit, AnyObject> {
        return SwiftOutcomeOk(value: KotlinUnit()).unwrap()
    }
    
}
