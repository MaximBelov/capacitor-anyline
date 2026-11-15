import Foundation

/// Turns what the SDK's wrapper session writes for a scan into what the call resolves with.
public enum ScanResults {
    public enum Failure: Error, Equatable {
        /// The SDK's result string is not a JSON object or array.
        case notJSON
    }

    /// `json` is the SDK's result string: one object for a single scan plugin, an array of them for a composite. A
    /// Capacitor call resolves with an object, so a composite's array is put under `results`; a single result is
    /// passed on as it is, the object io-anyline-cordova resolved with.
    public static func callResult(fromJSON json: String, composite: Bool) throws -> [String: Any] {
        guard let data = json.data(using: .utf8),
              let parsed = try? JSONSerialization.jsonObject(with: data) else {
            throw Failure.notJSON
        }
        if let array = parsed as? [Any] {
            return ["results": array]
        }
        guard let object = parsed as? [String: Any] else {
            throw Failure.notJSON
        }
        return composite ? ["results": [object]] : object
    }
}
