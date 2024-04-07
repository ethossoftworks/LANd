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
            changes.forEach { change in
                switch change {
                case .added(let result):
                    let serviceName = serviceNameFromResult(result: result)
                    self.tryEmit(value: NSDServiceEvent.ServiceAdded(service: NSDServicePartial(type: self.type, name: serviceName)))
                case .removed(let result):
                    let serviceName = serviceNameFromResult(result: result)
                    self.tryEmit(value: NSDServiceEvent.ServiceRemoved(service: NSDServicePartial(type: self.type, name: serviceName)))
                case .changed(let oldResult, let newResult, let flags):
                    let serviceName = serviceNameFromResult(result: newResult)
                    self.tryEmit(value: NSDServiceEvent.ServiceAdded(service: NSDServicePartial(type: self.type, name: serviceName)))
                default:
                    return
                }
            }
            
            results.forEach { result in
                let serviceName = serviceNameFromResult(result: result)
                
                resolveServiceHostPort(service: result) { host, port in
                    let resolved = NSDService(
                        type: self.type,
                        name: serviceName,
                        port: Int32(port),
                        iPv4Addresses: [host],
                        iPv6Addresses: [],
                        props: propertiesFromResult(result: result)
                    )
                    
                    self.tryEmit(value: NSDServiceEvent.ServiceResolved(service: resolved))
                }
            }
        }
        
        browser.start(queue: DispatchQueue.main)
        
        try await __awaitClose {
            self.browser.cancel()
        }
    }
}

private func serviceNameFromResult(result: NWBrowser.Result) -> String {
    switch result.endpoint {
    case .service(let name, _, _, _):
        return name
    default:
        return ""
    }
}

private func propertiesFromResult(result: NWBrowser.Result) -> Dictionary<String, KotlinByteArray> {
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
}

private func resolveServiceHostPort(service: NWBrowser.Result, onResolved: @escaping (_ host: String, _ port: UInt16) -> Void)  {
    let connection = NWConnection(to: service.endpoint, using: .tcp)

    connection.stateUpdateHandler = { state in
        switch state {
        case .ready:
            if let innerEndpoint = connection.currentPath?.remoteEndpoint,
               case .hostPort(let host, let port) = innerEndpoint {
                if let ipAddress = ipv4AddressFromHost(from: host) {
                    onResolved(ipAddress, port.rawValue)
                }
                connection.cancel()
            }
        default:
            break
        }
    }
    
    connection.start(queue: .global())
}

private func ipv4AddressFromHost(from host: NWEndpoint.Host) -> String? {
    if case let .ipv4(ipv4Address) = host {
        let hostString = String(describing: host)
        let components = hostString.components(separatedBy: "%")
        if let ipv4AddressString = components.first { return ipv4AddressString }
        return ipv4Address.debugDescription
    } else {
        return nil
    }
}
