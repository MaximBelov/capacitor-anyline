package io.github.maximbelov.anyline

import com.getcapacitor.PluginCall

/**
 * The calls waiting for the SDK: at most one initialisation and one scan, since the SDK's session answers one of each
 * at a time. A new call replaces a waiting one, which is handed back so it can be rejected rather than left hanging.
 * The SDK answers on its own threads, so every access is synchronised.
 */
class PendingCalls {
    private val slots = Slots<PluginCall>()

    fun putInit(call: PluginCall): PluginCall? = slots.put(INIT, call)

    fun takeInit(): PluginCall? = slots.take(INIT)

    fun putScan(call: PluginCall): PluginCall? = slots.put(SCAN, call)

    fun takeScan(): PluginCall? = slots.take(SCAN)

    private companion object {
        const val INIT = "init"
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
