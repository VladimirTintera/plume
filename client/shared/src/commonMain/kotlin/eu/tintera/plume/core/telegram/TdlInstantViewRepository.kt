package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlThrowable
import dev.g000sha256.tdl.dto.PageBlockTitle
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_audio
import eu.tintera.plume.resources.content_file
import eu.tintera.plume.resources.content_gif
import eu.tintera.plume.resources.conversation_voice_message
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_video

class TdlInstantViewRepository(private val holder: TelegramClientHolder) : InstantViewRepository {

    private val logger = Logger.withTag("InstantView")

    override suspend fun page(url: String): Result<InstantViewPage?> =
        holder.client.getWebPageInstantView(url = url, onlyLocal = false).toResult()
            .map<InstantViewPage?, dev.g000sha256.tdl.dto.WebPageInstantView> { page ->
                // Název článku jde do nadpisu čtečky; v textu by byl jen tučným řádkem.
                val titleBlock = page.blocks.firstOrNull { it is PageBlockTitle } as PageBlockTitle?
                InstantViewPage(
                    url = url,
                    title = titleBlock?.title?.plainText()?.trim()?.takeIf { it.isNotEmpty() },
                    body = page.blocks.filterNot { it === titleBlock }.toTypedArray().toRichBody(
                        RichMediaLabels(
                            photo = readString(Res.string.media_photo),
                            video = readString(Res.string.media_video),
                            gif = readString(Res.string.content_gif),
                            audio = readString(Res.string.content_audio),
                            voice = readString(Res.string.conversation_voice_message),
                            file = readString(Res.string.content_file),
                        ),
                        isFull = page.isFull,
                        spacious = true,
                    ),
                    viewCount = page.viewCount,
                    isRtl = page.isRtl,
                    isPartial = !page.isFull,
                )
            }
            // 404 = stránka Instant View nemá; to je odpověď, ne chyba.
            .recoverCatching { failure -> if (failure is TdlThrowable && failure.code == NOT_FOUND) null else throw failure }
            .onFailure { logger.w(it) { "Instant View se nepodařilo načíst." } }

    private companion object {
        const val NOT_FOUND = 404
    }
}
