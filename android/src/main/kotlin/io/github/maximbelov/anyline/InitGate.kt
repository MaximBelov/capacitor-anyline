package io.github.maximbelov.anyline

/**
 * Lets one SDK initialisation run at a time. The SDK's initialisation is not safe to run twice at once, so:
 *
 * - a request while nothing runs starts an initialisation ([Decision.Start]);
 * - a request with the key of the initialisation that runs joins it and settles with it ([Decision.Wait]);
 * - a request with another key waits until the running one settles, then runs ([Decision.Wait]);
 * - a request with the key the SDK was last initialised with, while nothing runs, resolves at once without touching the
 *   SDK ([Decision.AlreadyInitialised]).
 *
 * [settle] ends the running initialisation and says which calls it settles and what to start next: the oldest waiting
 * key, with every call waiting for that key joined to it. The gate holds the calls ([T]) only while they wait. It does no I/O, so the plugin
 * performs what it decides, outside the lock.
 */
class InitGate<T> {
    data class Request<T>(val licenseKey: String, val enableOfflineCache: Boolean, val call: T)

    sealed class Decision {
        /** Start an initialisation with this key; [InitGate.settle] must follow its answer. */
        data class Start(val licenseKey: String, val enableOfflineCache: Boolean) : Decision()

        /** The call is held, and comes back from a later [InitGate.settle]. */
        object Wait : Decision()

        /** The SDK is already initialised with this key: resolve the call now. */
        object AlreadyInitialised : Decision()
    }

    data class Settled<T>(
        /** The calls of the initialisation that ended: they settle with its outcome. */
        val settled: List<T>,
        /** The initialisation to start next, for the calls still waiting, or null when none wait. */
        val next: Decision.Start?,
    )

    private var running: Request<T>? = null
    private val joined = mutableListOf<T>()
    private val queue = ArrayDeque<Request<T>>()
    private var initialisedKey: String? = null

    @Synchronized
    fun request(request: Request<T>): Decision {
        val current = running
        return when {
            current == null && request.licenseKey == initialisedKey -> Decision.AlreadyInitialised
            current == null -> begin(request)
            current.licenseKey == request.licenseKey -> {
                joined += request.call
                Decision.Wait
            }
            else -> {
                queue.addLast(request)
                Decision.Wait
            }
        }
    }

    /** Ends the running initialisation; [initialised] is whether the SDK accepted its key. */
    @Synchronized
    fun settle(initialised: Boolean): Settled<T> {
        val ended = running ?: return Settled(emptyList(), null)
        val settled = listOf(ended.call) + joined
        running = null
        joined.clear()
        // A refused key leaves nothing to rely on: the next request initialises again.
        initialisedKey = if (initialised) ended.licenseKey else null

        // A waiting key is never the one that just ended (a call with that key joined it), so it always needs a start.
        val head = queue.removeFirstOrNull() ?: return Settled(settled, null)
        val next = begin(head)
        joined += takeQueued(head.licenseKey)
        return Settled(settled, next)
    }

    /** Whether an initialisation is running. */
    @get:Synchronized
    val isRunning: Boolean
        get() = running != null

    private fun begin(request: Request<T>): Decision.Start {
        running = request
        return Decision.Start(request.licenseKey, request.enableOfflineCache)
    }

    private fun takeQueued(licenseKey: String): List<T> {
        val same = queue.filter { it.licenseKey == licenseKey }
        queue.removeAll { it.licenseKey == licenseKey }
        return same.map { it.call }
    }
}
