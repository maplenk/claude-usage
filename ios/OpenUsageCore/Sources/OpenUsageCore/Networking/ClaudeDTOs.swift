import Foundation

/// Mirrors `data/remote/UsageResponseDto.kt`.
public struct UsageResponseDTO: Decodable, Sendable {
    public let fiveHour: UsageMetricDTO?
    public let sevenDay: UsageMetricDTO?
    public let sevenDayOpus: UsageMetricDTO?
    public let sevenDaySonnet: UsageMetricDTO?

    private enum CodingKeys: String, CodingKey {
        case fiveHour = "five_hour"
        case sevenDay = "seven_day"
        case sevenDayOpus = "seven_day_opus"
        case sevenDaySonnet = "seven_day_sonnet"
    }

    public init(
        fiveHour: UsageMetricDTO?,
        sevenDay: UsageMetricDTO?,
        sevenDayOpus: UsageMetricDTO?,
        sevenDaySonnet: UsageMetricDTO?
    ) {
        self.fiveHour = fiveHour
        self.sevenDay = sevenDay
        self.sevenDayOpus = sevenDayOpus
        self.sevenDaySonnet = sevenDaySonnet
    }
}

public struct UsageMetricDTO: Decodable, Sendable {
    public let utilization: Double
    public let resetsAt: String?

    private enum CodingKeys: String, CodingKey {
        case utilization
        case resetsAt = "resets_at"
    }

    public init(utilization: Double, resetsAt: String?) {
        self.utilization = utilization
        self.resetsAt = resetsAt
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.utilization = (try container.decodeIfPresent(FlexibleDouble.self, forKey: .utilization))?.value ?? 0.0
        self.resetsAt = try container.decodeIfPresent(String.self, forKey: .resetsAt)
    }
}

public struct OrganizationDTO: Decodable, Sendable {
    public let uuid: String
    public let name: String

    public init(uuid: String, name: String) {
        self.uuid = uuid
        self.name = name
    }
}
