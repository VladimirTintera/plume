package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceKindTest {

    @Test
    fun desktopPlumeSPlatformouAndroidJeDesktop() {
        assertEquals(DeviceKind.Desktop, refinedDeviceKind(DeviceKind.Phone, "Desktop (amd64)", "Windows 11 10.0"))
        assertEquals(DeviceKind.Desktop, refinedDeviceKind(DeviceKind.Phone, "Desktop (aarch64)", "Mac OS X 15.1"))
        assertEquals(DeviceKind.Desktop, refinedDeviceKind(DeviceKind.Unknown, "Desktop (amd64)", "Linux 6.8"))
    }

    @Test
    fun hodinkyPodleModelu() {
        assertEquals(DeviceKind.Watch, refinedDeviceKind(DeviceKind.Phone, "Google Pixel Watch 4", "Android 16"))
    }

    @Test
    fun telefonZustavaTelefonem() {
        assertEquals(DeviceKind.Phone, refinedDeviceKind(DeviceKind.Phone, "Google Pixel 11 Pro Fold", "Android 17"))
        assertEquals(DeviceKind.Phone, refinedDeviceKind(DeviceKind.Phone, "Samsung SM-S918B", "SDK 34"))
    }

    @Test
    fun druhOdTdlibMimoTelefonSeNemeni() {
        assertEquals(DeviceKind.Browser, refinedDeviceKind(DeviceKind.Browser, "Chrome 130", "Windows 11"))
        assertEquals(DeviceKind.Tablet, refinedDeviceKind(DeviceKind.Tablet, "iPad Pro", "iPadOS 18"))
    }
}
