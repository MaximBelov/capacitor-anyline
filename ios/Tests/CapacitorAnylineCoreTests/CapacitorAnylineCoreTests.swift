import XCTest
@testable import CapacitorAnylineCore

final class ScanResultsTests: XCTestCase {
    private let barcode =
        #"{"pluginID":"barcode","barcodeResult":{"barcodes":[{"value":"372123456789012","format":"CODE_128"}]}}"#

    func testPassesASingleResultOnAsTheObjectItIs() throws {
        let result = try ScanResults.callResult(fromJSON: barcode, composite: false)
        let barcodes = (result["barcodeResult"] as? [String: Any])?["barcodes"] as? [[String: Any]]
        XCTAssertEqual(barcodes?.first?["value"] as? String, "372123456789012")
        XCTAssertNil(result["results"])
    }

    func testPutsACompositesArrayUnderResults() throws {
        let result = try ScanResults.callResult(fromJSON: "[\(barcode),\(barcode)]", composite: true)
        XCTAssertEqual((result["results"] as? [Any])?.count, 2)
    }

    func testWrapsASingleObjectOfACompositeAsAListOfOne() throws {
        let result = try ScanResults.callResult(fromJSON: barcode, composite: true)
        XCTAssertEqual((result["results"] as? [Any])?.count, 1)
    }

    func testRefusesWhatIsNotJSON() {
        XCTAssertThrowsError(try ScanResults.callResult(fromJSON: "not json", composite: false)) {
            XCTAssertEqual($0 as? ScanResults.Failure, .notJSON)
        }
        XCTAssertThrowsError(try ScanResults.callResult(fromJSON: "\"a string\"", composite: false))
    }
}

final class SlotsTests: XCTestCase {
    func testHandsBackTheValueANewOneReplaces() {
        let slots = Slots<String>()
        XCTAssertNil(slots.put("scan", "first"))
        XCTAssertEqual(slots.put("scan", "second"), "first")
        XCTAssertEqual(slots.take("scan"), "second")
    }

    func testTakesAValueOnce() {
        let slots = Slots<String>()
        slots.put("init", "call")
        XCTAssertEqual(slots.take("init"), "call")
        XCTAssertNil(slots.take("init"))
    }

    func testKeepsTheSlotsApart() {
        let slots = Slots<String>()
        slots.put("init", "a")
        slots.put("scan", "b")
        XCTAssertEqual(slots.take("scan"), "b")
        XCTAssertEqual(slots.take("init"), "a")
    }
}

final class MessagesTests: XCTestCase {
    func testRejectsACancelWithTheStringTheCordovaPluginUsed() {
        XCTAssertEqual(AnylineMessages.canceled, "Canceled")
        XCTAssertEqual(AnylineErrorCode.canceled, "CANCELED")
    }
}
