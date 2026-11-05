package eu.tintera.plume.features.devices

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemLineTest {

    @Test
    fun systemSeJmenemPlatformuNeopakuje() {
        assertEquals("Android 16", systemLine("Android", "Android 16"))
        assertEquals("Windows 11 10.0", systemLine("Android", "Windows 11 10.0"))
    }

    @Test
    fun cisloVerzeDostanePlatformu() {
        assertEquals("Android SDK 34", systemLine("Android", "SDK 34"))
    }

    @Test
    fun prazdnaPlatformaNeboSystem() {
        assertEquals("iOS 18.1", systemLine("", "iOS 18.1"))
        assertEquals("Android", systemLine("Android", ""))
    }
}
