package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.suggested_post_action_failed
import eu.tintera.plume.resources.ephemeral_show_original_failed
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.ForwardOptions
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.ModerationChoice
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.report.ReportFlow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.replies_block_failed
import eu.tintera.plume.resources.replies_block_done
import eu.tintera.plume.resources.protection_request_failed
import eu.tintera.plume.resources.protection_request_rejected
import eu.tintera.plume.resources.protection_request_approved
import eu.tintera.plume.resources.photo_suggestion_failed
import eu.tintera.plume.resources.photo_suggestion_saved
import eu.tintera.plume.resources.birthdate_suggestion_failed
import eu.tintera.plume.resources.birthdate_suggestion_saved
import eu.tintera.plume.resources.call_unavailable
import eu.tintera.plume.resources.contact_add_failed
import eu.tintera.plume.resources.contact_remove_failed
import eu.tintera.plume.resources.conversation_cancel_send_failed
import eu.tintera.plume.resources.conversation_delete_failed
import eu.tintera.plume.resources.conversation_moderation_failed
import eu.tintera.plume.resources.member_banned
import eu.tintera.plume.resources.conversation_pin_failed
import eu.tintera.plume.resources.conversation_remind_failed
import eu.tintera.plume.resources.conversation_remind_set
import eu.tintera.plume.resources.conversation_resend_failed
import eu.tintera.plume.resources.conversation_send_failed_unknown
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Co se dá se zprávou udělat z nabídky nebo z karty ve zprávě: připnout a odepnout, smazat
 * (i zrušit odesílání), znovu odeslat, přeposlat, připomenout (s termínem od pravidel nebo
 * modelu), nahlásit, přidat kontakt z karty, zavolat zpět.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationMessageActions(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    /** Kam konverzace odchází (viz [ConversationNavEvent]); obrazovka to vyřídí. */
    private val navigate: (ConversationNavEvent) -> Unit,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val chatRepository: ChatRepository,
    /** Termín připomínky od modelu v zařízení, když ho nenajdou pravidla. */
    private val reminderTimeModel: ReminderTimeModel?,
    private val reportFlow: ReportFlow,
    private val closeMenus: () -> Unit,
    /** Odepnutí z nabídky jde touž cestou jako z pruhu připnutých (s „Vrátit"). */
    private val unpin: (ChatMessage) -> Unit,
    private val logger: Logger,
) {
    /**
     * Připnutí z nabídky.
     *
     * Ve skupině, kanálu i soukromém chatu se nejdřív zeptá — každý z nich má jednu volbu, která
     * mění, koho se to dotkne (upozornit členy / připnout i protějšku). V Uložených zprávách
     * žádná taková volba není, takže se připíná rovnou.
     */
    fun requestPin(messageId: Long) {
        val info = state.value.info
        if (info == null || info.isSavedMessages) {
            closeMenus()
            pin(messageId, notify = false, onlyForSelf = false)
            return
        }

        state.update {
            it.copy(
                messageMenu = null,
                dialog = ConversationDialog.Pin(PendingPin(
                    messageId = messageId,
                    scope = when {
                        info.isChannel -> PinScope.Channel
                        info.isGroup -> PinScope.Group
                        else -> PinScope.Private
                    },
                )),
            )
        }
    }

    fun cancelPin() {
        state.update { it.closing<ConversationDialog.Pin>() }
    }

    /** [option] je zaškrtávátko dialogu: upozornit členy, nebo v soukromém chatu připnout i protějšku. */
    fun confirmPin(option: Boolean) {
        val pending = state.value.dialog<ConversationDialog.Pin>()?.pending ?: return
        state.update { it.closing<ConversationDialog.Pin>() }

        when (pending.scope) {
            // Připnutí v soukromém chatu nikoho neupozorňuje — oznámení o něm by bylo jen šum
            // navíc k servisní zprávě, kterou protějšek stejně uvidí.
            PinScope.Private -> pin(pending.messageId, notify = false, onlyForSelf = !option)
            PinScope.Group, PinScope.Channel -> pin(pending.messageId, notify = option, onlyForSelf = false)
        }
    }

    /**
     * Pruh připnutých se neaktualizuje ručně: `UpdateMessageIsPinned` spustí nové čtení seznamu,
     * stejně jako u odepnutí jinde.
     */
    private fun pin(messageId: Long, notify: Boolean, onlyForSelf: Boolean) {
        scope.launch {
            repository.pin(chatId, messageId, notify = notify, onlyForSelf = onlyForSelf)
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_pin_failed)) }
                }
        }
    }

    /** „Schválit" na kartě navrhovaného příspěvku; nový stav přinese update zprávy. */
    fun approveSuggestedPost(messageId: Long) {
        scope.launch {
            repository.approveSuggestedPost(chatId, messageId).onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.suggested_post_action_failed)) }
            }
        }
    }

    /** „Zobrazit původní" pod mizejícím obsahem od bota; původní obsah přinese update zprávy. */
    fun showOriginalContent(messageId: Long) {
        scope.launch {
            repository.showOriginalContent(chatId, messageId).onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.ephemeral_show_original_failed)) }
            }
        }
    }

    /** „Odmítnout" otevře dialog s komentářem; odmítá se až jeho potvrzením. */
    fun askDeclineSuggestedPost(messageId: Long) = state.update { it.copy(dialog = ConversationDialog.DeclineSuggestedPost(messageId)) }

    fun cancelDeclineSuggestedPost() = state.update { it.closing<ConversationDialog.DeclineSuggestedPost>() }

    fun declineSuggestedPost(comment: String) {
        val messageId = state.value.dialog<ConversationDialog.DeclineSuggestedPost>()?.messageId ?: return
        state.update { it.closing<ConversationDialog.DeclineSuggestedPost>() }
        scope.launch {
            repository.declineSuggestedPost(chatId, messageId, comment).onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.suggested_post_action_failed)) }
            }
        }
    }

    /** Odepnutí z nabídky. Chová se stejně jako křížek v pruhu, včetně „Vrátit". */
    fun unpinFromMenu(messageId: Long) {
        val message = state.value.pinned.firstOrNull { it.id == messageId }
            ?: state.value.messages.firstOrNull { it.id == messageId }
        closeMenus()
        if (message != null) unpin(message)
    }

    /** Otevře potvrzení smazání. Nevratná akce se nedělá na jedno ťuknutí. */
    /**
     * Pošle znovu neodeslanou zprávu (u alba všechny jeho neodeslané zprávy naráz, viz
     * [failedGroup]). Zprávu, kterou znovu poslat nejde, neposílá — řekne, proč neodešla:
     * ťuknutí na „Neodesláno" musí něco udělat, i když opakování nepomůže.
     *
     * Obrazovka se srovná sama z updatů: TDLib starou zprávu smaže a novou ohlásí jako novou
     * ve stavu odesílání, takže se tady nic nepřepisuje.
     */
    fun resend(messageId: Long) {
        state.update { it.copy(messageMenu = null) }
        val group = failedGroup(state.value.messages, messageId)
        if (group.isEmpty()) return
        group.firstOrNull { it.sendFailure?.canRetry != true }?.let { blocked ->
            val reason = blocked.sendFailure?.reason ?: UiText(Res.string.conversation_send_failed_unknown)
            state.update { it.copy(notice = reason) }
            return
        }
        scope.launch {
            repository.resend(chatId, group.map { it.id }).onFailure { failure ->
                logger.w(failure) { "Zprávu $messageId se nepodařilo poslat znovu." }
                state.update { it.copy(notice = failure.userMessage(Res.string.conversation_resend_failed)) }
            }
        }
    }

    fun requestDelete(messageId: Long) {
        val menu = state.value.messageMenu ?: return
        state.update {
            it.copy(
                messageMenu = null,
                dialog = ConversationDialog.Delete(PendingDelete(
                    messageId = messageId,
                    // Nabídka nad albem maže celé album — v bublině je to jedna věc.
                    messageIds = it.albumMemberIds(messageId),
                    canDeleteForAll = menu.actions.canDeleteForAll,
                    canDeleteForSelf = menu.actions.canDeleteForSelf,
                )),
            )
        }
        loadSenderModeration()
    }

    /**
     * Dočte do otevřeného potvrzení smazání, co smí správce udělat s autorem zpráv navíc
     * ([PendingDelete.moderation]). Jen u zpráv, které jdou smazat všem — jinak nejde o cizí
     * zprávy ve skupině, kde je uživatel správce, a dotaz by byl zbytečný.
     */
    fun loadSenderModeration() {
        val pending = state.value.dialog<ConversationDialog.Delete>()?.pending?.takeIf { it.canDeleteForAll } ?: return
        scope.launch {
            val found = repository.senderModeration(chatId, pending.messageIds)
                .onFailure { logger.w(it) { "Moderování autora zpráv ${pending.messageIds} se nepodařilo zjistit." } }
                .getOrNull() ?: return@launch
            state.update { current ->
                // Dialog mezitím mohl zmizet, nebo se otevřít nad jinými zprávami.
                val open = current.dialog<ConversationDialog.Delete>()?.pending?.takeIf { it.messageIds == pending.messageIds } ?: return@update current
                current.copy(dialog = ConversationDialog.Delete(open.copy(moderation = found)))
            }
        }
    }

    /**
     * „Nahlásit" z nabídky nad zprávou: průvodce s touhle zprávou (u alba s celým albem, jako
     * u mazání). Server se pak zeptá na důvod — zprávy mu tím odpadne dodávat zvlášť.
     */
    /** „Zablokovat odesílatele" v chatu Odpovědi: nejdřív potvrzení s volbami. */
    fun requestBlockFromReplies(messageId: Long) {
        closeMenus()
        state.update { it.copy(dialog = ConversationDialog.BlockFromReplies(messageId)) }
    }

    fun dismissBlockFromReplies() = state.update { it.closing<ConversationDialog.BlockFromReplies>() }

    /** Zablokuje odesílatele zprávy z chatu Odpovědi. Smazané zprávy zmizí updatem. */
    fun confirmBlockFromReplies(deleteMessage: Boolean, deleteAll: Boolean, reportSpam: Boolean) {
        val messageId = state.value.dialog<ConversationDialog.BlockFromReplies>()?.messageId ?: return
        state.update { it.closing<ConversationDialog.BlockFromReplies>() }
        scope.launch {
            repository.blockFromReplies(messageId, deleteMessage, deleteAll, reportSpam)
                .onSuccess { state.update { it.copy(notice = UiText(Res.string.replies_block_done)) } }
                .onFailure { failure ->
                    logger.w(failure) { "Zablokování odesílatele z Odpovědí ($messageId) selhalo." }
                    state.update { it.copy(error = failure.userMessage(Res.string.replies_block_failed)) }
                }
        }
    }

    fun reportMessage(messageId: Long) {
        val ids = state.value.albumMemberIds(messageId)
        closeMenus()
        reportFlow.start(chatId, messageIds = ids)
    }

    /**
     * „Přidat do kontaktů" na kartě sdíleného kontaktu. Karta tlačítko schová hned po úspěchu —
     * zpráva se kvůli tomu znovu nepřevádí — a snackbar nabídne „Vrátit": přidání jde vzít
     * zpátky a dialog před vratnou akcí jen zdržuje.
     */
    fun addSharedContact(messageId: Long) {
        scope.launch {
            repository.addSharedContact(chatId, messageId)
                .onSuccess { userId ->
                    state.update { it.withContactAddable(userId, addable = false).copy(addedContact = userId) }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Přidání kontaktu ze zprávy $messageId selhalo." }
                    state.update { it.copy(error = failure.userMessage(Res.string.contact_add_failed)) }
                }
        }
    }

    /** Nastaví vlastní datum narození na to, které protějšek navrhl zprávou [messageId]. */
    fun acceptSuggestedBirthdate(messageId: Long) {
        scope.launch {
            repository.acceptSuggestedBirthdate(chatId, messageId)
                .onSuccess { state.update { it.copy(error = UiText(Res.string.birthdate_suggestion_saved)) } }
                .onFailure { failure ->
                    logger.w(failure) { "Nastavení navrženého data narození ze zprávy $messageId selhalo." }
                    state.update { it.copy(error = failure.userMessage(Res.string.birthdate_suggestion_failed)) }
                }
        }
    }

    /**
     * Odpoví na žádost protějšku o vypnutí ochrany obsahu. Oznámení se pak samo přepne podle
     * updatu obsahu zprávy; hláška jen potvrdí, co se stalo.
     */
    fun answerProtectionRequest(messageId: Long, approve: Boolean) {
        scope.launch {
            repository.answerProtectionDisableRequest(chatId, messageId, approve)
                .onSuccess {
                    val notice = if (approve) Res.string.protection_request_approved else Res.string.protection_request_rejected
                    state.update { it.copy(notice = UiText(notice)) }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Odpověď na žádost o vypnutí ochrany obsahu ($messageId) selhala." }
                    state.update { it.copy(error = failure.userMessage(Res.string.protection_request_failed)) }
                }
        }
    }

    /** Nastaví jako profilovku fotku, kterou protějšek navrhl zprávou [messageId]. */
    fun acceptSuggestedPhoto(messageId: Long) {
        scope.launch {
            repository.acceptSuggestedPhoto(chatId, messageId)
                .onSuccess { state.update { it.copy(error = UiText(Res.string.photo_suggestion_saved)) } }
                .onFailure { failure ->
                    logger.w(failure) { "Nastavení navržené profilovky ze zprávy $messageId selhalo." }
                    state.update { it.copy(error = failure.userMessage(Res.string.photo_suggestion_failed)) }
                }
        }
    }

    /**
     * Zavolat zpátky ze zprávy o hovoru: videohovor zase videem, když ho protějšek přijímá.
     * Samotné vytočení (s dotazem na mikrofon a kameru) dělá obrazovka ([ConversationState.callRequest]).
     */
    fun callBack(messageId: Long) {
        val call = state.value.messages.firstOrNull { it.id == messageId }?.media as? MessageMedia.Call ?: return
        val info = state.value.info
        if (info?.callUserId == null) {
            state.update { it.copy(error = UiText(Res.string.call_unavailable)) }
            return
        }
        state.update { it.copy(callRequest = CallRequest(video = call.isVideo && info.callSupportsVideo)) }
    }

    fun consumeCallRequest() {
        state.update { it.copy(callRequest = null) }
    }

    /** „Vrátit" ze snackbaru po přidání kontaktu. */
    fun undoAddContact(userId: Long) {
        scope.launch {
            chatRepository.removeContact(userId)
                .onSuccess { state.update { it.withContactAddable(userId, addable = true) } }
                .onFailure { failure ->
                    logger.w(failure) { "Odebrání kontaktu $userId selhalo." }
                    state.update { it.copy(error = failure.userMessage(Res.string.contact_remove_failed)) }
                }
        }
    }

    /** Obrazovka snackbar k přidanému kontaktu ukázala. */
    fun consumeAddedContact() {
        state.update { it.copy(addedContact = null) }
    }

    /**
     * „Přeposlat" z nabídky. Obrazovka si o cíl řekne sama (otevře výběr chatu), takže se tu jen
     * zapamatuje, co se přeposílá — u alba celé album, jako u mazání.
     */
    fun requestForward(messageId: Long) {
        val ids = state.value.albumMemberIds(messageId)
        state.update { it.copy(messageMenu = null) }
        navigate(ConversationNavEvent.Forward(ids))
    }

    /**
     * Sheet připomínky; termín ze zprávy ([reminderTimeFromText]) jde nahoru jako první volba.
     * Počítá se od odeslání zprávy a nabízí se, jen když ještě nebyl (a je aspoň za minutu,
     * jako u plánování).
     */
    fun requestRemind(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId }
        val zone = TimeZone.currentSystemDefault()
        val sentAt = message?.let { Instant.fromEpochSeconds(it.timestamp.toLong()).toLocalDateTime(zone) }
        val fromRules = sentAt?.let { reminderTimeFromText(message.text, it) }
        val suggested = fromRules?.toInstant(zone)?.epochSeconds?.takeIf { scheduleValid(it, Clock.System.now().epochSeconds) }
        state.update { it.copy(messageMenu = null, sheet = RemindRequest(messageId, suggested)) }

        // Model jen tam, kde pravidla nenašla NIC. Termín, který pravidla našla a který už
        // minul, model nespraví — a ptát se ho by jen zdrželo sheet.
        val model = reminderTimeModel ?: return
        if (fromRules != null || sentAt == null) return
        scope.launch {
            if (!model.available()) return@launch
            updateRemind(messageId) { it.copy(suggesting = true) }
            val at = model.suggest(message.text, sentAt)?.toInstant(zone)?.epochSeconds
                ?.takeIf { scheduleValid(it, Clock.System.now().epochSeconds) }
            updateRemind(messageId) { it.copy(suggesting = false, suggestedAt = at, fromModel = at != null) }
        }
    }

    /** Změna otevřeného sheetu připomínky — jen když je pořád otevřený u téže zprávy. */
    private fun updateRemind(messageId: Long, change: (RemindRequest) -> RemindRequest) = state.update { current ->
        val open = current.sheet<RemindRequest>()?.takeIf { it.messageId == messageId } ?: return@update current
        current.copy(sheet = change(open))
    }

    fun closeRemind() {
        state.update { it.closingSheet<RemindRequest>() }
    }

    /**
     * Připomínka: zpráva (u alba celé album) se naplánovaně přepošle do Uložených zpráv na
     * [at] (sekundy od epochy). Telegram na zprávu doručenou z naplánovaných v Uložených
     * zprávách upozorní — tak „Připomínky" dělají oficiální klienti, a nepotřebuje to vlastní
     * notifikaci ani oprávnění k budíku. Připomínka žije na serveru, takže přijde i na jiném
     * zařízení a přežije přeinstalaci.
     */
    fun remind(messageId: Long, at: Long) {
        val ids = state.value.albumMemberIds(messageId)
        state.update { it.closingSheet<RemindRequest>() }
        val saved = chatRepository.savedMessagesChatId.value ?: return
        scope.launch {
            val result = repository.forward(chatId, ids, saved, ForwardOptions(schedule = MessageSchedule.At(at)))
            result.onFailure { logger.w(it) { "Připomínku se nepodařilo naplánovat." } }
            val notice = if (result.isSuccess) Res.string.conversation_remind_set else Res.string.conversation_remind_failed
            state.update { it.copy(notice = UiText(notice)) }
        }
    }


    fun cancelDelete() {
        state.update { it.closing<ConversationDialog.Delete>() }
    }

    /**
     * Smaže zprávu.
     *
     * Ze seznamu se **neodstraňuje ručně** — TDLib pošle `UpdateDeleteMessages` a ten se už
     * zpracovává. Předběhnout server by znamenalo riskovat, že zpráva zmizí z obrazovky
     * a na serveru zůstane.
     */
    fun confirmDelete(revoke: Boolean, choice: ModerationChoice = ModerationChoice()) {
        val pending = state.value.dialog<ConversationDialog.Delete>()?.pending ?: return
        state.update { it.closing<ConversationDialog.Delete>() }
        val moderation = pending.moderation?.takeIf { choice.any }

        scope.launch {
            if (moderation == null) {
                repository.deleteMessages(chatId, pending.messageIds, revoke)
                    .onFailure { failure ->
                        state.update {
                            it.copy(error = failure.reported(logger, Res.string.conversation_delete_failed))
                        }
                    }
                return@launch
            }
            repository.deleteAsModerator(chatId, pending.messageIds, moderation.userId, choice)
                .onSuccess {
                    if (choice.ban) state.update { it.copy(notice = UiText(Res.string.member_banned, moderation.name)) }
                }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_moderation_failed)) }
                }
        }
    }

    /**
     * Zruší odesílání přílohy.
     *
     * Rozepsanou zprávu stačí smazat: TDLib tím zastaví i nahrávání. Ze seznamu zmizí updatem
     * o smazání jako každá jiná. Hláška jen při selhání — mezitím mohla zpráva doběhnout.
     */
    fun cancelUpload(messageId: Long) {
        scope.launch {
            repository.deleteMessages(chatId, listOf(messageId), revoke = true)
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_cancel_send_failed)) }
                }
        }
    }
}
