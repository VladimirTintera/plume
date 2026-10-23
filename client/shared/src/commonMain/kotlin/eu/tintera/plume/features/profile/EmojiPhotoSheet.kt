package eu.tintera.plume.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.CustomEmojiItem
import eu.tintera.plume.core.telegram.EMOJI_PHOTO_BACKGROUNDS
import eu.tintera.plume.core.telegram.EmojiPhotoBackground
import eu.tintera.plume.core.telegram.ProfilePhotoFile
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.ui.CustomEmojiImage
import eu.tintera.plume.core.ui.GroupEmptyRow
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.emoji_photo_background
import eu.tintera.plume.resources.emoji_photo_failed
import eu.tintera.plume.resources.emoji_photo_set
import eu.tintera.plume.resources.emoji_photo_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Profilová fotka z vlastního emoji: emoji z nabídky Telegramu na barevném přechodu, náhled
 * nahoře v tvaru avataru ([previewShape]). Obrázek vyrobí server; sheet vrátí jen volbu
 * ([ProfilePhotoFile.Emoji]) a nahrání nechá obrazovce, která má i průběh nad avatarem.
 *
 * - **Nabídka emoji je jiná pro účet a pro chat** ([forChat]) — Telegram ji tak vede.
 * - **Jednobarevné emoji se kreslí bíle**, jako ho na fotce vykreslí server; barva textu
 *   z motivu by na sytém pozadí nebyla vidět.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EmojiPhotoSheet(
    forChat: Boolean,
    previewShape: Shape,
    onSet: (ProfilePhotoFile.Emoji) -> Unit,
    onDismiss: () -> Unit,
) {
    val stickers = koinInject<StickerRepository>()
    val emoji by produceState<Result<List<CustomEmojiItem>>?>(initialValue = null, stickers, forChat) {
        value = stickers.defaultPhotoEmoji(forChat)
    }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var backgroundIndex by rememberSaveable { mutableIntStateOf(0) }
    val background = EMOJI_PHOTO_BACKGROUNDS[backgroundIndex]
    val items = emoji?.getOrNull().orEmpty()
    val selected = items.firstOrNull { it.id == selectedId } ?: items.firstOrNull()

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
                selected?.let { item ->
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        CustomEmojiImage(id = item.id, fallback = item.emoji, modifier = Modifier.size(PREVIEW_EMOJI_SIZE))
                    }
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
            when {
                emoji == null -> LoadingIndicator()
                items.isEmpty() -> GroupEmptyRow(stringResource(Res.string.emoji_photo_failed))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(EMOJI_CELL),
                    modifier = Modifier.fillMaxWidth().heightIn(max = GRID_MAX_HEIGHT),
                ) {
                    items(items, key = { it.id }) { item ->
                        EmojiCell(item = item, selected = item.id == selected?.id, onClick = { selectedId = item.id })
                    }
                }
            }
            MediumButton(
                onClick = { selected?.let { onSet(ProfilePhotoFile.Emoji(it.id, background)) } },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.emoji_photo_set))
            }
        }
    }
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
private fun EmojiCell(item: CustomEmojiItem, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(EMOJI_CELL)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = item.emoji
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        CustomEmojiImage(id = item.id, fallback = item.emoji, modifier = Modifier.size(EMOJI_SIZE))
    }
}

/** Barvy pozadí jsou RGB bez alfy (viz [EmojiPhotoBackground]); kreslí se neprůhledně. */
private fun EmojiPhotoBackground.brush(): Brush =
    Brush.verticalGradient(listOf(Color(OPAQUE or top), Color(OPAQUE or bottom)))

private const val OPAQUE = 0xFF000000.toInt()
private val PREVIEW_SIZE = 120.dp
private val PREVIEW_EMOJI_SIZE = 80.dp
private val SWATCH_SIZE = 40.dp
private val SWATCH_RING = 2.dp
private val EMOJI_CELL = 52.dp
private val EMOJI_SIZE = 36.dp
private val GRID_MAX_HEIGHT = 300.dp
