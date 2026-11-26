package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.highlightRanges
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.files.opensInViewer
import eu.tintera.plume.core.files.documentKind
import eu.tintera.plume.core.files.DocumentKind
import eu.tintera.plume.core.telegram.composedFormatsOf
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendFailure
import eu.tintera.plume.core.telegram.SendState

/**
 * Jedna položka seznamu konverzace: samostatná zpráva, nebo celé album.
 *
 * Album jsou v Telegramu samostatné zprávy spojené `mediaAlbumId`; konverzace je kreslí jako jednu
 * bublinu. [messages] jde stejně jako seznam zpráv **od nejnovější**.
 */
internal class MessageRow(val messages: List<ChatMessage>) {

    val newest: ChatMessage get() = messages.first()
    val oldest: ChatMessage get() = messages.last()
    val isAlbum: Boolean get() = messages.size > 1

    /** Zprávy alba v pořadí, ve kterém se čtou a kreslí v mřížce: od nejstarší. */
    val members: List<ChatMessage> get() = messages.asReversed()

    /**
     * Zpráva, ke které se v bublině vztahuje popisek, reakce, odpověď a nabídka.
     *
     * U alba ta s popiskem — tu uživatel vidí jako „text alba" a reaguje na ni. Bez popisku první
     * (nejstarší), jak to dělají ostatní klienti.
     */
    val primary: ChatMessage = members.firstOrNull { it.text.isNotBlank() } ?: oldest

    /**
     * Co bublina kreslí. U samostatné zprávy ona sama; u alba [primary] bez přílohy (tu nese
     * mřížka), s časem nejnovější zprávy a se „nejhorším" stavem doručení — album je odeslané,
     * až když jsou odeslané všechny jeho zprávy.
     */
    val display: ChatMessage = if (!isAlbum) {
        newest
    } else {
        primary.copy(
            media = null,
            timestamp = newest.timestamp,
            isEdited = messages.any { it.isEdited },
            sendState = when {
                messages.any { it.sendState == SendState.Failed } -> SendState.Failed
                messages.any { it.sendState == SendState.Sending } -> SendState.Sending
                else -> primary.sendState
            },
            sendFailure = albumFailure(messages),
        )
    }

    fun contains(messageId: Long): Boolean = messages.any { it.id == messageId }
}

/**
 * Položky, u kterých se hlásí „přečteno".
 *
 * Automaticky stačí **nejnovější zpráva chatu**, a jen když je moje: přečtením té poslední jsou
 * přečtené i všechny před ní, takže dvojitá fajfka u celé historie byla řada stejných odznaků
 * bez informace. A **když po mojí zprávě napsal protějšek, je přečtení dokázané samo** —
 * odznak už nemá co dodat. Proto se hledá nejnovější zpráva, ne nejnovější odchozí: vyjde-li
 * příchozí, nehlásí přečteno nikdo.
 *
 * [revealedMessageId] je zpráva, u které si odznak **vyžádal uživatel** ťuknutím. Je to výjimka
 * z pravidla výš, ne jeho přepsání: kdo chce ubezpečení u jedné konkrétní zprávy, dostane ho,
 * a automatický odznak u nejnovější zprávy zůstává. Rozhoduje se to tady, na jednom místě —
 * v bublině by to byla druhá podmínka o témže a ty dvě by se časem rozešly.
 *
 * Servisní oznámení se přeskakují. „X se přidal do skupiny" nedokazuje, že si někdo něco přečetl,
 * takže by odznak schovalo neprávem.
 *
 * [rows] jde jako seznam konverzace, tedy **od nejnovější**.
 */
internal fun readBadgeRows(rows: List<MessageRow>, revealedMessageId: Long? = null): List<MessageRow> {
    val automatic = rows.firstOrNull { !it.display.isService }?.takeIf { it.display.isOutgoing }

    val revealed = revealedMessageId
        ?.let { id -> rows.firstOrNull { it.contains(id) } }
        ?.takeIf { it.display.isOutgoing && !it.display.isService }

    return if (revealed == null || revealed === automatic) {
        listOfNotNull(automatic)
    } else {
        listOfNotNull(automatic, revealed)
    }
}

/**
 * Spojí sousední zprávy téhož alba do jedné položky.
 *
 * Jen **sousední**: album se odesílá naráz a jeho zprávy mají po sobě jdoucí id, takže mezi ně nic
 * nepatří. Když by se tam přesto něco dostalo, rozdělí se album na dvě bubliny — lepší než
 * přeskládat pořadí zpráv.
 *
 * Na hraně načtené historie může být album načtené jen z části; dotažením starší stránky se k němu
 * zbytek připojí.
 */
internal fun groupAlbums(messages: List<ChatMessage>): List<MessageRow> {
    val rows = ArrayList<MessageRow>(messages.size)
    var current = ArrayList<ChatMessage>()

    fun flush() {
        if (current.isNotEmpty()) rows += MessageRow(current)
        current = ArrayList()
    }

    for (message in messages) {
        val album = message.mediaAlbumId
        val continuesAlbum = album != 0L && current.isNotEmpty() && current.last().mediaAlbumId == album
        if (!continuesAlbum) flush()
        current += message
    }
    flush()
    return rows
}

/**
 * Kreslí se příloha zprávy přímo na plochu konverzace, bez bubliny?
 *
 * Fotka, video a mapa polohy jsou obrázek samy o sobě — bublina kolem nich nemá co ohraničovat a jen přidává
 * rám, ze kterého konverzace ztěžkne. Stejně to dělá Google Messages. Popisek pod snímkem bublinu
 * naopak dostane vlastní: text na holé ploše by se ztratil.
 *
 * **Citace bublinu nevynutí**: je horním dílem karty snímku, nad značkou přeposlání (viz
 * `MediaMessageItem`). Dřív si bublinu vynutila a snímek v ní ležel odsazený s vlastním
 * zaoblením jako fotka v rámečku, zatímco přeposlaný snímek šel od kraje ke kraji.
 * **Náhled odkazu** ano — u zprávy s přílohou zůstává v bublině (viz [showsLinkPreviewCard]).
 *
 * Ostatní přílohy bublinu potřebují dál: soubor, hlasovka i samolepkový popis jsou řádky s textem
 * a ikonou, ne obrázek. [album] nese zprávy alba, u samostatné zprávy `null`.
 */
internal fun showsBareMedia(message: ChatMessage, album: List<ChatMessage>?): Boolean {
    if (message.linkPreview != null) return false
    // Obrázek nebo video poslané jako soubor jde na plochu jen samo — album souborů je seznam.
    if (album == null && (message.media as? MessageMedia.Document)?.showsLargePreview == true) return true
    val media = album?.map { it.media } ?: listOf(message.media)
    return media.isNotEmpty() && media.all {
        it is MessageMedia.Photo || it is MessageMedia.Video || it is MessageMedia.Animation ||
            it is MessageMedia.Location || it is MessageMedia.PaidMedia || it is MessageMedia.Story
    }
}

/**
 * Kreslí se soubor jako VELKÝ náhled — snímek jako fotka, pod ním název a velikost — místo
 * řádku s ikonou?
 *
 * Jen obrázek a video ([documentKind]) s náhledem od serveru: rozměry souboru TDLib u dokumentu
 * nedává, takže rámeček stojí na poměru stran náhledu, a bez něj by nebylo co kreslit. HEIC
 * náhled nemá (ověřeno botem), zůstává tedy řádkem. **Citlivý soubor taky** — řádek ho umí
 * rozostřit a odkrýt ([SensitiveBox]); velký náhled by ho ukázal dřív, než o to kdo požádal.
 */
internal val MessageMedia.Document.showsLargePreview: Boolean
    get() = !isSensitive && thumbnailFileId != null && thumbnailWidth > 0 && thumbnailHeight > 0 &&
        documentKind(fileName, mimeType) != DocumentKind.Other

/**
 * Stáhne se u velkého náhledu i SOUBOR, aby snímek nebyl rozmazaný?
 *
 * Náhled od serveru má do 320 px a ve snímku přes většinu bubliny je vidět, že je malý. Originál
 * se ale stahuje sám, bez ťuknutí — proto jen obrázek, který umí nakreslit prohlížeč
 * ([opensInViewer], tedy i Coil v bublině), a jen do stropu [limit] — automatické stahování
 * souborů podle sítě (`AutoDownloadLimits.maxFileBytes`). Video, velký obrázek a formát, který neumíme, zůstanou u náhledu.
 */
internal fun MessageMedia.Document.loadsOriginalPreview(limit: Long): Boolean =
    showsLargePreview && documentKind(fileName, mimeType) == DocumentKind.Image &&
        sizeBytes in 1..limit && opensInViewer(fileName, mimeType)

/**
 * Kreslí se náhled odkazu jako **karta ze dvou částí** — nahoře text zprávy v barvě bubliny,
 * dole popis webu v tónované ploše — místo kousku uvnitř bubliny?
 *
 * Platí pro každý náhled, který má co ukázat (obrázek, nebo aspoň web, titulek či popis), u zprávy
 * bez přílohy a bez citace. S velkým obrázkem stojí obrázek mezi textem a popisem
 * ([LinkPreview.showsLargePhoto]), malý obrázek je čtvereček v popisu.
 *
 * Náhled u zprávy s přílohou nebo citací zůstává v bublině, jako celá ta zpráva: karta by se
 * s přílohou i s citací přela o to, co je hlavní obsah.
 */
internal fun showsLinkPreviewCard(message: ChatMessage, threadRootId: Long? = null): Boolean {
    val preview = message.linkPreview ?: return false
    val hasContent = preview.photo != null || preview.siteName != null || preview.title != null || preview.description != null
    return hasContent && message.media == null && !message.showsQuote(threadRootId)
}

/**
 * Kreslí se nad zprávou citace — tedy odpovídá na něco, co v téhle konverzaci NENÍ jen kořen
 * vlákna?
 *
 * **Ve vlákně komentářů odpovídá kořeni (příspěvku kanálu) skoro každý komentář** — TDLib mu
 * `replyTo` vyplní, i když nikdo „Odpovědět" neťukl. Citace kořene se tam nekreslí (stál by nad
 * každým komentářem znovu), takže si nesmí vynutit ani bublinu: dokud pravidla hleděla jen na
 * `replyToMessageId != null`, šly fotky, videa i samolepky v komentářích do prázdné bubliny.
 *
 * Odpověď na zprávu z jiného chatu (`externalReply`, i odpověď na příběh) citaci kreslí taky,
 * přestože `replyToMessageId` nemá.
 */
internal fun ChatMessage.showsQuote(threadRootId: Long?): Boolean {
    val replyTo = replyToMessageId
    return (replyTo != null && replyTo != threadRootId) || externalReply != null
}

/**
 * Sbalí se citace na pár řádků a nabídne pod sebou rozbalovač?
 *
 * Telegram zná sbalitelnou citaci (`TextEntityTypeExpandableBlockQuote`) a ostatní klienti ji
 * kreslí na pár řádků se „Zobrazit víc". Sbalit ji jde ale JEN tehdy, když je citace celý text
 * zprávy: `maxLines` platí pro celý `Text`, takže by ořízl i větu pod citací — a rozdělit zprávu
 * na víc `Text`ů nejde, protože zvýraznění hledání, spoilery i dlouhý stisk nad odkazem se
 * počítají nad jedním layoutem. Sbalitelná citace uvnitř delší zprávy se proto kreslí celá.
 */
internal fun collapsesQuote(message: ChatMessage): Boolean {
    val quote = message.entities.singleOrNull { it.type is MessageEntityType.Quote } ?: return false
    return (quote.type as MessageEntityType.Quote).expandable &&
        quote.offset == 0 &&
        quote.offset + quote.length >= message.text.length
}

/**
 * Smí se dlouhý text zprávy sbalit (viz `LongText`)? Ne u článku — ten má vlastní „Číst celý
 * článek" — a ne u zprávy s nálezem hledání [searchQuery]: nález by mohl ležet ve schované
 * části a skok na něj by ukázal zprávu bez něj.
 */
internal fun collapsesLongText(message: ChatMessage, searchQuery: String?): Boolean =
    message.article == null && (searchQuery == null || highlightRanges(message.text, searchQuery).isEmpty())

/** Na kolik řádků se sbalená citace ořízne. */
internal const val COLLAPSED_QUOTE_LINES = 3

/** Velký obrázek přes celou šířku karty; o velikosti rozhoduje odesílatel (`largePhoto`). */
internal val LinkPreview.showsLargePhoto: Boolean get() = largePhoto && photo != null

/** Co z té zprávy vstupní pole upravuje — a tím i které volání TDLib úpravu uloží. */
internal enum class ComposerEdit {
    /** Text zprávy (`editMessageText`). */
    Text,

    /** Popisek přílohy (`editMessageCaption`) — jiné volání, úprava textu by ho nezměnila. */
    Caption,
}

/**
 * Dá se zpráva upravit ve vstupním poli, aniž by přišla o formátování? A co se tím vlastně mění?
 *
 * Pole nese text a ruční formátování (tučné, kurzívu, spoiler, kód, citace — [composedFormatsOf]);
 * odkazy, zmínky a hashtagy se při odeslání úpravy dohledají znovu. Co z textu poznat nejde a pole
 * nést neumí (odkaz s vlastním popiskem, zmínka jménem, vlastní emoji, blok kódu s jazykem), by
 * úprava tiše smazala — takovou zprávu radši upravit nenabídneme. Položka, která rozbije obsah, je
 * horší než žádná.
 *
 * **U přílohy se nabízí i prázdný popisek** — tím se popisek přidá, a smazáním zase odebere.
 * Text zprávy prázdný být nesmí: prázdnou zprávu server odmítne.
 *
 * [customEmoji] = uživatel smí v chatu posílat vlastní emoji (viz [composedFormatsOf]).
 */
internal fun ChatMessage.composerEdit(customEmoji: Boolean = false): ComposerEdit? {
    if (composedFormatsOf(text, entities, customEmoji) == null) return null
    return editKind()
}

/**
 * Druh úpravy (text, nebo popisek) bez ohledu na formátování — pro místa, kde úprava už běží
 * a o tom, jestli ji nabídnout, se rozhodlo při jejím začátku ([composerEdit]).
 */
internal fun ChatMessage.editKind(): ComposerEdit? {
    // Text ankety je náš popis, ne obsah zprávy — a `canBeEdited` u ankety znamená „jde
    // ukončit" (viz `stopPoll`), takže by „Upravit" slibovalo úpravu, kterou server odmítne.
    // Seznam úkolů se upravuje celý (`editMessageChecklist`), ne textem v poli.
    if (isService || poll != null || checklist != null) return null

    val media = media ?: return ComposerEdit.Text.takeIf { text.isNotBlank() }
    return ComposerEdit.Caption.takeIf { media.carriesCaption }
}

/**
 * Nese ten druh přílohy popisek?
 *
 * Samolepka ani animované emoji ho v Telegramu mít nemůžou, takže by u nich „Upravit" slibovalo
 * něco, co server odmítne. `when` je bez `else`: nový druh přílohy se tím musí rozhodnout,
 * místo aby tiše spadl do jedné z větví.
 */
private val MessageMedia.carriesCaption: Boolean
    get() = when (this) {
        is MessageMedia.Photo,
        is MessageMedia.Video,
        is MessageMedia.Animation,
        is MessageMedia.Document,
        is MessageMedia.VoiceNote,
        is MessageMedia.Audio,
        is MessageMedia.Attachment,
        -> true

        is MessageMedia.Sticker,
        is MessageMedia.AnimatedEmoji,
        is MessageMedia.Dice,
        is MessageMedia.Location,
        is MessageMedia.Call,
        is MessageMedia.Contact,
        is MessageMedia.VideoNote,
        // Popisek placeného média jde upravit jen prodejci, a to ověřené není.
        is MessageMedia.PaidMedia,
        // Faktura ani hra popisek nemají; jejich text je popis zboží od bota.
        is MessageMedia.Product,
        // Soutěž popisek nemá; výhru navíc píše pořadatel při vyhlášení.
        is MessageMedia.Giveaway,
        // Dárek je servisní oznámení; vzkaz od dárce upravit nejde.
        is MessageMedia.Gift,
        // Sdílený příběh popisek nemá; jeho text napsal autor příběhu.
        is MessageMedia.Story,
        -> false
    }

/**
 * Text zprávy pro schránku.
 *
 * Animované emoji má prázdný text (kreslí se jako příloha), ale zkopírovat se má pořád jeho znak —
 * dřív, když bylo textovou zprávou, to šlo. Hod kostkou totéž, i s výsledkem.
 */
/**
 * Hotový přepis hlasovky nebo kolečka: od serveru ve zprávě, jinak z telefonu ([local],
 * `LocalTranscripts` — ten zpráva nenese). `null`, dokud hotový není, a u jiné zprávy.
 */
internal fun ChatMessage.doneTranscript(local: SpeechTranscript?): String? {
    val server = when (val media = media) {
        is MessageMedia.VoiceNote -> media.transcript
        is MessageMedia.VideoNote -> media.transcript
        else -> return null
    }
    return ((server as? SpeechTranscript.Done) ?: (local as? SpeechTranscript.Done))?.text?.takeIf { it.isNotBlank() }
}

internal fun ChatMessage.copyableText(): String = text.ifBlank {
    when (val media = media) {
        is MessageMedia.AnimatedEmoji -> media.emoji
        is MessageMedia.Dice -> media.label
        // Kontakt kopíruje jméno a číslo — to jediné, co z karty jde odnést jinam.
        is MessageMedia.Contact -> listOf(media.name, media.phone).filter { it.isNotBlank() }.joinToString("\n")
        // Hotový přepis hlasovky a kolečka je text — jde zkopírovat, vybrat i přeložit.
        is MessageMedia.VoiceNote -> (media.transcript as? SpeechTranscript.Done)?.text.orEmpty()
        is MessageMedia.VideoNote -> (media.transcript as? SpeechTranscript.Done)?.text.orEmpty()
        else -> ""
    }
}

/**
 * Proč album neodešlo: znovu poslat jde, jen když jde každá jeho neodeslaná zpráva — posílá se
 * celé najednou. `null`, když neodešlo nic.
 */
internal fun albumFailure(messages: List<ChatMessage>): SendFailure? {
    val failures = messages.mapNotNull { it.sendFailure }
    if (failures.isEmpty()) return null
    // Důvod té, kvůli které to nejde — uživatel se ptá, proč album nejde poslat znovu.
    val blocking = failures.firstOrNull { !it.canRetry }
    return SendFailure(canRetry = blocking == null, reason = (blocking ?: failures.first()).reason)
}

/**
 * Co poslat znovu po ťuknutí na [messageId]: ta zpráva, a když je z alba, všechny neodeslané
 * zprávy toho alba. Album poslané po kouskách by se rozpadlo na samostatné fotky. Prázdné, když
 * zpráva neodeslaná není (mezitím odešla nebo zmizela).
 */
internal fun failedGroup(messages: List<ChatMessage>, messageId: Long): List<ChatMessage> {
    val message = messages.firstOrNull { it.id == messageId } ?: return emptyList()
    val group = if (message.mediaAlbumId != 0L) {
        messages.filter { it.mediaAlbumId == message.mediaAlbumId }
    } else {
        listOf(message)
    }
    return group.filter { it.sendState == SendState.Failed }.sortedBy { it.id }
}
