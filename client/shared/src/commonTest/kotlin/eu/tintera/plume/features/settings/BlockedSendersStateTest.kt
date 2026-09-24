package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.BlockedSender
import eu.tintera.plume.core.telegram.BlockedSendersPage
import eu.tintera.plume.core.telegram.ChatKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockedSendersStateTest {

    private fun sender(id: Long) = BlockedSender(
        id = id,
        title = "Odesílatel $id",
        avatarFileId = null,
        kind = ChatKind.Private,
        username = null,
        isBot = false,
    )

    private fun page(vararg ids: Long, total: Int) = BlockedSendersPage(ids.map(::sender), total)

    @Test
    fun navazujiciStrankaSePripoji() {
        val state = BlockedSendersState()
            .withPage(0, page(1, 2, total = 150))
            .withPage(BLOCKED_PAGE_SIZE, page(3, total = 150))

        assertEquals(listOf(1L, 2L, 3L), state.senders.map { it.id })
        assertEquals(2 * BLOCKED_PAGE_SIZE, state.nextOffset)
        assertTrue(state.endReached)
    }

    /** Stránka ze seznamu, který se mezitím četl znovu od začátku, se nesmí vmíchat. */
    @Test
    fun nenavazujiciStrankaSeZahodi() {
        val state = BlockedSendersState().withPage(BLOCKED_PAGE_SIZE, page(9, total = 150))

        assertTrue(state.senders.isEmpty())
        assertNull(state.totalCount)
    }

    /** Seznam se mezi čteními posunul — dva stejné klíče by `LazyColumn` shodily. */
    @Test
    fun odesilatelNaDvouStrankachZustaneJednou() {
        val state = BlockedSendersState()
            .withPage(0, page(1, 2, total = 150))
            .withPage(BLOCKED_PAGE_SIZE, page(2, 3, total = 150))

        assertEquals(listOf(1L, 2L, 3L), state.senders.map { it.id })
    }

    /**
     * Konec určuje počet od serveru, ne počet dočtených: kdo se dočíst nedá, ze stránky vypadne,
     * a podle délky stránky by stránkování skončilo předčasně.
     */
    @Test
    fun konecPodleServeruNePodleDoctenych() {
        val state = BlockedSendersState().withPage(0, page(1, total = 150))

        assertFalse(state.endReached)
    }

    @Test
    fun pocetNeuvadiOdblokovane() {
        val state = BlockedSendersState()
            .withPage(0, page(1, 2, 3, total = 3))
            .copy(unblocked = setOf(2L))

        assertEquals(2, state.blockedCount)
    }
}
