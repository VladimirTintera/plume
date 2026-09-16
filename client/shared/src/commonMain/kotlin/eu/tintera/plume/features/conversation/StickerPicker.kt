package eu.tintera.plume.features.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.ui.AnimatedSticker
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.star_24dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

/** Záložka výběru samolepek. */
sealed interface StickerTab {
    data object Recent : StickerTab
    data object Favorites : StickerTab
    data class Set(val summary: StickerSetSummary) : StickerTab
}

data class StickerPickerState(
    val tabs: List<StickerTab> = listOf(StickerTab.Recent, StickerTab.Favorites),
    val selected: StickerTab = StickerTab.Recent,
    /** Samolepky vybrané záložky; `null`, dokud se obsah sady načítá. */
    val stickers: List<StickerItem>? = null,
)

/**
 * Stav výběru samolepek.
 *
 * Vlastní ViewModel, ne kus `ConversationViewModel`: výběr je pro všechny chaty stejný a konverzace
 * z něj potřebuje jen výsledek — ťuknutou samolepku.
 *
 * Obsah sady se načítá **až při otevření její záložky** a drží se po dobu života ViewModelu.
 * Sad bývá desítky a načíst je všechny kvůli otevření výběru by bylo zbytečné.
 */
class StickerPickerViewModel(
    private val repository: StickerRepository,
) : ViewModel() {

    private val selected = MutableStateFlow<StickerTab>(StickerTab.Recent)
    private val setContents = MutableStateFlow<Map<Long, List<StickerItem>>>(emptyMap())

    val state: StateFlow<StickerPickerState> = combine(
        repository.installedSets(),
        repository.recentStickers(),
        repository.favoriteStickers(),
        selected,
        setContents,
    ) { sets, recent, favorites, tab, contents ->
        StickerPickerState(
            tabs = listOf(StickerTab.Recent, StickerTab.Favorites) + sets.map { StickerTab.Set(it) },
            selected = tab,
            stickers = when (tab) {
                StickerTab.Recent -> recent
                StickerTab.Favorites -> favorites
                is StickerTab.Set -> contents[tab.summary.id]
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StickerPickerState())

    fun select(tab: StickerTab) {
        selected.value = tab
        if (tab !is StickerTab.Set) return

        val setId = tab.summary.id
        if (setId in setContents.value) return

        viewModelScope.launch {
            // Neúspěch se ukáže jako prázdná sada — výběr kvůli jedné sadě nezavíráme chybou.
            val stickers = repository.stickerSet(setId).getOrDefault(emptyList())
            setContents.update { it + (setId to stickers) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Výběr samolepek v bottom sheetu.
 *
 * Sheet, ne panel místo klávesnice: výměna klávesnice za panel bez poskočení vstupního pole je
 * v Compose nejkřehčí část celé věci, a sheet ji nepotřebuje. Klávesnice se před otevřením zavře.
 *
 * Mřížka má **pevnou výšku**, aby se sheet při přepínání záložek nenatahoval a nesmršťoval podle
 * počtu samolepek. Samolepky v ní **nehrají** (viz [StickerPreview]).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun StickerPickerSheet(
    onPick: (StickerItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: StickerPickerViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    /*
     * Samolepka rozhýbaná podržením. Vždy nejvýš JEDNA — hraje, dokud se nepodrží jiná.
     *
     * Jedna kvůli výkonu (mřížka je právě proto statická) i kvůli čitelnosti: kdo si prohlíží
     * sadu, porovnává samolepky po jedné. Přežívá přepnutí záložky; po návratu hraje dál.
     */
    var activeStickerId by rememberSaveable { mutableStateOf<Long?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val selectedIndex = state.tabs.indexOf(state.selected).coerceAtLeast(0)

        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 8.dp,
            containerColor = Color.Transparent,
        ) {
            state.tabs.forEach { tab ->
                Tab(
                    selected = tab == state.selected,
                    onClick = { viewModel.select(tab) },
                    icon = { StickerTabIcon(tab) },
                )
            }
        }

        Box(
            Modifier.fillMaxWidth().height(STICKER_PICKER_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            val stickers = state.stickers
            when {
                stickers == null -> LoadingIndicator()

                stickers.isEmpty() -> Text(
                    text = when (state.selected) {
                        StickerTab.Recent -> "Tady se objeví samolepky, které pošleš."
                        StickerTab.Favorites -> "Zatím tu nejsou žádné oblíbené samolepky."
                        is StickerTab.Set -> "Sada je prázdná, nebo se ji nepodařilo načíst."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(STICKER_CELL),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(stickers, key = { it.id }) { sticker ->
                        val active = sticker.id == activeStickerId

                        Box(
                            Modifier
                                .size(STICKER_CELL)
                                .clip(RoundedCornerShape(12.dp))
                                // Podbarvení nese potvrzení podržení i u statické samolepky,
                                // která se rozhýbat nemá jak.
                                .background(
                                    if (active) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                                )
                                .combinedClickable(
                                    onClick = {
                                        // Nejdřív sheet schovat, pak poslat: zmizení bez animace by
                                        // vypadalo jako pád, a zpráva se tak objeví až nad zavřeným sheetem.
                                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                                            onPick(sticker)
                                        }
                                    },
                                    // Podržení samolepku neposílá — jen ji rozhýbe. Po puštění
                                    // `combinedClickable` klik nevyvolá, takže nic neodejde omylem.
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        activeStickerId = sticker.id
                                    },
                                )
                                .padding(6.dp),
                        ) {
                            StickerPreview(sticker.preview, playing = active, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerTabIcon(tab: StickerTab) {
    when (tab) {
        StickerTab.Recent -> Icon(painterResource(Res.drawable.schedule_24dp), contentDescription = "Nedávné")
        StickerTab.Favorites -> Icon(painterResource(Res.drawable.star_24dp), contentDescription = "Oblíbené")
        is StickerTab.Set -> {
            val cover = tab.summary.cover
            if (cover != null) {
                StickerPreview(cover, playing = false, modifier = Modifier.size(28.dp))
            } else {
                // Sada bez obálky: aspoň první písmeno názvu, ať se záložky dají rozeznat.
                Text(tab.summary.title.take(1), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * Samolepka ve výběru.
 *
 * Běžně **nehraje** a kreslí se nejlevnější dostupnou podobou: rastrový náhled, pak statická `.webp`,
 * a teprve když není ani jedno, první snímek `.tgs` (ten znamená rozbalit a rozparsovat animaci).
 * Desítky animací v jedné mřížce by stály víc, než kolik přinesou.
 *
 * [playing] ji rozhýbe — jen podrženou. Do načtení animace drží místo tentýž statický náhled, takže
 * se samolepka rozběhne na místě, bez probliknutí. Statická `.webp` nemá co přehrát a zůstane stát.
 */
@Composable
private fun StickerPreview(
    sticker: MessageMedia.Sticker,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val still = sticker.thumbnailFileId ?: sticker.fileId
    val description = sticker.emoji.ifBlank { "Samolepka" }
    val animation = sticker.animationFileId

    val stillImage: @Composable () -> Unit = {
        if (still != null) {
            RemoteImage(
                fileId = still,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                background = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmojiFallback(sticker.emoji) }
        }
    }

    when {
        animation != null && playing -> AnimatedSticker(
            fileId = animation,
            contentDescription = description,
            isPlaying = true,
            modifier = modifier,
            placeholder = stillImage,
        )

        // Bez náhledu je první snímek animace pořád lepší než emoji.
        animation != null && still == null -> AnimatedSticker(
            fileId = animation,
            contentDescription = description,
            isPlaying = false,
            modifier = modifier,
            placeholder = stillImage,
        )

        else -> Box(modifier) { stillImage() }
    }
}

@Composable
private fun EmojiFallback(emoji: String) {
    Text(emoji, style = MaterialTheme.typography.headlineMedium)
}

/** Výška mřížky. Kolem tří řádků — dost na výběr, konverzace nad sheetem zůstává vidět. */
private val STICKER_PICKER_HEIGHT = 320.dp

/** Buňka mřížky. Na telefonu pět sloupců, jako v Telegramu. */
private val STICKER_CELL = 76.dp
