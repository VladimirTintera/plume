package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageStory
import dev.g000sha256.tdl.dto.Story
import dev.g000sha256.tdl.dto.StoryContentLive
import dev.g000sha256.tdl.dto.StoryContentPhoto
import dev.g000sha256.tdl.dto.StoryContentUnsupported
import dev.g000sha256.tdl.dto.StoryContentVideo
import dev.g000sha256.tdl.dto.StoryPrivacySettings
import dev.g000sha256.tdl.dto.StoryPrivacySettingsCloseFriends
import dev.g000sha256.tdl.dto.StoryPrivacySettingsContacts
import dev.g000sha256.tdl.dto.StoryPrivacySettingsEveryone
import dev.g000sha256.tdl.dto.StoryPrivacySettingsSelectedUsers
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
        live = (content as? StoryContentLive)?.let { LiveStoryCall(it.groupCallId, it.isRtmpStream) },
        canBeEdited = story?.canBeEdited == true,
        audience = story?.takeIf { it.canSetPrivacySettings }?.privacySettings?.toAudience(),
        canToggleOnProfile = story?.canToggleIsPostedToChatPage == true,
        isOnProfile = story?.isPostedToChatPage == true,
        canBeForwarded = story?.canBeForwarded == true,
    )
}

/**
 * Viditelnost příběhu v doméně. Výjimky („všichni kromě…") Plume nenastavuje ani neukazuje;
 * změna viditelnosti z Plume je zahodí, jako by je zahodila volba v oficiální aplikaci.
 */
internal fun StoryPrivacySettings.toAudience(): StoryAudience = when (this) {
    is StoryPrivacySettingsEveryone -> StoryAudience.Everyone
    is StoryPrivacySettingsContacts -> StoryAudience.Contacts
    is StoryPrivacySettingsCloseFriends -> StoryAudience.CloseFriends
    is StoryPrivacySettingsSelectedUsers -> StoryAudience.Selected(userIds.toList())
}

internal fun StoryAudience.toPrivacySettings(): StoryPrivacySettings = when (this) {
    StoryAudience.Everyone -> StoryPrivacySettingsEveryone(exceptUserIds = longArrayOf())
    StoryAudience.Contacts -> StoryPrivacySettingsContacts(exceptUserIds = longArrayOf())
    StoryAudience.CloseFriends -> StoryPrivacySettingsCloseFriends()
    is StoryAudience.Selected -> StoryPrivacySettingsSelectedUsers(userIds = userIds.toLongArray())
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
