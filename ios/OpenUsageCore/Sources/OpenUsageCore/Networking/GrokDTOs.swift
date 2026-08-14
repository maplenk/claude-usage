import Foundation

/// Mirrors `data/remote/GrokApiService.kt`.
public enum GrokAPIContract {
    public static let baseURL = "https://auth.x.ai/"
    public static let deviceCodeURL = "https://auth.x.ai/oauth2/device/code"
    public static let tokenURL = "https://auth.x.ai/oauth2/token"
    public static let creditsURL = "https://cli-chat-proxy.grok.com/v1/billing?format=credits"
    public static let clientId = "b1a00492-073a-47ea-816f-4c329264a828"
    public static let tokenAuthHeader = "xai-grok-cli"
    /// Android sends `openusage-android`; the iOS build identifies itself. If
    /// xAI ever rejects the value, changing this single constant reverts it.
    public static let referrer = "openusage-ios"
    public static let deviceGrantType = "urn:ietf:params:oauth:grant-type:device_code"
    public static let weeklyPeriodType = "USAGE_PERIOD_TYPE_WEEKLY"
    public static let scopes = "openid profile email offline_access grok-cli:access api:access"
    public static let userAgent = "OpenUsage iOS"
    public static let clientSurface = "ui"
}

public struct GrokDeviceCodeResponseDTO: Decodable, Sendable {
    public let deviceCode: String
    public let userCode: String
    public let verificationUri: String
    public let verificationUriComplete: String?
    public let expiresIn: Int
    public let interval: Int?

    private enum CodingKeys: String, CodingKey {
        case deviceCode = "device_code"
        case userCode = "user_code"
        case verificationUri = "verification_uri"
        case verificationUriComplete = "verification_uri_complete"
        case expiresIn = "expires_in"
        case interval
    }

    public init(
        deviceCode: String,
        userCode: String,
        verificationUri: String,
        verificationUriComplete: String?,
        expiresIn: Int,
        interval: Int?
    ) {
        self.deviceCode = deviceCode
        self.userCode = userCode
        self.verificationUri = verificationUri
        self.verificationUriComplete = verificationUriComplete
        self.expiresIn = expiresIn
        self.interval = interval
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.deviceCode = try container.decode(String.self, forKey: .deviceCode)
        self.userCode = try container.decode(String.self, forKey: .userCode)
        self.verificationUri = try container.decode(String.self, forKey: .verificationUri)
        self.verificationUriComplete = try container.decodeIfPresent(String.self, forKey: .verificationUriComplete)
        self.expiresIn = (try container.decodeIfPresent(FlexibleInt.self, forKey: .expiresIn))?.value ?? 0
        self.interval = (try container.decodeIfPresent(FlexibleInt.self, forKey: .interval))?.value
    }
}

public struct GrokTokenResponseDTO: Decodable, Sendable {
    public let accessToken: String?
    public let refreshToken: String?
    public let idToken: String?
    public let expiresIn: Int?

    private enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case idToken = "id_token"
        case expiresIn = "expires_in"
    }

    public init(accessToken: String?, refreshToken: String?, idToken: String?, expiresIn: Int?) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.idToken = idToken
        self.expiresIn = expiresIn
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.accessToken = try container.decodeIfPresent(String.self, forKey: .accessToken)
        self.refreshToken = try container.decodeIfPresent(String.self, forKey: .refreshToken)
        self.idToken = try container.decodeIfPresent(String.self, forKey: .idToken)
        self.expiresIn = (try container.decodeIfPresent(FlexibleInt.self, forKey: .expiresIn))?.value
    }
}

public struct GrokCreditsResponseDTO: Decodable, Sendable {
    public let config: GrokCreditsConfigDTO?

    public init(config: GrokCreditsConfigDTO?) {
        self.config = config
    }
}

public struct GrokCreditsConfigDTO: Decodable, Sendable {
    public let creditUsagePercent: Double?
    public let currentPeriod: GrokCurrentPeriodDTO?

    private enum CodingKeys: String, CodingKey {
        case creditUsagePercent
        case currentPeriod
    }

    public init(creditUsagePercent: Double?, currentPeriod: GrokCurrentPeriodDTO?) {
        self.creditUsagePercent = creditUsagePercent
        self.currentPeriod = currentPeriod
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.creditUsagePercent = (try container.decodeIfPresent(FlexibleDouble.self, forKey: .creditUsagePercent))?.value
        self.currentPeriod = try container.decodeIfPresent(GrokCurrentPeriodDTO.self, forKey: .currentPeriod)
    }
}

public struct GrokCurrentPeriodDTO: Decodable, Sendable {
    public let type: String?
    public let start: String?
    public let end: String?

    public init(type: String?, start: String?, end: String?) {
        self.type = type
        self.start = start
        self.end = end
    }
}

struct GrokOAuthErrorDTO: Decodable {
    let error: String?
}
