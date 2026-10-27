package eu.tintera.plume.features.conversation

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.location.GeoPoint
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.telegram.LiveFix
import eu.tintera.plume.core.telegram.LiveLocationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.OutgoingAnimation
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.Place
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.within
import eu.tintera.plume.core.ui.PhotoMarkup
import eu.tintera.plume.core.ui.PhotoMarkupRenderer
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_attachments_limit
import eu.tintera.plume.resources.conversation_attachments_send_failed
import eu.tintera.plume.resources.recent_files_failed
import eu.tintera.plume.resources.conversation_contact_load_failed
import eu.tintera.plume.resources.conversation_contact_no_phone
import eu.tintera.plume.resources.conversation_contact_send_failed
import eu.tintera.plume.resources.conversation_content_not_allowed
import eu.tintera.plume.resources.conversation_gif_send_failed
import eu.tintera.plume.resources.conversation_location_send_failed
import eu.tintera.plume.resources.conversation_scheduled_notice
import eu.tintera.plume.resources.conversation_self_destruct_album
import eu.tintera.plume.resources.conversation_sticker_send_failed
import eu.tintera.plume.resources.location_live_stop_failed
import eu.tintera.plume.resources.location_live_unavailable
import eu.tintera.plume.resources.location_proximity_failed
import eu.tintera.plume.resources.markup_failed
import eu.tintera.plume.resources.share_failed_files
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Co se posílá mimo napsaný text: samolepky a GIFy, poloha (i živá a upozornění na blízkost),
 * kontakt, přílohy nad polem (výběr, úprava fotky, režim, spoiler, mizení) a jejich odeslání
 * s popiskem, obsah sdílený ze systému.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationAttachments(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val repository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settings: AppSettings,
    private val shareRequests: ShareRequests,
    /** Do chatu se něco poslalo (`ShareTargets.reportUsed`); funkce kvůli testu, jako u psaní. */
    private val reportUsed: (chatId: Long) -> Unit,
    private val liveLocations: LiveLocationRepository,
    /** Kreslení do fotky před odesláním; bez něj se „Upravit" nenabízí. */
    private val photoMarkup: PhotoMarkupRenderer?,
    /** Popisek příloh je rozepsaný text; odeslání spotřebuje odpověď a koncept. */
    private val composer: ConversationComposer,
    private val suggestions: ConversationSuggestions,
    private val logger: Logger,
) {
    /** Poslední otevřená část výběru emoji/samolepek; řídí ikonu ve vstupním poli. */
    val expressionMode: StateFlow<ExpressionMode> = settings.expressionMode

    /**
     * Uloží, která část výběru byla otevřená. Zápis běží v `appScope`: přepnutí těsně před odchodem
     * z chatu se musí uložit, i když se `scope` mezitím zruší.
     */
    fun setExpressionMode(mode: ExpressionMode) {
        appScope.launch { settings.setExpressionMode(mode) }
    }

    /**
     * Odešle samolepku z výběru.
     *
     * Bez letu ze vstupního pole — samolepka z pole nevyletěla, přišla ze sheetu, který se právě
     * zavírá. Rozepsaný text zůstává: samolepka ho nenahrazuje, jen se odeslala mezi tím.
     * Citace se spotřebuje stejně jako u textu.
     */
    fun sendSticker(sticker: StickerItem, options: SendOptions = SendOptions()) {
        // Výběr samolepek se bez práva neotevře, ale sada jde otevřít i ťuknutím na samolepku
        // v historii — a z ní se posílá taky. Hláška je lepší než chyba od serveru.
        if (!state.value.content.stickers) {
            state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        composer.sendMedia(Res.string.conversation_sticker_send_failed, scheduled = options.schedule != null) { replyTo, quote ->
            repository.sendSticker(chatId, sticker, replyTo, quote, options)
        }
    }

    /** Odešle GIF z výběru. Stejné právo a stejné chování jako [sendSticker]. */
    fun sendGif(gif: GifItem, options: SendOptions = SendOptions()) {
        if (!state.value.content.stickers) {
            state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        composer.sendMedia(Res.string.conversation_gif_send_failed, scheduled = options.schedule != null) { replyTo, quote ->
            repository.sendGif(chatId, gif, replyTo, quote, options)
        }
    }

    /**
     * Odešle polohu (sponka → Poloha): vlastní polohu, nebo místo z hledání. Hned, bez čekání nad
     * polem, jako samolepku — rozepsaný text zůstává a citace se spotřebuje. Poloha je v TDLib
     * „základní zpráva" (`canSendBasicMessages`), takže platí totéž právo jako pro psaní.
     */
    fun sendLocation(place: Place) {
        if (!state.value.canWrite || state.value.editing != null) return
        composer.sendMedia(Res.string.conversation_location_send_failed) { replyTo, quote ->
            repository.sendLocation(chatId, place, replyTo, quote)
        }
    }

    /**
     * Začne sdílet živou polohu na [livePeriod] sekund (nebo „dokud nevypnu"), z polohy [point],
     * kterou už zjistila obrazovka. Další polohy posílá platforma ([LiveLocationRepository]
     * zprávu uvidí v seznamu aktivních a služba se spustí). `null` = poloha nedovolená nebo
     * nezjištěná — musí to být vidět, jinak by volba vypadala, že nic neudělala.
     */
    fun shareLiveLocation(point: GeoPoint?, livePeriod: Int) {
        if (!state.value.canWrite || state.value.editing != null) return
        if (point == null) {
            state.update { it.copy(notice = UiText(Res.string.location_live_unavailable)) }
            return
        }
        composer.sendMedia(Res.string.conversation_location_send_failed) { replyTo, quote ->
            repository.sendLiveLocation(chatId, LiveFix(point.latitude, point.longitude), livePeriod, replyTo, quote)
        }
    }

    /** „Zastavit sdílení" pod vlastní živou polohou. Potvrzení přijde úpravou zprávy. */
    fun stopLiveLocation(messageId: Long) {
        scope.launch {
            liveLocations.stop(chatId, messageId).onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.location_live_stop_failed)) }
            }
        }
    }

    /** „Upozornit na blízkost" pod vlastní živou polohou: dialog se vzdálenostmi. */
    fun openProximityAlert(messageId: Long) {
        // Z docku jde i u zprávy, která v načtené historii není.
        val message = state.value.messages.firstOrNull { it.id == messageId }
            ?: state.value.liveLocations.firstOrNull { it.id == messageId }
        val radius = (message?.media as? MessageMedia.Location)?.proximityAlertRadius ?: return
        state.update { it.copy(dialog = ConversationDialog.ProximityAlert(ProximityAlertPrompt(messageId, radius))) }
    }

    fun dismissProximityAlert() {
        state.update { it.closing<ConversationDialog.ProximityAlert>() }
    }

    /**
     * Nastaví upozornění na blízkost ([radiusMeters] 0 ho vypne). Výsledek přijde úpravou zprávy
     * jako každý posun polohy, takže se tady nic lokálně nepřepisuje.
     */
    fun setProximityAlert(radiusMeters: Int) {
        val prompt = state.value.dialog<ConversationDialog.ProximityAlert>()?.prompt ?: return
        state.update { it.closing<ConversationDialog.ProximityAlert>() }
        scope.launch {
            liveLocations.setProximityAlert(chatId, prompt.messageId, radiusMeters).onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.location_proximity_failed)) }
            }
        }
    }

    /**
     * Sponka → Kontakt: sheet se seznamem kontaktů. Kontakty se čtou při otevření — sheet se
     * otevírá jednou za čas a seznam by jinak ležel v paměti každé konverzace.
     */
    fun openContactPicker() {
        state.update { it.copy(sheet = ContactPicker(loading = true)) }
        scope.launch {
            // Vlastní účet: Uložené zprávy jsou chat s ním, id chatu je id uživatele. Kdo nemá
            // číslo vidět (nikdy by nemělo nastat), řádek nedostane.
            val me = chatRepository.savedMessagesChatId.value
                ?.let { repository.contactCard(it).getOrNull() }
                ?.takeIf { it.phone.isNotBlank() }
            chatRepository.contacts()
                .onSuccess { contacts ->
                    state.update { current ->
                        current.updatingSheet<ContactPicker> { open -> open.copy(loading = false, contacts = contacts, me = me) }
                    }
                }
                .onFailure { failure ->
                    state.update {
                        it.closingSheet<ContactPicker>().copy(error = failure.reported(logger, Res.string.conversation_contact_load_failed))
                    }
                }
        }
    }

    fun searchContacts(query: String) {
        state.update { current -> current.updatingSheet<ContactPicker> { open -> open.copy(query = query) } }
    }

    fun dismissContactPicker() {
        state.update { it.closingSheet<ContactPicker>() }
    }

    private var recentFilesJob: Job? = null

    /** Sheet nedávno odeslaných souborů (sponka → Poslední soubory). */
    fun openRecentFiles() {
        state.update { it.copy(sheet = RecentFilesSheet()) }
        loadRecentFiles(query = "", debounce = false)
    }

    /** Hledá server (v názvu i popisku), chvíli po posledním písmenu; výběr zůstává. */
    fun searchRecentFiles(query: String) {
        state.update { current -> current.updatingSheet<RecentFilesSheet> { open -> open.copy(query = query) } }
        loadRecentFiles(query.trim(), debounce = true)
    }

    private fun loadRecentFiles(query: String, debounce: Boolean) {
        recentFilesJob?.cancel()
        recentFilesJob = scope.launch {
            if (debounce) delay(RECENT_FILES_SEARCH_DELAY_MS)
            repository.recentFiles(query)
                .onSuccess { files ->
                    state.update { current -> current.updatingSheet<RecentFilesSheet> { open -> open.copy(files = files, error = null) } }
                }
                .onFailure { failure ->
                    val error = failure.reported(logger, Res.string.recent_files_failed)
                    state.update { current ->
                        current.updatingSheet<RecentFilesSheet> { open -> open.copy(files = open.files ?: emptyList(), error = error) }
                    }
                }
        }
    }

    fun toggleRecentFile(fileId: Int) {
        state.update { current ->
            val sheet = current.sheet<RecentFilesSheet>() ?: return@update current
            val selected = when {
                fileId in sheet.selected -> sheet.selected - fileId
                sheet.canSelectMore -> sheet.selected + fileId
                else -> sheet.selected
            }
            current.copy(sheet = sheet.copy(selected = selected))
        }
    }

    fun dismissRecentFiles() {
        recentFilesJob?.cancel()
        state.update { it.closingSheet<RecentFilesSheet>() }
    }

    /**
     * Vybrané soubory odejdou hned, ne přes pás příloh nad polem: ten stojí na místních
     * souborech, kdežto tyhle server už má a posílají se podle id. Napsaný text v poli zůstane.
     */
    fun sendRecentFiles() {
        val sheet = state.value.sheet<RecentFilesSheet>() ?: return
        val fileIds = sheet.selected.takeIf { it.isNotEmpty() } ?: return
        val replyTo = state.value.replyingTo?.id
        val quote = state.value.replyQuote
        composer.clearReply()
        dismissRecentFiles()
        scope.launch {
            repository.sendFiles(chatId, fileIds, replyTo, quote)
                .onSuccess { pending ->
                    reportUsed(chatId)
                    state.update { current ->
                        // Potvrzení mohlo dorazit dřív než výsledek — viz `send`.
                        val fresh = pending.filterNot { it.id in current.itemKeys.values }
                        current.copy(messages = merge(current.messages, fresh))
                    }
                }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_attachments_send_failed)) }
                }
        }
    }

    /**
     * Vybraný kontakt se neposílá hned: dialog ukáže jméno a ČÍSLO, které odejde. Posílá se cizí
     * telefonní číslo a uživatel má vidět přesně co. Kontakt se skrytým číslem poslat nejde.
     */
    fun pickContact(userId: Long) {
        state.update { it.closingSheet<ContactPicker>() }
        scope.launch {
            repository.contactCard(userId)
                .onSuccess { card ->
                    state.update {
                        if (card.phone.isBlank()) {
                            it.copy(notice = UiText(Res.string.conversation_contact_no_phone))
                        } else {
                            it.copy(dialog = ConversationDialog.ShareContact(card))
                        }
                    }
                }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_contact_send_failed)) }
                }
        }
    }

    fun cancelContact() {
        state.update { it.closing<ConversationDialog.ShareContact>() }
    }

    /** Kontakt odchází hned, jako poloha: citaci spotřebuje, rozepsaný text nechá. */
    fun confirmContact() {
        val userId = state.value.dialog<ConversationDialog.ShareContact>()?.contact?.userId ?: return
        state.update { it.closing<ConversationDialog.ShareContact>() }
        if (!state.value.canWrite || state.value.editing != null) return
        composer.sendMedia(Res.string.conversation_contact_send_failed) { replyTo, quote ->
            repository.sendContact(chatId, userId, replyTo, quote)
        }
    }

    /**
     * Odešle GIF vložený z klávesnice — hned, bez čekání nad polem, jako samolepku. Rozepsaný text
     * zůstává a citace se spotřebuje. GIFy vede TDLib pod týmž právem jako samolepky
     * (`canSendOtherMessages`); klávesnici se bez něj GIF nenabízí, ale pojistka patří i sem.
     */
    fun sendAnimation(animation: OutgoingAnimation) {
        if (!state.value.content.stickers || state.value.editing != null) {
            state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        composer.sendMedia(Res.string.conversation_gif_send_failed) { replyTo, quote ->
            repository.sendAnimation(chatId, animation, replyTo, quote)
        }
    }

    /**
     * Přílohy z výběru se **neposílají hned**, ale čekají nad vstupním polem: uživatel k nim napíše
     * popisek, některou odebere nebo přidá další, a teprve odeslání je pošle (jednu jako zprávu,
     * víc jako album). Pravidla skládání viz [stageAttachments].
     */
    fun addAttachments(picked: List<OutgoingAttachment>) {
        // Výběr fotek vydá fotky i videa najednou; co chat nepřijme, se sem nedostane.
        val allowed = allowedByContent(picked, state.value.content)
        val forbidden = allowed.size < picked.size
        val staged = stageAttachments(state.value.attachments, allowed)
        // Z jedné mizející fotky se přidáním další stává album, a v něm mizení není dovolené.
        val dropped = dropForbiddenSelfDestruct(
            staged.attachments,
            allowed = state.value.canSelfDestruct(staged.attachments.size),
        )
        state.update {
            it.copy(
                attachments = dropped ?: staged.attachments,
                notice = when {
                    forbidden -> UiText(Res.string.conversation_content_not_allowed)
                    staged.overflow -> UiText(Res.string.conversation_attachments_limit, MAX_ATTACHMENTS)
                    dropped != null -> UiText(Res.string.conversation_self_destruct_album)
                    else -> it.notice
                },
            )
        }
    }

    /**
     * Obsah sdílený ze systému: soubory k připraveným přílohám nad polem, text do pole.
     *
     * Nic se neposílá — uživatel má vidět, co odejde, a k souborům napsat popisek. Rozepsaný
     * koncept se **doplní, nepřepíše**: kdo sdílí odkaz do chatu, kde má něco rozepsaného,
     * nechce o to přijít.
     */
    fun receiveShared(content: SharedContent) {
        // Tentýž chat může být v zásobníku dvakrát (zmínka tam a zpátky) a obě konverzace
        // odebírají totéž doručení — vloží ho jen ta, která si ho vyzvedla první.
        if (!shareRequests.consume(content.id)) return
        content.attachments?.takeIf { it.isNotEmpty() }?.let(::addAttachments)
        content.text?.let { shared ->
            val text = if (composer.draft.text.isBlank()) shared else composer.draft.text.trimEnd() + "\n" + shared
            // Formátování konceptu platí dál — sdílený text se připojuje až za něj.
            val formats = if (composer.draft.text.isBlank()) emptyList() else composer.draft.formats.within(text.length)
            state.update { it.copy(inputFill = InputFill(text, onlyIfEmpty = false, formats = formats)) }
            /*
             * Pole se vyplní bez `onDraftChanged`, takže se text zapisuje sám: jinak by ho
             * ViewModel neznal a první uložení konceptu (třeba po změně volby náhledu) by
             * zapsalo prázdný koncept — sdílený text by po odchodu z chatu zmizel.
             */
            composer.draft = ComposedText(text, formats)
            composer.scheduleDraftSave()
            // Sdílený odkaz je přitom typický případ náhledu.
            composer.refreshLinkPreview(text)
        }
        if (content.failed > 0) {
            state.update { it.copy(notice = UiText(Res.string.share_failed_files)) }
        }
    }

    fun removeAttachment(index: Int) {
        state.update { current ->
            current.copy(attachments = current.attachments.filterIndexed { i, _ -> i != index })
        }
    }

    /**
     * Odebere přílohy podle CESTY — to je jediné, co o nich ví vestavěná galerie: odznačenou
     * fotku hlásí jako soubor, ne jako pořadí nad polem (viz `EmbeddedMediaPicker`). Upravená
     * fotka má cestu novou, galerie ale zná tu původní ([galleryPath]).
     */
    fun removeAttachments(paths: List<String>) {
        if (paths.isEmpty()) return
        val removed = paths.toSet()
        state.update { current ->
            current.copy(attachments = current.attachments.filterNot { current.galleryPath(it.path) in removed })
        }
    }

    /** Nabízí se úprava fotky? */
    val canMarkupPhotos: Boolean get() = photoMarkup?.isAvailable == true

    /**
     * Uloží úpravu fotky nad polem: tahy, otočení a ořez se vykreslí do NOVÉ kopie originálu
     * a příloha dostane její cestu i rozměry. Prázdná úprava vrátí originál. Fotka se hledá
     * podle cesty, ne pořadí — mezitím mohla ubýt jiná příloha.
     */
    fun applyPhotoMarkup(path: String, markup: PhotoMarkup) {
        val renderer = photoMarkup ?: return
        val current = state.value
        val photo = current.attachments.firstOrNull { it.path == path } as? OutgoingAttachment.Photo ?: return
        val edit = current.photoEdits[path]
        if (markup == edit.markupOrEmpty) return
        if (markup.isEmpty) {
            replacePhoto(path, revertedPhoto(photo, edit))
            return
        }
        state.update { it.copy(markupBusy = true) }
        scope.launch {
            runCatching { renderer.renderEdit(photo, edit, markup) }
                .onSuccess { edited -> replacePhoto(path, edited) }
                .onFailure { failure ->
                    logger.w(failure) { "Úpravu fotky $path se nepodařilo vykreslit." }
                    state.update { it.copy(notice = UiText(Res.string.markup_failed)) }
                }
            state.update { it.copy(markupBusy = false) }
        }
    }

    private fun replacePhoto(path: String, edited: EditedPhoto) {
        state.update { current ->
            current.copy(
                attachments = current.attachments.withEditedPhoto(path, edited),
                photoEdits = current.photoEdits.withEditedPhoto(path, edited),
            )
        }
    }

    fun setAttachmentMode(index: Int, mode: AttachmentMode) {
        val selfDestruct = mode is AttachmentMode.ViewOnce || mode is AttachmentMode.Timer
        // Volba se nenabízí tam, kde neprojde; kdyby sem přesto došla, zpráva by skončila chybou.
        if (selfDestruct && !state.value.canSelfDestruct()) return
        state.update { current ->
            val updated = current.attachments.mapIndexed { i, attachment ->
                if (i == index) {
                    when (attachment) {
                        is OutgoingAttachment.Photo -> attachment.copy(mode = mode)
                        is OutgoingAttachment.Video -> attachment.copy(mode = mode)
                        is OutgoingAttachment.Document, is OutgoingAttachment.Audio -> attachment
                    }
                } else {
                    attachment
                }
            }
            current.copy(attachments = updated)
        }
    }

    fun toggleAttachmentSpoiler(index: Int) {
        val current = state.value.attachments.getOrNull(index) ?: return
        val newMode = if (current.mode is AttachmentMode.Spoiler) AttachmentMode.Standard else AttachmentMode.Spoiler
        setAttachmentMode(index, newMode)
    }

    fun setAttachmentSelfDestruct(index: Int, type: SelfDestructType?) {
        val newMode = when (type) {
            is SelfDestructType.Immediately -> AttachmentMode.ViewOnce
            is SelfDestructType.Timer -> AttachmentMode.Timer(type.seconds)
            null -> AttachmentMode.Standard
        }
        setAttachmentMode(index, newMode)
    }

    fun sendAttachments(caption: ComposedText, attachments: List<OutgoingAttachment>, options: SendOptions) {
        composer.typingSentAt = null
        composer.draftSave?.cancel()
        composer.draftSave = null
        composer.draft = ComposedText("")
        composer.resetLinkPreview()

        val replyTo = state.value.replyingTo?.id
        val quote = state.value.replyQuote
        val mentions = suggestions.takeNameMentions()
        composer.clearReply()
        state.update { it.copy(attachments = emptyList()) }

        scope.launch {
            repository.sendAttachments(chatId, attachments, caption.text, replyTo, quote, options, mentions, caption.formats)
                .onSuccess { pending ->
                    reportUsed(chatId)
                    if (options.schedule != null) {
                        state.update { it.copy(notice = UiText(Res.string.conversation_scheduled_notice)) }
                        repository.saveDraft(chatId, "", null)
                        return@onSuccess
                    }
                    state.update { current ->
                        // Potvrzení mohlo dorazit dřív než výsledek — viz `send`.
                        val fresh = pending.filterNot { it.id in current.itemKeys.values }
                        current.copy(messages = merge(current.messages, fresh))
                    }
                    repository.saveDraft(chatId, "", null)
                }
                .onFailure { failure ->
                    state.update {
                        it.copy(
                            error = failure.reported(logger, Res.string.conversation_attachments_send_failed),
                            attachments = it.attachments.ifEmpty { attachments },
                        )
                    }
                }
        }
    }
}

/** Jak dlouho po posledním písmenu se nedávné soubory hledají. */
private const val RECENT_FILES_SEARCH_DELAY_MS = 300L
