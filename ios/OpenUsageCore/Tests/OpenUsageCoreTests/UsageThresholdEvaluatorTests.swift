import XCTest
@testable import OpenUsageCore

/// Direct port of `notification/UsageThresholdEvaluatorTest.kt`.
final class UsageThresholdEvaluatorTests: XCTestCase {

    func testHighestReachedThresholdFor84Is80() async throws {
        XCTAssertEqual(UsageThresholdEvaluator.highestReachedThreshold(currentUtilization: 84.0), 80)
    }

    func testHighestReachedThresholdFor74IsNil() async throws {
        XCTAssertNil(UsageThresholdEvaluator.highestReachedThreshold(currentUtilization: 74.0))
    }

    func test75To88ReturnsHighestCrossed85() async throws {
        let crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization: 75.0,
            currentUtilization: 88.0
        )
        XCTAssertEqual(crossed, 85)
    }

    func test74To91ReturnsHighestCrossed90() async throws {
        let crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization: 74.0,
            currentUtilization: 91.0
        )
        XCTAssertEqual(crossed, 90)
    }

    func test88To89ReturnsNil() async throws {
        let crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization: 88.0,
            currentUtilization: 89.0
        )
        XCTAssertNil(crossed)
    }

    func test89To90Returns90() async throws {
        let crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization: 89.0,
            currentUtilization: 90.0
        )
        XCTAssertEqual(crossed, 90)
    }

    func testNilTo88ReturnsHighestReached85() async throws {
        let crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization: nil,
            currentUtilization: 88.0
        )
        XCTAssertEqual(crossed, 85)
    }

    func testNilTo50ReturnsNil() async throws {
        let crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization: nil,
            currentUtilization: 50.0
        )
        XCTAssertNil(crossed)
    }

    func test99To100Returns100() async throws {
        let crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization: 99.0,
            currentUtilization: 100.0
        )
        XCTAssertEqual(crossed, 100)
    }

    func test100To100ReturnsNil() async throws {
        let crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization: 100.0,
            currentUtilization: 100.0
        )
        XCTAssertNil(crossed)
    }

    func testNilCurrentUtilizationReturnsNil() async throws {
        XCTAssertNil(UsageThresholdEvaluator.highestReachedThreshold(currentUtilization: nil))
        XCTAssertNil(
            UsageThresholdEvaluator.highestCrossedThreshold(
                previousUtilization: 10.0,
                currentUtilization: nil
            )
        )
    }

    func testValuesAboveHundredAreClamped() async throws {
        XCTAssertEqual(UsageThresholdEvaluator.highestReachedThreshold(currentUtilization: 140.0), 100)
    }
}
