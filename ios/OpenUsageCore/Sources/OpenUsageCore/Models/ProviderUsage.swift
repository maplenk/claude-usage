import Foundation

/// Mirrors `domain/model/ClaudeUsage.kt`.
public struct ClaudeUsage: Equatable, Codable, Sendable {
    public let fiveHour: UsageMetric?
    public let sevenDay: UsageMetric?
    public let sevenDayOpus: UsageMetric?
    public let sevenDaySonnet: UsageMetric?
    public let fetchedAt: Date

    public init(
        fiveHour: UsageMetric?,
        sevenDay: UsageMetric?,
        sevenDayOpus: UsageMetric?,
        sevenDaySonnet: UsageMetric?,
        fetchedAt: Date = Date()
    ) {
        self.fiveHour = fiveHour
        self.sevenDay = sevenDay
        self.sevenDayOpus = sevenDayOpus
        self.sevenDaySonnet = sevenDaySonnet
        self.fetchedAt = fetchedAt
    }
}

/// Mirrors `domain/model/CodexUsage.kt`.
public struct CodexUsage: Equatable, Codable, Sendable {
    public let weekly: UsageMetric
    public let fetchedAt: Date

    public init(weekly: UsageMetric, fetchedAt: Date = Date()) {
        self.weekly = weekly
        self.fetchedAt = fetchedAt
    }
}

public struct CodexDeviceCode: Equatable, Sendable {
    public let verificationUrl: String
    public let userCode: String
    public let deviceAuthId: String
    public let intervalSeconds: Int

    public init(verificationUrl: String, userCode: String, deviceAuthId: String, intervalSeconds: Int) {
        self.verificationUrl = verificationUrl
        self.userCode = userCode
        self.deviceAuthId = deviceAuthId
        self.intervalSeconds = intervalSeconds
    }
}

/// Mirrors `domain/model/GrokUsage.kt`.
public struct GrokUsage: Equatable, Codable, Sendable {
    public let weekly: UsageMetric
    public let fetchedAt: Date

    public init(weekly: UsageMetric, fetchedAt: Date = Date()) {
        self.weekly = weekly
        self.fetchedAt = fetchedAt
    }
}

public struct GrokDeviceCode: Equatable, Sendable {
    public let verificationUrl: String
    public let verificationUrlComplete: String?
    public let userCode: String
    public let deviceCode: String
    public let intervalSeconds: Int
    public let expiresAt: Date

    public init(
        verificationUrl: String,
        verificationUrlComplete: String?,
        userCode: String,
        deviceCode: String,
        intervalSeconds: Int,
        expiresAt: Date
    ) {
        self.verificationUrl = verificationUrl
        self.verificationUrlComplete = verificationUrlComplete
        self.userCode = userCode
        self.deviceCode = deviceCode
        self.intervalSeconds = intervalSeconds
        self.expiresAt = expiresAt
    }
}

/// Mirrors `domain/model/Organization.kt`.
public struct Organization: Equatable, Identifiable, Codable, Sendable {
    public let uuid: String
    public let name: String

    public init(uuid: String, name: String) {
        self.uuid = uuid
        self.name = name
    }

    public var id: String { uuid }
}

/// Mirrors `domain/model/UsageHistoryPoint.kt`.
public struct UsageHistoryPoint: Equatable, Sendable {
    public let timestamp: Date
    public let fiveHourUtilization: Double?
    public let fiveHourResetsAt: Date?
    public let sevenDayUtilization: Double?
    public let sevenDayOpusUtilization: Double?
    public let sevenDaySonnetUtilization: Double?

    public init(
        timestamp: Date,
        fiveHourUtilization: Double?,
        fiveHourResetsAt: Date?,
        sevenDayUtilization: Double?,
        sevenDayOpusUtilization: Double?,
        sevenDaySonnetUtilization: Double?
    ) {
        self.timestamp = timestamp
        self.fiveHourUtilization = fiveHourUtilization
        self.fiveHourResetsAt = fiveHourResetsAt
        self.sevenDayUtilization = sevenDayUtilization
        self.sevenDayOpusUtilization = sevenDayOpusUtilization
        self.sevenDaySonnetUtilization = sevenDaySonnetUtilization
    }
}
