package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.GifRepository
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.resources.conversation_saved_gif_added
import eu.tintera.plume.resources.conversation_saved_gif_removed
import eu.tintera.plume.resources.conversation_saved_gif_failed
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.ai.variantFor
import eu.tintera.plume.core.ai.AiTask
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ReactionChoices
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import eu.tintera.plume.features.conversation.items.contextMenuItems
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_bookmark_added
import eu.tintera.plume.resources.conversation_bookmark_removed
import eu.tintera.plume.resources.conversation_favorite_added
import eu.tintera.plume.resources.conversation_favorite_failed
import eu.tintera.plume.resources.conversation_favorite_removed
import eu.tintera.plume.resources.conversation_profile_audio_added
import eu.tintera.plume.resources.conversation_profile_audio_failed
import eu.tintera.plume.resources.conversation_profile_audio_removed
import eu.tintera.plume.resources.message_info_failed
import eu.tintera.plume.resources.message_info_link_copied
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Nabídka nad zprávou (a nad oznámením): co se v ní nabídne podle práv a obsahu zprávy,
 * a akce, které nevedou do jiné části — kopírování, výběr textu, informace o zprávě, odkaz na
 * zprávu, záložka, oblíbená samolepka, potvrzení přečtení.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationMessageMenu(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val settings: AppSettings,
    private val copyToClipboard: suspend (String) -> Unit,
    /** Potvrdí zkopírování sám systém (`TextClipboard.confirmsCopy`)? Pak hláška od nás není. */
    private val clipboardConfirmsCopy: Boolean,
    private val languageDetector: LanguageDetector,
    private val onDeviceAi: OnDeviceAi?,
    private val bookmarks: MessageBookmarks?,
    private val stickerRepository: StickerRepository,
    /** Uložené GIFy; bez nich se „Uložit GIF" nenabízí. */
    private val gifs: GifRepository? = null,
    /** Hudba ve vlastním profilu; bez ní se „Přidat do profilu" nenabízí. */
    private val profiles: ProfileRepository? = null,
    /** Nabídka nabízí „Akce u zprávy", když v ní model něco našel. */
    private val messageContext: ConversationMessageContext,
    /** „Přeložit" se nenabízí u zprávy, která už je v jazyce překladu. */
    private val translation: ConversationTranslation,
    private val logger: Logger,
    /** Přepis z telefonu ke zprávě (`LocalTranscripts`) — zpráva nese jen ten od serveru. */
    private val localTranscript: (Long) -> SpeechTranscript? = { null },
) {
    /**
     * Otevře text zprávy k výběru úryvku.
     *
     * Nabídku to ukončí, jako „Informace" — je to jednorázová akce, která si otevře vlastní
     * sheet. Text se bere týž, co by šel do schránky ([copyableText]), takže se výběr nabízí
     * přesně nad tím, co zpráva opravdu nese.
     */
    fun selectText(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        state.update { it.copy(messageMenu = null, sheet = SelectTextSheet(message)) }
    }

    fun dismissTextSelection() {
        state.update { it.closingSheet<SelectTextSheet>() }
    }

    /**
     * Informace o zprávě z nabídky: kdy odešla, kdy ji kdo přečetl, kdo ji zhlédl a odkaz na ni.
     *
     * Načítá se až tady. Skoro žádná zpráva nemá všechna tři práva (`canGetReadDate`,
     * `canGetViewers`, `canGetLink`), takže ptát se dopředu by znamenalo tři dotazy na každou
     * zprávu v historii kvůli sheetu, který se otevře jednou za čas.
     */
    fun showMessageInfo(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        state.update { it.copy(messageMenu = null, sheet = MessageInfoSheet(message = message)) }

        scope.launch {
            repository.messageInfo(chatId, messageId)
                .onSuccess { info ->
                    state.update { current ->
                        // Mezitím mohl uživatel sheet zavřít nebo otevřít u jiné zprávy.
                        val open = current.sheet<MessageInfoSheet>()?.takeIf { it.message.id == messageId }
                            ?: return@update current
                        current.copy(sheet = open.copy(info = info))
                    }
                }
                .onFailure { failure ->
                    state.update {
                        it.closingSheet<MessageInfoSheet>().copy(error = failure.reported(logger, Res.string.message_info_failed))
                    }
                }
        }
    }

    fun dismissMessageInfo() {
        state.update { it.closingSheet<MessageInfoSheet>() }
    }

    /**
     * Odkaz na zprávu do schránky.
     *
     * Sheet se přitom zavře. Jednak je tím skopírování hotová věc — kdo si o odkaz řekl, ten ho
     * má —, jednak by snackbar s potvrzením zůstal schovaný POD sheetem a nebylo by poznat,
     * že se něco stalo. Android 13+ ukazuje vlastní náhled schránky, takže tam snackbar odpadá
     * (viz `TextClipboard.confirmsCopy`); iOS a starší Androidy ho nemají.
     */
    fun copyMessageLink(link: String) {
        scope.launch {
            copyToClipboard(link)
            state.update {
                it.closingSheet<MessageInfoSheet>()
                    .copy(notice = if (clipboardConfirmsCopy) it.notice else UiText(Res.string.message_info_link_copied))
            }
        }
    }

    /**
     * Ťuknutí na vlastní zprávu: ukáže u ní odznak přečtení, druhé ťuknutí ho schová.
     *
     * Nic se nikam neposílá — stav přečtení je v `lastReadOutboxMessageId`, posuvném ukazateli
     * na chatu, takže se odvodí porovnáním id. Cizí a servisní zprávy odznak nemají, takže se
     * u nich ťuknutí zahodí.
     */
    fun toggleReadReceipt(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (!message.isOutgoing || message.isService) return

        state.update { it.copy(readReceiptFor = messageId.takeIf { id -> id != it.readReceiptFor }) }
    }

    /**
     * Nabídnout „Vysvětlit"? Se zapnutou AI v zařízení a modelem, který telefon umí (stažený být
     * nemusí — sheet pak řekne, kde ho stáhnout, stejně jako u shrnutí konverzace).
     */
    private suspend fun aiAvailable(task: AiTask): Boolean {
        val ai = onDeviceAi ?: return false
        if (!settings.onDeviceAiEnabled.value) return false
        return runCatching { ai.status(ai.variantFor(task, settings)) != OnDeviceAiStatus.Unsupported }.getOrDefault(false)
    }

    /** Nabídka s kandidáty, které nabídl blok; co z nich server dovolí, se zjistí tady. */
    fun showMessageMenu(
        messageId: Long,
        items: List<MessageMenuAction>,
        pressed: Long? = null,
        pollOptionId: String? = null,
        selection: String? = null,
    ) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return

        scope.launch {
            val sticker = message.media as? MessageMedia.Sticker

            val audio = message.media as? MessageMedia.Audio
            val animation = message.media as? MessageMedia.Animation
            // Tajný chat ne: jeho soubor na serveru k uložení není (stejně jako hudba do profilu).
            val savedGifTask = scope.async {
                if (animation == null || gifs == null || state.value.info?.isSecret == true) {
                    null
                } else {
                    gifs.savedGifs().first().any { it.animation.fileId == animation.fileId }
                }
            }
            // Tajný chat ne: jeho soubor na serveru pro profil není.
            val profileAudioTask = scope.async {
                if (audio == null || profiles == null || MessageMenuAction.ToggleProfileAudio !in items || state.value.info?.isSecret == true) {
                    null
                } else {
                    profiles.isProfileAudio(audio.fileId)
                }
            }
            val (emoji, actions, favorite, inTranslationLanguage) = coroutineScope {
                val emojiTask = async { repository.availableReactions(chatId, messageId) }
                val actionsTask = async { repository.messageActions(chatId, messageId) }
                // Oblíbenost jen u samolepky; seznam oblíbených je krátký a TDLib ho má lokálně.
                val favoriteTask = async {
                    sticker?.let { s ->
                        stickerRepository.favoriteStickers().first().any { it.sendFileId == s.stickerFileId }
                    }
                }
                // Rozpoznání jazyka jen kvůli „Přeložit", a se stropem: nabídka čeká na všechny
                // úlohy a pomalé rozpoznání (studený model) ji nesmí zdržet — pak se prostě nabízí.
                val languageTask = async {
                    val text = languageDetectionText(message.text)
                    if (MessageMenuAction.Translate !in items || text == null) return@async false
                    val detected = withTimeoutOrNull(LANGUAGE_DETECTION_TIMEOUT_MS) { languageDetector.detect(text) }
                    alreadyInLanguage(detected, translation.translationTarget())
                }
                MenuLoad(
                    emojiTask.await().getOrDefault(ReactionChoices.None),
                    actionsTask.await().getOrElse { failure ->
                        logger.w(failure) { "Příznaky zprávy $messageId se nepodařilo zjistit." }
                        MessageActions.Fallback
                    },
                    favoriteTask.await(),
                    languageTask.await(),
                )
            }

            val explainAvailable = MessageMenuAction.Explain in items && aiAvailable(AiTask.Explain)
            // Bez hotového přepisu se shrnutí nenabízí: přepisuje se tlačítkem v bublině, ne kvůli shrnutí.
            val voiceSummaryAvailable = MessageMenuAction.SummarizeVoice in items &&
                message.doneTranscript(localTranscript(messageId)) != null && aiAvailable(AiTask.Summary)
            val contextFindings = if (MessageMenuAction.ContextActions in items) messageContext.contextFindingsOf(message) else emptyList()
            // Kandidát na akce z textu se rozbalí na položky jednotlivých akcí.
            val shownItems = items.flatMap { item ->
                if (item == MessageMenuAction.ContextActions) contextMenuItems(contextFindings, messageContext.canJoinWifi) else listOf(item)
            }
            state.update {
                it.copy(
                    messageMenu = MessageMenu(
                        messageId = messageId,
                        timestamp = message.timestamp,
                        isOutgoing = message.isOutgoing,
                        emoji = emoji.emoji,
                        hasRecentReactions = emoji.hasRecent,
                        chosenEmoji = message.reactions.filter { r -> r.isChosen }.map { r -> r.emoji }.toSet(),
                        actions = actions,
                        items = shownItems,
                        favoriteSticker = favorite,
                        savedGif = savedGifTask.await(),
                        bookmarked = bookmarks?.isBookmarked(chatId, messageId),
                        profileAudio = profileAudioTask.await(),
                        pressedMessageId = pressed?.takeIf { it != messageId },
                        blockFromReplies = it.info?.isReplies == true && !message.isOutgoing,
                        pressedPollOptionId = pollOptionId?.takeIf { id -> message.poll?.options?.any { it.id == id && it.canDelete } == true },
                        bubbleSelection = selection,
                        inTranslationLanguage = inTranslationLanguage,
                        explainAvailable = explainAvailable,
                        voiceSummaryAvailable = voiceSummaryAvailable,
                        contextFindings = contextFindings,
                        resendable = failedGroup(it.messages, messageId).let { group ->
                            group.isNotEmpty() && group.all { m -> m.sendFailure?.canRetry == true }
                        },
                    ),
                )
            }
        }
    }

    /**
     * Přidá zprávu do záložek, nebo ji z nich odebere. Otisk zprávy (text, odesílatel) se skládá
     * teď, v jazyce aplikace — záložka pak v seznamu nepotřebuje zprávu načítat. Výsledek hlásí
     * snackbar, v konverzaci se nic nezmění.
     */
    fun toggleBookmark(messageId: Long) {
        val bookmarks = bookmarks ?: return
        val menu = state.value.messageMenu?.takeIf { it.messageId == messageId } ?: return
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        closeMenus()

        if (menu.bookmarked == true) {
            bookmarks.remove(chatId, messageId)
            state.update { it.copy(notice = UiText(Res.string.conversation_bookmark_removed)) }
            return
        }
        scope.launch {
            // Stejný otisk jako řádek přepisu výběru: „[Fotka] popisek", text, nebo popis.
            val preview = transcriptBody(message, resolve = { it.resolveNow() }).orEmpty()
            val sender = if (message.isOutgoing) null else message.senderName ?: state.value.info?.title
            if (bookmarks.add(chatId, messageId, preview, sender, message.timestamp)) {
                state.update { it.copy(notice = UiText(Res.string.conversation_bookmark_added)) }
            }
        }
    }

    private data class MenuLoad(
        val emoji: ReactionChoices,
        val actions: MessageActions,
        val favorite: Boolean?,
        val inTranslationLanguage: Boolean,
    )

    /**
     * Přidá samolepku ze zprávy do oblíbených, nebo ji z nich odebere.
     *
     * V konverzaci se tím nic nezmění, takže výsledek hlásí snackbar — jinak by po zavření nabídky
     * nebylo poznat, že se něco stalo.
     */
    fun toggleFavoriteSticker(messageId: Long) {
        val menu = state.value.messageMenu?.takeIf { it.messageId == messageId } ?: return
        val favorite = menu.favoriteSticker ?: return
        val sticker = state.value.messages.firstOrNull { it.id == messageId }?.media as? MessageMedia.Sticker
            ?: return
        closeMenus()

        scope.launch {
            stickerRepository.setFavorite(sticker.stickerFileId, !favorite)
                .onSuccess {
                    state.update {
                        it.copy(notice = UiText(if (favorite) Res.string.conversation_favorite_removed else Res.string.conversation_favorite_added))
                    }
                }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_favorite_failed)) }
                }
        }
    }

    /**
     * Uloží GIF ze zprávy mezi uložené GIFy, nebo ho z nich odebere. V konverzaci se nic
     * nezmění, takže výsledek hlásí snackbar, jako u oblíbené samolepky. Uložený se objeví
     * ve výběru GIFů (potvrzení přijde `savedGifs`).
     */
    fun toggleSavedGif(messageId: Long) {
        val gifs = gifs ?: return
        val menu = state.value.messageMenu?.takeIf { it.messageId == messageId } ?: return
        val saved = menu.savedGif ?: return
        val animation = state.value.messages.firstOrNull { it.id == messageId }?.media as? MessageMedia.Animation
            ?: return
        closeMenus()

        scope.launch {
            val gif = GifItem(animation)
            (if (saved) gifs.removeSaved(gif) else gifs.addSaved(gif))
                .onSuccess {
                    state.update {
                        it.copy(notice = UiText(if (saved) Res.string.conversation_saved_gif_removed else Res.string.conversation_saved_gif_added))
                    }
                }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_saved_gif_failed)) }
                }
        }
    }

    /**
     * Přidá skladbu ze zprávy do hudby ve vlastním profilu, nebo ji z ní odebere. V konverzaci
     * se nic nezmění, takže výsledek hlásí snackbar, jako u oblíbené samolepky.
     */
    fun toggleProfileAudio(messageId: Long) {
        val profiles = profiles ?: return
        val menu = state.value.messageMenu?.takeIf { it.messageId == messageId } ?: return
        val inProfile = menu.profileAudio ?: return
        val audio = state.value.messages.firstOrNull { it.id == messageId }?.media as? MessageMedia.Audio ?: return
        closeMenus()

        scope.launch {
            val result = if (inProfile) profiles.removeProfileAudio(audio.fileId) else profiles.addProfileAudio(audio)
            result
                .onSuccess {
                    state.update {
                        it.copy(notice = UiText(if (inProfile) Res.string.conversation_profile_audio_removed else Res.string.conversation_profile_audio_added))
                    }
                }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_profile_audio_failed)) }
                }
        }
    }

    fun closeMenus() {
        state.update { it.copy(messageMenu = null, noticeMenu = null) }
    }

    /**
     * Nabídka nad systémovým oznámením. Nabízí jen smazání, takže když ho server nedovolí
     * (oznámení v kanálu, kde účet není správce), neotevře se vůbec — prázdná nabídka nebo
     * „Smazat", které skončí chybou, by byly horší než nic.
     */
    fun showNoticeMenu(messageId: Long) {
        scope.launch {
            val actions = repository.messageActions(chatId, messageId).getOrElse { failure ->
                logger.w(failure) { "Příznaky oznámení $messageId se nepodařilo zjistit." }
                return@launch
            }
            if (!actions.canDeleteForSelf && !actions.canDeleteForAll) return@launch
            state.update {
                it.copy(noticeMenu = NoticeMenu(messageId, actions.canDeleteForSelf, actions.canDeleteForAll))
            }
        }
    }

    /**
     * Zkopíruje text zprávy do schránky.
     *
     * Kopíruje se text, jak ho vidí uživatel — bez entit. Formátování má smysl v Telegramu,
     * ale do cizí aplikace se stejně přenese jen holý řetězec.
     */
    fun copyMessage(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        copyText(message.copyableText())
    }

    /** Zkopíruje text — celou zprávu, nebo úryvek označený v nabídce nad zprávou. */
    fun copyText(text: String) {
        scope.launch { copyToClipboard(text) }
        closeMenus()
    }
}

/**
 * Strop pro rozpoznání jazyka při otevírání nabídky nad zprávou. Rychlé je v řádu milisekund,
 * jen první volání po startu načítá model; nabídka na něj čekat nemá.
 */
private const val LANGUAGE_DETECTION_TIMEOUT_MS = 300L
