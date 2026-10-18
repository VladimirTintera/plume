package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ui.DialogCheckRow
import eu.tintera.plume.resources.delete_sweep_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.resources.data_usage_24dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import eu.tintera.plume.core.ui.ChoiceRow
import eu.tintera.plume.core.ui.MediumButtonIcon
import eu.tintera.plume.core.ui.ButtonEmphasis
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.resources.network_usage_group
import eu.tintera.plume.resources.network_usage_summary
import eu.tintera.plume.resources.network_usage_title
import eu.tintera.plume.resources.storage_delete_pack_title
import eu.tintera.plume.resources.storage_pack_deleted
import eu.tintera.plume.resources.storage_delete_pack_body
import eu.tintera.plume.resources.storage_translation_packs
import eu.tintera.plume.resources.translate_24dp
import eu.tintera.plume.core.files.autosavesToGallery
import eu.tintera.plume.core.telegram.localizedLanguageName
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ai.GemmaModel
import eu.tintera.plume.core.settings.CacheLimit
import eu.tintera.plume.core.settings.KeepMedia
import eu.tintera.plume.core.telegram.CategoryUsage
import eu.tintera.plume.core.telegram.ChatStorage
import eu.tintera.plume.core.telegram.DEFAULT_CLEAR_CATEGORIES
import eu.tintera.plume.core.telegram.OTHER_CHATS
import eu.tintera.plume.core.telegram.StorageCategory
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.common_delete
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.database_24dp
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.gif_box_24dp
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.music_note_24dp
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.resources.videocam_24dp
import eu.tintera.plume.resources.storage_ai_model_partial
import eu.tintera.plume.resources.storage_ai_models
import eu.tintera.plume.resources.storage_category_files
import eu.tintera.plume.resources.storage_category_gifs
import eu.tintera.plume.resources.storage_category_music
import eu.tintera.plume.resources.storage_category_other
import eu.tintera.plume.resources.storage_category_photos
import eu.tintera.plume.resources.storage_category_stickers
import eu.tintera.plume.resources.storage_category_videos
import eu.tintera.plume.resources.storage_category_voice
import eu.tintera.plume.resources.storage_chats
import eu.tintera.plume.resources.storage_clear
import eu.tintera.plume.resources.storage_clear_body
import eu.tintera.plume.resources.storage_clear_chat_title
import eu.tintera.plume.resources.storage_clear_confirm
import eu.tintera.plume.resources.storage_clear_title
import eu.tintera.plume.resources.storage_computing
import eu.tintera.plume.resources.storage_database
import eu.tintera.plume.resources.storage_database_summary
import eu.tintera.plume.resources.storage_delete_model_body
import eu.tintera.plume.resources.storage_delete_model_title
import eu.tintera.plume.resources.storage_error_load
import eu.tintera.plume.resources.storage_freed
import eu.tintera.plume.resources.storage_keep_media
import eu.tintera.plume.resources.storage_cache_limit
import eu.tintera.plume.resources.storage_cache_limit_body
import eu.tintera.plume.resources.storage_cache_limit_off
import eu.tintera.plume.resources.storage_keep_media_body
import eu.tintera.plume.resources.storage_keep_media_title
import eu.tintera.plume.resources.storage_keep_month
import eu.tintera.plume.resources.storage_keep_forever
import eu.tintera.plume.resources.storage_keep_three_days
import eu.tintera.plume.resources.storage_keep_week
import eu.tintera.plume.resources.storage_model_deleted
import eu.tintera.plume.resources.storage_nothing
import eu.tintera.plume.resources.storage_other_chats
import eu.tintera.plume.resources.storage_total_summary
import eu.tintera.plume.resources.storage_used
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sekce „Úložiště": automatické stahování podle sítě, kolik zabírají stažené soubory a databáze,
 * mazání médií (všude i v jednom chatu), automatické mazání starých médií a stažený model AI.
 *
 * Obsah do sloupce sekce, ne celá obrazovka — hlavičku a scrollování drží `SettingsSectionScreen`.
 */
@Composable
fun StorageSettings(viewModel: StorageViewModel, snackbarHostState: SnackbarHostState, onOpenNetworkUsage: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keepMedia by viewModel.keepMedia.collectAsStateWithLifecycle()
    val cacheLimit by viewModel.cacheLimit.collectAsStateWithLifecycle()

    // Dialog mazání nese chat (`OTHER_CHATS`-ne, `null` = všude); obsah se bere ze stavu.
    var clearAll by rememberSaveable { mutableStateOf(false) }
    var clearChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteModel by rememberSaveable { mutableStateOf<GemmaModel?>(null) }
    var deletePack by rememberSaveable { mutableStateOf<String?>(null) }

    val notice = state.notice
    val noticeText = notice?.text()
    val scope = rememberCoroutineScope()
    // Snackbar běží mimo tělo efektu: vynulování hlášky mění klíč efektu a zrušilo by ho.
    LaunchedEffect(notice) {
        if (noticeText != null) {
            viewModel.noticeShown()
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }

    // Mezitím se mohlo cokoli stáhnout (nebo smazat úklidem).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // Nahoře a mimo načítání přehledu: s místem na disku nesouvisí, jen bydlí ve stejné sekci.
    AutoDownloadGroup(viewModel = koinViewModel())

    val usage = state.usage
    when {
        usage == null && state.loadFailed -> Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.storage_error_load), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = viewModel::refresh, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_try_again)) }
        }

        usage == null && state.databaseBytes == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }

        else -> {
            SettingsGroup(Res.string.storage_used) {
                if (usage == null) {
                    /*
                     * Podrobný přehled prochází celý adresář. Součet z rychlého přehledu se
                     * NEUKAZUJE: je to zastaralý odhad (na emulátoru 12 MB proti skutečným 65 MB)
                     * a skok o pětinásobek by vypadal jako chyba.
                     */
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { LoadingIndicator(Modifier.size(40.dp)) },
                        headlineContent = { Text(stringResource(Res.string.storage_computing)) },
                    )
                } else {
                    UsageHeader(filesBytes = usage.filesBytes)
                    usage.categories.forEach { part -> CategoryRow(part, total = usage.filesBytes) }
                }
                state.databaseBytes?.let { DatabaseRow(it) }
            }

            // Hlavní akce sekce, proto tlačítko a ne řádek. Tonální: maže, ale nic nenávratného —
            // soubory zůstávají na serveru.
            MediumButton(
                onClick = { clearAll = true },
                enabled = usage != null && usage.categories.isNotEmpty() && !state.clearing,
                modifier = Modifier.fillMaxWidth(),
                emphasis = ButtonEmphasis.Tonal,
            ) {
                if (state.clearing) {
                    LoadingIndicator(Modifier.padding(end = 8.dp).size(24.dp))
                } else {
                    MediumButtonIcon(Res.drawable.delete_24dp)
                }
                Text(stringResource(Res.string.storage_clear))
            }

            // Přenesená data nejsou místo na disku, ale „Data a úložiště" je jejich přirozený domov.
            SettingsGroup(Res.string.network_usage_group) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { CategoryIcon(Res.drawable.data_usage_24dp) },
                    headlineContent = { Text(stringResource(Res.string.network_usage_title)) },
                    supportingContent = state.networkUsage?.let { network ->
                        {
                            Text(
                                stringResource(
                                    Res.string.network_usage_summary,
                                    fileSizeText(network.receivedBytes),
                                    fileSizeText(network.sentBytes),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.clickable(onClick = onOpenNetworkUsage),
                )
            }

            if (autosavesToGallery) AutosaveGroup(viewModel = koinViewModel(), snackbarHostState = snackbarHostState)

            SettingsGroup(Res.string.storage_keep_media_title) {
                ChoiceRow(
                    headline = stringResource(Res.string.storage_keep_media),
                    options = KeepMedia.entries,
                    selected = keepMedia,
                    label = { stringResource(it.label) },
                    note = stringResource(Res.string.storage_keep_media_body),
                    onSelect = viewModel::setKeepMedia,
                    enabled = !state.clearing,
                )
                ChoiceRow(
                    headline = stringResource(Res.string.storage_cache_limit),
                    options = CacheLimit.entries,
                    selected = cacheLimit,
                    label = { it.label() },
                    note = stringResource(Res.string.storage_cache_limit_body),
                    onSelect = viewModel::setCacheLimit,
                    enabled = !state.clearing,
                )
            }

            // Chaty jen s podrobným přehledem a jen když v nich něco je.
            val chats = usage?.chats.orEmpty()
            if (chats.isNotEmpty()) {
                SettingsGroup(Res.string.storage_chats) {
                    chats.forEach { chat ->
                        ChatStorageRow(
                            chat = chat,
                            // Zbytek nejde smazat přesně: `optimizeStorage` s chatem 0 maže soubory
                            // MIMO chaty, ne „ostatní chaty".
                            onClick = if (chat.chatId == OTHER_CHATS || state.clearing) null else {
                                { clearChatId = chat.chatId }
                            },
                        )
                    }
                }
            }

            if (state.models.isNotEmpty()) {
                SettingsGroup(Res.string.storage_ai_models) {
                    state.models.forEach { stored ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { CategoryIcon(Res.drawable.auto_awesome_24dp) },
                            headlineContent = { Text(stored.model.displayName) },
                            supportingContent = {
                                Text(
                                    if (stored.complete) {
                                        fileSizeText(stored.model.sizeBytes)
                                    } else {
                                        stringResource(Res.string.storage_ai_model_partial)
                                    },
                                )
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { deleteModel = stored.model },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.common_delete))
                                }
                            },
                        )
                    }
                }
            }

            // Balíčky pro překlad v telefonu (`core/translation`). Angličtina je vestavěná a není tu.
            if (state.translationPacks.isNotEmpty()) {
                SettingsGroup(Res.string.storage_translation_packs) {
                    state.translationPacks.forEach { pack ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { CategoryIcon(Res.drawable.translate_24dp) },
                            headlineContent = { Text(localizedLanguageName(pack.language) ?: pack.language) },
                            supportingContent = pack.bytes?.let { bytes -> { Text(fileSizeText(bytes)) } },
                            trailingContent = {
                                IconButton(
                                    onClick = { deletePack = pack.language },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.common_delete))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (clearAll && usage != null) {
        ClearDialog(
            title = stringResource(Res.string.storage_clear_title),
            categories = usage.categories,
            onConfirm = { selected ->
                clearAll = false
                viewModel.clear(selected)
            },
            onDismiss = { clearAll = false },
        )
    }

    usage?.chats?.firstOrNull { it.chatId == clearChatId }?.let { chat ->
        ClearDialog(
            title = stringResource(Res.string.storage_clear_chat_title, chatTitle(chat.chatId, chat.title)),
            categories = chat.categories,
            onConfirm = { selected ->
                clearChatId = null
                viewModel.clear(selected, chat.chatId)
            },
            onDismiss = { clearChatId = null },
        )
    }

    deletePack?.let { language ->
        val name = localizedLanguageName(language) ?: language
        ConfirmDialog(
            title = stringResource(Res.string.storage_delete_pack_title, name),
            text = stringResource(Res.string.storage_delete_pack_body),
            confirmLabel = stringResource(Res.string.common_delete),
            onConfirm = {
                deletePack = null
                viewModel.deleteTranslationPack(language)
            },
            onDismiss = { deletePack = null },
            icon = Res.drawable.delete_24dp,
            destructive = true,
        )
    }

    deleteModel?.let { model ->
        ConfirmDialog(
            title = stringResource(Res.string.storage_delete_model_title, model.displayName),
            text = stringResource(Res.string.storage_delete_model_body),
            confirmLabel = stringResource(Res.string.common_delete),
            onConfirm = {
                deleteModel = null
                viewModel.deleteModel(model)
            },
            onDismiss = { deleteModel = null },
            icon = Res.drawable.delete_24dp,
            destructive = true,
        )
    }
}

@Composable
private fun StorageNotice.text(): String = when (this) {
    is StorageNotice.Freed -> if (bytes > 0) {
        stringResource(Res.string.storage_freed, fileSizeText(bytes))
    } else {
        stringResource(Res.string.storage_nothing)
    }
    is StorageNotice.ModelDeleted -> stringResource(Res.string.storage_model_deleted, name)
    is StorageNotice.PackDeleted -> stringResource(Res.string.storage_pack_deleted, localizedLanguageName(language) ?: language)
    is StorageNotice.Failed -> message.resolve()
}

/** Velké číslo nahoře: tohle je to, kvůli čemu se sem chodí. */
@Composable
private fun UsageHeader(filesBytes: Long) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            fileSizeText(filesBytes),
            style = MaterialTheme.typography.headlineMediumEmphasized.tabularNumbers(),
        )
        Text(
            stringResource(Res.string.storage_total_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Druh souborů s podílem na celku. Podíl je pruh, ne koláč s legendou: řádek nese název, velikost
 * i podíl naráz a nepotřebuje barvu pro každý druh.
 */
@Composable
private fun CategoryRow(part: CategoryUsage, total: Long) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { CategoryIcon(part.category.icon) },
        headlineContent = { Text(stringResource(part.category.label)) },
        supportingContent = {
            LinearProgressIndicator(
                progress = { if (total > 0) (part.bytes.toFloat() / total).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                // Bez tečky na konci dráhy: tady nejde o průběh, který někam dojde.
                drawStopIndicator = {},
            )
        },
        trailingContent = {
            Text(fileSizeText(part.bytes), style = MaterialTheme.typography.labelLarge.tabularNumbers())
        },
    )
}

/** Databáze (historie zpráv). Smazat jde jen odhlášením — řádek to říká, nic nenabízí. */
@Composable
private fun DatabaseRow(bytes: Long) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { CategoryIcon(Res.drawable.database_24dp) },
        headlineContent = { Text(stringResource(Res.string.storage_database)) },
        supportingContent = { Text(stringResource(Res.string.storage_database_summary)) },
        trailingContent = {
            Text(fileSizeText(bytes), style = MaterialTheme.typography.labelLarge.tabularNumbers())
        },
    )
}

@Composable
private fun ChatStorageRow(chat: ChatStorage, onClick: (() -> Unit)?) {
    val others = chat.chatId == OTHER_CHATS
    val title = if (others) stringResource(Res.string.storage_other_chats) else chatTitle(chat.chatId, chat.title)
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            if (others) {
                CategoryIcon(Res.drawable.folder_24dp)
            } else {
                Avatar(
                    chatId = chat.chatId,
                    title = title,
                    avatarFileId = chat.avatarFileId,
                    size = 40.dp,
                    isSavedMessages = isSavedMessages(chat.chatId),
                    shape = chat.kind.avatarShape(),
                )
            }
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // Co v chatu zabírá nejvíc — rozhoduje, jestli ho má smysl čistit.
        supportingContent = {
            Text(
                chat.categories.take(2).map { stringResource(it.category.label) }.joinToString(", "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Text(fileSizeText(chat.bytes), style = MaterialTheme.typography.labelLarge.tabularNumbers())
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** Ikona v tónovaném kruhu, jako u Zařízení — řádky se tím srovnají s avatary chatů. */
@Composable
internal fun CategoryIcon(icon: DrawableResource) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/**
 * Co smazat. Zaškrtávátka s velikostí u každého druhu a součet v tlačítku: uživatel vidí, kolik
 * místa získá, dřív než potvrdí. Předvybraná jsou média ([DEFAULT_CLEAR_CATEGORIES]).
 */
@Composable
private fun ClearDialog(
    title: String,
    categories: List<CategoryUsage>,
    onConfirm: (Set<StorageCategory>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(categories) {
        mutableStateOf(categories.map { it.category }.filter { it in DEFAULT_CLEAR_CATEGORIES }.toSet())
    }
    val selectedBytes = categories.filter { it.category in selected }.sumOf { it.bytes }
    ConfirmDialog(
        title = title,
        text = stringResource(Res.string.storage_clear_body),
        confirmLabel = stringResource(Res.string.storage_clear_confirm, fileSizeText(selectedBytes)),
        onConfirm = { onConfirm(selected) },
        onDismiss = onDismiss,
        icon = Res.drawable.delete_sweep_24dp,
        destructive = true,
        confirmEnabled = selected.isNotEmpty(),
    ) {
        categories.forEach { part ->
            DialogCheckRow(
                label = stringResource(part.category.label),
                checked = part.category in selected,
                trailing = { Text(fileSizeText(part.bytes), style = MaterialTheme.typography.labelLarge.tabularNumbers()) },
                onChange = { on -> selected = if (on) selected + part.category else selected - part.category },
            )
        }
    }
}

/** „1 GB" … „Bez stropu". */
@Composable
private fun CacheLimit.label(): String = bytes?.let { fileSizeText(it) } ?: stringResource(Res.string.storage_cache_limit_off)

private val KeepMedia.label: StringResource
    get() = when (this) {
        KeepMedia.ThreeDays -> Res.string.storage_keep_three_days
        KeepMedia.Week -> Res.string.storage_keep_week
        KeepMedia.Month -> Res.string.storage_keep_month
        KeepMedia.Forever -> Res.string.storage_keep_forever
    }

internal val StorageCategory.label: StringResource
    get() = when (this) {
        StorageCategory.Photos -> Res.string.storage_category_photos
        StorageCategory.Videos -> Res.string.storage_category_videos
        StorageCategory.VoiceMessages -> Res.string.storage_category_voice
        StorageCategory.Music -> Res.string.storage_category_music
        StorageCategory.Files -> Res.string.storage_category_files
        StorageCategory.Gifs -> Res.string.storage_category_gifs
        StorageCategory.Stickers -> Res.string.storage_category_stickers
        StorageCategory.Other -> Res.string.storage_category_other
    }

internal val StorageCategory.icon: DrawableResource
    get() = when (this) {
        StorageCategory.Photos -> Res.drawable.image_24dp
        StorageCategory.Videos -> Res.drawable.videocam_24dp
        StorageCategory.VoiceMessages -> Res.drawable.mic_24dp
        StorageCategory.Music -> Res.drawable.music_note_24dp
        StorageCategory.Files -> Res.drawable.description_24dp
        StorageCategory.Gifs -> Res.drawable.gif_box_24dp
        StorageCategory.Stickers -> Res.drawable.sticker_24dp
        StorageCategory.Other -> Res.drawable.folder_24dp
    }
