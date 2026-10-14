package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * StickerRepository pro testy: každé volání, které test nepřepsal, vyhodí chybu se jménem metody — test tak
 * hned řekne, na co ještě sahá. Vygenerované z rozhraní; po změně rozhraní stačí doplnit chybějící
 * člen (překladač ho vyjmenuje).
 */
open class UnimplementedStickerRepository : StickerRepository {
    override fun installedSets(kind: StickerSetKind) : Flow<List<StickerSetSummary>> = error("installedSets")
    override fun recentStickers() : Flow<List<StickerItem>> = error("recentStickers")
    override fun favoriteStickers() : Flow<List<StickerItem>> = error("favoriteStickers")
    override suspend fun removeRecent(stickerFileId: Int) : Result<Unit> = error("removeRecent")
    override suspend fun clearRecent() : Result<Unit> = error("clearRecent")
    override suspend fun setFavorite(stickerFileId: Int, favorite: Boolean) : Result<Unit> = error("setFavorite")
    override fun stickerSetDetail(setId: Long) : Flow<StickerSetDetail?> = error("stickerSetDetail")
    override fun trendingSets() : Flow<List<StickerSetSample>> = error("trendingSets")
    override suspend fun markTrendingViewed(setIds: List<Long>): Unit = error("markTrendingViewed")
    override suspend fun setInstalled(setId: Long, installed: Boolean) : Result<Unit> = error("setInstalled")
    override suspend fun setArchived(setId: Long, archived: Boolean) : Result<Unit> = error("setArchived")
    override suspend fun reorderSets(setIds: List<Long>, kind: StickerSetKind) : Result<Unit> = error("reorderSets")
    override suspend fun archivedSets(afterSetId: Long, kind: StickerSetKind) : Result<ArchivedStickerSets> = error("archivedSets")
    override suspend fun groupStickerSet(chatId: Long) : GroupStickerSet? = error("groupStickerSet")
    override suspend fun canUseCustomEmoji(chatId: Long) : Boolean = error("canUseCustomEmoji")
    override fun customEmojiSets() : Flow<List<CustomEmojiSet>> = error("customEmojiSets")
    override suspend fun customEmojiSet(setId: Long) : Result<List<CustomEmojiItem>> = error("customEmojiSet")
    override fun trendingCustomEmojiSets() : Flow<List<CustomEmojiSetSample>> = error("trendingCustomEmojiSets")
    override suspend fun stickerSet(setId: Long) : Result<List<StickerItem>> = error("stickerSet")
    override suspend fun search(query: StickerQuery) : StickerSearchResult = error("search")
    override suspend fun categories() : List<StickerCategory> = error("categories")
    override suspend fun stickersForEmoji(emoji: String) : List<StickerItem> = error("stickersForEmoji")
}
