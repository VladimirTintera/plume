package eu.tintera.plume.features.stickers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.stickers.StickerMaker
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSetDetail
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.telegram.stickerKeywords
import eu.tintera.plume.core.telegram.stickerKeywordsFit
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.sticker_keywords_saved
import eu.tintera.plume.resources.sticker_set_cover_failed
import eu.tintera.plume.resources.sticker_set_delete_failed
import eu.tintera.plume.resources.sticker_set_edit_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OwnedStickerSetState(
    /** Sada ze serveru; `null`, dokud se nenačte (nebo ji nejde načíst). */
    val set: StickerSetDetail? = null,
    /** Samolepky v pořadí, jak je uživatel vidí — během tahu místní, jinak ze serveru. */
    val stickers: List<StickerItem> = emptyList(),
    /** Rozepsaný název; `null` = beze změny. */
    val titleDraft: String? = null,
    /**
     * Hláška pro snackbar: chyba, nebo potvrzení změny, která jinak není vidět (klíčová slova
     * TDLib číst neumí, takže se v obrazovce nijak neprojeví).
     */
    val error: UiText? = null,
    /** Sada je smazaná — obrazovka se zavře. */
    val deleted: Boolean = false,
    /** Obálku jde vyrobit ze samolepky (Android a iOS, viz `StickerMaker.makeCover`). */
    val canSetCover: Boolean = false,
    /** Nová obálka se vyrábí a nahrává. */
    val coverBusy: Boolean = false,
) {
    /** Co se ukazuje jako obálka: vlastní, jinak první samolepka. */
    val cover: MessageMedia.Sticker? get() = set?.customCover ?: stickers.firstOrNull()?.preview

    val title: String get() = titleDraft ?: set?.title.orEmpty()

    /** Název jde uložit: změnil se a není prázdný. */
    val canSaveTitle: Boolean get() = titleDraft != null && titleDraft.isNotBlank() && titleDraft.trim() != set?.title
}

/**
 * Úprava vlastní sady samolepek: název, obálka, pořadí tažením, emoji a odebrání samolepky,
 * smazání celé sady. Sada se čte živě (`stickerSetDetail`): co server potvrdí, přijde updatem, takže
 * se po změně nic ručně nepřepisuje.
 *
 * - **Pořadí během tahu drží ViewModel** a serveru odejde až s puštěním úchytu, jedním
 *   voláním za taženou samolepku (`setStickerPositionInSet` bere jednu samolepku a cíl).
 */
class OwnedStickerSetViewModel(
    setId: Long,
    private val stickers: StickerRepository,
    private val files: FileRepository,
    private val maker: StickerMaker?,
) : ViewModel() {

    private val local = MutableStateFlow(OwnedStickerSetState(canSetCover = maker != null))

    /** Pořadí během tahu; `null` = podle serveru. */
    private val dragOrder = MutableStateFlow<List<StickerItem>?>(null)

    /** Kterou samolepku uživatel táhne (první `from` v tahu). */
    private var dragged: Long? = null

    private val detail = stickers.stickerSetDetail(setId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<OwnedStickerSetState> = combine(detail, dragOrder, local) { set, order, base ->
        base.copy(set = set, stickers = order ?: set?.stickers.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OwnedStickerSetState())

    fun setTitleDraft(title: String) = local.update { it.copy(titleDraft = title.take(MAX_TITLE)) }

    // Akce čtou zdroje (`local`, `detail`), ne odvozený `state`: ten se přepočítá až po dispatchi,
    // takže hned po `setTitleDraft` by ještě nesl starý koncept.
    fun saveTitle() {
        val set = detail.value ?: return
        val current = local.value.copy(set = set)
        if (!current.canSaveTitle) return
        val title = current.title.trim()
        viewModelScope.launch {
            stickers.renameSet(set.name, title)
                .onSuccess { local.update { it.copy(titleDraft = null) } }
                .onFailure(::failed)
        }
    }

    fun move(from: Any, to: Any) {
        val current = dragOrder.value ?: detail.value?.stickers.orEmpty()
        val fromIndex = current.indexOfFirst { it.id == from }
        val toIndex = current.indexOfFirst { it.id == to }
        if (fromIndex < 0 || toIndex < 0) return
        if (dragged == null) dragged = from as? Long
        dragOrder.value = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    /** Puštěný úchyt: tažená samolepka na nové místo, a jen když se opravdu pohnula. */
    fun commitMove() {
        val order = dragOrder.value
        val id = dragged
        dragged = null
        if (order == null || id == null) {
            dragOrder.value = null
            return
        }
        val sticker = order.firstOrNull { it.id == id }
        val position = order.indexOfFirst { it.id == id }
        val before = detail.value?.stickers?.indexOfFirst { it.id == id }
        if (sticker == null || position == before) {
            dragOrder.value = null
            return
        }
        viewModelScope.launch {
            stickers.moveInSet(sticker.sendFileId, position).onFailure(::failed)
            // Úspěch přijde updatem sady; do té doby drží místní pořadí, ať samolepka neposkočí.
            dragOrder.value = null
        }
    }

    fun changeEmoji(sticker: StickerItem, emojis: String) {
        val trimmed = emojis.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { stickers.setEmojis(sticker.sendFileId, trimmed).onFailure(::failed) }
    }

    /**
     * Klíčová slova samolepky — nahradí dosavadní, prázdné pole je smaže. Co se nevejde do limitů
     * ([stickerKeywordsFit]), dialog neodešle.
     */
    fun changeKeywords(sticker: StickerItem, text: String) {
        val keywords = stickerKeywords(text)
        if (!stickerKeywordsFit(keywords)) return
        viewModelScope.launch {
            stickers.setKeywords(sticker.sendFileId, keywords)
                .onSuccess { local.update { it.copy(error = UiText(Res.string.sticker_keywords_saved)) } }
                .onFailure(::failed)
        }
    }

    fun remove(sticker: StickerItem) {
        viewModelScope.launch { stickers.removeFromSet(sticker.sendFileId).onFailure(::failed) }
    }

    /**
     * Samolepka [sticker] obálkou sady. Obálka je samostatný soubor 100 × 100, ne odkaz na
     * samolepku: stáhne se, zmenší a nahraje. Jen statická (WebP) — animovanou vlastní sada
     * z fotek nemá.
     */
    fun setCover(sticker: StickerItem) {
        val set = detail.value ?: return
        val maker = maker ?: return
        val fileId = sticker.preview.fileId ?: return
        if (local.value.coverBusy) return
        local.update { it.copy(coverBusy = true) }
        viewModelScope.launch {
            runCatching {
                val file = files.file(fileId).first { it is FileState.Ready || it is FileState.Failed }
                val path = (file as? FileState.Ready)?.path ?: error("Samolepku $fileId se nepodařilo stáhnout.")
                val cover = maker.makeCover(path).getOrThrow()
                stickers.setSetCover(set.id, set.name, cover.path).getOrThrow()
            }.onFailure { failure ->
                local.update { it.copy(error = failure.userMessage(Res.string.sticker_set_cover_failed)) }
            }
            local.update { it.copy(coverBusy = false) }
        }
    }

    /** Zpátky bez vlastní obálky — obálkou bude první samolepka. */
    fun resetCover() {
        val set = detail.value ?: return
        if (local.value.coverBusy) return
        local.update { it.copy(coverBusy = true) }
        viewModelScope.launch {
            stickers.setSetCover(set.id, set.name, null).onFailure { failure ->
                local.update { it.copy(error = failure.userMessage(Res.string.sticker_set_cover_failed)) }
            }
            local.update { it.copy(coverBusy = false) }
        }
    }

    fun deleteSet() {
        val set = detail.value ?: return
        viewModelScope.launch {
            stickers.deleteSet(set.id, set.name)
                .onSuccess { local.update { it.copy(deleted = true) } }
                .onFailure { failure -> local.update { it.copy(error = failure.userMessage(Res.string.sticker_set_delete_failed)) } }
        }
    }

    fun errorShown() = local.update { it.copy(error = null) }

    private fun failed(failure: Throwable) {
        local.update { it.copy(error = failure.userMessage(Res.string.sticker_set_edit_failed)) }
    }

    private companion object {
        /** Název sady má nejvýš 64 znaků. */
        const val MAX_TITLE = 64
    }
}
