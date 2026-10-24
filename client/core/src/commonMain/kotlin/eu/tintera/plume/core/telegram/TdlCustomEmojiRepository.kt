package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.StickerFormatTgs
import dev.g000sha256.tdl.dto.StickerFormatWebp
import dev.g000sha256.tdl.dto.StickerFullTypeCustomEmoji
import dev.g000sha256.tdl.dto.ThumbnailFormatJpeg
import dev.g000sha256.tdl.dto.ThumbnailFormatPng
import dev.g000sha256.tdl.dto.ThumbnailFormatWebp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Vlastní emoji z `getCustomEmojiStickers`.
 *
 * **Dotazy se sbírají do dávky** ([BATCH_DELAY_MS]): zpráva v kanálu nese emoji po desítkách
 * a každé se skládá samo, takže by jinak šlo desítky volání naráz. Výsledek se pamatuje po
 * dobu života klienta — `fileId` v něm platí jen v jedné databázi TDLib, po odhlášení se zahazuje.
 */
class TdlCustomEmojiRepository(
    private val holder: TelegramClientHolder,
    private val scope: CoroutineScope,
) : CustomEmojiRepository {

    private val logger = Logger.withTag("CustomEmoji")

    private val client: TdlClient get() = holder.client

    private val mutex = Mutex()
    private val known = mutableMapOf<Long, CompletableDeferred<CustomEmoji?>>()
    private val pending = mutableSetOf<Long>()
    private var flush: Job? = null

    init {
        scope.launch {
            holder.replaced.collect { mutex.withLock { known.clear() } }
        }
    }

    override suspend fun customEmoji(id: Long): CustomEmoji? {
        val result = mutex.withLock {
            known.getOrPut(id) {
                pending += id
                if (flush?.isActive != true) flush = scope.launch { load() }
                CompletableDeferred()
            }
        }
        return result.await()
    }

    private suspend fun load() {
        delay(BATCH_DELAY_MS)
        val ids = mutex.withLock { pending.toList().also { pending.clear() } }
        ids.chunked(MAX_PER_CALL).forEach { chunk ->
            val stickers = client.getCustomEmojiStickers(customEmojiIds = chunk.toLongArray())
                .toResult()
                .onFailure { logger.w(it) { "Vlastní emoji se nepodařilo načíst." } }
                .getOrNull()
                ?.stickers
                ?.filterNotNull()
                .orEmpty()
            val found = stickers.mapNotNull { it.toCustomEmoji() }.associateBy { it.id }
            mutex.withLock {
                chunk.forEach { id ->
                    val emoji = found[id]
                    // Nevydané se zapomene, ať se o něj příště řekne znovu (výpadek sítě).
                    val deferred = if (emoji == null) known.remove(id) else known[id]
                    deferred?.complete(emoji)
                }
            }
        }
        // Dotazy, které přišly během načítání, čekají na další dávku.
        mutex.withLock {
            if (pending.isNotEmpty()) flush = scope.launch { load() }
        }
    }

    private companion object {
        /** Kolik na dávku počkat — jeden snímek, ať se sejdou emoji celé zprávy. */
        const val BATCH_DELAY_MS = 16L

        /** Strop `getCustomEmojiStickers`. */
        const val MAX_PER_CALL = 200
    }
}

private fun Sticker.toCustomEmoji(): CustomEmoji? {
    val type = fullType as? StickerFullTypeCustomEmoji ?: return null
    val rasterThumbnail = thumbnail?.takeIf {
        it.format is ThumbnailFormatWebp || it.format is ThumbnailFormatJpeg || it.format is ThumbnailFormatPng
    }
    return CustomEmoji(
        id = type.customEmojiId,
        stillFileId = sticker.id.takeIf { format is StickerFormatWebp } ?: rasterThumbnail?.file?.id,
        animationFileId = sticker.id.takeIf { format is StickerFormatTgs },
        emoji = emoji,
        tinted = type.needsRepainting,
    )
}
