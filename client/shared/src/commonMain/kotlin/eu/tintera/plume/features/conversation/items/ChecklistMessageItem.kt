package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.animateContentSize
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatChecklist
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChecklistItem
import eu.tintera.plume.core.telegram.shownDone
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.check_box_filled_24dp
import eu.tintera.plume.resources.check_box_outline_blank_24dp
import eu.tintera.plume.resources.checklist_add_task
import eu.tintera.plume.resources.checklist_kind
import eu.tintera.plume.resources.checklist_kind_group
import eu.tintera.plume.resources.checklist_progress
import eu.tintera.plume.resources.checklist_task_done_by
import eu.tintera.plume.resources.poll_subtitle_joined
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Seznam úkolů v bublině: název, kolik je hotovo, úkoly se zaškrtávátky a pod nimi „Přidat úkol".
 *
 * Popis zprávy („Seznam úkolů: název") se tu NEKRESLÍ — je pro místa, kde se seznam kreslit nedá
 * (citace, připnutá zpráva, náhled v seznamu chatů), jako u ankety.
 */
@Immutable
internal data class ChecklistMessageItem(
    override val frame: MessageFrameData,
    val checklist: ChatChecklist,
    val quoted: ChatMessage?,
    /** Koho citace uvede v soukromém chatu, kde TDLib jméno odesílatele nevyplňuje. */
    val partnerName: String?,
) : MessageItem {

    @Composable
    override fun MessageBodyScope.Body() {
        val quote = quoted ?: return Bubble { ChecklistContent(checklist) }
        QuotedBubble(
            quote = { ReplyQuote(quote, partnerName) },
            content = { ChecklistContent(checklist) },
        )
    }
}

/**
 * Obsah seznamu. **Šířka je pevná, ne podle obsahu**, jako u ankety: seznam krátkých úkolů by
 * jinak byl úzký sloupec, a po odškrtnutí, které přidá jméno pod úkol, by bublina poskočila.
 *
 * Odškrtnout jde, když to server dovolí ([ChatChecklist.canMarkTasksAsDone]); jestli má účet
 * Premium, rozhoduje ViewModel — bez něj ťuknutí vysvětlí proč, místo aby tiše nic neudělalo.
 * Ťuknutí se ukáže hned ([pendingChecklistMarks][ConversationItemScope.pendingChecklistMarks]),
 * na server se nečeká.
 */
@Composable
private fun MessageBodyScope.ChecklistContent(checklist: ChatChecklist) {
    val haptics = LocalHapticFeedback.current
    val muted = onBubbleColor.copy(alpha = MUTED_ALPHA)
    val pending = items.pendingChecklistMarks(message.id)
    val done = checklist.tasks.count { it.shownDone(pending) }

    Column(Modifier.widthIn(max = CHECKLIST_MAX_WIDTH).fillMaxWidth()) {
        SenderName(background = bubbleColor)
        OriginLabel()

        RichText(
            text = checklist.title,
            entities = checklist.titleEntities,
            style = MaterialTheme.typography.titleMedium,
            linkColor = accent,
            onLongClick = ::requestMenu,
        )
        Text(
            text = stringResource(
                Res.string.poll_subtitle_joined,
                stringResource(if (checklist.othersCanMarkTasksAsDone) Res.string.checklist_kind_group else Res.string.checklist_kind),
                stringResource(Res.string.checklist_progress, done, checklist.tasks.size),
            ),
            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
            color = muted,
            modifier = Modifier.padding(top = 2.dp),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(TASK_SPACING),
            modifier = Modifier.padding(top = 10.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            checklist.tasks.forEach { task ->
                val isDone = task.shownDone(pending)
                TaskRow(
                    task = task,
                    done = isDone,
                    // Kdo úkol odškrtl, má smysl jen tam, kde odškrtávají i ostatní — jinak je to
                    // vždycky autor.
                    showDoneBy = checklist.othersCanMarkTasksAsDone && task.isDone,
                    enabled = checklist.canMarkTasksAsDone,
                    muted = muted,
                    onClick = {
                        haptics.toggle(!isDone)
                        act(MessageAction.MarkChecklistTask(task.id, done = !isDone))
                    },
                )
            }
        }

        if (checklist.canAddTasks) {
            TextButton(
                onClick = { act(MessageAction.AddChecklistTask) },
                colors = ButtonDefaults.textButtonColors(contentColor = accent),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.padding(top = 4.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painterResource(Res.drawable.add_24dp),
                    contentDescription = null,
                    modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                )
                Text(stringResource(Res.string.checklist_add_task))
            }
        }
    }
}

/**
 * Úkol: zaškrtávátko a text, u odškrtnutého ve skupinovém seznamu pod ním, kdo ho odškrtl.
 *
 * Řádek, kde odškrtávat nejde, NESMÍ mít `enabled = false` — vzal by s sebou i dlouhý stisk
 * a nad seznamem by nešla otevřít nabídka zprávy (tatáž past jako u odpovědi v anketě).
 */
@Composable
private fun MessageBodyScope.TaskRow(
    task: ChecklistItem,
    done: Boolean,
    showDoneBy: Boolean,
    enabled: Boolean,
    muted: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TASK_MIN_HEIGHT)
            .clip(RoundedCornerShape(TASK_CORNER))
            .contextClickable(
                interactionSource = null,
                indication = if (enabled) ripple() else null,
                onLongClick = ::requestMenu,
                onClick = { if (enabled) onClick() },
            )
            .semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(done)
            }
            .padding(vertical = 6.dp),
    ) {
        Icon(
            painter = painterResource(if (done) Res.drawable.check_box_filled_24dp else Res.drawable.check_box_outline_blank_24dp),
            contentDescription = null,
            tint = if (enabled || done) accent else muted,
            modifier = Modifier.size(TASK_ICON),
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            RichText(
                text = task.text,
                entities = task.entities,
                style = MaterialTheme.typography.bodyLarge,
                linkColor = accent,
                onLinkClick = { act(MessageAction.OpenLink(it)) },
                onLongClick = ::requestMenu,
            )
            val doneBy = task.doneBy?.title
            if (showDoneBy && doneBy != null) {
                Text(
                    text = stringResource(Res.string.checklist_task_done_by, doneBy),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }
    }
}

/** Víc by na rozloženém Foldu z úkolů udělalo řádky přes půl obrazovky — stejně jako anketa. */
private val CHECKLIST_MAX_WIDTH = 360.dp
private val TASK_MIN_HEIGHT = 44.dp
private val TASK_SPACING = 2.dp
private val TASK_CORNER = 12.dp
private val TASK_ICON = 24.dp
private const val MUTED_ALPHA = 0.7f
