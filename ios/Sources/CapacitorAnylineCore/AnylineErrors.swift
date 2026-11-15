import Foundation

/// The error codes a call rejects with. The TypeScript API declares the same strings.
public enum AnylineErrorCode {
    public static let canceled = "CANCELED"
    public static let scanFailed = "SCAN_FAILED"
    public static let initFailed = "INIT_FAILED"
    public static let invalidConfig = "INVALID_CONFIG"
    /// Never raised on iOS, where the SDK ships for every device architecture; declared so both platforms agree.
    public static let unsupportedDevice = "UNSUPPORTED_DEVICE"
}

/// The messages both platforms reject with where the SDK gives none.
public enum AnylineMessages {
    /// The message of a scan the user backed out of: the string io-anyline-cordova rejected with.
    public static let canceled = "Canceled"
    public static let missingLicenseKey = "initAnylineSDK needs a licenseKey string"
    public static let missingConfig = "scan needs a config object"
    public static let superseded = "Another scan was started before this one ended"
    public static let noResult = "The scan ended without a result"
    public static let unsupportedDevice =
        "The Anyline scanner cannot run on this device: its native code is not available for this CPU"
}
