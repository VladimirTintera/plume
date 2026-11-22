package eu.tintera.plume.features.stories

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import eu.tintera.plume.core.adaptive.isWindowHeightCompact
import eu.tintera.plume.resources.story_cover_title
import eu.tintera.plume.resources.story_cover_hint
import eu.tintera.plume.core.telegram.StoryPoster
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.story_edit_title
import eu.tintera.plume.resources.story_edit_save
import eu.tintera.plume.resources.story_post_as
import eu.tintera.plume.resources.story_post_as_me
import eu.tintera.plume.core.ui.VideoTrimLabels
import eu.tintera.plume.core.ui.VideoTrimBar
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSearch
import eu.tintera.plume.core.ui.PickerSelection
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
import eu.tintera.plume.resources.story_audience_close_friends
import eu.tintera.plume.resources.story_close_friends_pick
import eu.tintera.plume.resources.story_close_friends_title
import eu.tintera.plume.resources.story_video_preparing
import eu.tintera.plume.resources.story_video_trimmed
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import eu.tintera.plume.core.ui.SwitchListItem

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
    // Otevřený výběr lidí: pro „Vybraní lidé", nebo úprava seznamu blízkých přátel.
    var picking by rememberSaveable { mutableStateOf<AudienceChoice?>(null) }

    LaunchedEffect(state.posted) { if (state.posted) currentOnPosted() }
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        haptics.reject()
        snackbar.showSnackbar(error.resolveNow())
        viewModel.errorShown()
    }

    when (picking) {
        AudienceChoice.Selected -> {
            StoryAudiencePicker(
                title = stringResource(Res.string.story_select_people),
                initial = state.selected,
                contactsOnly = false,
                onDone = { picked ->
                    viewModel.setSelected(picked)
                    picking = null
                },
                onBack = { picking = null },
                modifier = modifier,
            )
            return
        }

        AudienceChoice.CloseFriends -> {
            StoryAudiencePicker(
                title = stringResource(Res.string.story_close_friends_title),
                initial = state.closeFriends.orEmpty(),
                // Telegram bere mezi blízké přátele jen kontakty.
                contactsOnly = true,
                onDone = { picked ->
                    viewModel.saveCloseFriends(picked)
                    picking = null
                },
                onBack = { picking = null },
                modifier = modifier,
            )
            return
        }

        else -> Unit
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                viewModel.edit?.coverOnly == true -> Res.string.story_cover_title
                                viewModel.edit != null -> Res.string.story_edit_title
                                else -> Res.string.story_compose_title
                            },
                        ),
                    )
                },
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
                            Text(stringResource(if (viewModel.edit != null) Res.string.story_edit_save else Res.string.story_post))
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
                    .verticalScroll(rememberRestoringScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StoryPreview(path = state.previewPath, fill = state.fill)
                // Úsek a obálka: tentýž pás jako u videa profilovky. Je bílý na tmavém, proto
                // na černé ploše jako plátno příběhu.
                viewModel.video?.let { control ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(ShapeDefaults.Large)
                            .background(Color.Black)
                            .padding(12.dp),
                    ) {
                        VideoTrimBar(control, Modifier.fillMaxWidth(), labels = VideoTrimLabels.StoryCover)
                    }
                }
                // Jen obálka: video už je zveřejněné, ořez ani popisek se tu nemění.
                if (viewModel.edit?.coverOnly == true) {
                    Text(
                        stringResource(Res.string.story_cover_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    return@Column
                }
                if (viewModel.isVideo && (state.trimmed || state.posting)) {
                    Text(
                        stringResource(if (state.posting) Res.string.story_video_preparing else Res.string.story_video_trimmed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    FilterChip(
                        selected = state.fill,
                        onClick = { viewModel.setFill(true) },
                        label = { Text(stringResource(Res.string.story_fill)) },
                        shapes = FilterChipDefaults.shapes(),
                    )
                    FilterChip(
                        selected = !state.fill,
                        onClick = { viewModel.setFill(false) },
                        label = { Text(stringResource(Res.string.story_fit)) },
                        shapes = FilterChipDefaults.shapes(),
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

                // Úprava mění jen obsah a popisek; viditelnost a ostatní volby má příběh svoje.
                if (viewModel.edit != null) return@Column

                if (state.posters.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.story_post_as),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp).semantics { heading() },
                    )
                    Column(Modifier.selectableGroup()) {
                        val choices = listOf<StoryPoster?>(null) + state.posters
                        choices.forEachIndexed { index, poster ->
                            ChoiceListItem(
                                label = poster?.title ?: stringResource(Res.string.story_post_as_me),
                                supporting = if (poster != null && poster == state.poster) state.posterDenied?.resolve() else null,
                                selected = state.poster == poster,
                                index = index,
                                count = choices.size,
                                onClick = { viewModel.setPoster(poster) },
                            )
                        }
                    }
                }

                // Za kanál viditelnost není: příběh vidí jeho odběratelé.
                if (state.poster == null) {
                    Text(
                        stringResource(Res.string.story_audience_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp).semantics { heading() },
                    )
                    Column(Modifier.selectableGroup()) {
                        val selectedSummary = if (state.selected.isEmpty()) {
                            stringResource(Res.string.story_select_people)
                        } else {
                            pluralStringResource(Res.plurals.story_audience_selected_count, state.selected.size, state.selected.size)
                        }
                        val friends = state.closeFriends
                        val friendsSummary = when {
                            friends == null -> null
                            friends.isEmpty() -> stringResource(Res.string.story_close_friends_pick)
                            else -> pluralStringResource(Res.plurals.story_audience_selected_count, friends.size, friends.size)
                        }
                        val options = listOf(
                            Triple(AudienceChoice.Everyone, stringResource(Res.string.story_audience_everyone), null),
                            Triple(AudienceChoice.Contacts, stringResource(Res.string.story_audience_contacts), null),
                            Triple(AudienceChoice.CloseFriends, stringResource(Res.string.story_audience_close_friends), friendsSummary),
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
                                    // Vybraní / blízcí přátelé bez nikoho rovnou k výběru lidí (volba
                                    // by jinak nic neznamenala); podruhé výběr otevřou k úpravě.
                                    val people = when (choice) {
                                        AudienceChoice.Selected -> state.selected
                                        AudienceChoice.CloseFriends -> friends.orEmpty()
                                        else -> null
                                    }
                                    val pick = people != null && (people.isEmpty() || state.audience == choice)
                                    if (pick) picking = choice else viewModel.setAudience(choice)
                                },
                            )
                        }
                    }
                }

                GroupedColumn {
                    SwitchListItem(
                        headline = stringResource(Res.string.story_keep_on_profile),
                        supporting = stringResource(Res.string.story_keep_on_profile_summary),
                        checked = state.keepOnProfile,
                        onCheckedChange = viewModel::setKeepOnProfile,
                    )
                    SwitchListItem(
                        headline = stringResource(Res.string.story_allow_screenshots),
                        checked = state.allowScreenshots,
                        onCheckedChange = viewModel::setAllowScreenshots,
                    )
                }
            }
        }
    }
}

/**
 * Náhled tak, jak ho uvidí ostatní: plátno 9 : 16, fotka vyplněná, nebo celá na černé. U videa
 * jeho první snímek; než je, čeká se na černém plátně.
 */
@Composable
private fun StoryPreview(path: String?, fill: Boolean) {
    // Na telefonu naležato (kolem 360 dp) by 360dp náhled zabral celé okno i s lištou a k volbám
    // pod ním by se rolovalo přes něj; menší je pořád dost velký na kontrolu ořezu.
    val height = if (isWindowHeightCompact()) PREVIEW_HEIGHT_COMPACT else PREVIEW_HEIGHT
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .height(height)
                .aspectRatio(STORY_PREVIEW_ASPECT)
                .clip(ShapeDefaults.Large)
                // Černá, ne role schématu: tak bude plátno vypadat v příběhu u všech.
                .background(Color.Black),
        ) {
            if (path != null) {
                AsyncImage(
                    model = localImage(path),
                    contentDescription = null,
                    contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Výběr lidí, kteří příběh uvidí — kontakty a soukromé chaty, jako členové nové skupiny. Pro
 * blízké přátele ([contactsOnly]) jen kontakty a potvrdit jde i prázdný výběr (vyprázdnění seznamu).
 */
@Composable
internal fun StoryAudiencePicker(
    title: String,
    initial: Set<Long>,
    contactsOnly: Boolean,
    onDone: (Set<Long>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewChatViewModel = koinViewModel(key = "story-audience"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(initial.toList()) }
    val contacts = PickerSection(key = "contacts", chats = groupMemberCandidates(state.contacts, state.savedMessagesChatId), title = stringResource(Res.string.new_chat_contacts))
    ChatPicker(
        title = title,
        search = PickerSearch(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(Res.string.new_chat_search),
        ),
        loading = state.loading && state.contacts.isEmpty(),
        sections = if (contactsOnly) {
            listOf(contacts)
        } else {
            listOf(
                contacts,
                PickerSection(key = "chats", chats = groupMemberCandidates(state.chatResults, state.savedMessagesChatId), title = stringResource(Res.string.chats_search_section_chats)),
                PickerSection(key = "global", chats = groupMemberCandidates(state.globalResults, state.savedMessagesChatId), title = stringResource(Res.string.chats_search_section_global)),
            )
        },
        enabled = true,
        onPick = { chatId -> selected = if (chatId in selected) selected - chatId else selected + chatId },
        selection = PickerSelection(
            selected = selected.toSet(),
            // Soukromý chat má id uživatele, takže vybrané chaty jsou rovnou uživatelé.
            onConfirm = { onDone(selected.toSet()) },
            confirmLabel = Res.string.story_audience_done,
            confirmIcon = Res.drawable.check_24dp,
            confirmWhenEmpty = contactsOnly,
        ),
        showPresence = true,
        onBack = onBack,
        modifier = modifier,
    )
}

private val PREVIEW_HEIGHT = 360.dp
private val PREVIEW_HEIGHT_COMPACT = 200.dp
private const val STORY_PREVIEW_ASPECT = 9f / 16f
private const val CAPTION_MAX_LINES = 4
