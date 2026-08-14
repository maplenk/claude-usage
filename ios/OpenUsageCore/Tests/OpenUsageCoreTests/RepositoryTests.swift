import XCTest
@testable import OpenUsageCore

private func makeDefaults() -> UserDefaults {
    let suite = "openusage.tests.\(UUID().uuidString)"
    let defaults = UserDefaults(suiteName: suite)!
    defaults.removePersistentDomain(forName: suite)
    return defaults
}

private func makeHistoryStore() -> UsageHistoryStore {
    UsageHistoryStore(
        directory: FileManager.default.temporaryDirectory
            .appendingPathComponent("openusage-repo-\(UUID().uuidString)", isDirectory: true)
    )
}

private func jwt(_ payload: [String: Any]) -> String {
    let data = try! JSONSerialization.data(withJSONObject: payload)
    let encoded = data.base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
        .replacingOccurrences(of: "=", with: "")
    return "header.\(encoded).signature"
}

final class ClaudeUsageRepositoryTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    func testFetchUsageSendsCookieAndCachesResult() async throws {
        let transport = StubTransport()
        await transport.enqueue(
            status: 200,
            json: #"{"five_hour":{"utilization":"55","resets_at":"2026-08-14T12:00:00Z"},"seven_day":{"utilization":31}}"#
        )
        let credentials = InMemoryCredentialStore(sessionKey: "sk-ant-sid01-secret", orgId: "org-9")
        let cache = UsageCacheStore(defaults: makeDefaults())
        let history = makeHistoryStore()
        let repository = ClaudeUsageRepository(
            transport: transport,
            credentials: credentials,
            cache: cache,
            history: history
        )

        let usage = try await repository.fetchUsage(urgent: true, now: now)
        XCTAssertEqual(usage.fiveHour?.utilization, 55.0)
        XCTAssertEqual(usage.sevenDay?.utilization, 31.0)
        XCTAssertEqual(cache.claudeUsage?.fiveHour?.utilization, 55.0)
        XCTAssertEqual(history.history(now: now).count, 1)

        let records = await transport.recorded
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records[0].url, "https://claude.ai/api/organizations/org-9/usage")
        XCTAssertEqual(records[0].headers["Cookie"], "sessionKey=sk-ant-sid01-secret")
    }

    func testFetchUsageWithoutCredentialsThrows() async throws {
        let repository = ClaudeUsageRepository(
            transport: StubTransport(),
            credentials: InMemoryCredentialStore(),
            cache: UsageCacheStore(defaults: makeDefaults()),
            history: makeHistoryStore()
        )
        do {
            _ = try await repository.fetchUsage(urgent: true, now: now)
            XCTFail("expected noCredentials")
        } catch let error as UsageError {
            XCTAssertEqual(error, .noCredentials)
        }
    }

    func testUnauthorizedMapsToUsageError() async throws {
        let transport = StubTransport()
        await transport.enqueue(status: 401, json: "{}")
        let repository = ClaudeUsageRepository(
            transport: transport,
            credentials: InMemoryCredentialStore(sessionKey: "k", orgId: "o"),
            cache: UsageCacheStore(defaults: makeDefaults()),
            history: makeHistoryStore()
        )
        do {
            _ = try await repository.fetchUsage(urgent: true, now: now)
            XCTFail("expected unauthorized")
        } catch let error as UsageError {
            XCTAssertEqual(error, .unauthorized)
        }
    }

    func testRateLimitWindowBlocksNonUrgentRefetch() async throws {
        let transport = StubTransport()
        await transport.setFallback(status: 200, json: #"{"five_hour":{"utilization":10}}"#)
        let repository = ClaudeUsageRepository(
            transport: transport,
            credentials: InMemoryCredentialStore(sessionKey: "k", orgId: "o"),
            cache: UsageCacheStore(defaults: makeDefaults()),
            history: makeHistoryStore()
        )
        _ = try await repository.fetchUsage(urgent: false, now: now)
        do {
            _ = try await repository.fetchUsage(urgent: false, now: now.addingTimeInterval(2))
            XCTFail("expected rateLimited")
        } catch let error as UsageError {
            XCTAssertEqual(error, .rateLimited)
        }
        // Six seconds later the window has elapsed.
        _ = try await repository.fetchUsage(urgent: false, now: now.addingTimeInterval(6))
    }

    func testFetchOrganizations() async throws {
        let transport = StubTransport()
        await transport.enqueue(status: 200, json: #"[{"uuid":"a","name":"Alpha"}]"#)
        let repository = ClaudeUsageRepository(
            transport: transport,
            credentials: InMemoryCredentialStore(),
            cache: UsageCacheStore(defaults: makeDefaults()),
            history: makeHistoryStore()
        )
        let orgs = try await repository.validateSessionKey("sk-ant-sid01-abc")
        XCTAssertEqual(orgs.count, 1)
        XCTAssertEqual(orgs[0].name, "Alpha")
        let records = await transport.recorded
        XCTAssertEqual(records[0].url, "https://claude.ai/api/organizations")
        XCTAssertEqual(records[0].headers["Cookie"], "sessionKey=sk-ant-sid01-abc")
    }
}

final class CodexUsageRepositoryTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func liveToken() -> String {
        jwt(["exp": now.timeIntervalSince1970 + 7_200, "chatgpt_account_id": "acct-1"])
    }

    private func weeklyJSON(_ used: Double) -> String {
        """
        {"rate_limit":{"primary_window":{"used_percent":\(used),"limit_window_seconds":604800,"reset_at":1800600000}}}
        """
    }

    func testStartDeviceLoginParsesUserCodeAndInterval() async throws {
        let transport = StubTransport()
        await transport.enqueue(
            status: 200,
            json: #"{"device_auth_id":"dev-1","usercode":"ABCD-EFGH","interval":"7"}"#
        )
        let repository = CodexUsageRepository(
            transport: transport,
            credentials: InMemoryCredentialStore(),
            cache: UsageCacheStore(defaults: makeDefaults()),
            sleeper: ImmediateSleeper()
        )
        let code = try await repository.startDeviceLogin()
        XCTAssertEqual(code.userCode, "ABCD-EFGH")
        XCTAssertEqual(code.deviceAuthId, "dev-1")
        XCTAssertEqual(code.intervalSeconds, 7)
        XCTAssertEqual(code.verificationUrl, "https://auth.openai.com/codex/device")
    }

    func testCompleteDeviceLoginPollsThenExchangesAndFetches() async throws {
        let transport = StubTransport()
        // Two pending polls, then authorization, then token exchange, then usage.
        await transport.enqueue(status: 404, json: "{}")
        await transport.enqueue(status: 403, json: "{}")
        await transport.enqueue(
            status: 200,
            json: #"{"authorization_code":"code-1","code_challenge":"cc","code_verifier":"cv"}"#
        )
        await transport.enqueue(
            status: 200,
            json: #"{"access_token":"\#(liveToken())","refresh_token":"refresh-1","id_token":"\#(liveToken())"}"#
        )
        await transport.enqueue(status: 200, json: weeklyJSON(64))

        let credentials = InMemoryCredentialStore()
        let sleeper = ImmediateSleeper()
        let repository = CodexUsageRepository(
            transport: transport,
            credentials: credentials,
            cache: UsageCacheStore(defaults: makeDefaults()),
            sleeper: sleeper
        )
        let code = CodexDeviceCode(
            verificationUrl: "https://auth.openai.com/codex/device",
            userCode: "ABCD",
            deviceAuthId: "dev-1",
            intervalSeconds: 5
        )
        let usage = try await repository.completeDeviceLogin(code, now: { self.now })

        XCTAssertEqual(usage.weekly.utilization, 64.0)
        XCTAssertEqual(credentials.codexTokens()?.refreshToken, "refresh-1")
        XCTAssertEqual(credentials.codexTokens()?.accountId, "acct-1")
        let sleepRequests = await sleeper.requested
        XCTAssertEqual(sleepRequests, [5.0, 5.0])

        let records = await transport.recorded
        XCTAssertEqual(records[3].url, "https://auth.openai.com/oauth/token")
        XCTAssertEqual(records[3].headers["Content-Type"], "application/x-www-form-urlencoded")
        XCTAssertTrue(records[3].body?.contains("grant_type=authorization_code") == true)
        XCTAssertTrue(records[3].body?.contains("code_verifier=cv") == true)
        XCTAssertEqual(records[4].headers["User-Agent"], "codex-cli")
        XCTAssertEqual(records[4].headers["ChatGPT-Account-Id"], "acct-1")
    }

    func testDeviceLoginTimesOut() async throws {
        let transport = StubTransport()
        await transport.setFallback(status: 404, json: "{}")
        let repository = CodexUsageRepository(
            transport: transport,
            credentials: InMemoryCredentialStore(),
            cache: UsageCacheStore(defaults: makeDefaults()),
            sleeper: ImmediateSleeper()
        )
        let code = CodexDeviceCode(
            verificationUrl: "u",
            userCode: "c",
            deviceAuthId: "d",
            intervalSeconds: 5
        )
        // The clock jumps past the 15-minute budget on the second call.
        let counter = CallCounter()
        do {
            _ = try await repository.completeDeviceLogin(code, now: {
                counter.next() == 0 ? self.now : self.now.addingTimeInterval(16 * 60)
            })
            XCTFail("expected a timeout")
        } catch let error as ProviderError {
            XCTAssertEqual(error.message, "Codex sign-in timed out. Start again to receive a new code.")
        }
    }

    func testUsageRefreshesTokensOnUnauthorizedAndRetries() async throws {
        let transport = StubTransport()
        await transport.enqueue(status: 401, json: "{}")
        await transport.enqueue(
            status: 200,
            json: #"{"access_token":"\#(liveToken())","refresh_token":"refresh-2"}"#
        )
        await transport.enqueue(status: 200, json: weeklyJSON(77))

        let credentials = InMemoryCredentialStore(
            codexTokens: CodexAuthTokens(
                accessToken: liveToken(),
                refreshToken: "refresh-1",
                idToken: nil,
                accountId: "acct-1"
            )
        )
        let repository = CodexUsageRepository(
            transport: transport,
            credentials: credentials,
            cache: UsageCacheStore(defaults: makeDefaults()),
            sleeper: ImmediateSleeper()
        )
        let usage = try await repository.fetchWeekly(urgent: true, now: now)
        XCTAssertEqual(usage.weekly.utilization, 77.0)
        XCTAssertEqual(credentials.codexTokens()?.refreshToken, "refresh-2")
        let callCount = await transport.recorded.count
        XCTAssertEqual(callCount, 3)
    }

    func testExpiringTokenIsRefreshedBeforeTheUsageCall() async throws {
        let expiring = jwt(["exp": now.timeIntervalSince1970 + 60, "chatgpt_account_id": "acct-1"])
        let transport = StubTransport()
        await transport.enqueue(
            status: 200,
            json: #"{"access_token":"\#(liveToken())","refresh_token":"refresh-3"}"#
        )
        await transport.enqueue(status: 200, json: weeklyJSON(12))

        let credentials = InMemoryCredentialStore(
            codexTokens: CodexAuthTokens(
                accessToken: expiring,
                refreshToken: "refresh-1",
                idToken: nil,
                accountId: "acct-1"
            )
        )
        let repository = CodexUsageRepository(
            transport: transport,
            credentials: credentials,
            cache: UsageCacheStore(defaults: makeDefaults()),
            sleeper: ImmediateSleeper()
        )
        _ = try await repository.fetchWeekly(urgent: true, now: now)
        let records = await transport.recorded
        XCTAssertEqual(records.count, 2)
        XCTAssertEqual(records[0].url, "https://auth.openai.com/oauth/token")
        XCTAssertEqual(credentials.codexTokens()?.refreshToken, "refresh-3")
    }

    func testDisconnectClearsTokensAndCache() async throws {
        let cache = UsageCacheStore(defaults: makeDefaults())
        cache.codexUsage = CodexUsage(weekly: UsageMetric(utilization: 5, resetsAt: nil), fetchedAt: now)
        let credentials = InMemoryCredentialStore(
            codexTokens: CodexAuthTokens(accessToken: "a", refreshToken: "b", idToken: nil, accountId: nil)
        )
        let repository = CodexUsageRepository(
            transport: StubTransport(),
            credentials: credentials,
            cache: cache,
            sleeper: ImmediateSleeper()
        )
        let authenticatedBefore = await repository.isAuthenticated
        XCTAssertTrue(authenticatedBefore)
        await repository.disconnect()
        let authenticatedAfter = await repository.isAuthenticated
        XCTAssertFalse(authenticatedAfter)
        XCTAssertNil(cache.codexUsage)
    }
}

final class GrokUsageRepositoryTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func creditsJSON(_ percent: Double) -> String {
        """
        {"config":{"creditUsagePercent":\(percent),"currentPeriod":{"type":"USAGE_PERIOD_TYPE_WEEKLY","start":"2026-08-01T00:00:00Z","end":"2026-08-08T00:00:00Z"}}}
        """
    }

    private func makeRepository(
        transport: StubTransport,
        credentials: InMemoryCredentialStore,
        cache: UsageCacheStore = UsageCacheStore(defaults: makeDefaults())
    ) -> GrokUsageRepository {
        GrokUsageRepository(
            transport: transport,
            credentials: credentials,
            cache: cache,
            clientVersion: "1.2.3",
            sleeper: ImmediateSleeper()
        )
    }

    func testStartDeviceLoginSendsGrokHeaders() async throws {
        let transport = StubTransport()
        await transport.enqueue(
            status: 200,
            json: #"{"device_code":"dc","user_code":"UC-1","verification_uri":"https://x.ai/device","verification_uri_complete":"https://x.ai/device?code=UC-1","expires_in":600,"interval":5}"#
        )
        let repository = makeRepository(transport: transport, credentials: InMemoryCredentialStore())
        let code = try await repository.startDeviceLogin(now: now)

        XCTAssertEqual(code.userCode, "UC-1")
        XCTAssertEqual(code.verificationUrl, "https://x.ai/device")
        XCTAssertEqual(code.verificationUrlComplete, "https://x.ai/device?code=UC-1")
        XCTAssertEqual(code.expiresAt, now.addingTimeInterval(600))

        let records = await transport.recorded
        XCTAssertEqual(records[0].url, "https://auth.x.ai/oauth2/device/code")
        XCTAssertEqual(records[0].headers["x-grok-client-version"], "1.2.3")
        XCTAssertEqual(records[0].headers["x-grok-client-surface"], "ui")
        XCTAssertTrue(records[0].body?.contains("client_id=b1a00492-073a-47ea-816f-4c329264a828") == true)
    }

    func testInsecureVerificationUrlIsRejected() async throws {
        let transport = StubTransport()
        await transport.enqueue(
            status: 200,
            json: #"{"device_code":"dc","user_code":"UC","verification_uri":"http://x.ai/device","expires_in":600}"#
        )
        let repository = makeRepository(transport: transport, credentials: InMemoryCredentialStore())
        do {
            _ = try await repository.startDeviceLogin(now: now)
            XCTFail("expected an unsafe-URL error")
        } catch let error as ProviderError {
            XCTAssertEqual(error.message, "Grok returned an unsafe verification URL.")
        }
    }

    func testCompleteDeviceLoginHandlesPendingAndSlowDown() async throws {
        let transport = StubTransport()
        await transport.enqueue(status: 400, json: #"{"error":"authorization_pending"}"#)
        await transport.enqueue(status: 400, json: #"{"error":"slow_down"}"#)
        await transport.enqueue(
            status: 200,
            json: #"{"access_token":"at","refresh_token":"rt","expires_in":3600}"#
        )
        await transport.enqueue(status: 200, json: creditsJSON(48))

        let credentials = InMemoryCredentialStore()
        let sleeper = ImmediateSleeper()
        let repository = GrokUsageRepository(
            transport: transport,
            credentials: credentials,
            cache: UsageCacheStore(defaults: makeDefaults()),
            clientVersion: "1.2.3",
            sleeper: sleeper
        )
        let code = GrokDeviceCode(
            verificationUrl: "https://x.ai/device",
            verificationUrlComplete: nil,
            userCode: "UC",
            deviceCode: "dc",
            intervalSeconds: 5,
            expiresAt: now.addingTimeInterval(600)
        )
        let usage = try await repository.completeDeviceLogin(code, now: { self.now })

        XCTAssertEqual(usage.weekly.utilization, 48.0)
        XCTAssertEqual(credentials.grokTokens()?.accessToken, "at")
        XCTAssertEqual(credentials.grokTokens()?.expiresAt, now.addingTimeInterval(3_600))
        // 5s, then 5s, then 10s after the slow_down back-off.
        let sleepRequests = await sleeper.requested
        XCTAssertEqual(sleepRequests, [5.0, 5.0, 10.0])
    }

    func testAccessDeniedStopsPolling() async throws {
        let transport = StubTransport()
        await transport.enqueue(status: 400, json: #"{"error":"access_denied"}"#)
        let repository = makeRepository(transport: transport, credentials: InMemoryCredentialStore())
        let code = GrokDeviceCode(
            verificationUrl: "https://x.ai/device",
            verificationUrlComplete: nil,
            userCode: "UC",
            deviceCode: "dc",
            intervalSeconds: 1,
            expiresAt: now.addingTimeInterval(600)
        )
        do {
            _ = try await repository.completeDeviceLogin(code, now: { self.now })
            XCTFail("expected access denied")
        } catch let error as ProviderError {
            XCTAssertEqual(error.message, "Grok authorization was denied.")
        }
    }

    func testCreditsRequestCarriesTokenAuthHeader() async throws {
        let transport = StubTransport()
        await transport.enqueue(status: 200, json: creditsJSON(33))
        let credentials = InMemoryCredentialStore(
            grokTokens: GrokAuthTokens(
                accessToken: "at",
                refreshToken: "rt",
                idToken: nil,
                expiresAt: now.addingTimeInterval(3_600)
            )
        )
        let repository = makeRepository(transport: transport, credentials: credentials)
        let usage = try await repository.fetchWeekly(urgent: true, now: now)

        XCTAssertEqual(usage.weekly.utilization, 33.0)
        let records = await transport.recorded
        XCTAssertEqual(records[0].url, "https://cli-chat-proxy.grok.com/v1/billing?format=credits")
        XCTAssertEqual(records[0].headers["X-XAI-Token-Auth"], "xai-grok-cli")
        XCTAssertEqual(records[0].headers["Authorization"], "Bearer at")
        XCTAssertEqual(records[0].headers["User-Agent"], "OpenUsage iOS")
    }

    func testExpiringTokenTriggersRefreshFirst() async throws {
        let transport = StubTransport()
        await transport.enqueue(
            status: 200,
            json: #"{"access_token":"new-at","refresh_token":"new-rt","expires_in":7200}"#
        )
        await transport.enqueue(status: 200, json: creditsJSON(21))
        let credentials = InMemoryCredentialStore(
            grokTokens: GrokAuthTokens(
                accessToken: "old",
                refreshToken: "rt",
                idToken: nil,
                expiresAt: now.addingTimeInterval(60)
            )
        )
        let repository = makeRepository(transport: transport, credentials: credentials)
        _ = try await repository.fetchWeekly(urgent: true, now: now)

        let records = await transport.recorded
        XCTAssertEqual(records.count, 2)
        XCTAssertEqual(records[0].url, "https://auth.x.ai/oauth2/token")
        XCTAssertTrue(records[0].body?.contains("grant_type=refresh_token") == true)
        XCTAssertEqual(credentials.grokTokens()?.accessToken, "new-at")
        XCTAssertEqual(credentials.grokTokens()?.expiresAt, now.addingTimeInterval(7_200))
    }

    func testThrottleReturnsCachedUsage() async throws {
        let transport = StubTransport()
        await transport.enqueue(status: 200, json: creditsJSON(15))
        let cache = UsageCacheStore(defaults: makeDefaults())
        let credentials = InMemoryCredentialStore(
            grokTokens: GrokAuthTokens(
                accessToken: "at",
                refreshToken: "rt",
                idToken: nil,
                expiresAt: now.addingTimeInterval(7_200)
            )
        )
        let repository = makeRepository(transport: transport, credentials: credentials, cache: cache)
        _ = try await repository.fetchWeekly(urgent: false, now: now)
        let second = try await repository.fetchWeekly(urgent: false, now: now.addingTimeInterval(60))
        XCTAssertEqual(second.weekly.utilization, 15.0)
        let callCount = await transport.recorded.count
        XCTAssertEqual(callCount, 1, "the second call must be served from cache")
    }
}

/// Tiny mutable counter for driving stubbed clocks.
final class CallCounter: @unchecked Sendable {
    private var value = 0
    private let lock = NSLock()

    func next() -> Int {
        lock.lock()
        defer { lock.unlock() }
        let current = value
        value += 1
        return current
    }
}
