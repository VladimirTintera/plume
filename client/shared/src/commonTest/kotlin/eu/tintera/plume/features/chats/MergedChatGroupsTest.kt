package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Společný seznam chatů víc účtů (režim Vše): pořadí skupin a řazení mezi účty. Položky jsou
 * dvojice „pořadí účtu : id chatu", tak jak je nese klíč řádku.
 */
class MergedChatGroupsTest {

    /** `order` je číslo uvnitř účtu; mezi účty rozhoduje datum poslední zprávy. */
    @Test
    fun ostatniPodleDataPosledniZpravy() {
        val first = MergeSource(listOf(chat(1, time = 100, order = 900), chat(2, time = 300, order = 800)))
        val second = MergeSource(listOf(chat(3, time = 200, order = 1)))

        assertEquals(listOf(listOf("0:2", "1:3", "0:1")), mergedChatGroups(listOf(first, second)).keys())
    }

    /** Připnuté všech účtů nahoře, podle pořadí účtů a uvnitř účtu, jak je dal TDLib. */
    @Test
    fun pripnuteVsechUctuNahore() {
        val first = MergeSource(listOf(chat(1, pinned = true, time = 10), chat(2, pinned = true, time = 500), chat(3, time = 400)))
        val second = MergeSource(listOf(chat(4, pinned = true, time = 900), chat(5, time = 600)))

        assertEquals(
            listOf(listOf("0:1", "0:2", "1:4"), listOf("1:5", "0:3")),
            mergedChatGroups(listOf(first, second)).keys(),
        )
    }

    /**
     * Soukromý chat (i s botem, třeba Notifikátor) a obyčejná skupina jsou v každém účtu jiná
     * konverzace: dvakrát, s jiným klíčem.
     */
    @Test
    fun stejnyChatVeDvouUctechDvakrat() {
        val groups = mergedChatGroups(listOf(MergeSource(listOf(chat(7, time = 1))), MergeSource(listOf(chat(7, time = 2)))))

        assertEquals(listOf(listOf("1:7", "0:7")), groups.keys())
    }

    /** Sponzorovaný a vynořený odložený chat nad připnutými, jako v seznamu jednoho účtu. */
    @Test
    fun sponzorovanyAVynorenyNadPripnutymi() {
        val sponsored = chat(1).copy(sponsor = ChatSponsor.Proxy)
        val first = MergeSource(listOf(sponsored, chat(2, pinned = true)))
        val second = MergeSource(listOf(chat(3, time = 5), chat(4, time = 6)), due = setOf(3))

        assertEquals(
            listOf(listOf("0:1"), listOf("1:3"), listOf("0:2"), listOf("1:4")),
            mergedChatGroups(listOf(first, second)).keys(),
        )
    }

    /** Chat bez zprávy (čerstvě založený) až na konec, ne nahoru. */
    @Test
    fun chatBezZpravyNaKonci() {
        val groups = mergedChatGroups(listOf(MergeSource(listOf(chat(1, time = null))), MergeSource(listOf(chat(2, time = 3)))))

        assertEquals(listOf(listOf("1:2", "0:1")), groups.keys())
    }

    /** Uložené zprávy jen připnuté, jako v [chatGroups]. */
    @Test
    fun skryteUlozeneZpravy() {
        val groups = mergedChatGroups(listOf(MergeSource(listOf(chat(9, time = 50), chat(2, time = 1)), hiddenSavedMessagesChatId = 9)))

        assertEquals(listOf(listOf("0:2")), groups.keys())
    }

    /** Obyčejná skupina (`-id`) má v každém účtu jiná id zpráv: nespojuje se. */
    @Test
    fun obycejnaSkupinaSeNespojuje() {
        val groups = mergedChatGroups(listOf(MergeSource(listOf(chat(-42, time = 1))), MergeSource(listOf(chat(-42, time = 1)))))

        assertEquals(listOf(listOf("0:-42", "1:-42")), groups.keys())
    }

    /** Společná supergroupa nebo kanál je jednou, s odznaky obou účtů a otevírá se prvním. */
    @Test
    fun spolecnaSupergroupaJednou() {
        val groups = mergedChatGroups(
            listOf(
                MergeSource(listOf(chat(SUPERGROUP, time = 5), chat(1, time = 9))),
                MergeSource(listOf(chat(SUPERGROUP, time = 5), chat(2, time = 1))),
            ),
        )

        assertEquals(listOf(listOf("0:1", "shared:$SUPERGROUP", "1:2")), groups.keys())
        val merged = groups.flatten().single { it.alsoIn.isNotEmpty() }
        assertEquals(listOf(0, 1), merged.sources)
    }

    /** Kterým účtem se do skupiny psalo naposledy, tím se otevře; místo v seznamu se nemění. */
    @Test
    fun otevreSeUctemZPreference() {
        val sources = listOf(MergeSource(listOf(chat(SUPERGROUP, time = 5))), MergeSource(listOf(chat(SUPERGROUP, time = 5))))

        val merged = mergedChatGroups(sources, preferred = mapOf(SUPERGROUP to 1)).flatten().single()

        assertEquals(1, merged.source)
        assertEquals(listOf(1, 0), merged.sources)
        assertEquals("shared:$SUPERGROUP", merged.key)
    }

    /** Připnutá jen v druhém účtu stojí mezi připnutými a bez preference se otevře jím. */
    @Test
    fun pripnutaVJednomUctuMeziPripnutymi() {
        val groups = mergedChatGroups(
            listOf(
                MergeSource(listOf(chat(SUPERGROUP, time = 5), chat(1, time = 9))),
                MergeSource(listOf(chat(SUPERGROUP, pinned = true, time = 5))),
            ),
        )

        assertEquals(listOf(listOf("shared:$SUPERGROUP"), listOf("0:1")), groups.keys())
        val merged = groups.first().single()
        assertEquals(1, merged.source)
        assertEquals(true, merged.chat.isPinned)
    }

    /** Nepřečtené jsou tytéž zprávy: nejvyšší z účtů, ne součet. */
    /** Otevírá-li se podle preference účtem, kde připnutá není, připínáček i místo zůstanou. */
    @Test
    fun pripnutaPodlePreferenceZustavaNahore() {
        val groups = mergedChatGroups(
            listOf(MergeSource(listOf(chat(SUPERGROUP, time = 5), chat(1, time = 9))), MergeSource(listOf(chat(SUPERGROUP, pinned = true, time = 5)))),
            preferred = mapOf(SUPERGROUP to 0),
        )

        val merged = groups.first().single()
        assertEquals(0, merged.source)
        assertEquals(true, merged.chat.isPinned)
    }

    @Test
    fun neprectenePodleNejvyssiho() {
        val first = chat(SUPERGROUP, time = 5).copy(unreadCount = 3, unreadMentionCount = 0)
        val second = chat(SUPERGROUP, time = 5).copy(unreadCount = 7, unreadMentionCount = 1)

        val merged = mergedChatGroups(listOf(MergeSource(listOf(first)), MergeSource(listOf(second)))).flatten().single()

        assertEquals(7, merged.chat.unreadCount)
        assertEquals(1, merged.chat.unreadMentionCount)
    }

    /** Ve společném seznamu jen jednoho účtu se supergroupa nespojuje s ničím a klíč má účet. */
    @Test
    fun supergroupaJenVJednomUctu() {
        val groups = mergedChatGroups(listOf(MergeSource(listOf(chat(SUPERGROUP, time = 5))), MergeSource(emptyList())))

        assertEquals(listOf(listOf("0:$SUPERGROUP")), groups.keys())
    }

    @Test
    fun prazdnySeznam() {
        assertEquals(emptyList(), mergedChatGroups(listOf(MergeSource(emptyList()), MergeSource(emptyList()))))
    }

    private companion object {
        /** Id supergroupy, jak ho dává TDLib (`-100…`). */
        const val SUPERGROUP = -1_001_234_567_890L
    }

    private fun List<List<MergedChat>>.keys() = map { group -> group.map { it.key } }

    private fun chat(id: Long, pinned: Boolean = false, time: Int? = 0, order: Long = id) = ChatSummary(
        id = id,
        title = "Chat $id",
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = pinned,
        order = order,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = ChatKind.Private,
        lastMessageTimestamp = time,
        presence = null,
        activity = null,
        draft = null,
    )
}
