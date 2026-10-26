package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.PhotoSize
import dev.g000sha256.tdl.toResult

/**
 * Co k náhledu poslední zprávy patří kromě textu (viz pole [ChatSummary] stejného jména).
 * Připravuje se PŘED zápisem do seznamu: jméno se dohledává dotazem a zápis běží pod zámkem.
 */
internal class LastMessageExtras(
    val sender: String?,
    val showsSender: Boolean,
    val sendState: SendState?,
    val thumbnail: ChatPreviewThumbnail?,
) {
    companion object {
        /** Prázdný chat. */
        val None = LastMessageExtras(sender = null, showsSender = true, sendState = null, thumbnail = null)
    }
}

internal fun ChatSummary.withExtras(extras: LastMessageExtras): ChatSummary = copy(
    lastMessageSender = extras.sender,
    lastMessageShowsSender = extras.showsSender,
    lastMessageSendState = extras.sendState,
    lastMessageThumbnail = extras.thumbnail,
)

/**
 * Obrázek k náhledu poslední zprávy v seznamu chatů, nebo `null`, když není co ukázat.
 *
 * Jen fotka, video, GIF a kolečko — u souboru, samolepky nebo polohy by čtvereček v řádku nic
 * nevysvětlil a slovo v náhledu stačí.
 *
 * **Mizející, tajné a omezené médium náhled nemá**, ani rozmazaný: seznam je vidět bez otevření
 * chatu a náhled by z média zbyl i po vypršení. Skrytému ([hidden] — citlivý obsah — nebo
 * spoiler) zbyde rozmazání; stejné pravidlo jako u citace v konverzaci (`quoteThumbnail`).
 */
internal fun Message.listThumbnail(hidden: Boolean): ChatPreviewThumbnail? =
    if (selfDestructType != null || restrictionText != null) null else content.listThumbnail(hidden)

/** Obsahová část [Message.listThumbnail]; zvlášť kvůli testům, zpráva od TDLib má desítky polí. */
internal fun MessageContent.listThumbnail(hidden: Boolean): ChatPreviewThumbnail? {
    val thumbnail = when (val content = this) {
        is MessagePhoto -> if (content.isSecret) null else ChatPreviewThumbnail(
            fileId = content.photo.sizes.filterNotNull().listThumbnailSize()?.photo?.id,
            placeholder = content.photo.minithumbnail?.toDomain(),
            hidden = hidden || content.hasSpoiler,
        )
        is MessageVideo -> if (content.isSecret) null else ChatPreviewThumbnail(
            fileId = content.video.thumbnail.picture()?.file?.id,
            placeholder = content.video.minithumbnail?.toDomain(),
            hidden = hidden || content.hasSpoiler,
        )
        is MessageAnimation -> if (content.isSecret) null else ChatPreviewThumbnail(
            fileId = content.animation.thumbnail.picture()?.file?.id,
            placeholder = content.animation.minithumbnail?.toDomain(),
            hidden = hidden || content.hasSpoiler,
        )
        is MessageVideoNote -> if (content.isSecret) null else ChatPreviewThumbnail(
            fileId = content.videoNote.thumbnail.picture()?.file?.id,
            placeholder = content.videoNote.minithumbnail?.toDomain(),
            hidden = hidden,
            round = true,
        )
        else -> null
    } ?: return null
    // Skrytý bez rozmazaného náhledu, nebo ani soubor, ani náhled: prázdný čtvereček nic neřekne.
    return thumbnail.takeIf { it.placeholder != null || (!it.hidden && it.fileId != null) }
}

/**
 * Nejmenší varianta fotky, jejíž KRATŠÍ strana ještě zaplní čtvereček v řádku. Oříznutí na
 * čtverec bere kratší stranu — varianta `s` (100 px po delší straně) má u fotky 16 : 9 kratší
 * stranu kolem 56 px a byla by rozmazaná. Bez takové varianty ta největší.
 */
internal fun List<PhotoSize>.listThumbnailSize(): PhotoSize? =
    filter { minOf(it.width, it.height) >= LIST_THUMBNAIL_MIN_PX }.minByOrNull { it.width * it.height }
        ?: maxByOrNull { it.width * it.height }

/** Kratší strana miniatury v pixelech: čtvereček kolem 20 dp při hustotě 3–4×. */
private const val LIST_THUMBNAIL_MIN_PX = 64

/**
 * Jméno odesílatele do jednořádkového náhledu („Jana: …"), jen křestní — řádek je krátký a jméno
 * je tam jen proto, aby bylo poznat, kdo naposledy psal. Anonymní správce (odesílatel je skupina
 * sama) jméno nemá. Seznam chatů i seznam témat, ať se oba řádky nerozejdou.
 */
internal suspend fun TdlClient.previewSenderName(sender: MessageSender, chatId: Long): String? = when (sender) {
    is MessageSenderUser -> getUser(userId = sender.userId).toResult().getOrNull()
        ?.let { user -> user.firstName.ifBlank { user.lastName } }
        ?.takeIf { it.isNotBlank() }
    is MessageSenderChat -> sender.chatId.takeIf { it != chatId }
        ?.let { getChat(chatId = it).toResult().getOrNull()?.title }
}
