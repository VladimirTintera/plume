package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
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
    var searchText by rememberSaveable { mutableStateOf("") }
    val insert: (String) -> Unit = { emoji -> onDraftEdit(draft.inserting(emoji)) }

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
                        EmojiGrid(current.emojis, onPick = insert)
                    }
            }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { EMOJI_GROUPS.size })

    PrimaryScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        edgePadding = 8.dp,
        containerColor = Color.Transparent,
        modifier = Modifier.height(STICKER_TAB_ROW_HEIGHT),
    ) {
        EMOJI_GROUPS.forEachIndexed { index, group ->
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
        EmojiGrid(EMOJI_GROUPS[page].emojis, onPick = insert)
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

@Composable
private fun EmojiGrid(emojis: List<String>, onPick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(EMOJI_CELL),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(emojis, key = { it }) { emoji ->
            Box(
                Modifier
                    .size(EMOJI_CELL)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPick(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                // Mimo typografickou škálu, stejně jako emoji v čipu reakce: nosná je celá kresba.
                Text(emoji, fontSize = EMOJI_FONT_SIZE)
            }
        }
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
internal fun catalogEmoji(emoji: String): String? = CATALOG_BY_BARE[emoji.withoutVariation()]

private val CATALOG_BY_BARE: Map<String, String> by lazy {
    EMOJI_GROUPS.flatMap { it.emojis }.associateBy { it.withoutVariation() }
}

private fun String.withoutVariation(): String = replace("\uFE0F", "")

/** Mřížka emoji; náhled textu si z výšky mřížky samolepek ukousne svůj kus. */
private val DRAFT_PREVIEW_HEIGHT = 48.dp
private val EMOJI_GRID_HEIGHT = STICKER_PICKER_HEIGHT - DRAFT_PREVIEW_HEIGHT - 8.dp

private val EMOJI_CELL = 44.dp
private val EMOJI_FONT_SIZE = 28.sp
