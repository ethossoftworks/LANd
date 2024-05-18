import Foundation
import Network
import ComposeApp
import Combine
import AsyncDNSResolver
import Darwin


class IOSNSDService : INSDService {
    var service: NWListener? = nil
    
    func __doInit() async throws {}
    
    func __getLocalIpAddress() async throws -> String? {
        // Adapted from: https://gist.github.com/camyoh/aa341e4e40afaa40c84813d899369566
        var address : String?
        
        // Get list of all interfaces on the local machine:
        var ifaddr : UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0 else { return nil }
        guard let firstAddr = ifaddr else { return nil }
        
        // For each interface ...
        for ifptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
            let interface = ifptr.pointee
            
            // Check for IPv4 or IPv6 interface:
            let addrFamily = interface.ifa_addr.pointee.sa_family
            if addrFamily == UInt8(AF_INET) || addrFamily == UInt8(AF_INET6) {
                // Check interface name:
                let name = String(cString: interface.ifa_name)
                if  name == "en0" {
                    // Convert interface address to a human readable string:
                    var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                    getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                                &hostname, socklen_t(hostname.count),
                                nil, socklen_t(0), NI_NUMERICHOST)
                    address = String(cString: hostname)
                } else if (name == "pdp_ip0" || name == "pdp_ip1" || name == "pdp_ip2" || name == "pdp_ip3") {
                    // Convert interface address to a human readable string:
                    var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                    getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                                &hostname, socklen_t(hostname.count),
                                nil, socklen_t(1), NI_NUMERICHOST)
                    address = String(cString: hostname)
                }
            }
        }
        freeifaddrs(ifaddr)
        
        return address
    }
 
    // Using this requires requesting of an Entitlement. Generally most applications do not need to scan for all services unless they are
    // a service browser application. Currently, this function will return an empty flow.
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
        return await withCheckedContinuation { continuation in
            let formattedType = type.replacingOccurrences(of: ".local.", with: "")
            let listener: NWListener
            
            do {
                guard let port = NWEndpoint.Port(rawValue: UInt16(port)) else {
                    continuation.resume(returning: SwiftOutcomeError(error: IOSNSDError(type: IOSNSDErrorType.CouldNotCreateService, error: nil)).unwrap())
                    return
                }
                
                listener = try NWListener(using: .tcp, on: port)
                listener.service = .init(name: name, type: formattedType, txtRecord: txtRecordFromProps(props: properties))
                listener.stateUpdateHandler = { newState in
                    switch (newState) {
                    case .failed(let error):
                        continuation.resume(returning: SwiftOutcomeError(error: IOSNSDError(type: .CouldNotCreateService, error: error)).unwrap())
                        listener.stateUpdateHandler = nil
                    case .ready:
                        continuation.resume(returning: SwiftOutcomeOk(value: KotlinUnit()).unwrap())
                        listener.stateUpdateHandler = nil
                    default:
                        break
                    }
                }
                listener.newConnectionHandler = { connection in connection.cancel() }
            } catch {
                continuation.resume(returning: SwiftOutcomeError(error: IOSNSDError(type: .CouldNotCreateService, error: error)).unwrap())
                return
            }
            
            self.service = listener
            listener.start(queue: .global())
        }
    }
    
    func __unregisterService(
        type: String,
        name: String,
        port: Int32
    ) async throws -> Outcome<KotlinUnit, AnyObject> {
        self.service?.cancel()
        self.service = nil
        
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
                case .changed(_, let newResult, _):
                    let serviceName = serviceNameFromResult(result: newResult)
                    self.tryEmit(value: NSDServiceEvent.ServiceAdded(service: NSDServicePartial(type: self.type, name: serviceName)))
                default:
                    return
                }
            }
            
            results.forEach { result in
                let serviceName = serviceNameFromResult(result: result)
                
                Task {
                    guard let (host, port) = try await resolveServiceIPAndPort(service: result) else {
                        return
                    }
                        
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
        
        browser.stateUpdateHandler = { state in
            switch state {
            case .failed(_):
                self.tryEmit(value: NSDServiceEvent.Error(error: NSDServiceError.unknown))
                self.close()
            case .cancelled:
                self.tryEmit(value: NSDServiceEvent.Error(error: NSDServiceError.cancelled))
                self.close()
            case .waiting(let error):
                switch error {
                case .dns(let type):
                    if (type == kDNSServiceErr_PolicyDenied) {
                        self.tryEmit(value: NSDServiceEvent.Error(error: NSDServiceError.noPermission))
                        self.close()
                    }
                default:
                    return
                }
            default:
                return
            }
        }
        
        browser.start(queue: .global())
        
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

private func txtRecordFromProps(props: Dictionary<String, Any>) -> Data {
    var data = Data()
    
    guard let delimeter = "=".data(using: .utf8) else {
        return data
    }
    
    props.forEach { key, value in
        // Key Data
        guard let keyData = key.data(using: .utf8) else {
            return
        }
        
        // Value Data
        let valueData: Data
        
        switch (value) {
        case let stringValue as String:
            guard let stringData = stringValue.data(using: .utf8) else { return }
            valueData = stringData
        case let nsStringValue as NSString:
            guard let nsStringData = nsStringValue.data(using: NSUTF8StringEncoding) else { return }
            valueData = nsStringData
        case let kotlinByteArrayValue as KotlinByteArray:
            valueData = kotlinByteArrayToData(data: kotlinByteArrayValue)
        case let dataData as Data:
            valueData = dataData
        default:
            return
        }
        
        // Create Entry
        let entrySize = {
            if (valueData.count > 0) {
                return keyData.count + delimeter.count + valueData.count
            } else {
                return keyData.count
            }
        }()
        
        if (entrySize > 255) {
            return
        }
        
        data.append(UInt8(entrySize))
        data.append(keyData)
        if (valueData.count > 0) {
            data.append(delimeter)
        }
        data.append(valueData)
    }
    
    return data
}

private func resolveServiceIPAndPort(
    service: NWBrowser.Result
) async throws -> (host: String, port: UInt16)? {
    let ipAddressRegex = try NSRegularExpression(pattern: #"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"#)
    let name, type, domain: String

    switch(service.endpoint) {
    case .service(let innerName, let innerType, let innerDomain, _):
        name = innerName
        type = innerType
        domain = innerDomain
    default:
        return nil
    }
    
    do {
        let resolver = try AsyncDNSResolver()
        let results = try await resolver.querySRV(name: "\(name).\(type).\(domain)")
        
        for record in results {
            if !ipAddressRegex.matches(in: record.host, range: NSRange(record.host.startIndex..., in: record.host)).isEmpty {
                return (record.host, record.port)
            }
            
            guard let ip = try await resolveHostnameToIPv4(hostname: record.host) else {
                continue
            }

            return (ip, record.port)
        }
        
        return nil
    } catch {
        throw error
    }
}

private func resolveHostnameToIPv4(hostname: String) async throws -> String? {
    return await withCheckedContinuation { continuation in
        var hints = addrinfo()
        hints.ai_family = AF_INET // IPv4
        hints.ai_socktype = SOCK_STREAM
        
        var address: UnsafeMutablePointer<addrinfo>?
        let status = getaddrinfo(hostname, nil, &hints, &address)
        guard status == 0, let addr = address else {
            continuation.resume(returning: .none)
            return
        }
        
        defer {
            freeaddrinfo(addr)
        }
        
        guard let resolvedAddr = addr.pointee.ai_addr else {
            continuation.resume(returning: .none)
            return
        }

        var hostnameBuffer = [CChar](repeating: 0, count: Int(NI_MAXHOST))
        let sockaddrPtr = UnsafePointer<sockaddr>(resolvedAddr)
        let nameInfoResult = getnameinfo(
            sockaddrPtr,
            socklen_t(addr.pointee.ai_addrlen),
            &hostnameBuffer,
            socklen_t(hostnameBuffer.count),
            nil,
            0,
            NI_NUMERICHOST
        )
        
        if (nameInfoResult != 0) {
            continuation.resume(returning: .none)
            return
        }
        
        continuation.resume(returning: String(cString: hostnameBuffer))
    }
}


enum IOSNSDErrorType {
    case CouldNotCreateService
}

class IOSNSDError {
    let type: IOSNSDErrorType
    let error: Error?
    
    init(type: IOSNSDErrorType, error: Error?) {
        self.type = type
        self.error = error
    }
}
