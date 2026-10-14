package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.edit_note_24dp
import eu.tintera.plume.resources.conversation_send_rewrite
import eu.tintera.plume.core.ui.isFromHardwareKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import eu.tintera.plume.core.ui.threshold
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import eu.tintera.plume.core.ui.RootTooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.resources.conversation_record_hint_video_note
import eu.tintera.plume.resources.conversation_record_hint_voice
import eu.tintera.plume.resources.conversation_record_video_note
import eu.tintera.plume.resources.conversation_send_video_note
import eu.tintera.plume.resources.videocam_24dp
import eu.tintera.plume.core.telegram.codeBlockAt
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.linkAt
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull
import eu.tintera.plume.core.ui.rememberAfterKeyboardHidden
import eu.tintera.plume.core.ui.longPress
import eu.tintera.plume.core.telegram.BotMenuApp
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.resources.conversation_send_silent
import eu.tintera.plume.resources.conversation_send_schedule
import eu.tintera.plume.resources.conversation_send_when_online
import eu.tintera.plume.resources.schedule_send_24dp
import eu.tintera.plume.resources.online_prediction_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.conversation_scheduled_open
import androidx.compose.ui.platform.LocalHapticFeedback
import eu.tintera.plume.core.ui.occludesSharedElements
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.core.telegram.AttachmentMode
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ime
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import eu.tintera.plume.core.ui.ClipboardMediaKind
import eu.tintera.plume.core.ui.ClipboardPasteSuggestion
import eu.tintera.plume.core.ui.KeyboardContent
import eu.tintera.plume.core.ui.KeyboardContentReceiver
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.semantics.contentDescription
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import eu.tintera.plume.features.conversation.items.WavyShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import eu.tintera.plume.core.ui.animateMorph
import eu.tintera.plume.core.ui.morphSpring
import eu.tintera.plume.core.ui.ShapeMorphs
import eu.tintera.plume.core.ui.MorphShape
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import eu.tintera.plume.resources.smart_toy_24dp
import eu.tintera.plume.resources.conversation_bot_commands
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import eu.tintera.plume.core.telegram.TextFormat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.settings.GestureHint
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.LinkPreview
import androidx.compose.ui.layout.ContentScale
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.resources.link_preview_remove
import eu.tintera.plume.resources.link_preview_options
import eu.tintera.plume.resources.tune_24dp
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.strongestAccentOn
import eu.tintera.plume.core.ui.tintedSurface
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.features.conversation.items.BUBBLE_CORNER
import eu.tintera.plume.features.conversation.items.QuotedMessage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.conversation_send_effect
import eu.tintera.plume.resources.share_preparing
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.conversation_cancel_edit
import eu.tintera.plume.resources.conversation_cancel_reply
import eu.tintera.plume.resources.clipboard_has_gif
import eu.tintera.plume.resources.clipboard_has_image
import eu.tintera.plume.resources.conversation_editing
import eu.tintera.plume.resources.conversation_editing_caption
import eu.tintera.plume.resources.conversation_open_gifs
import eu.tintera.plume.resources.gif_box_24dp
import eu.tintera.plume.resources.conversation_input_caption
import eu.tintera.plume.resources.conversation_input_message
import eu.tintera.plume.resources.conversation_open_emoji
import eu.tintera.plume.resources.conversation_open_stickers
import eu.tintera.plume.resources.conversation_record_voice
import eu.tintera.plume.resources.conversation_save_edit
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.conversation_send_voice
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.mood_24dp
import eu.tintera.plume.resources.send_24dp
import eu.tintera.plume.resources.sticky_note_2_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MessageInput(
    /** Mléčné sklo nad seznamem: pás je průhledný, barvu i rozostření nese sklo. */
    glass: Modifier,
    replyingTo: ChatMessage?,
    /** Citovaný úryvek [replyingTo], nebo `null` (odpověď na celou zprávu). */
    replyQuote: String?,
    partnerName: String?,
    flight: MessageFlight?,
    onCancelReply: () -> Unit,
    onSend: (ComposedText, SendOptions) -> Unit,
    /** Co nabídne podržení odesílacího tlačítka ([sendMenuOffer]). */
    sendMenu: SendMenuOffer = SendMenuOffer(silent = false, schedule = false, whenOnline = false),
    /** „Naplánovat…" z nabídky; výběr času i odeslání řeší obrazovka, která drží text. */
    onSchedule: () -> Unit = {},
    /** Podržení → „Odeslat s efektem…": otevře výběr efektu. */
    onPickEffect: () -> Unit = {},
    /** Otevře seznam naplánovaných zpráv; `null`, když chat žádné nemá. */
    onOpenScheduled: (() -> Unit)? = null,
    /**
     * Jen ruční úpravy textu a formátování — vyprázdnění po odeslání se nehlásí, o to se stará
     * `onSend`.
     */
    onDraftChanged: (ComposedText) -> Unit,
    /** Upravovaná zpráva; karta pak místo citace ukazuje, co se upravuje. */
    editing: ChatMessage?,
    onCancelEdit: () -> Unit,
    /** Nápověda do prázdného pole, kterou si přeje bot; `null` = výchozí. */
    placeholder: String? = null,
    /** Náhled odkazu v rozepsaném textu, pokud se má ukázat ([composerLinkPreview]). */
    linkPreview: LinkPreview? = null,
    /** Odkazy v rozepsaném textu; s víc než jedním nabídne sheet, ke kterému náhled patří. */
    linkPreviewLinks: List<String> = emptyList(),
    linkPreviewOptions: LinkPreviewOptions = LinkPreviewOptions(),
    onLinkPreviewOptions: (LinkPreviewOptions) -> Unit = {},
    /** Text, který se má jednou vložit do pole (koncept, úprava). */
    inputFill: InputFill?,
    onInputFilled: (InputFill) -> Unit,
    onOpenStickers: () -> Unit,
    onPickMedia: () -> Unit,
    onPickDocument: () -> Unit,
    /** Založit anketu; `null`, kde anketa nejde. */
    onCreatePoll: (() -> Unit)?,
    /** Poslat polohu; `null`, kde nejde. */
    onSendLocation: (() -> Unit)?,
    onShareContact: (() -> Unit)?,
    /** Seznam příkazů botů; `null` v chatu bez botů s příkazy. */
    onToggleCommands: (() -> Unit)?,
    /** Tlačítko nabídky bota s mini aplikací; když je, stojí místo tlačítka příkazů. */
    menuApp: BotMenuApp? = null,
    onOpenMenuApp: () -> Unit = {},
    isRecording: Boolean,
    /** Čte se až v pruhu nahrávání — čas a úrovně se mění dvacetkrát za vteřinu. */
    recording: () -> VoiceRecording?,
    /**
     * Co nahraje podržení tlačítka v prázdném poli ([recordModeFor]); `null` = nic, tlačítko
     * je pak odeslání.
     */
    recordMode: RecordMode?,
    /** Ťuknutí na tlačítko přepne hlasovku ↔ kolečko ([canSwitchRecordMode]). */
    canSwitchRecordMode: Boolean,
    onSwitchRecordMode: () -> Unit,
    /** Co se právě nahrává — podle toho popis tlačítka odeslání. */
    recordingKind: RecordMode,
    /** Gesto nahrávání; drží ho obrazovka, protože zámek nad tlačítkem kreslí ona. */
    recordGesture: RecordGestureState,
    /**
     * Začne nahrávat. [held] = prst drží tlačítko; když se oprávnění teprve žádá, nahrávat se
     * potom nesmí (prst mezitím tlačítko pustil). Bez prstu (čtečka obrazovky) se začne zamčené.
     */
    onStartRecording: (held: Boolean) -> Unit,
    onCancelRecording: () -> Unit,
    /**
     * Zastavená hlasovka k poslechu před odesláním; `null` = žádná. Karta s ní vypadá jako při
     * nahrávání (vlnka, barva, odeslání hlasu), jen místo živých úrovní ukazuje celý waveform.
     */
    voicePreview: VoicePreview? = null,
    /** Přehrávání náhledu; čte se až v pruhu náhledu. */
    voicePreviewPlayback: () -> VoicePlayback? = { null },
    /** ■ u zamčené hlasovky: ukončí nahrávání a místo odeslání ukáže náhled. */
    onStopRecording: () -> Unit = {},
    onToggleVoicePreview: () -> Unit = {},
    onSeekVoicePreview: (Float) -> Unit = {},
    onDiscardVoicePreview: () -> Unit = {},
    onContinueVoicePreview: () -> Unit = {},
    /** Zastavené kolečko; samo hraje v kruhu nad konverzací, karta ukazuje jen ovládání. */
    videoNotePreview: VideoNotePreview? = null,
    videoNotePlayback: VideoNotePreviewPlayback? = null,
    onDiscardVideoNotePreview: () -> Unit = {},
    onContinueVideoNotePreview: () -> Unit = {},
    onSendRecording: (SendOptions) -> Unit,
    /** Nabídka odeslání nad hlasovkou se otevřela (`true`) nebo zavřela bez volby (`false`). */
    onVoiceMenu: (open: Boolean) -> Unit = {},
    /** „Naplánovat…" nad hlasovkou; výběr času řeší obrazovka, nahrávání je mezitím pozastavené. */
    onScheduleRecording: () -> Unit = {},
    /** Přílohy čekající na odeslání; text pole je pak jejich popisek. */
    attachments: List<OutgoingAttachment>,
    /**
     * Vybrané přílohy se ještě kopírují (velké video trvá vteřiny). Bez ukazatele by se po
     * zavření galerie chvíli nedělo nic a vypadalo by to, že výběr nezabral.
     */
    attachmentsPreparing: Boolean = false,
    onRemoveAttachment: (Int) -> Unit,
    onOpenAttachment: (Int) -> Unit = {},
    onSetAttachmentMode: (Int, AttachmentMode) -> Unit = { _, _ -> },
    /** Soukromý chat, kde se u jedné přílohy smí nabídnout mizení (`canSelfDestruct`). */
    selfDestructAllowed: Boolean = false,
    previewAttachmentIndex: Int? = null,
    /** Ikona tlačítka výběru ukazuje, čím se výběr otevře — emoji, nebo samolepkami. */
    expressionMode: ExpressionMode,
    /** Rozepsaný text i s formátováním; drží ho obrazovka, protože do něj vkládá i výběr emoji. */
    composer: ComposerDraft,
    /**
     * Které druhy obsahu chat přijme. Co se poslat nesmí, se nenabízí: sponka schová položku
     * (a bez jediné i sama sebe), mikrofon ustoupí odeslání, samolepky zůstanou na emoji.
     */
    content: ContentAccess = ContentAccess.All,
    /** GIF nebo obrázek vložený z klávesnice (Gboard), už zkopírovaný do cache. */
    onKeyboardContent: (KeyboardContent) -> Unit = {},
    onKeyboardContentFailed: (UiText) -> Unit = {},
    /** Nápovědy gest napoprvé; pole do nich hlásí, co právě dělá tlačítko odeslání. */
    gestureHints: GestureHints? = null,
    modifier: Modifier = Modifier,
) {
    var draft by composer
    /*
     * Přepis konceptu modelem v zařízení („Přepsat…" v nabídce odeslání). Žije tady, ne
     * v `ConversationScreen`, který je v dexu na hraně (viz „Gotchas" v CLAUDE.md). Přepsaný
     * text se jen VLOŽÍ do pole, jako návrh odpovědi; formátování konceptu se zahodí — model
     * vrací holý text a rozsahy by na něm ujely.
     */
    val rewrite = rememberDraftRewrite()
    DraftRewriteSheet(rewrite) { text ->
        composer.fill(text, emptyList())
        onDraftChanged(composer.composed)
    }
    // Gesto nahrávání (viz `holdToRecord` a `RecordGestureState`).
    var recordHeld by recordGesture::held
    var recordLocked by recordGesture::locked
    val recordDrag = recordGesture.drag
    LaunchedEffect(isRecording) { if (!isRecording) recordLocked = false }
    val recordHolding = isRecording && recordHeld && !recordLocked
    // Hlas ve vstupní kartě: nahrává se, nebo je nahrávka zastavená k poslechu. Karta i tlačítko
    // se v obou stavech chovají stejně; liší se jen pruh a to, že náhled nedýchá (nic neběží).
    val voiceActive = isRecording || voicePreview != null || videoNotePreview != null
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    /*
     * Úsek, ke kterému se právě zadává adresa odkazu. Zachycený při ťuknutí na „Odkaz": dialog
     * vezme poli fokus a výběr by se na něj nedal spolehnout.
     */
    // Blok kódu, kterému se nastavuje jazyk — úsek z doby ťuknutí, jako u odkazu.
    var languageTarget by rememberSaveable { mutableStateOf<List<Int>?>(null) }
    languageTarget?.let { (start, end) ->
        CodeLanguageDialog(
            current = composer.formats.codeBlockAt(start, end)?.language,
            onConfirm = { language ->
                composer.setCodeLanguage(start, end, language)
                onDraftChanged(composer.composed)
                languageTarget = null
            },
            onDismiss = { languageTarget = null },
        )
    }
    var linkTarget by rememberSaveable { mutableStateOf<List<Int>?>(null) }
    linkTarget?.let { (start, end) ->
        LinkDialog(
            current = composer.formats.linkAt(start, end),
            onConfirm = { url ->
                composer.link(start, end, url)
                onDraftChanged(composer.composed)
                linkTarget = null
            },
            onDismiss = { linkTarget = null },
        )
    }
    // Rozložení textu v poli a výška jeho viditelné části — podle nich se kreslí vlastní emoji
    // (`CustomEmojiOverlay`), jen když se text do pole vejde celý.
    var fieldLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var fieldViewport by remember { mutableStateOf(0) }
    val hasCustomEmoji = composer.formats.any { it.format == TextFormat.CustomEmoji }
    val customEmojiShown = hasCustomEmoji && customEmojiOverlayFits(fieldLayout, fieldViewport)
    val formatting = FormattingTransformation(
        formats = composer.formats,
        contentColor = MaterialTheme.colorScheme.onSurface,
        accentColor = MaterialTheme.colorScheme.primary,
        hideCustomEmoji = customEmojiShown,
    )

    /*
     * Vložení textu zvenku. Koncept z minula jde JEN do prázdného pole — než ho TDLib vydá, mohl
     * uživatel začít psát. Úprava zprávy pole přepisuje vždycky. Kurzor na konec, ať se píše dál.
     */
    LaunchedEffect(inputFill) {
        val fill = inputFill ?: return@LaunchedEffect
        if (!fill.onlyIfEmpty || draft.text.isEmpty()) composer.fill(fill.text, fill.formats)
        onInputFilled(fill)
    }

    // Úprava zprávy přílohy schová — odeslání by je stejně neposlalo.
    val withAttachments = attachments.isNotEmpty() && editing == null
    val keyboard = LocalSoftwareKeyboardController.current
    val afterKeyboardHidden = rememberAfterKeyboardHidden()
    // Nahoře, ne u pole: podle fokusu se ukazuje i návrh vložení ze schránky nad polem.
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()
    // Píše se: pole má fokus a klávesnice je vidět. Fokus sám nestačí — po schování klávesnice
    // ho pole drží dál. Výška klávesnice funguje i na iOS (`isImeVisible` je jen na Androidu).
    val ime = WindowInsets.ime
    val keyboardShown by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val typing = fieldFocused && keyboardShown && !voiceActive
    // Lišta formátování bez výběru (ikona „Aa"). Se schováním klávesnice se zavře — bez ní
    // není co formátovat a ikona může být mezitím zpátky sponkou.
    var formattingOpen by rememberSaveable { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    var fieldHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(typing) { if (!typing) formattingOpen = false }

    var linkOptionsOpen by rememberSaveable { mutableStateOf(false) }
    // Náhled, který zmizel (smazaný odkaz, odeslaná zpráva), si sheet nenechá otevřený.
    if (linkOptionsOpen && linkPreview != null) {
        LinkPreviewOptionsSheet(
            preview = linkPreview,
            links = linkPreviewLinks,
            options = linkPreviewOptions,
            onChange = onLinkPreviewOptions,
            onDismiss = { linkOptionsOpen = false },
        )
    }

    /*
     * Pruh má barvu POZADÍ, jen průsvitnou.
     *
     * Zprávy pod něj zajíždějí (padding `bottomBar` jde do `contentPadding` seznamu, viz výše)
     * a bez pruhu by se pod kartou
     * míchaly s textem. Plná barva by zase udělala ostrý předěl přes celou obrazovku. Průsvit
     * dělá závoj: je vidět, že tam obsah je, ale nepřebíjí psaní.
     */
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.then(glass),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                /*
                 * Spodek (lišta, klávesnice) i boky. Naležato leží na boku výřez kamery nebo
                 * navigační lišta se třemi tlačítky a tlačítko odeslání by jinak zajelo pod ně.
                 * Levý inset vedle railu už zkonzumoval shell.
                 */
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            /*
             * JEDNA karta na citaci i na psaní.
             *
             * Dřív to byly dva samostatné kusy nad sebou a odpověď působila jako cizí prvek,
             * co se vsunul mezi konverzaci a vstup. Takhle je vidět, že citace patří k tomu,
             * co se právě píše.
             *
             * Pole uvnitř je průhledné — kontejner nese karta, ne ono; dvě zaoblené plochy
             * v sobě by vypadaly jako chyba.
             */
            /*
             * Při nahrávání se karta ROZVLNÍ a přebarví. Pruh nahrávání vypadá jinak než pole,
             * ale na místě zůstává — bez změny karty se dal přehlédnout otevřený mikrofon.
             * Vlnka je v aplikaci „hlas": stejně (jen mělčeji a bez pohybu) jsou zvlněné
             * hlasovky v konverzaci. Stojí, neobíhá — živost nese dýchající tlačítko vedle.
             */
            val recordingWave by animateFloatAsState(
                targetValue = if (voiceActive) 1f else 0f,
                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                label = "composer-recording-wave",
            )
            val cardColor by animateColorAsState(
                targetValue = if (voiceActive) {
                    // Hlas je v konverzaci terciární (viz tlačítko vedle), takže i karta při nahrávání.
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    composerCardColor()
                },
                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                label = "composer-recording-color",
            )
            val cardShape = if (recordingWave > 0f) {
                WavyShape(ComposerShape, progress = recordingWave, phase = 0f)
            } else {
                ComposerShape
            }
            Surface(
                shape = cardShape,
                color = cardColor,
                modifier = Modifier.weight(1f).occludesSharedElements(cardShape),
            ) {
              Column {
                // Úprava má přednost před citací: odpověď se při ní neruší, jen se na chvíli schová.
                if (editing != null) {
                    EditingHeader(message = editing, onCancel = onCancelEdit)
                } else {
                  AnimatedVisibility(
                      visible = attachmentsPreparing,
                      enter = fadeIn() + expandVertically(),
                      exit = fadeOut() + shrinkVertically(),
                  ) {
                      AttachmentsPreparing()
                  }
                  StagedAttachmentsStrip(
                      attachments = attachments,
                      onRemove = onRemoveAttachment,
                      onOpen = onOpenAttachment,
                      onSetMode = onSetAttachmentMode,
                      selfDestructAllowed = selfDestructAllowed,
                      previewAttachmentIndex = previewAttachmentIndex,
                  )
                  replyingTo?.let { target ->
                    // Citace je horní pruh karty, barevně oddělený — stejně jako na kartě zprávy,
                    // ne subkartička v kartě.
                    ComposerHeader {
                        QuotedMessage(
                            message = target,
                            partnerName = partnerName,
                            accent = composerHeaderAccent(),
                            contained = false,
                            quoteText = replyQuote,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onCancelReply, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painterResource(Res.drawable.close_24dp),
                                contentDescription = stringResource(Res.string.conversation_cancel_reply),
                            )
                        }
                    }
                  }
                }
                  /*
                   * Náhled odkazu je poslední pruh nad polem, nejblíž textu, ke kterému patří.
                   * Mizí a objevuje se se svým místem — naskočí po pauze v psaní a skok by pole
                   * pod prstem posunul. Patří i k úpravě textu: upravená zpráva odejde s ním.
                   */
                  AnimatedVisibility(
                      visible = linkPreview != null,
                      enter = fadeIn() + expandVertically(),
                      exit = fadeOut() + shrinkVertically(),
                  ) {
                      // Při odjezdu je náhled už `null`; dobíhá se s posledním.
                      var shown by remember { mutableStateOf(linkPreview) }
                      if (linkPreview != null) shown = linkPreview
                      shown?.let { preview ->
                          LinkPreviewHeader(
                              preview = preview,
                              onOpenOptions = { linkOptionsOpen = true },
                              onDismiss = { onLinkPreviewOptions(linkPreviewOptions.copy(disabled = true)) },
                          )
                      }
                  }
                if (editing == null) {
                  /*
                   * GIF nebo obrázek ve schránce: na iOS nabídne vložení systémovým tlačítkem,
                   * které se na povolení neptá (viz `ClipboardPasteSuggestion`). Jen s fokusem
                   * v poli — návrh patří k psaní, jako návrhy klávesnice.
                   */
                  ClipboardPasteSuggestion(
                      active = fieldFocused && !voiceActive,
                      acceptAnimations = content.stickers,
                      acceptImages = content.photos,
                      onContent = onKeyboardContent,
                      onError = onKeyboardContentFailed,
                  ) { kind, pasteButton ->
                      ComposerHeader {
                          Text(
                              text = stringResource(
                                  when (kind) {
                                      ClipboardMediaKind.Animation -> Res.string.clipboard_has_gif
                                      ClipboardMediaKind.Image -> Res.string.clipboard_has_image
                                  },
                              ),
                              style = MaterialTheme.typography.labelLarge,
                              modifier = Modifier.weight(1f).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                          )
                          pasteButton()
                      }
                  }
                }

                // Při nahrávání místo pole pruh s časem; citace nad ním zůstává, hlasovka na ni odpoví.
                if (videoNotePreview != null && videoNotePlayback != null) {
                    VideoNotePreviewBar(
                        preview = videoNotePreview,
                        playback = videoNotePlayback,
                        onDiscard = onDiscardVideoNotePreview,
                        // Jako u hlasovky: ťuknutím, takže nahrávání je rovnou zamčené.
                        onContinue = {
                            recordLocked = true
                            onContinueVideoNotePreview()
                        },
                    )
                } else if (voicePreview != null) {
                    VoicePreviewBar(
                        preview = voicePreview,
                        playback = voicePreviewPlayback,
                        onToggle = onToggleVoicePreview,
                        onSeek = onSeekVoicePreview,
                        onDiscard = onDiscardVoicePreview,
                        // Spouští se ťuknutím, ne podržením: nahrávání je rovnou zamčené.
                        onContinue = {
                            recordLocked = true
                            onContinueVoicePreview()
                        },
                    )
                } else if (isRecording) {
                    RecordingBar(
                        recording = recording,
                        onCancel = onCancelRecording,
                        holding = recordHolding,
                        cancelProgress = { cancelProgress(recordDrag.value.x, with(density) { RECORD_CANCEL_DISTANCE.toPx() }) },
                        onStop = onStopRecording,
                        video = recordingKind == RecordMode.VideoNote,
                    )
                } else Row(verticalAlignment = Alignment.Bottom) {
            /*
             * Ikony stojí VEDLE pole, ne v jeho `leadingIcon` / `trailingIcon`. `TextField`
             * sloty centruje na výšku pole, takže s víceřádkovou zprávou ikony ujížděly doprostřed;
             * tady se drží dole u posledního řádku, stejně jako odesílací tlačítko vedle karty.
             * Odsazení 4 dp je (56 − 48) / 2: tlačítko pak sedí na střed jednořádkového pole.
             */
            // Přílohy jen při psaní — k úpravě textové zprávy příloha nepatří.
            // Poloha jde všude, kde se smí psát, takže sponka zůstává i ve skupině bez příloh.
            // Příkazy bota: v chatu s botem je to hlavní ovládání, jako „Menu" u oficiálních klientů.
            // Bot s mini aplikací v nabídce ji má na tom místě místo příkazů (ty dál napovídá „/").
            val botButton = editing == null && (menuApp != null || onToggleCommands != null)
            if (editing == null && menuApp != null) {
                BotMenuAppButton(
                    text = menuApp.text,
                    compact = draft.text.isNotBlank() || attachments.isNotEmpty(),
                    iconColor = inputIconColor(),
                    onClick = onOpenMenuApp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            } else if (editing == null && onToggleCommands != null) {
                IconButton(
                    onClick = onToggleCommands,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = inputIconColor()),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painterResource(Res.drawable.smart_toy_24dp),
                        contentDescription = stringResource(Res.string.conversation_bot_commands),
                    )
                }
            }
            // Sponka, nebo „Aa" — viz ComposerStartSlot. Smajlík má místo vpravo vždycky.
            val attachColor = inputIconColor()
            ComposerStartSlot(
                attach = if (editing == null && (content.anyAttachment || onSendLocation != null)) {
                    {
                        AttachButton(
                            onPickMedia = onPickMedia,
                            onPickDocument = onPickDocument,
                            // Skupina může povolit fotky a zakázat soubory; nabídne se jen dovolené.
                            canPickMedia = content.photos || content.videos,
                            canPickDocument = content.documents,
                            onCreatePoll = onCreatePoll,
                            onSendLocation = onSendLocation,
                            onShareContact = onShareContact,
                            contentColor = attachColor,
                        )
                    }
                } else {
                    null
                },
                formatAuto = typing && draft.text.isNotEmpty(),
                typing = typing,
                formattingShown = (typing && formattingOpen) || !draft.selection.collapsed,
                onToggleFormatting = { formattingOpen = !formattingOpen },
                onFormatWithoutKeyboard = {
                    // Lišta se ukáže, až klávesnice vyjede (kreslí se jen při psaní).
                    formattingOpen = true
                    fieldFocus.requestFocus()
                    keyboard?.show()
                },
                fieldHeight = fieldHeight,
                contentColor = attachColor,
                // Za tlačítkem příkazů bez odsazení: glyfy mají kolem sebe vzduch samy.
                modifier = Modifier.padding(start = if (botButton) 0.dp else 4.dp, bottom = 4.dp),
            )
            // Výška pole říká levému místu, jestli se ikony vejdou pod sebe.
            Box(Modifier.weight(1f).onSizeChanged { fieldHeight = with(density) { it.height.toDp() } }) {
            /*
             * `BasicTextField` s dekorací, ne `TextField`: ten má vnitřní odsazení 16 dp napevno
             * a vedle sponky (48dp tlačítko, glyf má kolem sebe dalších 12 dp) z toho byla
             * mezera skoro 30 dp, než začal text. U ikony stačí 4 dp — glyf má svůj vzduch.
             * Vlevo je ikona vždycky — sponka, nebo „Aa" (i při úpravě zprávy).
             */
            val fieldColors = TextFieldDefaults.colors(
                // Kontejner i podtržení nese karta kolem; pole samo nekreslí nic.
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
            // Klávesnice nabízí GIFy a samolepky jen poli, které řekne, že je přijme.
            // Při úpravě zprávy ne — k upravovanému textu příloha nepatří, stejně jako sponka.
            KeyboardContentReceiver(
                acceptAnimations = editing == null && content.stickers,
                acceptImages = editing == null && content.photos,
                acceptFiles = editing == null && content.documents,
                onContent = onKeyboardContent,
                onError = onKeyboardContentFailed,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        val changed = it.text != draft.text
                        draft = it
                        // Pohyb kurzoru nebo výběr taky volá `onValueChange`; psaní to není.
                        if (changed) onDraftChanged(composer.composed)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = TextFieldDefaults.MinHeight)
                        .focusRequester(fieldFocus)
                        /*
                         * Enter z fyzické klávesnice (Fold, tablet) odešle, Shift+Enter udělá nový
                         * řádek — jako na počítači. Enter ze softwarové klávesnice dál zalomí řádek,
                         * odeslání je tam tlačítko. Prázdnou zprávu Enter nepošle, ale ani nezalomí:
                         * prázdný řádek navíc by nikdo nechtěl. Podmínky jsou tytéž jako u tlačítka.
                         */
                        .onPreviewKeyEvent { event ->
                            val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                            if (!enter || event.isShiftPressed || !event.isFromHardwareKeyboard) {
                                return@onPreviewKeyEvent false
                            }
                            val sendable = !voiceActive && !attachmentsPreparing &&
                                (draft.text.isNotBlank() || withAttachments || editing?.composerEdit() == ComposerEdit.Caption)
                            if (event.type == KeyEventType.KeyDown && sendable) {
                                onSend(composer.composed, SendOptions())
                                draft = TextFieldValue()
                            }
                            true
                        },
                    visualTransformation = formatting,
                    onTextLayout = { fieldLayout = it },
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    interactionSource = fieldInteraction,
                    keyboardOptions = KeyboardOptions(
                        // Zpráva jsou věty, ne jedno slovo: klávesnice má po tečce nabídnout velké
                        // písmeno sama. Výchozí `None` nutí uživatele přepínat shift ručně.
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onSend(composer.composed, SendOptions())
                            draft = TextFieldValue()
                        },
                    ),
                    decorationBox = { innerTextField ->
                        TextFieldDefaults.DecorationBox(
                            value = draft.text,
                            innerTextField = {
                                Box(Modifier.onSizeChanged { fieldViewport = it.height }) {
                                    innerTextField()
                                    if (customEmojiShown) CustomEmojiOverlay(fieldLayout, composer.formats)
                                }
                            },
                            enabled = true,
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = fieldInteraction,
                            // S přílohami je text jejich popisek.
                            // Nápověda od bota platí jen pro zprávu; popisek příloh je jiná věc.
                            placeholder = {
                                Text(
                                    when {
                                        withAttachments -> stringResource(Res.string.conversation_input_caption)
                                        placeholder != null && editing == null -> placeholder
                                        else -> stringResource(Res.string.conversation_input_message)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = fieldColors,
                            contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                                // Vlevo je vždycky ikona (sponka, nebo „Aa").
                                start = FIELD_ICON_GAP,
                                end = FIELD_ICON_GAP,
                            ),
                            container = {},
                        )
                    },
                )
            }

            // Startovní strana letu: **už vypadá jako bublina**, ne jen jako text. Sdílí se celý
            // kontejner, takže po cestě roste i pozadí — kdyby se sdílel jen text, doletěl by
            // sám a bublina by pod ním na konci skokem naskočila.
            //
            // Kreslí se přes vyprázdněné pole, takže odeslání vypadá, jako by se napsaný text
            // proměnil v bublinu a odletěl.
            flight?.let { current ->
                Surface(
                    // MUSÍ sedět s barvou cílové bubliny, jinak zpráva po cestě změní barvu.
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // Odesílaná zpráva je vždycky nejnovější, takže pod ní nic nenavazuje
                    // a všechna nároží jsou zaoblená. Dřív tu bylo spodní stažené, protože takové
                    // bylo i v bublině; se seskupením podle obou sousedů to platit přestalo.
                    shape = RoundedCornerShape(BUBBLE_CORNER),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .sharedFlight(current.sharedKey, visible = current.itemKey == null),
                ) {
                    Text(
                        text = current.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            }
            // Naplánované zprávy: hodiny u pole, dokud nějaké čekají — do historie nepatří,
            // takže jinak by o nich nebylo vidět nic.
            if (editing == null && onOpenScheduled != null) {
                IconButton(
                    onClick = onOpenScheduled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = inputIconColor()),
                    modifier = Modifier.padding(bottom = 4.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painterResource(Res.drawable.schedule_24dp),
                        contentDescription = stringResource(Res.string.conversation_scheduled_open),
                    )
                }
            }
            // Výběr samolepek, emoji a GIFů je tu VŽDYCKY, i s otevřenou klávesnicí — potřebuje
            // se hlavně uprostřed psaní. „Aa" si místo vyměňuje se sponkou (ComposerStartSlot).
            IconButton(
                onClick = {
                    // Sheet a klávesnice by se jinak přetahovaly o spodek obrazovky; sheet je
                    // další okno, takže otevřený hned by klávesnici utrhl skokem.
                    afterKeyboardHidden(onOpenStickers)
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = inputIconColor()),
                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                shapes = IconButtonDefaults.shapes(),
            ) {
                // Ikona slibuje, co se otevře: poslední použitou část výběru. Při úpravě
                // zprávy jde otevřít jen emoji — samolepka do textu upravované zprávy nepatří.
                when {
                    editing != null || !content.stickers || expressionMode == ExpressionMode.Emoji ->
                        Icon(painterResource(Res.drawable.mood_24dp), contentDescription = stringResource(Res.string.conversation_open_emoji))
                    expressionMode == ExpressionMode.Gifs ->
                        Icon(painterResource(Res.drawable.gif_box_24dp), contentDescription = stringResource(Res.string.conversation_open_gifs))
                    else ->
                        Icon(painterResource(Res.drawable.sticky_note_2_24dp), contentDescription = stringResource(Res.string.conversation_open_stickers))
                }
            }
                }
                /*
                 * Lišta formátování, dokud je v poli označený text, nebo ji otevřela ikona „Aa"
                 * (viz `FormattingBar`). POD polem, ne nad ním: systémová lišta výběru (Vyjmout,
                 * Kopírovat…) se kreslí nad výběrem a lištu nad polem by přikryla. Vyjíždí se
                 * svým místem, jako náhled odkazu.
                 */
                AnimatedVisibility(
                    visible = (typing && formattingOpen) || (fieldFocused && !voiceActive && !draft.selection.collapsed),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    FormattingBar(
                        composer = composer,
                        onChanged = { onDraftChanged(composer.composed) },
                        onLink = { draft.selection.let { linkTarget = listOf(it.min, it.max) } },
                        onCodeLanguage = { draft.selection.let { languageTarget = listOf(it.min, it.max) } },
                    )
                }
              }
            }

            /*
             * Odeslání je velikost MEDIUM (56 dp), ne výchozí small (40 dp).
             *
             * 56 dp je přesně minimální výška jednořádkového `TextField`, takže tlačítko se
             * spodním zarovnáním řádku sedí na střed pole bez jakéhokoli dopočítávaného odsazení.
             * Když se pole zalomí na víc řádků nebo nad ním visí citace, zůstane u posledního
             * řádku — tam, kde se píše. Small tlačítko sedělo u spodní hrany o 8 dp níž než střed.
             *
             * Je to hlavní akce obrazovky, takže větší plocha dává smysl i sama o sobě. Large
             * (96 dp) by už bylo vyšší než pole a přebíjelo by psaní.
             *
             * Tvar nese stav: mikrofon je „sušenka", odeslání kruh (`MorphShape`, `ShapeMorphs.Rest`);
             * kruh se při stisku mění podle M3 Expressive na zaoblený čtverec.
             */
            /*
             * Prázdné pole bez příloh = mikrofon, nebo kamera (jako v Telegramu): tlačítko, které
             * by jinak bylo neaktivní, dostane smysl. Podržení nahrává, ťuknutí přepíná hlasovku
             * a kolečko (`holdToRecord`). Při úpravě zprávy není — nahrávka úprava není.
             */
            // Bez práva na hlasovky i kolečka tlačítko zůstane odesláním, jen neaktivním.
            val showMic = !voiceActive && editing == null && draft.text.isBlank() && !withAttachments &&
                !attachmentsPreparing && recordMode != null

            /*
             * Prázdný POPISEK je platná úprava — tím se popisek odebere —, takže tlačítko musí jít
             * zmáčknout i s prázdným polem. U textu zprávy ne: prázdnou zprávu server odmítne.
             */
            val removableCaption = editing?.composerEdit() == ComposerEdit.Caption
            val kind = when {
                editing != null -> SendButtonKind.Save
                voiceActive -> SendButtonKind.SendVoice
                showMic && recordMode == RecordMode.VideoNote -> SendButtonKind.Camera
                showMic -> SendButtonKind.Mic
                else -> SendButtonKind.Send
            }

            var sendMenuOpen by remember { mutableStateOf(false) }
            // Nabídka otevřená nad hlasovkou: volby jdou na nahrávku, ne na text pole.
            var voiceMenu by remember { mutableStateOf(false) }
            var voiceChosen by remember { mutableStateOf(false) }
            // Položka nabídky volá NEJDŘÍV zavření a teprve pak svoji akci, takže o obnovení
            // nahrávání se rozhoduje až po zavření: bez volby se nahrává dál, s volbou ne.
            LaunchedEffect(sendMenuOpen) {
                if (!sendMenuOpen && voiceMenu) {
                    if (!voiceChosen) onVoiceMenu(false)
                    voiceMenu = false
                    voiceChosen = false
                }
            }
            // Efekt jde vybrat jen k textu — výběr efektu posílá obsah pole.
            val voiceSendMenu = sendMenu.copy(effects = false)
            val recordActionLabel = stringResource(
                if (recordMode == RecordMode.VideoNote) Res.string.conversation_record_video_note else Res.string.conversation_record_voice,
            )
            // Ťuknutí na mikrofon či kameru nahrávání nespouští, jen přepíná — a řekne jak dál.
            val recordHint = rememberTooltipState(isPersistent = false)
            val hintScope = rememberCoroutineScope()
            // „Přepsat…" nese nabídka odeslání, ale do `sendMenuOffer` nepatří: rozhoduje o něm
            // text pole, a ten `ConversationScreen` číst nemá (skládal by se při každém úhozu).
            val rewriteOffered = rewrite.offered && editing == null && rewritable(draft.text)
            val sendOptionsOffered = (kind == SendButtonKind.Send && (sendMenu.any || rewriteOffered) && !attachmentsPreparing) ||
                (kind == SendButtonKind.SendVoice && voiceSendMenu.any)
            ButtonGestureHint(
                hints = gestureHints,
                sendOptions = kind == SendButtonKind.Send && sendOptionsOffered,
                recording = showMic,
                recordMode = recordMode ?: RecordMode.Voice,
                effects = sendMenu.effects,
            ) {
            Box(Modifier.onGloballyPositioned { recordGesture.buttonCenterX = it.boundsInWindow().center.x }) {
                RootTooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                stringResource(
                                    if (recordMode == RecordMode.VideoNote) {
                                        Res.string.conversation_record_hint_video_note
                                    } else {
                                        Res.string.conversation_record_hint_voice
                                    },
                                ),
                            )
                        }
                    },
                    state = recordHint,
                    // Podržení tu nahrává; nápověda na dlouhý stisk by se s ním prala.
                    enableUserInput = false,
                ) {
                /*
                 * Morf, dýchání a barvy se čtou AŽ TADY, v obsahu `RootTooltipBox` (vlastní rozsah
                 * rekompozice). Čtené v kartě pole by s každým snímkem animace přeskládaly celou
                 * kartu i s polem — při nahrávání po celou dobu nahrávky (změřeno Perfettem).
                 */
                /*
                 * Tvar nese stav: mikrofon (klidový stav prázdného pole) je „sušenka" z M3 Expressive,
                 * odeslání a uložení úpravy kruh. Změna se animuje morfem mezi nimi a ikona se vymění
                 * zvětšením. Tlačítko se mění samo od sebe (začne se psát, pole se vyprázdní) a skoková
                 * výměna ikony se dala přehlédnout — a ťuknout pak na mikrofon místo odeslání.
                 * Stav se tak pozná i bez ikony a barvy, třeba koutkem oka nebo na slunci.
                 */
                // Pružina přestřelí za oba konce; mimo 0..1 by se tvar zdeformoval.
                val morphProgress = animateMorph(
                    kind == SendButtonKind.Mic || kind == SendButtonKind.Camera,
                    "send-button-shape",
                ).coerceIn(0f, 1f)

                /*
                 * Při nahrávání tlačítko DÝCHÁ: pomalý morf kruh ↔ SoftBurst. Tečka v pruhu nahrávání
                 * už nebliká — dva blikající prvky vedle sebe by byly šum — a živost nese tlačítko,
                 * kterým se nahrávání i odesílá.
                 */
                val breathing = if (isRecording) {
                    rememberInfiniteTransition(label = "recording-breath").animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(RECORDING_BREATH_MS), RepeatMode.Reverse),
                        label = "recording-breath-progress",
                    ).value
                } else {
                    0f
                }

                /*
                 * Barva říká, ČEHO se akce týká: text jde `primary` (jako odchozí bublina), hlas
                 * `tertiary`. Hlasovka je doplňková cesta vedle psaní, a terciární role je v M3
                 * přesně na to — tlačítko je pak stejně výrazné jako odeslání textu (obojí je plná
                 * role, ne tonální), jen se pozná, že udělá něco jiného.
                 *
                 * Dřív byl mikrofon tonální `secondaryContainer`, tedy „klidový stav prázdného pole".
                 * Jenže tím bylo tlačítko v prázdném poli to nejméně nápadné na obrazovce, přestože
                 * je to jediná akce, která se z prázdného pole dá udělat.
                 *
                 * Nahrávání a mikrofon mají barvu STEJNOU: mezi „můžu nahrát" a „nahrávám" rozhoduje
                 * tvar (sušenka ↔ dýchající SoftBurst), vlnka karty a pruh s časem. Přebarvit k tomu
                 * ještě tlačítko by byl čtvrtý signál pro jednu změnu.
                 */
                // Kolečko je taky nahrávka, takže nese tutéž terciární roli jako hlas.
                val voiceButton = kind == SendButtonKind.Mic || kind == SendButtonKind.Camera ||
                    kind == SendButtonKind.SendVoice
                val colorSpec = MaterialTheme.motionScheme.slowEffectsSpec<Color>()
                val containerColor by animateColorAsState(
                    targetValue = if (voiceButton) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    animationSpec = colorSpec,
                    label = "send-button-container",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (voiceButton) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    animationSpec = colorSpec,
                    label = "send-button-content",
                )

                // Kruh (odeslání) se při stisku mění podle M3 Expressive na zaoblený čtverec.
                // Sušenka a tvary během přechodu stisk nemění: mezi obecnými tvary IconButton
                // neanimuje a skok by vypadal jako chyba.
                val buttonShapes = if (breathing > 0f) {
                    val shape = MorphShape(ShapeMorphs.Live, breathing, rotation = RECORDING_BREATH_ROTATION)
                    IconButtonShapes(shape = shape, pressedShape = shape)
                } else if (morphProgress > 0f) {
                    val shape = MorphShape(ShapeMorphs.Rest, morphProgress, rotation = ShapeMorphs.REST_ROTATION)
                    IconButtonShapes(shape = shape, pressedShape = shape)
                } else {
                    IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.mediumRoundShape,
                        pressedShape = IconButtonDefaults.mediumPressedShape,
                    )
                }
                FilledIconButton(
                    onClick = {
                        when {
                            voiceActive -> onSendRecording(SendOptions())
                            showMic -> {
                                if (canSwitchRecordMode) {
                                    haptics.toggle(on = recordMode == RecordMode.Voice)
                                    onSwitchRecordMode()
                                }
                                hintScope.launch { recordHint.show() }
                            }
                            else -> {
                                onSend(composer.composed, SendOptions())
                                draft = TextFieldValue()
                            }
                        }
                    },
                    // Přílohy jdou poslat i bez popisku a popisek jde i odebrat.
                    // Náhled, který se ještě dokončuje, poslat nejde — soubor není hotový.
                    enabled = (isRecording || voicePreview?.voice != null || videoNotePreview?.note != null || showMic || draft.text.isNotBlank() || withAttachments || removableCaption) &&
                        // Odeslání teď by poslalo zprávu bez videa, které se ještě kopíruje.
                        !(kind == SendButtonKind.Send && attachmentsPreparing),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                    shapes = buttonShapes,
                    modifier = Modifier
                        // PRVNÍ v řetězu: tlačítko během gesta změní druh i modifikátory za tímhle.
                        .holdToRecord(
                            enabled = showMic,
                            cancelDistance = RECORD_CANCEL_DISTANCE,
                            lockDistance = RECORD_LOCK_DISTANCE,
                            onHoldStart = {
                                gestureHints?.used(GestureHint.HoldToRecord)
                                recordHeld = true
                                // Zamčení z minula (akce čtečky, která nakonec nenahrávala) neplatí.
                                recordLocked = false
                                onStartRecording(true)
                            },
                            onDrag = { recordDrag.value = it },
                            onRelease = {
                                recordHeld = false
                                if (isRecording) onSendRecording(SendOptions())
                            },
                            onCancel = {
                                recordHeld = false
                                if (isRecording) onCancelRecording()
                            },
                            onLock = {
                                recordHeld = false
                                if (isRecording) {
                                    haptics.threshold()
                                    recordLocked = true
                                }
                            },
                        )
                        // Čtečka obrazovky podržet neumí: nahrávání spustí akcí, rovnou zamčené.
                        .semantics {
                            if (showMic) {
                                customActions = listOf(
                                    CustomAccessibilityAction(recordActionLabel) {
                                        recordLocked = true
                                        onStartRecording(false)
                                        true
                                    },
                                )
                            }
                        }
                        .size(IconButtonDefaults.mediumContainerSize())
                        // Klidový tvar; stisk ho na chvíli zhranatí, a to při letu fotky nehraje roli.
                        .occludesSharedElements(buttonShapes.shape)
                        // Podržení nabídne odeslat bez zvuku a naplánovat — u hotové zprávy
                        // i u nahrávané hlasovky.
                        .longPressInstead(enabled = sendOptionsOffered) {
                            gestureHints?.used(GestureHint.SendOptions)
                            voiceMenu = kind == SendButtonKind.SendVoice
                            if (voiceMenu) onVoiceMenu(true)
                            sendMenuOpen = true
                        },
                ) {
                    // Ikona jede stejnou pružinou jako tvar — rychlejší by se vyměnila dřív, než je
                    // morf vůbec vidět.
                    val iconScale = morphSpring<Float>()
                    val iconFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = kind,
                        transitionSpec = {
                            (scaleIn(iconScale, initialScale = 0.4f) + fadeIn(iconFade))
                                .togetherWith(scaleOut(iconScale, targetScale = 0.4f) + fadeOut(iconFade))
                        },
                        label = "send-button-icon",
                    ) { shown ->
                        // Při úpravě fajfka: nic se neposílá, jen potvrzuje změna.
                        Icon(
                            painterResource(
                                when (shown) {
                                    SendButtonKind.Save -> Res.drawable.check_24dp
                                    SendButtonKind.Mic -> Res.drawable.mic_24dp
                                    SendButtonKind.Camera -> Res.drawable.videocam_24dp
                                    SendButtonKind.Send, SendButtonKind.SendVoice -> Res.drawable.send_24dp
                                },
                            ),
                            contentDescription = stringResource(
                                when (shown) {
                                    SendButtonKind.Save -> Res.string.conversation_save_edit
                                    SendButtonKind.Mic -> Res.string.conversation_record_voice
                                    SendButtonKind.Camera -> Res.string.conversation_record_video_note
                                    SendButtonKind.SendVoice -> if (recordingKind == RecordMode.VideoNote) {
                                        Res.string.conversation_send_video_note
                                    } else {
                                        Res.string.conversation_send_voice
                                    }
                                    SendButtonKind.Send -> Res.string.conversation_send
                                },
                            ),
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                        )
                    }
                }
                }
                val offer = if (voiceMenu) voiceSendMenu else sendMenu
                ExpressiveMenu(
                    expanded = sendMenuOpen,
                    onDismiss = { sendMenuOpen = false },
                    popupPositionProvider = remember { MenuAbovePositionProvider(alignEnd = true) },
                    groups = listOf(
                        if (voiceMenu) {
                            listOfNotNull(
                                MenuAction(stringResource(Res.string.conversation_send_silent), Res.drawable.notifications_off_24dp) {
                                    voiceChosen = true
                                    sendMenuOpen = false
                                    onSendRecording(SendOptions(silent = true))
                                }.takeIf { offer.silent },
                                MenuAction(stringResource(Res.string.conversation_send_schedule), Res.drawable.schedule_send_24dp) {
                                    voiceChosen = true
                                    sendMenuOpen = false
                                    afterKeyboardHidden(onScheduleRecording)
                                }.takeIf { offer.schedule },
                                MenuAction(stringResource(Res.string.conversation_send_when_online), Res.drawable.online_prediction_24dp) {
                                    voiceChosen = true
                                    sendMenuOpen = false
                                    onSendRecording(SendOptions(schedule = MessageSchedule.WhenOnline))
                                }.takeIf { offer.whenOnline },
                            )
                        } else listOfNotNull(
                            MenuAction(stringResource(Res.string.conversation_send_silent), Res.drawable.notifications_off_24dp) {
                                sendMenuOpen = false
                                onSend(composer.composed, SendOptions(silent = true))
                                draft = TextFieldValue()
                            }.takeIf { sendMenu.silent },
                            MenuAction(stringResource(Res.string.conversation_send_schedule), Res.drawable.schedule_send_24dp) {
                                sendMenuOpen = false
                                afterKeyboardHidden(onSchedule)
                            }.takeIf { sendMenu.schedule },
                            MenuAction(stringResource(Res.string.conversation_send_when_online), Res.drawable.online_prediction_24dp) {
                                sendMenuOpen = false
                                onSend(composer.composed, SendOptions(schedule = MessageSchedule.WhenOnline))
                                draft = TextFieldValue()
                            }.takeIf { sendMenu.whenOnline },
                            MenuAction(stringResource(Res.string.conversation_send_effect), Res.drawable.auto_awesome_24dp) {
                                sendMenuOpen = false
                                afterKeyboardHidden(onPickEffect)
                            }.takeIf { sendMenu.effects },
                            MenuAction(stringResource(Res.string.conversation_send_rewrite), Res.drawable.edit_note_24dp) {
                                sendMenuOpen = false
                                val text = draft.text
                                afterKeyboardHidden { rewrite.open(text) }
                            }.takeIf { rewriteOffered },
                        ),
                    ),
                )
            }
            }
        }
    }
}

/** Tvar karty vstupního pole; při nahrávání se z něj rozvlní [WavyShape]. */
private val ComposerShape = RoundedCornerShape(24.dp)

/** Odsazení textu od ikony vedle pole — glyf má v 48dp tlačítku kolem sebe svých 12 dp. */
private val FIELD_ICON_GAP = 4.dp

/** Půlperioda dýchání tlačítka při nahrávání. */
private const val RECORDING_BREATH_MS = 900

/** O kolik se tlačítko při nádechu pootočí. */
private const val RECORDING_BREATH_ROTATION = 30f

/**
 * Barva ikon v kartě vstupního pole (přílohy, emoji/samolepky).
 *
 * Výchozí `onSurfaceVariant` byla na `surfaceContainerHigh` karty zašedlá a špatně čitelná.
 * Akcent je to, čím M3 odlišuje ovládání od textu (placeholder zůstává šedý) — jen se nebere
 * `primary` natvrdo, ale ten z akcentních rolí, který je na kartě nejlíp vidět
 * ([strongestAccentOn]).
 *
 * Samotná `primary` totiž byla na kartě NEJSLABŠÍ prvek: v Material You z tapety vyšla olivová
 * s kontrastem 5,3 : 1, zatímco placeholder vedle ní měl 7,6 a ostatní ikony v aplikaci přes 10.
 * Obrysová ikona s tahem 2 dp se pak čte jako rozmazaná — viz [strongestAccentOn]. Srovnání
 * s Google Zprávami sedí: tamní ikony u pole jsou taky akcent, ale o poznání tmavší.
 *
 * Plocha je cílová barva karty, ne ta právě animovaná: při nahrávání karta přebarvuje do
 * `primaryContainer`, jenže tehdy je místo pole pruh nahrávání a ikony se nekreslí vůbec.
 */
@Composable
private fun inputIconColor() = strongestAccentOn(
    background = composerCardColor(),
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.onPrimaryContainer,
)

/**
 * Klidová barva karty vstupního pole: `surfaceContainerHigh` přitónovaná do `primaryContainer`.
 *
 * V neutrálních surface rolích byla karta jediná velká šedá plocha na obrazovce a od pozadí
 * konverzace ji dělila jen světlost. S přitónováním nese barvu palety (a na Androidu tedy
 * i tapety), ale zůstane pozadím — plný `primaryContainer` by ji posunul na světlost odchozí
 * bubliny a pole by vypadalo jako rozepsaná zpráva. Viz [tintedSurface].
 */
@Composable
private fun composerCardColor(): Color = tintedSurface(
    surface = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint = MaterialTheme.colorScheme.primaryContainer,
)

/**
 * Co tlačítko u vstupního pole právě dělá. Změna mezi nimi se animuje.
 *
 * [SendVoice] je vlastní hodnota, i když kreslí tutéž ikonu jako [Send]: odeslání hlasovky patří
 * barevně k [Mic] (hlas = terciární), zatímco odeslání textu k `primary`. S jediným „Send" by se
 * tlačítko při spuštění nahrávání přebarvilo z terciární na primary a zase zpátky.
 */
private enum class SendButtonKind { Mic, Camera, SendVoice, Send, Save }

/**
 * Dlouhý stisk místo ťuknutí. `IconButton` `onLongClick` nemá, takže se gesto pozoruje
 * v `Initial` (dřív než tlačítko): po `longPressTimeout` s prstem na místě zavolá
 * [onLongPress] a **zbytek gesta spotřebuje** — puštění prstu pak tlačítko neodešle.
 * Krátké ťuknutí projde beze změny.
 */
internal fun Modifier.longPressInstead(enabled: Boolean, onLongPress: () -> Unit): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val action by rememberUpdatedState(onLongPress)
    if (!enabled) {
        this
    } else {
        pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // `null` = vypršel čas a prst je pořád dole na místě.
                val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    }
                }
                if (released != null) return@awaitEachGesture
                haptics.longPress()
                action()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
}

/** Řádek nad polem, dokud se vybrané přílohy kopírují do cache. */
@Composable
private fun AttachmentsPreparing() {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator(Modifier.size(32.dp))
        Text(
            text = stringResource(Res.string.share_preparing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Horní pruh karty nad vstupním polem (citace, úprava): tónovaná plocha přes celou šířku karty,
 * stejně jako citace na kartě zprávy. Zaoblení nahoře nese karta, pruh je rovný.
 */
@Composable
private fun ComposerHeader(content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Akcent uvnitř [ComposerHeader] — `tertiary`, dokud je na jeho ploše vidět.
 *
 * `tertiary` na `tertiaryContainer` je zkratka, kterou M3 negarantuje: v paletě se zvýšeným
 * kontrastem se v tmavém režimu obě potkají na téže světlosti a popisek zmizí. Tatáž past jako
 * u citace na kartě zprávy — viz [accentOn].
 */
@Composable
private fun composerHeaderAccent(): Color = accentOn(
    background = MaterialTheme.colorScheme.tertiaryContainer,
    accent = MaterialTheme.colorScheme.tertiary,
    fallback = MaterialTheme.colorScheme.onTertiaryContainer,
)

/**
 * Náhled odkazu v rozepsané zprávě: web, titulek a malý obrázek, jak ho připojí server.
 *
 * Tentýž pruh jako citace a úprava ([ComposerHeader]) — říká, co odejde spolu s textem. Ťuknutí
 * (i ikona voleb) otevře [LinkPreviewOptionsSheet], ✕ náhled vypne. Obrázek je vždycky malý
 * čtvereček: jde o to poznat, JAKÝ náhled to je; velikost v odeslané zprávě ukazuje sheet.
 */
@Composable
private fun LinkPreviewHeader(
    preview: LinkPreview,
    onOpenOptions: () -> Unit,
    onDismiss: () -> Unit,
) = ComposerHeader {
    val accent = composerHeaderAccent()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onOpenOptions)
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(Res.drawable.link_24dp),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(end = 4.dp).size(16.dp),
                )
                Text(
                    text = preview.siteName ?: preview.title ?: preview.url,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Druhý řádek jen tehdy, když řekne něco jiného než první.
            (preview.title?.takeIf { preview.siteName != null } ?: preview.description)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        preview.photo?.let { photo ->
            RemoteImage(
                fileId = photo.fileId,
                thumbnailFileId = photo.thumbnailFileId,
                placeholder = photo.minithumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(start = 8.dp).size(LINK_HEADER_THUMBNAIL).clip(RoundedCornerShape(8.dp)),
            )
        }
    }
    IconButton(onClick = onOpenOptions, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painterResource(Res.drawable.tune_24dp),
            contentDescription = stringResource(Res.string.link_preview_options),
        )
    }
    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painterResource(Res.drawable.close_24dp),
            contentDescription = stringResource(Res.string.link_preview_remove),
        )
    }
}

/** Obrázek v pruhu náhledu odkazu — vejde se do výšky dvou řádků textu. */
private val LINK_HEADER_THUMBNAIL = 36.dp

/**
 * Hlavička karty při úpravě zprávy.
 *
 * Vypadá jako citace (ikona, popisek, původní text), protože plní stejnou roli: říká, k čemu
 * se vztahuje to, co se píše. Původní text zůstává vidět, i když ho uživatel v poli přepíše.
 *
 * **Popisek přílohy to musí říct**: fotka v konverzaci zůstane, ať se v poli píše cokoli, takže
 * bez toho by nebylo poznat, že se upravuje popisek, a ne zpráva. U přílohy bez popisku se
 * původní text nekreslí vůbec — prázdný řádek by vypadal jako chyba vykreslení.
 */
@Composable
private fun EditingHeader(message: ChatMessage, onCancel: () -> Unit) = ComposerHeader {
    val accent = composerHeaderAccent()
    val caption = message.composerEdit() == ComposerEdit.Caption
    Column(Modifier.weight(1f).padding(start = 14.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(Res.drawable.edit_24dp),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(end = 4.dp).size(16.dp),
            )
            Text(
                text = stringResource(
                    if (caption) Res.string.conversation_editing_caption else Res.string.conversation_editing,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }
        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    IconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.conversation_cancel_edit))
    }
}
