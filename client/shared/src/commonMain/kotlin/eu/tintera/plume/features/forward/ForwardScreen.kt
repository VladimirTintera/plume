package eu.tintera.plume.features.forward

import eu.tintera.plume.core.ui.avatarShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.SearchPill
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.search_off_24dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import eu.tintera.plume.core.telegram.ForwardCapabilities
import eu.tintera.plume.core.telegram.ForwardOptions
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.forward_hide_captions
import eu.tintera.plume.resources.forward_hide_sender
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LocalTextStyle
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.forward_send
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.forward_empty
import eu.tintera.plume.resources.forward_search
import eu.tintera.plume.resources.forward_title
import eu.tintera.plume.resources.saved_messages
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kam přeposlat: hledání a seznam chatů.
 *
 * Obyčejná obrazovka, ne bottom sheet — seznam chatů je dlouhý a listuje se v něm; sheet by
 * navíc při hledání zápasil s klávesnicí. Po odeslání se otevře cílový chat, takže je hned vidět,
 * že zpráva dorazila.
 */
@Composable
fun ForwardScreen(
    viewModel: ForwardViewModel,
    onBack: () -> Unit,
    onForwarded: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatPicker(
        title = stringResource(Res.string.forward_title),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        loading = state.loading,
        chats = state.chats,
        // Dokud odesílání běží, další ťuknutí by poslalo zprávu dvakrát.
        enabled = !state.sending,
        onPick = viewModel::toggle,
        // Přeposílá se do víc chatů naráz, takže ťuknutí cíl VYBERE a odesílá se až tlačítkem.
        selected = state.selected,
        onConfirm = { viewModel.send(onForwarded) },
        savedMessagesChatId = state.savedMessagesChatId,
        onBack = onBack,
        error = state.error,
        onErrorShown = viewModel::consumeError,
        header = {
            ForwardOptionsRow(
                capabilities = state.capabilities,
                options = state.options,
                enabled = !state.sending,
                onHideSender = viewModel::setHideSender,
                onHideCaptions = viewModel::setHideCaptions,
            )
        },
        modifier = modifier,
    )
}

/**
 * Jak přeposlat: „Skrýt odesílatele" (kopie bez „Přeposláno od …") a s ní „Skrýt popisky".
 *
 * Dvě NEZÁVISLÁ přepínací tlačítka, ne connected group — ta je pro výběr z jedné sady (druh
 * chatu v hledání), tady jsou to dvě volby, z nichž druhá platí jen s první. Proto se druhá
 * ukáže, až je první zapnutá, a jen když zprávy nějaký popisek nesou.
 *
 * Co server nedovolí (`canBeCopied`), se nenabízí vůbec — stejně jako položky nabídky nad zprávou.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ForwardOptionsRow(
    capabilities: ForwardCapabilities,
    options: ForwardOptions,
    enabled: Boolean,
    onHideSender: (Boolean) -> Unit,
    onHideCaptions: (Boolean) -> Unit,
) {
    AnimatedVisibility(
        visible = capabilities.canHideSender,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) + shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            OptionToggle(
                checked = options.hideSender,
                onCheckedChange = onHideSender,
                enabled = enabled,
                label = stringResource(Res.string.forward_hide_sender),
            )
            AnimatedVisibility(
                visible = options.hideSender && capabilities.hasCaptions,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) + shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                OptionToggle(
                    checked = options.hideCaptions,
                    onCheckedChange = onHideCaptions,
                    enabled = enabled,
                    label = stringResource(Res.string.forward_hide_captions),
                )
            }
        }
    }
}

/** Přepínací tlačítko s fajfkou, když je zapnuté — jako filtry hledání zpráv. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean, label: String) {
    ToggleButton(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) {
        AnimatedVisibility(visible = checked) {
            Icon(
                painterResource(Res.drawable.check_24dp),
                contentDescription = null,
                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
            )
        }
        Text(label)
    }
}

/**
 * Výběr chatu: hledání nad načteným seznamem a seznam chatů. Sdílí ho přeposílání a sdílení
 * do aplikace ze systému — obojí je „vyber, kam s tím".
 *
 * [subtitle] je pod hledáním: co se sdílí, nebo že se to ještě připravuje.
 *
 * **Uložené zprávy ([savedMessagesChatId]) jsou vždy první**, i když v seznamu chatů nejsou
 * (nepřipnuté tam nejsou) — „pošlu si to" je nejčastější cíl. Hledání je najde podle
 * přeloženého názvu, ne podle jména účtu, které nese TDLib.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPicker(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    chats: List<ChatSummary>,
    enabled: Boolean,
    onPick: (chatId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** ✕ místo šipky: zavření celé akce, ne návrat o krok (sdílení ze systému). */
    closeIcon: Boolean = false,
    subtitle: String? = null,
    savedMessagesChatId: Long? = null,
    /**
     * Vybrané cíle; `null` = jeden cíl a ťuknutí ho rovnou použije.
     *
     * Sdílení ze systému zůstává na jednom cíli: obsah tam čeká nad vstupním polem, takže
     * „vyber a potvrď" by byl krok navíc před dalším krokem. Přeposílání vybírá, protože
     * poslat totéž do víc chatů je jeho běžný případ.
     */
    selected: Set<Long>? = null,
    onConfirm: () -> Unit = {},
    error: UiText? = null,
    onErrorShown: () -> Unit = {},
    /** Pod hledáním, nad seznamem: volby akce (jak přeposlat). */
    header: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val errorText = error?.resolve()
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        // I klávesnice, ne jen systémové lišty: podle spodního insetu staví Scaffold tlačítko
        // a snackbar, a vysunutá klávesnice by odesílací tlačítko schovala pod sebe.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        if (closeIcon) {
                            Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
                        } else {
                            Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Bez vybraného cíle není co odeslat — tlačítko naskočí, až je kam.
            AnimatedVisibility(
                visible = !selected.isNullOrEmpty(),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec(), targetScale = 0.8f),
            ) {
                ExtendedFloatingActionButton(onClick = onConfirm) {
                    Icon(painterResource(Res.drawable.forward_24dp), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(Res.string.forward_send, selected?.size ?: 0),
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
        val bottomInset = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
        Column(
            Modifier
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                )
                .fillMaxSize(),
        ) {
            // Tatáž pilulka jako v hlavičce seznamu chatů a při hledání v konverzaci — jeden
            // prvek pro hledání v celé aplikaci.
            SearchPill(
                initialQuery = query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(Res.string.forward_search),
                clearDescription = stringResource(Res.string.chats_search_clear),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            header()

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (loading) {
                Box(
                    Modifier.fillMaxSize().padding(bottom = bottomInset),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
                return@Column
            }

            val savedTitle = stringResource(Res.string.saved_messages)
            val showSaved = savedMessagesChatId != null && savedTitle.contains(query.trim(), ignoreCase = true)
            val others = chats.filter { it.id != savedMessagesChatId }

            if (others.isEmpty() && !showSaved) {
                // Tentýž prázdný stav jako u hledání v chatech a prázdného archivu.
                IntroEmptyState(
                    icon = Res.drawable.search_off_24dp,
                    title = stringResource(Res.string.chats_search_empty),
                    text = stringResource(Res.string.forward_empty),
                    containerColor = Color.Transparent,
                    // Bez toho by se prázdný stav vystředil do plochy pod lištou, tedy níž.
                    modifier = Modifier.fillMaxSize().padding(bottom = bottomInset),
                )
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    // Tlačítko leží na seznamu, takže poslední chat musí jít doscrollovat nad ně —
                    // jinak zůstane jeho zaškrtávátko schované pod tlačítkem.
                    bottom = 8.dp + bottomInset + if (selected != null) FAB_CLEARANCE else 0.dp,
                ),
                // Rozestup UVNITŘ bloku; mezeru mezi bloky si nese karta Uložených zpráv.
                verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showSaved && savedMessagesChatId != null) {
                    /*
                     * Uložené zprávy jsou samostatná karta nad seznamem, ne jeho první řádek.
                     * „Pošlu si to" (poznámky, odkazy na později) je nejčastější cíl a v řadě
                     * chatů by splynul s ostatními. Tonální `primaryContainer` ji odliší barvou,
                     * tvar má jako každý jiný blok.
                     */
                    item(key = savedMessagesChatId) {
                        PickerRow(
                            chatId = savedMessagesChatId,
                            title = savedTitle,
                            avatarFileId = null,
                            avatarShape = CircleShape,
                            isSavedMessages = true,
                            checked = selected?.contains(savedMessagesChatId),
                            enabled = enabled,
                            shape = RoundedCornerShape(GROUP_CORNER),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { onPick(savedMessagesChatId) },
                            modifier = Modifier.padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING),
                        )
                    }
                }
                itemsIndexed(others, key = { _, chat -> chat.id }) { index, chat ->
                    val isSelected = selected?.contains(chat.id) == true
                    PickerRow(
                        chatId = chat.id,
                        title = chat.title,
                        avatarFileId = chat.avatarFileId,
                        avatarShape = chat.kind.avatarShape(),
                        isSavedMessages = false,
                        checked = selected?.contains(chat.id),
                        enabled = enabled,
                        // Krajní položky bloku nesou jeho zaoblení; vybraný cíl je zaoblený celý,
                        // takže se z bloku vyčlení i tvarem — jako vybraný chat v seznamu.
                        shape = groupItemShape(index, others.size, selected = isSelected),
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        onClick = { onPick(chat.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

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
    chatId: Long,
    title: String,
    avatarFileId: Int?,
    avatarShape: Shape,
    isSavedMessages: Boolean,
    /** `null` = vybírá se jeden cíl a ťuknutí ho rovnou použije, takže políčko nemá co ukázat. */
    checked: Boolean?,
    enabled: Boolean,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(
                chatId = chatId,
                title = title,
                avatarFileId = avatarFileId,
                size = AVATAR_SIZE,
                shape = avatarShape,
                isSavedMessages = isSavedMessages,
                // Záložka Uložených zpráv je `primary`; na tónované kartě by s ní splynula,
                // takže `Avatar` musí vědět, na čem leží.
                backdrop = containerColor,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            checked?.let {
                Checkbox(
                    checked = it,
                    onCheckedChange = null,
                    // Políčko na tónované kartě nese popředí KARTY, ne `primary`: v paletě se
                    // zvýšeným kontrastem je `primaryContainer` v tmavém režimu světlý jako
                    // `primary` a plné políčko by s kartou splynulo.
                    colors = CheckboxDefaults.colors(
                        checkedColor = contentColor,
                        checkmarkColor = containerColor,
                        uncheckedColor = contentColor,
                    ),
                )
            }
        }
    }
}

/** Tvář chatu ve výběru — jako v seznamu chatů. */
private val AVATAR_SIZE = 40.dp

/** Výška odesílacího tlačítka i s odsazením, aby pod ním nezůstal schovaný poslední chat. */
private val FAB_CLEARANCE = 88.dp
