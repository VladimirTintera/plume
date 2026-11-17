package eu.tintera.plume.core.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundDataVerdictTest {

    private val blockedHighPriority = BackgroundNetworkSnapshot(
        inForeground = false,
        metered = true,
        blocked = true,
        dataSaverBlocks = false,
        highPriority = true,
        batterySaver = false,
        backgroundRestricted = false,
        restrictedBucket = false,
    )

    @Test
    fun zablokovanaBezJinePricinyJsouVypnutaData() {
        assertEquals(BackgroundDataVerdict.Restricted, backgroundDataVerdict(blockedHighPriority))
    }

    @Test
    fun pruchoziMerenaSitZnamenaPovolenaData() {
        assertEquals(BackgroundDataVerdict.Allowed, backgroundDataVerdict(blockedHighPriority.copy(blocked = false)))
    }

    @Test
    fun poprediANemerenaSitNicNerikaji() {
        assertEquals(BackgroundDataVerdict.Unknown, backgroundDataVerdict(blockedHighPriority.copy(inForeground = true)))
        assertEquals(BackgroundDataVerdict.Unknown, backgroundDataVerdict(blockedHighPriority.copy(metered = false)))
    }

    @Test
    fun pushSeSnizenouPrioritouVyjimkuNemaABlokovaniNicNerika() {
        assertEquals(BackgroundDataVerdict.Unknown, backgroundDataVerdict(blockedHighPriority.copy(highPriority = false)))
    }

    @Test
    fun omezeniBaterieDelajiBlokovaniNejednoznacnym() {
        assertEquals(BackgroundDataVerdict.Unknown, backgroundDataVerdict(blockedHighPriority.copy(batterySaver = true)))
        assertEquals(BackgroundDataVerdict.Unknown, backgroundDataVerdict(blockedHighPriority.copy(backgroundRestricted = true)))
        assertEquals(BackgroundDataVerdict.Unknown, backgroundDataVerdict(blockedHighPriority.copy(restrictedBucket = true)))
    }

    @Test
    fun sporicDatBezVyjimkyBlokujeVzdycky() {
        val snapshot = blockedHighPriority.copy(dataSaverBlocks = true, highPriority = false, batterySaver = true)
        assertEquals(BackgroundDataVerdict.Restricted, backgroundDataVerdict(snapshot))
    }
}
