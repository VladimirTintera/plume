package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimatedEmoji
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageDice
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.MessageCall
import dev.g000sha256.tdl.dto.MessageChecklist
import dev.g000sha256.tdl.dto.MessageContact
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageExpiredPhoto
import dev.g000sha256.tdl.dto.MessageExpiredVideo
import dev.g000sha256.tdl.dto.MessageExpiredVideoNote
import dev.g000sha256.tdl.dto.MessageExpiredVoiceNote
import dev.g000sha256.tdl.dto.MessageGame
import dev.g000sha256.tdl.dto.MessageInvoice
import dev.g000sha256.tdl.dto.MessageLiveLocation
import dev.g000sha256.tdl.dto.MessageLocation
import dev.g000sha256.tdl.dto.MessagePaidMedia
import dev.g000sha256.tdl.dto.MessagePoll
import dev.g000sha256.tdl.dto.MessageStory
import dev.g000sha256.tdl.dto.MessageUnsupported
import dev.g000sha256.tdl.dto.MessageVenue
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.LinkPreviewTypeEmbeddedAnimationPlayer
import dev.g000sha256.tdl.dto.LinkPreviewTypeEmbeddedVideoPlayer
import dev.g000sha256.tdl.dto.LinkPreviewTypeVideo
import dev.g000sha256.tdl.dto.LinkPreviewTypePhoto
import dev.g000sha256.tdl.dto.LinkPreviewTypeArticle
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeBold
import dev.g000sha256.tdl.dto.TextEntityTypeCode
import dev.g000sha256.tdl.dto.TextEntityTypeEmailAddress
import dev.g000sha256.tdl.dto.TextEntityTypeItalic
import dev.g000sha256.tdl.dto.TextEntityTypePhoneNumber
import dev.g000sha256.tdl.dto.TextEntityTypePre
import dev.g000sha256.tdl.dto.TextEntityTypePreCode
import dev.g000sha256.tdl.dto.TextEntityTypeSpoiler
import dev.g000sha256.tdl.dto.TextEntityTypeStrikethrough
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import dev.g000sha256.tdl.dto.TextEntityTypeUnderline
import dev.g000sha256.tdl.dto.TextEntityTypeUrl
import dev.g000sha256.tdl.dto.StickerFormatTgs
import dev.g000sha256.tdl.dto.StickerFormatWebp
import dev.g000sha256.tdl.dto.ThumbnailFormatJpeg
import dev.g000sha256.tdl.dto.ThumbnailFormatPng
import dev.g000sha256.tdl.dto.ThumbnailFormatWebp

/**
 * Textová podoba obsahu zprávy pro **náhled v seznamu chatů**.
 *
 * V seznamu není kam kreslit obrázek, takže i fotka musí mít slovní podobu. Text se bere doslova,
 * u médií se použije popisek, a když ho zpráva nemá, název typu — prázdný řádek u fotky vypadá
 * jako chyba načítání.
 */
internal fun Message.previewText(): String = when (val content = this.content) {
    is MessageText -> content.text.text
    is MessagePhoto -> content.caption.text.ifBlank { "Fotka" }
    is MessageVideo -> content.caption.text.ifBlank { "Video" }
    is MessageAnimation -> content.caption.text.ifBlank { "GIF" }
    is MessageDocument -> content.caption.text.ifBlank { "Soubor" }
    is MessageAudio -> content.caption.text.ifBlank { "Audio" }
    is MessageVoiceNote -> content.caption.text.ifBlank { "Hlasová zpráva" }
    is MessageSticker -> content.sticker.emoji.ifBlank { "Samolepka" }
    is MessageAnimatedEmoji -> content.emoji
    is MessageDice -> content.diceText()
    else -> content.describe()
}

/**
 * Jednořádkový popis obsahu, který nemá vlastní text.
 *
 * Poslední záchrana pro typy, které neumíme vykreslit. Dřív tu bylo pro všechny jedno „Zpráva",
 * takže se anketa, poloha i systémové oznámení tvářily stejně — a hlavně to vypadalo jako chyba
 * načítání, ne jako „tohle zatím neumíme".
 */
private fun MessageContent.describe(): String = when (this) {
    is MessageVideoNote -> "Video zpráva"
    is MessagePoll -> "Anketa: ${poll.question.text}"
    is MessageChecklist -> "Seznam úkolů: ${list.title.text}"
    is MessageLocation -> "Poloha"
    is MessageLiveLocation -> "Živá poloha"
    is MessageVenue -> "Místo: ${venue.title}"
    is MessageContact -> "Kontakt: ${contact.firstName} ${contact.lastName}".trim()
    is MessageGame -> "Hra: ${game.title}"
    is MessageInvoice -> "Platba: ${productInfo.title}"
    is MessageStory -> "Story"
    is MessagePaidMedia -> caption.text.ifBlank { "Placený obsah" }
    is MessageCall -> if (isVideo) "Videohovor" else "Hovor"

    // Zprávy s časovým omezením, které vypršely. Soubor už na serveru není, takže není co stáhnout.
    is MessageExpiredPhoto -> "Fotka vypršela"
    is MessageExpiredVideo -> "Video vypršelo"
    is MessageExpiredVideoNote -> "Video zpráva vypršela"
    is MessageExpiredVoiceNote -> "Hlasová zpráva vypršela"

    // Klient je starší než server. Doslovný název typu by uživateli neřekl nic.
    is MessageUnsupported -> "Tenhle typ zprávy zatím neumíme zobrazit"

    // Systémová oznámení mají vlastní formulace i vykreslení.
    else -> serviceText() ?: "Tenhle typ zprávy zatím neumíme zobrazit"
}

/**
 * Text zprávy pro **konverzaci**, i s formátováním.
 *
 * Na rozdíl od [previewText] vrací prázdno, když zpráva žádný vlastní text nemá — v bublině je
 * vidět samotná fotka a dopisovat pod ni slovo „Fotka" by byl šum. Typ přílohy nese [messageMedia].
 *
 * Text a entity vrací **jedna funkce**, ne dvě. Entity jsou rozsahy do konkrétního řetězce;
 * kdyby se každá půlka rozhodovala zvlášť, stačilo by přidat větev do jedné z nich a rozsahy
 * by ukazovaly do jiného textu, než který se vykreslí. Stejný důvod, proč o servisních zprávách
 * rozhoduje jediná `serviceText()`.
 */
internal fun Message.captionBody(): MessageBody = when (val content = this.content) {
    is MessageText -> content.text.toBody()
    is MessagePhoto -> content.caption.toBody()
    is MessageVideo -> content.caption.toBody()
    is MessageAnimation -> content.caption.toBody()
    is MessageDocument -> content.caption.toBody()
    is MessageAudio -> content.caption.toBody()
    is MessageVoiceNote -> content.caption.toBody()
    // Samolepka se kreslí, ne popisuje — její emoji by pod obrázkem bylo zdvojení.
    is MessageSticker -> MessageBody.Empty

    /*
     * Samotné emoji NENÍ textová zpráva.
     *
     * Telegram ho posílá jako `MessageAnimatedEmoji` s animovanou podobou navíc, takže spadlo
     * do větve `else` a v bublině z něj bylo slovo „Zpráva".
     *
     * Když je animace `.tgs`, kreslí se jako příloha (`MessageMedia.AnimatedEmoji`) a text zůstává
     * prázdný — jinak by pod animací stál ještě jednou jako znak. Bez animace, nebo v jiném formátu
     * (prémiová emoji můžou být cokoli), se vrací unicode znak: ten se vykreslí sám a chytí se
     * na něj vykreslení velkých emoji.
     */
    is MessageAnimatedEmoji ->
        if (content.animationFileId() != null) MessageBody.Empty else MessageBody(content.emoji)

    is MessageDice -> MessageBody(content.diceText())

    // Typy, které mají v bublině vlastní přílohu s popiskem — text by ho zdvojil.
    is MessageVideoNote, is MessageGame, is MessageInvoice -> MessageBody.Empty
    is MessagePaidMedia -> content.caption.toBody()

    /*
     * Zbytek nemá vlastní text, jen náš popis — a ten žádné entity nenese.
     *
     * Připojit k němu entity z původní zprávy by znamenalo rozsahy posunuté do jiného řetězce:
     * v lepším případě nesmyslně obarvené slovo, v horším pád na indexu mimo text.
     */
    else -> MessageBody(previewText())
}

/**
 * Text zprávy i s rozsahy formátování.
 *
 * Drží se pohromadě proto, aby se ty dvě věci nemohly rozejít — entity bez svého textu jsou
 * jen dvojice čísel a špatně spárované ukazují jinam, než by měly.
 */
internal data class MessageBody(
    val text: String,
    val entities: List<MessageEntity> = emptyList(),
) {
    companion object {
        val Empty = MessageBody(text = "")
    }
}

private fun FormattedText.toBody(): MessageBody =
    MessageBody(text = text, entities = entities.mapNotNull { it.toEntity(text) })

/**
 * Převod jedné entity z TDLib.
 *
 * `null` znamená „tenhle typ zatím nevykreslujeme" — text po něm zůstane obyčejný. Přeskakují se
 * hlavně zmínky, hashtagy a příkazy botů: obarvit je umíme, ale kliknutí by nemělo kam vést,
 * protože zmínka se hledá uvnitř aplikace, ne v prohlížeči. Viz [MessageEntityType].
 */
private fun TextEntity.toEntity(text: String): MessageEntity? {
    val mapped = when (val type = this.type) {
        // Adresa je přímo v textu. Bez schématu by ji `UrlOpener` nevzal.
        is TextEntityTypeUrl -> MessageEntityType.Link(text.range(this).withScheme())
        // Text je popisek, adresa přišla zvlášť — a ta už schéma má vždycky.
        is TextEntityTypeTextUrl -> MessageEntityType.Link(type.url)
        is TextEntityTypeEmailAddress -> MessageEntityType.Link("mailto:${text.range(this)}")
        is TextEntityTypePhoneNumber -> MessageEntityType.Link("tel:${text.range(this)}")

        is TextEntityTypeBold -> MessageEntityType.Bold
        is TextEntityTypeItalic -> MessageEntityType.Italic
        is TextEntityTypeUnderline -> MessageEntityType.Underline
        is TextEntityTypeStrikethrough -> MessageEntityType.Strikethrough
        // Inline kód i celý blok skončí stejně; liší se jen tím, že blok stojí na vlastních řádcích.
        is TextEntityTypeCode, is TextEntityTypePre, is TextEntityTypePreCode -> MessageEntityType.Code
        is TextEntityTypeSpoiler -> MessageEntityType.Spoiler

        else -> null
    }

    return mapped?.let { MessageEntity(offset = offset, length = length, type = it) }
}

/**
 * Úsek textu, na který entita ukazuje.
 *
 * Rozsahy se ořezávají do délky textu. Server posílá platné hodnoty, ale `substring` mimo rozsah
 * je pád aplikace kvůli jedné zprávě — a to je cena, kterou za důvěru platit nechceme.
 */
private fun String.range(entity: TextEntity): String {
    val start = entity.offset.coerceIn(0, length)
    val end = (entity.offset + entity.length).coerceIn(start, length)
    return substring(start, end)
}

/**
 * Doplní schéma odkazu psanému bez něj.
 *
 * TDLib označí za odkaz i holé `example.com`; `UrlOpener` by z toho na Androidu udělal relativní
 * URI bez schématu a intent by nikam nevedl.
 */
private fun String.withScheme(): String = if (contains("://")) this else "https://$this"

/**
 * Kostka (🎲, 🎯, 🎰 …).
 *
 * Hozená hodnota je součástí *animace*, kterou neumíme přehrát, takže se dopisuje číslem —
 * bez něj by ze zprávy zbylo jen „hodil jsem kostkou" bez výsledku. Nula znamená, že server
 * výsledek ještě neposlal; dorazí updatem.
 */
private fun MessageDice.diceText(): String = if (value > 0) "$emoji $value" else emoji

/** Příloha zprávy, nebo `null` u čistě textové. */
internal fun Message.messageMedia(): MessageMedia? = when (val content = this.content) {
    is MessagePhoto -> content.photo.toMedia()

    is MessageVideo -> MessageMedia.Attachment(
        label = "Video",
        thumbnailFileId = content.video.thumbnail?.file?.id,
    )

    is MessageAnimation -> MessageMedia.Attachment(
        label = "GIF",
        thumbnailFileId = content.animation.thumbnail?.file?.id,
    )

    is MessageDocument -> MessageMedia.Attachment(
        label = content.document.fileName.ifBlank { "Soubor" },
        thumbnailFileId = content.document.thumbnail?.file?.id,
    )

    is MessageAudio -> MessageMedia.Attachment(label = "Audio", thumbnailFileId = null)
    is MessageVoiceNote -> MessageMedia.Attachment(label = "Hlasová zpráva", thumbnailFileId = null)
    is MessageSticker -> content.sticker.toMedia()

    // O textu a příloze rozhoduje jedna funkce, `animationFileId` — obě větve se nesmí rozejít.
    is MessageAnimatedEmoji -> content.animationFileId()?.let { fileId ->
        MessageMedia.AnimatedEmoji(animationFileId = fileId, emoji = content.emoji)
    }

    // Kolečko s videem. Náhled je čtvercový, kulatý ořez by patřil k přehrávání, které neumíme.
    is MessageVideoNote -> MessageMedia.Attachment(
        label = "Video zpráva",
        thumbnailFileId = content.videoNote.thumbnail?.file?.id,
    )

    is MessageGame -> MessageMedia.Attachment(
        label = "Hra: ${content.game.title}",
        thumbnailFileId = content.game.photo.sizes.filterNotNull()
            .minByOrNull { it.width * it.height }?.photo?.id,
    )

    is MessageInvoice -> MessageMedia.Attachment(
        label = "Platba: ${content.productInfo.title}",
        thumbnailFileId = content.productInfo.photo?.sizes?.filterNotNull()
            ?.minByOrNull { it.width * it.height }?.photo?.id,
    )

    else -> null
}

/**
 * Samolepka na to, co se dá vykreslit.
 *
 * Coil umí `.webp`; `.tgs` je gzipovaná Lottie animace a `.webm` video — ani jedno se nedá poslat
 * do obrázkové knihovny. U nich zbývá statický náhled, který TDLib k samolepce dává, takže
 * z animované samolepky je aspoň nehybný obrázek místo slova „Samolepka".
 *
 * Náhled se bere jen v rastrovém formátu: `ThumbnailFormatTgs` a `Mpeg4` mají tentýž problém
 * jako samotná samolepka, takže by se jen přeneslo o úroveň níž.
 */
/**
 * Animace emoji, pokud je v přehratelném formátu.
 *
 * Společné rozhodnutí pro `captionBody()` i `messageMedia()`: kdyby každá větev rozhodovala sama,
 * mohla by zpráva skončit bez textu i bez přílohy, nebo s obojím.
 */
private fun MessageAnimatedEmoji.animationFileId(): Int? =
    animatedEmoji.sticker?.takeIf { it.format is StickerFormatTgs }?.sticker?.id

private fun Sticker.toMedia(): MessageMedia.Sticker {
    val rasterThumbnail = thumbnail?.takeIf {
        it.format is ThumbnailFormatWebp ||
            it.format is ThumbnailFormatJpeg ||
            it.format is ThumbnailFormatPng
    }

    return MessageMedia.Sticker(
        fileId = sticker.id.takeIf { format is StickerFormatWebp },
        animationFileId = sticker.id.takeIf { format is StickerFormatTgs },
        thumbnailFileId = rasterThumbnail?.file?.id,
        width = width,
        height = height,
        emoji = emoji,
    )
}

/**
 * Náhled odkazu, nebo `null`.
 *
 * Obrázek se bere jen z druhů, které nějaký nesou jako fotku: článek, fotka, obal videa, náhled
 * vloženého přehrávače. Ostatní (samolepka, hlasovka, chat, dárek…) mají jiné soubory, které
 * neumíme vykreslit; náhled u nich zůstane textový. Zcela prázdný náhled se zahodí — rámeček
 * bez obsahu by vypadal jako chyba načítání.
 */
internal fun Message.linkPreview(): LinkPreview? {
    val preview = (content as? MessageText)?.linkPreview ?: return null

    val photo = when (val type = preview.type) {
        is LinkPreviewTypeArticle -> type.photo
        is LinkPreviewTypePhoto -> type.photo
        is LinkPreviewTypeVideo -> type.cover
        is LinkPreviewTypeEmbeddedVideoPlayer -> type.thumbnail
        is LinkPreviewTypeEmbeddedAnimationPlayer -> type.thumbnail
        else -> null
    }?.toMedia()

    val result = LinkPreview(
        url = preview.url,
        siteName = preview.siteName.ifBlank { null },
        title = preview.title.ifBlank { null },
        description = preview.description.text.ifBlank { null },
        photo = photo,
        largePhoto = preview.showLargeMedia,
    )
    if (result.siteName == null && result.title == null && result.description == null && photo == null) {
        return null
    }
    return result
}

/**
 * Vybere z nabízených velikostí tu k zobrazení a tu nejmenší jako rychlý náhled.
 *
 * TDLib drží fotku v několika velikostech (typy `s`, `m`, `x`, `y`, `w` — od miniatury po originál).
 * Do bubliny se bere největší varianta do [MAX_DISPLAY_EDGE]; originál by u fotky z foťáku znamenal
 * desítky megabajtů kvůli obrázku širokému pár set bodů.
 */
private fun Photo.toMedia(): MessageMedia.Photo? {
    val available = sizes.filterNotNull().ifEmpty { return null }

    val display = available
        .filter { maxOf(it.width, it.height) <= MAX_DISPLAY_EDGE }
        .maxByOrNull { it.width * it.height }
        // Když jsou všechny varianty větší než práh, vezme se ta nejmenší z nich.
        ?: available.minByOrNull { it.width * it.height }
        ?: return null

    val thumbnail = available.minByOrNull { it.width * it.height }
    // Do prohlížeče patří to nejlepší, co server má — ten strop nad [MAX_DISPLAY_EDGE] platí
    // jen pro bublinu.
    val full = available.maxByOrNull { it.width * it.height } ?: display

    return MessageMedia.Photo(
        fileId = display.photo.id,
        // Když je náhled tentýž soubor jako zobrazovaný, nemá smysl ho stahovat dvakrát.
        thumbnailFileId = thumbnail?.photo?.id?.takeIf { it != display.photo.id },
        fullFileId = full.photo.id,
        width = display.width,
        height = display.height,
    )
}

/** Delší hrana zobrazované varianty fotky. Nad tím už jde o zbytečná data. */
private const val MAX_DISPLAY_EDGE = 1280
