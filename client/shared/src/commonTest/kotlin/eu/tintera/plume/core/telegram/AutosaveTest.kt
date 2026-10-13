package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.settings.AutoDownloadLevel
import eu.tintera.plume.core.settings.limits
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ukládání do galerie: co platí v chatu a zda kvůli tomu stahovat. */
class AutosaveTest {

    private val off = AutosaveScopeSettings(photos = false, videos = false, maxVideoBytes = 100 * MB)
    private val photos = off.copy(photos = true)
    private val everything = AutosaveScopeSettings(photos = true, videos = true, maxVideoBytes = 100 * MB)

    @Test
    fun vyjimkaPrebijeVychoziPravidloDruhu() {
        val settings = AutosaveSettings(
            privateChats = photos,
            groups = off,
            channels = off,
            exceptions = listOf(AutosaveException(chatId = -100, settings = everything)),
        )
        assertEquals(everything, settings.forChat(-100, AutosaveChatType.Group))
        assertEquals(off, settings.forChat(-200, AutosaveChatType.Group))
        assertEquals(photos, settings.forChat(5, AutosaveChatType.Private))
    }

    @Test
    fun fotkaSeStahneJenKdyzSeFotkyStahujiSamy() {
        val photo = candidate(isVideo = false, size = 300_000)
        assertEquals(AutosaveAction.Download, autosaveAction(photo, photos, AutoDownloadLevel.Medium.limits))
        assertEquals(AutosaveAction.WhenDownloaded, autosaveAction(photo, photos, AutoDownloadLevel.Off.limits))
        assertEquals(AutosaveAction.Skip, autosaveAction(photo, off, AutoDownloadLevel.High.limits))
    }

    @Test
    fun videoNadLimitUkladaniSeNeuloziPodLimitemSiteSeStahne() {
        val big = candidate(isVideo = true, size = 200 * MB)
        val wifiSized = candidate(isVideo = true, size = 15 * MB)
        assertEquals(AutosaveAction.Skip, autosaveAction(big, everything, AutoDownloadLevel.High.limits))
        // 15 MB se na Wi-Fi (Naplno, 20 MB) stáhne, na mobilních datech (Běžně, 5 MB) jen počká.
        assertEquals(AutosaveAction.Download, autosaveAction(wifiSized, everything, AutoDownloadLevel.High.limits))
        assertEquals(AutosaveAction.WhenDownloaded, autosaveAction(wifiSized, everything, AutoDownloadLevel.Medium.limits))
        assertEquals(AutosaveAction.Skip, autosaveAction(wifiSized, photos, AutoDownloadLevel.High.limits))
    }

    private fun candidate(isVideo: Boolean, size: Long) = AutosaveCandidate(
        chatId = 1, chatType = AutosaveChatType.Private, fileId = 7, isVideo = isVideo, size = size, date = 0,
    )

    private companion object {
        const val MB = 1024L * 1024
    }
}
