package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.adaptive.paneHeaderContainerColor
import androidx.compose.material3.TopAppBarDefaults
import eu.tintera.plume.core.accounts.AccountBadge
import eu.tintera.plume.core.accounts.rememberShownAccount
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.ShapeDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.AutoDeleteBadge
import eu.tintera.plume.core.ui.ConnectionStatusIcon
import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.EmojiStatusBadge
import eu.tintera.plume.core.ui.ForumTopicIcon
import eu.tintera.plume.core.ui.OnlineDot
import eu.tintera.plume.core.ui.SavedTopicAvatar
import eu.tintera.plume.core.ui.SecretChatLock
import eu.tintera.plume.core.ui.SecureScreen
import eu.tintera.plume.core.ui.VerificationBadge
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.describe
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.rememberShownConnection
import eu.tintera.plume.core.ui.savedTopicTitle
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.features.calls.ConversationCallButtons
import eu.tintera.plume.features.conversation.items.describe
import eu.tintera.plume.core.telegram.GroupMembers
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_members_online
import eu.tintera.plume.resources.invite_members
import eu.tintera.plume.resources.invite_subscribers
import eu.tintera.plume.resources.comments_title
import eu.tintera.plume.resources.direct_topics_subtitle
import eu.tintera.plume.resources.conversation_fallback_title
import eu.tintera.plume.resources.presence_online
import eu.tintera.plume.resources.profile_open
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.adaptive.WindowControlsEndSpace
import eu.tintera.plume.core.adaptive.publishToPaneBand
import eu.tintera.plume.features.profile.chatActionsViewModel
import eu.tintera.plume.core.adaptive.WindowControlsSpace

/**
 * Hlavička konverzace: výběr zpráv, hledání, nebo název chatu s avatarem, podtitulem a akcemi.
 *
 * Stav spojení si čte sama — v těle [ConversationScreen] by jeho změna skládala celou obrazovku.
 */
@Composable
internal fun ConversationTopBar(
    state: ConversationState,
    viewModel: ConversationViewModel,
    navigation: ConversationNavigation,
    /** Obsah chatu se neukazuje (omezený, nepotvrzený citlivý) — není co shrnout ani hledat. */
    chatHidden: Boolean,
    /** Shrnutí modelem v zařízení; `null` = nenabízet. */
    onSummary: (() -> Unit)?,
) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    // Šipka zpět i ⋮ otevírají tentýž přepínač — pod šipkou, ať je vidět, kde ho najít příště.
    val switcher = rememberChatSwitcherState(chatId = viewModel.chatId, onSwitch = navigation.onSwitchChat)
    state.selection?.let { selection ->
        SelectionTopBar(selection = selection, onClose = viewModel::closeSelection)
        return
    }
    state.search?.let { search ->
        InChatSearchTopBar(
            initialQuery = search.query,
            onQueryChange = viewModel::onSearchQuery,
            onClose = viewModel::closeSearch,
            sender = search.sender,
            // Podle člena jen ve skupině: v soukromém chatu jsou odesílatelé dva a v kanálu
            // píše jen kanál.
            onPickSender = viewModel::openMemberPicker.takeIf { state.info?.let { it.isGroup && !it.isChannel } == true },
            onRemoveSender = { viewModel.setSearchSender(null) },
            onJumpToDate = viewModel::jumpToDate.takeIf { viewModel.canJumpToDate },
        )
        return
    }
    // ViewModel akcí nad chatem bere konverzace sama: v pásu se akce skládají v kompozici
    // bočního panelu a `koinViewModel` by tam vrátil jinou instanci než hostiteli ořezu fotky.
    val chatActions = chatActionsViewModel(viewModel.chatId)
    val actions: @Composable () -> Unit = {
        // Hlavní akce jsou hovor a videohovor, všechno ostatní je za ⋮.
        ConversationCallButtons(state.info, state.callRequest, viewModel::consumeCallRequest)
        // Panel s profilem vedle konverzace (jen se zavřeným panelem; zkratka oběma směry).
        ProfilePaneButton(
            hasProfile = state.info?.isSavedMessages == false && state.threadRootId == null,
            onToggleProfile = navigation.onOpenProfile,
        )
        // Prázdný chat s botem je jeho profil — není co shrnout ani hledat.
        // Omezený chat nemá co ukázat, tedy ani co shrnout nebo hledat.
        ConversationMenu(
            state = state,
            viewModel = viewModel,
            showsContent = !showsIntroPage(state) && !chatHidden,
            onSummary = onSummary,
            // Uložené zprávy podle zdrojového chatu: jen u celých Uložených zpráv, ne v tématu.
            onViewAsChats = navigation.onViewAsChats?.takeIf { state.info?.isSavedMessages == true && !state.narrowed },
            // Skupina s tématy otevřená jako zprávy: zpátky na seznam témat, jen z celé skupiny.
            onViewAsTopics = navigation.onViewAsTopics?.takeIf { state.info?.isForum == true && !state.narrowed },
            onSwitchChat = switcher::open.takeIf { switcher.canSwitch },
            onOpenCalendar = navigation.onOpenCalendar?.let { open -> { open(viewModel.calendarAnchor()) } },
            onShareContact = navigation.onShareContact,
            onAddMembers = navigation.onAddMembers,
            onEditChat = navigation.onEditChat,
            chatActions = chatActions,
        )
    }
    // Vedle bočního panelu kreslí akce pás nad ním, u pravého okraje okna (`PaneBand`): stojí tak
    // na stejném místě s panelem i bez něj.
    val inBand = publishToPaneBand(actions)
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = paneHeaderContainerColor()),
        title = {
            // Hlavička vede do profilu chatu — tak to znají uživatelé ze všech klientů.
            // Uložené zprávy profil nemají (je to vlastní účet, nic k ukázání).
            val profileLabel = stringResource(Res.string.profile_open)
            /*
             * Stav spojení nese podtitul (znak a věta, jako pilulka v seznamu chatů), ne
             * avatar — tvář protějšku by jinak vypadala, že se něco děje s ním.
             */
            val shownConnection = rememberShownConnection(connection)
            val shownAccount = rememberShownAccount()
            // „Online" už v podtitulu není, nese ho jen tečka — čtečce ho proto řekne
            // popis stavu hlavičky. Bez spojení ne: dostupnost je stará hodnota.
            val onlineLabel = stringResource(Res.string.presence_online)
                .takeIf { shownConnection == null && state.presence is Presence.Online && state.info?.isSavedMessages == false }
            val kind = when {
                state.info?.isChannel == true -> ChatKind.Channel
                state.info?.isGroup == true -> ChatKind.Group
                else -> ChatKind.Private
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(ShapeDefaults.Medium)
                    .clickable(
                        // Vlákno komentářů vlastní profil nemá; profil skupiny by vedl pryč.
                        enabled = state.info?.isSavedMessages == false && state.threadRootId == null,
                        onClickLabel = profileLabel,
                        onClick = navigation.onOpenProfile,
                    )
                    .semantics { onlineLabel?.let { stateDescription = it } }
                    // Řádek ořezává (tvar vlnky stisku) a zaoblený roh by ukousl, co sahá
                    // k jeho okraji — spodek teček „Čekání na síť…", když je podtitul
                    // nejširší.
                    .padding(HEADER_ROW_PADDING),
            ) {
                // Nad profilem bota je jeho tvář velká uprostřed obrazovky a nese i tečku
                // (`BotIntroPage`); v hlavičce by byla podruhé.
                val headerAvatar = !showsIntroPage(state)
                // Téma: jeho ikona místo fotky skupiny. Nic neletí — seznam témat je
                // jiná obrazovka a fotka skupiny v ní v řádku není.
                state.info?.topic?.let { topic ->
                    ForumTopicIcon(
                        icon = topic.icon,
                        name = topic.name,
                        isGeneral = topic.isGeneral,
                        size = HEADER_AVATAR,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                // Téma Uložených zpráv: fotka chatu, ze kterého se ukládalo (Moje poznámky
                // záložka). Taky nic neletí, řádek v seznamu témat je jiná obrazovka.
                state.info?.savedTopic?.let { topic ->
                    SavedTopicAvatar(topic = topic, size = HEADER_AVATAR, modifier = Modifier.padding(end = 12.dp))
                }
                // Vlákno přímých zpráv kanálu: tvář toho, kdo kanálu píše.
                state.info?.directTopic?.let { topic ->
                    Avatar(
                        chatId = topic.senderId,
                        title = topic.title,
                        avatarFileId = topic.avatarFileId,
                        size = HEADER_AVATAR,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                state.info?.takeIf { it.topic == null && it.savedTopic == null && it.directTopic == null }?.let { info ->
                  AnimatedVisibility(
                      visible = headerAvatar,
                      enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                      exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
                  ) {
                    // Mezera ke jménu je součást avataru, ne řádku: schovaný avatar
                    // by jinak nechal před jménem díru.
                    Box(Modifier.padding(end = 12.dp)) {
                        Avatar(
                            chatId = info.avatarColorId,
                            title = info.title,
                            avatarFileId = info.avatarFileId,
                            size = HEADER_AVATAR,
                            isSavedMessages = info.isSavedMessages,
                            // Stejný tvar jako v seznamu, odkud avatar přiletěl.
                            shape = kind.avatarShape(),
                            // Vlákno komentářů je jiná obrazovka než chat skupiny; sdílený
                            // klíč by se přetahoval s její hlavičkou i s položkou v seznamu.
                            modifier = if (state.threadRootId == null) {
                                Modifier.sharedNavElement(chatAvatarKey(info.id))
                            } else {
                                Modifier
                            },
                        )
                        // Kterým účtem se tu píše, když je jich víc — ve skupině, kde jsou
                        // oba, se jinak snadno odpoví ze špatného. Jako odznak v seznamu chatů.
                        shownAccount?.let { account ->
                            AccountBadge(account, rim = MaterialTheme.colorScheme.surface, modifier = Modifier.align(Alignment.BottomStart))
                        }
                        // Jako v seznamu chatů. Bez spojení je dostupnost stará hodnota.
                        if (shownConnection == null && state.presence is Presence.Online && !info.isSavedMessages) {
                            OnlineDot(
                                rim = MaterialTheme.colorScheme.surface,
                                size = HEADER_ONLINE_DOT,
                                modifier = Modifier.align(Alignment.BottomEnd),
                                // Letí s avatarem ze seznamu chatů; ve vlákně ne, stejně
                                // jako avatar.
                                chatId = info.id.takeIf { state.threadRootId == null },
                            )
                        }
                    }
                  }
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Tajný chat: zámek a zákaz snímků obrazovky. Efekt patří celé
                        // obrazovce; tady je proto, že `ConversationScreen` je v dexu na
                        // hraně registrů (viz CLAUDE.md) a hlavička je vlastní lambda.
                        val secret = state.info?.isSecret == true
                        SecureScreen(enabled = secret)
                        if (secret && !state.narrowed) SecretChatLock()
                        Text(
                            text = when {
                                state.threadRootId != null -> stringResource(Res.string.comments_title)
                                state.info?.topic != null -> state.info?.topic?.name.orEmpty()
                                state.info?.savedTopic != null -> savedTopicTitle(state.info!!.savedTopic!!)
                                state.info?.directTopic != null -> state.info?.directTopic?.title.orEmpty()
                                else -> state.info?.let { chatTitle(it.id, it.title) }
                                    ?: stringResource(Res.string.conversation_fallback_title)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Klíč se dá jen když chat známe; než dorazí `info`, není s čím
                            // párovat. Váha bez vyplnění: dlouhý název se zkrátí, štítek
                            // za ním zůstane vidět.
                            modifier = Modifier.weight(1f, fill = false).then(
                                state.info
                                    ?.takeIf { !state.narrowed }
                                    ?.let { Modifier.sharedNavText(chatTitleKey(it.id)) }
                                    ?: Modifier,
                            ),
                        )
                        // Podvod a falešný účet musí být vidět tam, kde se píše — ve
                        // vlákně komentářů je hlavička o skupině, ne o účtu.
                        state.info?.verification
                            ?.takeIf { !state.narrowed }
                            ?.let { VerificationBadge(it) }
                        state.info?.emojiStatus
                            ?.takeIf { !state.narrowed }
                            ?.let { EmojiStatusBadge(it) }
                        // Automatické mazání: co se tu napíše, samo zmizí. Ve vlákně
                        // komentářů by patřilo celé skupině, ne vláknu.
                        state.autoDeleteTime
                            .takeIf { it > 0 && !state.narrowed }
                            ?.let { AutoDeleteBadge(it) }
                    }

                    /*
                     * Podtitul: kdy byl protějšek naposledy vidět, a bez spojení místo toho
                     * stav spojení se svým znakem. „Online" tu NENÍ, to říká tečka na
                     * avataru. Dvakrát totéž hned vedle sebe byl jen šum.
                     *
                     * Výpadek má přednost i před názvem skupiny ve vlákně: dokud spojení
                     * nestojí, je to jediné, co uživateli vysvětlí, proč nic nechodí — a
                     * „naposledy viděn" je jen stará hodnota, kterou nemá co obnovit.
                     *
                     * „Píše…" tu NENÍ: kreslí se v seznamu zpráv tam, kam zpráva naskočí
                     * (`TypingItem`), jako v Google Zprávách. Výšku hlavičky animuje
                     * `AnimatedContent`, jinak by název při objevení podtitulu poskočil.
                     */
                    // Ve vlákně komentářů a v tématu říká podtitul, ve které skupině se diskutuje.
                    val threadSubtitle = state.info
                        ?.takeIf { state.narrowed }
                        ?.let { HeaderSubtitle.Text(chatTitle(it.id, it.title)) }
                    // Přímé zprávy nesou název kanálu; bez podtitulu by vypadaly jako kanál sám.
                    val directSubtitle = stringResource(Res.string.direct_topics_subtitle)
                        .takeIf { state.info?.isDirectMessages == true && !state.narrowed }
                        ?.let { HeaderSubtitle.Text(it) }
                    // Skupina a kanál: počet členů (odběratelů), ve skupině i kolik je online.
                    val membersSubtitle = state.members?.let { HeaderSubtitle.Text(membersText(it)) }
                    val subtitle = shownConnection?.let { HeaderSubtitle.Connection(it) }
                        ?: threadSubtitle ?: directSubtitle ?: state.presence
                        // Vlastní dostupnost v Uložených zprávách nic neříká — byl bys
                        // pořád „online", protože se na ně právě díváš.
                        ?.takeUnless { state.info?.isSavedMessages == true }
                        ?.takeUnless { it is Presence.Online }
                        ?.let { HeaderSubtitle.Text(it.describe()) }
                        ?: membersSubtitle
                    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = { fadeIn(effects) togetherWith fadeOut(fastEffects) },
                        contentAlignment = Alignment.CenterStart,
                        // Mezi stavy výpadku se podtitul nevyměňuje celý, jinak by znak
                        // začínal znovu; věta se změní na místě.
                        contentKey = { it?.let { it::class } },
                        label = "header-subtitle",
                    ) { current ->
                        when (current) {
                            null -> Unit
                            is HeaderSubtitle.Text -> SubtitleText(current.text)
                            is HeaderSubtitle.Connection -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ConnectionStatusIcon(current.state, size = HEADER_STATUS_ICON)
                                current.state.label()?.let { SubtitleText(stringResource(it)) }
                            }
                        }
                    }
                }
            }
        },
        navigationIcon = {
            WindowControlsSpace {
                // Podržení nabídne rychlý přepínač chatů; ťuknutí je dál „zpět".
                ChatSwitcherBackButton(state = switcher, onBack = navigation.onBack)
            }
        },
        actions = {
            if (!inBand) actions()
            WindowControlsEndSpace()
        },
    )
}

/** Avatar v hlavičce. */
private val HEADER_AVATAR = 36.dp

/** Rezerva řádku hlavičky uvnitř ořezu vlnky stisku. */
private val HEADER_ROW_PADDING = 4.dp

/** Znak výpadku spojení v podtitulu — na výšku řádku `labelMedium`. */
private val HEADER_STATUS_ICON = 16.dp

/** Tečka dostupnosti na 36dp avataru — v seznamu chatů je 14 dp na 52dp. */
private val HEADER_ONLINE_DOT = 11.dp

/** Řádek pod názvem chatu: naposledy viděn, ve vlákně název skupiny, nebo výpadek spojení. */
/**
 * „128 členů, 12 online", u kanálu „3 400 odběratelů". Online se ukáže až od dvou: TDLib
 * počítá i uživatele samotného, takže „1 online" by znamenalo jen „ty".
 */
@Composable
private fun membersText(members: GroupMembers): String {
    val count = pluralStringResource(
        if (members.isChannel) Res.plurals.invite_subscribers else Res.plurals.invite_members,
        members.count,
        members.count.grouped(),
    )
    return if (members.online > 1) {
        stringResource(Res.string.conversation_members_online, count, members.online.grouped())
    } else {
        count
    }
}

private sealed interface HeaderSubtitle {
    data class Text(val text: String) : HeaderSubtitle

    data class Connection(val state: ConnectionState) : HeaderSubtitle
}

@Composable
private fun SubtitleText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}
