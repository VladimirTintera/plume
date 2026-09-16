package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.previousClusterStart
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.backspace_24dp
import eu.tintera.plume.resources.schedule_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * Výběr emoji — část sheetu výběru vedle samolepek.
 *
 * Na rozdíl od samolepky se emoji **neposílá, vkládá se do rozepsaného textu** na pozici kurzoru.
 * Vstupní pole je ale pod sheetem, takže panel nahoře ukazuje, jak text vypadá, a má vlastní
 * mazání. Sheet se po vložení nezavírá — emoji se většinou píšou po několika.
 *
 * Výška je součet stejných kusů jako u samolepek (náhled textu si ukousne z mřížky), aby přepnutí
 * mezi částmi sheet nenatáhlo ani nesmrštilo.
 */
@Composable
internal fun EmojiPanel(
    viewModel: StickerPickerViewModel,
    draft: TextFieldValue,
    onDraftEdit: (TextFieldValue) -> Unit,
    languageCodes: List<String>,
) {
    val search by viewModel.emojiSearch.collectAsStateWithLifecycle()
    val skinTones by viewModel.skinTones.collectAsStateWithLifecycle()
    var searchText by rememberSaveable { mutableStateOf("") }
    val insert: (String) -> Unit = { emoji ->
        onDraftEdit(draft.inserting(emoji))
        viewModel.recordEmoji(emoji)
    }

    /*
     * Nedávná emoji ZMRAŽENÁ na stav při otevření výběru. Každé ťuknutí emoji přesune na začátek;
     * kdyby se seznam měnil živě, mřížka by se přeskládala pod prstem a druhé ťuknutí by trefilo
     * jiné emoji. Nové pořadí se ukáže při dalším otevření — stejně to dělá Telegram.
     */
    val recent = remember { viewModel.recentEmojis.value }

    /*
     * Emoji se kreslí a vkládá ve zvolené podobě: kdo si jednou vybral 👍🏽, dostane ho příště
     * obyčejným ťuknutím. Podle základu, takže to platí v kategoriích, výsledcích hledání i nedávných.
     */
    val cells = EmojiCells(
        display = { emoji -> skinTones[baseEmoji(emoji)] ?: emoji },
        onPick = insert,
        onChooseTone = { base, variant ->
            viewModel.setSkinTone(base, variant)
            insert(variant)
        },
    )

    DraftPreview(
        draft = draft,
        onBackspace = { onDraftEdit(draft.deletingBackward()) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    StickerSearchField(
        text = searchText,
        placeholder = "Hledat emoji",
        onTextChange = {
            searchText = it
            viewModel.searchEmoji(it, languageCodes)
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    if (searchText.isNotBlank()) {
        Box(
            Modifier.fillMaxWidth().height(STICKER_TAB_ROW_HEIGHT + EMOJI_GRID_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = search) {
                EmojiSearchState.Idle, EmojiSearchState.Searching -> LoadingIndicator()
                is EmojiSearchState.Results ->
                    if (current.emojis.isEmpty()) {
                        Text(
                            text = "Žádné emoji jsme nenašli.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    } else {
                        EmojiGrid(current.emojis, cells)
                    }
            }
        }
        return
    }

    val scope = rememberCoroutineScope()
    // První stránka jsou nedávná; bez nich se otevře rovnou na první skupině, ne na prázdné stránce.
    val pagerState = rememberPagerState(
        initialPage = if (recent.isEmpty()) 1 else 0,
        pageCount = { EMOJI_GROUPS.size + 1 },
    )

    PrimaryScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        edgePadding = 8.dp,
        containerColor = Color.Transparent,
        modifier = Modifier.height(STICKER_TAB_ROW_HEIGHT),
    ) {
        Tab(
            selected = pagerState.currentPage == 0,
            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
        ) {
            Icon(
                painterResource(Res.drawable.schedule_24dp),
                contentDescription = "Nedávné",
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
        EMOJI_GROUPS.forEachIndexed { groupIndex, group ->
            val index = groupIndex + 1
            Tab(
                selected = index == pagerState.currentPage,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
            ) {
                // Emoji jako ikona záložky: obrázková ikona pro „Jídlo" by řekla míň než 🍔.
                Text(group.icon, fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().height(EMOJI_GRID_HEIGHT),
    ) { page ->
        if (page > 0) {
            EmojiGrid(EMOJI_GROUPS[page - 1].emojis, cells)
        } else if (recent.isNotEmpty()) {
            EmojiGrid(recent, cells)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Tady se objeví emoji, která vybereš.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

/**
 * Náhled rozepsaného textu s mazáním.
 *
 * Konec textu, ne začátek (`StartEllipsis`): vkládá se většinou na konec a právě tam má být vidět,
 * co přibylo.
 */
@Composable
private fun DraftPreview(
    draft: TextFieldValue,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().height(DRAFT_PREVIEW_HEIGHT),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = draft.text.ifEmpty { "Zpráva" },
                color = if (draft.text.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
            IconButton(onClick = onBackspace, enabled = draft.text.isNotEmpty()) {
                Icon(painterResource(Res.drawable.backspace_24dp), contentDescription = "Smazat")
            }
        }
    }
}

/** Co buňky mřížky emoji potřebují od panelu. */
private class EmojiCells(
    /** Podoba, ve které se emoji kreslí a vkládá — se zvoleným odstínem pleti. */
    val display: (String) -> String,
    val onPick: (String) -> Unit,
    /** Volba odstínu z nabídky po podržení: základ a vybraná varianta. */
    val onChooseTone: (base: String, variant: String) -> Unit,
)

@Composable
private fun EmojiGrid(emojis: List<String>, cells: EmojiCells) {
    // Po převodu na zvolené odstíny se v nedávných můžou sejít dvě podoby téhož emoji; klíče mřížky
    // musí být jedinečné.
    val shown = emojis.map(cells.display).distinct()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(EMOJI_CELL),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shown, key = { it }) { emoji -> EmojiCell(emoji, cells) }
    }
}

/**
 * Jedno emoji: ťuknutí vloží, podržení nabídne odstíny pleti (jen u emoji, která je mají).
 *
 * Emoji s odstíny má v rohu malý trojúhelník, stejně jako na systémových klávesnicích — jinak by
 * se o podržení nikdo nedozvěděl.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun EmojiCell(emoji: String, cells: EmojiCells) {
    val base = baseEmoji(emoji)
    val variants = skinToneVariants(base)
    val haptics = LocalHapticFeedback.current
    var choosing by remember { mutableStateOf(false) }
    val indicator = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        Modifier
            .size(EMOJI_CELL)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { cells.onPick(emoji) },
                onLongClick = variants?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        choosing = true
                    }
                },
            )
            .drawBehind {
                if (variants == null) return@drawBehind
                val size = SKIN_TONE_INDICATOR.toPx()
                val inset = 3.dp.toPx()
                val path = Path().apply {
                    moveTo(this@drawBehind.size.width - inset, this@drawBehind.size.height - inset - size)
                    lineTo(this@drawBehind.size.width - inset, this@drawBehind.size.height - inset)
                    lineTo(this@drawBehind.size.width - inset - size, this@drawBehind.size.height - inset)
                    close()
                }
                drawPath(path, indicator)
            },
        contentAlignment = Alignment.Center,
    ) {
        // Mimo typografickou škálu, stejně jako emoji v čipu reakce: nosná je celá kresba.
        Text(emoji, fontSize = EMOJI_FONT_SIZE)

        if (choosing && variants != null) {
            SkinTonePopup(
                options = listOf(base) + variants,
                selected = emoji,
                onChoose = { variant ->
                    choosing = false
                    cells.onChooseTone(base, variant)
                },
                onDismiss = { choosing = false },
            )
        }
    }
}

/**
 * Nabídka odstínů nad podrženým emoji: výchozí podoba a pět odstínů.
 *
 * `Popup`, ne vlastní overlay: tlačítko zpět si obslouží sám (`focusable`), bez `BackHandler`,
 * který by v aplikaci zabil predictive back. Umisťuje se nad buňku a vodorovně se drží v okně —
 * u krajního sloupce by jinak vyjela z obrazovky.
 */
@Composable
private fun SkinTonePopup(
    options: List<String>,
    selected: String,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positionProvider = remember(margin) { AboveAnchorPositionProvider(margin) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(4.dp)) {
                options.forEach { option ->
                    Box(
                        Modifier
                            .size(EMOJI_CELL)
                            .clip(CircleShape)
                            .background(
                                if (option == selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            )
                            .clickable { onChoose(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(option, fontSize = EMOJI_FONT_SIZE)
                    }
                }
            }
        }
    }
}

/** Nad kotvu, vodorovně na její střed, ale uvnitř okna. Když se nahoru nevejde, pod ni. */
private class AboveAnchorPositionProvider(private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centered = anchorBounds.center.x - popupContentSize.width / 2
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = centered.coerceIn(margin, maxX)
        val above = anchorBounds.top - popupContentSize.height - margin
        val y = if (above >= 0) above else anchorBounds.bottom + margin
        return IntOffset(x, y)
    }
}

/** Vloží text na místo kurzoru (nebo místo označeného úseku) a kurzor postaví za něj. */
internal fun TextFieldValue.inserting(inserted: String): TextFieldValue {
    val start = selection.min
    val newText = text.replaceRange(start, selection.max, inserted)
    return TextFieldValue(newText, selection = TextRange(start + inserted.length))
}

/**
 * Smaže znak před kurzorem — celé emoji, ne jeho kus (viz [previousClusterStart]). Označený úsek
 * se smaže celý, jako klávesou Backspace.
 */
internal fun TextFieldValue.deletingBackward(): TextFieldValue {
    if (!selection.collapsed) {
        return TextFieldValue(text.removeRange(selection.min, selection.max), selection = TextRange(selection.min))
    }
    val end = selection.start
    if (end == 0) return this
    val start = text.previousClusterStart(end)
    return TextFieldValue(text.removeRange(start, end), selection = TextRange(start))
}

/**
 * Emoji v podobě z katalogu, nebo `null`, když v katalogu není.
 *
 * TDLib vrací emoji někdy bez výběru varianty (U+FE0F); vložené bez něj by se na části systémů
 * nakreslilo jako černobílý textový symbol. Proto se porovnává bez něj a vrací se podoba z katalogu.
 */
internal fun catalogEmoji(emoji: String): String? =
    emoji.withoutVariation().let { bare -> CATALOG_BY_BARE[bare] ?: VARIANT_BY_BARE[bare] }

/** Varianty odstínu pleti podle základu (bez U+FE0F); `null`, když emoji odstíny nemá. */
internal fun skinToneVariants(emoji: String): List<String>? = SKIN_TONES[emoji.withoutVariation()]

/** Základ emoji z katalogu: pro variantu s odstínem její výchozí podoba, jinak emoji samo. */
internal fun baseEmoji(emoji: String): String = BASE_BY_VARIANT[emoji] ?: emoji

private class SkinToneEntry(val base: String, val variants: List<String>)

private val SKIN_TONE_ENTRIES: List<SkinToneEntry> by lazy {
    EMOJI_SKIN_TONES_PACKED.split(';').map { entry ->
        val (base, variants) = entry.split(':', limit = 2)
        SkinToneEntry(base, variants.split(' '))
    }
}

private val SKIN_TONES: Map<String, List<String>> by lazy {
    SKIN_TONE_ENTRIES.associate { it.base.withoutVariation() to it.variants }
}

private val BASE_BY_VARIANT: Map<String, String> by lazy {
    SKIN_TONE_ENTRIES.flatMap { entry -> entry.variants.map { it to entry.base } }.toMap()
}

/** Varianta podle podoby bez U+FE0F — nedávná a výsledky hledání můžou přijít bez něj. */
private val VARIANT_BY_BARE: Map<String, String> by lazy {
    SKIN_TONE_ENTRIES.flatMap { it.variants }.associateBy { it.withoutVariation() }
}

private val CATALOG_BY_BARE: Map<String, String> by lazy {
    EMOJI_GROUPS.flatMap { it.emojis }.associateBy { it.withoutVariation() }
}

private fun String.withoutVariation(): String = replace("\uFE0F", "")

/** Mřížka emoji; náhled textu si z výšky mřížky samolepek ukousne svůj kus. */
private val DRAFT_PREVIEW_HEIGHT = 48.dp
private val EMOJI_GRID_HEIGHT = STICKER_PICKER_HEIGHT - DRAFT_PREVIEW_HEIGHT - 8.dp

private val EMOJI_CELL = 44.dp

/** Trojúhelník v rohu emoji s odstíny pleti. */
private val SKIN_TONE_INDICATOR = 5.dp
private val EMOJI_FONT_SIZE = 28.sp
