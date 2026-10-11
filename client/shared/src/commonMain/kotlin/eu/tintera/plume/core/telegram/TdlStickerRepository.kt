package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.InputFileId
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.StickerFullTypeRegular
import dev.g000sha256.tdl.dto.StickerTypeRegular
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * [StickerRepository] nad TDLib.
 *
 * Každý seznam se na update **čte znovu**, neodvozuje se z něj: updaty nesou jen id (sad, souborů),
 * ne samolepky samotné. Odběr stojí před prvním čtením (`onStart`), aby se mezi nimi nic neztratilo.
 * Klient se nikde nezachytává — po odhlášení je jiný, viz `TelegramClientHolder`.
 */
/**
 * Kolik trendů se načte. Bez stránkování: první várka pokryje to, co si kdo v trendech prohlíží,
 * a TDLib stejně vrací méně, než o kolik se řekne.
 */
private const val TRENDING_LIMIT = 50

/** Nejvíc samolepek z jednoho hledání. TDLib víc než 100 nevrátí. */
private const val SEARCH_LIMIT = 100

/** Kolik samolepek se nabídne k emoji nad polem. Řádek se posouvá, víc by nikdo neprocházel. */
private const val SUGGESTION_LIMIT = 30

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

    override fun stickerSetDetail(setId: Long): Flow<StickerSetDetail?> =
        holder.updates { installedStickerSetsUpdates }
            // Přidání i odebrání sady mění seznam nainstalovaných; jiný update o sadě TDLib nepošle.
            .filter { it.stickerType is StickerTypeRegular }
            .map { }
            .onStart { emit(Unit) }
            .map {
                client.getStickerSet(setId = setId)
                    .toResult()
                    .map { set ->
                        StickerSetDetail(
                            id = set.id,
                            title = set.title,
                            isInstalled = set.isInstalled && !set.isArchived,
                            isOfficial = set.isOfficial,
                            stickers = set.stickers.toItems(),
                        )
                    }
                    .getOrElse { failure ->
                        logger.w(failure) { "Sadu $setId se nepodařilo načíst." }
                        null
                    }
            }

    override fun trendingSets(): Flow<List<TrendingStickerSet>> =
        merge(
            holder.updates { trendingStickerSetsUpdates }.filter { it.stickerType is StickerTypeRegular }.map { },
            // Přidání sady z trendů mění jen seznam nainstalovaných; trendy samy update nepošlou.
            holder.updates { installedStickerSetsUpdates }.filter { it.stickerType is StickerTypeRegular }.map { },
        )
            .onStart { emit(Unit) }
            .map {
                client.getTrendingStickerSets(stickerType = StickerTypeRegular(), offset = 0, limit = TRENDING_LIMIT)
                    .toResult()
                    .map { trending ->
                        trending.sets.filterNotNull().map { set ->
                            TrendingStickerSet(
                                id = set.id,
                                title = set.title,
                                isInstalled = set.isInstalled && !set.isArchived,
                                covers = set.covers.filterNotNull().map { it.toMedia() },
                            )
                        }
                    }
                    .getOrElse { failure ->
                        logger.w(failure) { "Trendy se nepodařilo načíst." }
                        emptyList()
                    }
            }

    override suspend fun markTrendingViewed(setIds: List<Long>) {
        if (setIds.isEmpty()) return
        client.viewTrendingStickerSets(stickerSetIds = setIds.toLongArray())
    }

    override suspend fun setInstalled(setId: Long, installed: Boolean): Result<Unit> =
        client.changeStickerSet(
            setId = setId,
            isInstalled = installed,
            // Archivace je třetí stav („odložená" sada); odebrání ji nepotřebuje, přidání ji ruší.
            isArchived = false,
        )
            .toResult()
            .map { }

    override suspend fun setFavorite(stickerFileId: Int, favorite: Boolean): Result<Unit> {
        val file = InputFileId(id = stickerFileId)
        return if (favorite) {
            client.addFavoriteSticker(sticker = file)
        } else {
            client.removeFavoriteSticker(sticker = file)
        }
            .toResult()
            .map { }
    }

    override suspend fun stickerSet(setId: Long): Result<List<StickerItem>> =
        client.getStickerSet(setId = setId)
            .toResult()
            .map { it.stickers.toItems() }

    /**
     * Hledání ve dvou zdrojích.
     *
     * - `getStickers` prochází nainstalované sady: podle emoji i podle klíčových slov samolepek.
     *   U slova se proto ptá dvakrát — jednou slovem, jednou emoji, na které se převedlo.
     * - `searchStickers` prochází veřejné sady, ale jen podle emoji (slovo je tam jen upřesnění).
     *
     * Slovo se na emoji převádí přes `getKeywordEmojis` v jazyce uživatele. Když převod nic nenajde,
     * veřejné hledání se vynechá — bez emoji by vrátilo nahodilé samolepky.
     */
    override suspend fun search(query: StickerQuery): StickerSearchResult {
        val languages = (query as? StickerQuery.Text)?.languageCodes.orEmpty().toTypedArray()

        val emojis = when (query) {
            is StickerQuery.Emoji -> query.emojis
            is StickerQuery.Text -> client.getKeywordEmojis(text = query.text, inputLanguageCodes = languages)
                .toResult()
                .map { it.emojis.filterNotNull().distinct().joinToString(" ") }
                .getOrElse { failure ->
                    logger.w(failure) { "Emoji ke slovu se nepodařilo dohledat." }
                    ""
                }
        }

        val installedQueries = buildList {
            if (query is StickerQuery.Text) add(query.text)
            if (emojis.isNotBlank()) add(emojis)
        }
        val installed = installedQueries
            .flatMap { installedQuery ->
                client.getStickers(
                    stickerType = StickerTypeRegular(),
                    query = installedQuery,
                    limit = SEARCH_LIMIT,
                    // Běžné samolepky na chatu nezávisí; na něm záleží jen u vlastních emoji.
                    chatId = 0L,
                )
                    .toResult()
                    .map { it.stickers.toItems() }
                    .getOrElse { failure ->
                        logger.w(failure) { "Hledání ve vlastních sadách selhalo." }
                        emptyList()
                    }
            }
            .distinctBy { it.id }

        val public = if (emojis.isBlank()) {
            emptyList()
        } else {
            client.searchStickers(
                stickerType = StickerTypeRegular(),
                emojis = emojis,
                query = (query as? StickerQuery.Text)?.text.orEmpty(),
                inputLanguageCodes = languages,
                offset = 0,
                limit = SEARCH_LIMIT,
            )
                .toResult()
                .map { it.stickers.toItems() }
                .getOrElse { failure ->
                    logger.w(failure) { "Hledání ve veřejných sadách selhalo." }
                    emptyList()
                }
        }

        val installedIds = installed.map { it.id }.toSet()
        return StickerSearchResult(
            installed = installed,
            public = public.filterNot { it.id in installedIds },
        )
    }

    override suspend fun stickersForEmoji(emoji: String): List<StickerItem> =
        client.getStickers(
            stickerType = StickerTypeRegular(),
            query = emoji,
            limit = SUGGESTION_LIMIT,
            chatId = 0L,
        )
            .toResult()
            .map { it.stickers.toItems() }
            .getOrElse { failure ->
                logger.w(failure) { "Samolepky k emoji se nepodařilo načíst." }
                emptyList()
            }

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
