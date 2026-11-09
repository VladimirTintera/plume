package eu.tintera.plume.core.snooze

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.settings.DataStoreAppSettings
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.telegram.FakeChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Odložení nad skutečným úložištěm nastavení (na skutečném čase, viz `DataStoreAppSettingsTest`).
 * Hodiny jsou ručně posouvané, ať se dá odložení „přespat" bez čekání.
 */
class ChatSnoozesTest {

    private var now = Instant.fromEpochSeconds(1_800_000_000)

    /** Co by se naplánovalo a zrušilo v systému: chatId → čas, a zrušené chatId po sobě. */
    private val scheduled = mutableMapOf<Long, Instant>()
    private val cancelled = mutableListOf<Long>()
    private val reminders = object : SnoozeReminders {
        override fun schedule(slot: AccountSlot, chatId: Long, title: String, at: Instant) {
            scheduled[chatId] = at
        }

        override fun cancel(slot: AccountSlot, chatId: Long) {
            scheduled -= chatId
            cancelled += chatId
        }
    }

    private fun snoozeTest(block: suspend (ChatSnoozes, DataStoreAppSettings) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val path = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-snooze-${Random.nextLong()}.preferences_pb").toString()
            val settings = DataStoreAppSettings(createSettingsDataStore(path), scope)
            block(ChatSnoozes(settings, FakeChatRepository(), reminders, now = { now }), settings)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun ChatSnoozes.awaitState(predicate: (ChatSnoozeState) -> Boolean): ChatSnoozeState =
        withTimeout(WAIT_MS) { state.first(predicate) }

    @Test
    fun odlozenyChatSpiAPakSeVynori() = snoozeTest { snoozes, settings ->
        snoozes.snooze(chatId = 5, until = now + 1.hours, title = "Pět")
        snoozes.awaitState { 5L in it.sleeping }
        assertTrue(snoozes.isSleeping(5))

        assertEquals(now + 1.hours, scheduled[5])

        // Nahlédnutí do spícího chatu odložení neruší — ani upozornění.
        snoozes.opened(5)
        assertTrue(snoozes.isSleeping(5))
        assertTrue(cancelled.isEmpty())

        now += 2.hours
        assertFalse(snoozes.isSleeping(5))
        // Stav se přepočítá s další změnou úložiště; tady ji vyvolá jiný chat.
        snoozes.snooze(chatId = 6, until = now + 1.hours, title = "Šest")
        snoozes.awaitState { 5L in it.due && 6L in it.sleeping }

        // Otevřením vynořená připomínka zmizí, spící chat zůstane.
        snoozes.opened(5)
        snoozes.opened(6)
        val state = snoozes.awaitState { 5L !in it.due }
        assertEquals(setOf(6L), state.sleeping.keys)
        assertEquals(listOf(6L), settings.chatSnoozes.value.map { it.chatId })
        // Upozornění vynořeného chatu se stáhne z lišty, spícímu zůstane naplánované.
        assertEquals(listOf(5L), cancelled)
        assertEquals(setOf(6L), scheduled.keys)
    }

    /** Odložení jiného účtu se v seznamu neukáže a notifikace neztiší. */
    @Test
    fun ciziUcetSeNepocita() = snoozeTest { snoozes, settings ->
        settings.setChatSnoozes(listOf(ChatSnooze(accountId = 1, chatId = 7, until = (now + 1.hours).toEpochMilliseconds())))
        withTimeout(WAIT_MS) { settings.chatSnoozes.first { it.isNotEmpty() } }
        assertFalse(snoozes.isSleeping(7))
        assertEquals(ChatSnoozeState(), snoozes.state.first())

        // Vlastní odložení cizí nepřepíše.
        snoozes.snooze(chatId = 7, until = now + 1.hours, title = "Sedm")
        snoozes.awaitState { 7L in it.sleeping }
        assertEquals(setOf(1L, 999L), settings.chatSnoozes.value.map { it.accountId }.toSet())

        snoozes.clear(7)
        snoozes.awaitState { it.sleeping.isEmpty() }
        assertEquals(listOf(7L), cancelled)
        assertEquals(listOf(1L), settings.chatSnoozes.value.map { it.accountId })
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
