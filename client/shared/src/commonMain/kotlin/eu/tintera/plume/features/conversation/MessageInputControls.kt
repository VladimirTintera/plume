package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.BotMenuApp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.SendOptions

/*
 * Skupiny parametrů [MessageInput]. Pole jich mělo skoro osmdesát vedle sebe; po skupinách
 * jde stav spolu se svými callbacky, takže je vidět, co k čemu patří, a celou část karty
 * (citaci, úpravu, náhled nahrávky) vypne jedno `null`.
 *
 * `data class` ze stejného důvodu jako [ConversationNavigation]: volající je skládá při každé
 * rekompozici a lambdy si Compose pamatuje (strong skipping), takže dvě po sobě složené
 * instance jsou si rovné a pole se kvůli nim znovu neskládá.
 */

/** Odpověď, která se píše: citovaná zpráva v horním pruhu karty. */
internal data class ReplyControls(
    val message: ChatMessage,
    /** Citovaný úryvek [message], nebo `null` (odpověď na celou zprávu). */
    val quote: String?,
    val partnerName: String?,
    val onCancel: () -> Unit,
)

/** Upravovaná zpráva; karta pak místo citace ukazuje, co se upravuje. */
internal data class EditingControls(
    val message: ChatMessage,
    val onCancel: () -> Unit,
    /** Výměna přílohy; `null` = nenabízí se (viz `replaceableMedia`). */
    val media: EditMediaControls? = null,
)

/** Jak zpráva odchází: odeslání, nabídka nad odesílacím tlačítkem a tlačítka vedle pole. */
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
)

/** Náhled odkazu v rozepsaném textu a jeho volby. */
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
internal data class VideoNotePreviewControls(
    val preview: VideoNotePreview,
    val playback: VideoNotePreviewPlayback,
    val onDiscard: () -> Unit,
    val onContinue: () -> Unit,
)

/** Přílohy čekající nad polem na odeslání; text pole je pak jejich popisek. */
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
    /** Návrh popisku první fotky modelem ([ConversationCaption]); ukáže se jen u prázdného pole. */
    val captionSuggestion: CaptionSuggestion = CaptionSuggestion.Hidden,
    val onSuggestCaption: () -> Unit = {},
)
