package eu.tintera.plume.features.conversation

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.ui.emojiClusterCount
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.stickers_preview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Emoji, ke kterému se nad polem nabídnou samolepky, nebo `null`.
 *
 * Jen když je v poli **jediné emoji a nic jiného** (mezery kolem nevadí) — jako u oficiálních
 * klientů. Emoji uprostřed věty je součást textu, ne pokus poslat samolepku.
 */
fun stickerSuggestionEmoji(text: String): String? =
    text.trim().takeIf { it.emojiClusterCount(max = 1) == 1 }

/** Samolepky k emoji; [emoji] je to, ke kterému patří — pomalá odpověď na starší se zahodí. */
data class StickerSuggestions(val emoji: String, val stickers: List<StickerItem>)

/**
 * Návrhy samolepek k emoji v poli. Samostatný ViewModel jako návrhy emoji ke slovu: obrazovka
 * podává emoji a on vrací samolepky z vlastních sad, nedávných a oblíbených.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class StickerSuggestionsViewModel(
    private val repository: StickerRepository,
) : ViewModel() {

    private val request = MutableStateFlow<String?>(null)

    val suggestions: StateFlow<StickerSuggestions?> = request
        .debounce { if (it == null) 0L else LOOKUP_DELAY_MS }
        .distinctUntilChanged()
        .mapLatest { emoji ->
            emoji ?: return@mapLatest null
            val found = repository.stickersForEmoji(emoji)
            StickerSuggestions(emoji, found).takeIf { found.isNotEmpty() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Emoji samotné v poli, nebo `null`, když se nemá nabízet nic. */
    fun lookup(emoji: String?) {
        request.value = emoji
    }
}

/**
 * Samolepky navržené k [emoji]; prázdné, když žádné nejsou, nebo ještě nedorazily. ViewModel je
 * klíčovaný chatem jako ostatní napovídání, logika zůstává mimo `ConversationScreen`.
 */
@Composable
internal fun rememberStickerSuggestions(emoji: String?, chatId: Long): List<StickerItem> {
    val viewModel: StickerSuggestionsViewModel = koinViewModel(key = "sticker-suggestions-$chatId")
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    LaunchedEffect(emoji) { viewModel.lookup(emoji) }
    return suggestions?.takeIf { it.emoji == emoji }?.stickers.orEmpty()
}

/**
 * Samolepky k emoji v poli, plovoucí nad vstupním polem ([FloatingSuggestion]) jako návrhy emoji
 * ke slovu. Ťuknutí samolepku pošle MÍSTO emoji — pole se vyprázdní. Kdo chtěl poslat emoji,
 * odešle ho jako vždycky; řádek mu nepřekáží, místo v rozvržení nezabírá.
 *
 * Samolepky hrají: je jich vidět pár a řádek je na obrazovce jediný, stejně jako mřížka výběru.
 *
 * Podržení otevře velký náhled jako ve výběru ([StickerPreviewDialog]) s Poslat a hvězdičkou
 * oblíbených — 64dp buňka je na rozeznání samolepky malá.
 */
@Composable
internal fun StickerSuggestionsPanel(
    stickers: List<StickerItem>,
    visible: Boolean,
    onPick: (StickerItem) -> Unit,
    /** Mléčné sklo nad seznamem: plocha je průhledná, barvu i rozostření nese sklo. */
    glass: Modifier = Modifier,
) {
    // Při odjezdu se kreslí poslední sada, ne prázdno. Stav, ne obyčejné pole — viz
    // `EmojiSuggestionsPanel`.
    val last = remember { mutableStateOf(stickers) }
    if (stickers.isNotEmpty()) last.value = stickers

    // Podržená samolepka ve velkém náhledu; rotaci nepřežije, je to chvilkové prohlédnutí.
    var preview by remember { mutableStateOf<StickerItem?>(null) }
    preview?.let { sticker ->
        SuggestedStickerPreview(sticker = sticker, onPick = onPick, onClose = { preview = null })
    }

    FloatingSuggestion(
        visible = visible && stickers.isNotEmpty(),
        shape = RoundedCornerShape(PANEL_CORNER),
        glass = glass,
        // Vyroste z rohu u pole, kde je emoji napsané.
        origin = TransformOrigin(0f, 1f),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(last.value, key = { it.id }) { sticker ->
                StickerPreview(
                    sticker = sticker.preview,
                    playing = true,
                    modifier = Modifier
                        .size(STICKER_SUGGESTION_CELL)
                        .clip(RoundedCornerShape(CELL_CORNER))
                        // Podržení si haptiku dělá `combinedClickable` samo — viz `Haptics`.
                        .combinedClickable(
                            onClick = { onPick(sticker) },
                            onLongClickLabel = stringResource(Res.string.stickers_preview),
                            onLongClick = { preview = sticker },
                        )
                        .padding(4.dp)
                        .fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Náhled navržené samolepky. Oblíbené si čte sám a jen po dobu náhledu: panel návrhů je plovoucí
 * vrstva nad polem bez vlastního ViewModelu na oblíbené a `ConversationScreen`, odkud se volá,
 * je v dexu na hraně (viz CLAUDE.md) — nic dalšího se do něj tahat nemá.
 */
@Composable
private fun SuggestedStickerPreview(
    sticker: StickerItem,
    onPick: (StickerItem) -> Unit,
    onClose: () -> Unit,
) {
    val repository = koinInject<StickerRepository>()
    val favorites by remember(repository) {
        repository.favoriteStickers().map { list -> list.mapTo(HashSet()) { it.sendFileId } }
    }.collectAsStateWithLifecycle(emptySet())
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val favorite = sticker.sendFileId in favorites

    StickerPreviewDialog(
        sticker = sticker,
        favorite = favorite,
        pickLabel = stringResource(Res.string.conversation_send),
        // Volby odeslání (bez zvuku, naplánovat) má výběr samolepek; tady by se musely protáhnout
        // přes `ConversationScreen`.
        sendMenu = null,
        onPick = {
            onClose()
            onPick(sticker)
        },
        onPickWith = {},
        onToggleFavorite = {
            haptics.toggle(on = !favorite)
            // Stav se lokálně nepřepisuje, potvrzení přijde `favoriteStickersUpdates`.
            scope.launch { repository.setFavorite(sticker.sendFileId, !favorite) }
        },
        recent = false,
        onRemoveRecent = {},
        onDismiss = onClose,
    )
}

private val STICKER_SUGGESTION_CELL = 64.dp

private val CELL_CORNER = 16.dp

private val PANEL_CORNER = 28.dp

/** Emoji se píše jedním ťuknutím, pauza jen slije rychlé přepsání za jiné. */
private const val LOOKUP_DELAY_MS = 150L

private const val STOP_TIMEOUT_MS = 5_000L
