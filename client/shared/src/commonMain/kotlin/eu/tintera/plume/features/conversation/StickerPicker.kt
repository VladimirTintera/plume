package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.ControlledSearchPill
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.animation.core.animateFloatAsState
import eu.tintera.plume.resources.check_24dp
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ShapeDefaults
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.foundation.lazy.rememberLazyListState
import eu.tintera.plume.core.ui.wheelScrollsHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.drawBehind
import eu.tintera.plume.core.ui.animateMorph
import eu.tintera.plume.core.ui.ShapeMorphs
import eu.tintera.plume.core.ui.MorphShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.CustomEmojiItem
import eu.tintera.plume.core.telegram.CustomEmojiSet
import eu.tintera.plume.core.telegram.CustomEmojiSetSample
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.EmojiRepository
import eu.tintera.plume.core.telegram.GroupStickerSet
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StickerCategory
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerQuery
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSearchResult
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.telegram.StickerSetSample
import eu.tintera.plume.core.ui.AnimatedSticker
import eu.tintera.plume.core.ui.CustomEmojiImage
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.StickerPlayback
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.VideoSticker
import eu.tintera.plume.core.ui.emojiClusterCount
import eu.tintera.plume.core.ui.tinted
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_sticker
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.stickers_preview
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.star_24dp
import eu.tintera.plume.resources.conversation_send_when_online
import eu.tintera.plume.resources.conversation_send_schedule
import eu.tintera.plume.resources.conversation_send_silent
import eu.tintera.plume.resources.online_prediction_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.schedule_send_24dp
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.resources.stickers_add
import eu.tintera.plume.resources.stickers_added
import eu.tintera.plume.resources.stickers_clear_search
import eu.tintera.plume.resources.stickers_favorites_empty
import eu.tintera.plume.resources.stickers_none_found
import eu.tintera.plume.resources.stickers_recent_clear
import eu.tintera.plume.resources.stickers_recent_clear_confirm
import eu.tintera.plume.resources.stickers_recent_clear_text
import eu.tintera.plume.resources.stickers_recent_clear_title
import eu.tintera.plume.resources.stickers_recent_empty
import eu.tintera.plume.resources.stickers_search
import eu.tintera.plume.resources.stickers_section_installed
import eu.tintera.plume.resources.stickers_section_public
import eu.tintera.plume.resources.stickers_section_sets
import eu.tintera.plume.resources.stickers_sets_show_all
import eu.tintera.plume.resources.stickers_set_empty
import eu.tintera.plume.resources.stickers_tab_emoji
import eu.tintera.plume.resources.stickers_tab_favorites
import eu.tintera.plume.resources.stickers_tab_group
import eu.tintera.plume.resources.stickers_tab_gifs
import eu.tintera.plume.resources.stickers_tab_recent
import eu.tintera.plume.resources.stickers_tab_stickers
import eu.tintera.plume.resources.stickers_tab_trending
import eu.tintera.plume.resources.stickers_trending_failed
import eu.tintera.plume.resources.trending_up_24dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Záložka výběru samolepek. */
sealed interface StickerTab {
    data object Recent : StickerTab
    data object Favorites : StickerTab
    /** Populární sady k přidání. Nemá mřížku samolepek, ale seznam sad. */
    data object Trending : StickerTab
    data class Set(val summary: StickerSetSummary) : StickerTab
    /**
     * Sada skupiny, ve které se píše. Jen v té skupině a i nenainstalovaná — správci ji
     * nastavili pro všechny členy.
     */
    data class Group(val group: GroupStickerSet) : StickerTab
}

data class StickerPickerState(
    val tabs: List<StickerTab> = FIXED_TABS,
    /** `null`, dokud se nenačetly. */
    val recent: List<StickerItem>? = null,
    val favorites: List<StickerItem>? = null,
    /** Obsah už otevřených sad podle id. Chybějící sada se teprve načítá. */
    val sets: Map<Long, List<StickerItem>> = emptyMap(),
    /** Seznam záložek je úplný — dorazily i nainstalované sady. */
    val loaded: Boolean = false,
    /**
     * Id nainstalovaných sad. Ze záložek se vyčíst nedá: sada skupiny, kterou má uživatel
     * i nainstalovanou, má jen záložku skupiny (viz [stickerTabs]).
     */
    val installedSetIds: Set<Long> = emptySet(),
) {
    /** Samolepky záložky; `null`, dokud se její obsah načítá. */
    fun stickersOf(tab: StickerTab): List<StickerItem>? = when (tab) {
        StickerTab.Recent -> recent
        StickerTab.Favorites -> favorites
        // Trendy mají vlastní stránku se seznamem sad, mřížku samolepek nemají.
        StickerTab.Trending -> null
        is StickerTab.Set -> sets[tab.summary.id]
        is StickerTab.Group -> sets[tab.group.set.id]
    }
}

/**
 * Záložky, které jsou vždycky, před nainstalovanými sadami. Trendy hned za oblíbenými: na konci
 * za desítkami sad by je nikdo nenašel.
 */
private val FIXED_TABS = stickerTabs(emptyList(), group = null)

/**
 * Stav výběru samolepek.
 *
 * Vlastní ViewModel, ne kus `ConversationViewModel`: výběr je pro všechny chaty stejný a konverzace
 * z něj potřebuje jen výsledek — ťuknutou samolepku.
 *
 * Drží obsah **všech** záložek, ne jen vybrané: při swipu mezi záložkami je vidět i sousední stránka.
 * Obsah sady se ale načítá **až když se její stránka poprvé ukáže** ([load]) a drží se po dobu
 * života ViewModelu. Sad bývá desítky a načíst je všechny kvůli otevření výběru by bylo zbytečné.
 */
class StickerPickerViewModel(
    private val repository: StickerRepository,
    private val emojiRepository: EmojiRepository,
    private val settings: AppSettings,
) : ViewModel() {

    /**
     * Nedávno vybraná emoji, obyčejná i vlastní, v pořadí použití ([parseRecentEmoji]: obyčejná
     * jen z katalogu, uložené mohlo zůstat z jiné verze aplikace). Vlastní emoji ukazuje výběr
     * jen tam, kde je uživatel smí použít.
     */
    val recentEmojis: StateFlow<List<EmojiEntry>> = settings.recentEmojis
        .map { list -> list.mapNotNull(::parseRecentEmoji) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settings.recentEmojis.value.mapNotNull(::parseRecentEmoji),
        )

    fun recordEmoji(emoji: String) = settings.recordRecentEmoji(emoji)

    /** Vlastní emoji do nedávných, mezi obyčejná (viz [recentKey]). */
    fun recordCustomEmoji(item: CustomEmojiItem) {
        if (item.emoji.isNotEmpty()) settings.recordRecentEmoji(item.recentKey())
    }

    /** Zvolené odstíny pleti: základ → varianta. */
    val skinTones: StateFlow<Map<String, String>> = settings.emojiSkinTones

    /** Zapamatuje odstín pro emoji; výchozí podoba (základ) zvolený odstín zruší. */
    fun setSkinTone(base: String, variant: String) = settings.setEmojiSkinTone(base, variant.takeIf { it != base })

    private val setContents = MutableStateFlow<Map<Long, List<StickerItem>>>(emptyMap())

    /** Sada skupiny, ve které se píše; `loaded` až po dotazu (i když žádná není). */
    private val group = MutableStateFlow(GroupSetLookup())

    /** Chat, pro který se sada skupiny zjišťuje — ať se při každém otevření výběru neptá znovu. */
    private var groupChatId: Long? = null

    /**
     * Zjistí sadu skupiny [chatId]. Dotaz bývá lokální (plné údaje skupiny už načetla
     * konverzace), ale může jít na server; výběr na něj čeká kvůli pořadí záložek (viz
     * [StickerPickerState.loaded]), proto má strop. Co nestihne, to se v tomhle otevření neukáže.
     */
    fun loadGroupSet(chatId: Long) {
        if (groupChatId == chatId) return
        groupChatId = chatId
        group.value = GroupSetLookup()
        viewModelScope.launch {
            val found = withTimeoutOrNull(GROUP_SET_TIMEOUT_MS) { repository.groupStickerSet(chatId) }
            if (groupChatId == chatId) group.value = GroupSetLookup(loaded = true, set = found)
        }
    }

    /** Sady, jejichž načítání už běží — aby dvě stránky nenačítaly totéž dvakrát. */
    private val loading = mutableSetOf<Long>()

    private val searchRequest = MutableStateFlow<StickerSearchRequest>(StickerSearchRequest.Typed("", emptyList()))

    /**
     * Výsledek hledání.
     *
     * Hledá se až po krátké pauze v psaní, ne na každý znak — veřejné hledání jde na server.
     * `transformLatest` rozepsané hledání zahodí, jakmile se text změní, takže pomalá odpověď na
     * „ko" nepřepíše výsledky pro „kočka". Kategorie se hledá hned: ťuknutí je hotový dotaz.
     */
    val search: StateFlow<StickerSearchState> = searchRequest
        .debounce { if (it is StickerSearchRequest.Typed && it.text.isNotBlank()) SEARCH_DEBOUNCE_MS else 0L }
        .distinctUntilChanged()
        .transformLatest { request ->
            val query = request.toQuery()
            if (query == null) {
                emit(StickerSearchState.Idle)
                return@transformLatest
            }
            emit(StickerSearchState.Searching)
            emit(StickerSearchState.Results(repository.search(query)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StickerSearchState.Idle)

    /**
     * Kategorie pod polem hledání. Jednou za život ViewModelu: TDLib je drží v lokální databázi
     * a mění se jen s novou sadou kategorií od Telegramu, ne během jednoho otevření výběru.
     */
    val categories: StateFlow<List<StickerCategory>> = flow { emit(repository.categories()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Hledání podle kategorie. Text v poli tím zmizí — obojí naráz se hledat nedá. */
    fun searchCategory(category: StickerCategory) {
        searchRequest.value = StickerSearchRequest.Category(category.emojis)
    }

    private val emojiRequest = MutableStateFlow(SearchRequest("", emptyList()))

    /**
     * Hledání emoji podle slov. Stejný debounce a `transformLatest` jako u samolepek. Výsledky se
     * zúží na emoji z katalogu — TDLib zná i novější, které by systémové písmo neumělo nakreslit.
     */
    val emojiSearch: StateFlow<EmojiSearchState> = emojiRequest
        .debounce { if (it.text.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .transformLatest { request ->
            val text = request.text.trim()
            if (text.isEmpty()) {
                emit(EmojiSearchState.Idle)
                return@transformLatest
            }
            emit(EmojiSearchState.Searching)
            val found = emojiRepository.searchEmojis(text, request.languageCodes).mapNotNull(::catalogEmoji).distinct()
            // Vlastní emoji z nainstalovaných sad jen tam, kde je smí uživatel poslat.
            val chatId = customEmojiChatId
            val custom = if (_customEmojiAllowed.value && chatId != null) {
                repository.searchCustomEmoji(text, found, chatId)
            } else {
                emptyList()
            }
            emit(EmojiSearchState.Results(found, custom))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EmojiSearchState.Idle)

    fun searchEmoji(text: String, languageCodes: List<String>) {
        emojiRequest.value = SearchRequest(text, languageCodes)
    }

    /** Text hledání. Jazyk zařízení určuje, jak se slova převedou na emoji. */
    fun search(text: String, languageCodes: List<String>) {
        searchRequest.value = StickerSearchRequest.Typed(text, languageCodes)
    }

    val state: StateFlow<StickerPickerState> = combine(
        repository.installedSets(),
        repository.recentStickers(),
        repository.favoriteStickers(),
        setContents,
        group,
    ) { sets, recent, favorites, contents, group ->
        val groupSet = group.set
        StickerPickerState(
            tabs = stickerTabs(sets, groupSet),
            recent = recent,
            favorites = favorites,
            sets = contents,
            loaded = group.loaded,
            installedSetIds = sets.mapTo(HashSet()) { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StickerPickerState())

    /**
     * Přidá samolepku do oblíbených, nebo ji odebere. Stav se lokálně nepřepisuje — potvrzení
     * dorazí updatem a přepočítá záložku Oblíbené i hvězdičku.
     */
    fun toggleFavorite(sticker: StickerItem) {
        val favorite = state.value.favorites.orEmpty().any { it.sendFileId == sticker.sendFileId }
        viewModelScope.launch { repository.setFavorite(sticker.sendFileId, !favorite) }
    }

    /**
     * Trendy. Vlastní tok s `WhileSubscribed`, mimo [state]: jdou na server, takže se načtou,
     * až když si je někdo opravdu otevře — ne při každém otevření výběru.
     */
    val trending: StateFlow<List<StickerSetSample>?> = repository.trendingSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun installSet(setId: Long) {
        viewModelScope.launch { repository.setInstalled(setId, installed = true) }
    }

    private val _customEmojiAllowed = MutableStateFlow(false)

    /** Výběr emoji nabízí vlastní emoji ([StickerRepository.canUseCustomEmoji]). */
    val customEmojiAllowed: StateFlow<Boolean> = _customEmojiAllowed

    private var customEmojiChatId: Long? = null

    /** Zjistí, jestli smí uživatel v chatu [chatId] posílat vlastní emoji. */
    fun checkCustomEmoji(chatId: Long) {
        if (customEmojiChatId == chatId) return
        customEmojiChatId = chatId
        viewModelScope.launch { _customEmojiAllowed.value = repository.canUseCustomEmoji(chatId) }
    }

    /** Nainstalované sady vlastních emoji — záložky ve výběru emoji (jen kde je smí uživatel použít). */
    val customEmojiSets: StateFlow<List<CustomEmojiSet>> = repository.customEmojiSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val customEmojiContents = MutableStateFlow<Map<Long, List<CustomEmojiItem>>>(emptyMap())

    /** Obsah už otevřených sad vlastních emoji podle id. */
    val customEmoji: StateFlow<Map<Long, List<CustomEmojiItem>>> = customEmojiContents

    private val customEmojiLoading = mutableSetOf<Long>()

    /** Načte obsah sady vlastních emoji, až se její stránka ukáže. */
    fun loadCustomEmoji(setId: Long) {
        if (setId in customEmojiContents.value || !customEmojiLoading.add(setId)) return
        viewModelScope.launch {
            val items = repository.customEmojiSet(setId).getOrDefault(emptyList())
            customEmojiContents.update { it + (setId to items) }
            customEmojiLoading.remove(setId)
        }
    }

    /** Populární sady vlastních emoji; načtou se až s otevřením záložky „+". */
    val trendingCustomEmoji: StateFlow<List<CustomEmojiSetSample>?> = repository.trendingCustomEmojiSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Trendy byly vidět — Telegram je přestane hlásit jako nové. */
    fun markTrendingViewed(sets: List<StickerSetSample>) {
        viewModelScope.launch { repository.markTrendingViewed(sets.map { it.id }) }
    }

    /**
     * Odebere samolepku z nedávných. Lokálně se nic nepřepisuje — potvrzení dorazí updatem
     * a samolepka z mřížky odejde animací, jako odebraná oblíbená.
     */
    fun removeRecent(sticker: StickerItem) {
        viewModelScope.launch { repository.removeRecent(sticker.sendFileId) }
    }

    fun clearRecent() {
        viewModelScope.launch { repository.clearRecent() }
    }

    /** Záložka, na které byl uživatel naposledy; čte se jednou, při otevření výběru. */
    val lastTabKey: String? get() = settings.lastStickerTab.value

    /** Uživatel dosedl na záložku — příště se výběr otevře na ní. */
    fun rememberTab(key: String) {
        if (key != settings.lastStickerTab.value) settings.setLastStickerTab(key)
    }

    /** Stránka záložky se ukázala. Nedávné a oblíbené jsou živé toky, načítá se jen sada. */
    fun load(tab: StickerTab) {
        val setId = when (tab) {
            is StickerTab.Set -> tab.summary.id
            is StickerTab.Group -> tab.group.set.id
            else -> return
        }
        if (setId in setContents.value || !loading.add(setId)) return

        viewModelScope.launch {
            // Neúspěch se ukáže jako prázdná sada — výběr kvůli jedné sadě nezavíráme chybou.
            val stickers = repository.stickerSet(setId).getOrDefault(emptyList())
            setContents.update { it + (setId to stickers) }
            loading.remove(setId)
        }
    }

    private data class SearchRequest(val text: String, val languageCodes: List<String>)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Jak dlouho výběr čeká na sadu skupiny, než se otevře bez ní. */
        const val GROUP_SET_TIMEOUT_MS = 1_500L

        /** Pauza v psaní, po které se hledá. */
        const val SEARCH_DEBOUNCE_MS = 300L

    }
}

/** Výsledek dotazu na sadu skupiny. */
private data class GroupSetLookup(val loaded: Boolean = false, val set: GroupStickerSet? = null)

/**
 * Záložky v pořadí: Nedávné, Oblíbené, sada skupiny, Trendy, nainstalované sady.
 *
 * Sada skupiny hned za Oblíbenými — TDLib ji chce „před sadami uživatele" a v té skupině je
 * nejčastější volbou. Když ji má uživatel i nainstalovanou, mezi sadami se znovu neukáže:
 * dvě záložky s týmž obsahem by jen mátly.
 */
internal fun stickerTabs(installed: List<StickerSetSummary>, group: GroupStickerSet?): List<StickerTab> =
    buildList {
        add(StickerTab.Recent)
        add(StickerTab.Favorites)
        group?.let { add(StickerTab.Group(it)) }
        add(StickerTab.Trending)
        installed.filter { it.id != group?.set?.id }.mapTo(this) { StickerTab.Set(it) }
    }

/** Co se v samolepkách hledá: napsaný text, nebo ťuknutá kategorie. */
internal sealed interface StickerSearchRequest {
    data class Typed(val text: String, val languageCodes: List<String>) : StickerSearchRequest
    data class Category(val emojis: String) : StickerSearchRequest
}

/**
 * Dotaz pro repository, nebo `null`, když se nehledá. Napsaná emoji se hledají jako emoji,
 * cokoli jiného jako slovo (a tím i jako název sady).
 */
internal fun StickerSearchRequest.toQuery(): StickerQuery? = when (this) {
    is StickerSearchRequest.Category -> StickerQuery.Emoji(emojis).takeIf { emojis.isNotBlank() }
    is StickerSearchRequest.Typed -> {
        val trimmed = text.trim()
        when {
            trimmed.isEmpty() -> null
            trimmed.emojiClusterCount(MAX_SEARCH_EMOJI) > 0 -> StickerQuery.Emoji(trimmed)
            else -> StickerQuery.Text(trimmed, languageCodes)
        }
    }
}

/** Kolik emoji v hledání se ještě bere jako hledání podle emoji. */
private const val MAX_SEARCH_EMOJI = 10

sealed interface EmojiSearchState {
    data object Idle : EmojiSearchState
    data object Searching : EmojiSearchState
    data class Results(val emojis: List<String>, val custom: List<CustomEmojiItem> = emptyList()) : EmojiSearchState
}

sealed interface StickerSearchState {
    /** Nehledá se — výběr ukazuje záložky. */
    data object Idle : StickerSearchState
    data object Searching : StickerSearchState
    data class Results(val result: StickerSearchResult) : StickerSearchState
}

/**
 * Výběr samolepek v bottom sheetu.
 *
 * Sheet, ne panel místo klávesnice: výměna klávesnice za panel bez poskočení vstupního pole je
 * v Compose nejkřehčí část celé věci, a sheet ji nepotřebuje. Klávesnice se před otevřením zavře.
 *
 * Záložky jsou stránky [HorizontalPager]u: mezi sadami se dá **swipovat do stran**. Řádek záložek
 * a pager sdílejí jeden stav — swipe přepne záložku, ťuknutí na záložku posune pager.
 *
 * Stránky mají **pevnou výšku**, aby se sheet při přepínání nenatahoval a nesmršťoval podle počtu
 * samolepek. Samolepky usazené stránky hrají, ale až ve vysunutém sheetu (viz [SheetStickerMotion]).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun StickerPickerSheet(
    mode: ExpressionMode,
    onModeChange: (ExpressionMode) -> Unit,
    /**
     * `false` při úpravě zprávy: výběr ukáže jen emoji a přepínač schová. Uložená volba ([mode])
     * se tím nemění — po úpravě se výběr otevře zase tam, kde byl naposledy.
     */
    stickersAvailable: Boolean,
    /** Rozepsaný text, do kterého emoji vkládá. */
    draft: TextFieldValue,
    onDraftEdit: (TextFieldValue) -> Unit,
    onPick: (StickerItem) -> Unit,
    /** Chat, do kterého se hledá — GIF bot to chce vědět. */
    chatId: Long,
    onPickGif: (GifItem) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Části, mezi kterými jde přepínat. Formulář ankety chce jen samolepku, nebo jen GIF —
     * s jedinou částí se přepínač nekreslí.
     */
    modes: List<ExpressionMode> = ExpressionMode.entries,
    /** Volby odeslání u podržené samolepky a GIFu; `null` = jde jen poslat ťuknutím. */
    sendMenu: SendMenuOffer? = null,
    onPickWith: (StickerItem, SendMenuChoice) -> Unit = { _, _ -> },
    onPickGifWith: (GifItem, SendMenuChoice) -> Unit = { _, _ -> },
    /** Hlavní tlačítko náhledu podržené samolepky. Formulář ankety samolepku nevysílá, vybírá. */
    pickLabel: StringResource = Res.string.conversation_send,
    /** Formátování rozepsaného textu — kvůli vlastním emoji v náhledu textu nad emoji. */
    draftFormats: List<FormatSpan> = emptyList(),
    /**
     * Vložení vlastního emoji; `null` = výběr sady vlastních emoji nenabízí (formulář ankety).
     * Bez Premium mimo Uložené zprávy je výběr nenabídne ani tak (`checkCustomEmoji`).
     */
    onCustomEmoji: ((CustomEmojiItem) -> Unit)? = null,
) {
    val viewModel: StickerPickerViewModel = koinViewModel()
    LaunchedEffect(chatId) {
        viewModel.loadGroupSet(chatId)
        viewModel.checkCustomEmoji(chatId)
    }
    val customEmojiAllowed by viewModel.customEmojiAllowed.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var searchText by rememberSaveable { mutableStateOf("") }
    /** Ťuknutá kategorie (podle názvu); s napsaným textem se vylučuje. */
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Jazyk zařízení a angličtina jako záloha: klíčová slova samolepek bývají anglicky.
    val languageCodes = remember { listOf(Locale.current.language, "en").distinct() }
    /*
     * Rovnou celý, bez napůl rozbaleného stavu. Obsah je vyšší než polovina obrazovky, takže
     * napůl rozbalený sheet schoval spodek pod okraj — tlačítko skončilo pod navigační lištou.
     */
    val sheetState = rememberExpandedSheetState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val motion = rememberSheetStickerMotion(sheetState)

    /*
     * Samolepka otevřená podržením ve velkém náhledu (s hvězdičkou oblíbených a volbami odeslání).
     * Její buňka je pod náhledem podbarvená, ať je vidět, odkud přišla. Rotaci nepřežije — je to
     * chvilkové prohlédnutí, ne stav výběru.
     */
    var previewSticker by remember { mutableStateOf<StickerItem?>(null) }

    /** Sada otevřená z trendů. Sheet nad sheetem: výběr pod ní zůstává, zpátky se vrací zavřením. */
    var openSetId by rememberSaveable { mutableStateOf<Long?>(null) }

    val pick: (StickerItem) -> Unit = { sticker ->
        // Nejdřív sheet schovat, pak poslat: zmizení bez animace by vypadalo jako pád,
        // a zpráva se tak objeví až nad zavřeným sheetem.
        scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(sticker) }
    }
    val pickWith: (StickerItem, SendMenuChoice) -> Unit = { sticker, choice ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { onPickWith(sticker, choice) }
    }
    val favoriteIds = state.favorites.orEmpty().map { it.sendFileId }.toSet()
    val recentIds = state.recent.orEmpty().map { it.sendFileId }.toSet()
    val toggleFavorite: (StickerItem) -> Unit = { sticker ->
        haptics.toggle(on = sticker.sendFileId !in favoriteIds)
        viewModel.toggleFavorite(sticker)
    }
    // Podržení haptiku NEPOTŘEBUJE: `combinedClickable` ji u dlouhého stisku dělá samo
    // a ruční volání navíc ji zdvojí (změřeno: dvě vibrace 9 ms po sobě).
    val cells = StickerCells(
        activeStickerId = previewSticker?.id,
        favoriteFileIds = favoriteIds,
        recentFileIds = recentIds,
        onRemoveRecent = viewModel::removeRecent,
        onPick = pick,
        onPreview = { sticker -> previewSticker = sticker },
        onToggleFavorite = toggleFavorite,
        sendMenu = sendMenu,
        onPickWith = pickWith,
        // Mřížka hraje až ve vysunutém sheetu a při zasouvání stojí.
        playing = true,
        held = !motion.started,
        paused = !motion.settled,
    )

    TrendingSetSheetHost(setId = openSetId, onClose = { openSetId = null }, pick = pick)
    StickerPreviewHost(
        sticker = previewSticker,
        cells = cells,
        pickLabel = stringResource(pickLabel),
        onClose = { previewSticker = null },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { expressionSheetInsets() },
    ) {
        /*
         * Emoji a samolepky jsou dvě části JEDNOHO sheetu, ne dva sheety: obojí se otevírá týmž
         * tlačítkem a patří k témuž psaní. Přepínač je spojená skupina přepínacích tlačítek
         * (M3 Expressive connected button group) — rovnocenné volby, ne záložky obsahu (ty má
         * každá část vlastní). Segmentová tlačítka, která tu byla, M3 Expressive nahrazuje právě
         * touhle skupinou; vybrané se zakulatí celé a dostane fajfku.
         */
        if (stickersAvailable && modes.size > 1) Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            modes.forEachIndexed { index, entry ->
                val checked = mode == entry
                // Vybrané se o fajfku rozšíří, jako záložky profilu: se stejnou šířkou všech tří
                // by fajfka česky ukrojila „Samolepky".
                val weight by animateFloatAsState(
                    targetValue = if (checked) SELECTED_MODE_WEIGHT else 1f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "expression-mode-weight",
                )
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { if (!checked) onModeChange(entry) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(weight).semantics { role = Role.Tab },
                ) {
                    if (checked) {
                        Icon(
                            painterResource(Res.drawable.check_24dp),
                            contentDescription = null,
                            modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                        )
                    }
                    Text(
                        when (entry) {
                            ExpressionMode.Emoji -> stringResource(Res.string.stickers_tab_emoji)
                            ExpressionMode.Stickers -> stringResource(Res.string.stickers_tab_stickers)
                            ExpressionMode.Gifs -> stringResource(Res.string.stickers_tab_gifs)
                        },
                        maxLines = 1,
                    )
                }
            }
        }

        if (mode == ExpressionMode.Emoji || !stickersAvailable) {
            EmojiPanel(
                viewModel = viewModel,
                draft = draft,
                onDraftEdit = onDraftEdit,
                languageCodes = languageCodes,
                draftFormats = draftFormats,
                onCustomEmoji = onCustomEmoji?.takeIf { customEmojiAllowed },
            )
            return@ModalBottomSheet
        }

        if (mode == ExpressionMode.Gifs) {
            GifPanel(
                chatId = chatId,
                // Jako u samolepky: nejdřív sheet schovat, pak poslat.
                onPick = { gif -> scope.launch { sheetState.hide() }.invokeOnCompletion { onPickGif(gif) } },
                sendMenu = sendMenu,
                onPickWith = { gif, choice ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onPickGifWith(gif, choice) }
                },
                pickLabel = pickLabel,
            )
            return@ModalBottomSheet
        }

        ControlledSearchPill(
            query = searchText,
            placeholder = stringResource(Res.string.stickers_search),
            clearDescription = stringResource(Res.string.stickers_clear_search),
            onQueryChange = {
                searchText = it
                // Psaní kategorii zruší: hledá se buď text, nebo kategorie.
                categoryName = null
                viewModel.search(it, languageCodes)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        StickerCategoryRow(
            categories = categories,
            selectedName = categoryName,
            onSelect = { category ->
                // Kategorie je hledání bez psaní: klávesnice by jen překryla výsledky.
                keyboard?.hide()
                focusManager.clearFocus()
                searchText = ""
                if (category.name == categoryName) {
                    categoryName = null
                    viewModel.search("", languageCodes)
                } else {
                    categoryName = category.name
                    viewModel.searchCategory(category)
                }
            },
        )

        /*
         * Hledání nahradí záložky i stránky, ale ve STEJNĚ vysoké ploše — jinak by sheet při
         * prvním písmenu poskočil. Výsledky se ukazují podle textu v poli, ne podle stavu
         * hledání: po smazání textu se záložky vrátí hned, bez čekání na debounce.
         */
        if (searchText.isNotBlank() || categoryName != null) {
            Box(Modifier.fillMaxWidth().height(STICKER_TAB_ROW_HEIGHT + STICKER_PICKER_HEIGHT + sheetBottomInset())) {
                StickerSearchResults(
                    search = search,
                    // Výsledky jsou jediná mřížka na obrazovce, hrají celé.
                    cells = cells,
                    installedSetIds = state.installedSetIds,
                    onOpenSet = { openSetId = it },
                    onInstallSet = viewModel::installSet,
                )
            }
            return@ModalBottomSheet
        }

        StickerTabsPager(
            state = state,
            viewModel = viewModel,
            cells = cells,
            onOpenSet = { openSetId = it },
        )
    }
}

/**
 * Řádek záložek a pod ním jejich stránky.
 *
 * Otevře se na záložce, na které byl uživatel naposledy ([AppSettings.lastStickerTab]) — kdo
 * posílá z jedné sady, nemusí k ní pokaždé dojíždět. `PagerState` proto vzniká až se seznamem
 * sad: s prvním stavem (jen Nedávné, Oblíbené, Trendy) by se výběr otevřel na Nedávných a pak
 * přeskočil na sadu. Čekání je dotaz do lokální databáze TDLib, mezitím drží místo indikátor
 * ve stejně vysoké ploše, takže sheet neposkočí.
 *
 * Pamatuje se USAZENÁ stránka, ne každá, přes kterou prst přejel. Sada, která mezitím zmizela,
 * se nenajde a výběr začne Nedávnými.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StickerTabsPager(
    state: StickerPickerState,
    viewModel: StickerPickerViewModel,
    cells: StickerCells,
    onOpenSet: (Long) -> Unit,
) {
    if (!state.loaded) {
        Box(
            Modifier.fillMaxWidth().height(STICKER_TAB_ROW_HEIGHT + STICKER_PICKER_HEIGHT + sheetBottomInset()),
            contentAlignment = Alignment.Center,
        ) { LoadingIndicator() }
        return
    }

    val scope = rememberCoroutineScope()
    val tabs by rememberUpdatedState(state.tabs)
    // Počet stránek se mění: sada přibude nebo zmizí i za otevřeného výběru. Proto lambda, ne číslo.
    val pagerState = rememberPagerState(
        initialPage = remember { state.tabs.indexOfStored(viewModel.lastTabKey).coerceAtLeast(0) },
        pageCount = { tabs.size },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { tabs.getOrNull(pagerState.settledPage)?.storageKey() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect(viewModel::rememberTab)
    }

        StickerTabRow(
            tabs = state.tabs,
            // `currentPage` se přepne v půlce swipu, takže indikátor jde s prstem, ne až po dojetí.
            selectedIndex = pagerState.currentPage.coerceIn(0, state.tabs.lastIndex),
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier.height(STICKER_TAB_ROW_HEIGHT),
        )

        HorizontalPager(
            state = pagerState,
            // Klíč podle záložky, ne indexu: až dorazí sady, stránky nedávných a oblíbených zůstanou
            // tytéž a nerozsype se jim stav (pozice scrollu).
            key = { index -> state.tabs.getOrNull(index)?.storageKey() ?: index },
            modifier = Modifier.fillMaxWidth().height(STICKER_PICKER_HEIGHT + sheetBottomInset()),
        ) { page ->
            val tab = state.tabs.getOrNull(page) ?: return@HorizontalPager
            LaunchedEffect(tab) { viewModel.load(tab) }

            if (tab == StickerTab.Trending) {
                TrendingPage(
                    viewModel = viewModel,
                    onOpenSet = onOpenSet,
                )
                return@HorizontalPager
            }

            StickerPage(
                tab = tab,
                stickers = state.stickersOf(tab),
                onClearRecent = viewModel::clearRecent,
                cells = cells.copy(
                    /*
                     * Hraje jen USAZENÁ stránka. Během swipu jsou vidět dvě; přijíždějící sada by
                     * se uprostřed tahu rozbalovala a parsovala desítky animací a tah by zadrhával.
                     * Rozběhne se po dosednutí, z náhledu, který odpovídá prvnímu snímku.
                     */
                    held = cells.held || page != pagerState.settledPage,
                ),
            )
        }
}

/**
 * Samolepka poslaná z detailu sady otevřeného nad výběrem. Oba sheety se zavřou: detail sám,
 * výběr přes [pick] — ten ho schová animací a pak samolepku pošle.
 */
@Composable
private fun TrendingSetSheetHost(
    setId: Long?,
    onClose: () -> Unit,
    pick: (StickerItem) -> Unit,
) {
    setId ?: return
    StickerSetSheet(
        setId = setId,
        onPick = { sticker ->
            onClose()
            pick(sticker)
        },
        onDismiss = onClose,
    )
}

/**
 * Stabilní klíč záložky: klíč stránky pageru i hodnota pro [AppSettings.lastStickerTab]. Sada
 * podle id, ne podle pořadí — pořadí sad si uživatel mění a přidaná sada by ostatní posunula.
 */
internal fun StickerTab.storageKey(): String = when (this) {
    StickerTab.Recent -> "recent"
    StickerTab.Favorites -> "favorites"
    StickerTab.Trending -> "trending"
    is StickerTab.Set -> "set:${summary.id}"
    // Vlastní klíč, ne „set:": jinde se tatáž sada ukáže jako obyčejná, nebo vůbec.
    is StickerTab.Group -> "group:${group.set.id}"
}

/** Index záložky uložené pod [key], nebo -1 (nic uloženo, sada mezitím odebraná). */
internal fun List<StickerTab>.indexOfStored(key: String?): Int =
    if (key == null) -1 else indexOfFirst { it.storageKey() == key }

/** Obsah jedné záložky: načítání, prázdný stav, nebo mřížka. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StickerPage(
    tab: StickerTab,
    stickers: List<StickerItem>?,
    cells: StickerCells,
    onClearRecent: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        ClearRecentDialog(
            onConfirm = {
                confirmClear = false
                onClearRecent()
            },
            onDismiss = { confirmClear = false },
        )
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (stickers == null) {
            LoadingIndicator()
            return@Box
        }

        /*
         * Mřížka zůstává, i když je prázdná. Kdyby ji prázdný stav nahradil, zmizela by v okamžiku,
         * kdy z ní odejde poslední samolepka — a ta by nestihla odejít animací (typicky poslední
         * oblíbená odebraná hvězdičkou). Prázdný text se proto nad ní jen plynule objeví.
         */
        LazyVerticalGrid(
            columns = GridCells.Adaptive(STICKER_CELL),
            contentPadding = sheetListPadding(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            /*
             * „Vymazat" nahoře v Nedávných, jako v Telegramu. Jen tady: oblíbené se odebírají
             * hvězdičkou po jedné a sada se odebírá celá v jejím detailu.
             */
            if (tab == StickerTab.Recent && stickers.isNotEmpty()) {
                item(key = "clear-recent", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.animateItem(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = { confirmClear = true }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.stickers_recent_clear))
                        }
                    }
                }
            }
            items(stickers, key = { it.id }) { sticker ->
                StickerCell(sticker, cells, animateStickerItem())
            }
        }

        AnimatedVisibility(
            visible = stickers.isEmpty(),
            enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Text(
                text = when (tab) {
                    StickerTab.Recent -> stringResource(Res.string.stickers_recent_empty)
                    StickerTab.Favorites -> stringResource(Res.string.stickers_favorites_empty)
                    StickerTab.Trending -> ""
                    is StickerTab.Set, is StickerTab.Group -> stringResource(Res.string.stickers_set_empty)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/**
 * Potvrzení vymazání nedávných. Seznam je v cloudu, takže zmizí na všech zařízeních účtu
 * a vrátit ho nejde — proto se ptá, na rozdíl od odebrání jedné samolepky.
 */
@Composable
private fun ClearRecentDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.stickers_recent_clear_title),
        text = stringResource(Res.string.stickers_recent_clear_text),
        confirmLabel = stringResource(Res.string.stickers_recent_clear_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = Res.drawable.delete_24dp,
        destructive = true,
    )
}

/**
 * Trendy: seznam populárních sad, u každé pár samolepek na ukázku a tlačítko Přidat.
 *
 * Ťuknutí na sadu otevře její detail (celá sada, posílání, přidání) — řádek ukazuje jen ochutnávku.
 * Trendy se načítají až tady, při prvním zobrazení stránky, a pak se označí za zhlédnuté.
 */
@Composable
private fun TrendingPage(
    viewModel: StickerPickerViewModel,
    onOpenSet: (Long) -> Unit,
) {
    val trending by viewModel.trending.collectAsStateWithLifecycle()

    LaunchedEffect(trending) {
        trending?.takeIf { it.isNotEmpty() }?.let(viewModel::markTrendingViewed)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val sets = trending
        when {
            sets == null -> LoadingIndicator()

            sets.isEmpty() -> Text(
                text = stringResource(Res.string.stickers_trending_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )

            else -> LazyColumn(
                contentPadding = sheetListPadding(horizontal = 0.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sets, key = { it.id }) { set ->
                    StickerSetRow(
                        set = set,
                        onOpen = { onOpenSet(set.id) },
                        onInstall = { viewModel.installSet(set.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerSetRow(
    set: StickerSetSample,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = set.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Přidaná sada ukazuje stav, ne mrtvé tlačítko. Odebírá se v detailu sady.
            if (set.isInstalled) {
                Text(
                    text = stringResource(Res.string.stickers_added),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FilledTonalButton(onClick = onInstall, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.stickers_add)) }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            set.covers.take(TRENDING_COVERS).forEach { cover ->
                StickerPreview(cover, playing = false, modifier = Modifier.size(TRENDING_COVER_SIZE))
            }
        }
    }
}

/**
 * Co buňky mřížky (a náhled podržené samolepky) potřebují od sheetu. Balík místo pěti parametrů,
 * které by se jinak táhly přes stránku záložky i výsledky hledání.
 */
internal data class StickerCells(
    /** Samolepka otevřená v náhledu; její buňka je podbarvená. */
    val activeStickerId: Long?,
    val favoriteFileIds: Set<Int>,
    val onPick: (StickerItem) -> Unit,
    /** Podržení: otevře náhled ([StickerPreviewHost]). */
    val onPreview: (StickerItem) -> Unit,
    val onToggleFavorite: (StickerItem) -> Unit,
    /**
     * Soubory nedávných samolepek: podržená samolepka mezi nimi nabídne „Odebrat z nedávných".
     * Prázdné tam, kde se nedávné neukazují (detail sady), a tlačítko tam pak není.
     */
    val recentFileIds: Set<Int> = emptySet(),
    val onRemoveRecent: (StickerItem) -> Unit = {},
    /** Volby odeslání v náhledu podržené samolepky; `null` = tlačítko není (sada, anketa). */
    val sendMenu: SendMenuOffer? = null,
    val onPickWith: (StickerItem, SendMenuChoice) -> Unit = { _, _ -> },
    /**
     * Hrají všechny samolepky mřížky, ne jen podržená. Jen tam, kde je mřížka na obrazovce
     * jediná (jedna sada, oblíbené, výsledky hledání) — vidět je jich nejvýš pár desítek
     * a `LazyVerticalGrid` skládá jen viditelné buňky.
     */
    val playing: Boolean = false,
    /**
     * Hrající mřížka animace zatím nenačítá a drží náhledy: sheet ještě nevyjel (viz
     * [SheetStickerMotion]), nebo stránka pageru nedosedla.
     */
    val held: Boolean = false,
    /** Rozhýbané samolepky stojí na místě — sheet se zasouvá (viz [SheetStickerMotion]). */
    val paused: Boolean = false,
)

/**
 * Kdy se samolepky v mřížce sheetu smějí hýbat.
 *
 * Rozbalit a rozparsovat pár desítek `.tgs` a pak je každý snímek kreslit na hlavním vlákně
 * stojí tolik, že vyjíždějící sheet drhnul — mřížka se rozhýbala hned v prvním snímku, ne až
 * po dojetí. Proto:
 * - [started]: sheet aspoň jednou dojel do rozbaleného stavu. Do té doby mřížka stojí na
 *   statických náhledech a animace se ani nenačítají ([StickerCells.held]). Rozběhne se
 *   z náhledu, který odpovídá prvnímu snímku, takže to není skok (stejně jako po dosednutí
 *   stránky v pageru).
 * - [settled]: sheet stojí rozbalený a nikam nemíří. Jakmile míří dolů (ťuknutí vedle, tah přes
 *   práh, odeslání samolepky), samolepky ZAMRZNOU na místě. Výměna za náhled by uprostřed
 *   zasouvání znamenala skládat obsah znovu, a to je přesně ta práce, které se tu šetří.
 */
internal data class SheetStickerMotion(val started: Boolean, val settled: Boolean)

@Composable
internal fun rememberSheetStickerMotion(sheetState: SheetState): SheetStickerMotion {
    // `targetValue` se přepne hned na začátku zasouvání, `currentValue` až po dojetí.
    val settled by remember(sheetState) {
        derivedStateOf {
            sheetState.currentValue == SheetValue.Expanded && sheetState.targetValue == SheetValue.Expanded
        }
    }
    var started by remember(sheetState) { mutableStateOf(false) }
    LaunchedEffect(settled) { if (settled) started = true }
    return SheetStickerMotion(started = started, settled = settled)
}

/**
 * Jedna samolepka v mřížce: ťuknutí pošle, podržení otevře velký náhled ([StickerPreviewDialog]).
 *
 * **Hvězdička oblíbených a volby odeslání** (bez zvuku, naplánovat, až bude online) jsou v náhledu,
 * ne v buňce: hvězdička u každé buňky by zaplevelila mřížku a ťuknutí vedle ní by samolepku
 * omylem poslalo. U textu jsou volby na podržení odesílacího tlačítka, jenže podržení samolepky
 * patří náhledu — druhé gesto nad ní by se s ním pralo.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun StickerCell(sticker: StickerItem, cells: StickerCells, modifier: Modifier = Modifier) {
    val active = sticker.id == cells.activeStickerId
    val highlight = animateMorph(active, "sticker-highlight")
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier
            .size(STICKER_CELL)
            /*
             * Podbarvení nese potvrzení podržení i u statické samolepky, která se rozhýbat nemá
             * jak. Rozvine se z malého kruhu do dvanáctistranné „sušenky" (`ShapeMorphs.Highlight`)
             * a zase se do něj stáhne — hravé „tuhle si prohlížíš", u samolepek hravost sedí.
             * Kreslí se až při kreslení, takže průběh nic nerekomponuje.
             */
            .drawBehind {
                val progress = highlight.coerceIn(0f, 1f)
                if (progress <= 0f) return@drawBehind
                val grow = HIGHLIGHT_START_SCALE + (1f - HIGHLIGHT_START_SCALE) * progress
                scale(grow) {
                    drawOutline(
                        MorphShape(ShapeMorphs.Highlight, progress, rotation = HIGHLIGHT_ROTATION)
                            .createOutline(size, layoutDirection, this),
                        highlightColor.copy(alpha = progress),
                    )
                }
            }
    ) {
        /*
         * Klikání (a s ním vlnka stisku) je ve vlastní vrstvě ořezané do téže sušenky, do jaké se
         * rozvine podbarvení. Dřív byl ořez zaoblený čtverec na celé buňce a pod sušenkou pak
         * vykukovala čtvercová vlnka. Samolepka sama ořezaná není — přišla by o rohy.
         */
        Box(
            Modifier
                .matchParentSize()
                .clip(STICKER_TOUCH_SHAPE)
                .contextClickable(
                    onClick = { cells.onPick(sticker) },
                    // Podržení samolepku neposílá — jen otevře náhled. Po puštění
                    // `combinedClickable` klik nevyvolá, takže nic neodejde omylem.
                    onLongClickLabel = stringResource(Res.string.stickers_preview),
                    onLongClick = { cells.onPreview(sticker) },
                ),
        )

        // Vlastní emoji (sada emoji otevřená z Nastavení) přes `CustomEmojiImage` — jednobarevné
        // obarví barvou textu; jako samolepka by bylo bílé na světlém pozadí.
        val customEmoji = sticker.customEmojiId
        if (customEmoji != null) {
            CustomEmojiImage(id = customEmoji, fallback = sticker.preview.emoji, modifier = Modifier.fillMaxSize().padding(14.dp))
        } else {
            StickerPreview(
                sticker.preview,
                playing = active || cells.playing,
                held = cells.held && !active,
                paused = cells.paused,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        }
    }
}

/** Nabízí aspoň jednu volbu odeslání (bez efektu, ten u samolepky a GIFu není). */
internal fun SendMenuOffer.hasSendOptions(): Boolean = silent || schedule || whenOnline

/** Nabídka voleb odeslání pod tlačítkem, které ji otevřelo. Volba nabídku zavře. */
@Composable
internal fun SendOptionsMenu(
    offer: SendMenuOffer,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onChoice: (SendMenuChoice) -> Unit,
) {
    ExpressiveMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        groups = listOf(
            listOfNotNull(
                MenuAction(stringResource(Res.string.conversation_send_silent), Res.drawable.notifications_off_24dp) {
                    onDismiss()
                    onChoice(SendMenuChoice.Silent)
                }.takeIf { offer.silent },
                MenuAction(stringResource(Res.string.conversation_send_schedule), Res.drawable.schedule_send_24dp) {
                    onDismiss()
                    onChoice(SendMenuChoice.Schedule)
                }.takeIf { offer.schedule },
                MenuAction(stringResource(Res.string.conversation_send_when_online), Res.drawable.online_prediction_24dp) {
                    onDismiss()
                    onChoice(SendMenuChoice.WhenOnline)
                }.takeIf { offer.whenOnline },
            ),
        ),
    )
}

/** Tvar vlnky stisku: sušenka, do které se podbarvení rozvine (plný průběh i s pootočením). */
private val STICKER_TOUCH_SHAPE = MorphShape(ShapeMorphs.Highlight, 1f, rotation = HIGHLIGHT_ROTATION)

/** Z jak malého kruhu se podbarvení podržené samolepky rozvine. */
private const val HIGHLIGHT_START_SCALE = 0.5f

/** Pootočení podbarvení při plném tvaru. */
private const val HIGHLIGHT_ROTATION = 45f

/**
 * Příchod, odchod a přeskupení samolepky v mřížce.
 *
 * Odebraná samolepka (typicky z oblíbených) plynule zmizí a ostatní se na její místo dosunou —
 * bez toho mřížka při každé změně seznamu skočila. Průhlednost jde přes effects spec (bez
 * přeběhu, ten by u alfy blikl), posun přes spatial (s jemným pružením jako zbytek aplikace).
 */
@Composable
internal fun LazyGridItemScope.animateStickerItem(): Modifier = Modifier.animateItem(
    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    fadeOutSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
)

/**
 * Výsledky hledání, každá skupina pod svým nadpisem: sady podle názvu, samolepky z vlastních sad
 * a samolepky z veřejných sad.
 *
 * Sady stojí NAHOŘE, ale jen dvě (zbytek po ťuknutí na „Všechny sady"). Samolepek z vlastních
 * sad bývá ke slovu i přes stovku (dva dotazy po nejvýš sto) — „duck" na testovacím účtu
 * zaplnil mnoho obrazovek — a sada pod nimi by byla k nenalezení. Dvě sady zaberou zhruba
 * polovinu plochy, takže první samolepky jsou vidět hned pod nimi. Nainstalovanost sady se čte
 * z [installedSetIds] (živý seznam záložek), ne z výsledku hledání — ten je jednorázový a sada
 * přidaná tlačítkem by jinak dál nabízela Přidat.
 */
@Composable
private fun StickerSearchResults(
    search: StickerSearchState,
    cells: StickerCells,
    installedSetIds: Set<Long>,
    onOpenSet: (Long) -> Unit,
    onInstallSet: (Long) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (search) {
            // Idle při neprázdném textu = debounce ještě neproběhl; vypadá to stejně jako hledání.
            StickerSearchState.Idle, StickerSearchState.Searching -> LoadingIndicator()

            is StickerSearchState.Results -> {
                val result = search.result
                // Nadpisy sekcí se čtou tady: `searchSection` je rozšíření `LazyGridScope`,
                // ne composable funkce.
                val installedTitle = stringResource(Res.string.stickers_section_installed)
                val publicTitle = stringResource(Res.string.stickers_section_public)
                val setsTitle = stringResource(Res.string.stickers_section_sets)
                // Rozbalení platí pro jeden výsledek; nové hledání začíná zase sbalené.
                var setsExpanded by remember(result) { mutableStateOf(false) }
                if (result.installed.isEmpty() && result.public.isEmpty() && result.sets.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.stickers_none_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                    return@Box
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(STICKER_CELL),
                    contentPadding = sheetListPadding(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    setsSection(
                        title = setsTitle,
                        sets = if (setsExpanded) result.sets else result.sets.take(SETS_COLLAPSED),
                        installedSetIds = installedSetIds,
                        onOpenSet = onOpenSet,
                        onInstallSet = onInstallSet,
                    )
                    if (!setsExpanded && result.sets.size > SETS_COLLAPSED) {
                        item(key = "sets-more", span = { GridItemSpan(maxLineSpan) }) {
                            TextButton(onClick = { setsExpanded = true }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(Res.string.stickers_sets_show_all))
                            }
                        }
                    }
                    searchSection(installedTitle, result.installed, cells)
                    searchSection(publicTitle, result.public, cells)
                }
            }
        }
    }
}

private fun LazyGridScope.searchSection(
    title: String,
    stickers: List<StickerItem>,
    cells: StickerCells,
) {
    if (stickers.isEmpty()) return

    sectionHeader(title)
    // Klíč nese sekci: tatáž samolepka se ve dvou sekcích neobjeví (veřejné se filtrují), ale
    // klíče nadpisu a samolepky musí být různého tvaru i tak.
    items(stickers, key = { "sticker-${it.id}" }) { sticker ->
        StickerCell(sticker, cells, animateStickerItem())
    }
}

/** Sady podle názvu: řádky jako v trendech, přes celou šířku mřížky. */
private fun LazyGridScope.setsSection(
    title: String,
    sets: List<StickerSetSample>,
    installedSetIds: Set<Long>,
    onOpenSet: (Long) -> Unit,
    onInstallSet: (Long) -> Unit,
) {
    if (sets.isEmpty()) return

    sectionHeader(title)
    items(sets, key = { "set-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { set ->
        StickerSetRow(
            set = set.copy(isInstalled = set.id in installedSetIds),
            onOpen = { onOpenSet(set.id) },
            onInstall = { onInstallSet(set.id) },
            // Mřížka má vlastní odsazení po stranách; řádek ho nepotřebuje znovu celé.
            horizontalPadding = 4.dp,
        )
    }
}

private fun LazyGridScope.sectionHeader(title: String) {
    item(key = "header-$title", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}

/**
 * Kategorie pod polem hledání (láska, pozdrav, smích…): ťuknutí hledá bez psaní, druhé ťuknutí
 * hledání zruší.
 *
 * Tlačítko nese JEN ikonu, jako v Telegramu. Název od Telegramu je jen pro čtečku obrazovky: server
 * ho lokalizuje podle jazykového balíčku Telegramu (`language_pack_id`, nastavuje ho
 * `syncLanguagePack`), jenže zrovna tyhle názvy česky nemá — v české aplikaci přijde „Love",
 * „Approval" i s českým balíčkem (německy už „Liebe"; ověřeno sondou). Ikony (srdce, palec,
 * úsměv) se čtou bez jazyka a vejde se jich na řádek víc.
 *
 * Řádek se objeví až s načtenými kategoriemi; bez nich (bez sítě při prvním otevření) by byl
 * jen prázdný pruh.
 */
@Composable
private fun StickerCategoryRow(
    categories: List<StickerCategory>,
    selectedName: String?,
    onSelect: (StickerCategory) -> Unit,
) {
    AnimatedVisibility(
        visible = categories.isNotEmpty(),
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
            fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
    ) {
        val categoryRow = rememberLazyListState()
        LazyRow(
            state = categoryRow,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().height(CATEGORY_ROW_HEIGHT).wheelScrollsHorizontally(categoryRow),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(categories, key = { it.name }) { category ->
                // Tónové přepínací tlačítko, ne obrysový čip: výběr jedné ikony z řady je v M3
                // Expressive přepínač s tvarem (vybraný se vyplní a zaoblí), jako formátování
                // nebo ztlumení v hovoru. Široké, aby cíl pro prst zůstal jako u čipu.
                val selected = category.name == selectedName
                FilledTonalIconToggleButton(
                    checked = selected,
                    onCheckedChange = { onSelect(category) },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier
                        .size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide))
                        .semantics { contentDescription = category.name },
                ) {
                    StickerCategoryIcon(category, selected = selected)
                }
            }
        }
    }
}

/**
 * Ikona kategorie: jednobarevné vlastní emoji Telegramu.
 *
 * Stojí na POSLEDNÍM snímku a po výběru se jednou přehraje. Animace ikon kategorií se kreslí
 * z prázdna, takže první snímek je prázdný, a statický náhled od Telegramu taky (viz
 * `TdlStickerRepository.categories`). Smyčka u deseti ikon vedle sebe by byla neklid.
 *
 * Barví se barvou obsahu tlačítka, takže se s výběrem přebarví spolu s plochou.
 */
@Composable
private fun StickerCategoryIcon(category: StickerCategory, selected: Boolean) {
    val tint = LocalContentColor.current
    val modifier = Modifier
        .size(CATEGORY_ICON_SIZE)
        .then(if (category.iconTinted) Modifier.tinted(tint) else Modifier)
    val animation = category.icon.animationFileId

    if (animation == null) {
        StickerPreview(category.icon, playing = false, modifier = modifier)
        return
    }
    AnimatedSticker(
        fileId = animation,
        // Popis nese popisek čipu.
        contentDescription = null,
        playback = if (selected) StickerPlayback.Once else StickerPlayback.LastFrame,
        modifier = modifier,
        placeholder = {},
    )
}

/** Kolik sad je ve výsledcích vidět, než je uživatel rozbalí. */
private const val SETS_COLLAPSED = 2

/** Ikona v tlačítku kategorie. O kus větší než ikona vedle popisku (18 dp) — popisek tu chybí. */
private val CATEGORY_ICON_SIZE = 20.dp

/** Řádek kategorií: malé tlačítko (40 dp) a kousek vzduchu nad záložkami. */
private val CATEGORY_ROW_HEIGHT = 44.dp

@Composable
internal fun StickerTabIcon(tab: StickerTab) {
    when (tab) {
        StickerTab.Recent -> Icon(painterResource(Res.drawable.schedule_24dp), contentDescription = stringResource(Res.string.stickers_tab_recent))
        StickerTab.Favorites -> Icon(painterResource(Res.drawable.star_24dp), contentDescription = stringResource(Res.string.stickers_tab_favorites))
        StickerTab.Trending -> Icon(painterResource(Res.drawable.trending_up_24dp), contentDescription = stringResource(Res.string.stickers_tab_trending))
        /*
         * Fotka skupiny, ne obálka sady: obálka by vypadala jako další nainstalovaná sada
         * a nebylo by poznat, že tahle patří k chatu. Tvar jako avatar skupiny všude jinde.
         */
        is StickerTab.Group -> {
            val description = stringResource(Res.string.stickers_tab_group, tab.group.chatTitle)
            Avatar(
                chatId = tab.group.chatId,
                title = tab.group.chatTitle,
                avatarFileId = tab.group.chatPhotoFileId,
                size = 28.dp,
                shape = ChatKind.Group.avatarShape(),
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
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
 * Bez [playing] **nehraje** a kreslí se nejlevnější dostupnou podobou: rastrový náhled, pak statická
 * `.webp`, a teprve když není ani jedno, první snímek `.tgs` (ten znamená rozbalit a rozparsovat
 * animaci). Tak se kreslí všude, kde je vedle sebe víc sad naráz — obálky v záložkách, ukázky
 * v trendech.
 *
 * [playing] ji rozhýbe — mřížku jedné sady, nebo podrženou samolepku. Do načtení animace drží místo
 * tentýž statický náhled, takže se samolepka rozběhne na místě, bez probliknutí. Statická `.webp`
 * nemá co přehrát a zůstane stát. Videosamolepka `.webm` hraje přes [VideoSticker] (na iOS
 * zůstane náhledem).
 *
 * [held] animaci zatím nenačítá a drží náhled, ale skladba zůstává táž jako u hrající samolepky:
 * po uvolnění se vymění jen náhled za animaci. Přepnutí [playing] by naopak náhled složilo znovu
 * na jiném místě stromu — u celé mřížky naráz. [paused] rozhýbanou samolepku zmrazí na právě
 * kresleném snímku a animaci si nechá.
 */
@Composable
internal fun StickerPreview(
    sticker: MessageMedia.Sticker,
    playing: Boolean,
    modifier: Modifier = Modifier,
    held: Boolean = false,
    paused: Boolean = false,
) {
    val still = sticker.thumbnailFileId ?: sticker.fileId
    val description = sticker.emoji.ifBlank { stringResource(Res.string.media_sticker) }
    val animation = sticker.animationFileId
    val video = sticker.videoFileId

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
            isPlaying = !paused && !held,
            // Bez náhledu je první snímek pořád lepší než emoji, takže se načte i zadržená.
            load = !held || still == null,
            modifier = modifier,
            placeholder = stillImage,
        )

        // Videosamolepka hraje jen tam, kde má hrát; jinak (a na iOS) zůstane náhledem.
        video != null && playing -> VideoSticker(
            fileId = video,
            contentDescription = description,
            playing = !paused,
            load = !held,
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

/**
 * Insety sheetu výběru: nahoře stavová lišta, dole JEN klávesnice (hledání).
 *
 * Výchozí `modalWindowInsets` dává obsahu jako odsazení i navigační lištu, takže mřížka končila
 * ostrou hranou nad lištou a pod ní zůstal prázdný pruh sheetu. Lištu si proto berou seznamy
 * v `contentPadding` ([sheetListPadding]) a jedou pod ni — tentýž vzor jako seznam chatů,
 * konverzace a výsledky hledání. Klávesnici nechat seznamům nejde: mřížka má pevnou výšku
 * a skoro celá by zmizela pod ní.
 */
@Composable
internal fun expressionSheetInsets(): WindowInsets =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Top).union(WindowInsets.ime)

/**
 * Kus navigační lišty, který sheet obsahu nechává (viz [expressionSheetInsets]). S otevřenou
 * klávesnicí nula — lištu překrývá klávesnice a její místo drží sheet. O tolik jsou plochy
 * s pevnou výškou vyšší, aby viditelná část zůstala stejná jako dřív.
 */
@Composable
internal fun sheetBottomInset(): Dp =
    WindowInsets.navigationBars.exclude(WindowInsets.ime).asPaddingValues().calculateBottomPadding()

/** Odsazení seznamu v sheetu výběru: poslední řádek se doroluje nad navigační lištu. */
@Composable
internal fun sheetListPadding(horizontal: Dp, vertical: Dp): PaddingValues =
    PaddingValues(start = horizontal, top = vertical, end = horizontal, bottom = vertical + sheetBottomInset())

/** Řádek záložek. Při hledání ho výsledky zastoupí, takže jeho výška je pevná. */
internal val STICKER_TAB_ROW_HEIGHT = 48.dp

/** Výška mřížky. Kolem tří řádků — dost na výběr, konverzace nad sheetem zůstává vidět. */
internal val STICKER_PICKER_HEIGHT = 320.dp

/** Kolik samolepek je v řádku trendu vidět. Pět se vejde na šířku telefonu. */
private const val TRENDING_COVERS = 5

private val TRENDING_COVER_SIZE = 56.dp

/** Buňka mřížky. Na telefonu pět sloupců, jako v Telegramu. */
internal val STICKER_CELL = 76.dp

/** Kolikrát je vybraný režim (Emoji / Samolepky / GIFy) širší než ostatní — místo na fajfku. */
private const val SELECTED_MODE_WEIGHT = 1.4f
