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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** Fotku jde před tvorbou upravit (na desktopu editor fotky zatím není). */
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
    val saving: Boolean = false,
    /** Hotovo: věta pro hlášku a zavření obrazovky. */
    val done: UiText? = null,
    val error: UiText? = null,
) {
    /** Přidat jde, když je náhled, emoji a cíl (nová sada chce název, plná sada nebere). */
    val canSave: Boolean
        get() = preview != null && !preparing && !saving && emoji.isNotBlank() && when (target) {
            is StickerTarget.Existing -> !target.set.isFull
            StickerTarget.NewSet -> newSetTitle.isNotBlank()
        }
}

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

    fun setNewSetTitle(title: String) = _state.update { it.copy(newSetTitle = title.take(MAX_TITLE)) }

    fun errorShown() = _state.update { it.copy(error = null) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val preview = current.preview ?: return
        val sticker = NewStickerFile(preview.path, current.emoji.trim())
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = when (val target = current.target) {
                is StickerTarget.Existing -> stickers.addToSet(target.set, sticker).map { target.set.title }
                StickerTarget.NewSet -> stickers.createSet(current.newSetTitle.trim(), sticker).map { it.title }
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
    }
}
