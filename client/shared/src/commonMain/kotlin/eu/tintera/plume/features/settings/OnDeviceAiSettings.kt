package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.GemmaModel
import eu.tintera.plume.core.ai.ModelDownload
import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.ai.NotEnoughSpaceException
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.files.formatFileSize
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_ai_provider_gemma
import eu.tintera.plume.resources.settings_ai_provider_gemma_summary
import eu.tintera.plume.resources.settings_ai_provider_nano
import eu.tintera.plume.resources.settings_ai_provider_nano_summary
import eu.tintera.plume.resources.settings_gemma_e2b_summary
import eu.tintera.plume.resources.settings_gemma_e4b_summary
import eu.tintera.plume.resources.settings_on_device_ai
import eu.tintera.plume.resources.settings_on_device_ai_delete
import eu.tintera.plume.resources.settings_on_device_ai_not_enough_space
import eu.tintera.plume.resources.settings_on_device_ai_checking
import eu.tintera.plume.features.conversation.items.label
import eu.tintera.plume.resources.settings_on_device_ai_download
import eu.tintera.plume.resources.settings_on_device_ai_download_progress
import eu.tintera.plume.resources.settings_on_device_ai_downloadable_named
import eu.tintera.plume.resources.settings_on_device_ai_resume
import eu.tintera.plume.resources.settings_on_device_ai_waiting_for_network
import eu.tintera.plume.resources.settings_on_device_ai_download_failed
import eu.tintera.plume.resources.settings_on_device_ai_downloadable
import eu.tintera.plume.resources.settings_on_device_ai_downloading
import eu.tintera.plume.resources.settings_on_device_ai_enable
import eu.tintera.plume.resources.settings_on_device_ai_enable_summary
import eu.tintera.plume.resources.settings_on_device_ai_fast
import eu.tintera.plume.resources.settings_on_device_ai_fast_summary
import eu.tintera.plume.resources.settings_on_device_ai_preview
import eu.tintera.plume.resources.settings_on_device_ai_preview_same
import eu.tintera.plume.resources.settings_reply_suggestions
import eu.tintera.plume.resources.settings_reply_suggestions_summary
import eu.tintera.plume.resources.settings_on_device_ai_preview_summary
import eu.tintera.plume.resources.settings_on_device_ai_token_limit
import eu.tintera.plume.resources.settings_on_device_ai_ready
import eu.tintera.plume.resources.settings_on_device_ai_ready_unnamed
import eu.tintera.plume.resources.settings_on_device_ai_unsupported
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Stav modelu, jak ho ukazuje Nastavení. */
sealed interface OnDeviceModelState {
    data object Checking : OnDeviceModelState
    data object Unsupported : OnDeviceModelState
    data object Downloadable : OnDeviceModelState
    /** [progress] je `null`, dokud stahování neohlásí první bajty. */
    data class Downloading(val progress: ModelDownload?) : OnDeviceModelState
    data object DownloadFailed : OnDeviceModelState
    data object NotEnoughSpace : OnDeviceModelState
    data class Ready(val name: String?, val tokenLimit: Int?) : OnDeviceModelState
}

/**
 * Volba „Shrnutí chatu", varianta modelu a jeho stažení.
 *
 * Stahování běží v [appScope], ne ve `viewModelScope`: trvá minuty a odchod z Nastavení ho
 * nemá zrušit. Nová obrazovka se k běžícímu stahování připojí (`download()` ve stavu
 * `Downloading`), takže průběh ukáže znovu.
 *
 * Varianta je jiný model: po její změně se stav čte znovu a preview, rychlá varianta nebo
 * jiná Gemma se může chtít stáhnout zvlášť. Totéž platí o volbě poskytovatele (Gemini Nano /
 * Gemma) — je to jen další pole varianty a platí pro celou aplikaci.
 */
class OnDeviceAiSettingsViewModel(
    private val settings: AppSettings,
    private val ai: OnDeviceAi,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val logger = Logger.withTag("OnDeviceAi")

    val enabled: StateFlow<Boolean> = settings.onDeviceAiEnabled
    val variant: StateFlow<ModelVariant> = settings.onDeviceAiVariant

    private val _model = MutableStateFlow<OnDeviceModelState>(OnDeviceModelState.Checking)
    val model: StateFlow<OnDeviceModelState> = _model.asStateFlow()

    /** Preview varianta vrací tentýž model jako stabilní — přepínač tedy nic nezmění. */
    private val _previewIsStable = MutableStateFlow(false)
    val previewIsStable: StateFlow<Boolean> = _previewIsStable.asStateFlow()

    /**
     * Co zařízení umí: kteří poskytovatelé a které modely Gemma. `null` = ještě nevíme.
     *
     * U Gemini Nano se ptá na VÝCHOZÍ variantu, ne na tu zvolenou: preview nebo rychlá může
     * chybět zvlášť, a kvůli tomu by celá sekce zmizela uživateli, který model má. Ze stejného
     * důvodu je to samostatný tok a ne odvozenina z [model] — ten se při každé změně varianty
     * vrací na `Checking` a sekce by problikávala.
     */
    val availability: StateFlow<AiAvailability?> = flow {
        emit(
            AiAvailability(
                providers = AiProvider.entries.filter { ai.status(ModelVariant(provider = it)) != OnDeviceAiStatus.Unsupported },
                gemmaModels = GemmaModel.entries.filter {
                    ai.status(ModelVariant(provider = AiProvider.Gemma, gemma = it)) != OnDeviceAiStatus.Unsupported
                },
            ),
        )
    }
        .catch { emit(AiAvailability(emptyList(), emptyList())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var download: Job? = null

    init {
        // Změna varianty = jiný model; rozběhnuté čtení stavu pro tu předchozí se zahodí.
        viewModelScope.launch { variant.collectLatest { refresh(it) } }
        // Zvolený poskytovatel (nebo model Gemmy), který tu není, se vymění za dostupný —
        // jinak by sekce ukazovala volbu, kterou zařízení nesplní, a funkce by se nenabídly.
        viewModelScope.launch {
            availability.filterNotNull().collect { available ->
                supportedVariant(variant.value, available)?.let(settings::setOnDeviceAiVariant)
            }
        }
    }

    fun setEnabled(on: Boolean) {
        settings.setOnDeviceAiEnabled(on)
    }

    val replySuggestions: StateFlow<Boolean> = settings.replySuggestionsEnabled

    fun setReplySuggestions(on: Boolean) = settings.setReplySuggestionsEnabled(on)

    fun setPreview(on: Boolean) = settings.setOnDeviceAiVariant(variant.value.copy(preview = on))

    fun setFast(on: Boolean) = settings.setOnDeviceAiVariant(variant.value.copy(fast = on))

    fun setProvider(provider: AiProvider) = settings.setOnDeviceAiVariant(variant.value.copy(provider = provider))

    fun setGemmaModel(model: GemmaModel) = settings.setOnDeviceAiVariant(variant.value.copy(gemma = model))

    /** Smaže stažený model (jen u modelu, který stahuje aplikace). Gigabajty zpátky. */
    fun delete() {
        val target = variant.value
        appScope.launch {
            try {
                ai.delete(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Model se nepodařilo smazat ($target)" }
            }
            if (variant.value == target) refresh(target)
        }
    }

    fun download() {
        if (download?.isActive == true) return
        val target = variant.value
        _model.value = OnDeviceModelState.Downloading(null)
        download = appScope.launch {
            try {
                ai.download(target).collect { progress ->
                    if (variant.value == target) _model.value = OnDeviceModelState.Downloading(progress)
                }
                if (variant.value == target) refresh(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotEnoughSpaceException) {
                logger.i(e) { "Na model není místo ($target)" }
                if (variant.value == target) _model.value = OnDeviceModelState.NotEnoughSpace
            } catch (e: Exception) {
                logger.w(e) { "Model se nepodařilo stáhnout ($target)" }
                if (variant.value == target) _model.value = OnDeviceModelState.DownloadFailed
            }
        }
    }

    private suspend fun refresh(variant: ModelVariant) {
        _model.value = OnDeviceModelState.Checking
        _model.value = when (ai.status(variant)) {
            OnDeviceAiStatus.Unsupported -> OnDeviceModelState.Unsupported
            OnDeviceAiStatus.Downloadable -> OnDeviceModelState.Downloadable
            OnDeviceAiStatus.Downloading -> {
                download()
                return
            }
            OnDeviceAiStatus.Ready -> ai.info(variant).let { OnDeviceModelState.Ready(it?.name, it?.tokenLimit) }
        }
        comparePreview(variant)
    }

    /**
     * Porovná jméno modelu preview a stabilní varianty. Jen u připraveného modelu: u nestaženého
     * jméno stejně nikdo neřekne. Selhání se polyká — je to doplňková informace, ne funkce.
     */
    private suspend fun comparePreview(variant: ModelVariant) {
        // Preview je volba Gemini Nano; u Gemmy není co porovnávat.
        if (_model.value !is OnDeviceModelState.Ready || variant.provider != AiProvider.GeminiNano) {
            _previewIsStable.value = false
            return
        }
        _previewIsStable.value = try {
            previewSameAsStable(
                previewName = ai.info(variant.copy(preview = true))?.name,
                stableName = ai.info(variant.copy(preview = false))?.name,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.i(e) { "Nepovedlo se porovnat preview se stabilní variantou" }
            false
        }
    }
}

/**
 * Vrací preview varianta tentýž model jako stabilní?
 *
 * Preview se nechová jako nedostupná funkce: `checkStatus` vrátí `AVAILABLE` (ne
 * `DOWNLOADABLE`) a `getBaseModelName()` dá totéž jméno co stabilní verze. Ověřeno na
 * Pixelu 11 Pro Foldu — AICore tam preview varianty ZNÁ a nabízí je ve svém Labs, ale stažený
 * je jen balík „Fast + Full [TPU]“; ML Kit si o preview neřekne a tiše spadne na stabilní.
 * Přepínač tedy nedělá nic a uživateli to nikdo neřekne, proto se jména porovnávají.
 *
 * Neznámé jméno (model se ještě nestáhl, AICore ho neřekl) NEZNAMENÁ shodu — tvrdit
 * „preview tu není" na základě chybějícího údaje by bylo horší než mlčet.
 */
fun previewSameAsStable(previewName: String?, stableName: String?): Boolean =
    previewName != null && previewName == stableName

/** Co zařízení umí: dostupní poskytovatelé a modely Gemma, které se tu dají pustit. */
data class AiAvailability(val providers: List<AiProvider>, val gemmaModels: List<GemmaModel>)

/**
 * Varianta opravená na to, co zařízení umí, nebo `null`, když je v pořádku.
 *
 * Zvolený poskytovatel, který tu není (telefon bez AICore, uložená volba z jiného zařízení),
 * se vymění za první dostupný; model Gemma, na který nestačí paměť, za první, na který ano.
 * Bez dostupného poskytovatele se nemění nic — sekce se pak vůbec nenabízí a opravovat není co.
 */
fun supportedVariant(current: ModelVariant, available: AiAvailability): ModelVariant? {
    val provider = current.provider.takeIf { it in available.providers }
        ?: available.providers.firstOrNull()
        ?: return null
    val gemma = current.gemma.takeIf { it in available.gemmaModels }
        ?: available.gemmaModels.firstOrNull()
        ?: current.gemma
    val fixed = current.copy(provider = provider, gemma = gemma)
    return fixed.takeIf { it != current }
}

/**
 * Skupina „AI v zařízení" v sekci Obecné. Stav modelu se ukazuje, jen když je volba zapnutá —
 * kdo o funkci nestojí, nemá co řešit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnDeviceAiSettings(viewModel: OnDeviceAiSettingsViewModel) {
    val haptics = LocalHapticFeedback.current
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val variant by viewModel.variant.collectAsStateWithLifecycle()
    val previewIsStable by viewModel.previewIsStable.collectAsStateWithLifecycle()
    val replySuggestions by viewModel.replySuggestions.collectAsStateWithLifecycle()
    val availability by viewModel.availability.collectAsStateWithLifecycle()

    /*
     * Na zařízení bez AICore se celá sekce NENABÍZÍ. Dřív se vykreslila vždycky a uživatel
     * zapnul přepínač, aby se teprve pak dozvěděl „tohle zařízení model nemá" — nabídka funkce,
     * kterou nelze mít. Stejné pravidlo jako u filtrů seznamu chatů, které nemají co odfiltrovat.
     *
     * Dokud stav nedorazí (`null`), taky se nekreslí: objevení sekce ruší míň než její zmizení,
     * a je to totéž rozhodnutí jako u filtrů, které naběhnou až s načteným seznamem.
     */
    val available = availability?.takeIf { it.providers.isNotEmpty() } ?: return

    SettingsGroup(Res.string.settings_on_device_ai) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(Res.string.settings_on_device_ai_enable)) },
            supportingContent = { Text(stringResource(Res.string.settings_on_device_ai_enable_summary)) },
            trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
            modifier = Modifier.toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = { on ->
                    haptics.toggle(on)
                    viewModel.setEnabled(on)
                },
            ),
        )

        if (enabled) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            /*
             * Návrhy odpovědí mají vlastní vypínač, shrnutí ne. Není to nedůslednost: shrnutí
             * se otevírá vědomě ťuknutím na ikonu, kdežto návrhy se objeví samy pod rozhovorem,
             * takže o ně nemusí stát ani ten, kdo shrnutí používá rád.
             */
            VariantSwitch(
                title = Res.string.settings_reply_suggestions,
                summary = Res.string.settings_reply_suggestions_summary,
                checked = replySuggestions,
                onChange = { on ->
                    haptics.toggle(on)
                    viewModel.setReplySuggestions(on)
                },
            )
            /*
             * Poskytovatel se nabízí, jen když je z čeho vybírat — na telefonu bez AICore je
             * jediná možnost Gemma a volba s jednou položkou by byla jen šum. Platí pro celou
             * aplikaci: shrnutí i návrhy odpovědí jedou přes zvoleného.
             */
            if (available.providers.size > 1) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                available.providers.forEach { provider ->
                    ChoiceRow(
                        title = stringResource(provider.title),
                        summary = stringResource(provider.summary),
                        selected = variant.provider == provider,
                        onSelect = { viewModel.setProvider(provider) },
                    )
                }
            }
            when (variant.provider) {
                AiProvider.GeminiNano -> {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    VariantSwitch(
                        title = Res.string.settings_on_device_ai_preview,
                        summary = Res.string.settings_on_device_ai_preview_summary,
                        checked = variant.preview,
                        onChange = { on ->
                            haptics.toggle(on)
                            viewModel.setPreview(on)
                        },
                        note = Res.string.settings_on_device_ai_preview_same.takeIf { previewIsStable },
                    )
                    VariantSwitch(
                        title = Res.string.settings_on_device_ai_fast,
                        summary = Res.string.settings_on_device_ai_fast_summary,
                        checked = variant.fast,
                        onChange = { on ->
                            haptics.toggle(on)
                            viewModel.setFast(on)
                        },
                    )
                }
                // Větší model se nabídne jen tam, kde na něj stačí paměť.
                AiProvider.Gemma -> if (available.gemmaModels.size > 1) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    available.gemmaModels.forEach { gemma ->
                        ChoiceRow(
                            title = gemma.displayName,
                            summary = stringResource(gemma.summary, fileSizeText(gemma.sizeBytes)),
                            selected = variant.gemma == gemma,
                            onSelect = { viewModel.setGemmaModel(gemma) },
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                val status = when (val current = model) {
                    OnDeviceModelState.Checking -> stringResource(Res.string.settings_on_device_ai_checking)
                    OnDeviceModelState.Unsupported -> stringResource(Res.string.settings_on_device_ai_unsupported)
                    /*
                     * U modelu, který stahuje aplikace, se jmenuje CO a KOLIK. „Zabere gigabajty"
                     * stačí u systémového modelu, jehož velikost nám nikdo neřekne; tady ji víme
                     * a pod 2,4 GB se uživatel podepisuje, ne pod „nějaké gigabajty".
                     */
                    OnDeviceModelState.Downloadable -> if (variant.appManaged) {
                        stringResource(
                            Res.string.settings_on_device_ai_downloadable_named,
                            variant.gemma.displayName,
                            fileSizeText(variant.gemma.sizeBytes),
                        )
                    } else {
                        stringResource(Res.string.settings_on_device_ai_downloadable)
                    }
                    is OnDeviceModelState.Downloading -> if (current.progress?.waitingForNetwork == true) {
                        stringResource(Res.string.settings_on_device_ai_waiting_for_network)
                    } else {
                        stringResource(Res.string.settings_on_device_ai_downloading)
                    }
                    OnDeviceModelState.DownloadFailed -> stringResource(Res.string.settings_on_device_ai_download_failed)
                    OnDeviceModelState.NotEnoughSpace -> stringResource(Res.string.settings_on_device_ai_not_enough_space)
                    is OnDeviceModelState.Ready -> current.name
                        ?.let { stringResource(Res.string.settings_on_device_ai_ready, it) }
                        ?: stringResource(Res.string.settings_on_device_ai_ready_unnamed)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                /*
                 * Kolik už je staženo: u gigabajtů trvá stahování minuty a samotná vlnka neřekne,
                 * jestli to bude za chvíli, nebo za čtvrt hodiny.
                 */
                val progress = (model as? OnDeviceModelState.Downloading)?.progress
                val total = progress?.totalBytes
                if (progress != null && total != null && total > 0) {
                    Text(
                        stringResource(
                            Res.string.settings_on_device_ai_download_progress,
                            fileSizeText(progress.downloadedBytes),
                            fileSizeText(total),
                        ),
                        style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                (model as? OnDeviceModelState.Ready)?.tokenLimit?.let { limit ->
                    Text(
                        stringResource(Res.string.settings_on_device_ai_token_limit, limit.grouped()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (val current = model) {
                    is OnDeviceModelState.Downloading -> {
                        val fraction = current.progress?.fraction
                        if (fraction != null) {
                            LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    OnDeviceModelState.Downloadable, OnDeviceModelState.DownloadFailed, OnDeviceModelState.NotEnoughSpace ->
                        FilledTonalButton(onClick = viewModel::download) {
                            // Stažený kus se nezahazuje, takže po selhání tlačítko slibuje navázání.
                            val resumes = model == OnDeviceModelState.DownloadFailed && variant.appManaged
                            Text(
                                stringResource(
                                    if (resumes) Res.string.settings_on_device_ai_resume
                                    else Res.string.settings_on_device_ai_download,
                                ),
                            )
                        }
                    /*
                     * Smazat jde jen model, který si stáhla aplikace. Systémový (AICore) patří
                     * telefonu a sdílí ho s ostatními aplikacemi; gigabajty Gemmy zabírají místo
                     * jen kvůli nám, a kdo funkci přestane používat, musí mít jak je vrátit.
                     */
                    /*
                     * Obrysové, ne textové tlačítko: `TextButton` nemá plochu a jeho vnitřní
                     * odsazení odsunulo popisek od textu nad ním. Obrys lícuje s hranou jako
                     * „Stáhnout model" na témže místě a vedle tónovaného tlačítka ustupuje.
                     */
                    is OnDeviceModelState.Ready -> if (variant.appManaged) {
                        OutlinedButton(onClick = viewModel::delete) {
                            Text(stringResource(Res.string.settings_on_device_ai_delete))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun VariantSwitch(
    title: StringResource,
    summary: StringResource,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    note: StringResource? = null,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(summary))
                // Barva popředí ListItemu, ne onSurfaceVariant: ten je počítaný na surface
                // role a řádek si barvy určuje sám (viz CLAUDE.md, „ListItem na tónované kartě").
                note?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge) }
            }
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

/** Jedna volba z několika (poskytovatel, model Gemma). Klikání nese celý řádek, jako u motivu. */
@Composable
private fun ChoiceRow(title: String, summary: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    )
}

private val AiProvider.title: StringResource
    get() = when (this) {
        AiProvider.GeminiNano -> Res.string.settings_ai_provider_nano
        AiProvider.Gemma -> Res.string.settings_ai_provider_gemma
    }

private val AiProvider.summary: StringResource
    get() = when (this) {
        AiProvider.GeminiNano -> Res.string.settings_ai_provider_nano_summary
        AiProvider.Gemma -> Res.string.settings_ai_provider_gemma_summary
    }

private val GemmaModel.summary: StringResource
    get() = when (this) {
        GemmaModel.E2B -> Res.string.settings_gemma_e2b_summary
        GemmaModel.E4B -> Res.string.settings_gemma_e4b_summary
    }

/** Velikost s jednotkou, stejně jako u souborů v konverzaci. */
@Composable
private fun fileSizeText(bytes: Long): String {
    val size = formatFileSize(bytes)
    return stringResource(size.unit.label(), size.value)
}
