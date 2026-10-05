package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Seskupení seznamu chatů.
 *
 * Skupiny nesou zaoblení bloku, takže prázdná skupina by v seznamu nechala mezeru — a právě
 * hraniční kombinace (samé připnuté, žádné nepřečtené, prázdný seznam) se na zařízení poznají
 * nejhůř.
 */
class ChatGroupsTest {

    /** Uložené zprávy jsou v menu horní lišty; v seznamu jen připnuté. */
    @Test
    fun ulozeneZpravyJenPripnute() {
        val saved = 9L
        assertEquals(
            listOf(listOf(2L, 3L)),
            chatGroups(listOf(chat(2), chat(saved), chat(3)), separateUnread = false, savedMessagesChatId = saved).ids(),
        )
        assertEquals(
            listOf(listOf(saved), listOf(2L)),
            chatGroups(listOf(chat(saved, pinned = true), chat(2)), separateUnread = false, savedMessagesChatId = saved).ids(),
        )
    }

    @Test
    fun bezVycleneniJsouPripnuteAOstatni() {
        val chats = listOf(chat(1, pinned = true), chat(2, unread = 3), chat(3))

        val groups = chatGroups(chats, separateUnread = false)

        assertEquals(listOf(listOf(1L), listOf(2L, 3L)), groups.ids())
    }

    @Test
    fun sVyclenenimJsouTriSkupiny() {
        val chats = listOf(chat(1, pinned = true), chat(2, unread = 3), chat(3))

        val groups = chatGroups(chats, separateUnread = true)

        assertEquals(listOf(listOf(1L), listOf(2L), listOf(3L)), groups.ids())
    }

    /** Připnutý chat patří mezi připnuté, i když je nepřečtený — jinak by byl ve dvou skupinách. */
    @Test
    fun pripnuteNepadajiMeziNeprectene() {
        val chats = listOf(chat(1, pinned = true, unread = 5), chat(2, unread = 1))

        val groups = chatGroups(chats, separateUnread = true)

        assertEquals(listOf(listOf(1L), listOf(2L)), groups.ids())
    }

    /** Prázdná skupina by v seznamu nechala mezeru po ničem. */
    @Test
    fun prazdneSkupinyVypadnou() {
        val chats = listOf(chat(1), chat(2))

        assertEquals(listOf(listOf(1L, 2L)), chatGroups(chats, separateUnread = true).ids())
        assertEquals(listOf(listOf(1L, 2L)), chatGroups(chats, separateUnread = false).ids())
    }

    @Test
    fun prazdnySeznamNemaSkupiny() {
        assertEquals(emptyList<List<Long>>(), chatGroups(emptyList(), separateUnread = true).ids())
    }

    /** Uvnitř skupiny se nic nepřeskládává — pořadí je to od TDLib. */
    @Test
    fun poradiUvnitrSkupinyZustava() {
        val chats = listOf(chat(3, unread = 1), chat(1, unread = 2), chat(2, unread = 1))

        val groups = chatGroups(chats, separateUnread = true)

        assertEquals(listOf(listOf(3L, 1L, 2L)), groups.ids())
    }

    /** Filtr podle druhu pouští jen svůj druh a pořadí nechává. */
    @Test
    fun filtrPodleDruhu() {
        val chats = listOf(
            chat(1, kind = ChatKind.Channel),
            chat(2),
            chat(3, kind = ChatKind.Group),
            chat(4),
        )

        assertEquals(listOf(1L, 2L, 3L, 4L), chats.filteredBy(ChatFilter.All).map { it.id })
        assertEquals(listOf(2L, 4L), chats.filteredBy(ChatFilter.Private).map { it.id })
        assertEquals(listOf(3L), chats.filteredBy(ChatFilter.Groups).map { it.id })
        assertEquals(listOf(1L), chats.filteredBy(ChatFilter.Channels).map { it.id })
    }

    /** Nepřečtené jsou i ručně označené — přesně ty, u kterých svítí odznak. */
    @Test
    fun filtrNeprecteneBereIRucneOznacene() {
        val chats = listOf(chat(1, unread = 2), chat(2), chat(3).copy(isMarkedAsUnread = true))

        assertEquals(listOf(1L, 3L), chats.filteredBy(ChatFilter.Unread).map { it.id })
    }

    /** Lidé, skupiny i kanály: nabízí se všechno. */
    @Test
    fun filtryPodleDruhuJenKdyzJeCoRozlisit() {
        val mixed = listOf(chat(1), chat(2, kind = ChatKind.Group), chat(3, kind = ChatKind.Channel))
        assertEquals(ChatFilter.entries, availableFilters(mixed, ChatFilter.All))

        // Bez kanálů Kanály nejsou.
        assertEquals(
            listOf(ChatFilter.All, ChatFilter.Unread, ChatFilter.Private, ChatFilter.Groups),
            availableFilters(mixed.dropLast(1), ChatFilter.All),
        )

        // Samí lidé: Lidé by byli totéž co Vše.
        assertEquals(listOf(ChatFilter.All, ChatFilter.Unread), availableFilters(listOf(chat(1), chat(2)), ChatFilter.All))
    }

    /** Vybraný filtr pod prstem nezmizí, i když už nemá co ukázat. */
    @Test
    fun vybranyFiltrZustava() {
        val chats = listOf(chat(1), chat(2, kind = ChatKind.Group))

        assertEquals(
            listOf(ChatFilter.All, ChatFilter.Unread, ChatFilter.Private, ChatFilter.Groups, ChatFilter.Channels),
            availableFilters(chats, ChatFilter.Channels),
        )
    }

    /** Nepřipnuté Uložené zprávy v seznamu nejsou, takže Lidé kvůli nim nevzniknou. */
    @Test
    fun ulozeneZpravyNedelajiLidi() {
        val saved = 9L
        val chats = listOf(chat(saved), chat(2, kind = ChatKind.Group))

        assertEquals(listOf(ChatFilter.All, ChatFilter.Unread), availableFilters(chats, ChatFilter.All, saved))
        assertEquals(
            listOf(ChatFilter.All, ChatFilter.Unread, ChatFilter.Private, ChatFilter.Groups),
            availableFilters(chats.map { if (it.id == saved) it.copy(isPinned = true) else it }, ChatFilter.All, saved),
        )
    }

    /** Než se seznam načte, nabízí se jen to, co je vždycky. */
    @Test
    fun behemNacitaniJenVseANeprectene() {
        assertEquals(listOf(ChatFilter.All, ChatFilter.Unread), availableFilters(null, ChatFilter.All))
    }

    private fun List<List<ChatSummary>>.ids(): List<List<Long>> = map { group -> group.map { it.id } }

    @Test
    fun sponzorovanyChatJeNahoreZvlastNadPripnutymi() {
        val sponsored = chat(4).copy(sponsor = ChatSponsor.Proxy)
        val pinned = chat(1, pinned = true)
        val rest = chat(2)
        assertEquals(
            listOf(listOf(sponsored), listOf(pinned), listOf(rest)),
            chatGroups(listOf(pinned, sponsored, rest), separateUnread = false),
        )
    }

    private fun chat(
        id: Long,
        unread: Int = 0,
        pinned: Boolean = false,
        kind: ChatKind = ChatKind.Private,
    ) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = unread,
        lastMessagePreview = null,
        isPinned = pinned,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = kind,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )
}
