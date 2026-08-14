import Foundation

/// Injectable delay so device-code polling loops can be unit-tested without
/// waiting in real time.
public protocol Sleeping: Sendable {
    func sleep(seconds: Double) async throws
}

public struct TaskSleeper: Sleeping {
    public init() {}

    public func sleep(seconds: Double) async throws {
        let nanoseconds = UInt64(max(seconds, 0) * 1_000_000_000)
        try await Task.sleep(nanoseconds: nanoseconds)
    }
}

/// Records requested delays and returns immediately.
public actor ImmediateSleeper: Sleeping {
    private var records: [Double] = []

    public init() {}

    public var requested: [Double] { records }

    public func sleep(seconds: Double) async throws {
        records.append(seconds)
    }
}
