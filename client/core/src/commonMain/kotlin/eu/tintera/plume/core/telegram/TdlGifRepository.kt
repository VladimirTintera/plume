package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.Animation
import dev.g000sha256.tdl.dto.InlineQueryResultAnimation
import dev.g000sha256.tdl.dto.InputFileId
import dev.g000sha256.tdl.dto.ThumbnailFormatMpeg4
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * [GifRepository] nad TDLib.
 *
 * Uložené se na update **čtou znovu** — `UpdateSavedAnimations` nese jen id souborů. Klient se
 * nikde nezachytává (viz `TelegramClientHolder`).
 */
class TdlGifRepository(
    private val holder: TelegramClientHolder,
) : GifRepository {

    private val logger = Logger.withTag("Gifs")

    private val client: TdlClient get() = holder.client

    private val bot = InlineBot(holder, option = "animation_search_bot_username", fallback = DEFAULT_GIF_BOT)

    override fun savedGifs(): Flow<List<GifItem>> =
        holder.updates { savedAnimationsUpdates }
            .map { }
            .onStart { emit(Unit) }
            .map {
                client.getSavedAnimations()
                    .toResult()
                    .map { saved -> saved.animations.filterNotNull().map { GifItem(it.toMedia()) } }
                    .getOrElse { failure ->
                        logger.w(failure) { "Uložené GIFy se nepodařilo načíst." }
                        emptyList()
                    }
            }

    override suspend fun search(chatId: Long, query: String, offset: String): Result<GifPage> {
        val botUserId = bot.userId().getOrElse { return Result.failure(it) }
        return client.getInlineQueryResults(
            botUserId = botUserId,
            chatId = chatId,
            userLocation = null,
            query = query,
            offset = offset,
        )
            .toResult()
            .map { results ->
                GifPage(
                    // Bot občas vrátí tentýž soubor dvakrát; klíč v mřížce musí být jedinečný.
                    gifs = results.results.filterNotNull()
                        .filterIsInstance<InlineQueryResultAnimation>()
                        .map { GifItem(it.animation.toMedia()) }
                        .distinctBy { it.id },
                    nextOffset = results.nextOffset,
                )
            }
    }

    override suspend fun removeSaved(gif: GifItem): Result<Unit> =
        client.removeSavedAnimation(animation = InputFileId(id = gif.animation.fileId))
            .toResult()
            .map { }

    override suspend fun addSaved(gif: GifItem): Result<Unit> =
        client.addSavedAnimation(animation = InputFileId(id = gif.animation.fileId))
            .toResult()
            .map { }

    private companion object {
        /** Kdyby server volbu neposlal. Tak se GIF bot jmenuje odjakživa. */
        const val DEFAULT_GIF_BOT = "gif"
    }
}

/** GIF tak, jak ho kreslí konverzace. Sdílí to bublina (`MessageAnimation`) i výběr. */
internal fun Animation.toMedia() = MessageMedia.Animation(
    fileId = animation.id,
    // Náhled MPEG-4 není obrázek a Coil ho neotevře — tatáž past jako u videa.
    thumbnailFileId = thumbnail?.takeIf { it.format !is ThumbnailFormatMpeg4 }?.file?.id,
    width = width,
    height = height,
    // `expectedSize` je známá i před stažením; `size` je nula, dokud soubor nedorazí.
    sizeBytes = animation.expectedSize,
    minithumbnail = minithumbnail?.toDomain(),
)
