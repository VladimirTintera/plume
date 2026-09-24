package eu.tintera.plume.features.share

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
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.conversation.MAX_ATTACHMENTS
import eu.tintera.plume.features.conversation.dropForbiddenSelfDestruct
import eu.tintera.plume.features.conversation.selfDestructAllowed
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
    private val chatId: Long,
    private val requests: ShareRequests,
    private val repository: ConversationRepository,
    private val shareTargets: ShareTargets,
    /**
     * Odeslání běží v rozsahu aplikace, ne ve `viewModelScope`: ten se ruší s odchodem obrazovky
     * a gesto zpět těsně po ťuknutí na odeslat by zprávu zahodilo v půlce.
     */
    private val appScope: CoroutineScope,
) : ViewModel() {

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
                 * Obsah zmizel (zabití procesu, nové sdílení jinam) — obrazovka nemá co potvrzovat.
                 *
                 * Vlastní odeslání se sem NESMÍ započítat: vyřídí sdílení (`consume`), takže
                 * `incoming` zhasne i při úspěchu. Bez téhle podmínky obrazovka odešla jako
                 * „zavřít" a aplikace po odeslání skočila na plochu, místo do konverzace.
                 */
                if (content == null || content.targetChatId != chatId) {
                    val ours = _state.value.sent || _state.value.sending
                    if (!ours) _state.update { it.copy(gone = true) }
                    return@collect
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
                            // Co se nepodařilo připravit, se taky neposlalo — uživatel to má vědět.
                            notice = if (content.failed > 0) UiText(Res.string.share_failed_files) else it.notice,
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
     * Fotky a videa dobrané z galerie pod popiskem. Jdou ZA sdílené — to, kvůli čemu uživatel
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
            state.copy(attachments = state.attachments?.filterNot { it.path in removed })
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
                    is OutgoingAttachment.Document -> attachment
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
        val content = requests.incoming.value ?: return
        val attachments = _state.value.attachments ?: return
        if (_state.value.sending || attachments.isEmpty()) return
        _state.update { it.copy(sending = true) }

        val text = caption.text.trim()
        val sending = appScope.async {
            repository.sendAttachments(chatId, attachments, text, replyToMessageId = null)
        }
        viewModelScope.launch {
            sending.await()
                .onSuccess {
                    // Pořadí chatů v nabídce „Sdílet" si systém skládá z toho, jak často
                    // zkratku publikujeme — odeslání se proto hlásí i odsud.
                    shareTargets.reportUsed(chatId)
                    // Stav napřed, vyřízení sdílení až po něm: `consume` zhasne `incoming`
                    // a odběr výš by to jinak stihl přečíst jako „obsah zmizel".
                    _state.update { it.copy(sending = false, sent = true) }
                    requests.consume(content.id)
                }
                .onFailure {
                    _state.update {
                        it.copy(sending = false, notice = UiText(Res.string.conversation_attachments_send_failed))
                    }
                }
        }
    }

    /** Obsah se zahodí; volá to obrazovka při odchodu bez odeslání, jako výběr chatu. */
    fun abandon() {
        if (!_state.value.sent) requests.abandon()
    }

    /** Chyba z galerie (fotku se nepodařilo připravit) — ukáže se jako hláška. */
    fun reportError(message: UiText) = _state.update { it.copy(notice = message) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }
}

data class ShareToChatState(
    val chatId: Long = 0,
    val chatTitle: String = "",
    val avatarFileId: Int? = null,
    /** Tvar tváře v hlavičce — jako v seznamu chatů a v konverzaci. */
    val kind: ChatKind = ChatKind.Private,
    val isSavedMessages: Boolean = false,
    /** `null`, dokud se soubory připravují. */
    val attachments: List<OutgoingAttachment>? = null,
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
) {
    /** Mizení jen u jediné fotky v soukromém chatu — tytéž meze jako nad vstupním polem. */
    val canSelfDestruct: Boolean
        get() = selfDestructAllowed(
            isPrivateChat = kind == ChatKind.Private,
            isSavedMessages = isSavedMessages,
            attachmentCount = attachments?.size ?: 0,
        )
}
