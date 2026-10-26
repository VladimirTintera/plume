package eu.tintera.plume.features.stickers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.stickers.MadeSticker
import eu.tintera.plume.core.stickers.NoStickerSubjectException
import eu.tintera.plume.core.stickers.StickerMaker
import eu.tintera.plume.core.stickers.StickerModelNotReadyException
import eu.tintera.plume.core.telegram.NewStickerFile
import eu.tintera.plume.core.telegram.OwnedStickerSet
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSetNameCheck
import eu.tintera.plume.core.telegram.stickerKeywords
import eu.tintera.plume.core.telegram.stickerKeywordsFit
import eu.tintera.plume.core.telegram.stickerSetNameInput
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.PhotoMarkup
import eu.tintera.plume.core.ui.PhotoMarkupRenderer
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.sticker_added
import eu.tintera.plume.resources.sticker_failed
import eu.tintera.plume.resources.sticker_model_not_ready
import eu.tintera.plume.resources.sticker_no_subject
import eu.tintera.plume.resources.sticker_prepare_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Jak je na tom jméno nové sady (odkaz `t.me/addstickers/…`). */
enum class SetNameStatus {
    /** Prázdné — jméno vybere Telegram při založení. */
    Empty,
    Checking,
    Ok,
    Invalid,
    Occupied,

    /** Ověřit nešlo (bez spojení); rozhodne server při založení. */
    Unknown,
}

/** Kam samolepka půjde: do vlastní sady, nebo do nové s názvem. */
sealed interface StickerTarget {
    data class Existing(val set: OwnedStickerSet) : StickerTarget
    data object NewSet : StickerTarget
}

data class StickerComposerState(
    /** Úprava fotky (ořez, otočení, kresba); drží se k originálu, jako u přílohy. */
    val markup: PhotoMarkup = PhotoMarkup(),
    val removeBackground: Boolean = false,
    /** Bílý obrys kolem vyříznutého objektu; bez odstranění pozadí se nekreslí. */
    val outline: Boolean = true,
    val canRemoveBackground: Boolean = false,
    /** Fotku jde před tvorbou upravit (platforma má [PhotoMarkupRenderer]). */
    val canEditPhoto: Boolean = false,
    /** Hotová samolepka k nahrání — přesně to, co uživatel vidí v náhledu. */
    val preview: MadeSticker? = null,
    val preparing: Boolean = true,
    /** Samolepku z fotky nejde vyrobit (s odstraněním pozadí třeba ještě není model). */
    val prepareError: UiText? = null,
    val emoji: String = "",
    /** Vlastní sady; `null`, dokud se nenačtou. */
    val sets: List<OwnedStickerSet>? = null,
    val target: StickerTarget = StickerTarget.NewSet,
    val newSetTitle: String = "",
    /** Jméno nové sady v odkazu; navrhuje se z názvu, dokud ho uživatel nezmění sám. */
    val newSetName: String = "",
    /** Jméno upravil uživatel — návrh z názvu ho už nepřepíše. */
    val newSetNameEdited: Boolean = false,
    val newSetNameStatus: SetNameStatus = SetNameStatus.Empty,
    /** Klíčová slova samolepky, jak jsou v poli (čárkami oddělená, [stickerKeywords]). */
    val keywords: String = "",
    val saving: Boolean = false,
    /** Hotovo: věta pro hlášku a zavření obrazovky. */
    val done: UiText? = null,
    val error: UiText? = null,
) {
    /** Klíčová slova se vejdou do limitů Telegramu ([stickerKeywordsFit]). */
    val keywordsFit: Boolean get() = stickerKeywordsFit(stickerKeywords(keywords))

    /**
     * Přidat jde, když je náhled, emoji, klíčová slova v limitu a cíl (nová sada chce název
     * a jméno, které neodmítla kontrola; plná sada nebere).
     */
    val canSave: Boolean
        get() = preview != null && !preparing && !saving && emoji.isNotBlank() && keywordsFit && when (target) {
            is StickerTarget.Existing -> !target.set.isFull
            StickerTarget.NewSet -> newSetTitle.isNotBlank() && newSetNameStatus !in BLOCKING_NAME_STATUS
        }
}

/** Se jménem v tomhle stavu se sada nezaloží — server by ho odmítl, nebo se to ještě neví. */
private val BLOCKING_NAME_STATUS = setOf(SetNameStatus.Checking, SetNameStatus.Invalid, SetNameStatus.Occupied)

/**
 * Tvorba samolepky z fotky [sourcePath] ([width] × [height]): úprava editorem fotky, volitelně
 * bez pozadí, emoji a sada — vlastní, nebo nová.
 *
 * - **Náhled je hotový soubor**, ne odhad: s každou změnou (úprava, pozadí) se samolepka vyrobí
 *   znovu ([StickerMaker]) a nahraje se přesně ta, kterou uživatel vidí.
 * - **Úprava se drží k originálu** ([PhotoMarkup]), jako u přílohy: další úprava začíná
 *   z originálu se vším dosavadním, takže jde vzít zpátky i ořez.
 */
class StickerComposerViewModel(
    private val sourcePath: String,
    private val width: Int,
    private val height: Int,
    private val stickers: StickerRepository,
    private val maker: StickerMaker,
    private val markupRenderer: PhotoMarkupRenderer?,
    /** Sada, kterou předvybrat (z úpravy vlastní sady); `null` = první, kam se vejde. */
    private val targetSetId: Long? = null,
) : ViewModel() {

    private val logger = Logger.withTag("StickerComposer")

    private val _state = MutableStateFlow(
        StickerComposerState(
            removeBackground = maker.canRemoveBackground,
            canRemoveBackground = maker.canRemoveBackground,
            canEditPhoto = markupRenderer?.isAvailable == true,
        ),
    )
    val state: StateFlow<StickerComposerState> = _state.asStateFlow()

    val photoPath: String get() = sourcePath
    val photoWidth: Int get() = width
    val photoHeight: Int get() = height

    /** Fotka po úpravě; bez úpravy originál. */
    private var editedPath: String = sourcePath
    private var prepareJob: Job? = null
    private var suggestJob: Job? = null
    private var checkJob: Job? = null

    init {
        prepare()
        viewModelScope.launch {
            stickers.ownedSets()
                .onSuccess { sets ->
                    _state.update { current ->
                        // Výchozí cíl: první sada, kam se ještě vejde; jinak nová.
                        val target = (sets.firstOrNull { it.id == targetSetId && !it.isFull } ?: sets.firstOrNull { !it.isFull })
                            ?.let { StickerTarget.Existing(it) } ?: StickerTarget.NewSet
                        current.copy(sets = sets, target = target)
                    }
                }
                .onFailure { _state.update { it.copy(sets = emptyList()) } }
        }
    }

    fun applyMarkup(markup: PhotoMarkup) {
        _state.update { it.copy(markup = markup) }
        val renderer = markupRenderer
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            _state.update { it.copy(preparing = true, prepareError = null) }
            editedPath = if (markup.isEmpty || renderer == null) {
                sourcePath
            } else {
                runCatching { renderer.render(sourcePath, markup).path }
                    .onFailure { logger.w(it) { "Úpravu fotky se nepodařilo vykreslit." } }
                    .getOrDefault(sourcePath)
            }
            make()
        }
    }

    fun setRemoveBackground(remove: Boolean) {
        if (_state.value.removeBackground == remove) return
        _state.update { it.copy(removeBackground = remove) }
        prepare()
    }

    fun setOutline(outline: Boolean) {
        if (_state.value.outline == outline) return
        _state.update { it.copy(outline = outline) }
        if (_state.value.removeBackground) prepare()
    }

    /** Znovu vyrobit samolepku (model odstranění pozadí se mezitím stáhl). */
    fun retry() = prepare()

    fun setEmoji(emoji: String) = _state.update { it.copy(emoji = emoji) }

    fun selectTarget(target: StickerTarget) = _state.update { it.copy(target = target) }

    /** Název nové sady; dokud uživatel jméno v odkazu nezměnil, navrhne se k názvu nové. */
    fun setNewSetTitle(title: String) {
        _state.update { it.copy(newSetTitle = title.take(MAX_TITLE)) }
        if (!_state.value.newSetNameEdited) suggestName()
    }

    /**
     * Jméno v odkazu od uživatele. Smazané pole zůstane prázdné (Telegram pak vybere sám) —
     * návrh, který by do něj po chvíli naskočil, by přepsal, co uživatel začíná psát.
     */
    fun setNewSetName(text: String) {
        suggestJob?.cancel()
        applyName(stickerSetNameInput(text), edited = true)
    }

    fun setKeywords(text: String) = _state.update { it.copy(keywords = text.take(MAX_KEYWORDS_FIELD)) }

    /** Návrh jména podle názvu, chvíli po posledním písmenu — server se neptá na každé. */
    private fun suggestName() {
        suggestJob?.cancel()
        val title = _state.value.newSetTitle.trim()
        if (title.isEmpty()) {
            applyName("", edited = false)
            return
        }
        suggestJob = viewModelScope.launch {
            delay(NAME_DELAY_MS)
            stickers.suggestSetName(title)
                .onSuccess { name -> if (!_state.value.newSetNameEdited) applyName(name, edited = false, suggested = true) }
                .onFailure { logger.w(it) { "Jméno sady se nepodařilo navrhnout." } }
        }
    }

    /** Návrh od Telegramu je volný a platný, ověřuje se jen jméno od uživatele. */
    private fun applyName(name: String, edited: Boolean, suggested: Boolean = false) {
        checkJob?.cancel()
        val status = when {
            name.isEmpty() -> SetNameStatus.Empty
            suggested -> SetNameStatus.Ok
            else -> SetNameStatus.Checking
        }
        _state.update { it.copy(newSetName = name, newSetNameEdited = edited, newSetNameStatus = status) }
        if (status != SetNameStatus.Checking) return
        checkJob = viewModelScope.launch {
            delay(NAME_DELAY_MS)
            val checked = stickers.checkSetName(name)
                .map { result ->
                    when (result) {
                        StickerSetNameCheck.Ok -> SetNameStatus.Ok
                        StickerSetNameCheck.Invalid -> SetNameStatus.Invalid
                        StickerSetNameCheck.Occupied -> SetNameStatus.Occupied
                    }
                }
                .getOrElse { failure ->
                    logger.w(failure) { "Jméno sady $name se nepodařilo ověřit." }
                    SetNameStatus.Unknown
                }
            _state.update { if (it.newSetName == name) it.copy(newSetNameStatus = checked) else it }
        }
    }

    fun errorShown() = _state.update { it.copy(error = null) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val preview = current.preview ?: return
        val sticker = NewStickerFile(preview.path, current.emoji.trim(), stickerKeywords(current.keywords))
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = when (val target = current.target) {
                is StickerTarget.Existing -> stickers.addToSet(target.set, sticker).map { target.set.title }
                StickerTarget.NewSet -> stickers.createSet(current.newSetTitle.trim(), sticker, current.newSetName).map { it.title }
            }
            result
                .onSuccess { title -> _state.update { it.copy(saving = false, done = UiText(Res.string.sticker_added, title)) } }
                .onFailure { failure -> _state.update { it.copy(saving = false, error = failure.userMessage(Res.string.sticker_failed)) } }
        }
    }

    private fun prepare() {
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch { make() }
    }

    private suspend fun make() {
        _state.update { it.copy(preparing = true, prepareError = null) }
        val current = _state.value
        maker.make(editedPath, current.removeBackground, current.outline)
            .onSuccess { made -> _state.update { it.copy(preview = made, preparing = false) } }
            .onFailure { failure ->
                val message = when (failure) {
                    is StickerModelNotReadyException -> Res.string.sticker_model_not_ready
                    is NoStickerSubjectException -> Res.string.sticker_no_subject
                    else -> Res.string.sticker_prepare_failed
                }
                _state.update { it.copy(preview = null, preparing = false, prepareError = UiText(message)) }
            }
    }

    private companion object {
        /** Název sady má nejvýš 64 znaků (`createNewStickerSet`). */
        const val MAX_TITLE = 64

        /** Strop pole klíčových slov — limit sám hlídá [stickerKeywordsFit], tohle jen brzdí vložený román. */
        const val MAX_KEYWORDS_FIELD = 200

        /** Jak dlouho po posledním písmenu se jméno navrhuje nebo ověřuje. */
        const val NAME_DELAY_MS = 400L
    }
}
