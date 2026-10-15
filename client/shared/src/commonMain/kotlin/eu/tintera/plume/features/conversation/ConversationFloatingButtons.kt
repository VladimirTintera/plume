package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.UnreadMarkKind
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.RollingNumber
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.alternate_email_24dp
import eu.tintera.plume.resources.arrow_downward_24dp
import eu.tintera.plume.resources.ballot_24dp
import eu.tintera.plume.resources.conversation_mark_read
import eu.tintera.plume.resources.conversation_new_messages
import eu.tintera.plume.resources.conversation_read_all_mentions
import eu.tintera.plume.resources.conversation_read_all_poll_votes
import eu.tintera.plume.resources.conversation_read_all_reactions
import eu.tintera.plume.resources.conversation_scroll_to_end
import eu.tintera.plume.resources.conversation_unread_mentions
import eu.tintera.plume.resources.conversation_unread_poll_votes
import eu.tintera.plume.resources.conversation_unread_reactions
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.favorite_filled_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Tlačítko „dolů", s počtem nových zpráv, když nějaké čekají.
 *
 * Objeví se, když je uživatel v historii — dole se totiž nescrolluje automaticky, aby ho nová
 * zpráva nevytrhla ze čtení. Bez nových zpráv je to jen kulatá šipka; s nimi se roztáhne
 * o počet, protože bez čísla by uživatel nevěděl, jestli se vyplatí skočit dolů.
 */
@Composable
internal fun ScrollToBottomButton(
    visible: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        // Vjezd zespoda: tlačítko přiletí odtamtud, kam po ťuknutí odskočí.
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 4.dp,
        ) {
            Row(
                // Bez počtu je z toho kruh: šířka i výška jsou dané touhle minimální plochou.
                Modifier
                    .heightIn(min = SCROLL_DOWN_BUTTON_SIZE)
                    .widthIn(min = SCROLL_DOWN_BUTTON_SIZE)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.arrow_downward_24dp),
                    contentDescription = if (count > 0) null else stringResource(Res.string.conversation_scroll_to_end),
                    modifier = Modifier.size(18.dp),
                )

                // Mezera je v obsahu, ne ve `spacedBy`: skrytý AnimatedVisibility je pořád potomek
                // a rozestup by kruh bez počtu nafoukl do šišky.
                AnimatedVisibility(
                    visible = count > 0,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    // Při zmizení se musí dál kreslit poslední nenulový počet, ne „0 zpráv".
                    // Obyčejné pole, ne stav: zápis do stavu v kompozici by jen vynutil další.
                    val shown = remember { intArrayOf(count) }
                    if (count > 0) shown[0] = count

                    NewMessagesLabel(shown[0], Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * „Označit jako přečtené" při diskrétním čtení (`AppSettings.discreetReading`). Sourozenec
 * tlačítek nad polem: stejná plocha a stín, jen s popiskem — ikona dvou fajfek sama neřekne,
 * že ťuknutí PROZRADÍ přečtení protějšku.
 */
@Composable
internal fun MarkReadChip(visible: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        // Mezera pod čipem je v obsahu, ne ve `spacedBy` sloupce — viz `UnreadMarkButton`.
        Box(Modifier.padding(bottom = 10.dp)) {
            Surface(
                onClick = {
                    haptics.confirm()
                    onClick()
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = 4.dp,
            ) {
                Row(
                    Modifier.heightIn(min = SCROLL_DOWN_BUTTON_SIZE).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(painterResource(Res.drawable.done_all_24dp), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.conversation_mark_read), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Tlačítko „@" (nepřečtené zmínky) nebo „❤" (nepřečtené reakce na vlastní zprávy) s počtem.
 *
 * Ťuknutí skočí na nejstarší, podržení nabídne „Označit vše jako přečtené" — tak to mají
 * i oficiální klienti. Vzhledem je to sourozenec tlačítka dolů: stejný kruh, stejná role,
 * počet nese odznak v rohu, aby se kruh netáhl do šířky.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UnreadMarkButton(
    kind: UnreadMarkKind,
    count: Int,
    onClick: (UnreadMarkKind) -> Unit,
    onReadAll: (UnreadMarkKind) -> Unit,
) {
    // Při zmizení se musí dál kreslit poslední nenulový počet, ne nula (viz tlačítko dolů).
    val shown = remember { intArrayOf(count) }
    if (count > 0) shown[0] = count

    AnimatedVisibility(
        visible = count > 0,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        var menuOpen by remember { mutableStateOf(false) }
        val label = when (kind) {
            UnreadMarkKind.Mention -> pluralStringResource(Res.plurals.conversation_unread_mentions, shown[0], shown[0])
            UnreadMarkKind.Reaction -> pluralStringResource(Res.plurals.conversation_unread_reactions, shown[0], shown[0])
            UnreadMarkKind.PollVote -> pluralStringResource(Res.plurals.conversation_unread_poll_votes, shown[0], shown[0])
        }
        val readAllLabel = stringResource(
            when (kind) {
                UnreadMarkKind.Mention -> Res.string.conversation_read_all_mentions
                UnreadMarkKind.Reaction -> Res.string.conversation_read_all_reactions
                UnreadMarkKind.PollVote -> Res.string.conversation_read_all_poll_votes
            },
        )

        // Mezera pod tlačítkem je v obsahu, ne ve `spacedBy` sloupce: skryté tlačítko by jinak
        // nechalo nad tlačítkem dolů prázdné místo.
        Box(Modifier.padding(bottom = 10.dp)) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        RollingNumber(shown[0], style = LocalTextStyle.current.tabularNumbers())
                    }
                },
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 4.dp,
                ) {
                    // Klikání uvnitř `Surface`: ten ořezává obsah tvarem, takže vlnka zůstane
                    // v kruhu, a stín, který by `clip` zvenku uřízl, zůstane celý.
                    Box(
                        Modifier
                            .size(SCROLL_DOWN_BUTTON_SIZE)
                            .contextClickable(
                                onClickLabel = label,
                                onLongClickLabel = readAllLabel,
                                onLongClick = { menuOpen = true },
                                onClick = { onClick(kind) },
                            )
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(
                                when (kind) {
                                    UnreadMarkKind.Mention -> Res.drawable.alternate_email_24dp
                                    UnreadMarkKind.Reaction -> Res.drawable.favorite_filled_24dp
                                    UnreadMarkKind.PollVote -> Res.drawable.ballot_24dp
                                },
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            ExpressiveMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                groups = listOf(
                    listOf(MenuAction(label = readAllLabel, icon = Res.drawable.done_all_24dp) { onReadAll(kind) }),
                ),
            )
        }
    }
}

/**
 * „N nových zpráv" s přetáčeným číslem ([RollingNumber]). Skloňování řeší `plurals`
 * v prostředcích (čeština má tři tvary, angličtina dva), takže se věta nesestavuje v kódu:
 * číslo se do ní dosadí jako značka a věta se kolem ní rozdělí. Compose Resources dosazuje
 * argumenty jako text (`replaceWithArgs`), takže značka projde beze změny.
 */
@Composable
private fun NewMessagesLabel(count: Int, modifier: Modifier = Modifier) {
    val template = pluralStringResource(Res.plurals.conversation_new_messages, count, NUMBER_MARK)
    val before = template.substringBefore(NUMBER_MARK)
    val after = template.substringAfter(NUMBER_MARK, missingDelimiterValue = "")
    val style = MaterialTheme.typography.labelLarge
    val label = pluralStringResource(Res.plurals.conversation_new_messages, count, count)
    Row(modifier.clearAndSetSemantics { contentDescription = label }) {
        if (before.isNotEmpty()) Text(before, style = style)
        RollingNumber(count, style = style.tabularNumbers())
        if (after.isNotEmpty()) Text(after, style = style)
    }
}

/** Značka místa čísla ve větě z prostředků; znak ze soukromé oblasti Unicode se v textu nevyskytne. */
private const val NUMBER_MARK = "\uE000"

/** Tlačítko „dolů" bez počtu je kruh téhle velikosti — zároveň minimální dotyková plocha. */
private val SCROLL_DOWN_BUTTON_SIZE = 48.dp

/**
 * FAB slot konverzace: odznaky zmínek, reakcí a hlasů v anketě, „přečíst" při diskrétním čtení
 * a tlačítko dolů. Scaffold ho drží nad polem a snackbar pak nad ním.
 */
@Composable
internal fun ConversationFloatingButtons(
    state: ConversationState,
    viewModel: ConversationViewModel,
    /** Nad polem plave napovídání (viz `FloatingSuggestion`) — tlačítka na tu chvíli ustoupí. */
    suggestionsFloating: Boolean,
    /** Seznam je víc než pár zpráv nad spodkem. */
    farFromBottom: Boolean,
    /** Kolik cizích zpráv přišlo, zatímco uživatel četl historii. */
    newMessages: Int,
    onScrollDown: () -> Unit,
) {
    // Nad polem zrovna plave napovídání (viz `FloatingSuggestion`) — tlačítka by visela
    // přes něj, takže na tu chvíli ustoupí.
    AnimatedVisibility(
        visible = !suggestionsFloating,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    // Zmínky a reakce nad tlačítkem dolů: jsou vidět, i když je uživatel dole — zmínka
    // bývá hluboko v nepřečtené historii a tlačítko dolů o ní nic neřekne.
    UnreadMarkButton(
        kind = UnreadMarkKind.Mention,
        count = state.unreadMarks.mentions,
        onClick = viewModel::jumpToUnreadMark,
        onReadAll = viewModel::readAllMarks,
    )
    UnreadMarkButton(
        kind = UnreadMarkKind.Reaction,
        count = state.unreadMarks.reactions,
        onClick = viewModel::jumpToUnreadMark,
        onReadAll = viewModel::readAllMarks,
    )
    UnreadMarkButton(
        kind = UnreadMarkKind.PollVote,
        count = state.unreadMarks.pollVotes,
        onClick = viewModel::jumpToUnreadMark,
        onReadAll = viewModel::readAllMarks,
    )
    // Diskrétní čtení: přečtení se samo nehlásí, tak jde poslat ručně. Jen v chatu samém —
    // ve vlákně a v tématu by „přečíst chat" přečetlo i to, co uživatel neviděl.
    MarkReadChip(
        visible = state.discreetUnread > 0 && state.threadRootId == null && state.forumTopicId == null,
        onClick = viewModel::readNow,
    )
    ScrollToBottomButton(
        // V okně v historii vede dolů i od spodní hrany — nejnovější zprávy nejsou načtené.
        visible = farFromBottom || newMessages > 0 || !state.reachedEnd,
        count = newMessages,
        onClick = onScrollDown,
    )
    }
    }
}
