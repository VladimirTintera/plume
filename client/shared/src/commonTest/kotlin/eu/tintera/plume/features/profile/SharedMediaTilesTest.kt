package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.Article
import eu.tintera.plume.core.telegram.ArticleMedia
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.NO_ARTICLE_MEDIA
import kotlin.test.Test
import kotlin.test.assertEquals

/** Dlaždice záložky Média — viz [sharedMediaTiles]. */
class SharedMediaTilesTest {

    private fun photo(id: Int) = MessageMedia.Photo(fileId = id, thumbnailFileId = null, fullFileId = id, width = 4, height = 3)

    private val gif = MessageMedia.Animation(fileId = 9, thumbnailFileId = null, width = 1, height = 1, sizeBytes = 1)

    private fun message(id: Long, media: MessageMedia? = null, article: Article? = null) = ChatMessage(
        id = id,
        isOutgoing = false,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 7L,
        text = "",
        entities = emptyList(),
        media = media,
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = null,
        isService = false,
        timestamp = 1,
        isEdited = false,
        article = article,
    )

    private fun anchor(index: Int, media: MessageMedia) = ArticleMedia(0, 0, index, media, caption = "")

    @Test
    fun clanekDaDlazdiciZaKazdouFotkuAVideo() {
        val tiles = sharedMediaTiles(
            listOf(
                message(1, media = photo(1)),
                message(2, article = Article(listOf(anchor(0, photo(2)), anchor(1, gif), anchor(2, photo(3))))),
            ),
        )
        assertEquals(listOf("1-$NO_ARTICLE_MEDIA", "2-0", "2-2"), tiles.map { it.key })
        assertEquals(listOf(1, 2, 3), tiles.map { (it.media as MessageMedia.Photo).fileId })
    }

    @Test
    fun zpravaBezMediaDlazdiciNema() {
        assertEquals(emptyList(), sharedMediaTiles(listOf(message(1))))
    }
}
