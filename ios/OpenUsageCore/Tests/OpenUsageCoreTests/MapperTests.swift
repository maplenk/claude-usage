import XCTest
@testable import OpenUsageCore

/// Ports `CodexUsageMapperTest.kt` and `GrokUsageMapperTest.kt`.
final class MapperTests: XCTestCase {

    private func window(seconds: Int, used: Double, resetAt: Int?) -> CodexRateLimitWindowDTO {
        CodexRateLimitWindowDTO(
            usedPercent: used,
            limitWindowSeconds: seconds,
            resetAtEpochSeconds: resetAt,
            resetAfterSeconds: nil
        )
    }

    func testMapsSevenDayWindowRegardlessOfPosition() async throws {
        let now = ISO8601.parse("2026-08-06T00:00:00Z")!
        let response = CodexUsageResponseDTO(
            rateLimit: CodexRateLimitDTO(
                primaryWindow: window(seconds: 18_000, used: 12.0, resetAt: 100),
                secondaryWindow: window(seconds: 604_800, used: 42.0, resetAt: 1_786_150_800)
            )
        )

        let usage = response.toWeeklyDomain(now: now)
        XCTAssertEqual(usage?.weekly.utilization, 42.0)
        XCTAssertEqual(usage?.weekly.resetsAt, Date(timeIntervalSince1970: 1_786_150_800))
        XCTAssertEqual(usage?.fetchedAt, now)
    }

    func testDoesNotMislabelFiveHourWindowAsWeekly() async throws {
        let response = CodexUsageResponseDTO(
            rateLimit: CodexRateLimitDTO(
                primaryWindow: window(seconds: 18_000, used: 12.0, resetAt: 100),
                secondaryWindow: nil
            )
        )
        XCTAssertNil(response.toWeeklyDomain())
    }

    func testCodexUsesResetAfterSecondsWhenResetAtMissing() async throws {
        let now = Date(timeIntervalSince1970: 1_000_000)
        let response = CodexUsageResponseDTO(
            rateLimit: CodexRateLimitDTO(
                primaryWindow: CodexRateLimitWindowDTO(
                    usedPercent: 55.0,
                    limitWindowSeconds: 604_800,
                    resetAtEpochSeconds: nil,
                    resetAfterSeconds: 3_600
                ),
                secondaryWindow: nil
            )
        )
        let usage = response.toWeeklyDomain(now: now)
        XCTAssertEqual(usage?.weekly.resetsAt, now.addingTimeInterval(3_600))
    }

    func testCodexWindowToleranceAcceptsOneHourDrift() async throws {
        let response = CodexUsageResponseDTO(
            rateLimit: CodexRateLimitDTO(
                primaryWindow: window(seconds: 604_800 - 3_600, used: 10.0, resetAt: 5),
                secondaryWindow: nil
            )
        )
        XCTAssertNotNil(response.toWeeklyDomain())

        let outOfTolerance = CodexUsageResponseDTO(
            rateLimit: CodexRateLimitDTO(
                primaryWindow: window(seconds: 604_800 - 3_601, used: 10.0, resetAt: 5),
                secondaryWindow: nil
            )
        )
        XCTAssertNil(outOfTolerance.toWeeklyDomain())
    }

    func testCodexJSONFixtureDecodesAndMaps() async throws {
        let json = """
        {
          "rate_limit": {
            "primary_window": {"used_percent": 12.5, "limit_window_seconds": 18000, "reset_at": 100},
            "secondary_window": {"used_percent": 88, "limit_window_seconds": 604800, "reset_at": 1786150800}
          }
        }
        """
        let dto = try JSONSupport.decode(CodexUsageResponseDTO.self, from: Data(json.utf8))
        let usage = dto.toWeeklyDomain(now: Date(timeIntervalSince1970: 0))
        XCTAssertEqual(usage?.weekly.utilization, 88.0)
    }

    // MARK: - Grok

    func testMapsCapturedWeeklyCreditsResponse() async throws {
        let json = """
        {
          "config": {
            "creditUsagePercent": 67.5,
            "currentPeriod": {
              "type": "USAGE_PERIOD_TYPE_WEEKLY",
              "start": "2026-08-01T04:01:09.238389+00:00",
              "end": "2026-08-08T04:01:09.238389+00:00"
            },
            "isUnifiedBillingUser": true
          }
        }
        """
        let dto = try JSONSupport.decode(GrokCreditsResponseDTO.self, from: Data(json.utf8))
        let fetchedAt = ISO8601.parse("2026-08-06T00:00:00Z")!
        let usage = try dto.toGrokWeeklyDomain(fetchedAt: fetchedAt)

        XCTAssertEqual(usage.weekly.utilization, 67.5)
        XCTAssertEqual(usage.weekly.resetsAt, ISO8601.parse("2026-08-08T04:01:09.238Z"))
        XCTAssertEqual(usage.fetchedAt, fetchedAt)
    }

    func testMissingPercentMapsToZero() async throws {
        let json = #"{"config":{"currentPeriod":{"type":"USAGE_PERIOD_TYPE_WEEKLY","start":"2026-08-01T00:00:00Z","end":"2026-08-08T00:00:00Z"}}}"#
        let dto = try JSONSupport.decode(GrokCreditsResponseDTO.self, from: Data(json.utf8))
        XCTAssertEqual(try dto.toGrokWeeklyDomain().weekly.utilization, 0.0)
    }

    func testMonthlyLegacyPeriodIsNotMislabeledWeekly() async throws {
        let json = #"{"config":{"creditUsagePercent":20,"currentPeriod":{"type":"USAGE_PERIOD_TYPE_MONTHLY","start":"2026-08-01T00:00:00Z","end":"2026-09-01T00:00:00Z"}}}"#
        let dto = try JSONSupport.decode(GrokCreditsResponseDTO.self, from: Data(json.utf8))
        do {
            _ = try dto.toGrokWeeklyDomain()
            XCTFail("Expected a ProviderError for a monthly billing period")
        } catch let error as ProviderError {
            XCTAssertEqual(error.message, "This Grok account does not have a weekly unified-billing limit yet.")
        }
    }

    func testMissingConfigThrows() async throws {
        let dto = try JSONSupport.decode(GrokCreditsResponseDTO.self, from: Data("{}".utf8))
        do {
            _ = try dto.toGrokWeeklyDomain()
            XCTFail("Expected a ProviderError when config is absent")
        } catch let error as ProviderError {
            XCTAssertEqual(error.message, "Grok billing response changed.")
        }
    }

    func testGrokPercentIsClampedToHundred() async throws {
        let json = #"{"config":{"creditUsagePercent":143.2,"currentPeriod":{"type":"USAGE_PERIOD_TYPE_WEEKLY","start":"2026-08-01T00:00:00Z","end":"2026-08-08T00:00:00Z"}}}"#
        let dto = try JSONSupport.decode(GrokCreditsResponseDTO.self, from: Data(json.utf8))
        XCTAssertEqual(try dto.toGrokWeeklyDomain().weekly.utilization, 100.0)
    }
}
