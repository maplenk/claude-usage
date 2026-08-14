import XCTest
@testable import OpenUsageCore

final class ISO8601ParsingTests: XCTestCase {

    func testParsesPlainInstant() async throws {
        let date = ISO8601.parse("2026-08-14T10:30:00Z")
        XCTAssertNotNil(date)
        // One hour later must be exactly 3600 seconds apart.
        let later = ISO8601.parse("2026-08-14T11:30:00Z")
        XCTAssertEqual(later!.timeIntervalSince(date!), 3_600)
        XCTAssertEqual(ISO8601.string(from: date!), "2026-08-14T10:30:00Z")
    }

    func testParsesMillisecondPrecision() async throws {
        XCTAssertNotNil(ISO8601.parse("2026-08-14T10:30:00.123Z"))
    }

    func testParsesMicrosecondPrecisionWithOffset() async throws {
        // Exactly what the Grok billing endpoint returns.
        let date = ISO8601.parse("2026-08-08T04:01:09.238389+00:00")
        XCTAssertNotNil(date)
        XCTAssertEqual(date, ISO8601.parse("2026-08-08T04:01:09.238Z"))
    }

    func testParsesNonZuluOffset() async throws {
        let plus = ISO8601.parse("2026-08-08T06:00:00+02:00")
        let zulu = ISO8601.parse("2026-08-08T04:00:00Z")
        XCTAssertEqual(plus, zulu)
    }

    func testReturnsNilForGarbage() async throws {
        XCTAssertNil(ISO8601.parse("not a date"))
        XCTAssertNil(ISO8601.parse(""))
        XCTAssertNil(ISO8601.parse(nil))
    }
}

final class FormatterTests: XCTestCase {

    func testMinutesFormatting() async throws {
        XCTAssertEqual(Formatters.minutes(0), "0m")
        XCTAssertEqual(Formatters.minutes(-5), "0m")
        XCTAssertEqual(Formatters.minutes(45), "45m")
        XCTAssertEqual(Formatters.minutes(60), "1h 0m")
        XCTAssertEqual(Formatters.minutes(154), "2h 34m")
    }

    func testCountdownFormatting() async throws {
        XCTAssertEqual(Formatters.countdown(secondsRemaining: 0), "Expired")
        XCTAssertEqual(Formatters.countdown(secondsRemaining: 65), "1m 5s")
        XCTAssertEqual(Formatters.countdown(secondsRemaining: 3_672), "1h 1m 12s")
        XCTAssertEqual(Formatters.countdown(secondsRemaining: 90_000), "1d 1h")
    }

    func testResetLabelRelative() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let metric = UsageMetric(utilization: 10, resetsAt: now.addingTimeInterval(2 * 3_600 + 15 * 60))
        XCTAssertEqual(Formatters.resetLabel(for: metric, useRelativeTime: true, now: now), "2h 15m")

        let soon = UsageMetric(utilization: 10, resetsAt: now.addingTimeInterval(9 * 60))
        XCTAssertEqual(Formatters.resetLabel(for: soon, useRelativeTime: true, now: now), "9m")

        let far = UsageMetric(utilization: 10, resetsAt: now.addingTimeInterval(3 * 86_400 + 4 * 3_600))
        XCTAssertEqual(Formatters.resetLabel(for: far, useRelativeTime: true, now: now), "3d 4h")
    }

    func testResetLabelForExpiredAndMissingMetrics() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let expired = UsageMetric(utilization: 10, resetsAt: now.addingTimeInterval(-60))
        XCTAssertEqual(Formatters.resetLabel(for: expired, useRelativeTime: true, now: now), "Awaiting sync")
        XCTAssertEqual(Formatters.resetLabel(for: nil, useRelativeTime: true, now: now), "—")
        let noReset = UsageMetric(utilization: 10, resetsAt: nil)
        XCTAssertEqual(Formatters.resetLabel(for: noReset, useRelativeTime: true, now: now), "—")
    }

    func testRelativeAge() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        XCTAssertEqual(Formatters.relativeAge(nil, now: now), "not yet")
        XCTAssertEqual(Formatters.relativeAge(now.addingTimeInterval(-30), now: now), "just now")
        XCTAssertEqual(Formatters.relativeAge(now.addingTimeInterval(-600), now: now), "10m ago")
        XCTAssertEqual(Formatters.relativeAge(now.addingTimeInterval(-7_200), now: now), "2h ago")
        XCTAssertEqual(Formatters.relativeAge(now.addingTimeInterval(-3 * 86_400), now: now), "3d ago")
    }

    func testAgeText() async throws {
        XCTAssertEqual(Formatters.ageText(minutes: nil), "an unknown time ago")
        XCTAssertEqual(Formatters.ageText(minutes: 0), "just now")
        XCTAssertEqual(Formatters.ageText(minutes: 42), "42m ago")
        XCTAssertEqual(Formatters.ageText(minutes: 130), "2h ago")
        XCTAssertEqual(Formatters.ageText(minutes: 3_000), "2d ago")
    }

    func testMaskKey() async throws {
        XCTAssertEqual(Formatters.maskKey("short"), "****")
        let key = "sk-ant-sid01-abcdefghijklmnop"
        let masked = Formatters.maskKey(key)
        XCTAssertEqual(masked.count, key.count)
        XCTAssertTrue(masked.hasPrefix("sk-ant-"))
        XCTAssertTrue(masked.hasSuffix("mnop"))
        XCTAssertFalse(masked.contains("sid01-abcdefghijkl"))
    }
}

final class UsageMetricTests: XCTestCase {

    func testStatusThresholds() async throws {
        XCTAssertEqual(UsageStatus.fromUtilization(49.9), .safe)
        XCTAssertEqual(UsageStatus.fromUtilization(50), .moderate)
        XCTAssertEqual(UsageStatus.fromUtilization(79.9), .moderate)
        XCTAssertEqual(UsageStatus.fromUtilization(80), .critical)
    }

    func testEffectiveUtilizationDropsToZeroAfterReset() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let metric = UsageMetric(utilization: 88, resetsAt: now.addingTimeInterval(-1))
        XCTAssertEqual(metric.effectiveUtilization(now: now), 0.0)
        XCTAssertEqual(metric.effectiveStatus(now: now), .safe)
        XCTAssertTrue(metric.isExpired(now: now))
    }

    func testMetricWithoutResetNeverExpires() async throws {
        let metric = UsageMetric(utilization: 88, resetsAt: nil)
        XCTAssertFalse(metric.isExpired())
        XCTAssertEqual(metric.effectiveUtilization(), 88)
    }

    func testHeadroomStatusBands() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let future = now.addingTimeInterval(3_600)
        XCTAssertEqual(HeadroomStatus.of(UsageMetric(utilization: 10, resetsAt: future), now: now), .normal)
        XCTAssertEqual(HeadroomStatus.of(UsageMetric(utilization: 50, resetsAt: future), now: now), .elevated)
        XCTAssertEqual(HeadroomStatus.of(UsageMetric(utilization: 75, resetsAt: future), now: now), .high)
        XCTAssertEqual(HeadroomStatus.of(UsageMetric(utilization: 90, resetsAt: future), now: now), .critical)
        XCTAssertEqual(HeadroomStatus.of(nil, now: now), .stale)
        XCTAssertEqual(
            HeadroomStatus.of(UsageMetric(utilization: 10, resetsAt: now.addingTimeInterval(-1)), now: now),
            .stale
        )
        XCTAssertEqual(
            HeadroomStatus.of(UsageMetric(utilization: 10, resetsAt: future), isStale: true, now: now),
            .stale
        )
    }
}

final class SyncStateTests: XCTestCase {

    func testOfflineWins() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let state = SyncState.make(fetchedAt: now, isOnline: false, now: now)
        XCTAssertTrue(state.isOffline)
        XCTAssertTrue(state.isStale)
    }

    func testFreshAgeingStaleBands() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        XCTAssertTrue(SyncState.make(fetchedAt: now.addingTimeInterval(-60), isOnline: true, now: now).isFresh)
        if case .ageing = SyncState.make(fetchedAt: now.addingTimeInterval(-6 * 60), isOnline: true, now: now) {
            // expected
        } else {
            XCTFail("6 minutes old should be ageing")
        }
        if case .stale = SyncState.make(fetchedAt: now.addingTimeInterval(-20 * 60), isOnline: true, now: now) {
            // expected
        } else {
            XCTFail("20 minutes old should be stale")
        }
    }

    func testNeverFetchedIsFresh() async throws {
        let state = SyncState.make(fetchedAt: nil, isOnline: true)
        XCTAssertTrue(state.isFresh)
        XCTAssertNil(state.ageMinutes)
    }
}

final class JWTClaimsTests: XCTestCase {

    private func makeToken(_ payload: [String: Any]) -> String {
        let data = try! JSONSerialization.data(withJSONObject: payload)
        let encoded = data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return "header.\(encoded).signature"
    }

    func testExpiringSoonWithinFiveMinutes() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let soon = makeToken(["exp": now.timeIntervalSince1970 + 120])
        let later = makeToken(["exp": now.timeIntervalSince1970 + 3_600])
        XCTAssertTrue(JWTClaims.isExpiringSoon(soon, now: now))
        XCTAssertFalse(JWTClaims.isExpiringSoon(later, now: now))
    }

    func testMalformedTokenIsNotConsideredExpiring() async throws {
        XCTAssertFalse(JWTClaims.isExpiringSoon("garbage"))
        XCTAssertFalse(JWTClaims.isExpiringSoon(""))
    }

    func testAccountIdFromTopLevelClaim() async throws {
        let token = makeToken(["chatgpt_account_id": "acct-123"])
        XCTAssertEqual(JWTClaims.accountId(idToken: token, accessToken: "x.y.z"), "acct-123")
    }

    func testAccountIdFromNestedClaim() async throws {
        let token = makeToken(["https://api.openai.com/auth": ["chatgpt_account_id": "acct-nested"]])
        XCTAssertEqual(JWTClaims.accountId(idToken: nil, accessToken: token), "acct-nested")
    }

    func testAccountIdFallsBackToAccessToken() async throws {
        let access = makeToken(["chatgpt_account_id": "from-access"])
        XCTAssertEqual(JWTClaims.accountId(idToken: "garbage", accessToken: access), "from-access")
    }
}
