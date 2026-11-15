import Foundation

/// Named slots holding one value each: `put` returns what it replaced, `take` empties the slot. The plugin keeps the
/// calls waiting for the SDK in it, at most one initialisation and one scan; the SDK answers on its own threads, so
/// every access is behind a lock.
public final class Slots<Value> {
    private var values: [String: Value] = [:]
    private let lock = NSLock()

    public init() {}

    @discardableResult
    public func put(_ name: String, _ value: Value) -> Value? {
        lock.lock()
        defer { lock.unlock() }
        let previous = values[name]
        values[name] = value
        return previous
    }

    public func take(_ name: String) -> Value? {
        lock.lock()
        defer { lock.unlock() }
        return values.removeValue(forKey: name)
    }
}
