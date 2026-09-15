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
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.Sticker
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
    else -> "Zpráva"
}

/**
 * Popisek zprávy pro **konverzaci**.
 *
 * Na rozdíl od [previewText] vrací prázdný řetězec, když zpráva žádný text nemá — v bublině je
 * vidět samotná fotka a dopisovat pod ni slovo „Fotka" by byl šum. Typ přílohy nese [messageMedia].
 */
internal fun Message.captionText(): String = when (val content = this.content) {
    is MessageText -> content.text.text
    is MessagePhoto -> content.caption.text
    is MessageVideo -> content.caption.text
    is MessageAnimation -> content.caption.text
    is MessageDocument -> content.caption.text
    is MessageAudio -> content.caption.text
    is MessageVoiceNote -> content.caption.text
    // Samolepka se kreslí, ne popisuje — její emoji by pod obrázkem bylo zdvojení.
    is MessageSticker -> ""

    /*
     * Samotné emoji NENÍ textová zpráva.
     *
     * Telegram ho posílá jako `MessageAnimatedEmoji` s animovanou podobou navíc, takže spadlo
     * do větve `else` a v bublině z něj bylo slovo „Zpráva". `emoji` je obyčejný unicode znak,
     * ten se vykreslí sám a rovnou se na něj chytí i vykreslení velkých emoji.
     *
     * Animovaná podoba (`animatedEmoji.sticker`) je `.tgs`, kterou stejně neumíme přehrát,
     * a u prémiových emoji může být v libovolném formátu. Unicode znak je proti tomu vždycky
     * k dispozici a ostrý v každé velikosti.
     */
    is MessageAnimatedEmoji -> content.emoji

    is MessageDice -> content.diceText()

    else -> previewText()
}

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
private fun Sticker.toMedia(): MessageMedia.Sticker {
    val rasterThumbnail = thumbnail?.takeIf {
        it.format is ThumbnailFormatWebp ||
            it.format is ThumbnailFormatJpeg ||
            it.format is ThumbnailFormatPng
    }

    return MessageMedia.Sticker(
        fileId = sticker.id.takeIf { format is StickerFormatWebp },
        thumbnailFileId = rasterThumbnail?.file?.id,
        width = width,
        height = height,
        emoji = emoji,
    )
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

    return MessageMedia.Photo(
        fileId = display.photo.id,
        // Když je náhled tentýž soubor jako zobrazovaný, nemá smysl ho stahovat dvakrát.
        thumbnailFileId = thumbnail?.photo?.id?.takeIf { it != display.photo.id },
        width = display.width,
        height = display.height,
    )
}

/** Delší hrana zobrazované varianty fotky. Nad tím už jde o zbytečná data. */
private const val MAX_DISPLAY_EDGE = 1280
