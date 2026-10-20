package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.ScopeNotificationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopeStoriesWorkaroundTest {

    private fun scope(muteFor: Int = 0, preview: Boolean = true, useDefaultStories: Boolean = true, muteStories: Boolean = false, poster: Boolean = true) =
        ScopeNotificationSettings(
            muteFor = muteFor,
            soundId = -1,
            showPreview = preview,
            useDefaultMuteStories = useDefaultStories,
            muteStories = muteStories,
            storySoundId = -1,
            showStoryPoster = poster,
            disablePinnedMessageNotifications = false,
            disableMentionNotifications = false,
        )

    @Test
    fun jenPribehyPotrebujiPostrcit() {
        assertTrue(onlyStoriesDiffer(scope(), scope(useDefaultStories = false, muteStories = true)))
        assertTrue(onlyStoriesDiffer(scope(), scope(poster = false)))
        assertFalse(onlyStoriesDiffer(scope(), scope()), "beze změny se nic nezapisuje navíc")
        assertFalse(onlyStoriesDiffer(scope(), scope(preview = false, poster = false)), "s náhledem TDLib změnu vezme sám")
    }

    @Test
    fun postrceniZtlumeniSeSamoVrati() {
        assertEquals(1, nudgedMuteFor(0), "zapnutý rozsah ztlumí na sekundu — kdyby druhý zápis selhal, vyprší")
        assertEquals(3601, nudgedMuteFor(3600))
        val forever = nudgedMuteFor(Int.MAX_VALUE)
        assertTrue(forever < Int.MAX_VALUE)
        assertEquals(MuteState.Forever, muteState(forever, nowEpochSeconds = 0), "10 let je pořád navždy")
    }
}
