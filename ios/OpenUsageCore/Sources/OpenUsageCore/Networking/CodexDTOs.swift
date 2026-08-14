import Foundation

/// Mirrors `data/remote/CodexApiService.kt`.
public enum CodexAPIContract {
    public static let authBaseURL = "https://auth.openai.com/"
    public static let usageBaseURL = "https://chatgpt.com/backend-api/"
    public static let deviceVerificationURL = "https://auth.openai.com/codex/device"
    public static let deviceRedirectURL = "https://auth.openai.com/deviceauth/callback"
    public static let clientId = "app_EMoamEEZ73f0CkXaXp7hrann"
    public static let weeklyWindowSeconds: Int = 7 * 24 * 60 * 60

    public static let deviceUserCodePath = "api/accounts/deviceauth/usercode"
    public static let deviceTokenPath = "api/accounts/deviceauth/token"
    public static let oauthTokenPath = "oauth/token"
    public static let usagePath = "wham/usage"
    public static let userAgent = "codex-cli"
}

struct CodexDeviceCodeRequestDTO: Encodable {
    let clientId: String

    private enum CodingKeys: String, CodingKey {
        case clientId = "client_id"
    }
}

public struct CodexDeviceCodeResponseDTO: Decodable, Sendable {
    public let deviceAuthId: String
    public let userCode: String
    public let interval: FlexibleInt?

    private enum CodingKeys: String, CodingKey {
        case deviceAuthId = "device_auth_id"
        case userCode = "user_code"
        case usercode
        case interval
    }

    public init(deviceAuthId: String, userCode: String, interval: FlexibleInt?) {
        self.deviceAuthId = deviceAuthId
        self.userCode = userCode
        self.interval = interval
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.deviceAuthId = try container.decode(String.self, forKey: .deviceAuthId)
        // Gson declares `alternate = ["usercode"]` for this field.
        if let userCode = try container.decodeIfPresent(String.self, forKey: .userCode) {
            self.userCode = userCode
        } else {
            self.userCode = try container.decode(String.self, forKey: .usercode)
        }
        self.interval = try container.decodeIfPresent(FlexibleInt.self, forKey: .interval)
    }
}

struct CodexDeviceTokenPollRequestDTO: Encodable {
    let deviceAuthId: String
    let userCode: String

    private enum CodingKeys: String, CodingKey {
        case deviceAuthId = "device_auth_id"
        case userCode = "user_code"
    }
}

public struct CodexDeviceTokenPollResponseDTO: Decodable, Sendable {
    public let authorizationCode: String
    public let codeChallenge: String?
    public let codeVerifier: String

    private enum CodingKeys: String, CodingKey {
        case authorizationCode = "authorization_code"
        case codeChallenge = "code_challenge"
        case codeVerifier = "code_verifier"
    }

    public init(authorizationCode: String, codeChallenge: String?, codeVerifier: String) {
        self.authorizationCode = authorizationCode
        self.codeChallenge = codeChallenge
        self.codeVerifier = codeVerifier
    }
}

public struct CodexTokenResponseDTO: Decodable, Sendable {
    public let idToken: String?
    public let accessToken: String?
    public let refreshToken: String?

    private enum CodingKeys: String, CodingKey {
        case idToken = "id_token"
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
    }

    public init(idToken: String?, accessToken: String?, refreshToken: String?) {
        self.idToken = idToken
        self.accessToken = accessToken
        self.refreshToken = refreshToken
    }
}

struct CodexRefreshTokenRequestDTO: Encodable {
    let clientId: String
    let grantType: String
    let refreshToken: String

    private enum CodingKeys: String, CodingKey {
        case clientId = "client_id"
        case grantType = "grant_type"
        case refreshToken = "refresh_token"
    }
}

public struct CodexUsageResponseDTO: Decodable, Sendable {
    public let rateLimit: CodexRateLimitDTO?

    private enum CodingKeys: String, CodingKey {
        case rateLimit = "rate_limit"
    }

    public init(rateLimit: CodexRateLimitDTO?) {
        self.rateLimit = rateLimit
    }
}

public struct CodexRateLimitDTO: Decodable, Sendable {
    public let primaryWindow: CodexRateLimitWindowDTO?
    public let secondaryWindow: CodexRateLimitWindowDTO?

    private enum CodingKeys: String, CodingKey {
        case primaryWindow = "primary_window"
        case secondaryWindow = "secondary_window"
    }

    public init(primaryWindow: CodexRateLimitWindowDTO?, secondaryWindow: CodexRateLimitWindowDTO?) {
        self.primaryWindow = primaryWindow
        self.secondaryWindow = secondaryWindow
    }
}

public struct CodexRateLimitWindowDTO: Decodable, Sendable {
    public let usedPercent: Double?
    public let limitWindowSeconds: Int?
    public let resetAtEpochSeconds: Int?
    public let resetAfterSeconds: Int?

    private enum CodingKeys: String, CodingKey {
        case usedPercent = "used_percent"
        case limitWindowSeconds = "limit_window_seconds"
        case resetAtEpochSeconds = "reset_at"
        case resetAfterSeconds = "reset_after_seconds"
    }

    public init(
        usedPercent: Double?,
        limitWindowSeconds: Int?,
        resetAtEpochSeconds: Int?,
        resetAfterSeconds: Int?
    ) {
        self.usedPercent = usedPercent
        self.limitWindowSeconds = limitWindowSeconds
        self.resetAtEpochSeconds = resetAtEpochSeconds
        self.resetAfterSeconds = resetAfterSeconds
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.usedPercent = (try container.decodeIfPresent(FlexibleDouble.self, forKey: .usedPercent))?.value
        self.limitWindowSeconds = (try container.decodeIfPresent(FlexibleInt.self, forKey: .limitWindowSeconds))?.value
        self.resetAtEpochSeconds = (try container.decodeIfPresent(FlexibleInt.self, forKey: .resetAtEpochSeconds))?.value
        self.resetAfterSeconds = (try container.decodeIfPresent(FlexibleInt.self, forKey: .resetAfterSeconds))?.value
    }
}
