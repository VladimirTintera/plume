package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.ReplyKeyboardButtonKind
import eu.tintera.plume.core.telegram.UnsupportedKeyboardButton
import eu.tintera.plume.features.conversation.items.BotKeyboardButton
import eu.tintera.plume.features.conversation.items.botButtonShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_keyboard_hide
import eu.tintera.plume.resources.bot_keyboard_show
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import eu.tintera.plume.resources.open_in_new_24dp
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.smartphone_24dp
import eu.tintera.plume.resources.ballot_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Vlastní klávesnice bota NAD vstupním polem, ne místo systémové klávesnice.
 *
 * Výměna systémové klávesnice za panel tak, aby pole neposkočilo, je v Compose nejkřehčí část
 * (proto je i výběr samolepek sheet). Nad polem se nic nepřepíná: jde psát i ťukat zároveň
 * a pole stojí. Cena je výška — u velké klávesnice s otevřenou systémovou by na konverzaci
 * nezbylo nic, proto strop ([MAX_HEIGHT]) a rolování, a šipka, která panel sbalí.
 *
 * Sbalení se pamatuje pro jednu klávesnici (klíč je zpráva, ze které je): nová od bota se ukáže
 * zase rozbalená, protože bot jí říká něco nového.
 */
@Composable
fun ReplyKeyboardPanel(
    keyboard: ChatReplyMarkup.Keyboard,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onPress: (row: Int, column: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Při odjezdu sbaleného panelu se kreslí poslední klávesnice, ne prázdno.
    val shown = remember { mutableStateOf(keyboard) }
    shown.value = keyboard
    val current by shown

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PANEL_PADDING_H, vertical = PANEL_PADDING_V),
    ) {
        FilledTonalIconButton(onClick = onToggle, modifier = Modifier.size(TOGGLE_SIZE)) {
            Icon(
                painterResource(if (collapsed) Res.drawable.keyboard_arrow_up_24dp else Res.drawable.keyboard_arrow_down_24dp),
                contentDescription = stringResource(if (collapsed) Res.string.bot_keyboard_show else Res.string.bot_keyboard_hide),
            )
        }
        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(KEYBOARD_SPACING),
                modifier = Modifier
                    .padding(top = PANEL_PADDING_V)
                    .fillMaxWidth()
                    .heightIn(max = MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                current.rows.forEachIndexed { row, buttons ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(KEYBOARD_SPACING),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        buttons.forEachIndexed { column, button ->
                            BotKeyboardButton(
                                text = button.text,
                                emojiIcon = button.iconCustomEmojiId,
                                style = button.style,
                                icon = button.kind.icon(),
                                enabled = true,
                                shape = botButtonShape(
                                    row = row,
                                    column = column,
                                    rows = current.rows.size,
                                    columns = buttons.size,
                                    hangsFromMessage = false,
                                ),
                                pressing = false,
                                onClick = { onPress(row, column) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ReplyKeyboardButtonKind.icon(): DrawableResource? = when (this) {
    ReplyKeyboardButtonKind.Text -> null
    ReplyKeyboardButtonKind.PhoneNumber -> Res.drawable.smartphone_24dp
    ReplyKeyboardButtonKind.Location -> Res.drawable.location_on_24dp
    is ReplyKeyboardButtonKind.Poll -> Res.drawable.ballot_24dp
    is ReplyKeyboardButtonKind.WebApp -> Res.drawable.open_in_new_24dp
    is ReplyKeyboardButtonKind.Unsupported -> when (feature) {
        UnsupportedKeyboardButton.Users, UnsupportedKeyboardButton.Chat, UnsupportedKeyboardButton.ManagedBot -> null
    }
}

/** Tři řádky tlačítek a kus čtvrtého — ten napoví, že se dá rolovat. */
private val MAX_HEIGHT = 190.dp
private val PANEL_PADDING_H = 12.dp
private val PANEL_PADDING_V = 4.dp
private val KEYBOARD_SPACING = 2.dp
private val TOGGLE_SIZE = 32.dp
