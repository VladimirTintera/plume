package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.MessageStory
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.PhotoSize
import dev.g000sha256.tdl.dto.RemoteFile
import dev.g000sha256.tdl.dto.Story
import dev.g000sha256.tdl.dto.StoryContent
import dev.g000sha256.tdl.dto.StoryContentLive
import dev.g000sha256.tdl.dto.StoryContentPhoto
import dev.g000sha256.tdl.dto.StoryContentVideo
import dev.g000sha256.tdl.dto.StoryPrivacySettingsEveryone
import dev.g000sha256.tdl.dto.StoryVideo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryTest {

    private val names = mapOf(-100L to "Kanál")
    private val senderName: suspend (Long) -> String? = { names[it] }

    private fun file(id: Int) = File(
        id = id,
        size = 0,
        expectedSize = 0,
        local = LocalFile("", false, false, false, false, 0, 0, 0),
        remote = RemoteFile("", "", false, false, 0),
    )

    private fun story(content: StoryContent, caption: String = "") = Story(
        id = 5,
        posterChatId = -100,
        posterId = null,
        date = 0,
        isBeingPosted = false,
        isBeingEdited = false,
        isEdited = false,
        isPostedToChatPage = false,
        isVisibleOnlyForSelf = false,
        canBeAddedToAlbum = false,
        canBeDeleted = false,
        canBeEdited = false,
        canBeForwarded = false,
        canBeReplied = false,
        canSetPrivacySettings = false,
        canToggleIsPostedToChatPage = false,
        canGetStatistics = false,
        canGetInteractions = false,
        hasExpiredViewers = false,
        repostInfo = null,
        interactionInfo = null,
        chosenReactionType = null,
        privacySettings = StoryPrivacySettingsEveryone(longArrayOf()),
        content = content,
        areas = emptyArray(),
        caption = FormattedText(caption, emptyArray()),
        albumIds = intArrayOf(),
    )

    private val message = MessageStory(storyPosterChatId = -100, storyId = 5, viaMention = true)

    @Test
    fun pribehSFotkouNeseFotkuAutoraAPopisek() = runTest {
        val photo = Photo(
            hasStickers = false,
            minithumbnail = null,
            sizes = arrayOf(PhotoSize("x", file(31), 720, 1280, intArrayOf())),
        )
        val media = message.toMedia(story(StoryContentPhoto(photo), caption = "Výhled"), senderName)

        assertEquals(31, assertIs<MessageMedia.Photo>(media.content).fileId)
        assertEquals("Kanál", media.posterName)
        assertEquals("Výhled", media.caption)
        assertTrue(media.isAvailable)
        assertTrue(media.viaMention)
        assertFalse(media.isLive)
    }

    @Test
    fun videoPribehuZaokrouhliDelkuNahoru() = runTest {
        val video = StoryVideo(
            duration = 4.2,
            width = 720,
            height = 1280,
            hasStickers = false,
            isAnimation = false,
            minithumbnail = null,
            thumbnail = null,
            preloadPrefixSize = 0,
            coverFrameTimestamp = 0.0,
            video = file(41),
        )
        val media = message.toMedia(story(StoryContentVideo(video, alternativeVideo = null)), senderName)

        val content = assertIs<MessageMedia.Video>(media.content)
        assertEquals(41, content.fileId)
        assertEquals(5, content.durationSeconds)
    }

    @Test
    fun zivyAZmizelyPribehObsahNemaji() = runTest {
        val live = message.toMedia(story(StoryContentLive(groupCallId = 1, isRtmpStream = false)), senderName)
        assertNull(live.content)
        assertTrue(live.isLive)
        assertTrue(live.isAvailable)

        // Příběh, který už není: karta řekne „není k dispozici", jméno autora zůstane.
        val gone = message.toMedia(story = null, senderName = senderName)
        assertNull(gone.content)
        assertFalse(gone.isAvailable)
        assertFalse(gone.isLive)
        assertEquals("Kanál", gone.posterName)
    }
}
