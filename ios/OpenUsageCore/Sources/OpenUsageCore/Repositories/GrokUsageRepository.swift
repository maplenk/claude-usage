import Foundation

/// Port of `data/repository/GrokUsageRepositoryImpl.kt`, including the xAI
/// device-code flow with `slow_down` back-off and silent token refresh.
public actor GrokUsageRepository {
    private static let defaultPollIntervalSeconds = 5
    private static let slowDownIncrementSeconds = 5
    private static let defaultTokenLifetimeSeconds: TimeInterval = 60 * 60
    private static let refreshWindowSeconds: TimeInterval = 5 * 60
    private static let minFetchIntervalSeconds: TimeInterval = 5 * 60

    private let transport: HTTPTransport
    private let credentials: CredentialStoring
    private let cache: UsageCacheStore
    private let sleeper: Sleeping
    private let clientVersion: String
    private var lastFetchTime: Date?

    public init(
        transport: HTTPTransport,
        credentials: CredentialStoring,
        cache: UsageCacheStore,
        clientVersion: String,
        sleeper: Sleeping = TaskSleeper()
    ) {
        self.transport = transport
        self.credentials = credentials
        self.cache = cache
        self.clientVersion = clientVersion
        self.sleeper = sleeper
    }

    public var isAuthenticated: Bool { credentials.grokTokens() != nil }

    public func cachedUsage() -> GrokUsage? { cache.grokUsage }

    // MARK: - Device-code sign-in

    public func startDeviceLogin(now: Date = Date()) async throws -> GrokDeviceCode {
        let request = HTTPRequestBuilder.postForm(
            url: URL(string: GrokAPIContract.deviceCodeURL)!,
            fields: [
                ("client_id", GrokAPIContract.clientId),
                ("scope", GrokAPIContract.scopes),
                ("referrer", GrokAPIContract.referrer),
            ],
            headers: [
                "x-grok-client-version": clientVersion,
                "x-grok-client-surface": GrokAPIContract.clientSurface,
            ]
        )
        let response = try await send(request)
        guard response.isSuccessful else {
            throw ProviderError("Grok sign-in could not start (\(response.statusCode)).")
        }
        guard let body = try? JSONSupport.decode(GrokDeviceCodeResponseDTO.self, from: response.data) else {
            throw ProviderError("Grok sign-in returned an empty response.")
        }
        let verificationUrl = body.verificationUriComplete ?? body.verificationUri
        guard verificationUrl.hasPrefix("https://") else {
            throw ProviderError("Grok returned an unsafe verification URL.")
        }
        return GrokDeviceCode(
            verificationUrl: body.verificationUri,
            verificationUrlComplete: body.verificationUriComplete,
            userCode: body.userCode,
            deviceCode: body.deviceCode,
            intervalSeconds: max(body.interval ?? Self.defaultPollIntervalSeconds, 1),
            expiresAt: now.addingTimeInterval(TimeInterval(max(body.expiresIn, 1)))
        )
    }

    public func completeDeviceLogin(
        _ deviceCode: GrokDeviceCode,
        now: @Sendable () -> Date = { Date() }
    ) async throws -> GrokUsage {
        var pollIntervalSeconds = deviceCode.intervalSeconds

        while now() < deviceCode.expiresAt {
            try Task.checkCancellation()
            try await sleeper.sleep(seconds: Double(pollIntervalSeconds))

            let request = HTTPRequestBuilder.postForm(
                url: URL(string: GrokAPIContract.tokenURL)!,
                fields: [
                    ("grant_type", GrokAPIContract.deviceGrantType),
                    ("device_code", deviceCode.deviceCode),
                    ("client_id", GrokAPIContract.clientId),
                ],
                headers: [
                    "x-grok-client-version": clientVersion,
                    "x-grok-client-surface": GrokAPIContract.clientSurface,
                ]
            )
            let response = try await send(request)

            if response.isSuccessful {
                let dto = try? JSONSupport.decode(GrokTokenResponseDTO.self, from: response.data)
                credentials.saveGrokTokens(try Self.requireCompleteTokens(dto, now: now()))
                return try await fetchWeekly(urgent: true, now: now())
            }

            switch Self.oauthError(response.data) {
            case "authorization_pending":
                break
            case "slow_down":
                pollIntervalSeconds += Self.slowDownIncrementSeconds
            case "access_denied":
                throw ProviderError("Grok authorization was denied.")
            case "expired_token":
                throw ProviderError("Grok sign-in code expired. Start again.")
            default:
                throw ProviderError("Grok authorization failed (\(response.statusCode)).")
            }
        }

        throw ProviderError("Grok sign-in code expired. Start again.")
    }

    // MARK: - Usage

    public func fetchWeekly(urgent: Bool = false, now: Date = Date()) async throws -> GrokUsage {
        if !urgent, let lastFetchTime, now.timeIntervalSince(lastFetchTime) < Self.minFetchIntervalSeconds {
            guard let cached = cache.grokUsage else {
                throw ProviderError("Grok usage has not been fetched yet.")
            }
            return cached
        }

        var tokens = try await validTokens(now: now)
        var response = try await sendCreditsRequest(tokens: tokens)

        if response.statusCode == 401 || response.statusCode == 403 {
            tokens = try await refreshTokens(force: true, now: now)
            response = try await sendCreditsRequest(tokens: tokens)
        }
        guard response.isSuccessful else {
            throw ProviderError("Grok usage request failed (\(response.statusCode)).")
        }
        guard let dto = try? JSONSupport.decode(GrokCreditsResponseDTO.self, from: response.data) else {
            throw ProviderError("Grok billing response was empty.")
        }
        let usage = try dto.toGrokWeeklyDomain(fetchedAt: now)
        cache.grokUsage = usage
        lastFetchTime = now
        return usage
    }

    public func disconnect() {
        credentials.clearGrokTokens()
        cache.clearGrok()
        lastFetchTime = nil
    }

    public func clearCachedData() {
        cache.clearGrok()
        lastFetchTime = nil
    }

    // MARK: - Private

    private func sendCreditsRequest(tokens: GrokAuthTokens) async throws -> HTTPResponse {
        let request = HTTPRequestBuilder.get(
            url: URL(string: GrokAPIContract.creditsURL)!,
            headers: [
                "Authorization": "Bearer \(tokens.accessToken)",
                "X-XAI-Token-Auth": GrokAPIContract.tokenAuthHeader,
                "Accept": "application/json",
                "User-Agent": GrokAPIContract.userAgent,
            ]
        )
        return try await send(request)
    }

    private func validTokens(now: Date) async throws -> GrokAuthTokens {
        guard let current = credentials.grokTokens() else {
            throw ProviderError("Connect Grok in Settings.")
        }
        let expiresSoon = current.expiresAt <= now.addingTimeInterval(Self.refreshWindowSeconds)
        if expiresSoon || JWTClaims.isExpiringSoon(current.accessToken, now: now) {
            return try await refreshTokens(force: false, now: now)
        }
        return current
    }

    @discardableResult
    private func refreshTokens(force: Bool, now: Date) async throws -> GrokAuthTokens {
        guard let current = credentials.grokTokens() else {
            throw ProviderError("Connect Grok in Settings.")
        }
        let expiresSoon = current.expiresAt <= now.addingTimeInterval(Self.refreshWindowSeconds)
        if !force, !expiresSoon, !JWTClaims.isExpiringSoon(current.accessToken, now: now) {
            return current
        }

        let request = HTTPRequestBuilder.postForm(
            url: URL(string: GrokAPIContract.tokenURL)!,
            fields: [
                ("grant_type", "refresh_token"),
                ("client_id", GrokAPIContract.clientId),
                ("refresh_token", current.refreshToken),
            ]
        )
        let response = try await send(request)
        guard response.isSuccessful else {
            throw ProviderError("Grok sign-in expired. Disconnect and connect Grok again.")
        }
        guard let refreshed = try? JSONSupport.decode(GrokTokenResponseDTO.self, from: response.data) else {
            throw ProviderError("Grok token refresh returned no data.")
        }
        guard let accessToken = refreshed.accessToken else {
            throw ProviderError("Grok token refresh omitted access token.")
        }
        let lifetime = TimeInterval(refreshed.expiresIn ?? Int(Self.defaultTokenLifetimeSeconds))
        let updated = GrokAuthTokens(
            accessToken: accessToken,
            refreshToken: refreshed.refreshToken ?? current.refreshToken,
            idToken: refreshed.idToken ?? current.idToken,
            expiresAt: now.addingTimeInterval(lifetime)
        )
        credentials.saveGrokTokens(updated)
        return updated
    }

    private static func requireCompleteTokens(_ dto: GrokTokenResponseDTO?, now: Date) throws -> GrokAuthTokens {
        guard let dto else { throw ProviderError("Grok token exchange returned no data.") }
        guard let access = dto.accessToken else { throw ProviderError("Grok access token was missing.") }
        guard let refresh = dto.refreshToken else { throw ProviderError("Grok refresh token was missing.") }
        let lifetime = TimeInterval(dto.expiresIn ?? Int(defaultTokenLifetimeSeconds))
        return GrokAuthTokens(
            accessToken: access,
            refreshToken: refresh,
            idToken: dto.idToken,
            expiresAt: now.addingTimeInterval(lifetime)
        )
    }

    static func oauthError(_ data: Data) -> String? {
        guard let dto = try? JSONSupport.decode(GrokOAuthErrorDTO.self, from: data) else { return nil }
        return dto.error
    }

    private func send(_ request: URLRequest) async throws -> HTTPResponse {
        do {
            return try await transport.send(request)
        } catch let error as ProviderError {
            throw error
        } catch {
            throw ProviderError("Grok request failed. Check your connection.")
        }
    }
}
