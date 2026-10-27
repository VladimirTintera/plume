package eu.tintera.plume.features.forward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.ForwardCapabilities
import eu.tintera.plume.core.telegram.ForwardOptions
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.features.chats.pickerChats
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forward_failed
import eu.tintera.plume.resources.forward_failed_partial
import eu.tintera.plume.resources.forward_failed_partial_more
import eu.tintera.plume.resources.saved_messages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Výběr chatu, do kterého se zpráva přepošle.
 *
 * Bez dotazu je to živý hlavní seznam z [ChatRepository], ten samý, který kreslí hlavní
 * obrazovka. S dotazem výsledky [ChatSearch] — ze všech chatů, i archivovaných.
 */
class ForwardViewModel(
    private val fromChatId: Long,
    private val messageIds: List<Long>,
    private val repository: ConversationRepository,
    private val chatRepository: ChatRepository,
    /**
     * Přeposlání je taky „sem posílám" — cíl se nahlásí jako použitý (`ShareTargets.reportUsed`,
     * Direct Share). Funkce, aby šla v testu nahradit — `ShareTargets` má závislosti na systému.
     */
    private val reportUsed: (chatId: Long) -> Unit,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _sending = MutableStateFlow(false)
    private val _error = MutableStateFlow<UiText?>(null)

    /**
     * Vybrané cíle. **Množina si drží pořadí výběru** (`Set.plus` vrací `LinkedHashSet`) — po
     * odeslání se otevře ten, který uživatel vybral první, takže je vidět, že zpráva dorazila.
     */
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())

    /** Hledání ve všech chatech, i v archivu — v načteném hlavním seznamu by archiv chyběl. */
    private val search = ChatSearch(chatRepository, viewModelScope)

    /**
     * Co zprávy dovolí, zjištěné jednou při otevření. Dokud to nedorazí, volby se nenabízejí —
     * přepínač, který by po chvíli zmizel, vypadá jako chyba.
     */
    private val _capabilities = MutableStateFlow(ForwardCapabilities())
    private val _options = MutableStateFlow(ForwardOptions())

    /** Komentář, který odejde do každého cíle PŘED přeposlanými zprávami. */
    private val _comment = MutableStateFlow("")

    /** Kam uživatel přeposílá nejčastěji (`getTopChats`); nahoře nad seznamem, jen bez dotazu. */
    private val _frequent = MutableStateFlow<List<ChatSummary>>(emptyList())

    /**
     * Cíle, kam komentář už odešel, i když přeposlání samo pak selhalo. Opakované „Odeslat" tam
     * pošle jen zprávy — komentář podruhé by byl tatáž chyba jako zpráva podruhé.
     */
    private val commented = mutableSetOf<Long>()

    init {
        viewModelScope.launch { _capabilities.value = repository.forwardCapabilities(fromChatId, messageIds) }
        viewModelScope.launch { _frequent.value = chatRepository.topForwardChats().getOrDefault(emptyList()) }
    }

    val state: StateFlow<ForwardState> = combine(
        combine(chatRepository.chats(), query, search.state) { list, text, found -> pickerChats(text, list, found) },
        query,
        combine(_sending, _selected, ::Pair),
        combine(_error, _comment, _frequent, ::Triple),
        combine(chatRepository.savedMessagesChatId, _capabilities, _options, ::Triple),
    ) { picker, text, (sending, selected), (error, comment, frequent), (saved, capabilities, options) ->
        ForwardState(
            query = text,
            loading = picker.loading,
            // Chat, ze kterého se přeposílá, ve výběru zůstává: přeposlat sám sobě jde a v Telegramu
            // to není výjimka.
            chats = picker.chats,
            sending = sending,
            selected = selected,
            error = error,
            savedMessagesChatId = saved,
            capabilities = capabilities,
            options = options,
            comment = comment,
            // Časté cíle jen bez dotazu: s dotazem uživatel ví, co hledá, a blok nad výsledky by
            // je odsunul.
            frequent = if (text.isBlank()) frequent else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForwardState())

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }

    fun setHideSender(hide: Boolean) {
        _options.update { it.copy(hideSender = hide) }
    }

    fun setHideCaptions(hide: Boolean) {
        _options.update { it.copy(hideCaptions = hide) }
    }

    fun setSilent(silent: Boolean) {
        _options.update { it.copy(silent = silent) }
    }

    fun setComment(text: String) {
        _comment.value = text
    }

    /** Ťuknutí na chat ho přidá mezi cíle, nebo ho z nich vyndá. Odesílá se až tlačítkem. */
    fun toggle(chatId: Long) {
        if (_sending.value) return
        _selected.update { if (chatId in it) it - chatId else it + chatId }
    }

    /**
     * Přepošle do všech vybraných cílů a ohlásí ten PRVNÍ vybraný — obrazovka ho otevře, aby
     * bylo vidět, že zpráva dorazila. Otevřít poslední nebo nic by bylo svévolné; první vybraný
     * je ten, na který uživatel myslel nejdřív.
     *
     * Posílá se **postupně**, ne souběžně: pořadí zpráv v cílech tím zůstane dané a server
     * nedostane deset přeposlání naráz. Selhání jednoho cíle ostatní nezastaví — co odešlo,
     * odešlo, a chyba se ohlásí na konci.
     *
     * **Po selhání zůstanou vybrané jen cíle, kam zpráva nedorazila.** Dřív zůstal výběr celý
     * a další „Odeslat" poslalo zprávu podruhé i tam, kde už byla. Hláška jmenuje, kam
     * nedorazila ([failureText]), ať je vidět, čeho se opakování týká.
     *
     * **Komentář jde do cíle PŘED zprávami**, jako u oficiálního klienta: čte se jako úvod
     * („koukni na tohle") a přeposlané zprávy pod ním. Když neodejde, zprávy se do toho cíle
     * neposílají — bez úvodu by přišly jinak, než je uživatel poslal. „Bez zvuku" platí pro
     * komentář i zprávy.
     */
    fun send(onDone: (chatId: Long) -> Unit) {
        val targets = _selected.value.toList()
        if (targets.isEmpty() || _sending.value) return
        val options = _options.value.allowedBy(_capabilities.value)
        val comment = _comment.value.trim()
        _sending.value = true

        viewModelScope.launch {
            val failed = mutableListOf<Long>()
            targets.forEach { target ->
                if (comment.isNotEmpty() && target !in commented) {
                    val sent = repository.send(target, comment, replyToMessageId = null, options = SendOptions(silent = options.silent))
                    if (sent.isFailure) {
                        failed += target
                        return@forEach
                    }
                    commented += target
                }
                repository.forward(fromChatId, messageIds, target, options)
                    .onSuccess { reportUsed(target) }
                    .onFailure { failed += target }
            }
            if (failed.isEmpty()) {
                onDone(targets.first())
                return@launch
            }
            _selected.value = targets.filter { it in failed }.toSet()
            _error.value = failureText(failed, allFailed = failed.size == targets.size)
            _sending.value = false
        }
    }

    /**
     * Hláška po selhání. Když neodešlo nic, stačí obecná věta — výběr se nezměnil. Když část
     * odešla, jmenuje se první cíl, kam zpráva nedorazila, a ostatní počtem (jména víc chatů
     * za sebou by se do snackbaru nevešla).
     */
    private suspend fun failureText(failed: List<Long>, allFailed: Boolean): UiText {
        if (allFailed) return UiText(Res.string.forward_failed)
        val first = failed.first()
        val name = if (first == chatRepository.savedMessagesChatId.value) {
            UiText(Res.string.saved_messages)
        } else {
            chatRepository.summaries(listOf(first)).firstOrNull()?.title?.let(UiText::Raw)
                ?: return UiText(Res.string.forward_failed)
        }
        val others = failed.size - 1
        return if (others == 0) {
            UiText(Res.string.forward_failed_partial, name)
        } else {
            UiText(Res.plurals.forward_failed_partial_more, others, name, others)
        }
    }

    fun consumeError() {
        _error.value = null
    }
}

data class ForwardState(
    val query: String = "",
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    val sending: Boolean = false,
    /** Vybrané cíle v pořadí výběru. */
    val selected: Set<Long> = emptySet(),
    val error: UiText? = null,
    val savedMessagesChatId: Long? = null,
    val capabilities: ForwardCapabilities = ForwardCapabilities(),
    /** Co uživatel zvolil; co se opravdu pošle, určí [ForwardOptions.allowedBy]. */
    val options: ForwardOptions = ForwardOptions(),
    /** Komentář k přeposlání; prázdný = jen zprávy. */
    val comment: String = "",
    /** Časté cíle nad seznamem; prázdné při hledání. */
    val frequent: List<ChatSummary> = emptyList(),
)

/**
 * Volby zúžené na to, co zprávy dovolí. Popisky jde skrýt jen u kopie a jen tehdy, když nějaké
 * jsou; vypnutím „skrýt odesílatele" se volba popisků neztratí, jen přestane platit — kdo si ji
 * zapne znovu, najde ji, jak ji nechal.
 */
internal fun ForwardOptions.allowedBy(capabilities: ForwardCapabilities): ForwardOptions {
    val hideSender = hideSender && capabilities.canHideSender
    return ForwardOptions(
        hideSender = hideSender,
        hideCaptions = hideSender && hideCaptions && capabilities.hasCaptions,
        // Bez zvuku jde vždycky, nezávisí na zprávách.
        silent = silent,
    )
}
