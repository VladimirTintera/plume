package eu.tintera.plume.features.calls

import eu.tintera.plume.core.telegram.CallHistoryEntry
import eu.tintera.plume.core.telegram.CallOutcome
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MergedCallsTest {

    private fun entry(chatId: Long, messageId: Long, date: Int) =
        CallHistoryEntry(
            chat = ChatSummary(
                id = chatId,
                title = "Člověk $chatId",
                unreadCount = 0,
                lastMessagePreview = null,
                isPinned = false,
                order = chatId,
                avatarFileId = null,
                isMuted = false,
                isMarkedAsUnread = false,
                kind = ChatKind.Private,
                lastMessageTimestamp = null,
                presence = null,
                activity = null,
                draft = null,
            ),
            messageId = messageId,
            outcome = CallOutcome.Missed,
            isVideo = false,
            durationSeconds = 0,
            isGroup = false,
            date = date,
            callUserId = chatId,
        )

    /** Poledne UTC: o pár minut sem a tam je to tentýž den v každém obvyklém pásmu. */
    private val noon = 1_700_049_600

    private fun MergedCallsState.ids() = calls?.map { it.source to it.entry.messageId }

    @Test
    fun dokudNemaKazdyUcetPrvniStrankuNeniCoUkazat() {
        val merged = mergeCallStates(listOf(CallsState(entries = listOf(entry(1, 1, noon))), CallsState()))
        assertNull(merged.calls)
    }

    @Test
    fun jedenUcetUkazeVsechnoNactene() {
        val merged = mergeCallStates(listOf(CallsState(entries = listOf(entry(1, 2, noon + 10), entry(1, 1, noon)))))
        assertEquals(listOf(0 to 2L, 0 to 1L), merged.ids())
        assertEquals(listOf(0), merged.needMore)
    }

    @Test
    fun hovoryUctuSeRadiPodleData() {
        val merged = mergeCallStates(
            listOf(
                CallsState(entries = listOf(entry(1, 5, noon + 50), entry(1, 3, noon + 30)), endReached = true),
                CallsState(entries = listOf(entry(1, 4, noon + 40), entry(1, 2, noon + 20)), endReached = true),
            ),
        )
        assertEquals(listOf(0 to 5L, 1 to 4L, 0 to 3L, 1 to 2L), merged.ids())
        assertEquals(emptyList(), merged.needMore)
    }

    @Test
    fun starsiNezHraniceUctuSDalsiStrankouSeNeukaze() {
        // Účet 0 má další stránku a nejstarší načtený hovor z noon+30: starší hovor účtu 1
        // by se zařadil před hovory účtu 0, které ještě nepřišly.
        val merged = mergeCallStates(
            listOf(
                CallsState(entries = listOf(entry(1, 5, noon + 50), entry(1, 3, noon + 30))),
                CallsState(entries = listOf(entry(2, 4, noon + 40), entry(2, 1, noon + 10)), endReached = true),
            ),
        )
        assertEquals(listOf(0 to 5L, 1 to 4L, 0 to 3L), merged.ids())
        assertEquals(listOf(0), merged.needMore)
    }

    @Test
    fun dalsiStrankuCteJenUcetKteryDrziHranici() {
        val merged = mergeCallStates(
            listOf(
                CallsState(entries = listOf(entry(1, 5, noon + 50), entry(1, 3, noon + 10))),
                CallsState(entries = listOf(entry(2, 4, noon + 40), entry(2, 2, noon + 30))),
            ),
        )
        assertEquals(listOf(0 to 5L, 1 to 4L, 1 to 2L), merged.ids())
        assertEquals(listOf(1), merged.needMore)
    }

    @Test
    fun nezdarCteniBezKonceHraniciNedrzi() {
        val merged = mergeCallStates(
            listOf(
                CallsState(entries = emptyList()),
                CallsState(entries = listOf(entry(2, 4, noon + 40)), endReached = true),
            ),
        )
        assertEquals(listOf(1 to 4L), merged.ids())
    }

    @Test
    fun hovoryRuznychUctuSeDoJednohoRadkuNeslucuji() {
        // Týž člověk (totéž id chatu) v obou účtech, zmeškaný hovor po zmeškaném.
        val calls = listOf(SourcedCall(0, entry(1, 3, noon + 30)), SourcedCall(1, entry(1, 2, noon + 20)), SourcedCall(1, entry(1, 1, noon + 10)))
        val groups = groupSourcedCallHistory(calls)
        assertEquals(listOf(0 to listOf(3L), 1 to listOf(2L, 1L)), groups.map { it.source to it.messageIds })
        assertEquals(2, groups.map { it.key }.distinct().size)
    }
}
