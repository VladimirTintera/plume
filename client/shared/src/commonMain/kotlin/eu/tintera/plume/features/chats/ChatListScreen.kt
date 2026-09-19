package eu.tintera.plume.features.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatActivity
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_draft_prefix
import eu.tintera.plume.resources.chats_empty
import eu.tintera.plume.resources.chats_muted
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.nav_chats
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatListScreen(
    viewModel: ChatsViewModel,
    selectedChatId: Long?,
    onChatClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val separateUnread by viewModel.separateUnreadChats.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)
    val snackbarHostState = remember { SnackbarHostState() }

    // Bez tohohle by neúspěšná akce (typicky překročený limit připnutých chatů) zmizela beze stopy
    // a vypadalo by to, že se nic nestalo.
    LaunchedEffect(actions.error) {
        val message = actions.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(getString(message.resource, *message.args.toTypedArray()))
        viewModel.dismissError()
    }

    actions.target?.let { chat ->
        ChatActionsSheet(
            chat = chat,
            onTogglePinned = { viewModel.togglePinned(chat) },
            onToggleMuted = { viewModel.toggleMuted(chat) },
            onToggleUnread = { viewModel.toggleUnread(chat) },
            onDelete = { viewModel.askDelete(chat) },
            onDismiss = viewModel::hideActions,
        )
    }

    actions.pendingDelete?.let { chat ->
        ChatDeleteDialog(
            chat = chat,
            onConfirm = { viewModel.confirmDelete(chat) },
            onDismiss = viewModel::cancelDelete,
        )
    }

    // Dotažení dalších chatů, jakmile se uživatel blíží ke konci. TDLib pak pošle updaty a seznam
    // se doplní sám — proto se tu nečeká na návratovou hodnotu.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .collect { if (it) viewModel.loadMore() }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Expressive „flexible" top bar — kolabuje při scrollu a mění výšku podle motion schématu.
            LargeFlexibleTopAppBar(
                title = {
                    /*
                     * Stav spojení NAHRAZUJE název sekce, nemá vlastní pruh ani odznak. Kdo se
                     * dívá na prázdný seznam, hledá vysvětlení v hlavičce — a „Chaty" žádné
                     * nedává. Stejně to řeší oficiální klient.
                     */
                    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

                    AnimatedContent(
                        targetState = connection.label() ?: Res.string.nav_chats,
                        transitionSpec = { fadeIn(effects) togetherWith fadeOut(fastEffects) },
                        contentAlignment = Alignment.CenterStart,
                        label = "chats-title",
                    ) { title -> Text(stringResource(title)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when (val current = state) {
            ChatListState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }

            is ChatListState.Failed -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.message.resolve(), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::loadMore) { Text(stringResource(Res.string.common_try_again)) }
                }
            }

            is ChatListState.Ready -> if (current.chats.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(Res.string.chats_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        // Kotva i rozestup za ní jdou z horního odsazení, první chat tak
                        // zůstane tam, kde byl bez ní.
                        top = padding.calculateTopPadding() + 8.dp - TOP_ANCHOR_HEIGHT -
                            GROUP_ITEM_SPACING,
                        bottom = padding.calculateBottomPadding() + 8.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                    // Rozestup UVNITŘ skupiny. Mezera mezi skupinami se přidává u její první
                    // položky — seznam o sousedech položky nic neví.
                    verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                ) {
                    val groups = chatGroups(current.chats, separateUnread)

                    /*
                     * Kotva nahoře. `LazyColumn` drží pozici podle klíče první viditelné
                     * položky; bez kotvy je to první chat, a když nad něj vyskočí jiný (nová
                     * zpráva, připnutí), seznam jde za tím původním a nový skončí nad horní
                     * hranou. S kotvou je první viditelná položka ona, takže seznam u vršku
                     * zůstane u vršku. Výška nesmí být nulová — položku bez výšky seznam
                     * za viditelnou nepovažuje.
                     */
                    item(key = TOP_ANCHOR_KEY, contentType = TOP_ANCHOR_KEY) {
                        Spacer(Modifier.fillMaxWidth().height(TOP_ANCHOR_HEIGHT))
                    }

                    groups.forEachIndexed { groupIndex, group ->
                        itemsIndexed(group, key = { _, chat -> chat.id }) { index, chat ->
                            ChatRow(
                                chat = chat,
                                selected = chat.id == selectedChatId,
                                // Krajní položky skupiny nesou její zaoblení, prostřední
                                // se drží u sebe.
                                firstInGroup = index == 0,
                                lastInGroup = index == group.lastIndex,
                                onClick = { onChatClick(chat.id) },
                                onLongClick = { viewModel.showActions(chat) },
                                // Animace přeskládání při změně pořadí — bez toho by chaty
                                // při nové zprávě skákaly.
                                modifier = Modifier
                                    .animateItem()
                                    .padding(
                                        top = if (index == 0 && groupIndex > 0) GROUP_SPACING
                                        else 0.dp,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatRow(
    chat: ChatSummary,
    selected: Boolean,
    firstInGroup: Boolean,
    lastInGroup: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * Blok je SVĚTLEJŠÍ než pozadí, ne tmavší.
     *
     * Původně to bylo obráceně — pozadí `surface` (nejsvětlejší role) a řádky `surfaceContainerHighest`
     * o tři stupně tmavší. Z položek pak byly šedé plochy na světlém a seznam působil těžce.
     * Takhle je to vztah „světlý panel na tónované stránce", což je i to, čím odstup dělá
     * Google Messages: jeho řádky samy žádnou výplň nemají.
     *
     * Výběr v list-detail je navigační stav, ne zvýraznění obsahu → `secondaryContainer`,
     * stejná role, jakou používá vybraná položka v nav railu.
     */
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    /*
     * Zaoblení dělá ze sousedních položek jeden blok: velké nahoře u první, velké dole
     * u poslední, malé mezi nimi. Animuje se, protože skupiny se přeskládávají za běhu —
     * přečtený chat přeskočí z nepřečtených mezi ostatní a tvary sousedů se tím mění.
     */
    val topCorner by animateDpAsState(
        // Vybraná položka se z bloku vyčlení: zaoblená celá, jako skupina sama (M3 Expressive).
        targetValue = if (firstInGroup || selected) GROUP_CORNER else INNER_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chat-corner-top",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (lastInGroup || selected) GROUP_CORNER else INNER_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chat-corner-bottom",
    )

    val shape = RoundedCornerShape(
        topStart = topCorner,
        topEnd = topCorner,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner,
    )

    // `Card(onClick = …)` umí jen krátké ťuknutí. Dlouhý stisk potřebuje `combinedClickable`,
    // takže se klikání přesouvá na Surface — Card zůstává jen tvarem a barvou.
    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box {
                Avatar(
                    chatId = chat.id,
                    title = chat.title,
                    avatarFileId = chat.avatarFileId,
                    size = AVATAR_SIZE,
                    modifier = Modifier.sharedNavElement(chatAvatarKey(chat.id)),
                )

                // Tečka sedí NA avataru, ne vedle jména: v seznamu se očima jede po levém
                // sloupci, takže se dostupnost přečte spolu s tím, kdo to je.
                if (chat.presence is Presence.Online) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(ONLINE_DOT)
                            // Obroučka barvou pozadí položky — bez ní tečka splyne s tmavým
                            // avatarem a je z ní jen skvrna.
                            .background(containerColor, CircleShape)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (chat.isPinned) {
                        Icon(
                            painterResource(Res.drawable.push_pin_filled_24dp),
                            contentDescription = stringResource(Res.string.chats_section_pinned),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = chat.title,
                        // `bodyLarge` místo `titleMedium`: stejná velikost, ale běžný řez.
                        // Polotučné jméno u KAŽDÉHO chatu znamená, že nevyčnívá žádný —
                        // tučností se proto odlišuje jen nepřečtený, jako v Google Messages.
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (chat.hasUnread) FontWeight.SemiBold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Plná váha: název roztlačí zbytek řádku doprava, takže čas sedí
                        // na konci a nelepí se na jméno.
                        modifier = Modifier.weight(1f).sharedNavText(chatTitleKey(chat.id)),
                    )

                    if (chat.isMuted) {
                        Icon(
                            painterResource(Res.drawable.notifications_off_24dp),
                            contentDescription = stringResource(Res.string.chats_muted),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Čas je zarovnaný doprava na konec řádku s názvem, ne pod něj — jinak by
                    // soupeřil o místo s náhledem zprávy, který bývá dlouhý.
                    // Koncept posouvá chat v seznamu nahoru, takže čas musí jít s ním — jinak by
                    // u chatu nahoře stálo datum starší než u těch pod ním.
                    listOfNotNull(chat.lastMessageTimestamp, chat.draft?.timestamp).maxOrNull()?.let { timestamp ->
                        Text(
                            text = timestamp.asRelativeTime(),
                            // Časy stojí pod sebou u pravého okraje — stejně široké číslice
                            // je drží v zákrytu.
                            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                /*
                 * Činnost > koncept > náhled poslední zprávy.
                 *
                 * „Píše…" je nejčerstvější informace. Koncept je to, co uživatel v chatu nechal
                 * rozdělané, a hledá ho spíš než poslední cizí zprávu. Obojí najednou se do
                 * řádku nevejde.
                 */
                val draftLabelColor = MaterialTheme.colorScheme.tertiary
                val subtitle: AnnotatedString? = chat.activity?.label()?.let { AnnotatedString(stringResource(it)) }
                    ?: chat.draft?.let { draft ->
                        buildAnnotatedString {
                            // Štítek jinou barvou než „píše…" (primary) — jde o náš text, ne o dění.
                            withStyle(SpanStyle(color = draftLabelColor)) { append(stringResource(Res.string.chats_draft_prefix)) }
                            // Zalomení by v jednořádkovém náhledu useklo text hned na začátku.
                            append(draft.text.replace('\n', ' '))
                        }
                    }
                    ?: chat.lastMessagePreview?.let(::AnnotatedString)

                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        // Nepřečtený náhled se odliší barvou, ne řezem: s polotučným názvem nad
                        // ním by byly na jednom řádku dvě úrovně tučnosti a blok by ztěžkl.
                        color = when {
                            // Činnost se odliší barvou, aby bylo poznat, že to není text zprávy.
                            chat.activity != null -> MaterialTheme.colorScheme.primary
                            selected -> MaterialTheme.colorScheme.onSecondaryContainer
                            chat.hasUnread -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (chat.unreadCount > 0) {
                // `Badge` je ve výchozím stavu chybově červený. Nepřečtená zpráva ale není
                // chyba — v odlehčeném seznamu byla ta červená jediná sytá barva na obrazovce
                // a přebíjela všechno ostatní.
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(chat.unreadCount.toString(), style = LocalTextStyle.current.tabularNumbers())
                }
            }
        }
    }
}

/** Kolik položek před koncem už spustí dotažení další dávky. */
private const val LOAD_MORE_THRESHOLD = 5

/** Zaoblení na krajích skupiny. */
private val GROUP_CORNER = 20.dp

/** Zaoblení mezi sousedy uvnitř skupiny — tak akorát, aby byly rozeznatelné, ne oddělené. */
private val INNER_CORNER = 4.dp

/** Rozestup mezi položkami jedné skupiny. */
private val GROUP_ITEM_SPACING = 2.dp

/** Mezera mezi skupinami. */
private val GROUP_SPACING = 14.dp

/** Klíč neviditelné kotvy nad prvním chatem. */
private const val TOP_ANCHOR_KEY = "top-anchor"

/** Výška kotvy — nejmenší, se kterou ji seznam ještě počítá za viditelnou. */
private val TOP_ANCHOR_HEIGHT = 1.dp

/** Avatar. Větší než výchozích 48 dp — v odlehčeném řádku nese hlavní váhu on, ne písmo. */
private val AVATAR_SIZE = 52.dp

/** Průměr zelené tečky i s obroučkou. */
private val ONLINE_DOT = 14.dp

