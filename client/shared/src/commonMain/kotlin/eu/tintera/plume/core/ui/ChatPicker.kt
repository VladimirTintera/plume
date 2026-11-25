package eu.tintera.plume.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.material3.IconButtonDefaults
import org.jetbrains.compose.resources.StringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.forward_empty
import eu.tintera.plume.resources.forward_send
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.search_off_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.adaptive.WindowControlsSpace
import eu.tintera.plume.core.adaptive.WindowControlsEndSpace

/**
 * Skupina řádků výběru: [chats] jako jeden blok zaoblených řádků, nad ním volitelný nadpis.
 *
 * Bez nadpisu je to prostě celý seznam (přeposlání, sdílení). S nadpisy se z výběru stane
 * členěný seznam (nová konverzace: Kontakty / Chaty / Globálně) — týž jazyk, jakým mluví
 * skupiny v seznamu chatů.
 *
 * [key] rozlišuje položky stejného chatu ve dvou sekcích; bez něj by `LazyColumn` spadl na
 * dvou stejných klíčích.
 */
data class PickerSection(
    val key: String,
    val chats: List<ChatSummary>,
    val title: String? = null,
)

/** Hledání nad výběrem: dotaz, jeho změna a nápověda v pilulce. */
data class PickerSearch(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val placeholder: String,
)

/**
 * Výběr víc cílů potvrzený tlačítkem: co je vybrané a jak tlačítko vypadá.
 *
 * Sdílení ze systému a nová konverzace zůstávají na jednom cíli (bez výběru): tam se po výběru
 * pokračuje dál, takže „vyber a potvrď" by byl krok navíc před dalším krokem. Přeposílání
 * vybírá, protože poslat totéž do víc chatů je jeho běžný případ.
 */
@Immutable
data class PickerSelection(
    /** Vybrané cíle. */
    val selected: Set<Long>,
    val onConfirm: () -> Unit,
    /**
     * Tlačítko i bez vybraného — kde výběr upravuje existující množinu (chaty ve složce)
     * a odznačit všechno je platná odpověď, nebo kde prázdný výběr dává smysl (nová skupina
     * bez členů, pozvat jde i odkazem).
     */
    val confirmWhenEmpty: Boolean = false,
    /** Popisek tlačítka odeslání s počtem cílů (`%1$d`) — „Přeposlat (2)", „Poslat (2)". */
    val confirmLabel: StringResource = Res.string.forward_send,
    val confirmIcon: DrawableResource = Res.drawable.forward_24dp,
)

/** Prázdný stav výběru; bez nadpisu a textu platí výchozí „nic nenalezeno". */
data class PickerEmpty(
    val icon: DrawableResource = Res.drawable.search_off_24dp,
    val title: String? = null,
    val text: String? = null,
)

/** Chyba, kterou výběr ohlásí snackbarem; po ohlášení zavolá [onShown]. */
@Immutable
data class PickerError(
    val text: UiText,
    val onShown: () -> Unit,
)

/**
 * Výběr chatu: hledání a seznam chatů v blocích zaoblených řádků.
 *
 * Sdílí ho **přeposílání, sdílení do aplikace ze systému i zahájení nové konverzace** — všechno
 * je to „vyber chat". Tři vlastní seznamy by znamenaly tři vzhledy pro totéž; dřív to tak bylo
 * a nová konverzace zůstala u `TextField` s podtržením a holých `ListItem`ů na pozadí, zatímco
 * přeposílání už mluvilo jazykem seznamu chatů.
 *
 * **Uložené zprávy ([savedMessagesChatId]) jsou vždy první**, i když v seznamu chatů nejsou
 * (nepřipnuté tam nejsou) — „pošlu si to" je nejčastější cíl. Hledání je najde podle
 * přeloženého názvu, ne podle jména účtu, které nese TDLib.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPicker(
    title: String,
    search: PickerSearch,
    loading: Boolean,
    sections: List<PickerSection>,
    enabled: Boolean,
    onPick: (chatId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** ✕ místo šipky: zavření celé akce, ne návrat o krok (sdílení ze systému). */
    closeIcon: Boolean = false,
    savedMessagesChatId: Long? = null,
    /** Výběr víc cílů s potvrzením; `null` = jeden cíl a ťuknutí ho rovnou použije. */
    selection: PickerSelection? = null,
    /**
     * Dostupnost protějšku pod jménem. Patří tam, kde se vybírá ČLOVĚK (nová konverzace), ne
     * kam se posílá obsah — u přeposílání by „naposledy před hodinou" o cíli nic neříkalo
     * a řádky by kvůli tomu byly vyšší.
     */
    showPresence: Boolean = false,
    empty: PickerEmpty = PickerEmpty(),
    error: PickerError? = null,
    /**
     * Pod hledáním, nad seznamem: co obrazovka k výběru dodává — volby přeposlání, u sdílení
     * karta s tím, co se posílá.
     */
    header: @Composable () -> Unit = {},
    /**
     * Pod seznamem, nad klávesnicí: co se k výběru píše (komentář k přeposlání). Spodní insety
     * (navigační lišta, klávesnice) si lišta dává SAMA — Scaffold jí je nepředá, a seznam
     * i tlačítko se pak drží nad ní.
     */
    bottomBar: @Composable () -> Unit = {},
    /**
     * Snackbar obrazovky. Výchozí stačí na chyby ([error]); vlastní dává ten, kdo potřebuje
     * snackbar s akcí — seznam blokovaných nabízí po odblokování „Vrátit".
     */
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    /*
     * Titulek se šipkou při scrollu dolů odjede a při scrollu nahoru se vrátí — stejně jako
     * hlavička seznamu chatů. Přečte se jednou a pak jen ukusuje ze seznamu.
     *
     * **Pilulka hledání s ním NEODJÍŽDÍ**, i když v seznamu chatů ano. Tam je totiž pilulka
     * sám ten bar a hlavní činnost je čtení seznamu; tady je hledání to hlavní — chatů jsou
     * stovky a ten hledaný v první obrazovce většinou není. Zmizelé pole přesně ve chvíli,
     * kdy ho uživatel potřebuje, je gesto navíc za nic.
     */
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val errorText = error?.text?.resolve()
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            error?.onShown()
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // I klávesnice, ne jen systémové lišty: podle spodního insetu staví Scaffold tlačítko
        // a snackbar, a vysunutá klávesnice by odesílací tlačítko schovala pod sebe.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    WindowControlsSpace {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            if (closeIcon) {
                                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
                            } else {
                                Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                            }
                        }
                    }
                },
                // Sbalený bar NEMĚNÍ barvu. Výchozí `surfaceContainer` nechal pod stavovou
                // lištou tónovaný pruh přes celou šířku — přesně to, kvůli čemu z hlavičky
                // seznamu chatů zmizel velký app bar.
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
                actions = {
                    AccountBadgeAction()
                    WindowControlsEndSpace()
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        floatingActionButton = {
            // Bez vybraného cíle není co odeslat — tlačítko naskočí, až je kam.
            AnimatedVisibility(
                visible = selection != null && (selection.confirmWhenEmpty || selection.selected.isNotEmpty()),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec(), targetScale = 0.8f),
            ) {
                // Bez výběru je tlačítko skryté; za běhu obrazovky se výběr nevypíná.
                if (selection != null) ExtendedFloatingActionButton(onClick = selection.onConfirm) {
                    Icon(painterResource(selection.confirmIcon), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(selection.confirmLabel, selection.selected.size),
                        style = LocalTextStyle.current.tabularNumbers(),
                        // Počet roste a klesá; bez animace by tlačítko poskakovalo. Animuje se
                        // POPISEK, ne tlačítko: `animateContentSize` ořezává na svoje hranice,
                        // takže na tlačítku uřízl stín i zaoblení spodních nároží.
                        modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    )
                }
            }
        },
    ) { padding ->
        /*
         * Spodní inset patří do `contentPadding` seznamu, ne na kontejner. S paddingem na
         * sloupci skončil seznam nad navigační lištou ostrou hranou a poslední chat zůstal
         * useknutý; takhle chaty jedou pod lištu a poslední se nad ni doroluje — tentýž vzor
         * jako seznam chatů a výsledky hledání. Klávesnice je v tom taky (`safeDrawing`).
         */
        val layoutDirection = LocalLayoutDirection.current
        // Bez spodní lišty je to inset `safeDrawing` (i klávesnice), s ní její výška — Scaffold
        // počítá obojí sám.
        val bottomInset = padding.calculateBottomPadding()
        /*
         * Odjíždějící bar smrskne horní padding od Scaffoldu až na nulu, takže by pilulka
         * skončila pod hodinami a ikonami stavové lišty. Drží se proto pod ní: níž než
         * stavová lišta nesjede. Seznam začíná až pod pilulkou, takže pod lištu nic nejede
         * a závoj (jako v seznamu chatů) tu potřeba není.
         */
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            Modifier
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    top = max(padding.calculateTopPadding(), statusBarTop),
                )
                .fillMaxSize(),
        ) {
            // Tatáž pilulka jako v hlavičce seznamu chatů a při hledání v konverzaci — jeden
            // prvek pro hledání v celé aplikaci.
            SearchPill(
                initialQuery = search.query,
                onQueryChange = search.onQueryChange,
                placeholder = search.placeholder,
                clearDescription = stringResource(Res.string.chats_search_clear),
                modifier = Modifier.fillMaxWidth().padding(horizontal = PICKER_HORIZONTAL, vertical = 8.dp),
            )

            /*
             * Co obrazovka k výběru dodává (volby přeposlání, karta „co se sdílí"), je OBSAH
             * úlohy, ne chrome: patří do seznamu jako jeho první položka a se scrollem odjede.
             * V načítání a v prázdném stavu žádný seznam není, takže se kreslí tady — vypadá
             * to stejně a uživatel se o tom, co sdílí, nedočte jen tehdy, když nic nenašel.
             */
            if (loading) {
                Box(Modifier.padding(horizontal = PICKER_HORIZONTAL)) { header() }
                Box(
                    Modifier.fillMaxSize().padding(bottom = bottomInset),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
                return@Column
            }

            val savedTitle = stringResource(Res.string.saved_messages)
            val showSaved = savedMessagesChatId != null && savedTitle.contains(search.query.trim(), ignoreCase = true)
            // Uložené zprávy kreslí karta nahoře, takže mezi ostatními řádky být nesmí.
            val blocks = sections
                .map { section -> section.copy(chats = section.chats.filter { it.id != savedMessagesChatId }) }
                .filter { it.chats.isNotEmpty() }

            if (blocks.isEmpty() && !showSaved) {
                Box(Modifier.padding(horizontal = PICKER_HORIZONTAL)) { header() }
                // Tentýž prázdný stav jako u hledání v chatech a prázdného archivu.
                IntroEmptyState(
                    icon = empty.icon,
                    title = empty.title ?: stringResource(Res.string.chats_search_empty),
                    text = empty.text ?: stringResource(Res.string.forward_empty),
                    containerColor = Color.Transparent,
                    // Bez toho by se prázdný stav vystředil do plochy pod lištou, tedy níž.
                    modifier = Modifier.fillMaxSize().padding(bottom = bottomInset),
                )
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = PICKER_HORIZONTAL,
                    end = PICKER_HORIZONTAL,
                    top = 8.dp,
                    // Tlačítko leží na seznamu, takže poslední chat musí jít doscrollovat nad ně —
                    // jinak zůstane jeho zaškrtávátko schované pod tlačítkem.
                    bottom = 8.dp + bottomInset + if (selection != null) FAB_CLEARANCE else 0.dp,
                ),
                // Rozestup UVNITŘ bloku; mezeru mezi bloky si nese jejich první položka.
                verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Mezeru pod sebou si hlavička nese sama — prázdná (přeposlání bez voleb,
                // sdílení bez textu) nesmí nechat mezeru po ničem.
                item(key = "picker-header") { header() }
                if (showSaved && savedMessagesChatId != null) {
                    /*
                     * Uložené zprávy jsou samostatná karta nad seznamem, ne jeho první řádek.
                     * „Pošlu si to" (poznámky, odkazy na později) je nejčastější cíl a v řadě
                     * chatů by splynul s ostatními. Tonální `primaryContainer` ji odliší barvou,
                     * tvar má jako každý jiný blok.
                     */
                    item(key = savedMessagesChatId) {
                        PickerRow(
                            avatar = PickerAvatar(
                                colorId = savedMessagesChatId,
                                fileId = null,
                                shape = CircleShape,
                                isSavedMessages = true,
                            ),
                            title = savedTitle,
                            subtitle = null,
                            checked = selection?.selected?.contains(savedMessagesChatId),
                            enabled = enabled,
                            shape = RoundedCornerShape(GROUP_CORNER),
                            colors = PickerRowColors(
                                container = MaterialTheme.colorScheme.primaryContainer,
                                content = MaterialTheme.colorScheme.onPrimaryContainer,
                                mutedContent = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            onClick = { onPick(savedMessagesChatId) },
                            modifier = Modifier.padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING),
                        )
                    }
                }
                blocks.forEachIndexed { blockIndex, section ->
                    // Mezera před blokem nese jeho první položka (nadpis, nebo první řádek) —
                    // `LazyColumn` o sousedech položky nic neví. Nad prvním blokem se nepřidává:
                    // odstup od hlavičky obrazovky dává `contentPadding`.
                    val blockSpacing = if (blockIndex == 0) 0.dp else GROUP_SPACING - GROUP_ITEM_SPACING
                    section.title?.let { sectionTitle ->
                        item(key = "header-${section.key}") {
                            SectionHeader(sectionTitle, Modifier.padding(top = blockSpacing))
                        }
                    }
                    itemsIndexed(section.chats, key = { _, chat -> "${section.key}-${chat.id}" }) { index, chat ->
                        val isSelected = selection?.selected?.contains(chat.id) == true
                        PickerRow(
                            avatar = PickerAvatar(
                                colorId = chat.avatarColorId,
                                fileId = chat.avatarFileId,
                                shape = chat.kind.avatarShape(),
                                isSavedMessages = false,
                            ),
                            title = chat.title,
                            secret = chat.isSecret,
                            subtitle = if (showPresence) chat.presence else null,
                            checked = selection?.selected?.contains(chat.id),
                            enabled = enabled,
                            // Krajní položky bloku nesou jeho zaoblení; vybraný cíl je zaoblený celý,
                            // takže se z bloku vyčlení i tvarem — jako vybraný chat v seznamu.
                            shape = groupItemShape(index, section.chats.size, selected = isSelected),
                            colors = PickerRowColors(
                                container = if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.groupRow
                                },
                                content = if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                mutedContent = if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                            onClick = { onPick(chat.id) },
                            // Mezeru nad blokem nese jeho první řádek jen tehdy, když blok nemá
                            // nadpis — jinak už ji nese ten.
                            modifier = Modifier
                                .padding(top = if (index == 0 && section.title == null) blockSpacing else 0.dp)
                                .animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/** Nadpis bloku — tentýž vzhled jako sekce ve výsledcích hledání v seznamu chatů. */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Avatar řádku výběru: barva a fotka chatu, tvar podle druhu chatu, záložka Uložených zpráv. */
private data class PickerAvatar(
    val colorId: Long,
    val fileId: Int?,
    val shape: Shape,
    val isSavedMessages: Boolean,
)

/** Barvy řádku výběru: plocha, popředí a ztlumené popředí podtitulu. */
private data class PickerRowColors(
    val container: Color,
    val content: Color,
    /**
     * Popředí podtitulu. `onSurfaceVariant` je počítaná na `surface` role, takže na vybraném
     * `secondaryContainer` i na kartě Uložených zpráv patří jejich vlastní `on*` — tatáž past
     * jako u času a ikon u vybraného chatu v seznamu.
     */
    val mutedContent: Color,
)

/**
 * Řádek výběru: blok zaoblených řádků jako seznam chatů, ne holý `ListItem` na pozadí.
 *
 * **Vybraný cíl nese `secondaryContainer`** — výběr je v M3 selekční stav, tatáž role jako
 * u vybraného chatu v dvoupanelu. Zaškrtávátko samo by u jediné vybrané položky nestačilo:
 * v dlouhém seznamu se hledá očima, a barva se najde dřív než políčko na konci řádku.
 *
 * **Klikání nese celý řádek, zaškrtávátko má `onCheckedChange = null`** — do políčka se nemá
 * trefovat a čtečka obrazovky má číst jednu položku, ne dvě.
 */
@Composable
private fun PickerRow(
    avatar: PickerAvatar,
    title: String,
    /** Tajný chat — zámek před jménem, jinak by splynul s obyčejným chatem téhož člověka. */
    secret: Boolean = false,
    /** Dostupnost protějšku pod jménem; `null` = řádek je jen jméno. */
    subtitle: Presence?,
    /** `null` = vybírá se jeden cíl a ťuknutí ho rovnou použije, takže políčko nemá co ukázat. */
    checked: Boolean?,
    enabled: Boolean,
    shape: Shape,
    colors: PickerRowColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(
                chatId = avatar.colorId,
                title = title,
                avatarFileId = avatar.fileId,
                size = AVATAR_SIZE,
                shape = avatar.shape,
                isSavedMessages = avatar.isSavedMessages,
                // Záložka Uložených zpráv je `primary`; na tónované kartě by s ní splynula,
                // takže `Avatar` musí vědět, na čem leží.
                backdrop = colors.container,
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (secret) SecretChatLock()
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                subtitle?.let { presence ->
                    Text(
                        text = presence.describe(),
                        style = MaterialTheme.typography.bodySmall,
                        // Zelená u „online" musí být čitelná na TÉHLE ploše: `primary` na
                        // tónované kartě je v paletě se zvýšeným kontrastem k nerozeznání,
                        // takže ustoupí ztlumenému popředí řádku.
                        color = if (presence is Presence.Online) {
                            accentOn(colors.container, MaterialTheme.colorScheme.primary, colors.mutedContent)
                        } else {
                            colors.mutedContent
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            checked?.let {
                Checkbox(
                    checked = it,
                    onCheckedChange = null,
                    // Políčko na tónované kartě nese popředí KARTY, ne `primary`: v paletě se
                    // zvýšeným kontrastem je `primaryContainer` v tmavém režimu světlý jako
                    // `primary` a plné políčko by s kartou splynulo.
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.content,
                        checkmarkColor = colors.container,
                        uncheckedColor = colors.content,
                    ),
                )
            }
        }
    }
}

/**
 * Odstup obsahu od krajů. Jedna hodnota pro pilulku, řádky i hlavičku — ta leží jednou
 * v seznamu (kde ji dává `contentPadding`) a jednou nad ním, a dvě konstanty by se rozešly.
 */
private val PICKER_HORIZONTAL = 16.dp

/** Tvář chatu ve výběru — jako v seznamu chatů. */
private val AVATAR_SIZE = 40.dp

/** Výška odesílacího tlačítka i s odsazením, aby pod ním nezůstal schovaný poslední chat. */
private val FAB_CLEARANCE = 88.dp
