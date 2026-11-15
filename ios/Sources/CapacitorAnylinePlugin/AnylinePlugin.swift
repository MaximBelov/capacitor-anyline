import Foundation
import UIKit
import Capacitor
import Anyline
#if canImport(CapacitorAnylineCore)
import CapacitorAnylineCore
#endif

/// The Anyline SDK through its wrapper session: `initAnylineSDK` asks the SDK to initialise, `scan` asks it to present
/// its own full-screen scanner, and the SDK answers both through the session client registered in `load`. Each call is
/// held until its answer arrives.
@objc(AnylinePlugin)
public class AnylinePlugin: CAPPlugin, CAPBridgedPlugin {
    /// The version reported to the SDK as the wrapper's. package.json carries the same.
    static let version = "0.1.0"

    public let identifier = "AnylinePlugin"
    public let jsName = "Anyline"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "initAnylineSDK", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "scan", returnType: CAPPluginReturnPromise)
    ]

    private let pending = Slots<CAPPluginCall>()
    // Held by the plugin, so the SDK's session keeps a live client for as long as the bridge lives.
    private lazy var client = AnylineSessionClient(pending: pending)

    override public func load() {
        let wrapper = ALWrapperConfig.cordova(Self.version, codename: ALWrapperCodename(rawValue: "legacy"))
        ALWrapperSessionProvider.setupWrapperSession(withWrapperInfo: wrapper, wrapperSessionClient: client)
    }

    @objc func initAnylineSDK(_ call: CAPPluginCall) {
        guard let licenseKey = call.getString("licenseKey"), !licenseKey.isEmpty else {
            call.reject(AnylineMessages.missingLicenseKey, AnylineErrorCode.invalidConfig)
            return
        }
        let enableOfflineCache = call.getBool("enableOfflineCache") ?? false
        pending.put(AnylineSessionClient.initSlot, call)?
            .reject(AnylineMessages.superseded, AnylineErrorCode.initFailed)
        // No asset path prefix: the SDK's default, as io-anyline-cordova passed on iOS.
        let request = ALLegacyPluginHelper.sdkInitializationRequestJson(
            withLicenseKey: licenseKey,
            enableOfflineCache: enableOfflineCache,
            assetPathPrefix: nil) as NSDictionary
        ALWrapperSessionProvider.requestSdkInitialization(withInitializationRequestParamsString: request.asJSONString())
    }

    @objc func scan(_ call: CAPPluginCall) {
        guard let config = call.getObject("config") else {
            call.reject(AnylineMessages.missingConfig, AnylineErrorCode.invalidConfig)
            return
        }
        let request: ALWrapperSessionScanStartRequest
        do {
            let configJSON = String(data: try JSONSerialization.data(withJSONObject: config), encoding: .utf8) ?? "{}"
            // No initialisation parameters or callback config: the result is delivered to the call. A relative path in
            // the config is read from the app bundle, and images are written to files whose paths are in the result,
            // both as io-anyline-cordova did.
            request = try ALLegacyPluginHelper.scanStartRequest(
                withScanViewConfigContentString: configJSON,
                scanViewInitializationParametersString: nil,
                scanViewConfigPath: Bundle.main.bundlePath,
                scanResultCallbackConfigString: nil,
                shouldReturnImages: true)
        } catch {
            call.reject(error.localizedDescription, AnylineErrorCode.invalidConfig)
            return
        }
        pending.put(AnylineSessionClient.scanSlot, call)?
            .reject(AnylineMessages.superseded, AnylineErrorCode.scanFailed)
        ALWrapperSessionProvider.requestScanStart(with: request)
    }
}

/// The SDK's answers, routed to the waiting calls.
final class AnylineSessionClient: NSObject, ALWrapperSessionClientDelegate {
    static let initSlot = "init"
    static let scanSlot = "scan"

    private let pending: Slots<CAPPluginCall>

    init(pending: Slots<CAPPluginCall>) {
        self.pending = pending
    }

    // nil for both: the SDK presents its scanner over the top view controller itself.
    func getContainerView() -> UIView? {
        nil
    }

    func getTopViewController() -> UIViewController? {
        nil
    }

    func onSdkInitializationResponse(_ initializationResponse: ALWrapperSessionSDKInitializationResponse) {
        guard let call = pending.take(Self.initSlot) else {
            return
        }
        if initializationResponse.initialized?.boolValue == true {
            call.resolve()
        } else {
            let json = (initializationResponse.toJSONDictionary() as NSDictionary).asJSONString()
            call.reject(json, AnylineErrorCode.initFailed)
        }
    }

    func onScanResults(_ scanResultsResponse: ALWrapperSessionScanResultsResponse) {
        guard let call = pending.take(Self.scanSlot) else {
            return
        }
        let type = scanResultsResponse.scanResultExtraInfo?.viewPluginType ?? ALViewPluginType.viewPlugin()
        let composite = type.value == ALViewPluginType.viewPluginComposite().value
        do {
            let json = try ALLegacyPluginHelper.scanResultsWithImagePath(
                from: Self.exportedResults(of: scanResultsResponse),
                viewPluginType: type)
            call.resolve(try ScanResults.callResult(fromJSON: json, composite: composite))
        } catch {
            call.reject("Could not read the scan result: \(error.localizedDescription)", AnylineErrorCode.scanFailed)
        }
    }

    func onScanResponse(_ scanResponse: ALWrapperSessionScanResponse) {
        switch scanResponse.status?.value {
        case ALWrapperSessionScanResponseStatus.scanAborted().value:
            pending.take(Self.scanSlot)?.reject(AnylineMessages.canceled, AnylineErrorCode.canceled)
        case ALWrapperSessionScanResponseStatus.scanFailed().value:
            pending.take(Self.scanSlot)?
                .reject(scanResponse.failInfo?.lastError ?? AnylineMessages.noResult, AnylineErrorCode.scanFailed)
        default:
            // The result itself arrives through onScanResults, which has already answered the call.
            break
        }
    }

    func onUIElementClicked(_ scanResultConfig: ALWrapperSessionScanResultConfig,
                            uiFeedbackElementConfig: ALUIFeedbackElementConfig) {}

    func onUCRReportResponse(_ ucrReportResponse: ALWrapperSessionUCRReportResponse) {}

    func onExportCachedEventsResponse(_ exportCachedEventsResponse: ALWrapperSessionExportCachedEventsResponse) {}

    /// The session's results as the SDK's result objects. The property is declared as dictionaries; read it untyped
    /// and take either form, so neither a dictionary nor a result object is force-cast.
    private static func exportedResults(of response: ALWrapperSessionScanResultsResponse) -> [ALExportedScanResult] {
        guard let raw = response.value(forKey: "exportedScanResults") as? NSArray else {
            return []
        }
        return raw.compactMap { item -> ALExportedScanResult? in
            if let result = item as? ALExportedScanResult {
                return result
            }
            if let dictionary = item as? [AnyHashable: Any] {
                return ALExportedScanResult.fromJSONDictionary(dictionary)
            }
            return nil
        }
    }
}
