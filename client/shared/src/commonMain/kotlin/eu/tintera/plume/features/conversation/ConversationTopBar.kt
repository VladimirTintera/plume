package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.AutoDeleteBadge
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.EmojiStatusBadge
import eu.tintera.plume.core.ui.ForumTopicIcon
import eu.tintera.plume.core.ui.OnlineDot
import eu.tintera.plume.core.ui.RING_STROKE
import eu.tintera.plume.core.ui.SavedTopicAvatar
import eu.tintera.plume.core.ui.SecretChatLock
import eu.tintera.plume.core.ui.SecureScreen
import eu.tintera.plume.core.ui.VerificationBadge
import eu.tintera.plume.core.ui.avatarPolygon
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.describe
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.rememberConnectionIndicator
import eu.tintera.plume.core.ui.rememberShownConnection
import eu.tintera.plume.core.ui.savedTopicTitle
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.features.calls.ConversationCallButtons
import eu.tintera.plume.features.conversation.items.describe
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.comments_title
import eu.tintera.plume.resources.conversation_fallback_title
import eu.tintera.plume.resources.presence_online
import eu.tintera.plume.resources.profile_open
import org.jetbrains.compose.resources.stringResource

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
    TopAppBar(
        title = {
            // Hlavička vede do profilu chatu — tak to znají uživatelé ze všech klientů.
            // Uložené zprávy profil nemají (je to vlastní účet, nic k ukázání).
            val profileLabel = stringResource(Res.string.profile_open)
            /*
             * Stav spojení nese avatar (zvlní se a točí), stejně jako fotka účtu
             * v seznamu chatů — ne text pod názvem. Čtečce obrazovky ho řekne popis
             * stavu hlavičky.
             */
            val shownConnection = rememberShownConnection(connection)
            val connectionLabel = shownConnection?.label()?.let { stringResource(it) }
            // „Online" už v podtitulu není, nese ho jen tečka — čtečce ho proto řekne
            // popis stavu hlavičky, stejně jako výpadek spojení (ten má přednost: bez
            // spojení je dostupnost stará hodnota).
            val onlineLabel = stringResource(Res.string.presence_online)
                .takeIf { state.presence is Presence.Online && state.info?.isSavedMessages == false }
            val headerState = connectionLabel ?: onlineLabel
            val kind = when {
                state.info?.isChannel == true -> ChatKind.Channel
                state.info?.isGroup == true -> ChatKind.Group
                else -> ChatKind.Private
            }
            val indicator = rememberConnectionIndicator(
                connection = shownConnection,
                avatarSize = HEADER_AVATAR,
                grownSize = HEADER_AVATAR_GROWN,
                base = kind.avatarPolygon(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        // Vlákno komentářů vlastní profil nemá; profil skupiny by vedl pryč.
                        enabled = state.info?.isSavedMessages == false && state.threadRootId == null,
                        onClickLabel = profileLabel,
                        onClick = navigation.onOpenProfile,
                    )
                    .semantics { headerState?.let { stateDescription = it } }
                    // Místo pro zvětšený avatar s prstencem: řádek ořezává (tvar vlnky
                    // stisku), takže bez rezervy by prstenec usekl vlevo i nahoře a dole.
                    .padding(horizontal = HEADER_RING_ROOM, vertical = HEADER_RING_ROOM),
            ) {
                // Nad profilem bota je jeho tvář velká uprostřed obrazovky a nese i tečku
                // a výpadek spojení (`BotIntroPage`); v hlavičce by byla podruhé.
                val headerAvatar = !showsBotIntro(state)
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
                state.info?.takeIf { it.topic == null && it.savedTopic == null }?.let { info ->
                  AnimatedVisibility(
                      visible = headerAvatar,
                      enter = fadeIn() + expandHorizontally(),
                      exit = fadeOut() + shrinkHorizontally(),
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
                            // Stejný tvar jako v seznamu, odkud avatar přiletěl. Při výpadku
                            // ořezává indikátor — vlastní tvar by vlnky u skupiny schoval.
                            shape = if (indicator.isShaping) RectangleShape else kind.avatarShape(),
                            // Vlákno komentářů je jiná obrazovka než chat skupiny; sdílený
                            // klíč by se přetahoval s její hlavičkou i s položkou v seznamu.
                            modifier = Modifier
                                .then(
                                    if (state.threadRootId == null) {
                                        Modifier.sharedNavElement(chatAvatarKey(info.id))
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(with(indicator) { Modifier.avatar() }),
                        )
                        with(indicator) { Spacer(Modifier.matchParentSize().ring()) }
                        // Jako v seznamu chatů. Bez spojení je dostupnost stará hodnota
                        // a tečka by přeškrtla prstenec.
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
                     * Podtitul: kdy byl protějšek naposledy vidět. Stav spojení tu NENÍ, ten
                     * nese avatar — a „online" taky ne, to říká tečka na avataru. Dvakrát
                     * totéž hned vedle sebe byl jen šum.
                     *
                     * Bez spojení se ale dostupnost schová: dokud spojení nestojí, je
                     * „naposledy viděn" jen stará hodnota, kterou nemá co obnovit.
                     *
                     * „Píše…" tu NENÍ: kreslí se v seznamu zpráv tam, kam zpráva naskočí
                     * (`TypingItem`), jako v Google Zprávách. Výšku hlavičky animuje
                     * `AnimatedContent`, jinak by název při objevení podtitulu poskočil.
                     */
                    // Ve vlákně komentářů a v tématu říká podtitul, ve které skupině se diskutuje.
                    val threadSubtitle = state.info
                        ?.takeIf { state.narrowed }
                        ?.let { HeaderSubtitle(chatTitle(it.id, it.title)) }
                    val subtitle = threadSubtitle ?: state.presence
                        ?.takeIf { shownConnection == null }
                        // Vlastní dostupnost v Uložených zprávách nic neříká — byl bys
                        // pořád „online", protože se na ně právě díváš.
                        ?.takeUnless { state.info?.isSavedMessages == true }
                        ?.takeUnless { it is Presence.Online }
                        ?.let { HeaderSubtitle(it.describe()) }
                    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = { fadeIn(effects) togetherWith fadeOut(fastEffects) },
                        contentAlignment = Alignment.CenterStart,
                        label = "header-subtitle",
                    ) { current ->
                        current ?: return@AnimatedContent
                        Text(
                            text = current.text,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            // Podržení nabídne rychlý přepínač chatů; ťuknutí je dál „zpět".
            ChatSwitcherBackButton(chatId = viewModel.chatId, onBack = navigation.onBack, onSwitch = navigation.onSwitchChat)
        },
        actions = {
            // Hlavní akce jsou hovor a videohovor, všechno ostatní je za ⋮.
            ConversationCallButtons(state.info, state.callRequest, viewModel::consumeCallRequest)
            // Prázdný chat s botem je jeho profil — není co shrnout ani hledat.
            // Omezený chat nemá co ukázat, tedy ani co shrnout nebo hledat.
            ConversationMenu(
                state = state,
                viewModel = viewModel,
                showsContent = !showsBotIntro(state) && !chatHidden,
                onSummary = onSummary,
                // Uložené zprávy podle zdrojového chatu: jen u celých Uložených zpráv, ne v tématu.
                onViewAsChats = navigation.onViewAsChats?.takeIf { state.info?.isSavedMessages == true && !state.narrowed },
                // Skupina s tématy otevřená jako zprávy: zpátky na seznam témat, jen z celé skupiny.
                onViewAsTopics = navigation.onViewAsTopics?.takeIf { state.info?.isForum == true && !state.narrowed },
            )
        },
    )
}

/** Avatar v hlavičce. */
private val HEADER_AVATAR = 36.dp

/** Avatar v hlavičce při výpadku spojení (viz `ConnectionIndicator`). */
private val HEADER_AVATAR_GROWN = 42.dp

/** Rezerva řádku hlavičky kolem avataru pro jeho zvětšení a půl tahu prstence. */
private val HEADER_RING_ROOM = (HEADER_AVATAR_GROWN + RING_STROKE - HEADER_AVATAR) / 2

/** Tečka dostupnosti na 36dp avataru — v seznamu chatů je 14 dp na 52dp. */
private val HEADER_ONLINE_DOT = 11.dp

/** Řádek pod názvem chatu: naposledy viděn, nebo ve vlákně název skupiny. */
private data class HeaderSubtitle(val text: String)
