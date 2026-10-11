package eu.tintera.plume.features.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.CustomEmojiItem
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.TextFormat
import eu.tintera.plume.core.ui.MenuPopupProperties
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.previousClusterStart
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.backspace_24dp
import eu.tintera.plume.resources.custom_emoji_add_sets
import eu.tintera.plume.resources.conversation_input_message
import eu.tintera.plume.resources.emoji_delete
import eu.tintera.plume.resources.emoji_none_found
import eu.tintera.plume.resources.emoji_recent
import eu.tintera.plume.resources.emoji_recent_empty
import eu.tintera.plume.resources.emoji_search
import eu.tintera.plume.resources.schedule_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    draftFormats: List<FormatSpan> = emptyList(),
    /** Vložení vlastního emoji; `null` = sady vlastních emoji se nenabízejí. */
    onCustomEmoji: ((CustomEmojiItem) -> Unit)? = null,
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
        formats = draftFormats,
        onBackspace = { onDraftEdit(draft.deletingBackward()) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    StickerSearchField(
        text = searchText,
        placeholder = stringResource(Res.string.emoji_search),
        onTextChange = {
            searchText = it
            viewModel.searchEmoji(it, languageCodes)
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    if (searchText.isNotBlank()) {
        Box(
            Modifier.fillMaxWidth().height(STICKER_TAB_ROW_HEIGHT + EMOJI_GRID_HEIGHT + sheetBottomInset()),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = search) {
                EmojiSearchState.Idle, EmojiSearchState.Searching -> LoadingIndicator()
                is EmojiSearchState.Results ->
                    if (current.emojis.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.emoji_none_found),
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
    /*
     * Za skupinami obyčejných emoji jdou sady vlastních emoji a za nimi „+" s populárními sadami.
     * Jen tam, kde je uživatel smí použít ([onCustomEmoji]). Sady dorazí až po otevření, proto
     * počet stránek jako lambda; přibývají na konci, takže se stránkám před nimi nic neposune.
     */
    val customSets by viewModel.customEmojiSets.collectAsStateWithLifecycle()
    val customPages = if (onCustomEmoji != null) customSets.size + 1 else 0
    val firstCustomPage = EMOJI_GROUPS.size + 1
    // První stránka jsou nedávná; bez nich se otevře rovnou na první skupině, ne na prázdné stránce.
    val pagerState = rememberPagerState(
        initialPage = if (recent.isEmpty()) 1 else 0,
        pageCount = { EMOJI_GROUPS.size + 1 + customPages },
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
                contentDescription = stringResource(Res.string.emoji_recent),
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
                // Čtečce obrazovky ale samotné emoji nestačí, takže popisek nese název skupiny.
                val title = stringResource(group.title)
                Text(
                    group.icon,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .semantics { contentDescription = title },
                )
            }
        }
        if (onCustomEmoji != null) {
            customSets.forEachIndexed { setIndex, set ->
                val index = firstCustomPage + setIndex
                Tab(
                    selected = index == pagerState.currentPage,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                ) { CustomEmojiTabIcon(set) }
            }
            val addIndex = firstCustomPage + customSets.size
            Tab(
                selected = addIndex == pagerState.currentPage,
                onClick = { scope.launch { pagerState.animateScrollToPage(addIndex) } },
            ) {
                Icon(
                    painterResource(Res.drawable.add_24dp),
                    contentDescription = stringResource(Res.string.custom_emoji_add_sets),
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().height(EMOJI_GRID_HEIGHT + sheetBottomInset()),
    ) { page ->
        if (page >= firstCustomPage && onCustomEmoji != null) {
            val set = customSets.getOrNull(page - firstCustomPage)
            if (set != null) {
                CustomEmojiPage(set, viewModel, onPick = onCustomEmoji)
            } else {
                CustomEmojiTrendingPage(viewModel)
            }
        } else if (page > 0) {
            EmojiGrid(EMOJI_GROUPS[page - 1].emojis, cells)
        } else if (recent.isNotEmpty()) {
            EmojiGrid(recent, cells)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.emoji_recent_empty),
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
    formats: List<FormatSpan>,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Vlastní emoji se v náhledu kreslí obrázkem, jako ve zprávě (`appendWithCustomEmoji`).
    val customEmoji = formats.filter { it.format == TextFormat.CustomEmoji && it.customEmojiId != null && it.end <= draft.text.length }
    val shown = remember(draft.text, customEmoji) {
        var ids: Set<Long> = emptySet()
        val text = buildAnnotatedString {
            ids = appendWithCustomEmoji(
                draft.text,
                customEmoji.map { MessageEntity(it.start, it.end - it.start, MessageEntityType.CustomEmoji(it.customEmojiId!!)) },
            )
        }
        text to ids
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().height(DRAFT_PREVIEW_HEIGHT),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (draft.text.isEmpty()) {
                    AnnotatedString(stringResource(Res.string.conversation_input_message))
                } else {
                    shown.first
                },
                inlineContent = customEmojiInlineContent(shown.second),
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
            IconButton(
                onClick = onBackspace,
                enabled = draft.text.isNotEmpty(),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(painterResource(Res.drawable.backspace_24dp), contentDescription = stringResource(Res.string.emoji_delete))
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
        contentPadding = sheetListPadding(horizontal = 12.dp, vertical = 4.dp),
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
    val mixed = mixedSkinToneGrid(base)
    var choosing by remember { mutableStateOf(false) }
    val indicator = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        Modifier
            .size(EMOJI_CELL)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { cells.onPick(emoji) },
                // Haptiku u dlouhého stisku dělá `combinedClickable` samo — viz `Haptics`.
                onLongClick = variants?.let { { choosing = true } },
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
                grid = mixed,
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
 * Emoji se dvěma osobami ([grid] není `null`) dostanou místo řady mřížku 5 × 5 — řádek je odstín
 * první osoby, sloupec druhé, na úhlopříčce oba stejné. Pod výchozí podobou tak jde vybrat i pár
 * s různou pletí, jako na systémových klávesnicích.
 *
 * `Popup`, ne vlastní overlay: tlačítko zpět si obslouží sám (`focusable`), bez `BackHandler`,
 * který by v aplikaci zabil predictive back. Klávesnici hledání si ale nebere
 * ([MenuPopupProperties]), takže při výběru odstínu nad výsledky hledání nesjede. Umisťuje se
 * nad buňku a vodorovně se drží v okně — u krajního sloupce by jinak vyjela z obrazovky.
 */
@Composable
private fun SkinTonePopup(
    options: List<String>,
    grid: List<String>?,
    selected: String,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positionProvider = remember(margin) { AboveAnchorPositionProvider(margin) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = MenuPopupProperties,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
        ) {
            if (grid == null) {
                Row(Modifier.padding(4.dp)) {
                    options.forEach { option -> SkinToneOption(option, option == selected, onChoose) }
                }
            } else {
                Column(Modifier.padding(4.dp)) {
                    SkinToneOption(options.first(), options.first() == selected, onChoose)
                    grid.chunked(SKIN_TONE_COUNT).forEach { row ->
                        Row { row.forEach { option -> SkinToneOption(option, option == selected, onChoose) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinToneOption(option: String, selected: Boolean, onChoose: (String) -> Unit) {
    Box(
        Modifier
            .size(EMOJI_CELL)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable { onChoose(option) },
        contentAlignment = Alignment.Center,
    ) {
        Text(option, fontSize = EMOJI_FONT_SIZE)
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

/**
 * Všech 25 kombinací odstínů u emoji se dvěma osobami (👫, 🤝, 💑…), po řádcích: řádek je odstín
 * první osoby, sloupec druhé. `null` u emoji, kde se odstíny kombinovat nedají.
 */
internal fun mixedSkinToneGrid(emoji: String): List<String>? = MIXED_SKIN_TONES[emoji.withoutVariation()]

/** Základ emoji z katalogu: pro variantu s odstínem její výchozí podoba, jinak emoji samo. */
internal fun baseEmoji(emoji: String): String = BASE_BY_VARIANT[emoji] ?: emoji

private class SkinToneEntry(val base: String, val variants: List<String>)

private fun unpackSkinTones(packed: String): List<SkinToneEntry> =
    packed.split(';').map { entry ->
        val (base, variants) = entry.split(':', limit = 2)
        SkinToneEntry(base, variants.split(' '))
    }

private val SKIN_TONE_ENTRIES: List<SkinToneEntry> by lazy { unpackSkinTones(EMOJI_SKIN_TONES_PACKED) }

private val MIXED_SKIN_TONE_ENTRIES: List<SkinToneEntry> by lazy { unpackSkinTones(EMOJI_MIXED_SKIN_TONES_PACKED) }

private val MIXED_SKIN_TONES: Map<String, List<String>> by lazy {
    MIXED_SKIN_TONE_ENTRIES.associate { it.base.withoutVariation() to it.variants }
}

/** Všechny varianty s odstínem, i smíšené — podle nich se hledá základ a podoba z katalogu. */
private val ALL_SKIN_TONE_ENTRIES: List<SkinToneEntry> by lazy { SKIN_TONE_ENTRIES + MIXED_SKIN_TONE_ENTRIES }

private val SKIN_TONES: Map<String, List<String>> by lazy {
    SKIN_TONE_ENTRIES.associate { it.base.withoutVariation() to it.variants }
}

private val BASE_BY_VARIANT: Map<String, String> by lazy {
    ALL_SKIN_TONE_ENTRIES.flatMap { entry -> entry.variants.map { it to entry.base } }.toMap()
}

/** Varianta podle podoby bez U+FE0F — nedávná a výsledky hledání můžou přijít bez něj. */
private val VARIANT_BY_BARE: Map<String, String> by lazy {
    ALL_SKIN_TONE_ENTRIES.flatMap { it.variants }.associateBy { it.withoutVariation() }
}

private val CATALOG_BY_BARE: Map<String, String> by lazy {
    EMOJI_GROUPS.flatMap { it.emojis }.associateBy { it.withoutVariation() }
}

private fun String.withoutVariation(): String = replace("\uFE0F", "")

/** Mřížka emoji; náhled textu si z výšky mřížky samolepek ukousne svůj kus. */
private val DRAFT_PREVIEW_HEIGHT = 48.dp
private val EMOJI_GRID_HEIGHT = STICKER_PICKER_HEIGHT - DRAFT_PREVIEW_HEIGHT - 8.dp

private val EMOJI_CELL = 44.dp

/** Odstínů pleti je pět (U+1F3FB až U+1F3FF); mřížka dvou osob má tolik sloupců. */
private const val SKIN_TONE_COUNT = 5

/** Trojúhelník v rohu emoji s odstíny pleti. */
private val SKIN_TONE_INDICATOR = 5.dp
private val EMOJI_FONT_SIZE = 28.sp
