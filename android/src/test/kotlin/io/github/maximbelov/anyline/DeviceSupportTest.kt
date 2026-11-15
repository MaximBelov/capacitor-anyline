package io.github.maximbelov.anyline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSupportTest {
    @Test
    fun acceptsAnArmDevice() {
        assertTrue(DeviceSupport.hasSdkAbi(listOf("arm64-v8a", "armeabi-v7a", "armeabi")))
        assertTrue(DeviceSupport.hasSdkAbi(listOf("armeabi-v7a", "armeabi")))
    }

    @Test
    fun refusesADeviceWithOnlyIntelAbis() {
        assertFalse(DeviceSupport.hasSdkAbi(listOf("x86_64", "x86")))
        assertFalse(DeviceSupport.hasSdkAbi(emptyList()))
    }

    @Test
    fun loadsTheSdksLibraryOnlyWhenTheAbiMatches() {
        val loaded = mutableListOf<String>()
        assertFalse(DeviceSupport.isSupported(listOf("x86")) { loaded += it })
        assertTrue(loaded.isEmpty())
        assertTrue(DeviceSupport.isSupported(listOf("arm64-v8a")) { loaded += it })
        assertEquals(listOf("anylineCore"), loaded)
    }

    @Test
    fun refusesAnArmListingDeviceWhoseProcessCannotLoadTheLibrary() {
        // A device that translates ARM lists armeabi-v7a, while the app was installed with its x86 libraries.
        assertFalse(DeviceSupport.isSupported(listOf("x86", "armeabi-v7a")) { throw UnsatisfiedLinkError("dlopen failed") })
    }
}
