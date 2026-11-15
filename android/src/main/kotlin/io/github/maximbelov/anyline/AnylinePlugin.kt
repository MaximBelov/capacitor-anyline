package io.github.maximbelov.anyline

import android.content.Context
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
import io.anyline2.wrapper.extensions.WrapperSessionScanStartRequestExtensionKt
import io.anyline2.wrapper.extensions.WrapperSessionSdkInitializationResponseExtensionKt
import io.anyline2.wrapper.legacy.LegacyPluginHelper

/**
 * The Anyline SDK through its wrapper session: `initAnylineSDK` asks the SDK to initialise, `scan` asks it to open its
 * own full-screen scan activity, and the SDK answers both through the [WrapperSessionClientInterface] registered in
 * [load]. Each call is held until its answer arrives.
 */
@CapacitorPlugin(name = "Anyline")
class AnylinePlugin : Plugin() {
    private val pending = PendingCalls()

    // Held by the plugin, so the SDK's session keeps a live client for as long as the bridge lives.
    private val client = object : WrapperSessionClientInterface {
        override fun getContext(): Context = activity ?: this@AnylinePlugin.context

        // null: the SDK opens its own activity rather than a view inside ours.
        override fun getContainerView(): ViewGroup? = null

        override fun onSdkInitializationResponse(initializationResponse: WrapperSessionSdkInitializationResponse) {
            val call = pending.takeInit() ?: return
            if (initializationResponse.initialized == true) {
                call.resolve()
            } else {
                val json = WrapperSessionSdkInitializationResponseExtensionKt.toJsonObject(initializationResponse)
                call.reject(json.toString(), AnylineErrorCode.INIT_FAILED)
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
            } catch (e: Exception) {
                call.reject(e.message ?: e.toString(), AnylineErrorCode.SCAN_FAILED, e)
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

    override fun load() {
        WrapperSessionProvider.setupWrapperSession(
            WrapperInfo(WrapperInfo.WrapperType.Cordova, VERSION, WrapperInfo.WrapperCodename.Legacy),
            client,
        )
    }

    @PluginMethod
    fun initAnylineSDK(call: PluginCall) {
        val licenseKey = call.getString("licenseKey")
        if (licenseKey.isNullOrEmpty()) {
            call.reject(AnylineMessages.MISSING_LICENSE_KEY, AnylineErrorCode.INVALID_CONFIG)
            return
        }
        val enableOfflineCache = call.getBoolean("enableOfflineCache", false) ?: false
        pending.putInit(call)?.reject(AnylineMessages.SUPERSEDED, AnylineErrorCode.INIT_FAILED)
        val request = LegacyPluginHelper.getWrapperSessionSdkInitializationRequestJson(
            licenseKey,
            enableOfflineCache,
            WEB_ASSETS_PATH,
        )
        WrapperSessionProvider.requestSdkInitialization(request.toString())
    }

    @PluginMethod
    fun scan(call: PluginCall) {
        val config = call.getObject("config")
        if (config == null) {
            call.reject(AnylineMessages.MISSING_CONFIG, AnylineErrorCode.INVALID_CONFIG)
            return
        }
        val request = try {
            // No initialisation parameters, config path or callback config: the result is delivered to the call.
            // Images are written to files, whose paths are in the result, as io-anyline-cordova did.
            LegacyPluginHelper.getWrapperSessionScanStartRequest(client.getContext(), config.toString(), null, null, null, true)
        } catch (e: Exception) {
            call.reject(e.message ?: e.toString(), AnylineErrorCode.INVALID_CONFIG, e)
            return
        }
        pending.putScan(call)?.reject(AnylineMessages.SUPERSEDED, AnylineErrorCode.SCAN_FAILED)
        WrapperSessionProvider.requestScanStart(WrapperSessionScanStartRequestExtensionKt.toJsonObject(request).toString())
    }

    companion object {
        /** The version reported to the SDK as the wrapper's. package.json carries the same. */
        const val VERSION = "0.1.0"

        /**
         * The folder of the APK's assets the SDK reads a relative asset path from: Capacitor packages the web assets
         * under public/ (Cordova used www/), so `assets/x` of the web build is `public/assets/x` in the APK.
         */
        const val WEB_ASSETS_PATH = "public/assets"
    }
}
