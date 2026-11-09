package eu.tintera.plume.core.snooze

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Odložený chat — připomínka jen pro uživatele, v tomhle zařízení. Do [until] (ms od epochy) je
 * chat ztišený, pak se vynoří nahoře v seznamu, dokud ho uživatel neotevře. [accountId] drží
 * odložení od sebe mezi účty.
 */
@Serializable
data class ChatSnooze(val accountId: Long, val chatId: Long, val until: Long)

/**
 * Odložení z pohledu seznamu chatů v jednom okamžiku: kdo ještě spí a do kdy ([sleeping], ms od
 * epochy), a kdo se už vynořil ([due]).
 */
data class ChatSnoozeState(
    val sleeping: Map<Long, Long> = emptyMap(),
    val due: Set<Long> = emptySet(),
)

/**
 * Rozdělení odložených chatů v okamžiku [now] (ms od epochy). Odložení, které právě končí
 * (`until == now`), už spí dost — patří mezi vynořené.
 */
fun snoozeState(snoozes: List<ChatSnooze>, now: Long): ChatSnoozeState = ChatSnoozeState(
    sleeping = snoozes.filter { it.until > now }.associate { it.chatId to it.until },
    due = snoozes.filter { it.until <= now }.mapTo(LinkedHashSet()) { it.chatId },
)

/**
 * Odložené chaty přihlášeného účtu (uložené v [AppSettings]).
 *
 * Účet je id Uložených zpráv ([ChatRepository.savedMessagesChatId]), stejně jako u záložek
 * zpráv. Dokud není přihlášeno, odložené chaty žádné nejsou.
 */
class ChatSnoozes(
    private val settings: AppSettings,
    private val chats: ChatRepository,
    /** Upozornění v čase vynoření; bez něj se chat vynoří jen v seznamu. */
    private val reminders: SnoozeReminders? = null,
    private val slot: AccountSlot = AccountSlot.Primary,
    private val now: () -> Instant = { Clock.System.now() },
) {

    private val own: Flow<List<ChatSnooze>> = combine(settings.chatSnoozes, chats.savedMessagesChatId) { list, account ->
        if (account == null) emptyList() else list.filter { it.accountId == account }
    }

    /**
     * Stav, který se sám přepočítá, když některé odložení skončí.
     *
     * **Čeká se po nejvýš minutě, ne rovnou do konce odložení.** `delay` běží na monotónních
     * hodinách, které na Androidu během hlubokého spánku zařízení stojí; odložení do rána by se
     * pak vynořilo až o noc později. Po minutách se po probuzení dožene do minuty.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<ChatSnoozeState> = own.transformLatest { list ->
        while (true) {
            val nowMs = now().toEpochMilliseconds()
            emit(snoozeState(list, nowMs))
            val next = list.filter { it.until > nowMs }.minOfOrNull { it.until } ?: break
            delay((next - nowMs).coerceAtMost(MAX_WAIT_MS))
        }
    }.distinctUntilChanged()

    /** Spí chat právě teď? Pro notifikace — ty se u odloženého chatu ztiší. */
    fun isSleeping(chatId: Long): Boolean {
        val account = chats.savedMessagesChatId.value ?: return false
        val nowMs = now().toEpochMilliseconds()
        return settings.chatSnoozes.value.any { it.accountId == account && it.chatId == chatId && it.until > nowMs }
    }

    /**
     * Odloží chat do [until]; dřívější odložení téhož chatu nahradí. [title] je název chatu do
     * upozornění — v čase vynoření nemusí proces běžet a TDLib se ptát nedá.
     */
    fun snooze(chatId: Long, until: Instant, title: String) {
        val account = chats.savedMessagesChatId.value ?: return
        val others = settings.chatSnoozes.value.filterNot { it.accountId == account && it.chatId == chatId }
        settings.setChatSnoozes(others + ChatSnooze(account, chatId, until.toEpochMilliseconds()))
        reminders?.schedule(slot, chatId, title, until)
    }

    /** Zruší odložení, ať chat ještě spí, nebo se už vynořil, i s upozorněním. */
    fun clear(chatId: Long) {
        update(chatId) { true }
        reminders?.cancel(slot, chatId)
    }

    /**
     * Chat se otevřel: vynořená připomínka splnila účel a z bloku nahoře zmizí. Chat, který ještě
     * spí, zůstává odložený — nahlédnutí do něj odložení neruší.
     */
    fun opened(chatId: Long) {
        val nowMs = now().toEpochMilliseconds()
        // Upozornění v liště splnilo účel spolu s připomínkou; spícímu chatu zůstává.
        if (update(chatId) { it.until <= nowMs }) reminders?.cancel(slot, chatId)
    }

    /** Odebere odložení chatu, která vyhoví [remove]; vrací, jestli nějaké ubylo. */
    private fun update(chatId: Long, remove: (ChatSnooze) -> Boolean): Boolean {
        val account = chats.savedMessagesChatId.value ?: return false
        val current = settings.chatSnoozes.value
        val kept = current.filterNot { it.accountId == account && it.chatId == chatId && remove(it) }
        if (kept.size == current.size) return false
        settings.setChatSnoozes(kept)
        return true
    }

    private companion object {
        const val MAX_WAIT_MS = 60_000L
    }
}
