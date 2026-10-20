package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.InviteLinkRepository
import eu.tintera.plume.core.telegram.JoinResult
import eu.tintera.plume.core.telegram.isChannelLimit
import eu.tintera.plume.core.telegram.PendingJoinRequests
import eu.tintera.plume.core.telegram.SharePhoneBar
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.chats.SpamCleanup
import eu.tintera.plume.features.chats.cleanUpSpam
import eu.tintera.plume.features.report.ReportFlow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_default_silent_failed
import eu.tintera.plume.resources.conversation_default_silent_off
import eu.tintera.plume.resources.conversation_default_silent_on
import eu.tintera.plume.resources.clear_history_done
import eu.tintera.plume.resources.clear_history_failed
import eu.tintera.plume.resources.clear_history_period_done
import eu.tintera.plume.resources.forum_view_mode_failed
import eu.tintera.plume.resources.chat_bar_phone_shared
import eu.tintera.plume.resources.chats_blocked
import eu.tintera.plume.resources.chats_unarchived
import eu.tintera.plume.resources.conversation_join_failed
import eu.tintera.plume.resources.conversation_unblock_failed
import eu.tintera.plume.resources.invite_request_sent
import eu.tintera.plume.resources.join_guard_declined
import eu.tintera.plume.resources.profile_action_failed
import eu.tintera.plume.resources.profile_mute_failed
import eu.tintera.plume.resources.secret_chat_close_failed
import eu.tintera.plume.resources.secret_chat_closed
import eu.tintera.plume.resources.spam_bar_contact_added
import eu.tintera.plume.resources.spam_bar_failed
import eu.tintera.plume.resources.video_chat_join_elsewhere
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Chat jako celek, ne jeho zprávy: pruhy nad konverzací (spam, sdílení čísla, žádosti o vstup,
 * videochat) a jejich akce, přidání se, ztlumení, nahlášení, smazání a odchod, ukončení tajného
 * chatu. Konec chatu (smazaný, vrácený z archivu) hlásí jako stav, který obrazovka vyzvedne.
 *
 * Část [ConversationViewModel] — ten na ni deleguje a drží její toky ve veřejném API.
 */
internal class ConversationChatActions(
    private val chatId: Long,
    /** Vlákno komentářů: videochat ani žádosti o vstup se v něm neukazují. */
    private val threadRootId: Long?,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val inviteLinks: InviteLinkRepository,
    /** Průvodce nahlášením; sdílí ho i nabídka nad zprávou. */
    private val reportFlow: ReportFlow,
    private val logger: Logger,
    /**
     * Vstup hlídaný strážním botem: mini aplikaci a čekání na rozhodnutí obstará
     * [ConversationLinks.awaitGuardBot]; [onApproved] se zavolá po schválení.
     */
    private val awaitGuardBot: (guard: JoinResult.GuardBot, onApproved: (chatId: Long) -> Unit) -> Unit = { _, _ -> },
    /** Odblokovaný bot dostane „/start" (Start z [ConversationBots], i s parametrem z odkazu). */
    private val restartBot: () -> Unit = {},
    /** Limit skupin a kanálů: místo hlášky výběr neaktivních k opuštění (`ChannelLimitRequests`). */
    private val onChannelLimit: () -> Unit = {},
) {
    /**
     * Pruh nad konverzací (spam od neznámého, nasdílení čísla, žádost o vstup). Samostatný tok
     * ze stejného důvodu jako [connection]: mění se zřídka a nezávisle na zprávách.
     */
    val chatBar: StateFlow<ChatBar?> = chatRepository.chatBar(chatId)
        // Převedená základní skupina: TDLib u ní dál drží pruh „Pozvat členy", jenže zvát
        // a psát se dá jen do té nové. Pruh tam nic nenabízí.
        .combine(state.map { it.info?.upgradedToChatId != null }.distinctUntilChanged()) { bar, upgraded ->
            bar.takeIf { !upgraded }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Videochat skupiny / živé vysílání kanálu pro pruh nad konverzací. Ve vlákně komentářů ne —
     * patří diskusní skupině, ne příspěvku.
     */
    val videoChat: StateFlow<VideoChatInfo?> =
        (if (threadRootId == null) chatRepository.videoChat(chatId) else flowOf(null))
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Čekající žádosti o vstup pro pruh nad konverzací. TDLib je posílá jen správci, který je smí
     * vyřizovat (`Chat.pendingJoinRequests`), takže tu o právech rozhodovat netřeba. Ve vlákně
     * komentářů ne, ze stejného důvodu jako videochat.
     */
    val joinRequests: StateFlow<PendingJoinRequests?> =
        (if (threadRootId == null) inviteLinks.pendingJoinRequests(chatId) else flowOf(null))
            .map { requests -> requests?.takeIf { it.count > 0 } }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ťuknutí na pruh videochatu: připojit se jde zatím jen jinde. */
    fun explainVideoChat() = state.update { it.copy(notice = UiText(Res.string.video_chat_join_elsewhere)) }

    private val _chatGone = MutableStateFlow(false)

    /**
     * Chat se z pruhu proti spamu smazal — obrazovka konverzaci zavře. Stav, ne událost:
     * obrazovka si ho vyzvedne, i kdyby zrovna nebyla složená.
     */
    val chatGone: StateFlow<Boolean> = _chatGone.asStateFlow()

    private val _unarchived = MutableStateFlow(false)

    /**
     * Automaticky archivovaný chat se z pruhu vrátil mezi chaty. Obrazovka otevřená z archivu
     * se tím přestěhuje do hlavního seznamu — v archivu už chat není. Stav ze stejného důvodu
     * jako [chatGone].
     */
    val unarchived: StateFlow<Boolean> = _unarchived.asStateFlow()

    /**
     * Přidat se ke skupině nebo kanálu z globálního hledání. Po úspěchu se místo tlačítka
     * vrátí vstupní pole (v kanálu ho server stejně nepustí, ale to platí pro každý kanál).
     * Skupina se schvalováním vstupu tlačítko nechá (žádost čeká), strážní bot otevře svou mini
     * aplikaci. Dřív se výsledek `joinChat` zahazoval a obojí vypadalo jako přidání, po kterém
     * se nic nestalo.
     */
    fun join() {
        if (state.value.joining) return
        state.update { it.copy(joining = true) }
        scope.launch {
            repository.joinChat(chatId)
                .onSuccess { result ->
                    when (result) {
                        is JoinResult.Joined -> joined()
                        JoinResult.Requested -> state.update { it.copy(notice = UiText(Res.string.invite_request_sent)) }
                        JoinResult.Declined -> state.update { it.copy(error = UiText(Res.string.join_guard_declined)) }
                        is JoinResult.GuardBot -> awaitGuardBot(result) { joined() }
                    }
                }
                .onFailure { failure ->
                    if (failure.isChannelLimit()) {
                        onChannelLimit()
                    } else {
                        val message = failure.reported(logger, Res.string.conversation_join_failed)
                        state.update { it.copy(error = message) }
                    }
                }
            state.update { it.copy(joining = false) }
        }
    }

    private fun joined() = state.update { it.copy(info = it.info?.copy(canJoin = false)) }

    /**
     * „Odblokovat" z lišty místo vstupního pole. Jde tou jedinou cestou k blokování
     * ([ChatRepository.setBlocked]). Stav se přepíše hned po úspěchu, ať se pole vrátí bez
     * čekání na `updateChatBlockList` — ten pak přijde se stejnou hodnotou.
     *
     * Bota rovnou i spustí, jako „Restartovat" v oficiálním klientu: zablokovaný bot o chatu
     * neví a psát mu bez „/start" nemá smysl (stejný důvod jako u tlačítka Start).
     */
    fun unblock() {
        val current = state.value
        if (current.unblocking || !current.peerBlocked) return
        val bot = current.botIntro != null
        state.update { it.copy(unblocking = true) }
        scope.launch {
            chatRepository.setBlocked(chatId, blocked = false)
                .onSuccess {
                    state.update { it.copy(spam = it.spam?.copy(isBlocked = false), unblocking = false) }
                    if (bot) restartBot()
                }
                .onFailure { failure ->
                    state.update {
                        it.copy(unblocking = false, error = failure.reported(logger, Res.string.conversation_unblock_failed))
                    }
                }
        }
    }

    /** Ztlumit / zapnout zvuk kanálu z pruhu místo vstupního pole. Potvrzení přijde updatem. */
    fun toggleMute() {
        val muted = state.value.muted ?: return
        scope.launch {
            chatRepository.setMuted(chatId, !muted).onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.profile_mute_failed)) }
            }
        }
    }

    /** Pruh proti spamu: přidat protějšek do kontaktů. Pruh pak zmizí sám (update od serveru). */
    fun addSpamBarContact() {
        scope.launch {
            chatRepository.addToContacts(chatId)
                .onSuccess { state.update { it.copy(notice = UiText(Res.string.spam_bar_contact_added)) } }
                .onFailure { failure ->
                    logger.w(failure) { "Přidání do kontaktů z chatu $chatId selhalo." }
                    state.update { it.copy(error = failure.userMessage(Res.string.spam_bar_failed)) }
                }
        }
    }

    /** „Vrátit mezi chaty" z pruhu u chatu, který server archivoval sám. */
    fun unarchiveFromSpamBar() {
        scope.launch {
            chatRepository.unarchiveFromSpamBar(chatId)
                .onSuccess {
                    state.update { it.copy(notice = UiText(Res.string.chats_unarchived)) }
                    _unarchived.value = true
                }
                .onFailure { failure ->
                    logger.w(failure) { "Vrácení chatu $chatId z archivu selhalo." }
                    state.update { it.copy(error = failure.userMessage(Res.string.spam_bar_failed)) }
                }
        }
    }

    /** Nasdílí vlastní číslo protějšku (pruh [SharePhoneBar], po potvrzení dialogem). */
    fun sharePhoneNumber() {
        scope.launch {
            chatRepository.sharePhoneNumber(chatId)
                .onSuccess { state.update { it.copy(notice = UiText(Res.string.chat_bar_phone_shared)) } }
                .onFailure { failure ->
                    logger.w(failure) { "Nasdílení čísla v chatu $chatId selhalo." }
                    state.update { it.copy(error = failure.userMessage(Res.string.spam_bar_failed)) }
                }
        }
    }

    /** Zavře pruh nad konverzací napořád. */
    fun dismissChatBar() {
        scope.launch {
            chatRepository.dismissChatBar(chatId).onFailure { failure ->
                logger.w(failure) { "Pruh proti spamu v chatu $chatId nejde zavřít." }
                state.update { it.copy(error = failure.userMessage(Res.string.spam_bar_failed)) }
            }
        }
    }

    /**
     * Akce z pruhu proti spamu se vším, co si uživatel přibalil. Pořadí je nahlásit →
     * zablokovat → smazat, jako u smazání v seznamu chatů ([cleanUpSpam]): nahlásit jde jen
     * existující chat a po chybě blokování se nesmí smazat nic.
     */
    fun resolveSpamBar(cleanup: SpamBarCleanup) {
        reportFlow.thenAfterReport(cleanup.report, chatId) {
            cleanUpSpam(
                cleanup = SpamCleanup(block = cleanup.block),
                block = { chatRepository.setBlocked(chatId, true) },
                delete = {
                    if (cleanup.delete) {
                        chatRepository.deleteOrLeave(chatId).onSuccess { _chatGone.value = true }
                    } else {
                        Result.success(Unit)
                    }
                },
            ).onSuccess {
                if (cleanup.block && !cleanup.delete) {
                    state.update { it.copy(notice = UiText(Res.string.chats_blocked)) }
                }
            }.onFailure { failure ->
                logger.w(failure) { "Vyřízení spamu v chatu $chatId selhalo." }
                state.update { it.copy(error = failure.userMessage(Res.string.spam_bar_failed)) }
            }
        }
    }

    /** Nahlášení celého chatu z nabídky ⋮ v hlavičce, bez mazání. */
    fun reportChat() {
        reportFlow.start(chatId)
    }

    /**
     * Smazání chatu / odchod ze skupiny nebo kanálu z nabídky ⋮ v hlavičce, s volbami ze sheetu
     * smazání — stejné pořadí jako v seznamu chatů ([cleanUpSpam]). Po úspěchu se konverzace
     * zavře ([chatGone]).
     */
    fun deleteOrLeave(cleanup: SpamCleanup) {
        reportFlow.thenAfterReport(cleanup.report, chatId) {
            cleanUpSpam(
                cleanup = cleanup,
                block = { chatRepository.setBlocked(chatId, true) },
                delete = { chatRepository.deleteOrLeave(chatId) },
            )
                .onSuccess { _chatGone.value = true }
                .onFailure { failure -> state.update { it.copy(error = failure.reported(logger, Res.string.profile_action_failed)) } }
        }
    }

    /**
     * Ukončí tajný chat z nabídky ⋮. Historie zůstane, psát do něj už nepůjde ani protějšku —
     * místo pole se ukáže věta o ukončeném chatu, až to ohlásí `accessUpdates`.
     */
    /**
     * Celá skupina s tématy → seznam témat. Volba se ukládá na serveru (platí i na ostatních
     * zařízeních); [onDone] převede obrazovku, až ji TDLib přijme — ten ji zapíše lokálně a na
     * server ji pošle sám, takže se nečeká na síť. Navigace až po odpovědi, protože s konverzací
     * zmizí i ViewModel a jeho korutiny.
     */
    fun viewAsTopics(onDone: () -> Unit) {
        scope.launch {
            chatRepository.setViewAsTopics(chatId, viewAsTopics = true)
                .onSuccess { onDone() }
                .onFailure { failure -> state.update { it.copy(error = failure.reported(logger, Res.string.forum_view_mode_failed)) } }
        }
    }

    /**
     * Vymaže historii z nabídky ⋮, celou ([from] `null`), nebo jen zprávy z vybraných dnů
     * (sekundy epochy, oba konce včetně). Chat zůstane v seznamu; zprávy z konverzace zmizí
     * updaty o smazání, jako když je smaže kdokoli jiný.
     */
    fun clearHistory(from: Int?, to: Int?, forAll: Boolean) {
        scope.launch {
            val result = if (from != null && to != null) {
                repository.clearHistoryBetween(chatId, from, to, forAll)
            } else {
                repository.clearHistory(chatId, forAll)
            }
            result
                .onSuccess {
                    state.update {
                        it.copy(notice = UiText(if (from != null) Res.string.clear_history_period_done else Res.string.clear_history_done))
                    }
                }
                .onFailure { failure -> state.update { it.copy(error = failure.reported(logger, Res.string.clear_history_failed)) } }
        }
    }

    /**
     * Zvoneček v kanálu: výchozí odesílání bez zvuku. Stav se přepíše hned (zvoneček nečeká na
     * server) a hláška řekne, co se změnilo — ikona sama by šla přehlédnout.
     */
    fun toggleDefaultSilent() {
        val info = state.value.info ?: return
        val silent = !info.defaultSilent
        state.update {
            it.copy(
                info = it.info?.copy(defaultSilent = silent),
                notice = UiText(if (silent) Res.string.conversation_default_silent_on else Res.string.conversation_default_silent_off),
            )
        }
        scope.launch {
            repository.setDefaultSilent(chatId, silent).onFailure { failure ->
                state.update {
                    it.copy(
                        info = it.info?.copy(defaultSilent = !silent),
                        error = failure.reported(logger, Res.string.conversation_default_silent_failed),
                    )
                }
            }
        }
    }

    fun closeSecretChat() {
        scope.launch {
            chatRepository.closeSecretChat(chatId)
                .onSuccess { state.update { it.copy(notice = UiText(Res.string.secret_chat_closed)) } }
                .onFailure { failure -> state.update { it.copy(error = failure.reported(logger, Res.string.secret_chat_close_failed)) } }
        }
    }
}
