package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.messageTextStyle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_pending_stop
import eu.tintera.plume.resources.conversation_pending_thinking
import eu.tintera.plume.resources.stop_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Rozepsaná odpověď bota (`updatePendingMessage`): bublina příchozí zprávy s textem, který bot
 * právě generuje, a pod ní tlačítko Zastavit, když ho bot dovolí.
 *
 * **Není to zpráva chatu** — nemá id, nedá se na ni odpovědět, reagovat ani ji podržet. Proto
 * vlastní blok a ne [MessageFrame]: rám nese nabídku, tah na odpověď, reakce a doručení, a ty by
 * mířily na zprávu, kterou server nezná. Kreslí se ale STEJNOU bublinou (barva, zaoblení, písmo),
 * aby dopsaná zpráva, která ji nahradí, nevypadala jako něco jiného.
 *
 * - **Klíč nese `draftId`.** Update téže odpovědi mění obsah na místě a bublina dorůstá plynule
 *   (`animateContentSize`, tak TDLib chce „nahradit s animací"); jiná odpověď je jiná položka
 *   a prolne se.
 * - Stojí tam, kam dopsaná zpráva naskočí, a místo „píše…" — obojí říká totéž.
 * - **Prázdný text je „Přemýšlí…"**: Bot API tak dovoluje botovi ukázat, že na odpovědi pracuje
 *   (`sendMessageDraft` s prázdným textem), i s tlačítkem Zastavit.
 */
@Immutable
internal data class PendingReplyItem(
    val draftId: Long,
    val text: String,
    val entities: List<MessageEntity>,
    val canStop: Boolean,
) : ConversationItem {
    override val key: Any get() = PENDING_REPLY_KEY_PREFIX + draftId

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        val colors = MaterialTheme.colorScheme
        val bubble = bubbleColorFor(colors, isOutgoing = false, emphasized = false)
        val onBubble = colors.onSurface
        Column(
            Modifier
                .animateItem()
                .gathered(key, scope)
                // Při odkrývání časů stojí jako příchozí zprávy (viz RevealAnchor): bublina má
                // nejvýš MESSAGE_MAX_WIDTH_FRACTION šířky, takže se pruh časů vejde vedle ní.
                .fillMaxWidth()
                // Je to nový blok (bot odpovídá), takže vzduch jako mezi skupinami.
                .padding(top = GROUP_SPACING),
        ) {
            Box(Modifier.fillMaxWidth(MESSAGE_MAX_WIDTH_FRACTION)) {
                Surface(
                    shape = RoundedCornerShape(BUBBLE_CORNER),
                    color = bubble,
                    contentColor = onBubble,
                    modifier = Modifier
                        .widthIn(max = ContentWidth.Bubble)
                        // Dorůstá i z „Přemýšlí…" na první slova.
                        .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                ) {
                    // Prázdný text = bot přemýšlí (Bot API to tak definuje); ztlumeně, ať se
                    // nečte jako jeho slova.
                    if (text.isBlank()) {
                        Text(
                            text = stringResource(Res.string.conversation_pending_thinking),
                            style = messageTextStyle(),
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = MESSAGE_PADDING_H, vertical = MESSAGE_PADDING_V),
                        )
                    } else RichText(
                        text = text,
                        entities = entities,
                        style = messageTextStyle(),
                        linkColor = accentOn(background = bubble, accent = colors.primary, fallback = onBubble),
                        onLinkClick = { scope.onAction(NO_MESSAGE, MessageAction.OpenLink(it)) },
                        onEntityClick = { type ->
                            when (type) {
                                is MessageEntityType.Mention -> scope.onAction(NO_MESSAGE, MessageAction.OpenMention(type.username))
                                is MessageEntityType.MentionUser -> scope.onAction(NO_MESSAGE, MessageAction.OpenUser(type.userId))
                                else -> Unit
                            }
                        },
                        quoteBackground = onBubble.copy(alpha = QUOTE_TINT_ALPHA),
                        quoteBleed = MESSAGE_PADDING_H,
                        quotePadding = MESSAGE_PADDING_V,
                        modifier = Modifier.padding(horizontal = MESSAGE_PADDING_H, vertical = MESSAGE_PADDING_V),
                    )
                }
            }

            // Po zastavení s `keepOnStop` text zůstává, tlačítko odejde.
            AnimatedVisibility(
                visible = canStop,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    // K nule bez překmitu — effects spec, ne spatial.
                    shrinkVertically(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                FilledTonalButton(
                    onClick = scope::stopPendingReply,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Icon(
                        painterResource(Res.drawable.stop_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(
                        stringResource(Res.string.conversation_pending_stop),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    )
                }
            }
        }
    }
}

/** Předpona klíče bloku; za ní `draftId`. */
internal const val PENDING_REPLY_KEY_PREFIX = "pending-reply-"

/** Rozepsaná odpověď nemá id zprávy; odkazy a zmínky ho nepotřebují. */
private const val NO_MESSAGE = 0L
