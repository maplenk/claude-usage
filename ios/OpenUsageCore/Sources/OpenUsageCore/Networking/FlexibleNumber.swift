import Foundation

/// Swift equivalent of `data/remote/UtilizationAdapter.kt`.
///
/// `utilization` arrives from claude.ai as an Int (`72`), a Double (`72.5`) or a
/// String (`"72.5"`). Anything else — including an explicit `null` — decodes to
/// `0.0`, exactly like the Gson adapter.
public struct FlexibleDouble: Decodable, Equatable, Sendable {
    public let value: Double

    public init(_ value: Double) {
        self.value = value
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if container.decodeNil() {
            self.value = 0.0
            return
        }
        if let double = try? container.decode(Double.self) {
            self.value = double
            return
        }
        if let int = try? container.decode(Int.self) {
            self.value = Double(int)
            return
        }
        if let string = try? container.decode(String.self) {
            self.value = Double(string.trimmingCharacters(in: .whitespaces)) ?? 0.0
            return
        }
        if let bool = try? container.decode(Bool.self) {
            // Gson would skipValue() here; both paths land on 0.0.
            _ = bool
            self.value = 0.0
            return
        }
        self.value = 0.0
    }
}

/// The Codex device-code response returns `interval` as a JSON element that has
/// been observed both as a number and as a string.
public struct FlexibleInt: Decodable, Equatable, Sendable {
    public let value: Int?

    public init(_ value: Int?) {
        self.value = value
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if container.decodeNil() {
            self.value = nil
            return
        }
        if let int = try? container.decode(Int.self) {
            self.value = int
            return
        }
        if let double = try? container.decode(Double.self) {
            self.value = Int(double)
            return
        }
        if let string = try? container.decode(String.self) {
            self.value = Int(string.trimmingCharacters(in: .whitespaces))
            return
        }
        self.value = nil
    }
}
