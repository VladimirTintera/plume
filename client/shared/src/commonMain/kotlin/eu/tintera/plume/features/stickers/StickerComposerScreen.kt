package eu.tintera.plume.features.stickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.telegram.OwnedStickerSet
import eu.tintera.plume.core.ui.ChoiceListItem
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.IconSwitch
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.core.ui.MediumButtonHeight
import eu.tintera.plume.core.ui.MediumButtonIcon
import eu.tintera.plume.core.ui.localImage
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.conversation.PhotoMarkupEditor
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.sticker_done
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.resources.sticker_add
import eu.tintera.plume.resources.sticker_added_title
import eu.tintera.plume.resources.sticker_edit_photo
import eu.tintera.plume.resources.sticker_emoji
import eu.tintera.plume.resources.sticker_emoji_hint
import eu.tintera.plume.resources.sticker_emoji_other
import eu.tintera.plume.resources.sticker_new_title
import eu.tintera.plume.resources.sticker_outline
import eu.tintera.plume.resources.sticker_outline_hint
import eu.tintera.plume.resources.sticker_remove_background
import eu.tintera.plume.resources.sticker_remove_background_hint
import eu.tintera.plume.resources.sticker_retry
import eu.tintera.plume.resources.sticker_set
import eu.tintera.plume.resources.sticker_set_count
import eu.tintera.plume.resources.sticker_set_full
import eu.tintera.plume.resources.sticker_set_new
import eu.tintera.plume.resources.sticker_set_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Samolepka z fotky: náhled na šachovnici (průhlednost je vidět), úprava fotky editorem
 * příloh, odstranění pozadí, emoji a sada. Po přidání místo formuláře potvrzení a „Hotovo".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickerComposerScreen(viewModel: StickerComposerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    // Snackbar mimo tělo efektu: vynulování chyby mění klíč a zrušilo by ho.
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.errorShown()
        snackbarScope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }

    if (editing) {
        PhotoMarkupEditor(
            path = viewModel.photoPath,
            width = viewModel.photoWidth,
            height = viewModel.photoHeight,
            initial = state.markup,
            onCancel = { editing = false },
            onDone = { markup ->
                editing = false
                viewModel.applyMarkup(markup)
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.sticker_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
                val modifier = Modifier.widthIn(max = ContentWidth.Form).fillMaxWidth()
                if (state.done != null) {
                    MediumButton(onClick = onBack, modifier = modifier) { Text(stringResource(Res.string.sticker_done)) }
                } else {
                    MediumButton(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = modifier.height(MediumButtonHeight),
                    ) {
                        if (state.saving) {
                            LoadingIndicator(Modifier.size(24.dp))
                        } else {
                            MediumButtonIcon(Res.drawable.sticker_24dp)
                            Text(stringResource(Res.string.sticker_add))
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val done = state.done
            if (done != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = ContentWidth.Form)) {
                    StickerPreview(state, Modifier.padding(top = 24.dp).size(PREVIEW_DONE_SIZE))
                    IntroEmptyState(
                        icon = Res.drawable.sticker_24dp,
                        title = stringResource(Res.string.sticker_added_title),
                        text = done.resolve(),
                    )
                }
            } else {
                StickerForm(state, viewModel, onEdit = { editing = true }, modifier = Modifier.widthIn(max = ContentWidth.Form))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickerForm(
    state: StickerComposerState,
    viewModel: StickerComposerViewModel,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "preview") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StickerPreview(state, Modifier.size(PREVIEW_SIZE))
                if (state.prepareError != null && !state.preparing) {
                    TextButton(onClick = viewModel::retry, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(Res.string.sticker_retry))
                    }
                }
                if (state.canEditPhoto) {
                    FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.edit_24dp), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(Res.string.sticker_edit_photo))
                    }
                }
            }
        }
        if (state.canRemoveBackground) {
            item(key = "background") {
                GroupedColumn(Modifier.fillMaxWidth()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.sticker_remove_background)) },
                        supportingContent = { Text(stringResource(Res.string.sticker_remove_background_hint)) },
                        trailingContent = { IconSwitch(checked = state.removeBackground, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = state.removeBackground,
                            role = Role.Switch,
                            onValueChange = viewModel::setRemoveBackground,
                        ),
                    )
                    // Obrys sedí jen kolem vyříznutého objektu; kolem celé fotky by byl jen rámeček.
                    if (state.removeBackground) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(Res.string.sticker_outline)) },
                            supportingContent = { Text(stringResource(Res.string.sticker_outline_hint)) },
                            trailingContent = { IconSwitch(checked = state.outline, onCheckedChange = null) },
                            modifier = Modifier.toggleable(
                                value = state.outline,
                                role = Role.Switch,
                                onValueChange = viewModel::setOutline,
                            ),
                        )
                    }
                }
            }
        }
        item(key = "emoji") {
            Section(stringResource(Res.string.sticker_emoji)) {
                Text(
                    stringResource(Res.string.sticker_emoji_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUGGESTED_EMOJI.forEach { emoji ->
                        FilterChip(
                            selected = state.emoji == emoji,
                            onClick = { viewModel.setEmoji(emoji) },
                            label = { Text(emoji, style = MaterialTheme.typography.titleMedium) },
                        )
                    }
                }
                FormTextField(
                    value = state.emoji.takeUnless { it in SUGGESTED_EMOJI }.orEmpty(),
                    onValueChange = viewModel::setEmoji,
                    singleLine = true,
                    label = { Text(stringResource(Res.string.sticker_emoji_other)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "set") {
            Section(stringResource(Res.string.sticker_set)) {
                val sets = state.sets
                if (sets == null) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                } else {
                    val count = sets.size + 1
                    Column {
                        sets.forEachIndexed { index, set ->
                            ChoiceListItem(
                                label = set.title,
                                selected = (state.target as? StickerTarget.Existing)?.set?.id == set.id,
                                index = index,
                                count = count,
                                onClick = { viewModel.selectTarget(StickerTarget.Existing(set)) },
                                supporting = setSupporting(set),
                            )
                        }
                        ChoiceListItem(
                            label = stringResource(Res.string.sticker_set_new),
                            selected = state.target == StickerTarget.NewSet,
                            index = sets.size,
                            count = count,
                            onClick = { viewModel.selectTarget(StickerTarget.NewSet) },
                        )
                    }
                    if (state.target == StickerTarget.NewSet) {
                        FormTextField(
                            value = state.newSetTitle,
                            onValueChange = viewModel::setNewSetTitle,
                            singleLine = true,
                            label = { Text(stringResource(Res.string.sticker_set_title)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun setSupporting(set: OwnedStickerSet): String =
    if (set.isFull) stringResource(Res.string.sticker_set_full) else pluralStringResource(Res.plurals.sticker_set_count, set.size, set.size)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
        content()
    }
}

/** Náhled na šachovnici, ať je vidět, kde je samolepka průhledná. */
@Composable
private fun StickerPreview(state: StickerComposerState, modifier: Modifier = Modifier) {
    val light = MaterialTheme.colorScheme.surfaceContainerHigh
    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.aspectRatio(1f).clip(ShapeDefaults.ExtraLarge), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = CHECKER_CELL.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = 0f
                var column = 0
                while (x < size.width) {
                    drawRect(if ((row + column) % 2 == 0) light else dark, Offset(x, y), Size(cell, cell))
                    x += cell
                    column++
                }
                y += cell
                row++
            }
        }
        val preview = state.preview
        if (preview != null) {
            AsyncImage(
                model = localImage(preview.path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
        val error = state.prepareError
        when {
            state.preparing -> LoadingIndicator()
            error != null -> Text(
                error.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/** Emoji, která se ke samolepce hodí nejčastěji; jiné jde napsat do pole pod nimi. */
private val SUGGESTED_EMOJI = listOf("😀", "😂", "😍", "😎", "🥳", "😢", "😡", "👍", "👋", "❤️", "🔥", "🎉", "🤔", "😴", "🙏", "👌")

private val PREVIEW_SIZE = 240.dp
private val PREVIEW_DONE_SIZE = 160.dp
private val CHECKER_CELL = 12.dp
