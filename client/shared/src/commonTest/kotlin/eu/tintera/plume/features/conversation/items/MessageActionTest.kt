package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.resources.menu_summarize_voice
import eu.tintera.plume.core.ai.VOICE_SUMMARY_MIN_SECONDS
import eu.tintera.plume.resources.menu_explain
import eu.tintera.plume.core.ai.EXPLAIN_MIN_CHARS
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.files.copiesAnyFile
import eu.tintera.plume.core.files.fileShareSupported
import eu.tintera.plume.core.files.saveAsSupported
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.features.conversation.MessageMenu
import eu.tintera.plume.features.conversation.replyTarget
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.menu_bookmark_add
import eu.tintera.plume.resources.menu_bookmark_remove
import eu.tintera.plume.features.conversation.ContextFinding
import eu.tintera.plume.features.conversation.ParcelCarrier
import eu.tintera.plume.resources.menu_context_actions
import eu.tintera.plume.resources.message_context_code_copy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kandidáti do nabídky podle toho, co zpráva JE, a jejich filtr podle práv od serveru. */
class MessageActionTest {

    private fun message(
        text: String = "ahoj",
        media: MessageMedia? = null,
        entities: List<MessageEntity> = emptyList(),
        outgoing: Boolean = true,
    ) = ChatMessage(
        id = 1L,
        isOutgoing = outgoing,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = text,
        entities = entities,
        media = media,
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = null,
        isService = false,
        timestamp = 0,
        isEdited = false,
    )

    private val photo = MessageMedia.Photo(fileId = 1, thumbnailFileId = null, fullFileId = 2, width = 10, height = 10)

    private val sticker = MessageMedia.Sticker(
        stickerFileId = 1,
        setId = 5L,
        fileId = 1,
        animationFileId = null,
        thumbnailFileId = null,
        width = 512,
        height = 512,
        emoji = "🙂",
    )

    private val all = MessageActions(
        canReply = true,
        canCopy = true,
        canDeleteForSelf = true,
        canDeleteForAll = true,
        canEdit = true,
        canPin = true,
        canForward = true,
        canSave = true,
        canReport = true,
    )

    private fun menu(
        items: List<MessageMenuAction>,
        actions: MessageActions = all,
        favoriteSticker: Boolean? = null,
    ) = MessageMenu(
        messageId = 1L,
        timestamp = 0,
        isOutgoing = true,
        emoji = emptyList(),
        chosenEmoji = emptySet(),
        actions = actions,
        items = items,
        favoriteSticker = favoriteSticker,
    )

    /** Podržená fotka uprostřed alba: odpověď míří na ni, ne na zprávu s popiskem, které patří blok. */
    @Test
    fun odpovedMiriNaPodrzenouFotkuAlba() {
        assertEquals(1L, menu(items = emptyList()).replyTarget)
        assertEquals(3L, menu(items = emptyList()).copy(pressedMessageId = 3L).replyTarget)
    }

    @Test
    fun textNabiziUpravuAKopirovaniVPoradiNabidky() {
        assertEquals(
            listOf(
                MessageMenuAction.Resend,
                MessageMenuAction.Edit,
                MessageMenuAction.Reply,
                MessageMenuAction.Copy,
                MessageMenuAction.Forward,
                MessageMenuAction.Translate,
                MessageMenuAction.ContextActions,
                MessageMenuAction.SelectText,
                MessageMenuAction.Info,
                MessageMenuAction.ToggleBookmark,
                MessageMenuAction.Remind,
                MessageMenuAction.Pin,
                MessageMenuAction.Select,
                MessageMenuAction.Report,
                MessageMenuAction.BlockFromReplies,
                MessageMenuAction.Delete,
            ),
            menuActions(message(), isPinned = false),
        )
    }

    /** Vysvětlit jen dlouhou CIZÍ zprávu, a ukáže se jen s AI v telefonu. */
    @Test
    fun vysvetlitJenDlouhouPrichoziZpravu() {
        val long = "a".repeat(EXPLAIN_MIN_CHARS)
        assertTrue(MessageMenuAction.Explain in menuActions(message(text = long, outgoing = false), isPinned = false))
        assertFalse(MessageMenuAction.Explain in menuActions(message(text = long), isPinned = false))
        assertFalse(MessageMenuAction.Explain in menuActions(message(text = long.drop(1), outgoing = false), isPinned = false))

        val items = listOf(MessageMenuAction.Explain)
        assertNull(MessageMenuAction.Explain.presentation(menu(items)))
        assertEquals(
            Res.string.menu_explain,
            MessageMenuAction.Explain.presentation(menu(items).copy(explainAvailable = true))?.label,
        )
    }

    /** Shrnout přepis jen u DLOUHÉHO CIZÍHO záznamu, a ukáže se jen s hotovým přepisem a AI. */
    @Test
    fun shrnoutPrepisJenDlouhehoPrichozihoZaznamu() {
        fun voice(seconds: Int) = MessageMedia.VoiceNote(fileId = 1, durationSeconds = seconds, waveform = emptyList(), isListened = true)
        val long = message(text = "", media = voice(VOICE_SUMMARY_MIN_SECONDS), outgoing = false)
        assertTrue(MessageMenuAction.SummarizeVoice in menuActions(long, isPinned = false))
        assertFalse(MessageMenuAction.SummarizeVoice in menuActions(long.copy(isOutgoing = true), isPinned = false))
        assertFalse(
            MessageMenuAction.SummarizeVoice in
                menuActions(message(text = "", media = voice(VOICE_SUMMARY_MIN_SECONDS - 1), outgoing = false), isPinned = false),
        )
        // Dlouhý TEXT kandidátem na shrnutí přepisu není — na ten je „Vysvětlit".
        assertFalse(MessageMenuAction.SummarizeVoice in menuActions(message(text = "a".repeat(EXPLAIN_MIN_CHARS), outgoing = false), isPinned = false))

        val items = listOf(MessageMenuAction.SummarizeVoice)
        assertNull(MessageMenuAction.SummarizeVoice.presentation(menu(items)))
        assertEquals(
            Res.string.menu_summarize_voice,
            MessageMenuAction.SummarizeVoice.presentation(menu(items).copy(voiceSummaryAvailable = true))?.label,
        )
    }

    /** Akce z textu: položka pro každou akci, hodnota jen u stejného druhu, zbytek za „Akce ze zprávy…". */
    @Test
    fun akceZeZpravyPoPolozkach() {
        val parcel = ContextFinding.Parcel(ParcelCarrier.Packeta, "Z1234567890")
        val payment = ContextFinding.Payment(iban = "CZ6508000000192000145399", account = "19-2000145399/0800")
        val code = ContextFinding.Code("LETO20")
        assertEquals(
            listOf(
                MessageMenuAction.ContextItem(ContextVerb.TrackParcel, 0),
                MessageMenuAction.ContextItem(ContextVerb.Payment, 1),
                MessageMenuAction.ContextItem(ContextVerb.CopyCode, 2),
            ),
            contextMenuItems(listOf(parcel, payment, code), canJoinWifi = true),
        )
        // Dvě zásilky: popisek nese číslo, ať se dají rozlišit.
        val second = parcel.copy(number = "Z0000000000")
        assertEquals(
            listOf("Z1234567890", "Z0000000000"),
            contextMenuItems(listOf(parcel, second), canJoinWifi = true).map { (it as MessageMenuAction.ContextItem).value },
        )
        // Víc než tři: dvě a „Akce ze zprávy…" — skupina na první úrovni nabídky má nejvýš tři řádky.
        val many = contextMenuItems(listOf(parcel, payment, code, ContextFinding.Booking("XKQ7ZT"), ContextFinding.Code("ABCD12")), canJoinWifi = true)
        assertEquals(3, many.size)
        assertEquals(MessageMenuAction.ContextActions, many.last())
        // Wi-Fi bez možnosti připojit (iOS) otevře sheet s heslem.
        assertEquals(
            ContextVerb.Wifi,
            (contextMenuItems(listOf(ContextFinding.Wifi("Doma", "tajne1234")), canJoinWifi = false).single() as MessageMenuAction.ContextItem).verb,
        )
    }

    @Test
    fun akceZeZpravyJenSeSvolenymObsahem() {
        val item = MessageMenuAction.ContextItem(ContextVerb.CopyCode, 0)
        val menu = menu(listOf(item)).copy(contextFindings = listOf(ContextFinding.Code("LETO20")))
        assertEquals(Res.string.message_context_code_copy, item.presentation(menu)?.label)
        // Chráněný obsah ven nesmí ani do schránky.
        assertNull(item.presentation(menu.copy(actions = all.copy(canCopy = false))))
        assertEquals(
            Res.string.menu_context_actions,
            MessageMenuAction.ContextActions.presentation(menu)?.label,
        )
    }

    @Test
    fun zalozkaSePrepinaPodleStavuANeznamyStavSeNenabizi() {
        val items = listOf(MessageMenuAction.ToggleBookmark)
        // Bez záložek (bubliny, testy bez MessageBookmarks) se položka neukáže vůbec.
        assertNull(MessageMenuAction.ToggleBookmark.presentation(menu(items)))
        assertEquals(
            Res.string.menu_bookmark_add,
            MessageMenuAction.ToggleBookmark.presentation(menu(items).copy(bookmarked = false))?.label,
        )
        assertEquals(
            Res.string.menu_bookmark_remove,
            MessageMenuAction.ToggleBookmark.presentation(menu(items).copy(bookmarked = true))?.label,
        )
    }

    @Test
    fun pripnutaZpravaSeOdepina() {
        val items = menuActions(message(), isPinned = true)
        assertTrue(MessageMenuAction.Unpin in items)
        assertFalse(MessageMenuAction.Pin in items)
    }

    @Test
    fun fotkaBezPopiskuNemaCoKopirovat() {
        val items = menuActions(message(text = "", media = photo), isPinned = false)
        assertFalse(MessageMenuAction.Copy in items)
        // Upravit jde i tak — popisek se tím k fotce teprve přidá.
        assertTrue(MessageMenuAction.Edit in items)
    }

    @Test
    fun popisekFotkySeUpravuje() {
        val items = menuActions(message(text = "popisek", media = photo), isPinned = false)
        assertTrue(MessageMenuAction.Copy in items)
        assertTrue(MessageMenuAction.Edit in items)
    }

    @Test
    fun rucniFormatovaniPopiskuUpravuDovoli() {
        // Pole formátování nese, takže tučné v popisku úprava nesmaže.
        val bold = MessageEntity(offset = 0, length = 4, type = MessageEntityType.Bold)
        val items = menuActions(message(text = "tučný popisek", media = photo, entities = listOf(bold)), isPinned = false)
        assertTrue(MessageMenuAction.Edit in items)
    }

    @Test
    fun citaceVTextuUpravuDovoli() {
        val quote = MessageEntity(offset = 0, length = 4, type = MessageEntityType.Quote(expandable = false))
        assertTrue(MessageMenuAction.Edit in menuActions(message(entities = listOf(quote)), isPinned = false))
    }

    @Test
    fun odkazSPopiskemUpravuDovoli() {
        // Adresu za popiskem nese pole jako formát odkazu.
        val link = MessageEntity(offset = 0, length = 4, type = MessageEntityType.Link("https://example.com"))
        assertTrue(MessageMenuAction.Edit in menuActions(message(entities = listOf(link)), isPinned = false))
    }

    @Test
    fun zminkaJmenemUpravuVylucuje() {
        // Id uživatele za jménem pole nenese, úprava by ze zmínky udělala holý text.
        val mention = MessageEntity(offset = 0, length = 4, type = MessageEntityType.MentionUser(42))
        assertFalse(MessageMenuAction.Edit in menuActions(message(entities = listOf(mention)), isPinned = false))
    }

    @Test
    fun samolepkaPopisekNema() {
        // Samolepka ani animované emoji popisek nést nemůžou; „Upravit" by slibovalo chybu.
        assertFalse(MessageMenuAction.Edit in menuActions(message(text = "", media = sticker), isPinned = false))
    }

    @Test
    fun rucniFormatovaniUpravuDovoli() {
        val bold = MessageEntity(offset = 0, length = 4, type = MessageEntityType.Bold)
        assertTrue(MessageMenuAction.Edit in menuActions(message(entities = listOf(bold)), isPinned = false))
    }

    @Test
    fun samolepkaJdeDoOblibenych() {
        assertTrue(MessageMenuAction.ToggleFavoriteSticker in menuActions(message(text = "", media = sticker), isPinned = false))
        assertFalse(MessageMenuAction.ToggleFavoriteSticker in menuActions(message(), isPinned = false))
    }

    @Test
    fun souborJdeSdiletAUlozit() {
        val document = MessageMedia.Document(
            fileId = 7,
            fileName = "smlouva.pdf",
            mimeType = "application/pdf",
            sizeBytes = 1024,
            thumbnailFileId = null,
        )
        val items = menuActions(message(text = "", media = document), isPinned = false)
        // Sdílet jen se systémovým dialogem; desktop místo něj kopíruje do schránky.
        assertEquals(fileShareSupported, MessageMenuAction.ShareFile in items)
        assertEquals(copiesAnyFile, MessageMenuAction.CopyMedia(MessageMenuAction.CopyMedia.Kind.File) in items)
        // „Uložit jako…" jen tam, kde ho platforma má; na iOS ho nahrazuje sdílení.
        assertEquals(saveAsSupported, MessageMenuAction.SaveFileAs in items)
        // Ven jdou za přeposláním, před informacemi.
        val exports = listOf(MessageMenuAction.CopyMedia(MessageMenuAction.CopyMedia.Kind.File), MessageMenuAction.ShareFile)
        assertTrue(items[items.indexOf(MessageMenuAction.Forward) + 1] in exports)

        assertFalse(MessageMenuAction.ShareFile in menuActions(message(), isPinned = false))
        assertFalse(MessageMenuAction.ShareFile in menuActions(message(text = "", media = photo), isPinned = false))
    }

    @Test
    fun fotkaJdeDoSchranky() {
        val items = menuActions(message(text = "", media = photo), isPinned = false)
        assertTrue(MessageMenuAction.CopyMedia(MessageMenuAction.CopyMedia.Kind.Photo) in items)
        // „Uložit jako…" u fotky jen bez dialogu sdílení (desktop); na telefonu je cesta ven z prohlížeče.
        assertEquals(saveAsSupported && !fileShareSupported, MessageMenuAction.SaveFileAs in items)

        val video = MessageMedia.Video(fileId = 3, thumbnailFileId = null, width = 10, height = 10, durationSeconds = 5)
        val videoItems = menuActions(message(text = "", media = video), isPinned = false)
        assertEquals(copiesAnyFile, MessageMenuAction.CopyMedia(MessageMenuAction.CopyMedia.Kind.Video) in videoItems)
        assertFalse(videoItems.any { it is MessageMenuAction.CopyMedia && it.kind != MessageMenuAction.CopyMedia.Kind.Video })
    }

    @Test
    fun skladbaJdeSdiletJakoSoubor() {
        val audio = MessageMedia.Audio(
            fileId = 8,
            title = "Pírko",
            performer = "Plume Band",
            fileName = "pirko.mp3",
            mimeType = "audio/mpeg",
            durationSeconds = 180,
            sizeBytes = 4096,
        )
        assertEquals(fileShareSupported, MessageMenuAction.ShareFile in menuActions(message(text = "", media = audio), isPinned = false))
        // Sdílení a uložení jdou cestou dokumentu — se jménem a typem souboru, ne názvem skladby.
        val document = audio.asDocument()
        assertEquals("pirko.mp3", document.fileName)
        assertEquals("audio/mpeg", document.mimeType)
        assertEquals(8, document.fileId)
    }

    @Test
    fun chranenySouborVenNesmi() {
        val protected = all.copy(canSave = false)
        val items = listOf(
            MessageMenuAction.ShareFile,
            MessageMenuAction.SaveFileAs,
            MessageMenuAction.CopyMedia(MessageMenuAction.CopyMedia.Kind.Photo),
        )
        assertTrue(items.none { it.presentation(menu(items, actions = protected)) != null })
        assertTrue(items.all { it.presentation(menu(items)) != null })
    }

    /** „Poslat znovu" je kandidát u každé zprávy, ukáže se jen u té, kterou poslat znovu jde. */
    @Test
    fun poslatZnovuJenUNeodeslaneZpravy() {
        val items = menuActions(message(), isPinned = false)
        assertNull(MessageMenuAction.Resend.presentation(menu(items)))
        assertNotNull(MessageMenuAction.Resend.presentation(menu(items).copy(resendable = true)))
    }

    @Test
    fun coServerNedovoliSeNenabizi() {
        val items = menuActions(message(), isPinned = false)
        val shown = items.filter { it.presentation(menu(items, actions = MessageActions.Fallback)) != null }
        // Bez příznaků od serveru zbude jen to neškodné: odpověď, kopírování, výběr textu
        // (což je kopírování po kouskách), překlad (text ven jinou řečí — stojí na témže právu),
        // informace a vstup do výběru — ten na právech nestojí, co výběr dovolí, se zjistí až
        // u jeho akcí.
        assertEquals(
            listOf(
                MessageMenuAction.Reply,
                MessageMenuAction.Copy,
                MessageMenuAction.Translate,
                MessageMenuAction.SelectText,
                MessageMenuAction.Info,
                MessageMenuAction.Select,
            ),
            shown,
        )
    }

    @Test
    fun oblibenostRozhodujePopisek() {
        val action = MessageMenuAction.ToggleFavoriteSticker
        // Nezjištěná oblíbenost = položka se nenabízí; jinak „přidat" a „odebrat" jsou dvě různé.
        assertNull(action.presentation(menu(listOf(action), favoriteSticker = null)))
        val add = action.presentation(menu(listOf(action), favoriteSticker = false))?.label
        val remove = action.presentation(menu(listOf(action), favoriteSticker = true))?.label
        assertTrue(add != null && remove != null && add != remove)
    }

    @Test
    fun vyberTextuJdeVzdyRukuVRuceSKopirovanim() {
        // Bez textu není co vybírat — stejná podmínka jako u kopírování.
        val bezTextu = menuActions(message(text = ""), isPinned = false)
        assertTrue(MessageMenuAction.Copy !in bezTextu && MessageMenuAction.SelectText !in bezTextu)
        assertTrue(MessageMenuAction.Translate !in bezTextu)

        // A chráněný obsah ven nesmí ani po kouskách: obojí stojí na `canCopy`.
        val items = menuActions(message(), isPinned = false)
        val chraneny = menu(items, actions = MessageActions.Fallback.copy(canCopy = false))
        assertNull(MessageMenuAction.Copy.presentation(chraneny))
        assertNull(MessageMenuAction.SelectText.presentation(chraneny))
        // Překlad by chráněný text vynesl jinou řečí.
        assertNull(MessageMenuAction.Translate.presentation(chraneny))
    }
}
