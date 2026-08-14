import XCTest
@testable import OpenUsageCore

/// The `utilization` field arrives as Int, Double or String. These mirror the
/// contract that `UtilizationAdapter.kt` enforces on Android.
final class UtilizationDecodingTests: XCTestCase {

    private func decodeUsage(_ json: String) throws -> UsageResponseDTO {
        try JSONSupport.decode(UsageResponseDTO.self, from: Data(json.utf8))
    }

    func testDecodesIntegerUtilization() async throws {
        let dto = try decodeUsage(#"{"five_hour":{"utilization":72,"resets_at":"2026-08-14T10:00:00Z"}}"#)
        XCTAssertEqual(dto.fiveHour?.utilization, 72.0)
    }

    func testDecodesDoubleUtilization() async throws {
        let dto = try decodeUsage(#"{"five_hour":{"utilization":72.5,"resets_at":null}}"#)
        XCTAssertEqual(dto.fiveHour?.utilization, 72.5)
    }

    func testDecodesStringUtilization() async throws {
        let dto = try decodeUsage(#"{"five_hour":{"utilization":"72.5"}}"#)
        XCTAssertEqual(dto.fiveHour?.utilization, 72.5)
    }

    func testDecodesUnparseableStringAsZero() async throws {
        let dto = try decodeUsage(#"{"five_hour":{"utilization":"n/a"}}"#)
        XCTAssertEqual(dto.fiveHour?.utilization, 0.0)
    }

    func testDecodesNullUtilizationAsZero() async throws {
        let dto = try decodeUsage(#"{"five_hour":{"utilization":null}}"#)
        XCTAssertEqual(dto.fiveHour?.utilization, 0.0)
    }

    func testDecodesMissingUtilizationAsZero() async throws {
        let dto = try decodeUsage(#"{"five_hour":{"resets_at":"2026-08-14T10:00:00Z"}}"#)
        XCTAssertEqual(dto.fiveHour?.utilization, 0.0)
    }

    func testDecodesAllFourMetricsWithMixedTypes() async throws {
        let json = """
        {
          "five_hour": {"utilization": 41, "resets_at": "2026-08-14T10:00:00Z"},
          "seven_day": {"utilization": 63.25, "resets_at": "2026-08-18T10:00:00Z"},
          "seven_day_opus": {"utilization": "12", "resets_at": "2026-08-18T10:00:00Z"},
          "seven_day_sonnet": {"utilization": 0, "resets_at": null}
        }
        """
        let usage = try decodeUsage(json).toDomain(now: Date(timeIntervalSince1970: 0))
        XCTAssertEqual(usage.fiveHour?.utilization, 41.0)
        XCTAssertEqual(usage.sevenDay?.utilization, 63.25)
        XCTAssertEqual(usage.sevenDayOpus?.utilization, 12.0)
        XCTAssertEqual(usage.sevenDaySonnet?.utilization, 0.0)
        XCTAssertNil(usage.sevenDaySonnet?.resetsAt)
        XCTAssertNotNil(usage.fiveHour?.resetsAt)
    }

    func testMissingMetricsDecodeToNil() async throws {
        let usage = try decodeUsage(#"{"five_hour":{"utilization":10}}"#).toDomain()
        XCTAssertNotNil(usage.fiveHour)
        XCTAssertNil(usage.sevenDay)
        XCTAssertNil(usage.sevenDayOpus)
        XCTAssertNil(usage.sevenDaySonnet)
    }

    func testOrganizationsDecodeAndMap() async throws {
        let json = #"[{"uuid":"org-1","name":"Personal"},{"uuid":"org-2","name":"Work"}]"#
        let dtos = try JSONSupport.decode([OrganizationDTO].self, from: Data(json.utf8))
        let orgs = dtos.toDomain()
        XCTAssertEqual(orgs.count, 2)
        XCTAssertEqual(orgs[0].uuid, "org-1")
        XCTAssertEqual(orgs[1].name, "Work")
    }

    func testFlexibleIntHandlesNumberAndString() async throws {
        struct Wrapper: Decodable { let interval: FlexibleInt? }
        let asNumber = try JSONSupport.decode(Wrapper.self, from: Data(#"{"interval":7}"#.utf8))
        let asString = try JSONSupport.decode(Wrapper.self, from: Data(#"{"interval":"9"}"#.utf8))
        let asNull = try JSONSupport.decode(Wrapper.self, from: Data(#"{"interval":null}"#.utf8))
        XCTAssertEqual(asNumber.interval?.value, 7)
        XCTAssertEqual(asString.interval?.value, 9)
        XCTAssertNil(asNull.interval?.value)
    }
}
