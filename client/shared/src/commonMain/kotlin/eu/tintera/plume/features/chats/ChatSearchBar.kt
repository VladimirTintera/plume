package eu.tintera.plume.features.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chats_search
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.close_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Hlavička seznamu chatů: vyhledávací pilulka a vedle ní [actions].
 *
 * Nahradila `LargeFlexibleTopAppBar`. Ten byl hezký jen rozbalený: sbalený přešel na
 * `surfaceContainer`, tedy pruh přes celou šířku v roli, která jinde v seznamu není, a na
 * rozloženém Foldu stál vedle 64dp hlavičky konverzace bar jiné výšky i barvy. Pilulka je
 * zaoblený blok jako skupiny chatů, bar kolem ní má barvu stránky i po odscrollování a je
 * vysoký přesně jako hlavička konverzace.
 *
 * **Při scrollu dolů bar odjede ([scrollBehavior], `enterAlways`), ale stav spojení ho přivolá
 * zpátky.** Odjetý bar by jinak schoval jediné vysvětlení, proč nic nechodí. Pás pod stavovou
 * lištou kreslí seznam chatů.
 *
 * **Stav spojení je pilulka UVNITŘ hledání** ([connection]), ne nadpis. Skoro celou dobu je
 * `null` a pak není vidět nic; nadpis „Chaty" by jen opakoval, co říká navigace.
 *
 * Hledá se zatím jen v načteném seznamu ([chats]), jako při přeposílání. Uložené zprávy se
 * najdou podle přeloženého názvu, i když v seznamu nejsou.
 */
@Composable
internal fun ChatSearchBar(
    connection: StringResource?,
    chats: List<ChatSummary>,
    savedMessagesChatId: Long?,
    scrollBehavior: SearchBarScrollBehavior,
    onChatClick: (Long) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val expanded = searchBarState.currentValue == SearchBarValue.Expanded

    fun collapse() {
        scope.launch {
            searchBarState.animateToCollapsed()
        }
    }

    // Bar má barvu stránky i nad odscrollovaným seznamem — tónovaný pruh přes celou šířku byl
    // to, co vadilo na sbaleném velkém baru. Odstup od obsahu nese pilulka.
    val colors = SearchBarDefaults.appBarWithSearchColors(
        scrolledAppBarContainerColor = MaterialTheme.colorScheme.surface,
    )

    // Výpadek spojení bar vrátí, i když je odjetý.
    LaunchedEffect(connection != null) {
        if (connection != null && scrollBehavior.scrollOffset != 0f) {
            animate(scrollBehavior.scrollOffset, 0f, animationSpec = spatial) { value, _ ->
                scrollBehavior.scrollOffset = value
            }
        }
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = {},
            colors = colors.searchBarColors.inputFieldColors,
            placeholder = {
                Text(
                    stringResource(Res.string.chats_search),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                if (expanded) {
                    IconButton(onClick = ::collapse) {
                        Icon(
                            painterResource(Res.drawable.arrow_back_24dp),
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                } else {
                    Icon(painterResource(Res.drawable.search_24dp), contentDescription = null)
                }
            },
            trailingIcon = {
                when {
                    expanded && textFieldState.text.isNotEmpty() -> IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(
                            painterResource(Res.drawable.close_24dp),
                            contentDescription = stringResource(Res.string.chats_search_clear),
                        )
                    }
                    // V rozbaleném hledání stav nepatří — uživatel se dívá na výsledky.
                    !expanded -> ConnectionPill(connection)
                }
            },
        )
    }

    // Hledání zavřené gestem zpět jde mimo `collapse()`; dotaz by jinak zůstal v pilulce.
    LaunchedEffect(expanded) {
        if (!expanded) textFieldState.clearText()
    }

    AppBarWithSearch(
        state = searchBarState,
        inputField = inputField,
        colors = colors,
        // Barva stránky výslovně. Bar ji má mít sám (`appBarContainerColor`), ale bez scroll
        // behavior ji v téhle alphě nenakreslil a chaty pod ním prosvítaly.
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        scrollBehavior = scrollBehavior,
        actions = actions,
    )

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField,
        colors = colors.searchBarColors,
    ) {
        SearchResults(
            query = textFieldState.text.toString(),
            chats = chats,
            savedMessagesChatId = savedMessagesChatId,
            onPick = { chatId ->
                collapse()
                onChatClick(chatId)
            },
        )
    }
}

/**
 * Stav spojení jako tonální pilulka na konci hledání.
 *
 * Při odjezdu kreslí poslední známý text — v okamžiku skrytí je hodnota už `null` a
 * `AnimatedVisibility` by dobíhala s prázdnou pilulkou (tatáž past jako u pruhu připnutých).
 */
@Composable
private fun ConnectionPill(connection: StringResource?) {
    val last = remember { LastValue<StringResource>() }
    connection?.let { last.value = it }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    AnimatedVisibility(
        visible = connection != null,
        enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.8f),
        exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.8f),
    ) {
        val label = last.value ?: return@AnimatedVisibility
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Obyčejné pole, ne stav — čte se jen při skládání obsahu, který se kvůli němu nepřekresluje. */
private class LastValue<T> {
    var value: T? = null
}

@Composable
private fun SearchResults(
    query: String,
    chats: List<ChatSummary>,
    savedMessagesChatId: Long?,
    onPick: (Long) -> Unit,
) {
    val trimmed = query.trim()
    // Prázdné hledání neukazuje nic: celý seznam je hned pod ním a opakovat ho nemá smysl.
    if (trimmed.isEmpty()) return

    val savedTitle = stringResource(Res.string.saved_messages)
    val showSaved = savedMessagesChatId != null && savedTitle.contains(trimmed, ignoreCase = true)
    val matches = remember(chats, trimmed, savedMessagesChatId) {
        chats.filter { it.id != savedMessagesChatId && it.title.contains(trimmed, ignoreCase = true) }
    }
    val onPickLatest by rememberUpdatedState(onPick)

    if (matches.isEmpty() && !showSaved) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                stringResource(Res.string.chats_search_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (showSaved && savedMessagesChatId != null) {
            item(key = savedMessagesChatId) {
                ListItem(
                    headlineContent = { Text(savedTitle) },
                    leadingContent = {
                        Avatar(chatId = savedMessagesChatId, title = savedTitle, avatarFileId = null, size = 40.dp, isSavedMessages = true)
                    },
                    colors = itemColors,
                    modifier = Modifier.clickable { onPickLatest(savedMessagesChatId) },
                )
            }
        }
        items(matches, key = { it.id }) { chat ->
            ListItem(
                headlineContent = { Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    Avatar(chatId = chat.id, title = chat.title, avatarFileId = chat.avatarFileId, size = 40.dp)
                },
                colors = itemColors,
                modifier = Modifier.clickable { onPickLatest(chat.id) },
            )
        }
    }
}
