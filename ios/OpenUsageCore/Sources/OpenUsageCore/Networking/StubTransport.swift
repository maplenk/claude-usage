import Foundation

/// Scripted transport for unit tests and SwiftUI previews. Requests are matched
/// in order against a queue of canned responses; every request is recorded so
/// headers and bodies can be asserted on.
public actor StubTransport: HTTPTransport {
    public struct Recorded: Sendable {
        public let url: String
        public let method: String
        public let headers: [String: String]
        public let body: String?
    }

    public enum Outcome: Sendable {
        case response(HTTPResponse)
        case failure(UsageError)
    }

    private var queue: [Outcome] = []
    private var records: [Recorded] = []
    /// Reused once the queue is exhausted, so polling loops can be driven.
    private var fallback: Outcome?

    public init(fallback: Outcome? = nil) {
        self.fallback = fallback
    }

    public var recorded: [Recorded] { records }

    public func enqueue(status: Int, json: String) {
        queue.append(.response(HTTPResponse(statusCode: status, data: Data(json.utf8))))
    }

    public func enqueue(_ outcome: Outcome) {
        queue.append(outcome)
    }

    public func setFallback(status: Int, json: String) {
        fallback = .response(HTTPResponse(statusCode: status, data: Data(json.utf8)))
    }

    public func send(_ request: URLRequest) async throws -> HTTPResponse {
        records.append(
            Recorded(
                url: request.url?.absoluteString ?? "",
                method: request.httpMethod ?? "GET",
                headers: request.allHTTPHeaderFields ?? [:],
                body: request.httpBody.flatMap { String(data: $0, encoding: .utf8) }
            )
        )
        let outcome = queue.isEmpty ? fallback : queue.removeFirst()
        guard let outcome else { throw UsageError.networkError }
        switch outcome {
        case .response(let response): return response
        case .failure(let error): throw error
        }
    }
}
