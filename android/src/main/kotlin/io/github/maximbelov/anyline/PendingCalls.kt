package io.github.maximbelov.anyline

import com.getcapacitor.PluginCall

/**
 * The scan waiting for the SDK: at most one, since the SDK's session runs one scan at a time. A new scan replaces a
 * waiting one, which is handed back so it can be rejected rather than left hanging. The SDK answers on its own threads,
 * so every access is synchronised. Initialisations are held by [InitGate].
 */
class PendingCalls {
    private val slots = Slots<PluginCall>()

    fun putScan(call: PluginCall): PluginCall? = slots.put(SCAN, call)

    fun takeScan(): PluginCall? = slots.take(SCAN)

    private companion object {
        const val SCAN = "scan"
    }
}

/** Named slots holding one value each: [put] returns what it replaced, [take] empties the slot. */
class Slots<T> {
    private val values = HashMap<String, T>()

    @Synchronized
    fun put(name: String, value: T): T? = values.put(name, value)

    @Synchronized
    fun take(name: String): T? = values.remove(name)
}
