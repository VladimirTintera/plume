package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVoiceNote

/**
 * Textová podoba obsahu zprávy.
 *
 * Sdílí ji náhled v seznamu chatů i samotná konverzace, aby tytéž zprávy nevypadaly na dvou
 * místech jinak. Text se bere doslova, u médií se použije popisek, a když ho zpráva nemá,
 * název typu — prázdný řádek u fotky vypadá jako chyba načítání.
 *
 * Až přijde skutečné vykreslování médií, nahradí tohle u těch typů bohatší model; tenhle
 * mapper zůstane jako fallback pro náhledy.
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
    else -> "Zpráva"
}
