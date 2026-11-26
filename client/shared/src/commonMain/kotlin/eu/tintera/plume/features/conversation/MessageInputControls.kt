package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.BotMenuApp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.ui.KeyboardContent
import eu.tintera.plume.core.ui.UiText

/*
 * Skupiny parametrů [MessageInput]. Pole jich mělo skoro osmdesát vedle sebe; po skupinách
 * jde stav spolu se svými callbacky, takže je vidět, co k čemu patří, a celou část karty
 * (citaci, úpravu, náhled nahrávky) vypne jedno `null`.
 *
 * `data class` ze stejného důvodu jako [ConversationNavigation]: volající je skládá při každé
 * rekompozici a lambdy si Compose pamatuje (strong skipping), takže dvě po sobě složené
 * instance jsou si rovné a pole se kvůli nim znovu neskládá.
 *
 * `@Immutable` / `@Stable` tam, kde skupina nese seznam nebo typ z `:client:core`: kompilátor
 * by ji pak vedl jako nestabilní a porovnával IDENTITOU — a nová instance z každé rekompozice
 * by pole skládala pokaždé, i když se nic nezměnilo. Dřív šly tytéž hodnoty přímo a stejná
 * instance seznamu se identitou rovnala.
 */

/** Textové pole: rozepsaný text, co se do něj vkládá zvenku, a co pole hlásí. */
@Stable
internal data class ComposerField(
    /** Rozepsaný text i s formátováním; drží ho obrazovka, protože do něj vkládá i výběr emoji. */
    val draft: ComposerDraft,
    /**
     * Jen ruční úpravy textu a formátování — vyprázdnění po odeslání se nehlásí, o to se stará
     * [SendControls.onSend].
     */
    val onDraftChanged: (ComposedText) -> Unit,
    /** Text, který se má jednou vložit do pole (koncept, úprava). */
    val fill: InputFill?,
    val onFilled: (InputFill) -> Unit,
    /** Počítadlo z `ConversationState.inputFocusRequest`; každá nová hodnota zaostří pole. */
    val focusRequest: Int,
    /** Nápověda do prázdného pole, kterou si přeje bot; `null` = výchozí. */
    val placeholder: String? = null,
    /** Šipka nahoru v prázdném poli: úprava poslední vlastní zprávy; `null` = nic. */
    val onEditLast: (() -> Unit)? = null,
    /** Účet, který pole jmenuje („Zpráva jako …"); `null` = nejmenuje (`rememberConversationAccount`). */
    val account: AccountInfo? = null,
)

/** Tlačítko výběru samolepek, emoji a GIFů vpravo u pole. */
internal data class ExpressionButton(
    /** Ikona ukazuje, čím se výběr otevře — emoji, nebo samolepkami. */
    val mode: ExpressionMode,
    val onOpen: () -> Unit,
)

/** GIF, obrázek nebo soubor vložený z klávesnice (Gboard) nebo ze schránky, už zkopírovaný do cache. */
internal data class KeyboardContentHandlers(
    val onContent: (KeyboardContent) -> Unit = {},
    val onFailed: (UiText) -> Unit = {},
)

/** Odpověď, která se píše: citovaná zpráva v horním pruhu karty. */
@Immutable
internal data class ReplyControls(
    val message: ChatMessage,
    /** Citovaný úryvek [message], nebo `null` (odpověď na celou zprávu). */
    val quote: String?,
    val partnerName: String?,
    val onCancel: () -> Unit,
)

/** Upravovaná zpráva; karta pak místo citace ukazuje, co se upravuje. */
@Immutable
internal data class EditingControls(
    val message: ChatMessage,
    val onCancel: () -> Unit,
    /** Výměna přílohy; `null` = nenabízí se (viz `replaceableMedia`). */
    val media: EditMediaControls? = null,
)

/** Jak zpráva odchází: odeslání, nabídka nad odesílacím tlačítkem a tlačítka vedle pole. */
@Immutable
internal data class SendControls(
    val onSend: (ComposedText, SendOptions) -> Unit,
    /** Co nabídne podržení odesílacího tlačítka ([sendMenuOffer]). */
    val menu: SendMenuOffer = SendMenuOffer(silent = false, schedule = false, whenOnline = false),
    /** „Naplánovat…" z nabídky; výběr času i odeslání řeší obrazovka, která drží text. */
    val onSchedule: () -> Unit = {},
    /** Podržení → „Odeslat s efektem…": otevře výběr efektu. */
    val onPickEffect: () -> Unit = {},
    /** Otevře seznam naplánovaných zpráv; `null`, když chat žádné nemá. */
    val onOpenScheduled: (() -> Unit)? = null,
    /**
     * Zvoneček výchozího odesílání bez zvuku (`true` = ticho); `null` = zvoneček se nekreslí.
     * Jen v kanálu, kam uživatel píše — tam ticho dává smysl jako stav, ne jednorázová volba.
     */
    val defaultSilent: Boolean? = null,
    val onToggleDefaultSilent: () -> Unit = {},
    /** Pomalý režim: kdy (epoch ms) půjde poslat další zpráva; do té doby je místo odeslání odpočet. */
    val slowModeUntil: Long? = null,
    /** Ťuknutí na odpočet: věta, kdy půjde psát. */
    val onSlowModeWait: () -> Unit = {},
    /** Startovní bublina letu se u pole rozvrhla (viz [MessageFlight.launched]). */
    val onFlightLaunched: (Long) -> Unit = {},
)

/** Náhled odkazu v rozepsaném textu a jeho volby. */
@Immutable
internal data class LinkPreviewControls(
    /** Náhled, pokud se má ukázat ([composerLinkPreview]). */
    val preview: LinkPreview? = null,
    /** Odkazy v rozepsaném textu; s víc než jedním nabídne sheet, ke kterému náhled patří. */
    val links: List<String> = emptyList(),
    val options: LinkPreviewOptions = LinkPreviewOptions(),
    val onOptions: (LinkPreviewOptions) -> Unit = {},
)

/** Položky nabídky sponky ([AttachButton]); `null` = položka se v chatu nenabízí. */
internal data class AttachActions(
    val onPickMedia: () -> Unit,
    val onPickDocument: () -> Unit,
    /** Založit anketu; `null`, kde anketa nejde (soukromý chat s člověkem, zakázané právo). */
    val onCreatePoll: (() -> Unit)? = null,
    /** Založit seznam úkolů; `null`, kde nejde (kanál, tajný chat, účet bez Premium). */
    val onCreateChecklist: (() -> Unit)? = null,
    /** Poslat polohu; `null`, kde nejde. */
    val onSendLocation: (() -> Unit)? = null,
    /** Poslat kontakt; `null`, kde nejde. */
    val onShareContact: (() -> Unit)? = null,
    /** Znovu poslat nedávno odeslaný soubor; `null`, kde soubory nejdou. */
    val onPickRecentFiles: (() -> Unit)? = null,
    /**
     * Vyfotit (`video = false`) nebo natočit video systémovým fotoaparátem; `null`, kde zařízení
     * fotoaparát nemá.
     */
    val onCapture: ((video: Boolean) -> Unit)? = null,
)

/** Tlačítko bota vlevo u pole: mini aplikace z nabídky, nebo seznam příkazů. */
@Immutable
internal data class BotControls(
    /** Tlačítko nabídky bota s mini aplikací; když je, stojí místo tlačítka příkazů. */
    val menuApp: BotMenuApp? = null,
    val onOpenMenuApp: () -> Unit = {},
    /** Seznam příkazů botů; `null` v chatu bez botů s příkazy. */
    val onToggleCommands: (() -> Unit)? = null,
)

/** Nahrávání hlasovky a kolečka z odesílacího tlačítka. */
internal data class RecordingControls(
    val isRecording: Boolean,
    /** Čte se až v pruhu nahrávání — čas a úrovně se mění dvacetkrát za vteřinu. */
    val current: () -> VoiceRecording?,
    /**
     * Co nahraje podržení tlačítka v prázdném poli ([recordModeFor]); `null` = nic, tlačítko
     * je pak odeslání.
     */
    val mode: RecordMode?,
    /** Ťuknutí na tlačítko přepne hlasovku ↔ kolečko ([canSwitchRecordMode]). */
    val canSwitchMode: Boolean,
    val onSwitchMode: () -> Unit,
    /** Co se právě nahrává — podle toho popis tlačítka odeslání. */
    val kind: RecordMode,
    /** Gesto nahrávání; drží ho obrazovka, protože zámek nad tlačítkem kreslí ona. */
    val gesture: RecordGestureState,
    /**
     * Začne nahrávat. `held` = prst drží tlačítko; když se oprávnění teprve žádá, nahrávat se
     * potom nesmí (prst mezitím tlačítko pustil). Bez prstu (čtečka obrazovky) se začne zamčené.
     */
    val onStart: (held: Boolean) -> Unit,
    val onCancel: () -> Unit,
    /** ■ u zamčené hlasovky: ukončí nahrávání a místo odeslání ukáže náhled. */
    val onStop: () -> Unit = {},
    val onSend: (SendOptions) -> Unit,
    /** Nabídka odeslání nad hlasovkou se otevřela (`true`) nebo zavřela bez volby (`false`). */
    val onMenu: (open: Boolean) -> Unit = {},
    /** „Naplánovat…" nad hlasovkou; výběr času řeší obrazovka, nahrávání je mezitím pozastavené. */
    val onSchedule: () -> Unit = {},
)

/**
 * Zastavená hlasovka k poslechu před odesláním. Karta s ní vypadá jako při nahrávání (vlnka,
 * barva, odeslání hlasu), jen místo živých úrovní ukazuje celý waveform.
 */
@Immutable
internal data class VoicePreviewControls(
    val preview: VoicePreview,
    /** Přehrávání náhledu; čte se až v pruhu náhledu. */
    val playback: () -> VoicePlayback?,
    val onToggle: () -> Unit,
    val onSeek: (Float) -> Unit,
    val onDiscard: () -> Unit,
    val onContinue: () -> Unit,
)

/** Zastavené kolečko; samo hraje v kruhu nad konverzací, karta ukazuje jen ovládání. */
@Stable
internal data class VideoNotePreviewControls(
    val preview: VideoNotePreview,
    val playback: VideoNotePreviewPlayback,
    val onDiscard: () -> Unit,
    val onContinue: () -> Unit,
)

/** Přílohy čekající nad polem na odeslání; text pole je pak jejich popisek. */
@Immutable
internal data class ComposerAttachments(
    val items: List<OutgoingAttachment>,
    /**
     * Vybrané přílohy se ještě kopírují (velké video trvá vteřiny). Bez ukazatele by se po
     * zavření galerie chvíli nedělo nic a vypadalo by to, že výběr nezabral.
     */
    val preparing: Boolean = false,
    val onRemove: (Int) -> Unit,
    val onOpen: (Int) -> Unit = {},
    val onSetMode: (Int, AttachmentMode) -> Unit = { _, _ -> },
    /** Soukromý chat, kde se u jedné přílohy smí nabídnout mizení (`canSelfDestruct`). */
    val selfDestructAllowed: Boolean = false,
    val previewIndex: Int? = null,
    /** „Poslat jednotlivě" místo alba; [onToggleSeparately] `null` volbu nenabízí. */
    val separately: Boolean = false,
    val onToggleSeparately: (() -> Unit)? = null,
    /** Návrh popisku první fotky modelem ([ConversationCaption]); ukáže se jen u prázdného pole. */
    val captionSuggestion: CaptionSuggestion = CaptionSuggestion.Hidden,
    val onSuggestCaption: () -> Unit = {},
)
