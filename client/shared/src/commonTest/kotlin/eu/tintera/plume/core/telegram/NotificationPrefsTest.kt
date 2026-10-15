package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.ChatNotificationSettings
import dev.g000sha256.tdl.dto.ScopeNotificationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPrefsTest {

    private val now = 1_800_000_000L

    /** Výchozí nastavení skupin: zapnuto, s náhledem, bez upozornění na připnuté, se zmínkami. */
    private val groups = ScopeNotificationSettings(
        muteFor = 0,
        soundId = 0L,
        showPreview = true,
        useDefaultMuteStories = true,
        muteStories = false,
        storySoundId = 0L,
        showStoryPoster = false,
        disablePinnedMessageNotifications = true,
        disableMentionNotifications = false,
    )

    /** Všechno podle vrstvy níž. */
    private val inheriting = defaultChatNotificationSettings()

    private fun topic(own: ChatNotificationSettings, group: ChatNotificationSettings) =
        notificationPrefs(own = own, inherited = group, scopeDefaults = groups, scope = NotificationScope.Groups, nowEpochSeconds = now)

    @Test
    fun chatBezVlastnihoJedePodleRozsahu() {
        val prefs = notificationPrefs(inheriting, null, groups, NotificationScope.Groups, now)
        assertEquals(MuteState.Off, prefs.mute)
        assertTrue(prefs.showPreview)
        assertFalse(prefs.pinnedMessages)
        assertTrue(prefs.mentions)
        assertTrue(prefs.isDefault)
        assertEquals(NotificationDefaults.Scope, prefs.defaults)
    }

    /** Ztlumená skupina ztlumí i téma, které vlastní ztlumení nemá — a sheet to musí ukázat. */
    @Test
    fun temaBereZtlumeniZeSkupiny() {
        val prefs = topic(own = inheriting, group = inheriting.withMuteFor(Int.MAX_VALUE))
        assertEquals(MuteState.Forever, prefs.mute)
        assertTrue(prefs.muteIsDefault)
        assertEquals(NotificationDefaults.Chat, prefs.defaults)
    }

    @Test
    fun vlastniZtlumeniTematuMaPrednostPredSkupinou() {
        val prefs = topic(own = inheriting.withMuteFor(0), group = inheriting.withMuteFor(Int.MAX_VALUE))
        assertEquals(MuteState.Off, prefs.mute)
        assertFalse(prefs.muteIsDefault)
        assertFalse(prefs.isDefault)
    }

    /** Každá volba zvlášť: téma má vlastní náhled, zmínky bere ze skupiny, ta je má vlastní. */
    @Test
    fun kazdaVolbaHledaSvouVrstvuZvlast() {
        val prefs = topic(
            own = inheriting.withOption(ChatNotificationOption.ShowPreview, false),
            group = inheriting.withOption(ChatNotificationOption.Mentions, false),
        )
        assertFalse(prefs.showPreview)
        assertFalse(prefs.mentions)
        // Připnuté nemá žádná vrstva vlastní, takže rozhoduje rozsah.
        assertFalse(prefs.pinnedMessages)
    }

    /** Změna jedné volby ostatní nechá jet podle vrstvy níž. */
    @Test
    fun zmenaVolbyNechaOstatniPodleSkupiny() {
        val own = inheriting.withOption(ChatNotificationOption.PinnedMessages, true)
        assertFalse(own.useDefaultDisablePinnedMessageNotifications)
        assertFalse(own.disablePinnedMessageNotifications)
        assertTrue(own.useDefaultShowPreview)
        assertTrue(own.useDefaultMuteFor)
        assertTrue(own.useDefaultDisableMentionNotifications)
    }

    /** Zvuk: chat bez vlastního zní rozsahem, s vlastním svým — a „Obnovit" pak má smysl. */
    @Test
    fun zvukChatuMaPrednostPredRozsahem() {
        val scope = groups.let {
            ScopeNotificationSettings(
                muteFor = it.muteFor, soundId = 42L, showPreview = it.showPreview,
                useDefaultMuteStories = it.useDefaultMuteStories, muteStories = it.muteStories,
                storySoundId = it.storySoundId, showStoryPoster = it.showStoryPoster,
                disablePinnedMessageNotifications = it.disablePinnedMessageNotifications,
                disableMentionNotifications = it.disableMentionNotifications,
            )
        }
        val inheritingPrefs = notificationPrefs(inheriting, null, scope, NotificationScope.Groups, now)
        assertEquals(42L, inheritingPrefs.soundId)
        assertEquals(42L, inheritingPrefs.defaultSoundId)
        assertTrue(inheritingPrefs.soundIsDefault)

        val own = notificationPrefs(inheriting.withSound(NO_SOUND_ID), null, scope, NotificationScope.Groups, now)
        assertEquals(NO_SOUND_ID, own.soundId)
        assertEquals(42L, own.defaultSoundId)
        assertFalse(own.soundIsDefault)
        assertFalse(own.isDefault)
        // Ostatní volby se zvukem nezměnily.
        assertTrue(own.muteIsDefault)
    }

    /** Návrat k výchozímu zvuku zapne jen `useDefaultSound`, zbytek zůstane. */
    @Test
    fun zvukPodleRozsahuNechaOstatni() {
        val own = inheriting.withMuteFor(0).withSound(7L).withSound(null)
        assertTrue(own.useDefaultSound)
        assertFalse(own.useDefaultMuteFor)
    }

    /** U tématu zvuk „Obnovit" nespouští — vybrat ho tam nejde. */
    @Test
    fun zvukTematuNeniDuvodemKObnoveni() {
        val prefs = topic(own = inheriting.withSound(7L), group = inheriting)
        assertTrue(prefs.isDefault)
        assertEquals(7L, prefs.soundId)
    }
}
