package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Row
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.accounts.rememberConversationAccount
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import eu.tintera.plume.core.audio.VoicePlayback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.adaptive.centeredColumn
import eu.tintera.plume.core.location.rememberDeviceLocation
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.telegram.PollComposerMode
import eu.tintera.plume.core.telegram.ReplyKeyboardButtonKind
import eu.tintera.plume.core.telegram.SavedTopicKind
import eu.tintera.plume.core.telegram.WriteAccess
import eu.tintera.plume.core.telegram.commandSuggestions
import eu.tintera.plume.core.telegram.inlineQueryOf
import eu.tintera.plume.core.telegram.hashtagInsertion
import eu.tintera.plume.core.telegram.hashtagQueryAt
import eu.tintera.plume.core.telegram.mentionInsertion
import eu.tintera.plume.core.telegram.mentionQueryAt
import eu.tintera.plume.core.ui.EmbeddedMediaSelection
import eu.tintera.plume.core.ui.KeyboardContent
import eu.tintera.plume.core.ui.LocalSharedElementOcclusion
import eu.tintera.plume.core.ui.SharedElementClip
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.embeddedMediaPickerAvailable
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.sharedElementOcclusionBand
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_bot_start
import eu.tintera.plume.resources.conversation_camera_permission
import eu.tintera.plume.resources.conversation_microphone_permission
import eu.tintera.plume.resources.conversation_not_member
import eu.tintera.plume.resources.conversation_secret_closed
import eu.tintera.plume.resources.conversation_premium_only
import eu.tintera.plume.resources.conversation_deleted_account
import eu.tintera.plume.resources.conversation_paid_messages
import eu.tintera.plume.resources.conversation_secret_pending
import eu.tintera.plume.resources.conversation_topic_closed
import eu.tintera.plume.resources.conversation_write_restricted
import eu.tintera.plume.resources.saved_topic_open_chat
import eu.tintera.plume.resources.saved_topic_read_only
import eu.tintera.plume.resources.service_upgraded_open_new
import eu.tintera.plume.resources.conversation_unblock_bot
import eu.tintera.plume.resources.profile_unblock
import org.koin.compose.viewmodel.koinViewModel

/**
 * Spodek konverzace: vstupní pole s napovídáním nad ním, nebo to, co stojí místo něj — akce
 * výběru, šipky hledání, Přidat se / Start, ztlumení kanálu, věta, proč psát nejde.
 *
 * Vstupní pole JE `bottomBar` Scaffoldu: ten ho změří (i s citací, úpravou, víc řádky
 * a klávesnicí) a podle toho postaví snackbar a tlačítko dolů nad něj.
 */
@Composable
internal fun ConversationBottomBar(
    state: ConversationState,
    viewModel: ConversationViewModel,
    navigation: ConversationNavigation,
    composer: ComposerDraft,
    sheets: ConversationSheets,
    recording: ConversationRecording,
    videoNotePlayback: VideoNotePreviewPlayback,
    voicePlayback: State<VoicePlayback?>,
    replies: ReplySuggestionsViewModel,
    gestureHints: GestureHints,
    mediaSelection: EmbeddedMediaSelection,
    /** Obsah chatu se neukazuje (omezený, nepotvrzený citlivý) — do něj se ani psát nedá. */
    chatHidden: Boolean,
    /** Zdroj mléčného skla: seznam zpráv pod lištou. */
    listHaze: HazeState,
    listClip: SharedElementClip,
    /** Horní hrana lišty v okně — spodní mez pro nabídku nad zprávou. */
    onContentBottom: (Float) -> Unit,
    /** Nad polem plave napovídání; tlačítko dolů a zmínky zatím ustoupí. */
    onSuggestionsFloating: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Spojená společná skupina: kterým účtem psát, vybírá tlačítko u pole (`AccountSwitchButton`).
    val switchAccount = navigation.onSwitchAccount
    val chatAccounts = rememberChatAccounts(viewModel.chatId, enabled = switchAccount != null)
    val currentSlot = LocalAccount.current.slot
    var draft by composer
    val replySuggestions by replies.suggestions.collectAsStateWithLifecycle()
    // Emoji k rozepsanému slovu. Jazyk zařízení a angličtina, jako hledání ve výběru emoji.
    val emojiWords: EmojiSuggestionsViewModel = koinViewModel(key = "emoji-words-${viewModel.chatId}")
    val emojiSuggestions by emojiWords.suggestions.collectAsStateWithLifecycle()
    val locale = LocalLocale.current
    val emojiLanguages = remember(locale) { listOf(locale.language, "en").distinct() }
    /** Poslední otevřená část výběru — uložená v nastavení, takže platí i po restartu aplikace. */
    val expressionMode by viewModel.expressionMode.collectAsStateWithLifecycle()
    // Seznam úkolů smí založit jen účet s Premium; bez něj ho sponka nenabízí (jako prémiové efekty).
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val recordMode = recording.mode(state.content)

    // Vybrané soubory jsou už zkopírované v cache aplikace; čekají nad polem na popisek a odeslání.
    val attachmentPicker = rememberAttachmentPicker(
        onPicked = viewModel::addAttachments,
        onError = viewModel::reportError,
    )
    // Nová příloha při úpravě zprávy — jiný cíl než přílohy nad polem, proto vlastní výběr.
    val replacementPicker = rememberAttachmentPicker(
        onPicked = viewModel::replaceEditMedia,
        onError = viewModel::reportError,
    )
    /*
     * Galerie se kreslí rovnou nad vstupním polem, kde ji systém umí vložit dovnitř aplikace
     * (`EmbeddedMediaPicker`); jinde zůstává systémový výběr, tedy odchod na vlastní obrazovku.
     * Rozhoduje se tady, aby o tom `AttachButton` nemusel vědět — nabídka je v obou případech
     * stejná.
     */
    val embeddedMediaPicker = embeddedMediaPickerAvailable()

    // Horní hrana pole je spodní mez pro nabídku nad zprávou (viz `contentBottom`).
    // Na široké ploše ve sloupci uprostřed, stejně jako zprávy nad ním.
    Box(
        Modifier
            .centeredColumn(ContentWidth.Conversation)
            .onGloballyPositioned { onContentBottom(it.boundsInWindow().top) },
    ) {
    /*
     * Lišty místo pole (Přidat se, Start, Ztlumit, věta „nejde psát") mají pod sebou
     * TENTÝŽ pás skla jako pole. Seznam zpráv pod spodní lištu zajíždí (spodek je
     * `contentPadding`, ne konec plochy), takže bez pásu prosvítaly bubliny zpod tlačítka.
     */
    val barGlass = Modifier.hazeBlur(input = HazeInput.Sources(listHaze), style = bandGlassStyle())
    // Seznam výsledků hledání (sheet nad lištou „N z M"); se zavřeným hledáním zmizí s ní.
    var showSearchList by rememberSaveable { mutableStateOf(false) }
    if (state.search == null && showSearchList) showSearchList = false
    // Při výběru patří spodek akcím nad ním; rozepsaný text zůstává v konceptu.
    state.selection?.let { selection ->
        SelectionToolbar(
            selection = selection,
            onCopy = viewModel::copySelection,
            onCopyWithNames = viewModel::copySelectionWithNames,
            onCopyMarkdown = viewModel::copySelectionAsMarkdown,
            onForward = viewModel::forwardSelection,
            onReport = viewModel::reportSelection,
            onDelete = viewModel::deleteSelection,
        )
        return@Box
    }
    // Při hledání patří spodek šipkám mezi výsledky; rozepsaný text zůstává v konceptu.
    state.search?.let { search ->
        InChatSearchNavigator(
            search = search,
            // Procházení po dílcích — tik, ne potvrzení: výsledků bývají stovky.
            onOlder = {
                haptics.tick()
                viewModel.searchOlder()
            },
            onNewer = {
                haptics.tick()
                viewModel.searchNewer()
            },
            onShowList = { showSearchList = true },
        )
        if (showSearchList) {
            // Fokus se po zavření sheetu vrací do pole hledání a s ním klávesnice, která by
            // zakryla půlku konverzace i se zprávou, na kterou se právě skočilo.
            val focusManager = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            InChatSearchResultsSheet(
                search = search,
                partnerName = state.info?.title,
                onPick = { index ->
                    showSearchList = false
                    focusManager.clearFocus()
                    keyboard?.hide()
                    viewModel.searchGoTo(index)
                },
                onLoadMore = viewModel::loadMoreSearchResults,
                onDismiss = { showSearchList = false },
            )
        }
        return@Box
    }
    // Omezený chat: do chatu, který se nedá ukázat, se ani psát nedá.
    if (chatHidden) return@Box
    if (state.info?.canJoin == true) {
        JoinBar(joining = state.joining, onJoin = viewModel::join, modifier = barGlass)
        return@Box
    }
    // Převedená základní skupina: psát se dá jen do té nové, tak tam vede.
    state.info?.upgradedToChatId?.let { upgraded ->
        JoinBar(
            joining = false,
            onJoin = { navigation.onOpenChat(ChatTarget(upgraded)) },
            label = Res.string.service_upgraded_open_new,
            modifier = barGlass,
        )
        return@Box
    }
    // Zablokovaný protějšek: psát mu nejde, dokud se neodblokuje (bota to rovnou i spustí).
    // Před Startem — zablokovanému botovi by „/start" neodešel.
    if (state.showsUnblock) {
        JoinBar(
            joining = state.unblocking,
            onJoin = {
                haptics.confirm()
                viewModel.unblock()
            },
            label = if (state.botIntro != null) Res.string.conversation_unblock_bot else Res.string.profile_unblock,
            modifier = barGlass,
        )
        return@Box
    }
    // Nespuštěný bot (nebo čekající parametr z odkazu): Start místo pole.
    if (showsBotStart(state)) {
        JoinBar(
            joining = state.startingBot,
            onJoin = {
                haptics.confirm()
                viewModel.startBot()
            },
            label = Res.string.conversation_bot_start,
            modifier = barGlass,
        )
        return@Box
    }
    // Kde se psát nesmí, pole by skončilo chybou až po odeslání. Kanál aspoň nabídne ztlumení.
    when (state.info?.writeAccess) {
        WriteAccess.ChannelReadOnly -> {
            val directChat = state.info?.directMessagesChatId
            MuteBar(
                muted = state.muted,
                onToggle = viewModel::toggleMute,
                modifier = barGlass,
                onMessage = directChat?.let { { navigation.onOpenChat(ChatTarget(it)) } },
            )
            return@Box
        }
        WriteAccess.Restricted -> {
            ReadOnlyNote(Res.string.conversation_write_restricted, barGlass)
            return@Box
        }
        WriteAccess.NotMember -> {
            ReadOnlyNote(Res.string.conversation_not_member, barGlass)
            return@Box
        }
        WriteAccess.TopicClosed -> {
            ReadOnlyNote(Res.string.conversation_topic_closed, barGlass)
            return@Box
        }
        WriteAccess.SecretPending -> {
            ReadOnlyNote(Res.string.conversation_secret_pending, barGlass)
            return@Box
        }
        WriteAccess.SecretClosed -> {
            ReadOnlyNote(Res.string.conversation_secret_closed, barGlass)
            return@Box
        }
        // Protějšek zprávy nepřijímá (`canSendMessageToUser`): věta místo chyby po odeslání.
        WriteAccess.PremiumOnly -> {
            ReadOnlyNote(Res.string.conversation_premium_only, barGlass)
            return@Box
        }
        WriteAccess.DeletedAccount -> {
            ReadOnlyNote(Res.string.conversation_deleted_account, barGlass)
            return@Box
        }
        WriteAccess.PaidMessages -> {
            ReadOnlyNote(Res.string.conversation_paid_messages, barGlass)
            return@Box
        }
        // Téma Uložených zpráv uložené odjinud: odeslat do něj TDLib nedovolí. Zdrojový
        // chat aspoň jde otevřít — tam se odpovídá; bez známého chatu jen proč.
        WriteAccess.SavedTopic -> {
            val source = (state.info?.savedTopic?.kind as? SavedTopicKind.FromChat)?.chatId
            if (source != null) {
                JoinBar(
                    joining = false,
                    onJoin = { navigation.onOpenChat(ChatTarget(source)) },
                    label = Res.string.saved_topic_open_chat,
                    modifier = barGlass,
                )
            } else {
                ReadOnlyNote(Res.string.saved_topic_read_only, barGlass)
            }
            return@Box
        }
        WriteAccess.Allowed, null -> Unit
    }
    /*
     * Sklo leží pod CELÝM blokem nad konverzací — návrhy odpovědi, klávesnice bota i pole —,
     * ne jen pod polem. Návrhy a klávesnice stojí v rozvržení, takže zprávy pod ně při
     * odrolování zajedou stejně jako pod pole; bez skla pod nimi prosvítala ostrá příchozí
     * bublina a s čipem (plocha karty pole) splývala do dvou čipů přes sebe.
     */
    Column(
        // Bez rozostření zůstane krycí pás (`bandGlassStyle`), jinak by se v něm psaní ztratilo.
        Modifier.hazeBlur(input = HazeInput.Sources(listHaze), style = bandGlassStyle()),
    ) {
    ReplySuggestionChips(
        suggestions = replySuggestions,
        // Návrh se jen VLOŽÍ do pole; odeslání zůstává na uživateli. `onDraftChanged`
        // musí jít taky — uloží koncept a ohlásí „píše…", jako by to napsal sám.
        onPick = { suggestion ->
            draft = TextFieldValue(suggestion, selection = TextRange(suggestion.length))
            viewModel.onDraftChanged(composer.composed)
            replies.consume()
        },
        onHide = replies::consume,
        onTurnOff = replies::turnOff,
    )
    // Vlastní klávesnice bota stojí NAD polem (viz `ReplyKeyboardPanel`); při úpravě
    // zprávy ne — tlačítko by poslalo novou zprávu uprostřed opravy staré.
    (state.replyMarkup as? ChatReplyMarkup.Keyboard)?.takeIf { state.editing == null }?.let { keyboard ->
        // Použitá jednorázová klávesnice se otevírá sbalená i po návratu do chatu — TDLib
        // si použití nepamatuje a klávesnice v chatu zůstává (viz `keyboardUsed`).
        val usedKeyboard by viewModel.usedKeyboardMessageId.collectAsStateWithLifecycle()
        var collapsed by rememberSaveable(keyboard.messageId) {
            mutableStateOf(keyboard.oneTime && usedKeyboard == keyboard.messageId)
        }
        ReplyKeyboardPanel(
            keyboard = keyboard,
            collapsed = collapsed,
            onToggle = { collapsed = !collapsed },
            onPress = { row, column ->
                haptics.confirm()
                // Jednorázová klávesnice se po použití SCHOVÁ, nezmizí (Bot API
                // `one_time_keyboard`) — šipkou jde vrátit. Vysvětlení u tlačítka, které
                // Plume neumí, použití není.
                val kind = keyboard.rows.getOrNull(row)?.getOrNull(column)?.kind
                if (keyboard.oneTime && kind !is ReplyKeyboardButtonKind.Unsupported) collapsed = true
                viewModel.pressReplyKeyboardButton(row, column)
            },
        )
    }
    // Příkazy botů: napovídání podle rozepsaného „/…", nebo celý seznam z tlačítka u pole.
    val typedCommands = commandSuggestions(draft.text, state.botCommands)
    // Inline bot (`@gif kočka`): výsledky nad polem. S přílohami je text popisek a při úpravě
    // text staré zprávy — ani jedno není dotaz na bota.
    val inlineDraft = remember(draft.text, state.editing, state.attachments.isEmpty()) {
        inlineQueryOf(draft.text).takeIf { state.editing == null && state.attachments.isEmpty() }
    }
    // Zmínka (`@ja` u kurzoru): členové skupiny nad polem. Kurzor, ne celý text — zmínka se
    // píše kdekoli ve větě. S rozepsaným dotazem na inline bota se nenapovídá, to je jiné „@".
    val mentionQuery = remember(draft, inlineDraft) {
        draft.takeIf { inlineDraft == null && it.selection.collapsed }
            ?.let { mentionQueryAt(it.text, it.selection.start) }
    }
    // Na začátku zprávy se napovídají i nedávní inline boti — jen tam z `@bot ` vznikne dotaz.
    LaunchedEffect(mentionQuery?.query, mentionQuery?.start == 0) {
        viewModel.mentionQuery(mentionQuery?.query, atStart = mentionQuery?.start == 0)
    }
    // Hashtag (`#pl` u kurzoru): dříve použité hashtagy nad polem.
    val hashtagQuery = remember(draft, inlineDraft) {
        draft.takeIf { inlineDraft == null && it.selection.collapsed }
            ?.let { hashtagQueryAt(it.text, it.selection.start) }
    }
    LaunchedEffect(hashtagQuery?.query) { viewModel.hashtagQuery(hashtagQuery?.query) }
    // Emoji ke slovu u kurzoru. Ne vedle jiného napovídání — dotaz na inline bota a zmínka
    // mají nad polem přednost a emoji plovoucí přes jejich panel by ho zakryla.
    val emojiWord = remember(draft, inlineDraft, mentionQuery, hashtagQuery, typedCommands) {
        draft.takeIf {
            inlineDraft == null && mentionQuery == null && hashtagQuery == null && typedCommands == null &&
                it.selection.collapsed
        }?.let { emojiWordAt(it.text, it.selection.start) }
    }
    LaunchedEffect(emojiWord?.word) { emojiWords.lookup(emojiWord?.word, emojiLanguages) }
    // Sada ke slovu z minulého písmene zůstává, dokud nepřijde nová: pilulka se mění na místě.
    val shownEmojis = emojiSuggestions?.takeIf { emojiWord != null }?.emojis.orEmpty()
    // Samolepky k emoji, které je v poli samo. Jen kde se samolepka smí poslat a pole
    // je obyčejná zpráva — ne při úpravě ani s přílohou, kde by emoji byl popisek.
    val stickerEmoji = remember(draft.text, inlineDraft, state.editing, state.attachments.isEmpty(), state.content.stickers) {
        stickerSuggestionEmoji(draft.text).takeIf {
            inlineDraft == null && state.editing == null && state.attachments.isEmpty() && state.content.stickers
        }
    }
    val shownStickers = rememberStickerSuggestions(stickerEmoji, viewModel.chatId)
    LaunchedEffect(inlineDraft) { viewModel.inlineQuery(inlineDraft) }
    // Bot, který chce polohu, ji dostane přibližnou — dotaz na oprávnění přijde až u něj.
    val inlineLocation = rememberDeviceLocation()
    val locationBot = state.inline?.bot?.takeIf { it.needLocation }?.userId
    LaunchedEffect(locationBot) {
        if (locationBot != null) inlineLocation.request(viewModel::setInlineLocation)
    }
    /*
     * Napovídání při psaní PLAVE nad polem a místo v rozvržení nezabírá (viz
     * `FloatingSuggestion`): mění se s každým písmenem a konverzace pod ním nesmí
     * poskakovat. Stojí těsně nad polem, u textu, ke kterému patří. Návrhy odpovědi
     * a klávesnice bota výš zůstávají v rozvržení — viz `ReplySuggestionChips`.
     */
    // Tatáž pilulka jako pruhy nad konverzací (viz `FloatingSuggestion`).
    val suggestionGlass = pillGlass(listHaze)
    val commandsShown = state.editing == null && (typedCommands != null || state.commandsOpen)
    // Plovoucí napovídání leží tam, kde jinak stojí tlačítko dolů a zmínky — ty se
    // mezitím schovají, jinak by visely přes seznam příkazů nebo přes emoji.
    val floatingShown = (commandsShown && state.botCommands.isNotEmpty()) ||
        (mentionQuery != null && state.mentionSuggestions?.candidates.orEmpty().isNotEmpty()) ||
        (hashtagQuery != null && state.hashtagSuggestions?.hashtags.orEmpty().isNotEmpty()) ||
        (emojiWord != null && shownEmojis.isNotEmpty()) ||
        (stickerEmoji != null && shownStickers.isNotEmpty()) ||
        inlineDraft != null
    SideEffect { onSuggestionsFloating(floatingShown) }
    // Boky jako pole (`MessageInput`): naležato by pilulka jinak zajela pod výřez kamery.
    Column(
        Modifier
            .floatAbove()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        BotCommandsPanel(
            commands = typedCommands ?: state.botCommands.takeIf { state.commandsOpen }.orEmpty(),
            visible = commandsShown,
            onPick = { command ->
                haptics.confirm()
                // Rozepsané „/st" byl ten příkaz — pole se vyprázdní a koncept s ním.
                if (typedCommands != null) {
                    draft = TextFieldValue()
                    viewModel.onDraftChanged(composer.composed)
                }
                viewModel.closeCommands()
                viewModel.sendCommand(command.text)
            },
            glass = suggestionGlass,
        )
        MentionSuggestionsPanel(
            candidates = state.mentionSuggestions?.candidates.orEmpty(),
            visible = mentionQuery != null,
            onPick = { candidate ->
                val query = mentionQuery ?: return@MentionSuggestionsPanel
                val (text, cursor) = mentionInsertion(draft.text, draft.selection.start, query, candidate)
                draft = TextFieldValue(text, selection = TextRange(cursor))
                viewModel.mentionPicked(candidate)
                viewModel.onDraftChanged(composer.composed)
            },
            glass = suggestionGlass,
        )
        HashtagSuggestionsPanel(
            hashtags = state.hashtagSuggestions?.hashtags.orEmpty(),
            visible = hashtagQuery != null,
            onPick = { hashtag ->
                val query = hashtagQuery ?: return@HashtagSuggestionsPanel
                val (text, cursor) = hashtagInsertion(draft.text, draft.selection.start, query, hashtag)
                draft = TextFieldValue(text, selection = TextRange(cursor))
                viewModel.hashtagPicked()
                viewModel.onDraftChanged(composer.composed)
            },
            onRemove = viewModel::removeHashtag,
            glass = suggestionGlass,
        )
        EmojiSuggestionsPanel(
            emojis = shownEmojis,
            visible = emojiWord != null,
            onPick = { emoji ->
                val word = emojiWord ?: return@EmojiSuggestionsPanel
                // Sada k předchozímu slovu nesmí přepsat to, které už je v poli jiné.
                if (emojiSuggestions?.word != word.word) return@EmojiSuggestionsPanel
                haptics.confirm()
                val (text, cursor) = emojiReplacement(draft.text, word, emoji)
                draft = TextFieldValue(text, selection = TextRange(cursor))
                emojiWords.picked(emoji)
                viewModel.onDraftChanged(composer.composed)
            },
            glass = suggestionGlass,
        )
        StickerSuggestionsPanel(
            stickers = shownStickers,
            visible = stickerEmoji != null,
            onPick = { sticker ->
                haptics.confirm()
                viewModel.sendSticker(sticker)
                // Emoji v poli byla ta samolepka — pole se vyprázdní a koncept s ním.
                draft = TextFieldValue()
                viewModel.onDraftChanged(composer.composed)
            },
            glass = suggestionGlass,
        )
        InlineResultsPanel(
            inline = state.inline,
            visible = inlineDraft != null,
            onPick = { result ->
                haptics.confirm()
                viewModel.sendInlineResult(result)
                // Dotaz v poli byl ten výsledek — pole se vyprázdní a koncept s ním.
                draft = TextFieldValue()
                viewModel.onDraftChanged(composer.composed)
            },
            onButton = viewModel::pressInlineButton,
            onLoadMore = viewModel::loadMoreInline,
            glass = suggestionGlass,
        )
    }
    // Fotka letící z prohlížeče zajede pod kartu pole a tlačítko, ne jen za čáru nad nimi.
    CompositionLocalProvider(LocalSharedElementOcclusion provides listClip) {
    MessageInput(
        gestureHints = gestureHints,
        // AI úpravy od serveru tajný chat nesmí (dokumentace `composeTextWithAi`).
        serverAiAllowed = state.info?.isSecret != true,
        senderSlot = senderSlot(
            accounts = chatAccounts.takeIf { it.size > 1 && switchAccount != null }?.let { accounts ->
                { modifier -> AccountSwitchButton(accounts, currentSlot, { slot -> switchAccount?.invoke(slot) }, modifier) }
            },
            sender = state.messageSender?.let { choice ->
                { modifier ->
                    SendAsButton(
                        choice = choice,
                        onOpen = viewModel::refreshMessageSenders,
                        onChoose = viewModel::chooseMessageSender,
                        modifier = modifier,
                    )
                }
            },
        ),
        // Sklo má celý sloupec výš; pás pro letící fotku zůstává jen u pole — přes návrhy
        // a klávesnici bota se letět nesmí (`SharedElementClip.openBand`).
        glass = Modifier.sharedElementOcclusionBand(),
        reply = state.replyingTo?.let { target ->
            ReplyControls(
                message = target,
                quote = state.replyQuote?.text,
                // U zprávy z jiného chatu jmenuje pruh ten chat (bez jména odesílatele by
                // příchozí zpráva ze soukromého chatu nesla jméno tohohle chatu).
                partnerName = state.replyElsewhere?.takeIf { it.messageId == target.id }?.chatTitle ?: state.info?.title,
                onCancel = viewModel::clearReply,
            )
        },
        flight = state.flight,
        send = SendControls(
            // Odeslání je potvrzení akce; dosud ho doprovázel jen let bubliny. Hlásí se
            // v okamžiku ťuknutí, ne po odpovědi serveru — odezva patří ke gestu.
            onSend = { text, options ->
                haptics.confirm()
                viewModel.send(text, options)
            },
            onFlightLaunched = viewModel::flightLaunched,
            menu = sendMenuOffer(state),
            onSchedule = { sheets.scheduleTarget = ScheduleTarget.Draft },
            onPickEffect = {
                viewModel.loadEffects()
                sheets.effectPicker = true
            },
            onOpenScheduled = viewModel::openScheduled.takeIf { state.hasScheduledMessages },
            defaultSilent = state.info?.takeIf { it.isChannel && !state.narrowed }?.defaultSilent,
            onToggleDefaultSilent = viewModel::toggleDefaultSilent,
            slowModeUntil = state.slowModeUntil,
            onSlowModeWait = viewModel::explainSlowMode,
        ),
        field = ComposerField(
            draft = composer,
            onDraftChanged = viewModel::onDraftChanged,
            fill = state.inputFill,
            onFilled = viewModel::consumeInputFill,
            focusRequest = state.inputFocusRequest,
            placeholder = state.inputPlaceholder,
            onEditLast = viewModel::editLastMessage,
            account = rememberConversationAccount(viewModel.chatId)?.info,
        ),
        editing = state.editing?.let { message ->
            EditingControls(
                message = message,
                onCancel = viewModel::cancelEdit,
                media = state.editReplace?.let { replace ->
                    EditMediaControls(
                        replacement = state.editReplacement,
                        onReplace = {
                            when (replace) {
                                // Fotka a video jdou z téže galerie v sheetu jako přílohy, kde je.
                                ReplaceMedia.Visual ->
                                    if (embeddedMediaPicker) sheets.replacementPicker = true else replacementPicker.pickMedia()
                                ReplaceMedia.File -> replacementPicker.pickDocument()
                                ReplaceMedia.Gif -> sheets.gifReplacement = true
                            }
                        },
                        onKeep = viewModel::keepEditMedia,
                    )
                },
            )
        },
        linkPreview = LinkPreviewControls(
            preview = composerLinkPreview(
                preview = state.linkPreview,
                options = state.linkPreviewOptions,
                editingCaption = state.editing?.editKind() == ComposerEdit.Caption,
                hasAttachments = state.attachments.isNotEmpty(),
            ),
            links = state.linkPreviewLinks,
            options = state.linkPreviewOptions,
            onOptions = viewModel::setLinkPreviewOptions,
        ),
        expression = ExpressionButton(mode = expressionMode, onOpen = { sheets.stickerPicker = true }),
        recording = RecordingControls(
            isRecording = recording.isRecording,
            current = { recording.current() },
            mode = recordMode,
            canSwitchMode = canSwitchRecordMode(state.content.voiceNotes, recording.videoNotesAllowed(state.content)),
            onSwitchMode = {
                viewModel.setRecordMode(if (recordMode == RecordMode.VideoNote) RecordMode.Voice else RecordMode.VideoNote)
            },
            // Zastavené kolečko je pořád kolečko: odeslání se tak jmenuje i v náhledu.
            kind = if (recording.isVideoRecording || recording.videoNotePreview != null) RecordMode.VideoNote else RecordMode.Voice,
            gesture = recording.gesture,
            onStart = { held ->
                val video = recordMode == RecordMode.VideoNote
                // Odpověď bez dialogu přijde hned, uvnitř `request`; z dialogu až potom.
                var immediate = true
                (if (video) recording.videoNotePermission else recording.recordPermission).request { granted ->
                    when {
                        !granted -> {
                            haptics.reject()
                            viewModel.reportError(
                                UiText(if (video) Res.string.conversation_camera_permission else Res.string.conversation_microphone_permission),
                            )
                        }
                        // Oprávnění přišlo až z dialogu: prst mezitím tlačítko pustil, a nahrávat
                        // bez něj by bylo nahrávání, o kterém uživatel neví. Podrží znovu.
                        held && !immediate -> Unit
                        else -> {
                            // Nahrávání běží, dokud ho uživatel neukončí — přepnutý stav, ne
                            // hotová akce. Odepřené oprávnění je naopak odmítnutí.
                            haptics.toggle(on = true)
                            if (video) viewModel.startVideoRecording() else viewModel.startRecording()
                        }
                    }
                }
                immediate = false
            },
            onCancel = {
                haptics.reject()
                viewModel.cancelRecording()
            },
            onStop = {
                // Mikrofon se zavírá — přepnutý stav, jako při spuštění.
                haptics.toggle(on = false)
                viewModel.stopRecordingForPreview()
            },
            onSend = { options ->
                haptics.confirm()
                viewModel.sendRecording(options)
            },
            // Nabídka nad hlasovkou nahrávání pozastaví, zavření bez volby ho obnoví.
            onMenu = { open -> if (open) viewModel.pauseRecording() else viewModel.resumeRecording() },
            onSchedule = { sheets.scheduleTarget = ScheduleTarget.Voice },
        ),
        voicePreview = recording.voicePreview?.let { preview ->
            VoicePreviewControls(
                preview = preview,
                playback = { voicePlayback.value?.takeIf { it.key == voicePreviewKey(viewModel.slot, viewModel.chatId) } },
                onToggle = viewModel::toggleVoicePreview,
                onSeek = viewModel::seekVoicePreview,
                onDiscard = {
                    haptics.reject()
                    viewModel.discardVoicePreview()
                },
                onContinue = {
                    haptics.toggle(on = true)
                    viewModel.continueVoicePreview()
                },
            )
        },
        videoNotePreview = recording.videoNotePreview?.let { preview ->
            VideoNotePreviewControls(
                preview = preview,
                playback = videoNotePlayback,
                onDiscard = {
                    haptics.reject()
                    viewModel.discardVideoNotePreview()
                },
                onContinue = {
                    haptics.toggle(on = true)
                    viewModel.continueVideoNotePreview()
                },
            )
        },
        attach = AttachActions(
            onPickMedia = { if (embeddedMediaPicker) sheets.mediaPicker = true else attachmentPicker.pickMedia() },
            onPickDocument = attachmentPicker::pickDocument,
            // Anketa jde jen tam, kde to dovolí právo i druh chatu (`ContentAccess.polls`).
            onCreatePoll = {
                navigation.onCreatePoll(PollComposerRequest(PollComposerMode.Any, state.replyingTo?.id, state.replyQuote))
            }.takeIf { state.content.polls },
            // Seznam úkolů jde tam, kde to dovolí právo i druh chatu (`ContentAccess.checklists`), a jen s Premium.
            onCreateChecklist = {
                navigation.onCreateChecklist(ChecklistComposerRequest(state.replyingTo?.id, state.replyQuote))
            }.takeIf { state.content.checklists && isPremium },
            // Poloha je „základní zpráva" jako text: smí se všude, kde je vstupní pole.
            onSendLocation = { sheets.locationPicker = true },
            // Kontakt je taky „základní zpráva" (TDLib ho řadí k textu), jako poloha.
            onShareContact = viewModel::openContactPicker,
            // Znovu poslaný soubor je soubor — jen kde chat soubory dovolí, jako „Soubor" v nabídce.
            onPickRecentFiles = viewModel::openRecentFiles.takeIf { state.content.documents },
            // Fotka a video z fotoaparátu jdou stejnou cestou jako z galerie: nad pole, k popisku.
            onCapture = if (attachmentPicker.canCapture) {
                { video -> if (video) attachmentPicker.captureVideo() else attachmentPicker.capturePhoto() }
            } else {
                null
            },
        ),
        bot = BotControls(
            menuApp = state.botMenuApp,
            onOpenMenuApp = viewModel::openBotMenuApp,
            onToggleCommands = viewModel::toggleCommands.takeIf { state.botCommands.isNotEmpty() },
        ),
        attachments = ComposerAttachments(
            items = state.attachments,
            // Vykreslovaná úprava fotky blokuje odeslání stejně jako kopírování z galerie.
            preparing = mediaSelection.preparing || state.markupBusy,
            onRemove = viewModel::removeAttachment,
            onOpen = { index -> sheets.previewAttachmentIndex = index },
            onSetMode = viewModel::setAttachmentMode,
            selfDestructAllowed = state.canSelfDestruct(attachmentCount = 1),
            previewIndex = sheets.previewAttachmentIndex,
            separately = state.attachmentsSeparately,
            onToggleSeparately = viewModel::toggleAttachmentsSeparately,
            captionSuggestion = state.captionSuggestion,
            onSuggestCaption = viewModel::suggestCaption,
        ),
        content = state.content,
        keyboardContent = KeyboardContentHandlers(
            onContent = { received ->
                when (received) {
                    // GIF odchází hned, jako samolepka; obrázek čeká nad polem jako z výběru.
                    is KeyboardContent.Animation -> viewModel.sendAnimation(received.animation)
                    is KeyboardContent.Image -> viewModel.addAttachments(listOf(received.photo))
                    is KeyboardContent.File -> viewModel.addAttachments(listOf(received.document))
                    is KeyboardContent.Attachments -> viewModel.addAttachments(received.attachments)
                }
            },
            onFailed = viewModel::reportError,
        ),
    )
    }
    }
    }
}

/**
 * Levé místo u pole: výběr účtu spojené společné skupiny ([accounts]) a výběr odesílatele
 * skupiny ([sender]), každý jen když je. Oba naráz (ve společné skupině, kde jde psát i za
 * kanál) stojí vedle sebe: napřed účet, pak kým v něm.
 */
private fun senderSlot(
    accounts: (@Composable (Modifier) -> Unit)?,
    sender: (@Composable (Modifier) -> Unit)?,
): (@Composable (Modifier) -> Unit)? = when {
    accounts != null && sender != null -> { modifier ->
        Row(modifier) {
            accounts(Modifier)
            sender(Modifier)
        }
    }
    else -> accounts ?: sender
}
