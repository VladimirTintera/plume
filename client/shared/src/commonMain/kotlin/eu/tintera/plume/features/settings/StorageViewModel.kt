package eu.tintera.plume.features.settings

import eu.tintera.plume.core.translation.OnDeviceTranslator
import eu.tintera.plume.core.translation.TranslationPack
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.LiteRtModel
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.CacheLimit
import eu.tintera.plume.core.settings.KeepMedia
import eu.tintera.plume.core.storage.StorageCleaner
import eu.tintera.plume.core.telegram.StorageCategory
import eu.tintera.plume.core.telegram.NetworkUsage
import eu.tintera.plume.core.telegram.StorageRepository
import eu.tintera.plume.core.telegram.StorageUsage
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.forgetKnownImagePaths
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.storage_error_clear
import eu.tintera.plume.resources.storage_error_load
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageState(
    /** Databáze TDLib; `null` = ještě se nezjistila. */
    val databaseBytes: Long? = null,
    /** Podrobný přehled; `null` = ještě se počítá (u velké keše vteřiny). */
    val usage: StorageUsage? = null,
    val loadFailed: Boolean = false,
    /** Právě se maže — tlačítka se mezitím nedají ťuknout znovu. */
    val clearing: Boolean = false,
    /** Modely Gemmy, které leží v telefonu (celé nebo zčásti stažené). */
    val models: List<StoredModel> = emptyList(),
    /** Stažené balíčky pro překlad v telefonu; prázdné i tam, kde překlad v telefonu není (iOS). */
    val translationPacks: List<TranslationPack> = emptyList(),
    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    val notice: StorageNotice? = null,
    /** Přenesená data pro souhrn v řádku; `null` = ještě nedorazila (nebo selhala). */
    val networkUsage: NetworkUsage? = null,
)

/** Model AI v telefonu. [complete] = stažený celý; jinak zbyla část po zastaveném stahování. */
data class StoredModel(val model: LiteRtModel, val complete: Boolean)

sealed interface StorageNotice {
    /** Uvolněné místo; velikost se do věty skládá až v UI (jazyk a oddělovač patří jazyku). */
    data class Freed(val bytes: Long) : StorageNotice

    /** Smazaný model AI. Velikost se neříká: u zčásti staženého ji neznáme. */
    data class ModelDeleted(val name: String) : StorageNotice

    /** Smazaný balíček pro překlad; jméno jazyka dodá obrazovka podle kódu. */
    data class PackDeleted(val language: String) : StorageNotice

    data class Failed(val message: UiText) : StorageNotice
}

/**
 * Sekce Úložiště: kolik zabírají stažené soubory, jejich mazání (všude i v jednom chatu),
 * volba „Uchovávat média" a stažený model AI.
 *
 * Přehled se po každé akci **čte znovu** — co se smazalo a co mezitím přibylo, ví jen TDLib.
 */
class StorageViewModel(
    private val repository: StorageRepository,
    private val cleaner: StorageCleaner,
    private val settings: AppSettings,
    private val ai: OnDeviceAi,
    /** Překlad v telefonu; bez něj (iOS) skupina balíčků není. */
    private val translator: OnDeviceTranslator? = null,
) : ViewModel() {

    private val logger = Logger.withTag("Storage")

    private val _state = MutableStateFlow(StorageState())
    val state: StateFlow<StorageState> = _state.asStateFlow()

    val keepMedia: StateFlow<KeepMedia> = settings.keepMedia

    val cacheLimit: StateFlow<CacheLimit> = settings.cacheLimit

    private var loading: Job? = null

    // Načítá se z obrazovky při každém návratu (`LifecycleResumeEffect`), tedy i poprvé.

    fun refresh() {
        // Rozběhnuté počítání se nezačíná znovu — podrobný přehled prochází celý adresář.
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            repository.databaseBytes()
                .onSuccess { bytes -> _state.update { it.copy(databaseBytes = bytes) } }
                .onFailure { failure -> logger.w(failure) { "Velikost databáze se nepodařilo zjistit" } }
            loadModels()
            loadTranslationPacks()
            // Rychlé lokální čtení — před podrobným přehledem, který trvá vteřiny.
            repository.networkUsage().onSuccess { usage -> _state.update { it.copy(networkUsage = usage) } }
            repository.usage()
                .onSuccess { usage -> _state.update { it.copy(usage = usage, loadFailed = false) } }
                .onFailure { failure ->
                    logger.w(failure) { "Přehled úložiště se nepodařilo načíst" }
                    _state.update {
                        it.copy(
                            loadFailed = it.usage == null,
                            notice = StorageNotice.Failed(failure.userMessage(Res.string.storage_error_load)),
                        )
                    }
                }
        }
    }

    /** Smaže stažené soubory daných druhů — v chatu [chatId], nebo všude. */
    fun clear(categories: Set<StorageCategory>, chatId: Long? = null) {
        if (categories.isEmpty() || _state.value.clearing) return
        viewModelScope.launch {
            _state.update { it.copy(clearing = true) }
            val result = repository.clear(categories, chatId)
            finishClearing(result)
        }
    }

    /**
     * Uloží dobu uchování a hned podle ní uklidí — změna se má projevit, dokud se na ni uživatel
     * dívá, ne až zítra.
     */
    fun setKeepMedia(keep: KeepMedia) {
        if (keep == keepMedia.value) return
        settings.setKeepMedia(keep)
        if (keep.seconds == null) return
        viewModelScope.launch {
            _state.update { it.copy(clearing = true) }
            finishClearing(cleaner.runNow(keep, cacheLimit.value))
        }
    }

    /**
     * Uloží strop mezipaměti a hned podle něj uklidí — stejně jako u doby uchování. Zrušení
     * stropu nemaže nic.
     */
    fun setCacheLimit(limit: CacheLimit) {
        if (limit == cacheLimit.value) return
        settings.setCacheLimit(limit)
        if (limit.bytes == null) return
        viewModelScope.launch {
            _state.update { it.copy(clearing = true) }
            finishClearing(cleaner.runNow(keepMedia.value, limit))
        }
    }

    fun deleteModel(model: LiteRtModel) {
        viewModelScope.launch {
            runCatching { ai.delete(model.variant) }
                .onSuccess { _state.update { it.copy(notice = StorageNotice.ModelDeleted(model.displayName)) } }
                .onFailure { failure ->
                    logger.w(failure) { "Model ${model.displayName} se nepodařilo smazat" }
                    _state.update { it.copy(notice = StorageNotice.Failed(UiText(Res.string.storage_error_clear))) }
                }
            loadModels()
        }
    }

    fun deleteTranslationPack(language: String) {
        val translator = translator ?: return
        viewModelScope.launch {
            translator.deletePack(language)
                .onSuccess { _state.update { it.copy(notice = StorageNotice.PackDeleted(language)) } }
                .onFailure { _state.update { it.copy(notice = StorageNotice.Failed(UiText(Res.string.storage_error_clear))) } }
            loadTranslationPacks()
        }
    }

    private suspend fun loadTranslationPacks() {
        val packs = translator?.downloadedPacks().orEmpty()
        _state.update { it.copy(translationPacks = packs) }
    }

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }

    private suspend fun finishClearing(result: Result<Long>) {
        result
            .onSuccess { freed ->
                // Obrázky, které si cestu pamatují, by jinak sahaly na smazaný soubor.
                forgetKnownImagePaths()
                _state.update { it.copy(clearing = false, notice = StorageNotice.Freed(freed)) }
            }
            .onFailure { failure ->
                _state.update {
                    it.copy(
                        clearing = false,
                        notice = StorageNotice.Failed(failure.userMessage(Res.string.storage_error_clear)),
                    )
                }
            }
        loading?.cancel()
        refresh()
    }

    /**
     * Stažené modely (Gemma, Qwen) v telefonu. Kde model není (iOS, starý telefon), je stav
     * `Unsupported` a řádek se neukáže; Gemini Nano spravuje systém a smazat ho aplikace nemůže.
     */
    private suspend fun loadModels() {
        val models = LiteRtModel.entries.mapNotNull { model ->
            val status = runCatching { ai.status(model.variant) }
                .getOrDefault(OnDeviceAiStatus.Unsupported)
            when (status) {
                OnDeviceAiStatus.Ready -> StoredModel(model, complete = true)
                OnDeviceAiStatus.Paused -> StoredModel(model, complete = false)
                OnDeviceAiStatus.Unsupported, OnDeviceAiStatus.Downloadable, OnDeviceAiStatus.Downloading -> null
            }
        }
        _state.update { it.copy(models = models) }
    }
}
