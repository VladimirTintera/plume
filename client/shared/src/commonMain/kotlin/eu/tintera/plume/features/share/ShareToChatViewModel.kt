package eu.tintera.plume.features.share

import eu.tintera.plume.core.accounts.AccountSlot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.conversation.MAX_ATTACHMENTS
import eu.tintera.plume.features.conversation.EditedPhoto
import eu.tintera.plume.features.conversation.PhotoEdit
import eu.tintera.plume.features.conversation.galleryPath
import eu.tintera.plume.features.conversation.markupOrEmpty
import eu.tintera.plume.features.conversation.renderEdit
import eu.tintera.plume.features.conversation.revertedPhoto
import eu.tintera.plume.features.conversation.withEditedPhoto
import eu.tintera.plume.core.ui.PhotoMarkup
import eu.tintera.plume.core.ui.PhotoMarkupRenderer
import eu.tintera.plume.resources.markup_failed
import co.touchlab.kermit.Logger
import eu.tintera.plume.features.conversation.dropForbiddenSelfDestruct
import eu.tintera.plume.features.conversation.selfDestructAllowed
import eu.tintera.plume.features.conversation.sendInBatches
import eu.tintera.plume.features.conversation.stageAttachments
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_attachments_limit
import eu.tintera.plume.resources.conversation_attachments_send_failed
import eu.tintera.plume.resources.conversation_self_destruct_album
import eu.tintera.plume.resources.share_failed_files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Potvrzení sdílení do chatu vybraného už v systémové nabídce (Direct Share).
 *
 * Proti výběru chatu ([ShareViewModel]) je to opačná polovina úlohy: chat je daný a vybírá se,
 * **co a jak** odejde — snímek je vidět velký, popisek se píše k němu a u jedné fotky v soukromém
 * chatu jde zapnout spoiler nebo mizení. Odesílá tahle obrazovka sama, konverzace si obsah
 * nevyzvedává (`ShareRequests.confirm`).
 */
class ShareToChatViewModel(
    private val slot: AccountSlot,
    private val chatId: Long,
    private val requests: ShareRequests,
    private val repository: ConversationRepository,
    private val shareTargets: ShareTargets,
    /**
     * Odeslání běží v rozsahu aplikace, ne ve `viewModelScope`: ten se ruší s odchodem obrazovky
     * a gesto zpět těsně po ťuknutí na odeslat by zprávu zahodilo v půlce.
     */
    private val appScope: CoroutineScope,
    /** Kreslení do fotky před odesláním; bez něj se úprava nenabízí. */
    private val photoMarkup: PhotoMarkupRenderer? = null,
) : ViewModel() {

    private val logger = Logger.withTag("ShareToChat")

    private val _state = MutableStateFlow(ShareToChatState(chatId = chatId))
    val state: StateFlow<ShareToChatState> = _state.asStateFlow()

    /**
     * Popisek je Compose stav přímo tady, ne `StateFlow` — hodnota vrácená o dispatch později
     * poskakuje kurzorem (tatáž past jako u úpravy profilu a vstupního pole konverzace).
     */
    var caption by mutableStateOf(TextFieldValue())
        private set

    /** Sdílený text se do popisku vloží jednou; další průchod stavem by přepsal, co uživatel píše. */
    private var captionFilled = false

    init {
        viewModelScope.launch {
            repository.chatInfo(chatId).onSuccess { info ->
                _state.update {
                    it.copy(
                        chatTitle = info.title,
                        avatarFileId = info.avatarFileId,
                        // `isChannel` se počítá i do `isGroup`, takže se kanál rozlišuje první.
                        kind = when {
                            info.isChannel -> ChatKind.Channel
                            info.isGroup -> ChatKind.Group
                            else -> ChatKind.Private
                        },
                        isSavedMessages = info.isSavedMessages,
                    )
                }
            }
        }

        viewModelScope.launch {
            requests.incoming.collect { content ->
                /*
                 * Obsah zmizel (zabití procesu, zahozený) — obrazovka nemá co potvrzovat.
                 *
                 * Vlastní odeslání se sem NESMÍ započítat: vyřídí sdílení (`consume`), takže
                 * `incoming` zhasne i při úspěchu. Bez téhle podmínky obrazovka odešla jako
                 * „zavřít" a aplikace po odeslání skočila na plochu, místo do konverzace.
                 */
                if (content == null) {
                    val ours = _state.value.sent || _state.value.sending
                    if (!ours) _state.update { it.copy(gone = true) }
                    return@collect
                }
                /*
                 * Nové sdílení jinam (jiný chat, nebo výběr chatu) tuhle obrazovku NEZAVÍRÁ —
                 * shell ji kvůli němu nahradí. Kdyby odešla sama, poslala by uživatele do
                 * předchozí aplikace a nové sdílení by se neukázalo vůbec.
                 */
                if (content.targetSlot != slot || content.targetChatId != chatId || !content.awaitingConfirmation) return@collect
                if (content.id != _state.value.contentId) {
                    /*
                     * Nové sdílení do TÉHOŽ chatu: shell obrazovku nechává (je to tatáž routa),
                     * takže se přenastaví na nový obsah. Rozdělaný popisek, režimy i odebrané
                     * snímky patřily předchozímu sdílení — nové ho nahrazuje, jako všude jinde.
                     */
                    if (_state.value.sending) return@collect
                    _state.update {
                        ShareToChatState(
                            chatId = chatId,
                            contentId = content.id,
                            chatTitle = it.chatTitle,
                            avatarFileId = it.avatarFileId,
                            kind = it.kind,
                            isSavedMessages = it.isSavedMessages,
                        )
                    }
                    caption = TextFieldValue()
                    captionFilled = false
                }
                if (!captionFilled) {
                    content.text?.let { caption = TextFieldValue(it, TextRange(it.length)) }
                    captionFilled = true
                }
                val prepared = content.attachments ?: return@collect
                // Přílohy si dál drží obrazovka: režimy a odebírání jsou její věc, ne stav sdílení.
                if (_state.value.attachments == null) {
                    _state.update {
                        it.copy(
                            attachments = prepared,
                            sources = content.sources,
                            accessDenied = prepared.isEmpty() && content.denied > 0,
                            // Co se nepodařilo připravit, se taky neposlalo — uživatel to má vědět.
                            // Když nezbylo nic kvůli oprávnění, říká to obrazovka sama a líp.
                            notice = when {
                                prepared.isEmpty() && content.denied > 0 -> it.notice
                                content.failed > 0 -> UiText(Res.string.share_failed_files)
                                else -> it.notice
                            },
                        )
                    }
                }
            }
        }
    }

    fun onCaptionChange(value: TextFieldValue) {
        caption = value
    }

    /**
     * Ubere snímek z odesílané dávky. Odebrání posledního nemá co poslat, takže se sdílení zahodí
     * a obrazovka odejde — ✕ na jediném snímku tedy znamená totéž co ✕ v hlavičce.
     */
    fun removeAttachment(index: Int) {
        val current = _state.value.attachments ?: return
        val rest = current.filterIndexed { i, _ -> i != index }
        if (rest.isEmpty()) {
            _state.update { it.copy(gone = true) }
            return
        }
        _state.update { it.copy(attachments = rest, removals = it.removals + 1) }
    }

    /**
     * Fotky a videa dobrané z galerie pod popiskem (nebo systémovým výběrem, kde galerie není). Jdou ZA sdílené — to, kvůli čemu uživatel
     * sdílel, zůstává první a v albu nahoře.
     *
     * Tatáž pravidla jako přílohy nad vstupním polem: strop alba a mizení jen u jediné fotky.
     * Z mizející fotky se přidáním druhé stává album, takže se mizení viditelně zruší a ohlásí.
     */
    fun addAttachments(picked: List<OutgoingAttachment>) {
        val current = _state.value.attachments ?: return
        val staged = stageAttachments(current, picked)
        val dropped = dropForbiddenSelfDestruct(
            staged.attachments,
            allowed = _state.value.copy(attachments = staged.attachments).canSelfDestruct,
        )
        _state.update {
            it.copy(
                attachments = dropped ?: staged.attachments,
                notice = when {
                    staged.overflow -> UiText(Res.string.conversation_attachments_limit, MAX_ATTACHMENTS)
                    dropped != null -> UiText(Res.string.conversation_self_destruct_album)
                    else -> it.notice
                },
            )
        }
    }

    /**
     * Odznačené v galerii. Obrazovka tím NEODCHÁZÍ ani u posledního snímku — na rozdíl od ✕ na
     * snímku: odznačení je oprava výběru, ne rozhodnutí nic neposlat.
     */
    fun removeAttachments(paths: List<String>) {
        if (paths.isEmpty()) return
        val removed = paths.toSet()
        _state.update { state ->
            // Upravenou fotku zná galerie pod původní cestou (viz `galleryPath`).
            state.copy(attachments = state.attachments?.filterNot { state.photoEdits.galleryPath(it.path) in removed })
        }
    }

    /** Nabízí se úprava fotky? */
    val canMarkupPhotos: Boolean get() = photoMarkup?.isAvailable == true

    /**
     * Uloží úpravu fotky — stejně jako nad vstupním polem konverzace (`PhotoEdits`): vykreslí se
     * z originálu do nové kopie, prázdná úprava vrátí originál, fotka se hledá podle cesty.
     */
    fun applyPhotoMarkup(path: String, markup: PhotoMarkup) {
        val renderer = photoMarkup ?: return
        val state = _state.value
        val photo = state.attachments?.firstOrNull { it.path == path } as? OutgoingAttachment.Photo ?: return
        val edit = state.photoEdits[path]
        if (markup == edit.markupOrEmpty) return
        if (markup.isEmpty) {
            replacePhoto(path, revertedPhoto(photo, edit))
            return
        }
        _state.update { it.copy(markupBusy = true) }
        viewModelScope.launch {
            runCatching { renderer.renderEdit(photo, edit, markup) }
                .onSuccess { edited -> replacePhoto(path, edited) }
                .onFailure { failure ->
                    logger.w(failure) { "Úpravu sdílené fotky $path se nepodařilo vykreslit." }
                    _state.update { it.copy(notice = UiText(Res.string.markup_failed)) }
                }
            _state.update { it.copy(markupBusy = false) }
        }
    }

    private fun replacePhoto(path: String, edited: EditedPhoto) {
        _state.update { state ->
            state.copy(
                attachments = state.attachments?.withEditedPhoto(path, edited),
                // `sources` zůstávají pod původní cestou: galerie dostává cesty přes `galleryPath`.
                photoEdits = state.photoEdits.withEditedPhoto(path, edited),
            )
        }
    }

    fun setMode(index: Int, mode: AttachmentMode) {
        val current = _state.value.attachments ?: return
        val selfDestruct = mode is AttachmentMode.ViewOnce || mode is AttachmentMode.Timer
        // Volba se nenabízí tam, kde neprojde; kdyby sem přesto došla, zpráva by skončila chybou.
        if (selfDestruct && !_state.value.canSelfDestruct) return
        val updated = current.mapIndexed { i, attachment ->
            if (i != index) {
                attachment
            } else {
                when (attachment) {
                    is OutgoingAttachment.Photo -> attachment.copy(mode = mode)
                    is OutgoingAttachment.Video -> attachment.copy(mode = mode)
                    is OutgoingAttachment.Document, is OutgoingAttachment.Audio -> attachment
                }
            }
        }
        _state.update { it.copy(attachments = updated) }
    }

    /**
     * Odešle, co je na obrazovce, a teprve pak se otevře konverzace — je tím vidět, že zpráva
     * dorazila (stejně jako u přeposílání).
     *
     * Sdílení se vyřizuje (`consume`) ještě před odchodem: konverzace odebírá `deliveries` a bez
     * toho by jí tytéž přílohy naskočily nad vstupní pole podruhé.
     */
    fun send() {
        // Jen sdílení, které obrazovka ukazuje — nové mohlo přijít těsně před ťuknutím.
        val content = requests.incoming.value?.takeIf { it.id == _state.value.contentId } ?: return
        val attachments = _state.value.attachments ?: return
        // Během vykreslování úpravy má fotka ještě cestu k NEUPRAVENÉ — odešla by i se začerněným.
        if (_state.value.sending || _state.value.markupBusy || attachments.isEmpty()) return
        _state.update { it.copy(sending = true) }

        val text = caption.text.trim()
        val sending = appScope.async {
            // Víc než deset příloh odejde v několika albech (`sendInBatches`).
            repository.sendInBatches(
                chatId = chatId,
                attachments = attachments,
                separately = false,
                caption = text,
                replyToMessageId = null,
                quote = null,
                options = SendOptions(),
            )
        }
        viewModelScope.launch {
            val result = sending.await()
            if (result.sent > 0) {
                // Pořadí chatů v nabídce „Sdílet" si systém skládá z toho, jak často
                // zkratku publikujeme — odeslání se proto hlásí i odsud.
                shareTargets.reportUsed(chatId)
            }
            if (result.failure == null) {
                // Stav napřed, vyřízení sdílení až po něm: `consume` zhasne `incoming`
                // a odběr výš by to jinak stihl přečíst jako „obsah zmizel".
                _state.update { it.copy(sending = false, sent = true) }
                requests.consume(content.id)
                return@launch
            }
            // Odeslaná alba z obrazovky zmizí i s popiskem, který šel s prvním; druhý pokus
            // pošle jen zbytek.
            if (result.sent > 0) caption = TextFieldValue()
            _state.update {
                it.copy(
                    sending = false,
                    attachments = it.attachments?.drop(result.sent),
                    notice = UiText(Res.string.conversation_attachments_send_failed),
                )
            }
        }
    }

    /** Obsah se zahodí; volá to obrazovka při odchodu bez odeslání, jako výběr chatu. */
    fun abandon() {
        if (!_state.value.sent) _state.value.contentId?.let(requests::abandon)
    }

    /** Chyba z galerie (fotku se nepodařilo připravit) — ukáže se jako hláška. */
    fun reportError(message: UiText) = _state.update { it.copy(notice = message) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }
}

data class ShareToChatState(
    val chatId: Long = 0,
    /**
     * Sdílení, které obrazovka ukazuje. Nové sdílení do téhož chatu ho vymění a obrazovka se
     * s ním přenastaví; odchod zahazuje jen tohle, ne novější.
     */
    val contentId: Long? = null,
    val chatTitle: String = "",
    val avatarFileId: Int? = null,
    /** Tvar tváře v hlavičce — jako v seznamu chatů a v konverzaci. */
    val kind: ChatKind = ChatKind.Private,
    val isSavedMessages: Boolean = false,
    /** `null`, dokud se soubory připravují. */
    val attachments: List<OutgoingAttachment>? = null,
    /** Upravené fotky podle současné cesty (viz `PhotoEdit`). */
    val photoEdits: Map<String, PhotoEdit> = emptyMap(),
    /** Úprava fotky se právě vykresluje do souboru. */
    val markupBusy: Boolean = false,
    val sending: Boolean = false,
    /** Odesláno — obrazovka otevře konverzaci. */
    val sent: Boolean = false,
    /** Není co potvrzovat (obsah zmizel nebo ho uživatel odebral) — obrazovka odejde. */
    val gone: Boolean = false,
    val notice: UiText? = null,
    /**
     * Kolikrát uživatel snímek odebral ✕. Galerie si zaškrtnutí obnovuje jen při otevření,
     * takže se podle tohohle počítadla otevře znovu — jinak by v ní odebraná fotka zůstala
     * zaškrtnutá.
     */
    val removals: Int = 0,
    /** Odkud přišly sdílené přílohy (cesta → URI); galerie podle toho sdílenou fotku nepřidá podruhé. */
    val sources: Map<String, String> = emptyMap(),
    /**
     * Sdílený snímek nedorazil, protože ho sdílející aplikace nedovolila přečíst — typicky
     * screenshot sdílený z jeho náhledu na kontakt (viz `ShareRequests.prepared`). Obrazovka
     * místo prázdna radí vybrat ho v galerii.
     */
    val accessDenied: Boolean = false,
) {
    /** Mizení jen u jediné fotky v soukromém chatu — tytéž meze jako nad vstupním polem. */
    val canSelfDestruct: Boolean
        get() = selfDestructAllowed(
            isPrivateChat = kind == ChatKind.Private,
            isSavedMessages = isSavedMessages,
            attachmentCount = attachments?.size ?: 0,
        )
}
