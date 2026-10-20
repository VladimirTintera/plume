package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageStory
import dev.g000sha256.tdl.dto.Story
import dev.g000sha256.tdl.dto.StoryContentLive
import dev.g000sha256.tdl.dto.StoryContentPhoto
import dev.g000sha256.tdl.dto.StoryContentUnsupported
import dev.g000sha256.tdl.dto.StoryContentVideo
import dev.g000sha256.tdl.dto.StoryVideo

/**
 * Sdílený příběh jako příloha zprávy. Zpráva nese jen odkaz (kdo příběh zveřejnil a jeho id);
 * obsah dočte repository přes `getStory` a předá ho sem jako [story]. Příběh, který už není
 * (vypršel, autor ho smazal, je jen pro vybrané), je `null` — karta pak řekne, že není
 * k dispozici, místo prázdného místa.
 */
internal suspend fun MessageStory.toMedia(
    story: Story?,
    senderName: suspend (Long) -> String?,
): MessageMedia.Story {
    val content = story?.content
    return MessageMedia.Story(
        posterChatId = storyPosterChatId,
        storyId = storyId,
        posterName = storyPosterChatId.takeIf { it != 0L }?.let { senderName(it) }?.takeIf { it.isNotBlank() },
        content = when (content) {
            is StoryContentPhoto -> content.photo.toMedia()
            is StoryContentVideo -> content.video.toMedia()
            is StoryContentLive, is StoryContentUnsupported, null -> null
        },
        caption = story?.caption?.text.orEmpty(),
        isLive = content is StoryContentLive,
        isAvailable = story != null,
        viaMention = viaMention,
        postedAt = story?.date ?: 0,
        canBeReplied = story?.canBeReplied == true,
        chosenReaction = story?.chosenReactionType?.toReactionKey(),
        // TDLib „vlastní" přímo neříká; smazat jde jen příběh, který jsme zveřejnili my
        // (nebo kanál, který spravujeme), a na ten se nereaguje.
        isOwn = story?.canBeDeleted == true,
        viewCount = story?.interactionInfo?.viewCount,
        viewersExpired = story?.hasExpiredViewers == true,
        albumIds = story?.albumIds?.toList().orEmpty(),
        canBeAddedToAlbum = story?.canBeAddedToAlbum == true,
    )
}

/**
 * Video příběhu jako video zprávy. Délka chodí v desetinách sekundy, zaokrouhlí se nahoru jako
 * u videa ve zprávě (TDLib tam dělá totéž). Streamovat se nezkouší: příběhy jsou krátké a kde
 * má soubor index, nikde napsané není (viz „Video" v docs/poznatky/prohlizec-medii.md, `supportsStreaming`).
 */
internal fun StoryVideo.toMedia() = MessageMedia.Video(
    fileId = video.id,
    thumbnailFileId = thumbnail.picture()?.file?.id,
    width = width,
    height = height,
    durationSeconds = kotlin.math.ceil(duration).toInt(),
    minithumbnail = minithumbnail?.toDomain(),
)
