import Foundation
import Network
import ComposeApp


class IOSNSDService : INSDService {
    
    func __doInit() async throws {}
           
    func __getLocalIpAddress() async throws -> String {
        return ""
    }
 
    func __observeServiceTypes() async throws -> SkieSwiftFlow<NSDServiceType> {
        return ServiceTypesFlow().unwrap()
    }
    
    func __observeServices(type: String) async throws -> SkieSwiftFlow<NSDServiceEvent> {
        return ServiceFlow(type: type).unwrap()
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

private class ServiceTypesFlow : SwiftFlow<NSDServiceType> {
    override init() {}
    
    override func __produce() async throws {
    }
}

private class ServiceFlow : SwiftFlow<NSDServiceEvent> {
    let browser: NWBrowser
    let type: String
    
    init(type: String) {
        self.type = type
        let formattedType = type.replacingOccurrences(of: "local.", with: "")
        self.browser = NWBrowser(for: NWBrowser.Descriptor.bonjourWithTXTRecord(type: formattedType, domain: nil), using: NWParameters.tcp)
    }
    
    override func __produce() async throws {
        browser.browseResultsChangedHandler = { results, changes in
            results.forEach { result in
                
                let serviceName: String = {
                    switch result.endpoint {
                    case .service(let name, _, _, _):
                        return name
                    default:
                        return ""
                    }
                }()
                
                let props = {
                    switch result.metadata {
                    case .bonjour(let record):
                        var map: Dictionary<String, KotlinByteArray> = [:]
                        record.dictionary.forEach { k, v in
                            guard let data = v.data(using: String.Encoding.utf8) else { return }
                            map[k] = KotlinByteArray(size: Int32(data.count), init: { i in KotlinByte(integerLiteral: Int(data[Data.Index(truncating: i)])) })
                        }
                        return map
                    default:
                        return [:]
                    }
                }()
                
                let resolved = NSDService(
                    type: self.type,
                    name: serviceName,
                    port: 0,
                    iPv4Addresses: [""],
                    iPv6Addresses: [""],
                    props: props
                )
                self.tryEmit(value: NSDServiceEvent.ServiceResolved(service: resolved))
                
                print(result)
            }
        }
        
        browser.start(queue: DispatchQueue.main)
        
        try await __awaitClose {
            self.browser.cancel()
        }
    }
}
