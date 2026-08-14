import XCTest
@testable import OpenUsageCore

final class UsageHistoryStoreTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func makeStore() -> (UsageHistoryStore, URL) {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("openusage-tests-\(UUID().uuidString)", isDirectory: true)
        return (UsageHistoryStore(directory: directory), directory)
    }

    func testAppendAndReloadRoundTrips() async throws {
        let (store, directory) = makeStore()
        defer { try? FileManager.default.removeItem(at: directory) }

        let usage = ClaudeUsage(
            fiveHour: UsageMetric(utilization: 42.5, resetsAt: now.addingTimeInterval(3_600)),
            sevenDay: UsageMetric(utilization: 61.0, resetsAt: nil),
            sevenDayOpus: UsageMetric(utilization: 12.0, resetsAt: nil),
            sevenDaySonnet: nil,
            fetchedAt: now
        )
        store.append(usage, now: now)

        let reloaded = UsageHistoryStore(directory: directory).history(now: now)
        XCTAssertEqual(reloaded.count, 1)
        XCTAssertEqual(reloaded[0].fiveHourUtilization, 42.5)
        XCTAssertEqual(reloaded[0].sevenDayUtilization, 61.0)
        XCTAssertEqual(reloaded[0].sevenDayOpusUtilization, 12.0)
        XCTAssertNil(reloaded[0].sevenDaySonnetUtilization)
        XCTAssertEqual(reloaded[0].fiveHourResetsAt, now.addingTimeInterval(3_600))
        XCTAssertEqual(epochMillis(reloaded[0].timestamp), epochMillis(now))
    }

    func testClearRemovesEverything() async throws {
        let (store, directory) = makeStore()
        defer { try? FileManager.default.removeItem(at: directory) }

        store.append(
            ClaudeUsage(fiveHour: UsageMetric(utilization: 5, resetsAt: nil),
                        sevenDay: nil, sevenDayOpus: nil, sevenDaySonnet: nil, fetchedAt: now),
            now: now
        )
        store.clear()
        XCTAssertTrue(store.history(now: now).isEmpty)
        XCTAssertTrue(UsageHistoryStore(directory: directory).history(now: now).isEmpty)
    }

    func testLegacyFiveColumnRowsStillParse() async throws {
        let line = "\(epochMillis(now)),42.5,61.0,12.0,3.0"
        let point = UsageHistoryStore.parseLine(line)
        XCTAssertNotNil(point)
        XCTAssertEqual(point?.fiveHourUtilization, 42.5)
        XCTAssertNil(point?.fiveHourResetsAt)
        XCTAssertEqual(point?.sevenDayUtilization, 61.0)
        XCTAssertEqual(point?.sevenDaySonnetUtilization, 3.0)
    }

    func testMalformedRowsAreSkipped() async throws {
        XCTAssertNil(UsageHistoryStore.parseLine(""))
        XCTAssertNil(UsageHistoryStore.parseLine("abc,1,2,3,4,5"))
        XCTAssertNil(UsageHistoryStore.parseLine("123,1,2"))
    }

    func testEmptyTokensDecodeToNil() async throws {
        let line = "\(epochMillis(now)),,,,,"
        let point = UsageHistoryStore.parseLine(line)
        XCTAssertNotNil(point)
        XCTAssertNil(point?.fiveHourUtilization)
        XCTAssertNil(point?.fiveHourResetsAt)
        XCTAssertNil(point?.sevenDaySonnetUtilization)
    }

    func testRetentionDropsPointsOlderThanThirtyDays() async throws {
        let old = UsageHistoryPoint(
            timestamp: now.addingTimeInterval(-31 * 86_400),
            fiveHourUtilization: 1,
            fiveHourResetsAt: nil,
            sevenDayUtilization: nil,
            sevenDayOpusUtilization: nil,
            sevenDaySonnetUtilization: nil
        )
        let recent = UsageHistoryPoint(
            timestamp: now.addingTimeInterval(-2 * 86_400),
            fiveHourUtilization: 2,
            fiveHourResetsAt: nil,
            sevenDayUtilization: nil,
            sevenDayOpusUtilization: nil,
            sevenDaySonnetUtilization: nil
        )
        let pruned = UsageHistoryStore.pruneAndTrim([old, recent], now: now)
        XCTAssertEqual(pruned.count, 1)
        XCTAssertEqual(pruned[0].fiveHourUtilization, 2)
    }

    func testTrimKeepsTheMostRecentTenThousandPoints() async throws {
        let points = (0..<(UsageHistoryStore.maxPoints + 25)).map { index in
            UsageHistoryPoint(
                timestamp: now.addingTimeInterval(TimeInterval(index)),
                fiveHourUtilization: Double(index),
                fiveHourResetsAt: nil,
                sevenDayUtilization: nil,
                sevenDayOpusUtilization: nil,
                sevenDaySonnetUtilization: nil
            )
        }
        let trimmed = UsageHistoryStore.pruneAndTrim(points, now: now.addingTimeInterval(-86_400))
        XCTAssertEqual(trimmed.count, UsageHistoryStore.maxPoints)
        XCTAssertEqual(trimmed.last?.fiveHourUtilization, Double(UsageHistoryStore.maxPoints + 24))
    }
}
