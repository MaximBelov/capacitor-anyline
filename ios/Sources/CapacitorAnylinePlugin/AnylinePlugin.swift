import Foundation
import UIKit
import os
import Capacitor
import Anyline
#if canImport(CapacitorAnylineCore)
import CapacitorAnylineCore
#endif

/// The Anyline SDK through its wrapper session: `initAnylineSDK` asks the SDK to initialise, `scan` asks it to present
/// its own full-screen scanner, and the SDK answers both through the `AnylineSession` registered in `load`. Each call is
/// held until its answer arrives.
@objc(AnylinePlugin)
public class AnylinePlugin: CAPPlugin, CAPBridgedPlugin {
    /// The version reported to the SDK as the wrapper's. package.json carries the same.
    static let version = "0.3.2"

    public let identifier = "AnylinePlugin"
    public let jsName = "Anyline"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "initAnylineSDK", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "scan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getMemoryInfo", returnType: CAPPluginReturnPromise)
    ]

    // Held by the plugin, so the SDK's session keeps a live client for as long as the bridge lives. The session holds
    // nothing of the plugin, so there is no cycle between them.
    private let session = AnylineSession()

    override public func load() {
        session.setUp(wrapperVersion: Self.version)
    }

    @objc func initAnylineSDK(_ call: CAPPluginCall) {
        guard let licenseKey = call.getString("licenseKey"), !licenseKey.isEmpty else {
            call.reject(AnylineMessages.missingLicenseKey, AnylineErrorCode.invalidConfig)
            return
        }
        session.initialise(licenseKey: licenseKey,
                           enableOfflineCache: call.getBool("enableOfflineCache") ?? false,
                           call: call)
    }

    @objc func scan(_ call: CAPPluginCall) {
        guard let config = call.getObject("config") else {
            call.reject(AnylineMessages.missingConfig, AnylineErrorCode.invalidConfig)
            return
        }
        let configJSON: String
        do {
            configJSON = String(data: try JSONSerialization.data(withJSONObject: config), encoding: .utf8) ?? "{}"
        } catch {
            call.reject(error.localizedDescription, AnylineErrorCode.invalidConfig)
            return
        }
        session.scan(configJSON: configJSON, call: call)
    }

    @objc func getMemoryInfo(_ call: CAPPluginCall) {
        var result: [String: Any] = [:]
        // What the process can still allocate before iOS terminates it; 0 where the system does not say.
        let available = os_proc_available_memory()
        if available > 0 {
            result["availableBytes"] = Int(available)
        }
        // The process's physical footprint, the figure the system's memory limit is measured against.
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<integer_t>.size)
        let status = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        if status == KERN_SUCCESS {
            result["footprintBytes"] = Int(info.phys_footprint)
        }
        call.resolve(result)
    }
}

/// The plugin's side of the SDK's wrapper session.
///
/// Every call into the SDK (`ALWrapperSessionProvider`, `ALLegacyPluginHelper`) is made on `sdkQueue`, one serial queue
/// owned by the session, so no two of them ever overlap, whatever queue Capacitor or the SDK calls back on. The SDK's
/// headers state no threading contract; it presents and dismisses its scanner itself, and the plugin touches no UI. On
/// top of that, `InitGate` lets one initialisation run at a time, so a second `initAnylineSDK` never issues a second
/// request while the first is answered.
///
/// The session holds a waiting call only until it settles, and nothing of the SDK's beyond that: not its scan view
/// controller, not its result objects (a result is turned into a dictionary and handed on), so the scanner can be freed
/// once it has closed.
final class AnylineSession: NSObject, ALWrapperSessionClientDelegate {
    private static let scanSlot = "scan"

    private let sdkQueue = DispatchQueue(label: "io.github.maximbelov.anyline.sdk")
    private let initGate = InitGate<CAPPluginCall>()
    private let scans = Slots<CAPPluginCall>()

    func setUp(wrapperVersion: String) {
        sdkQueue.async {
            let wrapper = ALWrapperConfig.cordova(wrapperVersion, codename: ALWrapperCodename(rawValue: "legacy"))
            ALWrapperSessionProvider.setupWrapperSession(withWrapperInfo: wrapper, wrapperSessionClient: self)
        }
    }

    func initialise(licenseKey: String, enableOfflineCache: Bool, call: CAPPluginCall) {
        switch initGate.request(licenseKey: licenseKey, enableOfflineCache: enableOfflineCache, call: call) {
        case .start(let start):
            requestInitialisation(start)
        case .alreadyInitialised:
            call.resolve()
        case .wait:
            break
        }
    }

    func scan(configJSON: String, call: CAPPluginCall) {
        sdkQueue.async {
            let request: ALWrapperSessionScanStartRequest
            do {
                // No initialisation parameters or callback config: the result is delivered to the call. A relative path
                // in the config is read from the app bundle, and images are written to files whose paths are in the
                // result, both as io-anyline-cordova did.
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
            self.scans.put(Self.scanSlot, call)?.reject(AnylineMessages.superseded, AnylineErrorCode.scanFailed)
            ALWrapperSessionProvider.requestScanStart(with: request)
        }
    }

    private func requestInitialisation(_ start: InitGate<CAPPluginCall>.Start) {
        sdkQueue.async {
            // No asset path prefix: the SDK's default, as io-anyline-cordova passed on iOS.
            let request = ALLegacyPluginHelper.sdkInitializationRequestJson(
                withLicenseKey: start.licenseKey,
                enableOfflineCache: start.enableOfflineCache,
                assetPathPrefix: nil) as NSDictionary
            ALWrapperSessionProvider.requestSdkInitialization(withInitializationRequestParamsString: request.asJSONString())
        }
    }

    private func finishInitialisation(failure: String?) {
        let settled = initGate.settle(initialised: failure == nil)
        for call in settled.settled {
            if let failure {
                call.reject(failure, AnylineErrorCode.initFailed)
            } else {
                call.resolve()
            }
        }
        if let next = settled.next {
            requestInitialisation(next)
        }
    }

    // MARK: ALWrapperSessionClientDelegate

    // nil for both: the SDK presents its scanner over the top view controller itself.
    func getContainerView() -> UIView? {
        nil
    }

    func getTopViewController() -> UIViewController? {
        nil
    }

    func onSdkInitializationResponse(_ initializationResponse: ALWrapperSessionSDKInitializationResponse) {
        let initialised = initializationResponse.initialized?.boolValue == true
        sdkQueue.async {
            let failure = initialised ? nil : (initializationResponse.toJSONDictionary() as NSDictionary).asJSONString()
            self.finishInitialisation(failure: failure)
        }
    }

    func onScanResults(_ scanResultsResponse: ALWrapperSessionScanResultsResponse) {
        guard let call = scans.take(Self.scanSlot) else {
            return
        }
        sdkQueue.async {
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
    }

    func onScanResponse(_ scanResponse: ALWrapperSessionScanResponse) {
        switch scanResponse.status?.value {
        case ALWrapperSessionScanResponseStatus.scanAborted().value:
            scans.take(Self.scanSlot)?.reject(AnylineMessages.canceled, AnylineErrorCode.canceled)
        case ALWrapperSessionScanResponseStatus.scanFailed().value:
            scans.take(Self.scanSlot)?
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
