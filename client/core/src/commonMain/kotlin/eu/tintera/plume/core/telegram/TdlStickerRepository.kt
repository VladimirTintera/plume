package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.StickerFormatWebp
import dev.g000sha256.tdl.dto.NewSticker
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.EmojiCategorySourceSearch
import dev.g000sha256.tdl.dto.EmojiCategoryTypeRegularStickers
import dev.g000sha256.tdl.dto.InputFileId
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.StickerSetInfo
import dev.g000sha256.tdl.dto.CheckStickerSetNameResultNameInvalid
import dev.g000sha256.tdl.dto.CheckStickerSetNameResultNameOccupied
import dev.g000sha256.tdl.dto.CheckStickerSetNameResultOk
import dev.g000sha256.tdl.dto.StickerFullTypeCustomEmoji
import dev.g000sha256.tdl.dto.StickerFullTypeRegular
import dev.g000sha256.tdl.dto.StickerSet
import dev.g000sha256.tdl.dto.StickerType
import dev.g000sha256.tdl.dto.StickerTypeCustomEmoji
import dev.g000sha256.tdl.dto.StickerTypeRegular
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
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

/** Stránka archivovaných sad. TDLib víc než 100 najednou nevrátí. */
private const val ARCHIVED_PAGE = 100

/** Kolik nainstalovaných sad se najde podle názvu. Veřejné hledání limit nemá, TDLib vrací pár desítek. */
private const val SET_SEARCH_LIMIT = 20

/** Kolik samolepek se nabídne k emoji nad polem. Řádek se posouvá, víc by nikdo neprocházel. */
private const val SUGGESTION_LIMIT = 30

class TdlStickerRepository(
    private val holder: TelegramClientHolder,
) : StickerRepository {

    private val logger = Logger.withTag("Stickers")

    private val client: TdlClient get() = holder.client

    override fun installedSets(kind: StickerSetKind): Flow<List<StickerSetSummary>> =
        merge(
            holder.updates { installedStickerSetsUpdates }
                // Každý druh má vlastní seznam (masky, samolepky, emoji); bere se jen ten jeden.
                .filter { it.stickerType.kind() == kind }
                .map { },
            // Přejmenovaná nainstalovaná sada nebo nová obálka; seznam nainstalovaných se tím nemění.
            installedSetChanges { it.stickerType.kind() == kind },
        )
            .onStart { emit(Unit) }
            .map {
                client.getInstalledStickerSets(stickerType = kind.toTdl())
                    .toResult()
                    .map { sets ->
                        sets.sets.toSummaries()
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
        merge(
            // Přidání i odebrání sady mění seznam nainstalovaných, ne sadu samu.
            // Bez filtru druhu: detail ukazuje i sady vlastních emoji.
            holder.updates { installedStickerSetsUpdates }.map { },
            // Změna obsahu nebo názvu sady (autor přidal samolepku) — `UpdateStickerSet`.
            holder.updates { stickerSetUpdates }.filter { it.stickerSet.id == setId }.map { },
        )
            .onStart { emit(Unit) }
            .map {
                client.getStickerSet(setId = setId)
                    .toResult()
                    .map { set ->
                        StickerSetDetail(
                            id = set.id,
                            title = set.title,
                            name = set.name,
                            isInstalled = set.isInstalled && !set.isArchived,
                            isOfficial = set.isOfficial,
                            stickers = set.stickers.toItems(),
                            isCustomEmoji = set.stickerType is StickerTypeCustomEmoji,
                            isOwned = set.isOwned,
                            customCover = set.thumbnail?.toSetCover(set.id),
                        )
                    }
                    .getOrElse { failure ->
                        logger.w(failure) { "Sadu $setId se nepodařilo načíst." }
                        null
                    }
            }

    /**
     * `UpdateStickerSet` chodí jen u sady, kterou už TDLib načetl CELOU a klientovi jednou
     * poslal, a jen při skutečné změně (`StickersManager::update_sticker_set`: `is_changed_`,
     * `was_loaded_`, `was_update_sent_`). Načtení sady ani přidání sady z trendů (zná jen ukázku)
     * ho nevyvolá — obnova obsahu se tedy nezacyklí.
     */
    override fun changedSets(): Flow<Long> =
        holder.updates { stickerSetUpdates }.map { it.stickerSet.id }

    /**
     * Změna nainstalované sady daného druhu (`UpdateStickerSet`). Chodí i u sad, které uživatel
     * nemá (otevřený detail, právě odebraná) — ty seznam nainstalovaných nemění.
     */
    private fun installedSetChanges(kind: (StickerSet) -> Boolean): Flow<Unit> =
        holder.updates { stickerSetUpdates }
            .filter { it.stickerSet.isInstalled && !it.stickerSet.isArchived && kind(it.stickerSet) }
            .map { }

    override fun trendingSets(): Flow<List<StickerSetSample>> =
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
                        trending.sets.toSamples()
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

    override suspend fun removeRecent(stickerFileId: Int): Result<Unit> =
        client.removeRecentSticker(isAttached = false, sticker = InputFileId(id = stickerFileId))
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Samolepku se nepodařilo odebrat z nedávných." } }

    override suspend fun clearRecent(): Result<Unit> =
        client.clearRecentStickers(isAttached = false)
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Nedávné samolepky se nepodařilo vymazat." } }

    override suspend fun setArchived(setId: Long, archived: Boolean): Result<Unit> =
        // Nainstalovaná a archivovaná zároveň sada být nemůže; archiv ji z nainstalovaných vyjme.
        client.changeStickerSet(setId = setId, isInstalled = !archived, isArchived = archived)
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Sadu $setId se nepodařilo ${if (archived) "archivovat" else "vrátit z archivu"}." } }

    override suspend fun reorderSets(setIds: List<Long>, kind: StickerSetKind): Result<Unit> =
        client.reorderInstalledStickerSets(stickerType = kind.toTdl(), stickerSetIds = setIds.toLongArray())
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Pořadí sad se nepodařilo uložit." } }

    override suspend fun archivedSets(afterSetId: Long, kind: StickerSetKind): Result<ArchivedStickerSets> =
        client.getArchivedStickerSets(
            stickerType = kind.toTdl(),
            offsetStickerSetId = afterSetId,
            limit = ARCHIVED_PAGE,
        )
            .toResult()
            .map { ArchivedStickerSets(total = it.totalCount, sets = it.sets.toSummaries()) }
            .onFailure { logger.w(it) { "Archivované sady se nepodařilo načíst." } }

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

    override suspend fun groupStickerSet(chatId: Long): GroupStickerSet? {
        val chat = client.getChat(chatId = chatId).toResult().getOrNull() ?: return null
        val type = chat.type as? ChatTypeSupergroup ?: return null
        // Kanál sadu skupiny nemá (a samolepky do něj píše jen správce).
        if (type.isChannel) return null

        val setId = client.getSupergroupFullInfo(supergroupId = type.supergroupId)
            .toResult()
            .getOrElse { failure ->
                logger.w(failure) { "Údaje skupiny $chatId kvůli sadě se nepodařilo načíst." }
                return null
            }
            .stickerSetId
        if (setId == 0L) return null

        val set = client.getStickerSet(setId = setId).toResult().getOrElse { failure ->
            logger.w(failure) { "Sadu skupiny $chatId se nepodařilo načíst." }
            return null
        }
        return GroupStickerSet(
            set = StickerSetSummary(
                id = set.id,
                title = set.title,
                cover = set.thumbnail?.toSetCover(set.id) ?: set.stickers.filterNotNull().firstOrNull()?.toMedia(),
            ),
            chatId = chatId,
            chatTitle = chat.title,
            chatPhotoFileId = chat.photo?.small?.id,
        )
    }

    override suspend fun canUseCustomEmoji(chatId: Long): Boolean {
        val me = client.getMe().toResult().getOrNull() ?: return false
        // Uložené zprávy jsou soukromý chat se sebou samým: jeho id je id uživatele.
        return me.isPremium || chatId == me.id
    }

    override fun customEmojiSets(): Flow<List<CustomEmojiSet>> =
        merge(
            holder.updates { installedStickerSetsUpdates }
                .filter { it.stickerType is StickerTypeCustomEmoji }
                .map { },
            installedSetChanges { it.stickerType is StickerTypeCustomEmoji },
        )
            .onStart { emit(Unit) }
            .map {
                client.getInstalledStickerSets(stickerType = StickerTypeCustomEmoji())
                    .toResult()
                    .map { sets ->
                        sets.sets.filterNotNull().map { set ->
                            CustomEmojiSet(
                                id = set.id,
                                title = set.title,
                                cover = set.covers.filterNotNull().firstOrNull()?.toCustomEmojiItem(),
                            )
                        }
                    }
                    .getOrElse { failure ->
                        logger.w(failure) { "Sady vlastních emoji se nepodařilo načíst." }
                        emptyList()
                    }
            }

    override suspend fun defaultPhotoEmoji(forChat: Boolean): Result<List<CustomEmojiItem>> =
        (if (forChat) client.getDefaultChatPhotoCustomEmojiStickers() else client.getDefaultProfilePhotoCustomEmojiStickers())
            .toResult()
            .map { found -> found.stickers.filterNotNull().mapNotNull { it.toCustomEmojiItem() } }
            .onFailure { logger.w(it) { "Emoji na profilovou fotku se nepodařilo načíst." } }

    override suspend fun customEmojiSet(setId: Long): Result<List<CustomEmojiItem>> =
        client.getStickerSet(setId = setId)
            .toResult()
            .map { set -> set.stickers.filterNotNull().mapNotNull { it.toCustomEmojiItem() } }

    /**
     * `getStickers` s typem vlastních emoji, dvakrát jako u samolepek: jednou textem (klíčová
     * slova samolepek, prefix), jednou emoji, na která se převedl. Bez veřejného
     * `searchStickers` — veřejná vlastní emoji z cizích sad by bez Premium poslat nešla.
     */
    override suspend fun searchCustomEmoji(text: String, emojis: List<String>, chatId: Long): List<CustomEmojiItem> =
        listOf(text.trim(), emojis.joinToString(" "))
            .filter { it.isNotBlank() }
            .distinct()
            .flatMap { query ->
                client.getStickers(
                    stickerType = StickerTypeCustomEmoji(),
                    query = query,
                    limit = SEARCH_LIMIT,
                    chatId = chatId,
                )
                    .toResult()
                    .map { result -> result.stickers.filterNotNull().mapNotNull { it.toCustomEmojiItem() } }
                    .getOrElse { failure ->
                        logger.w(failure) { "Hledání vlastních emoji selhalo." }
                        emptyList()
                    }
            }
            .distinctBy { it.id }

    override fun trendingCustomEmojiSets(): Flow<List<CustomEmojiSetSample>> =
        merge(
            holder.updates { trendingStickerSetsUpdates }.filter { it.stickerType is StickerTypeCustomEmoji }.map { },
            holder.updates { installedStickerSetsUpdates }.filter { it.stickerType is StickerTypeCustomEmoji }.map { },
        )
            .onStart { emit(Unit) }
            .map {
                client.getTrendingStickerSets(stickerType = StickerTypeCustomEmoji(), offset = 0, limit = TRENDING_LIMIT)
                    .toResult()
                    .map { trending ->
                        trending.sets.filterNotNull().map { set ->
                            CustomEmojiSetSample(
                                id = set.id,
                                title = set.title,
                                isInstalled = set.isInstalled && !set.isArchived,
                                covers = set.covers.filterNotNull().mapNotNull { it.toCustomEmojiItem() },
                            )
                        }
                    }
                    .getOrElse { failure ->
                        logger.w(failure) { "Populární sady vlastních emoji se nepodařilo načíst." }
                        emptyList()
                    }
            }

    override suspend fun attachedStickerSets(fileId: Int): Result<List<StickerSetSummary>> =
        client.getAttachedStickerSets(fileId = fileId).toResult().map { it.sets.toSummaries() }

    /** Obrysy podle souboru samolepky — mřížka výběru se ptá na stovky samolepek opakovaně. */
    private val outlines = mutableMapOf<Int, String>()
    private val outlinesMutex = Mutex()

    override suspend fun outline(stickerFileId: Int): String {
        outlinesMutex.withLock { outlines[stickerFileId] }?.let { return it }
        val path = client.getStickerOutlineSvgPath(
            stickerFileId = stickerFileId,
            forAnimatedEmoji = false,
            forClickedAnimatedEmojiMessage = false,
        ).toResult().getOrNull()?.text.orEmpty()
        outlinesMutex.withLock { outlines[stickerFileId] = path }
        return path
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
    override suspend fun search(query: StickerQuery): StickerSearchResult = coroutineScope {
        // Sady podle názvu jdou souběžně se samolepkami; veřejné hledání sad jde na server.
        val sets = (query as? StickerQuery.Text)?.let { async { searchSets(it.text) } }
        val stickers = searchStickers(query)
        stickers.copy(sets = sets?.await().orEmpty())
    }

    private suspend fun searchStickers(query: StickerQuery): StickerSearchResult {
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

    /**
     * Sady podle názvu: nainstalované (`searchInstalledStickerSets`) a veřejné (`searchStickerSets`,
     * ta nainstalované sama vynechá). Nainstalované jdou první — tu, kterou uživatel hledá, většinou
     * už má a jde se k ní jen proklikat.
     */
    private suspend fun searchSets(text: String): List<StickerSetSample> {
        val installed = client.searchInstalledStickerSets(
            stickerType = StickerTypeRegular(),
            query = text,
            limit = SET_SEARCH_LIMIT,
        )
            .toResult()
            .map { it.sets.toSamples() }
            .getOrElse { failure ->
                logger.w(failure) { "Hledání ve vlastních sadách podle názvu selhalo." }
                emptyList()
            }
        val public = client.searchStickerSets(stickerType = StickerTypeRegular(), query = text)
            .toResult()
            .map { it.sets.toSamples() }
            .getOrElse { failure ->
                logger.w(failure) { "Hledání veřejných sad podle názvu selhalo." }
                emptyList()
            }
        return (installed + public).distinctBy { it.id }
    }

    /**
     * Kategorie samolepek. Prémiová („samolepky Premium") se vynechává: nese jiný zdroj než
     * emoji a výběr prémiové samolepky stejně nenabízí.
     */
    override suspend fun categories(): List<StickerCategory> =
        client.getEmojiCategories(type = EmojiCategoryTypeRegularStickers())
            .toResult()
            .map { result ->
                result.categories.filterNotNull().mapNotNull { category ->
                    val source = category.source as? EmojiCategorySourceSearch ?: return@mapNotNull null
                    val emojis = source.emojis.filterNotNull().filter { it.isNotBlank() }
                    if (emojis.isEmpty()) return@mapNotNull null
                    StickerCategory(
                        name = category.name,
                        /*
                         * Bez náhledu: ten je u ikon kategorií prázdný průhledný obrázek
                         * (126 B, ověřeno na zařízení), kresba je jen v animaci. Výběr pak
                         * nehrající ikonu nakreslí prvním snímkem `.tgs`.
                         */
                        icon = category.icon.toMedia().copy(thumbnailFileId = null),
                        iconTinted = (category.icon.fullType as? StickerFullTypeCustomEmoji)?.needsRepainting == true,
                        emojis = emojis.joinToString(" "),
                    )
                }
            }
            .getOrElse { failure ->
                logger.w(failure) { "Kategorie samolepek se nepodařilo načíst." }
                emptyList()
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

    override suspend fun ownedSets(): Result<List<OwnedStickerSet>> =
        client.getOwnedStickerSets(offsetStickerSetId = 0, limit = OWNED_SETS_LIMIT).toResult()
            .onFailure { logger.w(it) { "Vlastní sady se nepodařilo načíst." } }
            .map { result ->
                result.sets.filterNotNull()
                    // Do vlastních sad emoji tvorba samolepek nepatří.
                    .filter { it.stickerType is StickerTypeRegular }
                    .map { set ->
                        OwnedStickerSet(
                            id = set.id,
                            name = set.name,
                            title = set.title,
                            size = set.size,
                            cover = set.cover(set.covers.filterNotNull().firstOrNull()),
                        )
                    }
            }

    override suspend fun createSet(title: String, sticker: NewStickerFile, name: String): Result<OwnedStickerSet> = runCatching {
        // Bez zvoleného jména ho navrhne Telegram (volné a platné).
        val name = name.ifBlank { client.getSuggestedStickerSetName(title = title).toResult().getOrThrow().text }
        val me = client.getMe().toResult().getOrThrow().id
        val set = client.createNewStickerSet(
            userId = me,
            title = title,
            name = name,
            stickerType = StickerTypeRegular(),
            needsRepainting = false,
            stickers = arrayOf(sticker.toNewSticker()),
            source = "",
        ).toResult().getOrThrow()
        // Nová sada se sama nenainstaluje (ověřeno: po restartu aplikace v seznamu sad chyběla),
        // a bez toho by ji autor neviděl ve výběru samolepek.
        client.changeStickerSet(setId = set.id, isInstalled = true, isArchived = false).toResult()
            .onFailure { logger.w(it) { "Novou sadu ${set.name} se nepodařilo nainstalovat." } }
        OwnedStickerSet(
            id = set.id,
            name = set.name,
            title = set.title,
            size = set.stickers.size,
            cover = set.stickers.filterNotNull().firstOrNull()?.toMedia(),
        )
    }.onFailure { logger.w(it) { "Sadu samolepek se nepodařilo založit." } }

    override suspend fun suggestSetName(title: String): Result<String> =
        client.getSuggestedStickerSetName(title = title).toResult().map { it.text }

    override suspend fun checkSetName(name: String): Result<StickerSetNameCheck> =
        client.checkStickerSetName(name = name).toResult().map { result ->
            when (result) {
                is CheckStickerSetNameResultOk -> StickerSetNameCheck.Ok
                is CheckStickerSetNameResultNameInvalid -> StickerSetNameCheck.Invalid
                is CheckStickerSetNameResultNameOccupied -> StickerSetNameCheck.Occupied
            }
        }

    override suspend fun setKeywords(stickerFileId: Int, keywords: List<String>): Result<Unit> =
        client.setStickerKeywords(sticker = InputFileId(id = stickerFileId), keywords = keywords.toTypedArray()).toResult()
            .onFailure { logger.w(it) { "Klíčová slova samolepky $stickerFileId se nepodařilo změnit." } }
            .map { }

    override suspend fun addToSet(set: OwnedStickerSet, sticker: NewStickerFile): Result<Unit> = runCatching {
        val me = client.getMe().toResult().getOrThrow().id
        client.addStickerToSet(userId = me, name = set.name, sticker = sticker.toNewSticker()).toResult().getOrThrow()
        // Vlastní sada může být odinstalovaná (nebo založená dřív, než se instalovalo samo);
        // kdo do ní přidává, chce ji mít ve výběru.
        client.changeStickerSet(setId = set.id, isInstalled = true, isArchived = false).toResult()
            .onFailure { logger.w(it) { "Sadu ${set.name} se nepodařilo nainstalovat." } }
        Unit
    }.onFailure { logger.w(it) { "Samolepku se nepodařilo přidat do sady ${set.name}." } }

    override suspend fun renameSet(name: String, title: String): Result<Unit> =
        client.setStickerSetTitle(name = name, title = title).toResult()
            .onFailure { logger.w(it) { "Sadu $name se nepodařilo přejmenovat." } }
            .map { }

    override suspend fun removeFromSet(stickerFileId: Int): Result<Unit> =
        client.removeStickerFromSet(sticker = InputFileId(id = stickerFileId)).toResult()
            .onFailure { logger.w(it) { "Samolepku $stickerFileId se nepodařilo odebrat ze sady." } }
            .map { }

    override suspend fun moveInSet(stickerFileId: Int, position: Int): Result<Unit> =
        client.setStickerPositionInSet(sticker = InputFileId(id = stickerFileId), position = position).toResult()
            .onFailure { logger.w(it) { "Samolepku $stickerFileId se nepodařilo přesunout." } }
            .map { }

    override suspend fun setEmojis(stickerFileId: Int, emojis: String): Result<Unit> =
        client.setStickerEmojis(sticker = InputFileId(id = stickerFileId), emojis = emojis).toResult()
            .onFailure { logger.w(it) { "Emoji samolepky $stickerFileId se nepodařilo změnit." } }
            .map { }

    /**
     * Po změně obálky TDLib sadu vyřadí z nainstalovaných — z Moje sady i z výběru samolepek
     * zmizela, nová i vrácená obálka stejně (ověřeno na emulátoru). Proto se znovu nainstaluje,
     * jako po založení sady a přidání samolepky.
     */
    override suspend fun setSetCover(setId: Long, name: String, path: String?): Result<Unit> = runCatching {
        val me = client.getMe().toResult().getOrThrow().id
        client.setStickerSetThumbnail(
            userId = me,
            name = name,
            thumbnail = path?.let { InputFileLocal(path = it) },
            format = path?.let { StickerFormatWebp() },
        ).toResult().getOrThrow()
        client.changeStickerSet(setId = setId, isInstalled = true, isArchived = false).toResult()
            .onFailure { logger.w(it) { "Sadu $name se po změně obálky nepodařilo nainstalovat." } }
        Unit
    }.onFailure { logger.w(it) { "Obálku sady $name se nepodařilo změnit." } }

    /**
     * Napřed odinstalovat, pak smazat: `deleteStickerSet` sadu ze seznamu nainstalovaných
     * neodebere (ověřeno na emulátoru — smazaná sada v něm visela dál, i po návratu na obrazovku).
     * Když smazání selže, sada se nainstaluje zpátky.
     */
    override suspend fun deleteSet(setId: Long, name: String): Result<Unit> {
        client.changeStickerSet(setId = setId, isInstalled = false, isArchived = false).toResult()
            .onFailure { logger.w(it) { "Sadu $name se před smazáním nepodařilo odinstalovat." } }
        return client.deleteStickerSet(name = name).toResult()
            .onFailure {
                logger.w(it) { "Sadu $name se nepodařilo smazat." }
                client.changeStickerSet(setId = setId, isInstalled = true, isArchived = false)
            }
            .map { }
    }

    private fun NewStickerFile.toNewSticker() = NewSticker(
        sticker = InputFileLocal(path = path),
        format = StickerFormatWebp(),
        emojis = emojis,
        maskPosition = null,
        keywords = keywords.toTypedArray(),
    )

    private fun Sticker.toCustomEmojiItem(): CustomEmojiItem? =
        (fullType as? StickerFullTypeCustomEmoji)?.let { CustomEmojiItem(id = it.customEmojiId, emoji = emoji) }

    /**
     * Obálka sady: vlastní (`thumbnail`), a když ji autor nenastavil, [first] samolepka. Jen
     * u samolepek — sady emoji mají obálku jako vlastní emoji (`coverCustomEmojiId`).
     */
    private fun StickerSetInfo.cover(first: Sticker?): MessageMedia.Sticker? =
        thumbnail?.takeIf { stickerType is StickerTypeRegular }?.toSetCover(id) ?: first?.toMedia()

    private fun Array<StickerSetInfo>.toSummaries(): List<StickerSetSummary> =
        filterNotNull().map { set ->
            StickerSetSummary(
                id = set.id,
                title = set.title,
                cover = set.cover(set.covers.filterNotNull().firstOrNull()),
                size = set.size,
                isOfficial = set.isOfficial,
                isOwned = set.isOwned,
                coverCustomEmojiId = (set.covers.filterNotNull().firstOrNull()?.fullType as? StickerFullTypeCustomEmoji)?.customEmojiId,
            )
        }

    private fun Array<StickerSetInfo>.toSamples(): List<StickerSetSample> =
        filterNotNull().map { set ->
            StickerSetSample(
                id = set.id,
                title = set.title,
                isInstalled = set.isInstalled && !set.isArchived,
                covers = set.covers.filterNotNull().map { it.toMedia() },
            )
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
                    customEmojiId = (sticker.fullType as? StickerFullTypeCustomEmoji)?.customEmojiId,
                )
            }
    }
}

private fun StickerSetKind.toTdl(): StickerType = when (this) {
    StickerSetKind.Stickers -> StickerTypeRegular()
    StickerSetKind.Emoji -> StickerTypeCustomEmoji()
}

/** Druh sady z TDLib; masky se nespravují (`null`). */
private fun StickerType.kind(): StickerSetKind? = when (this) {
    is StickerTypeRegular -> StickerSetKind.Stickers
    is StickerTypeCustomEmoji -> StickerSetKind.Emoji
    else -> null
}

/** Kolik vlastních sad načíst; víc TDLib najednou nedá a víc jich běžný uživatel nemá. */
private const val OWNED_SETS_LIMIT = 100
