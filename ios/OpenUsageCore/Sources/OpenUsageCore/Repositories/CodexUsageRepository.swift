import Foundation

/// Port of `data/repository/CodexUsageRepositoryImpl.kt`, including the
/// device-code sign-in flow and silent token refresh.
public actor CodexUsageRepository {
    private static let defaultPollIntervalSeconds = 5
    private static let minPollIntervalSeconds = 1
    private static let deviceLoginTimeoutSeconds: TimeInterval = 15 * 60
    private static let minFetchIntervalSeconds: TimeInterval = 5 * 60

    private let transport: HTTPTransport
    private let credentials: CredentialStoring
    private let cache: UsageCacheStore
    private let sleeper: Sleeping
    private var lastFetchTime: Date?

    public init(
        transport: HTTPTransport,
        credentials: CredentialStoring,
        cache: UsageCacheStore,
        sleeper: Sleeping = TaskSleeper()
    ) {
        self.transport = transport
        self.credentials = credentials
        self.cache = cache
        self.sleeper = sleeper
    }

    public var isAuthenticated: Bool { credentials.codexTokens() != nil }

    public func cachedUsage() -> CodexUsage? { cache.codexUsage }

    // MARK: - Device-code sign-in

    public func startDeviceLogin() async throws -> CodexDeviceCode {
        let url = URL(string: CodexAPIContract.authBaseURL + CodexAPIContract.deviceUserCodePath)!
        let request = try HTTPRequestBuilder.postJSON(
            url: url,
            body: CodexDeviceCodeRequestDTO(clientId: CodexAPIContract.clientId)
        )
        let response = try await send(request)
        guard response.isSuccessful else {
            throw ProviderError("Codex sign-in could not start (\(response.statusCode)).")
        }
        guard let body = try? JSONSupport.decode(CodexDeviceCodeResponseDTO.self, from: response.data) else {
            throw ProviderError("Codex sign-in returned an empty response.")
        }
        let interval = body.interval?.value ?? Self.defaultPollIntervalSeconds
        return CodexDeviceCode(
            verificationUrl: CodexAPIContract.deviceVerificationURL,
            userCode: body.userCode,
            deviceAuthId: body.deviceAuthId,
            intervalSeconds: max(interval, Self.minPollIntervalSeconds)
        )
    }

    public func completeDeviceLogin(
        _ deviceCode: CodexDeviceCode,
        now: @Sendable () -> Date = { Date() }
    ) async throws -> CodexUsage {
        let startedAt = now()
        var authorization: CodexDeviceTokenPollResponseDTO?

        while authorization == nil {
            if now().timeIntervalSince(startedAt) >= Self.deviceLoginTimeoutSeconds {
                throw ProviderError("Codex sign-in timed out. Start again to receive a new code.")
            }
            try Task.checkCancellation()

            let url = URL(string: CodexAPIContract.authBaseURL + CodexAPIContract.deviceTokenPath)!
            let request = try HTTPRequestBuilder.postJSON(
                url: url,
                body: CodexDeviceTokenPollRequestDTO(
                    deviceAuthId: deviceCode.deviceAuthId,
                    userCode: deviceCode.userCode
                )
            )
            let response = try await send(request)

            if response.isSuccessful {
                guard let body = try? JSONSupport.decode(
                    CodexDeviceTokenPollResponseDTO.self,
                    from: response.data
                ) else {
                    throw ProviderError("Codex authorization returned an empty response.")
                }
                authorization = body
            } else if response.statusCode == 403 || response.statusCode == 404 {
                try await sleeper.sleep(seconds: Double(deviceCode.intervalSeconds))
            } else {
                throw ProviderError("Codex authorization failed (\(response.statusCode)).")
            }
        }

        guard let authorized = authorization else {
            throw ProviderError("Codex authorization returned an empty response.")
        }

        let tokenURL = URL(string: CodexAPIContract.authBaseURL + CodexAPIContract.oauthTokenPath)!
        let exchange = HTTPRequestBuilder.postForm(
            url: tokenURL,
            fields: [
                ("grant_type", "authorization_code"),
                ("code", authorized.authorizationCode),
                ("redirect_uri", CodexAPIContract.deviceRedirectURL),
                ("client_id", CodexAPIContract.clientId),
                ("code_verifier", authorized.codeVerifier),
            ]
        )
        let tokenResponse = try await send(exchange)
        guard tokenResponse.isSuccessful else {
            throw ProviderError("Codex token exchange failed (\(tokenResponse.statusCode)).")
        }
        let dto = try? JSONSupport.decode(CodexTokenResponseDTO.self, from: tokenResponse.data)
        credentials.saveCodexTokens(try Self.requireCompleteTokens(dto))

        return try await fetchWeekly(urgent: true)
    }

    // MARK: - Usage

    public func fetchWeekly(urgent: Bool = false, now: Date = Date()) async throws -> CodexUsage {
        if !urgent, let lastFetchTime, now.timeIntervalSince(lastFetchTime) < Self.minFetchIntervalSeconds {
            guard let cached = cache.codexUsage else {
                throw ProviderError("Codex usage has not been fetched yet.")
            }
            return cached
        }

        var tokens = try await validTokens(now: now)
        var response = try await sendUsageRequest(tokens: tokens)

        if response.statusCode == 401 || response.statusCode == 403 {
            tokens = try await refreshTokens(force: true, now: now)
            response = try await sendUsageRequest(tokens: tokens)
        }
        guard response.isSuccessful else {
            throw ProviderError("Codex usage request failed (\(response.statusCode)).")
        }
        guard let dto = try? JSONSupport.decode(CodexUsageResponseDTO.self, from: response.data),
              let usage = dto.toWeeklyDomain(now: now) else {
            throw ProviderError("Codex weekly limit was not present in the response.")
        }
        cache.codexUsage = usage
        lastFetchTime = now
        return usage
    }

    public func disconnect() {
        credentials.clearCodexTokens()
        cache.clearCodex()
        lastFetchTime = nil
    }

    public func clearCachedData() {
        cache.clearCodex()
        lastFetchTime = nil
    }

    // MARK: - Private

    private func sendUsageRequest(tokens: CodexAuthTokens) async throws -> HTTPResponse {
        var headers = [
            "Authorization": "Bearer \(tokens.accessToken)",
            "User-Agent": CodexAPIContract.userAgent,
            "Accept": "application/json",
        ]
        if let accountId = tokens.accountId {
            headers["ChatGPT-Account-Id"] = accountId
        }
        let url = URL(string: CodexAPIContract.usageBaseURL + CodexAPIContract.usagePath)!
        return try await send(HTTPRequestBuilder.get(url: url, headers: headers))
    }

    private func validTokens(now: Date) async throws -> CodexAuthTokens {
        guard let current = credentials.codexTokens() else {
            throw ProviderError("Connect Codex in Settings.")
        }
        if JWTClaims.isExpiringSoon(current.accessToken, now: now) {
            return try await refreshTokens(force: false, now: now)
        }
        return current
    }

    @discardableResult
    private func refreshTokens(force: Bool, now: Date) async throws -> CodexAuthTokens {
        guard let current = credentials.codexTokens() else {
            throw ProviderError("Connect Codex in Settings.")
        }
        if !force, !JWTClaims.isExpiringSoon(current.accessToken, now: now) { return current }

        let url = URL(string: CodexAPIContract.authBaseURL + CodexAPIContract.oauthTokenPath)!
        let request = try HTTPRequestBuilder.postJSON(
            url: url,
            body: CodexRefreshTokenRequestDTO(
                clientId: CodexAPIContract.clientId,
                grantType: "refresh_token",
                refreshToken: current.refreshToken
            )
        )
        let response = try await send(request)
        guard response.isSuccessful else {
            throw ProviderError("Codex sign-in expired. Disconnect and connect Codex again.")
        }
        guard let refreshed = try? JSONSupport.decode(CodexTokenResponseDTO.self, from: response.data) else {
            throw ProviderError("Codex token refresh returned no data.")
        }
        guard let accessToken = refreshed.accessToken else {
            throw ProviderError("Codex token refresh omitted access token.")
        }
        let idToken = refreshed.idToken ?? current.idToken
        let updated = CodexAuthTokens(
            accessToken: accessToken,
            refreshToken: refreshed.refreshToken ?? current.refreshToken,
            idToken: idToken,
            accountId: JWTClaims.accountId(idToken: idToken, accessToken: accessToken) ?? current.accountId
        )
        credentials.saveCodexTokens(updated)
        return updated
    }

    private static func requireCompleteTokens(_ dto: CodexTokenResponseDTO?) throws -> CodexAuthTokens {
        guard let dto else { throw ProviderError("Codex token exchange returned no data.") }
        guard let access = dto.accessToken else { throw ProviderError("Codex access token was missing.") }
        guard let refresh = dto.refreshToken else { throw ProviderError("Codex refresh token was missing.") }
        return CodexAuthTokens(
            accessToken: access,
            refreshToken: refresh,
            idToken: dto.idToken,
            accountId: JWTClaims.accountId(idToken: dto.idToken, accessToken: access)
        )
    }

    private func send(_ request: URLRequest) async throws -> HTTPResponse {
        do {
            return try await transport.send(request)
        } catch let error as ProviderError {
            throw error
        } catch {
            throw ProviderError("Codex request failed. Check your connection.")
        }
    }
}
