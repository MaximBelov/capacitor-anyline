package io.github.maximbelov.anyline

import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

// The JavaScript reaches the plugin by the name in @CapacitorPlugin and each method by its name; a rename on either side
// fails only at run time on a phone, so both are pinned here.
class AnylinePluginTest {
    @Test
    fun isRegisteredUnderTheNameTheJavaScriptUses() {
        val annotation = AnylinePlugin::class.java.getAnnotation(CapacitorPlugin::class.java)
        assertNotNull(annotation)
        assertEquals("Anyline", annotation!!.name)
    }

    @Test
    fun exposesExactlyTheMethodsOfTheApi() {
        val methods = AnylinePlugin::class.java.declaredMethods
            .filter { it.isAnnotationPresent(PluginMethod::class.java) }
            .map { it.name }
            .sorted()
        assertEquals(listOf("getMemoryInfo", "initAnylineSDK", "scan"), methods)
        methods.forEach { AnylinePlugin::class.java.getMethod(it, PluginCall::class.java) }
    }

    @Test
    fun pointsTheSdkAtCapacitorsWebAssets() {
        assertEquals("public/assets", AnylinePlugin.WEB_ASSETS_PATH)
    }

    @Test
    fun rejectsACancelWithTheStringTheCordovaPluginUsed() {
        assertEquals("Canceled", AnylineMessages.CANCELED)
        assertEquals("CANCELED", AnylineErrorCode.CANCELED)
    }
}
