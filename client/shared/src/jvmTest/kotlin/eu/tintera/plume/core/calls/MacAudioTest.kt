package eu.tintera.plume.core.calls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MacAudioTest {

    @Test
    fun fourCcMatchesCoreAudioConstants() {
        // kAudioDeviceTransportTypeBuiltIn = 'bltn' = 0x626C746E (CoreAudio/AudioHardwareBase.h).
        assertEquals(0x626C746E, fourCc("bltn"))
        assertEquals(0x75736220, fourCc("usb "))
    }

    @Test
    fun transportDecidesKind() {
        assertEquals(CallAudioOutput.Kind.Bluetooth, macOutputKind(fourCc("blue")))
        assertEquals(CallAudioOutput.Kind.Bluetooth, macOutputKind(fourCc("blea")))
        assertEquals(CallAudioOutput.Kind.Headset, macOutputKind(fourCc("usb ")))
        assertEquals(CallAudioOutput.Kind.Speaker, macOutputKind(fourCc("bltn")))
        assertEquals(CallAudioOutput.Kind.Speaker, macOutputKind(fourCc("hdmi")))
        assertEquals(CallAudioOutput.Kind.Speaker, macOutputKind(0))
        // Virtuální zařízení (záznam obrazovky, konferenční aplikace) do výběru nepatří.
        assertNull(macOutputKind(fourCc("virt")))
    }
}
