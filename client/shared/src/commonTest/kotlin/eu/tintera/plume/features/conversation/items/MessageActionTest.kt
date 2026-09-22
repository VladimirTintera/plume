package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.files.saveAsSupported
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.features.conversation.MessageMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kandidáti do nabídky podle toho, co zpráva JE, a jejich filtr podle práv od serveru. */
class MessageActionTest {

    private fun message(
        text: String = "ahoj",
        media: MessageMedia? = null,
        entities: List<MessageEntity> = emptyList(),
    ) = ChatMessage(
        id = 1L,
        isOutgoing = true,
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

    @Test
    fun textNabiziUpravuAKopirovaniVPoradiNabidky() {
        assertEquals(
            listOf(
                MessageMenuAction.Reply,
                MessageMenuAction.Edit,
                MessageMenuAction.Copy,
                MessageMenuAction.SelectText,
                MessageMenuAction.Forward,
                MessageMenuAction.Info,
                MessageMenuAction.Pin,
                MessageMenuAction.Select,
                MessageMenuAction.Delete,
            ),
            menuActions(message(), isPinned = false),
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
    fun rucniFormatovaniVylucujeIUpravuPopisku() {
        val bold = MessageEntity(offset = 0, length = 4, type = MessageEntityType.Bold)
        val items = menuActions(message(text = "tučný popisek", media = photo, entities = listOf(bold)), isPinned = false)
        assertFalse(MessageMenuAction.Edit in items)
    }

    @Test
    fun citaceVTextuUpravuVylucuje() {
        // Pole umí jen holý text, takže by úprava citaci smazala.
        val quote = MessageEntity(offset = 0, length = 4, type = MessageEntityType.Quote(expandable = false))
        assertFalse(MessageMenuAction.Edit in menuActions(message(entities = listOf(quote)), isPinned = false))
    }

    @Test
    fun samolepkaPopisekNema() {
        // Samolepka ani animované emoji popisek nést nemůžou; „Upravit" by slibovalo chybu.
        assertFalse(MessageMenuAction.Edit in menuActions(message(text = "", media = sticker), isPinned = false))
    }

    @Test
    fun rucniFormatovaniUpravuVylucuje() {
        val bold = MessageEntity(offset = 0, length = 4, type = MessageEntityType.Bold)
        assertFalse(MessageMenuAction.Edit in menuActions(message(entities = listOf(bold)), isPinned = false))
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
        assertTrue(MessageMenuAction.ShareFile in items)
        // „Uložit jako…" jen tam, kde ho platforma má; na iOS ho nahrazuje sdílení.
        assertEquals(saveAsSupported, MessageMenuAction.SaveFileAs in items)
        // Ven jdou za přeposláním, před informacemi.
        assertEquals(items.indexOf(MessageMenuAction.Forward) + 1, items.indexOf(MessageMenuAction.ShareFile))

        assertFalse(MessageMenuAction.ShareFile in menuActions(message(), isPinned = false))
        assertFalse(MessageMenuAction.ShareFile in menuActions(message(text = "", media = photo), isPinned = false))
    }

    @Test
    fun chranenySouborVenNesmi() {
        val protected = all.copy(canSave = false)
        val items = listOf(MessageMenuAction.ShareFile, MessageMenuAction.SaveFileAs)
        assertTrue(items.none { it.presentation(menu(items, actions = protected)) != null })
        assertTrue(items.all { it.presentation(menu(items)) != null })
    }

    @Test
    fun coServerNedovoliSeNenabizi() {
        val items = menuActions(message(), isPinned = false)
        val shown = items.filter { it.presentation(menu(items, actions = MessageActions.Fallback)) != null }
        // Bez příznaků od serveru zbude jen to neškodné: odpověď, kopírování, výběr textu
        // (což je kopírování po kouskách), informace a vstup do výběru — ten na právech
        // nestojí, co výběr dovolí, se zjistí až u jeho akcí.
        assertEquals(
            listOf(
                MessageMenuAction.Reply,
                MessageMenuAction.Copy,
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

        // A chráněný obsah ven nesmí ani po kouskách: obojí stojí na `canCopy`.
        val items = menuActions(message(), isPinned = false)
        val chraneny = menu(items, actions = MessageActions.Fallback.copy(canCopy = false))
        assertNull(MessageMenuAction.Copy.presentation(chraneny))
        assertNull(MessageMenuAction.SelectText.presentation(chraneny))
    }
}
