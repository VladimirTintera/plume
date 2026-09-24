package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.BlockedSender
import eu.tintera.plume.core.telegram.BlockedSendersPage
import eu.tintera.plume.core.telegram.ChatKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockedSendersStateTest {

    private fun sender(id: Long, title: String = "Odesílatel $id") = BlockedSender(
        id = id,
        title = title,
        avatarFileId = null,
        kind = ChatKind.Private,
        username = null,
        isBot = false,
    )

    private fun page(vararg ids: Long, total: Int) = BlockedSendersPage(ids.map { sender(it) }, total)

    @Test
    fun ctePoStrankachAzKeKonci() = runTest {
        val offsets = mutableListOf<Int>()
        val result = collectBlockedSenders { offset ->
            offsets += offset
            Result.success(if (offset == 0) page(1, 2, total = 150) else page(3, total = 150))
        }

        assertEquals(listOf(1L, 2L, 3L), result.getOrThrow().map { it.id })
        assertEquals(listOf(0, BLOCKED_PAGE_SIZE), offsets)
    }

    /**
     * Konec určuje počet od serveru, ne délka stránky: kdo se nedá dočíst, ze stránky vypadne,
     * a podle délky stránky by čtení skončilo předčasně.
     */
    @Test
    fun kratkaStrankaNeniKonec() = runTest {
        var calls = 0
        collectBlockedSenders { offset ->
            calls++
            Result.success(if (offset == 0) page(1, total = 150) else page(total = 150))
        }

        assertEquals(2, calls)
    }

    /** Seznam se mezi čteními posunul — dva stejné klíče by `LazyColumn` shodily. */
    @Test
    fun odesilatelNaDvouStrankachZustaneJednou() = runTest {
        val result = collectBlockedSenders { offset ->
            Result.success(if (offset == 0) page(1, 2, total = 150) else page(2, 3, total = 150))
        }

        assertEquals(listOf(1L, 2L, 3L), result.getOrThrow().map { it.id })
    }

    /** Půlka seznamu by tvrdila, že zbytek blokovaný není. */
    @Test
    fun selhanaStrankaJeSelhaniCelku() = runTest {
        val result = collectBlockedSenders { offset ->
            if (offset == 0) Result.success(page(1, total = 150)) else Result.failure(RuntimeException("síť"))
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun hledaBezOhleduNaDiakritikuAVelikostPismen() {
        val state = BlockedSendersState(
            senders = listOf(sender(1, "Šárka Nováková"), sender(2, "Petr")),
            query = "sarka",
        )

        assertEquals(listOf(1L), state.visible.map { it.id })
    }
}
