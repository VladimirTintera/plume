package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ui.highlightAnchor
import eu.tintera.plume.resources.ai_task_folders
import eu.tintera.plume.resources.ai_task_chat
import eu.tintera.plume.resources.ai_playground_title
import eu.tintera.plume.resources.ai_playground_row_help
import eu.tintera.plume.resources.chat_bubble_24dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.resources.settings_gemma_under_load
import eu.tintera.plume.resources.settings_qwen_under_load
import eu.tintera.plume.resources.settings_eurollm_under_load
import eu.tintera.plume.resources.settings_aya_8b_summary
import eu.tintera.plume.resources.settings_aya_license
import eu.tintera.plume.resources.settings_aya_under_load
import eu.tintera.plume.resources.settings_ai_tasks_note_aya
import eu.tintera.plume.resources.settings_ai_provider_aya
import eu.tintera.plume.resources.settings_ai_provider_aya_summary
import eu.tintera.plume.resources.settings_eurollm_9b_summary
import eu.tintera.plume.resources.settings_euromoe_summary
import eu.tintera.plume.resources.settings_ai_tasks_note_eurollm
import eu.tintera.plume.resources.settings_ai_provider_eurollm
import eu.tintera.plume.resources.settings_ai_provider_eurollm_summary
import eu.tintera.plume.resources.settings_qwen_4b_summary
import eu.tintera.plume.resources.settings_qwen_1_5b_summary
import eu.tintera.plume.resources.settings_qwen_0_6b_summary
import eu.tintera.plume.resources.ai_task_photo_description
import eu.tintera.plume.resources.ai_task_transcription
import eu.tintera.plume.resources.ai_task_search
import eu.tintera.plume.resources.ai_task_triage
import eu.tintera.plume.resources.ai_task_scam
import eu.tintera.plume.resources.ai_task_dates
import eu.tintera.plume.resources.ai_task_rewrite
import eu.tintera.plume.resources.ai_task_replies
import eu.tintera.plume.resources.ai_task_explain
import eu.tintera.plume.resources.ai_task_summary
import eu.tintera.plume.resources.settings_ai_tasks_note
import eu.tintera.plume.resources.settings_ai_tasks_note_qwen
import eu.tintera.plume.resources.settings_ai_task_not_ready
import eu.tintera.plume.resources.settings_ai_task_auto_named
import eu.tintera.plume.resources.settings_ai_task_auto
import eu.tintera.plume.resources.settings_ai_tasks
import eu.tintera.plume.resources.settings_ai_model
import eu.tintera.plume.resources.settings_ai_models
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import eu.tintera.plume.core.ui.ChoiceRow
import eu.tintera.plume.core.ai.variantFor
import eu.tintera.plume.core.ai.handles
import eu.tintera.plume.core.ai.chooseProvider
import eu.tintera.plume.core.ai.AiTask
import eu.tintera.plume.core.ai.AiModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import eu.tintera.plume.core.ui.GroupBreak
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.ai.core.AiProvider
import eu.tintera.ai.core.DownloadStoppedException
import eu.tintera.ai.core.LiteRtModel
import eu.tintera.ai.core.MeteredNetwork
import eu.tintera.ai.core.ModelDownload
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.NoMeteredNetwork
import eu.tintera.ai.core.NotEnoughSpaceException
import eu.tintera.ai.core.RuntimeInstallException
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_ai_provider_apple
import eu.tintera.plume.resources.settings_ai_provider_apple_summary
import eu.tintera.plume.resources.settings_ai_provider_gemma
import eu.tintera.plume.resources.settings_ai_provider_gemma_summary
import eu.tintera.plume.resources.settings_ai_provider_qwen
import eu.tintera.plume.resources.settings_ai_provider_qwen_summary
import eu.tintera.plume.resources.settings_ai_provider_nano
import eu.tintera.plume.resources.settings_ai_provider_nano_summary
import eu.tintera.plume.resources.settings_gemma_e2b_summary
import eu.tintera.plume.resources.settings_gemma_e4b_summary
import eu.tintera.plume.resources.settings_on_device_ai
import eu.tintera.plume.resources.settings_on_device_ai_delete
import eu.tintera.plume.resources.settings_on_device_ai_not_enough_space
import eu.tintera.plume.resources.settings_on_device_ai_checking
import eu.tintera.plume.resources.settings_on_device_ai_download
import eu.tintera.plume.resources.settings_ai_wifi_only
import eu.tintera.plume.resources.settings_ai_wifi_only_summary
import eu.tintera.plume.resources.settings_ai_mobile_data_title
import eu.tintera.plume.resources.settings_ai_mobile_data_text
import eu.tintera.plume.resources.settings_ai_mobile_data_confirm
import eu.tintera.plume.resources.settings_ai_mobile_data_wait
import eu.tintera.plume.resources.settings_on_device_ai_waiting_for_wifi
import eu.tintera.plume.resources.signal_cellular_alt_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.resources.settings_on_device_ai_download_progress
import eu.tintera.plume.resources.settings_on_device_ai_downloadable_named
import eu.tintera.plume.resources.settings_on_device_ai_discard_part
import eu.tintera.plume.resources.settings_on_device_ai_paused
import eu.tintera.plume.resources.settings_on_device_ai_resume
import eu.tintera.plume.resources.settings_on_device_ai_stop
import eu.tintera.plume.resources.settings_on_device_ai_waiting_for_network
import eu.tintera.plume.resources.settings_on_device_ai_download_failed
import eu.tintera.plume.resources.settings_on_device_ai_runtime_failed
import eu.tintera.plume.resources.settings_on_device_ai_runtime_failed_code
import eu.tintera.plume.resources.settings_on_device_ai_downloadable
import eu.tintera.plume.resources.settings_on_device_ai_downloading
import eu.tintera.plume.resources.settings_on_device_ai_enable
import eu.tintera.plume.resources.settings_on_device_ai_enable_summary
import eu.tintera.plume.resources.settings_on_device_ai_fast
import eu.tintera.plume.resources.settings_on_device_ai_fast_summary
import eu.tintera.plume.resources.settings_on_device_ai_preview
import eu.tintera.plume.resources.settings_reply_suggestions
import eu.tintera.plume.resources.settings_reply_suggestions_summary
import eu.tintera.plume.resources.settings_chat_triage
import eu.tintera.plume.resources.settings_background_photos
import eu.tintera.plume.resources.settings_background_photos_summary
import eu.tintera.plume.resources.settings_chat_triage_summary
import eu.tintera.plume.resources.settings_scam_warnings
import eu.tintera.plume.resources.settings_scam_warnings_summary
import eu.tintera.plume.resources.settings_on_device_ai_preview_summary
import eu.tintera.plume.resources.settings_on_device_ai_token_limit
import eu.tintera.plume.resources.settings_on_device_ai_ready
import eu.tintera.plume.resources.settings_on_device_ai_ready_unnamed
import eu.tintera.plume.resources.settings_on_device_ai_unsupported
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import eu.tintera.plume.core.ai.backgroundPhotoDescriptionsSupported
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
import eu.tintera.plume.core.ui.SwitchListItem
import eu.tintera.plume.core.ui.TitledGroup

/** Stav modelu, jak ho ukazuje Nastavení. */
sealed interface OnDeviceModelState {
    data object Checking : OnDeviceModelState
    data object Unsupported : OnDeviceModelState
    data object Downloadable : OnDeviceModelState
    /** [progress] je `null`, dokud stahování neohlásí první bajty. */
    data class Downloading(val progress: ModelDownload?) : OnDeviceModelState
    data object DownloadFailed : OnDeviceModelState
    /**
     * Model se ani nezačal stahovat: runtime, na kterém běží (na Androidu součást z Google Play),
     * se nepodařilo doinstalovat. [code] je kód od Google Play, když nějaký je.
     */
    data class RuntimeInstallFailed(val code: Int?) : OnDeviceModelState
    /** Model je stažený zčásti a stahování neběží (zastavené, nebo po selhání a restartu). */
    data object Paused : OnDeviceModelState
    data object NotEnoughSpace : OnDeviceModelState
    data class Ready(val name: String?, val tokenLimit: Int?) : OnDeviceModelState
}

/**
 * Volba „AI v zařízení", zapnuté modely, jejich stažení a co čím počítat.
 *
 * Sledování stahování běží v [appScope], ne ve `viewModelScope`: trvá minuty a odchod
 * z Nastavení ho nemá zrušit. Nová obrazovka se k běžícímu stahování připojí (`download()` ve
 * stavu `Downloading`), takže průběh ukáže znovu. Gemmu přitom stahuje úloha WorkManageru, ne
 * tahle korutina — přežije i zabití procesu (viz `ModelDownloadWorker`).
 *
 * **Stav se vede pro každý model zvlášť** ([states]): zapnuté můžou být Gemini Nano i Gemma
 * naráz a každá má vlastní stažení. Volba varianty (preview, rychlá, jiná Gemma) je jiný model,
 * takže se stav toho modelu čte znovu. Stav se čte i u vypnutého modelu: stažená Gemma zabírá
 * gigabajty, i když ji nic nepoužívá, a musí jít smazat.
 */
class OnDeviceAiSettingsViewModel(
    private val settings: AppSettings,
    private val ai: OnDeviceAi,
    private val appScope: CoroutineScope,
    /**
     * Hlídá platforma úsporný režim a přehřátí (`DeviceLoad`)? Jen pak má smysl věta, že pod nimi
     * stahovaný model sám nic nepočítá; desktop zátěž nesleduje a věta by slibovala něco, co
     * se tam neděje.
     */
    val tracksLoad: Boolean = true,
    /** Placená síť; [NoMeteredNetwork] = platforma ji nepozná a volba „jen přes Wi-Fi" se nenabízí. */
    private val meteredNetwork: MeteredNetwork = NoMeteredNetwork,
) : ViewModel() {

    private val logger = Logger.withTag("OnDeviceAi")

    val enabled: StateFlow<Boolean> = settings.onDeviceAiEnabled
    val models: StateFlow<AiModels> = settings.onDeviceAiModels

    private val _states = MutableStateFlow<Map<AiProvider, OnDeviceModelState>>(emptyMap())

    /** Stav každého modelu, který zařízení umí. Chybí-li model, ještě se zjišťuje. */
    val states: StateFlow<Map<AiProvider, OnDeviceModelState>> = _states.asStateFlow()

    /**
     * Co zařízení umí: kteří poskytovatelé a které modely Gemma. `null` = ještě nevíme.
     *
     * U Gemini Nano se ptá na VÝCHOZÍ variantu, ne na tu zvolenou: preview nebo rychlá může
     * chybět zvlášť, a kvůli tomu by celá sekce zmizela uživateli, který model má. Ze stejného
     * důvodu je to samostatný tok a ne odvozenina z [states] — ten se při každé změně varianty
     * vrací na `Checking` a sekce by problikávala.
     */
    val availability: StateFlow<AiAvailability?> = ai.availability()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Komu by úlohu dalo „Automaticky" — pro popisek řádku („Automaticky (Gemini Nano)"). Bez
     * něj by volba nic neříkala: kdo nevidí, co automatika vybrala, nepozná, jestli ji měnit.
     */
    val automatic: StateFlow<Map<AiTask, AiProvider>> = combine(models, states) { models, states ->
        val automatic = models.copy(assigned = emptyMap())
        AiTask.entries.associateWith { task -> chooseProvider(task, automatic) { states[it].asStatus() } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val downloads = mutableMapOf<AiProvider, Job>()

    init {
        viewModelScope.launch {
            val providers = availability.filterNotNull().first().providers
            // Změna varianty = jiný model; rozběhnuté čtení stavu pro tu předchozí se zahodí.
            providers.forEach { provider ->
                launch {
                    models.map { it.variant(provider) }.distinctUntilChanged().collectLatest { refresh(it) }
                }
            }
        }
        // Zapnutý model (nebo model Gemmy), který tu není, se vymění za dostupný — jinak by
        // sekce ukazovala volbu, kterou zařízení nesplní, a funkce by se nenabídly.
        viewModelScope.launch {
            availability.filterNotNull().collect { available ->
                supportedModels(models.value, available)?.let(settings::setOnDeviceAiModels)
            }
        }
    }

    fun setEnabled(on: Boolean) {
        settings.setOnDeviceAiEnabled(on)
    }

    val replySuggestions: StateFlow<Boolean> = settings.replySuggestionsEnabled

    fun setReplySuggestions(on: Boolean) = settings.setReplySuggestionsEnabled(on)

    val scamWarnings: StateFlow<Boolean> = settings.scamWarningsEnabled

    fun setScamWarnings(on: Boolean) = settings.setScamWarningsEnabled(on)

    val chatTriage: StateFlow<Boolean> = settings.chatTriageEnabled

    val backgroundPhotos: StateFlow<Boolean> = settings.backgroundPhotoDescriptions

    fun setBackgroundPhotos(on: Boolean) = settings.setBackgroundPhotoDescriptions(on)

    /**
     * Umí připravený model popsat fotku (dnes Gemma)? Jen pak má smysl volba popisů na pozadí.
     * Počítá se znovu se změnou modelů i jejich stavu — dostažená Gemma ji zpřístupní.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val canDescribePhotos: StateFlow<Boolean> = combine(models, states) { models, _ -> models }
        .mapLatest { models ->
            runCatching { ai.canDescribeImage(ai.variantFor(AiTask.PhotoDescription, models)) }.getOrDefault(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setChatTriage(on: Boolean) = settings.setChatTriageEnabled(on)

    fun setPreview(on: Boolean) = settings.setOnDeviceAiModels(models.value.copy(preview = on))

    fun setFast(on: Boolean) = settings.setOnDeviceAiModels(models.value.copy(fast = on))

    /** Zvolí velikost modelu: soubor patří Gemmě, Qwenu, nebo EuroLLM ([LiteRtModel.provider]). */
    fun setSize(model: LiteRtModel) = settings.setOnDeviceAiModels(
        when (model.provider) {
            AiProvider.Qwen -> models.value.copy(qwen = model)
            AiProvider.EuroLlm -> models.value.copy(euroLlm = model)
            AiProvider.Aya -> models.value.copy(aya = model)
            else -> models.value.copy(gemma = model)
        },
    )

    /**
     * Zapne nebo vypne model. **Poslední zapnutý vypnout nejde** (přepínač je pak neaktivní):
     * vypnout AI celou je vypínač nahoře, tady se jen volí, čím počítat.
     *
     * Vypnutí rozběhnuté stahování zastaví (stažená část zůstane a jde zahodit) — jinak by úloha
     * WorkManageru stahovala gigabajty modelu, o který uživatel právě řekl, že nestojí. Stažený
     * model se NEMAŽE: zpátky zapnutý je hned připravený a smazat ho jde zvlášť.
     */
    fun setModelEnabled(provider: AiProvider, on: Boolean) {
        val current = models.value
        if (!on && current.enabled == setOf(provider)) return
        settings.setOnDeviceAiModels(current.copy(enabled = if (on) current.enabled + provider else current.enabled - provider))
        if (!on && states.value[provider] is OnDeviceModelState.Downloading) stopDownload(provider)
    }

    /** Přiřadí úlohu modelu; `null` = automaticky (výchozí, neukládá se). */
    fun assign(task: AiTask, provider: AiProvider?) {
        val current = models.value
        val assigned = if (provider == null) current.assigned - task else current.assigned + (task to provider)
        settings.setOnDeviceAiModels(current.copy(assigned = assigned))
    }

    /** Smaže stažený model (jen u modelu, který stahuje aplikace). Gigabajty zpátky. */
    fun delete(provider: AiProvider) {
        val target = models.value.variant(provider)
        appScope.launch {
            try {
                ai.delete(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Model se nepodařilo smazat ($target)" }
            }
            if (isCurrent(target)) refresh(target)
        }
    }

    /** Zastaví stahování modelu, který stahuje aplikace. Stažená část zůstává. */
    fun stopDownload(provider: AiProvider) {
        val target = models.value.variant(provider)
        appScope.launch {
            try {
                ai.stopDownload(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Stahování se nepodařilo zastavit ($target)" }
            }
        }
    }

    /**
     * Nabízí se volba „jen přes Wi-Fi"? Jen kde platforma placenou síť pozná (Android); na
     * desktopu by slibovala něco, co se nestane.
     */
    val wifiOnlyOffered: Boolean = meteredNetwork !== NoMeteredNetwork

    val wifiOnly: StateFlow<Boolean> = settings.modelDownloadWifiOnly

    private val _mobileDataPrompt = MutableStateFlow<AiProvider?>(null)

    /** Model, u kterého se mimo Wi-Fi ptáme, jestli stahovat přes data ([download]). */
    val mobileDataPrompt: StateFlow<AiProvider?> = _mobileDataPrompt.asStateFlow()

    /** Smí stahování podle Nastavení přes data? Bez volby (desktop) vždycky. */
    private fun mobileDataAllowed() = !wifiOnlyOffered || !wifiOnly.value

    /**
     * Zapne/vypne stahování jen přes Wi-Fi. Platí i pro stahování, které právě běží nebo čeká
     * na Wi-Fi: po povolení dat se čekající rozběhne, po zákazu na datech běžící počká.
     */
    fun setWifiOnly(on: Boolean) {
        settings.setModelDownloadWifiOnly(on)
        states.value.filterValues { it is OnDeviceModelState.Downloading }.keys.forEach { provider ->
            val target = models.value.variant(provider)
            if (!target.appManaged) return@forEach
            appScope.launch {
                try {
                    ai.setDownloadNetwork(target, mobileData = !on)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "Síť stahování se nepodařilo změnit ($target)" }
                }
            }
        }
    }

    /**
     * Ťuknutí na Stáhnout. S volbou „jen přes Wi-Fi" na placené síti se napřed zeptá
     * ([mobileDataPrompt]): gigabajty přes data jedním ťuknutím nikdo nechce omylem.
     */
    fun download(provider: AiProvider) {
        if (downloads[provider]?.isActive == true) return
        if (models.value.variant(provider).appManaged && !mobileDataAllowed() && meteredNetwork.isMetered()) {
            _mobileDataPrompt.value = provider
            return
        }
        startDownload(provider, mobileData = mobileDataAllowed())
    }

    /** Odpověď na [mobileDataPrompt]: stáhnout hned, přes data (jen tohle stažení). */
    fun downloadOverMobileData() {
        val provider = _mobileDataPrompt.value ?: return
        _mobileDataPrompt.value = null
        startDownload(provider, mobileData = true)
    }

    /**
     * Odpověď na [mobileDataPrompt]: stahování naplánovat a počkat na Wi-Fi. I při zavření
     * dialogu mimo tlačítka — uživatel o model řekl a čekání na Wi-Fi nic nestojí.
     */
    fun downloadOnWifi() {
        val provider = _mobileDataPrompt.value ?: return
        _mobileDataPrompt.value = null
        startDownload(provider, mobileData = false)
    }

    private fun startDownload(provider: AiProvider, mobileData: Boolean) {
        if (downloads[provider]?.isActive == true) return
        val target = models.value.variant(provider)
        setState(provider, OnDeviceModelState.Downloading(null))
        downloads[provider] = appScope.launch {
            try {
                ai.download(target, mobileData).collect { progress ->
                    if (isCurrent(target)) setState(provider, OnDeviceModelState.Downloading(progress))
                }
                if (isCurrent(target)) refresh(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadStoppedException) {
                if (isCurrent(target)) setState(provider, OnDeviceModelState.Paused)
            } catch (e: NotEnoughSpaceException) {
                logger.i(e) { "Na model není místo ($target)" }
                if (isCurrent(target)) setState(provider, OnDeviceModelState.NotEnoughSpace)
            } catch (e: RuntimeInstallException) {
                logger.w(e) { "Runtime modelu se nepodařilo doinstalovat ($target)" }
                if (isCurrent(target)) setState(provider, OnDeviceModelState.RuntimeInstallFailed(e.code))
            } catch (e: Exception) {
                logger.w(e) { "Model se nepodařilo stáhnout ($target)" }
                if (isCurrent(target)) setState(provider, OnDeviceModelState.DownloadFailed)
            }
        }
    }

    /** Je [variant] pořád to, co je u jejího modelu zvolené? Jinak se její výsledek zahodí. */
    private fun isCurrent(variant: ModelVariant) = models.value.variant(variant.provider) == variant

    private fun setState(provider: AiProvider, state: OnDeviceModelState) {
        _states.update { it + (provider to state) }
    }

    private suspend fun refresh(variant: ModelVariant) {
        val provider = variant.provider
        setState(provider, OnDeviceModelState.Checking)
        val state = when (runCatching { ai.status(variant) }.getOrDefault(OnDeviceAiStatus.Unsupported)) {
            OnDeviceAiStatus.Unsupported -> OnDeviceModelState.Unsupported
            OnDeviceAiStatus.Downloadable -> OnDeviceModelState.Downloadable
            OnDeviceAiStatus.Paused -> OnDeviceModelState.Paused
            // Připojení k běžícímu stahování, ne nové ťuknutí: bez dotazu na síť.
            OnDeviceAiStatus.Downloading -> {
                startDownload(provider, mobileData = mobileDataAllowed())
                return
            }
            OnDeviceAiStatus.Ready -> ai.info(variant).let { OnDeviceModelState.Ready(it?.name, it?.tokenLimit) }
        }
        setState(provider, state)
    }
}

/**
 * Stav z obrazovky pro výběr „Automaticky" ([chooseProvider]). Rozhoduje jen připravený model
 * a model, který zařízení neumí; ostatní stavy (stahuje se, dá se stáhnout, ještě nevíme) jsou
 * pro výběr totéž — model tu je, jen ještě ne připravený.
 */
private fun OnDeviceModelState?.asStatus(): OnDeviceAiStatus = when (this) {
    is OnDeviceModelState.Ready -> OnDeviceAiStatus.Ready
    OnDeviceModelState.Unsupported -> OnDeviceAiStatus.Unsupported
    else -> OnDeviceAiStatus.Downloadable
}

/**
 * Co zařízení umí: kteří poskytovatelé a které stahované modely. Ptá se ho ViewModel sekce
 * i seznam sekcí Nastavení, který sekci AI v zařízení bez modelu vůbec nenabízí.
 *
 * U Gemini Nano se ptá na VÝCHOZÍ variantu, ne na tu zvolenou: preview nebo rychlá může chybět
 * zvlášť, a kvůli tomu by celá sekce zmizela uživateli, který model má. **Stahovaný poskytovatel
 * je tu, když zařízení unese aspoň jednu jeho velikost** — telefon se 6 GB na Qwen3 4B nestačí,
 * na Qwen3 0,6B ano, a výchozí (největší) velikost by ho jinak schovala celý.
 */
fun OnDeviceAi.availability(): Flow<AiAvailability> = flow {
    val models = LiteRtModel.entries.filter { status(it.variant) != OnDeviceAiStatus.Unsupported }
    emit(
        AiAvailability(
            providers = AiProvider.entries.filter { provider ->
                if (LiteRtModel.entries.any { it.provider == provider }) {
                    models.any { it.provider == provider }
                } else {
                    status(ModelVariant(provider = provider)) != OnDeviceAiStatus.Unsupported
                }
            },
            models = models,
        ),
    )
}.catch { emit(AiAvailability(emptyList(), emptyList())) }

/** Co zařízení umí: dostupní poskytovatelé a stahované modely, které se tu dají pustit. */
data class AiAvailability(val providers: List<AiProvider>, val models: List<LiteRtModel>) {
    /** Velikosti modelu [provider], které zařízení unese, od největší. */
    fun sizes(provider: AiProvider): List<LiteRtModel> = models.filter { it.provider == provider }
}

/**
 * Modely opravené na to, co zařízení umí, nebo `null`, když jsou v pořádku.
 *
 * Zapnutý model, který tu není (telefon bez AICore, uložená volba z jiného zařízení, výchozí
 * Gemini Nano na iOS), se vypustí; když tím nezbude žádný, zapne se první dostupný. Velikost
 * Gemmy nebo Qwenu, na kterou nestačí paměť, se vymění za největší, na kterou ano. Ruční přiřazení se nechávají —
 * přiřazení k nezapnutému modelu se nepočítá ([AiModels.assignment]), a kdyby se model vrátil
 * (obnova zálohy), platí zase. Bez dostupného poskytovatele se nemění nic: sekce se pak vůbec
 * nenabízí a opravovat není co.
 */
fun supportedModels(current: AiModels, available: AiAvailability): AiModels? {
    val first = available.providers.firstOrNull() ?: return null
    val enabled = current.enabled.filterTo(LinkedHashSet()) { it in available.providers }.ifEmpty { setOf(first) }
    fun size(chosen: LiteRtModel) = chosen.takeIf { it in available.models }
        ?: available.sizes(chosen.provider).firstOrNull()
        ?: chosen
    val fixed = current.copy(
        enabled = enabled,
        gemma = size(current.gemma),
        qwen = size(current.qwen),
        euroLlm = size(current.euroLlm),
        aya = size(current.aya),
    )
    return fixed.takeIf { it != current }
}

/**
 * Obsah sekce „AI v zařízení". Modely a co čím počítat se ukazují, jen když je volba
 * zapnutá — kdo o funkci nestojí, nemá co řešit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnDeviceAiSettings(viewModel: OnDeviceAiSettingsViewModel, onOpenPlayground: () -> Unit) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val states by viewModel.states.collectAsStateWithLifecycle()
    val automatic by viewModel.automatic.collectAsStateWithLifecycle()
    val replySuggestions by viewModel.replySuggestions.collectAsStateWithLifecycle()
    val scamWarnings by viewModel.scamWarnings.collectAsStateWithLifecycle()
    val chatTriage by viewModel.chatTriage.collectAsStateWithLifecycle()
    val backgroundPhotos by viewModel.backgroundPhotos.collectAsStateWithLifecycle()
    val canDescribePhotos by viewModel.canDescribePhotos.collectAsStateWithLifecycle()
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val mobileDataPrompt by viewModel.mobileDataPrompt.collectAsStateWithLifecycle()

    /*
     * Na zařízení bez AICore se celá sekce NENABÍZÍ. Dřív se vykreslila vždycky a uživatel
     * zapnul přepínač, aby se teprve pak dozvěděl „tohle zařízení model nemá" — nabídka funkce,
     * kterou nelze mít. Stejné pravidlo jako u filtrů seznamu chatů, které nemají co odfiltrovat.
     *
     * Dokud stav nedorazí (`null`), taky se nekreslí: objevení sekce ruší míň než její zmizení,
     * a je to totéž rozhodnutí jako u filtrů, které naběhnou až s načteným seznamem.
     */
    val available = availability?.takeIf { it.providers.isNotEmpty() } ?: return

    TitledGroup(Res.string.settings_on_device_ai) {
        SwitchListItem(
            headline = stringResource(Res.string.settings_on_device_ai_enable),
            checked = enabled,
            supporting = stringResource(Res.string.settings_on_device_ai_enable_summary),
            onCheckedChange = viewModel::setEnabled,
        )

        if (enabled) {
            GroupBreak()
            /*
             * Návrhy odpovědí mají vlastní vypínač, shrnutí ne. Není to nedůslednost: shrnutí
             * se otevírá vědomě ťuknutím na ikonu, kdežto návrhy se objeví samy pod rozhovorem,
             * takže o ně nemusí stát ani ten, kdo shrnutí používá rád.
             */
            VariantSwitch(
                title = Res.string.settings_reply_suggestions,
                summary = Res.string.settings_reply_suggestions_summary,
                checked = replySuggestions,
                onChange = { on -> viewModel.setReplySuggestions(on) },
            )
            // Varování před podvodem má vlastní vypínač ze stejného důvodu: model posuzuje
            // příchozí zprávy sám, bez ťuknutí.
            VariantSwitch(
                title = Res.string.settings_scam_warnings,
                summary = Res.string.settings_scam_warnings_summary,
                checked = scamWarnings,
                onChange = { on -> viewModel.setScamWarnings(on) },
            )
            // Odznak „čeká na odpověď" taky: model posuzuje nepřečtené chaty sám.
            VariantSwitch(
                title = Res.string.settings_chat_triage,
                summary = Res.string.settings_chat_triage_summary,
                checked = chatTriage,
                onChange = { on -> viewModel.setChatTriage(on) },
            )
            // Popisy fotek předem: model běží sám na pozadí (jen na nabíječce), proto vlastní
            // vypínač. Jen s modelem, který obraz bere, a jen kde je pozadí (Android).
            if (canDescribePhotos && backgroundPhotoDescriptionsSupported) {
                VariantSwitch(
                    title = Res.string.settings_background_photos,
                    summary = Res.string.settings_background_photos_summary,
                    checked = backgroundPhotos,
                    onChange = { on -> viewModel.setBackgroundPhotos(on) },
                )
            }
        }
    }
    if (!enabled) return

    /*
     * Modely. Přepínač u modelu je, jen když je z čeho vybírat — na telefonu bez AICore je
     * jediná možnost Gemma a přepínač, který nejde vypnout, by byl jen šum. Zapnout jde víc
     * modelů naráz: Nano na text a Gemmu na to, co Nano neumí (zvuk, obraz).
     */
    val choosable = available.providers.size > 1
    TitledGroup(if (choosable) Res.string.settings_ai_models else Res.string.settings_ai_model) {
        available.providers.forEachIndexed { index, provider ->
            if (index > 0) GroupBreak()
            val on = provider in models.enabled
            if (choosable) {
                VariantSwitch(
                    title = provider.title,
                    summary = provider.summary,
                    checked = on,
                    onChange = { checked -> viewModel.setModelEnabled(provider, checked) },
                    // Poslední zapnutý vypnout nejde: vypnout AI je přepínač nahoře.
                    enabled = !on || models.enabled.size > 1,
                )
            }
            val state = states[provider] ?: OnDeviceModelState.Checking
            if (on) {
                ModelOptions(provider, models, available, viewModel)
                ModelStatus(models.variant(provider), state, viewModel)
            } else if (provider.appManaged && (state is OnDeviceModelState.Ready || state == OnDeviceModelState.Paused)) {
                // Vypnutá, ale stažená Gemma pořád zabírá gigabajty — musí jít smazat i teď.
                ModelStatus(models.variant(provider), state, viewModel)
            }
        }
        /*
         * Síť pro stahování: jen u modelů, které stahuje aplikace (systémový model stahuje AICore
         * po svém), a jen kde platforma placenou síť pozná. Pod modely, ne u každého zvlášť —
         * volba platí pro všechny a u tří modelů by se opakovala třikrát.
         */
        if (viewModel.wifiOnlyOffered && available.providers.any { it.appManaged }) {
            GroupBreak()
            VariantSwitch(
                title = Res.string.settings_ai_wifi_only,
                summary = Res.string.settings_ai_wifi_only_summary,
                checked = wifiOnly,
                onChange = { on -> viewModel.setWifiOnly(on) },
            )
        }
        /*
         * Hřiště pod modely, ne v seznamu funkcí nahoře: není to funkce, která se zapíná, ale
         * místo, kde se zapnuté modely zkouší (a porovnávají přepnutím v jeho hlavičce).
         */
        GroupBreak()
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.chat_bubble_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.ai_playground_title)) },
            supportingContent = { Text(stringResource(Res.string.ai_playground_row_help)) },
            modifier = Modifier.clickable(onClick = onOpenPlayground),
        )
    }

    mobileDataPrompt?.let { provider ->
        val file = models.variant(provider).model
        ConfirmDialog(
            title = stringResource(Res.string.settings_ai_mobile_data_title),
            text = file?.let { stringResource(Res.string.settings_ai_mobile_data_text, it.displayName, fileSizeText(it.sizeBytes)) },
            confirmLabel = stringResource(Res.string.settings_ai_mobile_data_confirm),
            onConfirm = viewModel::downloadOverMobileData,
            // Zavření mimo tlačítka = počkat: o model uživatel řekl a čekání na Wi-Fi nic nestojí.
            onDismiss = viewModel::downloadOnWifi,
            dismissLabel = stringResource(Res.string.settings_ai_mobile_data_wait),
            icon = Res.drawable.signal_cellular_alt_24dp,
        )
    }

    /*
     * Co čím počítat — jen s víc zapnutými modely; s jedním není co přiřazovat. Řádek má jen
     * úloha, kterou zvládne víc zapnutých modelů: přepis řeči a popis fotek umí jen Gemma
     * a volba „Automaticky / Gemma" by nic nevolila. Řekne to věta pod řádky.
     */
    val enabledHere = available.providers.filter { it in models.enabled }
    if (enabledHere.size > 1) {
        TitledGroup(Res.string.settings_ai_tasks) {
            AiTask.entries.filter { task -> enabledHere.count { it.handles(task) } > 1 }.forEach { task ->
                val options = listOf<AiProvider?>(null) + enabledHere.filter { it.handles(task) }
                val chosen = models.assignment(task)
                ChoiceRow(
                    headline = stringResource(task.title),
                    options = options,
                    selected = chosen,
                    label = { option ->
                        if (option == null) {
                            automatic[task]?.let { stringResource(Res.string.settings_ai_task_auto_named, stringResource(it.title)) }
                                ?: stringResource(Res.string.settings_ai_task_auto)
                        } else {
                            // Ručně zvolený model, který ještě není připravený: funkce se bez něj
                            // nenabídne, tak ať je u řádku vidět proč.
                            val name = stringResource(option.title)
                            if (states[option] is OnDeviceModelState.Ready) name
                            else stringResource(Res.string.settings_ai_task_not_ready, name)
                        }
                    },
                    onSelect = { viewModel.assign(task, it) },
                )
            }
            // Kam jde text automaticky, říká věta jen tam, kde to platí: Nano před EuroLLM,
            // EuroLLM před Ayou, Aya před Qwenem a Qwen před Gemmou (pořadí `chooseProvider`).
            val note = when {
                AiProvider.Gemma !in enabledHere -> null
                AiProvider.GeminiNano in enabledHere -> Res.string.settings_ai_tasks_note
                AiProvider.EuroLlm in enabledHere -> Res.string.settings_ai_tasks_note_eurollm
                AiProvider.Aya in enabledHere -> Res.string.settings_ai_tasks_note_aya
                AiProvider.Qwen in enabledHere -> Res.string.settings_ai_tasks_note_qwen
                else -> null
            }
            if (note != null) {
                Text(
                    stringResource(note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

/** Volby zapnutého modelu: preview a rychlá varianta u Nano, velikost u Gemmy. */
@Composable
private fun ModelOptions(
    provider: AiProvider,
    models: AiModels,
    available: AiAvailability,
    viewModel: OnDeviceAiSettingsViewModel,
) {
    when (provider) {
        AiProvider.GeminiNano -> {
            VariantSwitch(
                title = Res.string.settings_on_device_ai_preview,
                summary = Res.string.settings_on_device_ai_preview_summary,
                checked = models.preview,
                onChange = { on -> viewModel.setPreview(on) },
            )
            VariantSwitch(
                title = Res.string.settings_on_device_ai_fast,
                summary = Res.string.settings_on_device_ai_fast_summary,
                checked = models.fast,
                onChange = { on -> viewModel.setFast(on) },
            )
        }
        // Větší model se nabídne jen tam, kde na něj stačí paměť.
        AiProvider.Gemma -> {
            SizeChoices(available.sizes(provider), models.gemma, viewModel::setSize)
            /*
             * Pod úsporným režimem a přehřátím Gemma sama nic nepočítá (`proactiveVariant`).
             * Bez téhle věty by uživatel s jedinou Gemmou nepoznal, proč návrhy a varování
             * zmizely — vypadalo by to jako rozbitý model.
             */
            if (viewModel.tracksLoad) {
                Text(
                    stringResource(Res.string.settings_gemma_under_load),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
        // Velikosti jako u Gemmy; malé jsou pro telefony, na které Gemma nestačí.
        AiProvider.Qwen -> {
            SizeChoices(available.sizes(provider), models.qwen, viewModel::setSize)
            if (viewModel.tracksLoad) {
                Text(
                    stringResource(Res.string.settings_qwen_under_load),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
        // Velikosti jako u Qwenu: 9B pro telefony s 16 GB, EuroMoE pro ostatní.
        AiProvider.EuroLlm -> {
            SizeChoices(available.sizes(provider), models.euroLlm, viewModel::setSize)
            if (viewModel.tracksLoad) {
                Text(
                    stringResource(Res.string.settings_eurollm_under_load),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
        /*
         * Jediná velikost, tedy bez volby. Licence je nekomerční a přijímá ji ten, kdo model
         * stahuje — proto je tady, nad tlačítkem „Stáhnout model", ne v nápovědě.
         */
        AiProvider.Aya -> {
            Text(
                stringResource(Res.string.settings_aya_license),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            if (viewModel.tracksLoad) {
                Text(
                    stringResource(Res.string.settings_aya_under_load),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
        // Systémový model bez voleb: verzi i stahování řídí iOS.
        AiProvider.Apple -> Unit
    }
}

/** Stav jednoho modelu, průběh stahování a tlačítka (stáhnout, zastavit, smazat). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelStatus(variant: ModelVariant, model: OnDeviceModelState, viewModel: OnDeviceAiSettingsViewModel) {
    val provider = variant.provider
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        val status = when (model) {
            OnDeviceModelState.Checking -> stringResource(Res.string.settings_on_device_ai_checking)
            OnDeviceModelState.Unsupported -> stringResource(Res.string.settings_on_device_ai_unsupported)
            /*
             * U modelu, který stahuje aplikace, se jmenuje CO a KOLIK. „Zabere gigabajty"
             * stačí u systémového modelu, jehož velikost nám nikdo neřekne; tady ji víme
             * a pod 2,4 GB se uživatel podepisuje, ne pod „nějaké gigabajty".
             */
            OnDeviceModelState.Downloadable -> variant.model?.let { file ->
                stringResource(
                    Res.string.settings_on_device_ai_downloadable_named,
                    file.displayName,
                    fileSizeText(file.sizeBytes),
                )
            } ?: run {
                stringResource(Res.string.settings_on_device_ai_downloadable)
            }
            is OnDeviceModelState.Downloading -> if (model.progress?.waitingForNetwork == true) {
                stringResource(
                    if (model.progress?.wifiOnly == true) Res.string.settings_on_device_ai_waiting_for_wifi
                    else Res.string.settings_on_device_ai_waiting_for_network,
                )
            } else {
                stringResource(Res.string.settings_on_device_ai_downloading)
            }
            OnDeviceModelState.DownloadFailed -> stringResource(Res.string.settings_on_device_ai_download_failed)
            /*
             * Jiná věta než u stahování modelu: tady chybí kus aplikace, ne model, a kód od Play
             * (APP_NOT_OWNED -15 i u aplikace z Play) je to, podle čeho se dá chyba dohledat.
             */
            is OnDeviceModelState.RuntimeInstallFailed -> model.code
                ?.let { stringResource(Res.string.settings_on_device_ai_runtime_failed_code, it) }
                ?: stringResource(Res.string.settings_on_device_ai_runtime_failed)
            OnDeviceModelState.Paused -> stringResource(Res.string.settings_on_device_ai_paused)
            OnDeviceModelState.NotEnoughSpace -> stringResource(Res.string.settings_on_device_ai_not_enough_space)
            is OnDeviceModelState.Ready -> model.name
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

        when (model) {
            is OnDeviceModelState.Downloading -> {
                val fraction = model.progress?.fraction
                if (fraction != null) {
                    LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                }
                /*
                 * Stahování Gemmy běží ve WorkManageru a přežije i zabití aplikace, takže
                 * bez tlačítka by ho nešlo zastavit vůbec. Systémový model stahuje AICore
                 * a ten zastavit nedovolí.
                 */
                if (variant.appManaged) {
                    OutlinedButton(onClick = { viewModel.stopDownload(provider) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(Res.string.settings_on_device_ai_stop))
                    }
                }
            }
            OnDeviceModelState.Downloadable, OnDeviceModelState.DownloadFailed, OnDeviceModelState.NotEnoughSpace,
            OnDeviceModelState.Paused, is OnDeviceModelState.RuntimeInstallFailed ->
                FilledTonalButton(onClick = { viewModel.download(provider) }, shapes = ButtonDefaults.shapes()) {
                    // Stažený kus se nezahazuje, takže po selhání i zastavení tlačítko slibuje navázání.
                    val resumes = variant.appManaged &&
                        (model == OnDeviceModelState.DownloadFailed || model == OnDeviceModelState.Paused ||
                            model is OnDeviceModelState.RuntimeInstallFailed)
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
                OutlinedButton(onClick = { viewModel.delete(provider) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.settings_on_device_ai_delete))
                }
            }
            else -> Unit
        }
        /*
         * Zčásti stažený model jsou klidně gigabajty, a bez tohohle by šly z telefonu
         * dostat jen dokončením stahování a smazáním hotového modelu.
         */
        if (model == OnDeviceModelState.Paused && variant.appManaged) {
            OutlinedButton(onClick = { viewModel.delete(provider) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.settings_on_device_ai_discard_part))
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
    enabled: Boolean = true,
) {
    SwitchListItem(
        checked = checked,
        onCheckedChange = onChange,
        headlineContent = { Text(stringResource(title)) },
        modifier = Modifier.highlightAnchor(stringResource(title)),
        supportingContent = { Text(stringResource(summary)) },
        enabled = enabled,
    )
}

/** Velikosti jednoho modelu, jen když je z čeho vybírat. */
@Composable
private fun SizeChoices(sizes: List<LiteRtModel>, selected: LiteRtModel, onSelect: (LiteRtModel) -> Unit) {
    if (sizes.size < 2) return
    sizes.forEach { size ->
        ModelChoiceRow(
            title = size.displayName,
            summary = stringResource(size.summary, fileSizeText(size.sizeBytes)),
            selected = selected == size,
            onSelect = { onSelect(size) },
        )
    }
}

/** Jedna volba z několika (velikost modelu). Klikání nese celý řádek, jako u motivu. */
@Composable
private fun ModelChoiceRow(title: String, summary: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    )
}

/** Jméno modelu pro člověka; bere ho i porovnání modelů v sheetu shrnutí. */
internal val AiProvider.title: StringResource
    get() = when (this) {
        AiProvider.GeminiNano -> Res.string.settings_ai_provider_nano
        AiProvider.Gemma -> Res.string.settings_ai_provider_gemma
        AiProvider.Qwen -> Res.string.settings_ai_provider_qwen
        AiProvider.EuroLlm -> Res.string.settings_ai_provider_eurollm
        AiProvider.Aya -> Res.string.settings_ai_provider_aya
        AiProvider.Apple -> Res.string.settings_ai_provider_apple
    }

private val AiProvider.summary: StringResource
    get() = when (this) {
        AiProvider.GeminiNano -> Res.string.settings_ai_provider_nano_summary
        AiProvider.Gemma -> Res.string.settings_ai_provider_gemma_summary
        AiProvider.Qwen -> Res.string.settings_ai_provider_qwen_summary
        AiProvider.EuroLlm -> Res.string.settings_ai_provider_eurollm_summary
        AiProvider.Aya -> Res.string.settings_ai_provider_aya_summary
        AiProvider.Apple -> Res.string.settings_ai_provider_apple_summary
    }

/** Popis velikosti u volby modelu. */
private val LiteRtModel.summary: StringResource
    get() = when (this) {
        LiteRtModel.E2B -> Res.string.settings_gemma_e2b_summary
        LiteRtModel.E4B -> Res.string.settings_gemma_e4b_summary
        LiteRtModel.Qwen3_4B -> Res.string.settings_qwen_4b_summary
        LiteRtModel.Qwen2_5_1_5B -> Res.string.settings_qwen_1_5b_summary
        LiteRtModel.Qwen3_0_6B -> Res.string.settings_qwen_0_6b_summary
        LiteRtModel.EuroLlm9B -> Res.string.settings_eurollm_9b_summary
        LiteRtModel.EuroMoe2_6B -> Res.string.settings_euromoe_summary
        LiteRtModel.AyaExpanse8B -> Res.string.settings_aya_8b_summary
    }

/** Model stahuje a maže aplikace sama ([ModelVariant.appManaged]). */
private val AiProvider.appManaged: Boolean get() = ModelVariant(provider = this).appManaged

private val AiTask.title: StringResource
    get() = when (this) {
        AiTask.Summary -> Res.string.ai_task_summary
        AiTask.Explain -> Res.string.ai_task_explain
        AiTask.Replies -> Res.string.ai_task_replies
        AiTask.Rewrite -> Res.string.ai_task_rewrite
        AiTask.Dates -> Res.string.ai_task_dates
        AiTask.Scam -> Res.string.ai_task_scam
        AiTask.Triage -> Res.string.ai_task_triage
        AiTask.Search -> Res.string.ai_task_search
        AiTask.Folders -> Res.string.ai_task_folders
        AiTask.Chat -> Res.string.ai_task_chat
        AiTask.Transcription -> Res.string.ai_task_transcription
        AiTask.PhotoDescription -> Res.string.ai_task_photo_description
    }
