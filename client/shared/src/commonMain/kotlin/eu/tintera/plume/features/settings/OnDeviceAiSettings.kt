package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_on_device_ai
import eu.tintera.plume.resources.settings_on_device_ai_checking
import eu.tintera.plume.resources.settings_on_device_ai_download
import eu.tintera.plume.resources.settings_on_device_ai_download_failed
import eu.tintera.plume.resources.settings_on_device_ai_downloadable
import eu.tintera.plume.resources.settings_on_device_ai_downloading
import eu.tintera.plume.resources.settings_on_device_ai_enable
import eu.tintera.plume.resources.settings_on_device_ai_enable_summary
import eu.tintera.plume.resources.settings_on_device_ai_fast
import eu.tintera.plume.resources.settings_on_device_ai_fast_summary
import eu.tintera.plume.resources.settings_on_device_ai_preview
import eu.tintera.plume.resources.settings_on_device_ai_preview_summary
import eu.tintera.plume.resources.settings_on_device_ai_token_limit
import eu.tintera.plume.resources.settings_on_device_ai_ready
import eu.tintera.plume.resources.settings_on_device_ai_ready_unnamed
import eu.tintera.plume.resources.settings_on_device_ai_unsupported
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Stav modelu, jak ho ukazuje Nastavení. */
sealed interface OnDeviceModelState {
    data object Checking : OnDeviceModelState
    data object Unsupported : OnDeviceModelState
    data object Downloadable : OnDeviceModelState
    data class Downloading(val fraction: Float?) : OnDeviceModelState
    data object DownloadFailed : OnDeviceModelState
    data class Ready(val name: String?, val tokenLimit: Int?) : OnDeviceModelState
}

/**
 * Volba „Shrnutí chatu", varianta modelu a jeho stažení.
 *
 * Stahování běží v [appScope], ne ve `viewModelScope`: trvá minuty a odchod z Nastavení ho
 * nemá zrušit. Nová obrazovka se k běžícímu stahování připojí (`download()` ve stavu
 * `Downloading`), takže průběh ukáže znovu.
 *
 * Varianta je pro AICore jiný model: po její změně se stav čte znovu a preview nebo rychlá
 * varianta se může chtít stáhnout zvlášť.
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

    private var download: Job? = null

    init {
        // Změna varianty = jiný model; rozběhnuté čtení stavu pro tu předchozí se zahodí.
        viewModelScope.launch { variant.collectLatest { refresh(it) } }
    }

    fun setEnabled(on: Boolean) {
        settings.setOnDeviceAiEnabled(on)
    }

    fun setPreview(on: Boolean) = settings.setOnDeviceAiVariant(variant.value.copy(preview = on))

    fun setFast(on: Boolean) = settings.setOnDeviceAiVariant(variant.value.copy(fast = on))

    fun download() {
        if (download?.isActive == true) return
        val target = variant.value
        _model.value = OnDeviceModelState.Downloading(null)
        download = appScope.launch {
            try {
                ai.download(target).collect { progress ->
                    if (variant.value == target) _model.value = OnDeviceModelState.Downloading(progress.fraction)
                }
                if (variant.value == target) refresh(target)
            } catch (e: CancellationException) {
                throw e
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
    }
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
            VariantSwitch(
                title = Res.string.settings_on_device_ai_preview,
                summary = Res.string.settings_on_device_ai_preview_summary,
                checked = variant.preview,
                onChange = { on ->
                    haptics.toggle(on)
                    viewModel.setPreview(on)
                },
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
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                val status = when (val current = model) {
                    OnDeviceModelState.Checking -> stringResource(Res.string.settings_on_device_ai_checking)
                    OnDeviceModelState.Unsupported -> stringResource(Res.string.settings_on_device_ai_unsupported)
                    OnDeviceModelState.Downloadable -> stringResource(Res.string.settings_on_device_ai_downloadable)
                    is OnDeviceModelState.Downloading -> stringResource(Res.string.settings_on_device_ai_downloading)
                    OnDeviceModelState.DownloadFailed -> stringResource(Res.string.settings_on_device_ai_download_failed)
                    is OnDeviceModelState.Ready -> current.name
                        ?.let { stringResource(Res.string.settings_on_device_ai_ready, it) }
                        ?: stringResource(Res.string.settings_on_device_ai_ready_unnamed)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                (model as? OnDeviceModelState.Ready)?.tokenLimit?.let { limit ->
                    Text(
                        stringResource(Res.string.settings_on_device_ai_token_limit, limit.grouped()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (val current = model) {
                    is OnDeviceModelState.Downloading -> {
                        val fraction = current.fraction
                        if (fraction != null) {
                            LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    OnDeviceModelState.Downloadable, OnDeviceModelState.DownloadFailed ->
                        FilledTonalButton(onClick = viewModel::download) {
                            Text(stringResource(Res.string.settings_on_device_ai_download))
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
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}
