import Foundation

/// Mirrors `data/remote/ClaudeApiService.kt`.
public enum ClaudeAPIContract {
    public static let baseURL = "https://claude.ai/"

    public static func formatSessionCookie(_ sessionKey: String) -> String {
        "sessionKey=\(sessionKey)"
    }

    public static func organizationsURL() -> URL {
        URL(string: baseURL + "api/organizations")!
    }

    public static func usageURL(orgId: String) -> URL {
        let encoded = orgId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? orgId
        return URL(string: baseURL + "api/organizations/\(encoded)/usage")!
    }
}

/// Port of `UsageRepositoryImpl` minus the Android-only side effects (widget
/// push, sync log). Notification decisions are made by `NotificationPlanner`,
/// which the app layer calls with the result of `fetchUsage`.
public actor ClaudeUsageRepository {
    /// Minimum interval between fetches to avoid hammering the API.
    private static let minFetchIntervalSeconds: TimeInterval = 5

    private let transport: HTTPTransport
    private let credentials: CredentialStoring
    private let cache: UsageCacheStore
    private let history: UsageHistoryStore
    private var lastFetchTime: Date?

    public init(
        transport: HTTPTransport,
        credentials: CredentialStoring,
        cache: UsageCacheStore,
        history: UsageHistoryStore
    ) {
        self.transport = transport
        self.credentials = credentials
        self.cache = cache
        self.history = history
    }

    public var isConfigured: Bool {
        credentials.sessionKey() != nil && credentials.orgId() != nil
    }

    public func cachedUsage() -> ClaudeUsage? { cache.claudeUsage }

    public func usageHistory(now: Date = Date()) -> [UsageHistoryPoint] { history.history(now: now) }

    /// Returns the freshly fetched usage. Throws a `UsageError`.
    public func fetchUsage(urgent: Bool = false, now: Date = Date()) async throws -> ClaudeUsage {
        if !urgent, let lastFetchTime, now.timeIntervalSince(lastFetchTime) < Self.minFetchIntervalSeconds {
            throw UsageError.rateLimited
        }
        lastFetchTime = now

        guard let sessionKey = credentials.sessionKey() else { throw UsageError.noCredentials }
        guard let orgId = credentials.orgId() else { throw UsageError.noCredentials }

        let request = HTTPRequestBuilder.get(
            url: ClaudeAPIContract.usageURL(orgId: orgId),
            headers: [
                "Cookie": ClaudeAPIContract.formatSessionCookie(sessionKey),
                "Accept": "application/json",
            ]
        )

        let dto: UsageResponseDTO = try await execute(request)
        let usage = dto.toDomain(now: now)
        cache.claudeUsage = usage
        _ = history.append(usage, now: now)
        return usage
    }

    public func fetchOrganizations(sessionKey: String) async throws -> [Organization] {
        let request = HTTPRequestBuilder.get(
            url: ClaudeAPIContract.organizationsURL(),
            headers: [
                "Cookie": ClaudeAPIContract.formatSessionCookie(sessionKey),
                "Accept": "application/json",
            ]
        )
        let dtos: [OrganizationDTO] = try await execute(request)
        return dtos.toDomain()
    }

    public func validateSessionKey(_ sessionKey: String) async throws -> [Organization] {
        try await fetchOrganizations(sessionKey: sessionKey)
    }

    public func clearUsageHistory() { history.clear() }

    public func clearCachedData() {
        cache.clearClaude()
        history.clear()
        lastFetchTime = nil
    }

    // MARK: - Private

    private func execute<T: Decodable>(_ request: URLRequest) async throws -> T {
        let response: HTTPResponse
        do {
            response = try await transport.send(request)
        } catch let error as UsageError {
            throw error
        } catch {
            throw UsageError.networkError
        }

        guard response.isSuccessful else {
            throw UsageError.fromHTTPStatus(response.statusCode)
        }
        guard !response.data.isEmpty else {
            throw UsageError.serverError(code: response.statusCode, message: "Empty response body")
        }
        do {
            return try JSONSupport.decode(T.self, from: response.data)
        } catch {
            throw UsageError.unknown(message: "\(error)")
        }
    }
}
