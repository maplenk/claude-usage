import Foundation

#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public struct HTTPResponse: Sendable {
    public let statusCode: Int
    public let data: Data

    public init(statusCode: Int, data: Data) {
        self.statusCode = statusCode
        self.data = data
    }

    public var isSuccessful: Bool { (200..<300).contains(statusCode) }
}

/// Seam that replaces OkHttp/Retrofit. The live implementation is
/// `URLSessionTransport`; tests inject a stub so every repository path — device
/// code polling, token refresh, 401 retry — is exercised without a network.
public protocol HTTPTransport: Sendable {
    func send(_ request: URLRequest) async throws -> HTTPResponse
}

public struct URLSessionTransport: HTTPTransport {
    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    public static func makeDefault(timeout: TimeInterval = 30) -> URLSessionTransport {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = timeout
        configuration.timeoutIntervalForResource = timeout * 2
        configuration.httpShouldSetCookies = false
        configuration.httpCookieAcceptPolicy = .never
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSessionTransport(session: URLSession(configuration: configuration))
    }

    public func send(_ request: URLRequest) async throws -> HTTPResponse {
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw UsageError.networkError
            }
            return HTTPResponse(statusCode: http.statusCode, data: data)
        } catch let error as UsageError {
            throw error
        } catch {
            throw UsageError.networkError
        }
    }
}

// MARK: - Request building helpers

public enum HTTPRequestBuilder {
    public static func get(
        url: URL,
        headers: [String: String] = [:]
    ) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        for (key, value) in headers { request.setValue(value, forHTTPHeaderField: key) }
        return request
    }

    public static func postJSON<Body: Encodable>(
        url: URL,
        body: Body,
        headers: [String: String] = [:]
    ) throws -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        for (key, value) in headers { request.setValue(value, forHTTPHeaderField: key) }
        request.httpBody = try JSONEncoder().encode(body)
        return request
    }

    public static func postForm(
        url: URL,
        fields: [(String, String)],
        headers: [String: String] = [:]
    ) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        for (key, value) in headers { request.setValue(value, forHTTPHeaderField: key) }
        request.httpBody = Data(formEncode(fields).utf8)
        return request
    }

    public static func formEncode(_ fields: [(String, String)]) -> String {
        fields
            .map { "\(percentEncode($0.0))=\(percentEncode($0.1))" }
            .joined(separator: "&")
    }

    private static func percentEncode(_ value: String) -> String {
        // application/x-www-form-urlencoded: everything outside the unreserved
        // set is escaped, and spaces become '+'.
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._*")
        let escaped = value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
        return escaped.replacingOccurrences(of: "%20", with: "+")
    }
}

public enum JSONSupport {
    public static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        try JSONDecoder().decode(type, from: data)
    }
}
