package io.github.maximbelov.anyline

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.view.ViewGroup
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import io.anyline.plugin.config.UIFeedbackElementConfig
import io.anyline.wrapper.config.WrapperSessionExportCachedEventsResponse
import io.anyline.wrapper.config.WrapperSessionScanResponse
import io.anyline.wrapper.config.WrapperSessionScanResponse.WrapperSessionScanResponseStatus
import io.anyline.wrapper.config.WrapperSessionScanResultConfig
import io.anyline.wrapper.config.WrapperSessionScanResultExtraInfo.ViewPluginType
import io.anyline.wrapper.config.WrapperSessionScanResultsResponse
import io.anyline.wrapper.config.WrapperSessionSdkInitializationResponse
import io.anyline.wrapper.config.WrapperSessionUCRReportResponse
import io.anyline2.WrapperInfo
import io.anyline2.wrapper.WrapperSessionClientInterface
import io.anyline2.wrapper.WrapperSessionProvider
import io.anyline2.wrapper.extensions.toJsonObject
import io.anyline2.wrapper.legacy.LegacyPluginHelper

/**
 * The Anyline SDK through its wrapper session: `initAnylineSDK` asks the SDK to initialise, `scan` asks it to open its
 * own full-screen scan activity, and the SDK answers both through the [WrapperSessionClientInterface] registered on
 * the first call. Each call is held until its answer arrives.
 *
 * Every call into the SDK is guarded: the SDK is not used at all on a device whose CPU it has no native code for
 * ([DeviceSupport]), one initialisation runs at a time ([InitGate]), and anything the SDK throws, a [LinkageError]
 * included, rejects the call instead of crashing the app.
 */
@CapacitorPlugin(name = "Anyline")
class AnylinePlugin : Plugin() {
    private val pending = PendingCalls()
    private val initGate = InitGate<PluginCall>()

    private val supported: Boolean by lazy {
        DeviceSupport.isSupported(Build.SUPPORTED_ABIS.toList()) { System.loadLibrary(it) }
    }

    // Held by the plugin, so the SDK's session keeps a live client for as long as the bridge lives.
    private val client = object : WrapperSessionClientInterface {
        override fun getContext(): Context = activity ?: this@AnylinePlugin.context

        // null: the SDK opens its own activity rather than a view inside ours.
        override fun getContainerView(): ViewGroup? = null

        override fun onSdkInitializationResponse(initializationResponse: WrapperSessionSdkInitializationResponse) {
            if (initializationResponse.initialized == true) {
                finishInit(null, AnylineErrorCode.INIT_FAILED)
            } else {
                val message = try {
                    initializationResponse.toJsonObject().toString()
                } catch (e: Throwable) {
                    e.message ?: e.toString()
                }
                finishInit(message, AnylineErrorCode.INIT_FAILED)
            }
        }

        override fun onScanResults(scanResultsResponse: WrapperSessionScanResultsResponse) {
            val call = pending.takeScan() ?: return
            try {
                val type = scanResultsResponse.scanResultExtraInfo?.viewPluginType
                val json = LegacyPluginHelper.getScanResultsWithImagePath(
                    scanResultsResponse.exportedScanResults ?: emptyList(),
                    type ?: ViewPluginType.VIEW_PLUGIN,
                )
                call.resolve(JSObject.fromJSONObject(ScanResults.toCallResult(json, type == ViewPluginType.VIEW_PLUGIN_COMPOSITE)))
            } catch (e: Throwable) {
                call.reject(e.message ?: e.toString(), AnylineErrorCode.SCAN_FAILED, e as? Exception)
            }
        }

        override fun onScanResponse(scanResponse: WrapperSessionScanResponse) {
            when (scanResponse.status) {
                WrapperSessionScanResponseStatus.SCAN_ABORTED ->
                    pending.takeScan()?.reject(AnylineMessages.CANCELED, AnylineErrorCode.CANCELED)
                WrapperSessionScanResponseStatus.SCAN_FAILED ->
                    pending.takeScan()?.reject(
                        scanResponse.failInfo?.lastError ?: AnylineMessages.NO_RESULT,
                        AnylineErrorCode.SCAN_FAILED,
                    )
                // The result itself arrives through onScanResults, which has already answered the call.
                else -> Unit
            }
        }

        override fun onUIElementClicked(
            scanResultConfig: WrapperSessionScanResultConfig,
            uiFeedbackElementConfig: UIFeedbackElementConfig,
        ) = Unit

        override fun onUCRReportResponse(ucrReportResponse: WrapperSessionUCRReportResponse) = Unit

        override fun onExportCachedEventsResponse(exportCachedEventsResponse: WrapperSessionExportCachedEventsResponse) =
            Unit
    }

    private var sessionReady = false

    /**
     * Sets the SDK's session up on the first call that needs it, rather than when the app starts, so an app that never
     * scans never loads the SDK's native library. False on a device the SDK cannot run on, or when the SDK threw while
     * setting up.
     */
    @Synchronized
    private fun ensureSession(): Boolean {
        if (sessionReady) {
            return true
        }
        if (!supported) {
            return false
        }
        sessionReady = try {
            WrapperSessionProvider.setupWrapperSession(
                WrapperInfo(WrapperInfo.WrapperType.Cordova, VERSION, WrapperInfo.WrapperCodename.Legacy),
                client,
            )
            true
        } catch (e: Throwable) {
            false
        }
        return sessionReady
    }

    @PluginMethod
    fun initAnylineSDK(call: PluginCall) {
        val licenseKey = call.getString("licenseKey")
        if (licenseKey.isNullOrEmpty()) {
            call.reject(AnylineMessages.MISSING_LICENSE_KEY, AnylineErrorCode.INVALID_CONFIG)
            return
        }
        if (!ensureSession()) {
            call.reject(AnylineMessages.UNSUPPORTED_DEVICE, AnylineErrorCode.UNSUPPORTED_DEVICE)
            return
        }
        val enableOfflineCache = call.getBoolean("enableOfflineCache", false) ?: false
        when (val decision = initGate.request(InitGate.Request(licenseKey, enableOfflineCache, call))) {
            is InitGate.Decision.Start -> startInit(decision)
            InitGate.Decision.AlreadyInitialised -> call.resolve()
            InitGate.Decision.Wait -> Unit
        }
    }

    @PluginMethod
    fun scan(call: PluginCall) {
        val config = call.getObject("config")
        if (config == null) {
            call.reject(AnylineMessages.MISSING_CONFIG, AnylineErrorCode.INVALID_CONFIG)
            return
        }
        if (!ensureSession()) {
            call.reject(AnylineMessages.UNSUPPORTED_DEVICE, AnylineErrorCode.UNSUPPORTED_DEVICE)
            return
        }
        val request = try {
            // No initialisation parameters, config path or callback config: the result is delivered to the call.
            // Images are written to files, whose paths are in the result, as io-anyline-cordova did.
            LegacyPluginHelper.getWrapperSessionScanStartRequest(client.getContext(), config.toString(), null, null, null, true)
        } catch (e: Throwable) {
            call.reject(e.message ?: e.toString(), AnylineErrorCode.INVALID_CONFIG, e as? Exception)
            return
        }
        pending.putScan(call)?.reject(AnylineMessages.SUPERSEDED, AnylineErrorCode.SCAN_FAILED)
        try {
            WrapperSessionProvider.requestScanStart(request.toJsonObject().toString())
        } catch (e: Throwable) {
            pending.takeScan()?.reject(e.message ?: e.toString(), errorCodeFor(e, AnylineErrorCode.SCAN_FAILED), e as? Exception)
        }
    }

    @PluginMethod
    fun getMemoryInfo(call: PluginCall) {
        val result = JSObject()
        try {
            val manager = context.getSystemService(ActivityManager::class.java)
            if (manager != null) {
                val info = ActivityManager.MemoryInfo()
                manager.getMemoryInfo(info)
                result.put("availableBytes", info.availMem)
            }
        } catch (e: Throwable) {
            // Left out: the method reports what it can read.
        }
        try {
            // The process's proportional set size: Java and native heaps, graphics and mapped files, the nearest
            // Android figure to the footprint iOS reports. Debug.getPss() is in kilobytes.
            result.put("footprintBytes", Debug.getPss() * 1024)
        } catch (e: Throwable) {
            // Left out, as above.
        }
        call.resolve(result)
    }

    private fun startInit(start: InitGate.Decision.Start) {
        try {
            val request = LegacyPluginHelper.getWrapperSessionSdkInitializationRequestJson(
                start.licenseKey,
                start.enableOfflineCache,
                WEB_ASSETS_PATH,
            )
            WrapperSessionProvider.requestSdkInitialization(request.toString())
        } catch (e: Throwable) {
            finishInit(e.message ?: e.toString(), errorCodeFor(e, AnylineErrorCode.INIT_FAILED))
        }
    }

    /** Settles the running initialisation: resolved when [failure] is null, rejected with it and [code] otherwise. */
    private fun finishInit(failure: String?, code: String) {
        val settled = initGate.settle(failure == null)
        settled.settled.forEach { if (failure == null) it.resolve() else it.reject(failure, code) }
        settled.next?.let { startInit(it) }
    }

    /** A native library or class the SDK needs could not be loaded: this device cannot run it. */
    private fun errorCodeFor(error: Throwable, otherwise: String): String =
        if (error is LinkageError) AnylineErrorCode.UNSUPPORTED_DEVICE else otherwise

    companion object {
        /** The version reported to the SDK as the wrapper's. package.json carries the same. */
        const val VERSION = "0.2.0"

        /**
         * The folder of the APK's assets the SDK reads a relative asset path from: Capacitor packages the web assets
         * under public/ (Cordova used www/), so `assets/x` of the web build is `public/assets/x` in the APK.
         */
        const val WEB_ASSETS_PATH = "public/assets"
    }
}
