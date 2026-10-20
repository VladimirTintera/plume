package eu.tintera.plume.features.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.ChoiceListItem
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.core.ui.SaveActionButton
import eu.tintera.plume.core.ui.localImage
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.chats.NewChatViewModel
import eu.tintera.plume.features.chats.groupMemberCandidates
import eu.tintera.plume.features.settings.SwitchRow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.chats_search_section_global
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.new_chat_contacts
import eu.tintera.plume.resources.new_chat_search
import eu.tintera.plume.resources.story_audience_contacts
import eu.tintera.plume.resources.story_audience_done
import eu.tintera.plume.resources.story_audience_everyone
import eu.tintera.plume.resources.story_audience_selected
import eu.tintera.plume.resources.story_audience_selected_count
import eu.tintera.plume.resources.story_audience_title
import eu.tintera.plume.resources.story_caption
import eu.tintera.plume.resources.story_compose_title
import eu.tintera.plume.resources.story_fill
import eu.tintera.plume.resources.story_fit
import eu.tintera.plume.resources.story_keep_on_profile
import eu.tintera.plume.resources.story_keep_on_profile_summary
import eu.tintera.plume.resources.story_allow_screenshots
import eu.tintera.plume.resources.story_post
import eu.tintera.plume.resources.story_select_people
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Nový vlastní příběh: náhled na plátně 9 : 16 (vyplnit, nebo celá fotka), popisek, kdo ho
 * uvidí a dvě volby. Zveřejnit je akce v liště jako Přidat u nového kontaktu; obrazovka se
 * zavře, jakmile TDLib zveřejnění přijme, a jak nahrávání dopadlo, ohlásí seznam chatů.
 */
@Composable
fun StoryComposerScreen(
    viewModel: StoryComposerViewModel,
    onBack: () -> Unit,
    onPosted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val currentOnPosted by rememberUpdatedState(onPosted)
    var picking by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.posted) { if (state.posted) currentOnPosted() }
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        haptics.reject()
        snackbar.showSnackbar(error.resolveNow())
        viewModel.errorShown()
    }

    if (picking) {
        StoryAudiencePicker(
            initial = state.selected,
            onDone = { picked ->
                viewModel.setSelected(picked)
                picking = false
            },
            onBack = { picking = false },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.story_compose_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.posting, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    if (state.posting) {
                        LoadingIndicator(Modifier.padding(end = 12.dp).size(40.dp))
                    } else {
                        SaveActionButton(onClick = viewModel::post, enabled = true) {
                            Text(stringResource(Res.string.story_post))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = ContentWidth.Form)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StoryPreview(path = viewModel.photoPath, fill = state.fill)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    FilterChip(
                        selected = state.fill,
                        onClick = { viewModel.setFill(true) },
                        label = { Text(stringResource(Res.string.story_fill)) },
                    )
                    FilterChip(
                        selected = !state.fill,
                        onClick = { viewModel.setFill(false) },
                        label = { Text(stringResource(Res.string.story_fit)) },
                    )
                }

                FormTextField(
                    value = state.caption,
                    onValueChange = viewModel::setCaption,
                    label = { Text(stringResource(Res.string.story_caption)) },
                    enabled = !state.posting,
                    maxLines = CAPTION_MAX_LINES,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    stringResource(Res.string.story_audience_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                Column(Modifier.selectableGroup()) {
                    val selectedSummary = if (state.selected.isEmpty()) {
                        stringResource(Res.string.story_select_people)
                    } else {
                        pluralStringResource(Res.plurals.story_audience_selected_count, state.selected.size, state.selected.size)
                    }
                    val options = listOf(
                        Triple(AudienceChoice.Everyone, stringResource(Res.string.story_audience_everyone), null),
                        Triple(AudienceChoice.Contacts, stringResource(Res.string.story_audience_contacts), null),
                        Triple(AudienceChoice.Selected, stringResource(Res.string.story_audience_selected), selectedSummary),
                    )
                    options.forEachIndexed { index, (choice, label, supporting) ->
                        ChoiceListItem(
                            label = label,
                            supporting = supporting,
                            selected = state.audience == choice,
                            index = index,
                            count = options.size,
                            onClick = {
                                // Vybraní bez výběru rovnou k výběru lidí (volba by jinak nic
                                // neznamenala); vybraní podruhé výběr otevřou k úpravě.
                                val pick = choice == AudienceChoice.Selected &&
                                    (state.selected.isEmpty() || state.audience == AudienceChoice.Selected)
                                if (pick) picking = true else viewModel.setAudience(choice)
                            },
                        )
                    }
                }

                GroupedColumn {
                    SwitchRow(
                        title = Res.string.story_keep_on_profile,
                        summary = Res.string.story_keep_on_profile_summary,
                        checked = state.keepOnProfile,
                        onChange = viewModel::setKeepOnProfile,
                    )
                    SwitchRow(
                        title = Res.string.story_allow_screenshots,
                        checked = state.allowScreenshots,
                        onChange = viewModel::setAllowScreenshots,
                    )
                }
            }
        }
    }
}

/** Náhled tak, jak ho uvidí ostatní: plátno 9 : 16, fotka vyplněná, nebo celá na černé. */
@Composable
private fun StoryPreview(path: String, fill: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .height(PREVIEW_HEIGHT)
                .aspectRatio(STORY_PREVIEW_ASPECT)
                .clip(ShapeDefaults.Large)
                // Černá, ne role schématu: tak bude plátno vypadat v příběhu u všech.
                .background(Color.Black),
        ) {
            AsyncImage(
                model = localImage(path),
                contentDescription = null,
                contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Výběr lidí, kteří příběh uvidí — kontakty a soukromé chaty, jako členové nové skupiny. */
@Composable
private fun StoryAudiencePicker(
    initial: Set<Long>,
    onDone: (Set<Long>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewChatViewModel = koinViewModel(key = "story-audience"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(initial.toList()) }
    ChatPicker(
        title = stringResource(Res.string.story_select_people),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(Res.string.new_chat_search),
        loading = state.loading && state.contacts.isEmpty(),
        sections = listOf(
            PickerSection(key = "contacts", chats = groupMemberCandidates(state.contacts, state.savedMessagesChatId), title = stringResource(Res.string.new_chat_contacts)),
            PickerSection(key = "chats", chats = groupMemberCandidates(state.chatResults, state.savedMessagesChatId), title = stringResource(Res.string.chats_search_section_chats)),
            PickerSection(key = "global", chats = groupMemberCandidates(state.globalResults, state.savedMessagesChatId), title = stringResource(Res.string.chats_search_section_global)),
        ),
        enabled = true,
        onPick = { chatId -> selected = if (chatId in selected) selected - chatId else selected + chatId },
        selected = selected.toSet(),
        // Soukromý chat má id uživatele, takže vybrané chaty jsou rovnou uživatelé.
        onConfirm = { onDone(selected.toSet()) },
        confirmLabel = Res.string.story_audience_done,
        confirmIcon = Res.drawable.check_24dp,
        showPresence = true,
        onBack = onBack,
        modifier = modifier,
    )
}

private val PREVIEW_HEIGHT = 360.dp
private const val STORY_PREVIEW_ASPECT = 9f / 16f
private const val CAPTION_MAX_LINES = 4
