package io.github.maximbelov.anyline

/**
 * Whether this device can run the Anyline SDK's native code. The SDK ships its native library for ARM only, and a
 * device that cannot load it would crash in the SDK's own scan activity rather than fail a call.
 */
object DeviceSupport {
    /**
     * The ABIs the SDK's AAR ships native libraries for (its `jni/` folders). The Gradle task
     * `testAnylineSdkAbis` reads them from the resolved AAR and fails the build when this list no longer matches.
     */
    val SDK_ABIS = listOf("arm64-v8a", "armeabi-v7a")

    /** The native library the SDK loads (`System.loadLibrary`) when its first class is used. */
    const val SDK_NATIVE_LIBRARY = "anylineCore"

    /** Whether any of [deviceAbis] (`Build.SUPPORTED_ABIS`) is one the SDK ships native code for. */
    fun hasSdkAbi(deviceAbis: List<String>, sdkAbis: List<String> = SDK_ABIS): Boolean =
        deviceAbis.any { it in sdkAbis }

    /**
     * Whether the SDK can run here: the device reports an ABI the SDK ships, and the SDK's library actually loads into
     * this process. The second matters on its own: a device that translates ARM can list `armeabi-v7a` while the app
     * was installed for `x86`, without the ARM libraries.
     */
    fun isSupported(deviceAbis: List<String>, loadLibrary: (String) -> Unit): Boolean {
        if (!hasSdkAbi(deviceAbis)) {
            return false
        }
        return try {
            loadLibrary(SDK_NATIVE_LIBRARY)
            true
        } catch (e: Throwable) {
            false
        }
    }
}
