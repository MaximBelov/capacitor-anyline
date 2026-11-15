package io.github.maximbelov.anyline

import org.json.JSONArray
import org.json.JSONObject

/** Turns what the SDK's wrapper session writes for a scan into what the call resolves with. */
object ScanResults {
    /**
     * [json] is the SDK's result string: one object for a single scan plugin, an array of them for a composite. A
     * Capacitor call resolves with an object, so a composite's array is put under `results`; a single result is passed
     * on as it is, the object io-anyline-cordova resolved with.
     */
    fun toCallResult(json: String, composite: Boolean): JSONObject {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) {
            return JSONObject().put("results", JSONArray(trimmed))
        }
        val result = JSONObject(trimmed)
        return if (composite) JSONObject().put("results", JSONArray().put(result)) else result
    }
}
