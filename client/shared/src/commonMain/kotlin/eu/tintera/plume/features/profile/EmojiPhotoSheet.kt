package eu.tintera.plume.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.CustomEmojiItem
import eu.tintera.plume.core.telegram.EMOJI_PHOTO_BACKGROUNDS
import eu.tintera.plume.core.telegram.EmojiPhotoBackground
import eu.tintera.plume.core.telegram.ProfilePhotoFile
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSetKind
import eu.tintera.plume.core.ui.CustomEmojiImage
import eu.tintera.plume.core.ui.GroupEmptyRow
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.features.conversation.StickerPreview
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.emoji_photo_background
import eu.tintera.plume.resources.emoji_photo_failed
import eu.tintera.plume.resources.emoji_photo_no_stickers
import eu.tintera.plume.resources.emoji_photo_set
import eu.tintera.plume.resources.emoji_photo_source_emoji
import eu.tintera.plume.resources.emoji_photo_source_stickers
import eu.tintera.plume.resources.emoji_photo_suggested
import eu.tintera.plume.resources.emoji_photo_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject

/** Co je na fotce vybrané: vlastní emoji, nebo samolepka ze sady. */
private sealed interface PhotoPick {
    data class Emoji(val item: CustomEmojiItem) : PhotoPick
    data class Sticker(val item: StickerItem) : PhotoPick
}

/**
 * Profilová fotka z vlastního emoji nebo samolepky na barevném pozadí, náhled nahoře v tvaru
 * avataru ([previewShape]). Obrázek vyrobí server; sheet vrátí jen volbu
 * ([ProfilePhotoFile.Emoji] / [ProfilePhotoFile.Sticker]) a nahrání nechá obrazovce, která má
 * i průběh nad avatarem.
 *
 * - **Emoji: nabídka od Telegramu a nainstalované sady vlastních emoji.** Nabídka je jiná pro
 *   účet a pro chat ([forChat]) a jde použít bez Premium.
 * - **Samolepky: nainstalované sady.** Hledat ve všech sadách sheet neumí — od toho je výběr
 *   samolepek v konverzaci, odkud se sada přidá.
 * - **Jednobarevné emoji se kreslí bíle**, jako ho na fotce vykreslí server; barva textu
 *   z motivu by na sytém pozadí nebyla vidět.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EmojiPhotoSheet(
    forChat: Boolean,
    previewShape: Shape,
    onSet: (ProfilePhotoFile) -> Unit,
    onDismiss: () -> Unit,
) {
    val stickers = koinInject<StickerRepository>()
    var stickerMode by rememberSaveable { mutableStateOf(false) }
    // `null` u emoji = nabídka od Telegramu; u samolepek první nainstalovaná sada.
    var emojiSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var stickerSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var backgroundIndex by rememberSaveable { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf<PhotoPick?>(null) }
    val background = EMOJI_PHOTO_BACKGROUNDS[backgroundIndex]

    val emojiSets by remember(stickers) { stickers.customEmojiSets() }.collectAsState(emptyList())
    val stickerSets by remember(stickers) { stickers.installedSets(StickerSetKind.Stickers) }.collectAsState(emptyList())
    val emoji by produceState<Result<List<CustomEmojiItem>>?>(initialValue = null, stickers, forChat, emojiSetId) {
        value = null
        value = emojiSetId?.let { stickers.customEmojiSet(it) } ?: stickers.defaultPhotoEmoji(forChat)
    }
    val shownStickerSet = stickerSetId ?: stickerSets.firstOrNull()?.id
    val stickerDetail by remember(stickers, shownStickerSet) {
        shownStickerSet?.let { stickers.stickerSetDetail(it) } ?: flowOf(null)
    }.collectAsState(null)

    val emojiItems = emoji?.getOrNull().orEmpty()
    // Než si uživatel vybere, ukazuje náhled první emoji z nabídky — prázdný kruh by nic neřekl.
    val shown = pick ?: emojiItems.firstOrNull()?.takeIf { !stickerMode }?.let { PhotoPick.Emoji(it) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetTitle(stringResource(Res.string.emoji_photo_title), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            Box(
                Modifier.size(PREVIEW_SIZE).clip(previewShape).background(background.brush()),
                contentAlignment = Alignment.Center,
            ) {
                when (val current = shown) {
                    is PhotoPick.Emoji -> CompositionLocalProvider(LocalContentColor provides Color.White) {
                        CustomEmojiImage(id = current.item.id, fallback = current.item.emoji, modifier = Modifier.size(PREVIEW_EMOJI_SIZE))
                    }
                    is PhotoPick.Sticker -> Box(Modifier.size(PREVIEW_EMOJI_SIZE)) {
                        StickerPreview(current.item.preview, playing = true, modifier = Modifier.fillMaxSize())
                    }
                    null -> Unit
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(EMOJI_PHOTO_BACKGROUNDS.size) { index ->
                    BackgroundSwatch(
                        background = EMOJI_PHOTO_BACKGROUNDS[index],
                        selected = index == backgroundIndex,
                        label = stringResource(Res.string.emoji_photo_background, index + 1),
                        onClick = { backgroundIndex = index },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                listOf(false, true).forEachIndexed { index, stickersSource ->
                    val checked = stickerMode == stickersSource
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { if (!checked) stickerMode = stickersSource },
                        shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (checked) {
                            Icon(
                                painterResource(Res.drawable.check_24dp),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp).size(ButtonDefaults.IconSize),
                            )
                        }
                        Text(stringResource(if (stickersSource) Res.string.emoji_photo_source_stickers else Res.string.emoji_photo_source_emoji))
                    }
                }
            }
            if (!stickerMode) {
                SetChips(
                    entries = listOf<Pair<Long?, String>>(null to stringResource(Res.string.emoji_photo_suggested)) + emojiSets.map { it.id to it.title },
                    selected = emojiSetId,
                    onSelect = { emojiSetId = it },
                )
                when {
                    emoji == null -> GridBox { LoadingIndicator() }
                    emojiItems.isEmpty() -> GroupEmptyRow(stringResource(Res.string.emoji_photo_failed))
                    else -> LazyVerticalGrid(columns = GridCells.Adaptive(CELL), modifier = Modifier.fillMaxWidth().height(GRID_HEIGHT)) {
                        items(emojiItems, key = { it.id }) { item ->
                            PickCell(
                                description = item.emoji,
                                selected = (shown as? PhotoPick.Emoji)?.item?.id == item.id,
                                onClick = { pick = PhotoPick.Emoji(item) },
                            ) {
                                CustomEmojiImage(id = item.id, fallback = item.emoji, modifier = Modifier.size(ITEM_SIZE))
                            }
                        }
                    }
                }
            } else if (stickerSets.isEmpty()) {
                GroupEmptyRow(stringResource(Res.string.emoji_photo_no_stickers))
            } else {
                SetChips(
                    entries = stickerSets.map { it.id to it.title },
                    selected = shownStickerSet,
                    onSelect = { id -> id?.let { stickerSetId = it } },
                )
                val items = stickerDetail?.stickers
                if (items == null) {
                    GridBox { LoadingIndicator() }
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(CELL), modifier = Modifier.fillMaxWidth().height(GRID_HEIGHT)) {
                        items(items, key = { it.id }) { item ->
                            PickCell(
                                description = item.preview.emoji,
                                selected = (shown as? PhotoPick.Sticker)?.item?.id == item.id,
                                onClick = { pick = PhotoPick.Sticker(item) },
                            ) {
                                Box(Modifier.size(ITEM_SIZE)) { StickerPreview(item.preview, playing = false, modifier = Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }
            }
            MediumButton(
                onClick = { shown?.let { onSet(it.toPhoto(background)) } },
                enabled = shown != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.emoji_photo_set))
            }
        }
    }
}

private fun PhotoPick.toPhoto(background: EmojiPhotoBackground): ProfilePhotoFile = when (this) {
    is PhotoPick.Emoji -> ProfilePhotoFile.Emoji(item.id, background)
    is PhotoPick.Sticker -> ProfilePhotoFile.Sticker(
        stickerSetId = item.preview.setId,
        stickerId = item.id,
        background = background,
        preview = item.preview,
    )
}

/** Sady jako čipy v řadě; název sady bývá dlouhý, proto strop šířky. */
@Composable
private fun SetChips(entries: List<Pair<Long?, String>>, selected: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(entries, key = { it.first ?: SUGGESTED_KEY }) { (id, title) ->
            FilterChip(
                selected = id == selected,
                onClick = { onSelect(id) },
                label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = CHIP_MAX)) },
                shapes = FilterChipDefaults.shapes(),
            )
        }
    }
}

/** Místo mřížky, dokud se načítá — stejná výška, ať tlačítko pod ní neposkakuje. */
@Composable
private fun GridBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(GRID_HEIGHT), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun BackgroundSwatch(background: EmojiPhotoBackground, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(background.brush())
            .then(if (selected) Modifier.border(SWATCH_RING, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        // Fajfka nese výběr i bez rámečku — na světlém pozadí by tenký rámeček splynul.
        if (selected) Icon(painterResource(Res.drawable.check_24dp), contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun PickCell(description: String, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(CELL)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Náhled fotky z emoji nebo samolepky mimo sheet — zakládání skupiny ukazuje volbu, než ji po
 * založení pošle na server. Fotka ze souboru sem nepatří (`null` nic nekreslí).
 */
@Composable
internal fun EmojiPhotoPreview(photo: ProfilePhotoFile, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val (background, content) = when (photo) {
        is ProfilePhotoFile.Emoji -> photo.background to @Composable {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                CustomEmojiImage(id = photo.customEmojiId, fallback = "", modifier = Modifier.fillMaxSize())
            }
        }
        is ProfilePhotoFile.Sticker -> photo.background to @Composable {
            photo.preview?.let { StickerPreview(it, playing = true, modifier = Modifier.fillMaxSize()) }
        }
        else -> return
    }
    Box(modifier.size(size).clip(shape).background(background.brush()), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * PREVIEW_EMOJI_SIZE.value / PREVIEW_SIZE.value)) { content() }
    }
}

/** Barvy pozadí jsou RGB bez alfy (viz [EmojiPhotoBackground]); kreslí se neprůhledně. */
private fun EmojiPhotoBackground.brush(): Brush =
    Brush.verticalGradient(listOf(Color(OPAQUE or top), Color(OPAQUE or bottom)))

private const val OPAQUE = 0xFF000000.toInt()
private const val SUGGESTED_KEY = -1L
private val PREVIEW_SIZE = 120.dp
private val PREVIEW_EMOJI_SIZE = 80.dp
private val SWATCH_SIZE = 40.dp
private val SWATCH_RING = 2.dp
private val CELL = 52.dp
private val ITEM_SIZE = 36.dp
private val GRID_HEIGHT = 260.dp
private val CHIP_MAX = 160.dp
