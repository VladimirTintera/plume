package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.StickerFullTypeRegular
import dev.g000sha256.tdl.dto.StickerTypeRegular
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * [StickerRepository] nad TDLib.
 *
 * Každý seznam se na update **čte znovu**, neodvozuje se z něj: updaty nesou jen id (sad, souborů),
 * ne samolepky samotné. Odběr stojí před prvním čtením (`onStart`), aby se mezi nimi nic neztratilo.
 * Klient se nikde nezachytává — po odhlášení je jiný, viz `TelegramClientHolder`.
 */
class TdlStickerRepository(
    private val holder: TelegramClientHolder,
) : StickerRepository {

    private val logger = Logger.withTag("Stickers")

    private val client: TdlClient get() = holder.client

    override fun installedSets(): Flow<List<StickerSetSummary>> =
        holder.updates { installedStickerSetsUpdates }
            // Masky a vlastní emoji mají vlastní seznamy; výběr samolepek se jich netýká.
            .filter { it.stickerType is StickerTypeRegular }
            .map { }
            .onStart { emit(Unit) }
            .map {
                client.getInstalledStickerSets(stickerType = StickerTypeRegular())
                    .toResult()
                    .map { sets ->
                        sets.sets.filterNotNull().map { set ->
                            StickerSetSummary(
                                id = set.id,
                                title = set.title,
                                cover = set.covers.filterNotNull().firstOrNull()?.toMedia(),
                            )
                        }
                    }
                    .getOrElse { failure ->
                        logger.w(failure) { "Nainstalované sady se nepodařilo načíst." }
                        emptyList()
                    }
            }

    override fun recentStickers(): Flow<List<StickerItem>> =
        holder.updates { recentStickersUpdates }
            // „Připojené" jsou samolepky přidané k fotkám, ne odeslané samostatně.
            .filter { !it.isAttached }
            .map { }
            .onStart { emit(Unit) }
            .map {
                client.getRecentStickers(isAttached = false)
                    .toResult()
                    .map { it.stickers.toItems() }
                    .getOrElse { failure ->
                        logger.w(failure) { "Nedávné samolepky se nepodařilo načíst." }
                        emptyList()
                    }
            }

    override fun favoriteStickers(): Flow<List<StickerItem>> =
        holder.updates { favoriteStickersUpdates }
            .map { }
            .onStart { emit(Unit) }
            .map {
                client.getFavoriteStickers()
                    .toResult()
                    .map { it.stickers.toItems() }
                    .getOrElse { failure ->
                        logger.w(failure) { "Oblíbené samolepky se nepodařilo načíst." }
                        emptyList()
                    }
            }

    override suspend fun stickerSet(setId: Long): Result<List<StickerItem>> =
        client.getStickerSet(setId = setId)
            .toResult()
            .map { it.stickers.toItems() }

    /**
     * Převod na položky výběru, bez prémiových samolepek u uživatele bez Premium.
     *
     * Na Premium se ptá při každém čtení: předplatné se mění a `getMe` je lokální dotaz. Když
     * se zjistit nepodaří, bere se, že Premium není — nabídnout neodeslatelnou samolepku je horší.
     */
    private suspend fun Array<Sticker>.toItems(): List<StickerItem> {
        val isPremium = client.getMe().toResult().getOrNull()?.isPremium == true

        return filterNotNull()
            .filter { sticker ->
                isPremium || (sticker.fullType as? StickerFullTypeRegular)?.premiumAnimation == null
            }
            .map { sticker ->
                StickerItem(
                    id = sticker.id,
                    sendFileId = sticker.sticker.id,
                    preview = sticker.toMedia(),
                )
            }
    }
}
