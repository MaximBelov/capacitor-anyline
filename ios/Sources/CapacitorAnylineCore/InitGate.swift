import Foundation

/// Lets one SDK initialisation run at a time. The SDK's initialisation is not safe to run twice at once, so:
///
/// - a request while nothing runs starts an initialisation (`.start`);
/// - a request with the key of the initialisation that runs joins it and settles with it (`.wait`);
/// - a request with another key waits until the running one settles, then runs (`.wait`);
/// - a request with the key the SDK was last initialised with, while nothing runs, resolves at once without touching the
///   SDK (`.alreadyInitialised`).
///
/// `settle` ends the running initialisation and says which calls it settles and what to start next: the oldest waiting
/// key, with every call waiting for that key joined to it. The gate holds the calls only while they wait. It does no
/// I/O, so the plugin performs what it decides, outside the lock.
public final class InitGate<Call> {
    public struct Start: Equatable {
        public let licenseKey: String
        public let enableOfflineCache: Bool

        public init(licenseKey: String, enableOfflineCache: Bool) {
            self.licenseKey = licenseKey
            self.enableOfflineCache = enableOfflineCache
        }
    }

    public enum Decision: Equatable {
        /// Start an initialisation with this key; `settle` must follow its answer.
        case start(Start)
        /// The call is held, and comes back from a later `settle`.
        case wait
        /// The SDK is already initialised with this key: resolve the call now.
        case alreadyInitialised
    }

    public struct Settled {
        /// The calls of the initialisation that ended: they settle with its outcome.
        public let settled: [Call]
        /// The initialisation to start next, for the calls still waiting, or nil when none wait.
        public let next: Start?
    }

    private struct Request {
        let start: Start
        let call: Call
    }

    private var running: Request?
    private var joined: [Call] = []
    private var queue: [Request] = []
    private var initialisedKey: String?
    private let lock = NSLock()

    public init() {}

    public var isRunning: Bool {
        lock.lock()
        defer { lock.unlock() }
        return running != nil
    }

    public func request(licenseKey: String, enableOfflineCache: Bool, call: Call) -> Decision {
        lock.lock()
        defer { lock.unlock() }
        let request = Request(start: Start(licenseKey: licenseKey, enableOfflineCache: enableOfflineCache), call: call)
        guard let current = running else {
            if licenseKey == initialisedKey {
                return .alreadyInitialised
            }
            running = request
            return .start(request.start)
        }
        if current.start.licenseKey == licenseKey {
            joined.append(call)
        } else {
            queue.append(request)
        }
        return .wait
    }

    /// Ends the running initialisation; `initialised` is whether the SDK accepted its key.
    public func settle(initialised: Bool) -> Settled {
        lock.lock()
        defer { lock.unlock() }
        guard let ended = running else {
            return Settled(settled: [], next: nil)
        }
        let settled = [ended.call] + joined
        running = nil
        joined = []
        // A refused key leaves nothing to rely on: the next request initialises again.
        initialisedKey = initialised ? ended.start.licenseKey : nil
        // A waiting key is never the one that just ended (a call with that key joined it), so it always needs a start.
        guard !queue.isEmpty else {
            return Settled(settled: settled, next: nil)
        }
        let head = queue.removeFirst()
        running = head
        joined = queue.filter { $0.start.licenseKey == head.start.licenseKey }.map(\.call)
        queue.removeAll { $0.start.licenseKey == head.start.licenseKey }
        return Settled(settled: settled, next: head.start)
    }
}
