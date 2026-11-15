package io.github.maximbelov.anyline

/** The error codes a call rejects with. The TypeScript API declares the same strings. */
object AnylineErrorCode {
    const val CANCELED = "CANCELED"
    const val SCAN_FAILED = "SCAN_FAILED"
    const val INIT_FAILED = "INIT_FAILED"
    const val INVALID_CONFIG = "INVALID_CONFIG"
    const val UNSUPPORTED_DEVICE = "UNSUPPORTED_DEVICE"
}

/** The messages both platforms reject with where the SDK gives none. */
object AnylineMessages {
    /** The message of a scan the user backed out of: the string io-anyline-cordova rejected with. */
    const val CANCELED = "Canceled"
    const val MISSING_LICENSE_KEY = "initAnylineSDK needs a licenseKey string"
    const val MISSING_CONFIG = "scan needs a config object"
    const val SUPERSEDED = "Another scan was started before this one ended"
    const val NO_RESULT = "The scan ended without a result"
    const val UNSUPPORTED_DEVICE = "The Anyline scanner cannot run on this device: its native code is not available for this CPU"
}
