import XCTest
@testable import CapacitorAnylineCore

final class InitGateTests: XCTestCase {
    private var gate = InitGate<String>()

    override func setUp() {
        gate = InitGate<String>()
    }

    private func request(_ key: String, _ call: String, offline: Bool = false) -> InitGate<String>.Decision {
        gate.request(licenseKey: key, enableOfflineCache: offline, call: call)
    }

    private func start(_ key: String, offline: Bool = false) -> InitGate<String>.Start {
        InitGate<String>.Start(licenseKey: key, enableOfflineCache: offline)
    }

    func testStartsTheFirstInitialisation() {
        XCTAssertEqual(request("a", "c1", offline: true), .start(start("a", offline: true)))
        XCTAssertTrue(gate.isRunning)
    }

    func testASecondCallWithTheSameKeyJoinsTheRunningOneInsteadOfStartingAnother() {
        _ = request("a", "c1")
        XCTAssertEqual(request("a", "c2"), .wait)
        XCTAssertEqual(request("a", "c3"), .wait)
        let settled = gate.settle(initialised: true)
        XCTAssertEqual(settled.settled, ["c1", "c2", "c3"])
        XCTAssertNil(settled.next)
        XCTAssertFalse(gate.isRunning)
    }

    func testJoinedCallsShareARefusal() {
        _ = request("a", "c1")
        _ = request("a", "c2")
        XCTAssertEqual(gate.settle(initialised: false).settled, ["c1", "c2"])
    }

    func testACallWithAnotherKeyWaitsForTheRunningOneThenRuns() {
        _ = request("a", "c1")
        XCTAssertEqual(request("b", "c2"), .wait)
        XCTAssertEqual(request("b", "c3"), .wait)
        let first = gate.settle(initialised: false)
        XCTAssertEqual(first.settled, ["c1"])
        XCTAssertEqual(first.next, start("b"))
        XCTAssertTrue(gate.isRunning)
        XCTAssertEqual(gate.settle(initialised: true).settled, ["c2", "c3"])
    }

    func testNeverHasTwoInitialisationsRunning() {
        let decisions = [request("a", "1"), request("b", "2"), request("a", "3"), request("c", "4"), request("b", "5")]
        XCTAssertEqual(decisions.filter { if case .start = $0 { return true }; return false }.count, 1)
        var starts = 0
        while gate.isRunning {
            if gate.settle(initialised: true).next != nil {
                starts += 1
            }
        }
        XCTAssertEqual(starts, 2)
    }

    func testARepeatWithTheInitialisedKeyResolvesWithoutTheSdk() {
        _ = request("a", "c1")
        _ = gate.settle(initialised: true)
        XCTAssertEqual(request("a", "c2"), .alreadyInitialised)
        XCTAssertFalse(gate.isRunning)
    }

    func testARefusedKeyIsInitialisedAgainNextTime() {
        _ = request("a", "c1")
        _ = gate.settle(initialised: false)
        XCTAssertEqual(request("a", "c2"), .start(start("a")))
    }

    func testACallWithTheRunningKeyJoinsItEvenWithOthersWaiting() {
        _ = request("a", "c1")
        _ = request("b", "c2")
        _ = request("a", "c3")
        let settled = gate.settle(initialised: true)
        XCTAssertEqual(settled.settled, ["c1", "c3"])
        XCTAssertEqual(settled.next, start("b"))
    }

    func testASettleWithNothingRunningSettlesNothing() {
        let settled = gate.settle(initialised: true)
        XCTAssertTrue(settled.settled.isEmpty)
        XCTAssertNil(settled.next)
    }

    func testConcurrentRequestsStartOnlyOnce() {
        let starts = NSLock()
        var count = 0
        DispatchQueue.concurrentPerform(iterations: 200) { index in
            if case .start = gate.request(licenseKey: "a", enableOfflineCache: false, call: "\(index)") {
                starts.lock()
                count += 1
                starts.unlock()
            }
        }
        XCTAssertEqual(count, 1)
        XCTAssertEqual(gate.settle(initialised: true).settled.count, 200)
    }
}
