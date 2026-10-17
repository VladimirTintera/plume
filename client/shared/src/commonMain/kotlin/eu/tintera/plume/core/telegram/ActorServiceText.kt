package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageChatBoost
import dev.g000sha256.tdl.dto.MessageChatChangePhoto
import dev.g000sha256.tdl.dto.MessageChatChangeTitle
import dev.g000sha256.tdl.dto.MessageChatDeletePhoto
import dev.g000sha256.tdl.dto.MessageChatJoinFromCommunity
import dev.g000sha256.tdl.dto.MessageContactRegistered
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageForumTopicCreated
import dev.g000sha256.tdl.dto.MessageForumTopicEdited
import dev.g000sha256.tdl.dto.MessageForumTopicIsClosedToggled
import dev.g000sha256.tdl.dto.MessageForumTopicIsHiddenToggled
import dev.g000sha256.tdl.dto.MessageGameScore
import dev.g000sha256.tdl.dto.MessageScreenshotTaken
import dev.g000sha256.tdl.dto.MessageSenderUser
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.service_actor_boost
import eu.tintera.plume.resources.service_actor_boost_count
import eu.tintera.plume.resources.service_actor_community_joined
import eu.tintera.plume.resources.service_actor_contact_joined
import eu.tintera.plume.resources.service_actor_game_score
import eu.tintera.plume.resources.service_actor_photo_changed
import eu.tintera.plume.resources.service_actor_photo_removed
import eu.tintera.plume.resources.service_actor_screenshot
import eu.tintera.plume.resources.service_actor_title_changed
import eu.tintera.plume.resources.service_actor_topic_closed
import eu.tintera.plume.resources.service_actor_topic_created
import eu.tintera.plume.resources.service_actor_topic_edited
import eu.tintera.plume.resources.service_actor_topic_hidden
import eu.tintera.plume.resources.service_actor_topic_icon_changed
import eu.tintera.plume.resources.service_actor_topic_icon_removed
import eu.tintera.plume.resources.service_actor_topic_renamed
import eu.tintera.plume.resources.service_actor_topic_reopened
import eu.tintera.plume.resources.service_actor_topic_unhidden
import eu.tintera.plume.resources.service_you_boost
import eu.tintera.plume.resources.service_you_boost_count
import eu.tintera.plume.resources.service_you_community_joined
import eu.tintera.plume.resources.service_you_game_score
import eu.tintera.plume.resources.service_you_photo_changed
import eu.tintera.plume.resources.service_you_photo_removed
import eu.tintera.plume.resources.service_you_screenshot
import eu.tintera.plume.resources.service_you_title_changed
import eu.tintera.plume.resources.service_you_topic_closed
import eu.tintera.plume.resources.service_you_topic_created
import eu.tintera.plume.resources.service_you_topic_edited
import eu.tintera.plume.resources.service_you_topic_hidden
import eu.tintera.plume.resources.service_you_topic_icon_changed
import eu.tintera.plume.resources.service_you_topic_icon_removed
import eu.tintera.plume.resources.service_you_topic_renamed
import eu.tintera.plume.resources.service_you_topic_reopened
import eu.tintera.plume.resources.service_you_topic_unhidden
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * Servisní věta se jménem toho, kdo akci udělal („Jana změnil(a) název na „Výlet"",
 * „Pořídil(a) jsi snímek obrazovky"). Aktér je odesílatel oznámení; ostatní lidi tyhle zprávy
 * nenesou, takže se dohledává jediné jméno. Bezejmenná věta z [serviceText] zůstává jako
 * náhrada, když jméno neznáme, a v kanálu, kde oznámení posílá kanál sám.
 *
 * `null` = tenhle druh zprávy jméno nemá (zprávy o členech, úkoly a připnutí mají vlastní
 * funkce, ostatní oznámení aktéra nepotřebují).
 */
internal fun MessageContent.actorServiceText(actor: ServicePerson): UiText? = when (this) {
    is MessageChatChangeTitle -> actor.say(Res.string.service_you_title_changed, Res.string.service_actor_title_changed, title)
    is MessageChatChangePhoto -> actor.say(Res.string.service_you_photo_changed, Res.string.service_actor_photo_changed)
    is MessageChatDeletePhoto -> actor.say(Res.string.service_you_photo_removed, Res.string.service_actor_photo_removed)
    is MessageScreenshotTaken -> actor.say(Res.string.service_you_screenshot, Res.string.service_actor_screenshot)
    is MessageForumTopicCreated -> actor.say(Res.string.service_you_topic_created, Res.string.service_actor_topic_created, name)
    is MessageForumTopicEdited -> when {
        name.isNotBlank() -> actor.say(Res.string.service_you_topic_renamed, Res.string.service_actor_topic_renamed, name)
        editIconCustomEmojiId && iconCustomEmojiId == 0L ->
            actor.say(Res.string.service_you_topic_icon_removed, Res.string.service_actor_topic_icon_removed)
        editIconCustomEmojiId -> actor.say(Res.string.service_you_topic_icon_changed, Res.string.service_actor_topic_icon_changed)
        else -> actor.say(Res.string.service_you_topic_edited, Res.string.service_actor_topic_edited)
    }
    is MessageForumTopicIsClosedToggled ->
        if (isClosed) actor.say(Res.string.service_you_topic_closed, Res.string.service_actor_topic_closed)
        else actor.say(Res.string.service_you_topic_reopened, Res.string.service_actor_topic_reopened)
    is MessageForumTopicIsHiddenToggled ->
        if (isHidden) actor.say(Res.string.service_you_topic_hidden, Res.string.service_actor_topic_hidden)
        else actor.say(Res.string.service_you_topic_unhidden, Res.string.service_actor_topic_unhidden)
    is MessageChatBoost ->
        if (boostCount <= 1) actor.say(Res.string.service_you_boost, Res.string.service_actor_boost)
        else actor.counted(Res.plurals.service_you_boost_count, Res.plurals.service_actor_boost_count, boostCount)
    is MessageGameScore -> actor.say(Res.string.service_you_game_score, Res.string.service_actor_game_score, score.grouped())
    // „Ty ses připojil(a) k Telegramu" nemá kdy přijít — oznámení dostávají kontakty.
    is MessageContactRegistered -> if (actor.isMe) null else UiText(Res.string.service_actor_contact_joined, actor.name)
    is MessageChatJoinFromCommunity -> actor.say(Res.string.service_you_community_joined, Res.string.service_actor_community_joined)
    else -> null
}

/** Oznámení, u kterého stojí za to dohledávat jméno aktéra (viz [actorServiceText]). */
internal val MessageContent.hasActorServiceText: Boolean
    get() = this is MessageChatChangeTitle || this is MessageChatChangePhoto || this is MessageChatDeletePhoto ||
        this is MessageScreenshotTaken || this is MessageForumTopicCreated || this is MessageForumTopicEdited ||
        this is MessageForumTopicIsClosedToggled || this is MessageForumTopicIsHiddenToggled ||
        this is MessageChatBoost || this is MessageGameScore || this is MessageContactRegistered ||
        this is MessageChatJoinFromCommunity

/**
 * [actorServiceText] i s dohledáním jména. Aktér musí být uživatel: oznámení od chatu
 * (kanál, anonymní správce) by neslo název téhož chatu, a to nic neříká.
 */
internal suspend fun Message.namedActorServiceText(
    myUserId: suspend () -> Long?,
    nameOf: suspend (Long) -> String?,
): UiText? {
    if (!content.hasActorServiceText) return null
    val actorId = (senderId as? MessageSenderUser)?.userId ?: return null
    val actor = if (isOutgoing || actorId == myUserId()) {
        ServicePerson(name = "", isMe = true)
    } else {
        ServicePerson(nameOf(actorId) ?: return null)
    }
    return content.actorServiceText(actor)
}

private fun ServicePerson.say(mine: StringResource, theirs: StringResource, vararg args: Any): UiText =
    if (isMe) UiText(mine, *args) else UiText(theirs, name, *args)

private fun ServicePerson.counted(mine: PluralStringResource, theirs: PluralStringResource, count: Int): UiText =
    if (isMe) UiText(mine, count, count) else UiText(theirs, count, name, count)
