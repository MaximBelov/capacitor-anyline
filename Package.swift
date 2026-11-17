// swift-tools-version: 5.9
import Foundation
import PackageDescription

// CapacitorAnylineCore (mapping a scan result, the error codes, the waiting calls) needs only Foundation;
// CapacitorAnylinePlugin is the Capacitor and Anyline layer on top. The Anyline SDK ships no macOS slice and no arm64
// simulator slice, so on a Mac `CAPACITOR_ANYLINE_CORE_ONLY=1 swift test` builds and tests the core alone. An app never
// sets it.
//
// The SDK comes from Anyline's own Swift package, and the plugin ships it: it is the app's one copy of
// Anyline.xcframework. The lower bound is the SDK this plugin was written against; the app's Package.resolved pins
// the version.
// The package and its product are named as the Capacitor CLI names them for capacitor-anyline (fixName in
// @capacitor/cli): an app's CapApp-SPM refers to both by that name.
let coreOnly = ProcessInfo.processInfo.environment["CAPACITOR_ANYLINE_CORE_ONLY"] != nil

var targets: [Target] = [
    .target(
        name: "CapacitorAnylineCore",
        path: "ios/Sources/CapacitorAnylineCore"),
    .testTarget(
        name: "CapacitorAnylineCoreTests",
        dependencies: ["CapacitorAnylineCore"],
        path: "ios/Tests/CapacitorAnylineCoreTests")
]
var products: [Product] = [
    .library(name: "CapacitorAnylineCore", targets: ["CapacitorAnylineCore"])
]
var dependencies: [Package.Dependency] = []

if !coreOnly {
    dependencies += [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0"),
        .package(url: "https://github.com/Anyline/anyline-ocr-spm-module.git", from: "56.7.0")
    ]
    products = [
        .library(
            name: "CapacitorAnyline",
            targets: ["CapacitorAnylinePlugin"])
    ]
    targets += [
        .target(
            name: "CapacitorAnylinePlugin",
            dependencies: [
                "CapacitorAnylineCore",
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                .product(name: "AnylinePackage", package: "anyline-ocr-spm-module")
            ],
            path: "ios/Sources/CapacitorAnylinePlugin")
    ]
}

let package = Package(
    name: "CapacitorAnyline",
    platforms: [.iOS(.v15), .macOS(.v12)],
    products: products,
    dependencies: dependencies,
    targets: targets
)
