package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.BotButtonKind
import eu.tintera.plume.core.telegram.BotButtonStyle
import eu.tintera.plume.core.telegram.BotKeyboard
import eu.tintera.plume.core.telegram.UnsupportedBotButton
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.open_in_new_24dp
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.share_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max
import kotlin.math.min

/**
 * Zpráva a pod ní tlačítka bota, jako jeden kus.
 *
 * **Tlačítka jsou aspoň tak široká jako zpráva, a jen když potřebují, širší** — do stropu šířky
 * zprávy ([maxWidthFraction]). Na šířku zprávy natvrdo by se u krátké otázky („Kolik je 2 + 3?")
 * nevešly ani popisky, přes celý řádek by zase tlačítka odtrhla od zprávy, ke které patří.
 * `IntrinsicSize` na to nejde použít: zpráva bývá fotka v `BoxWithConstraints` a ten intrinsické
 * míry neumí (viz „Gotchas").
 *
 * Obojí se zarovnává ke straně odesílatele, jako zpráva sama.
 */
@Composable
internal fun WithBotKeyboard(
    keyboard: BotKeyboard?,
    isOutgoing: Boolean,
    maxWidthFraction: Float,
    keyboardContent: @Composable () -> Unit,
    message: @Composable () -> Unit,
) {
    if (keyboard == null) {
        message()
        return
    }
    Layout(contents = listOf(message, keyboardContent)) { (messages, keyboards), constraints ->
        val messagePlaceable = messages.first().measure(constraints)
        val maxWidth = if (constraints.hasBoundedWidth) {
            min((constraints.maxWidth * maxWidthFraction).toInt(), MAX_MESSAGE_WIDTH.roundToPx())
        } else {
            MAX_MESSAGE_WIDTH.roundToPx()
        }
        val keyboardPlaceable = keyboards.first().measure(
            Constraints(minWidth = min(messagePlaceable.width, maxWidth), maxWidth = maxWidth),
        )
        val gap = KEYBOARD_GAP.roundToPx()
        val width = max(messagePlaceable.width, keyboardPlaceable.width)
        layout(width, messagePlaceable.height + gap + keyboardPlaceable.height) {
            messagePlaceable.place(if (isOutgoing) width - messagePlaceable.width else 0, 0)
            keyboardPlaceable.place(
                if (isOutgoing) width - keyboardPlaceable.width else 0,
                messagePlaceable.height + gap,
            )
        }
    }
}

/**
 * Tlačítka bota po řádcích. Tlačítka v řádku mají stejnou šířku — tak je bot skládá a tak je
 * kreslí i oficiální klienti.
 *
 * Blok zaoblených tlačítek jako skupiny v seznamu chatů: malé zaoblení uvnitř, velké v dolních
 * rozích bloku ([botButtonShape]). Nahoře je zaoblení malé, protože blok visí ze zprávy.
 */
@Composable
internal fun BotKeyboardView(
    keyboard: BotKeyboard,
    /** Tlačítko, na jehož odpověď se čeká (řádek, sloupec); `null` = žádné. */
    pressing: Pair<Int, Int>?,
    onPress: (row: Int, column: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KEYBOARD_SPACING),
        modifier = modifier.width(IntrinsicSize.Max),
    ) {
        keyboard.rows.forEachIndexed { row, buttons ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(KEYBOARD_SPACING),
                modifier = Modifier.fillMaxWidth(),
            ) {
                buttons.forEachIndexed { column, button ->
                    BotKeyboardButton(
                        text = button.text,
                        style = button.style,
                        icon = button.kind.cornerIcon(),
                        enabled = button.kind != BotButtonKind.Disabled,
                        shape = botButtonShape(
                            row = row,
                            column = column,
                            rows = keyboard.rows.size,
                            columns = buttons.size,
                        ),
                        pressing = pressing == row to column,
                        onClick = { onPress(row, column) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Jedno tlačítko bota — pod zprávou i ve vlastní klávesnici nad polem, ať vypadají stejně.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BotKeyboardButton(
    text: String,
    style: BotButtonStyle,
    /** Co tlačítko udělá jinak než obvykle (odkaz, kopie…); `null` = nic zvláštního. */
    icon: DrawableResource?,
    enabled: Boolean,
    shape: RoundedCornerShape,
    pressing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val disabled = !enabled
    val (container, content) = MaterialTheme.colorScheme.botButtonColors(style)
    Surface(
        onClick = onClick,
        enabled = !disabled,
        shape = shape,
        color = if (disabled) MaterialTheme.colorScheme.surfaceContainerHigh else container,
        contentColor = if (disabled) MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA) else content,
        modifier = modifier.heightIn(min = BUTTON_MIN_HEIGHT),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = BUTTON_PADDING_H, vertical = BUTTON_PADDING_V)
                    // Na odpověď bota se čeká: text drží místo, aby tlačítko nezměnilo šířku.
                    .alpha(if (pressing) 0f else 1f),
            )
            if (pressing) LoadingIndicator(Modifier.size(PRESSING_INDICATOR_SIZE))
            icon?.let { icon ->
                // Co tlačítko udělá jinak než zpětné volání (odkaz, kopie…), napoví ikona v rohu —
                // jako u oficiálních klientů. Popisek píše bot a nemusí to říkat.
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(CORNER_ICON_PADDING)
                        .size(CORNER_ICON_SIZE),
                )
            }
        }
    }
}

/**
 * Barva podle přání bota. „Úspěch" nemá v M3 vlastní roli, nese ho terciární — stejně jako
 * doplňkové prvky jinde v konverzaci.
 */
private fun ColorScheme.botButtonColors(style: BotButtonStyle): Pair<Color, Color> = when (style) {
    BotButtonStyle.Default -> secondaryContainer to onSecondaryContainer
    BotButtonStyle.Primary -> primary to onPrimary
    BotButtonStyle.Success -> tertiaryContainer to onTertiaryContainer
    BotButtonStyle.Danger -> errorContainer to onErrorContainer
}

private fun BotButtonKind.cornerIcon(): DrawableResource? = when (this) {
    is BotButtonKind.Url -> Res.drawable.open_in_new_24dp
    is BotButtonKind.CopyText -> Res.drawable.content_copy_24dp
    is BotButtonKind.User -> Res.drawable.person_24dp
    is BotButtonKind.Unsupported -> when (feature) {
        UnsupportedBotButton.WebApp, UnsupportedBotButton.LoginUrl -> Res.drawable.open_in_new_24dp
        UnsupportedBotButton.SwitchInline -> Res.drawable.share_24dp
        UnsupportedBotButton.Buy, UnsupportedBotButton.Game, UnsupportedBotButton.Password -> null
    }
    BotButtonKind.Callback, BotButtonKind.Disabled -> null
}

/**
 * Tvar tlačítka v bloku: velké zaoblení v rozích celé klávesnice. Pod zprávou jen v dolních —
 * nahoře blok visí ze zprávy ([hangsFromMessage]); samostatná klávesnice nad polem má všechny.
 */
internal fun botButtonShape(
    row: Int,
    column: Int,
    rows: Int,
    columns: Int,
    hangsFromMessage: Boolean = true,
): RoundedCornerShape {
    val firstRow = row == 0 && !hangsFromMessage
    val lastRow = row == rows - 1
    fun corner(outer: Boolean): Dp = if (outer) BUBBLE_CORNER else BUBBLE_TIGHT
    return RoundedCornerShape(
        topStart = corner(firstRow && column == 0),
        topEnd = corner(firstRow && column == columns - 1),
        bottomStart = corner(lastRow && column == 0),
        bottomEnd = corner(lastRow && column == columns - 1),
    )
}

/** Stejný strop jako u zprávy samotné (`MessageBox`). */
private val MAX_MESSAGE_WIDTH = 560.dp
private val KEYBOARD_GAP = 4.dp
private val KEYBOARD_SPACING = 2.dp
/**
 * Rovnou dotyková velikost. S nižším tlačítkem ho `Surface` dorovná na 48 dp PRŮHLEDNÝM okrajem
 * (`minimumInteractiveComponentSize`), a svislé mezery mezi řádky pak vyšly o 8 dp větší než
 * vodorovné — klávesnice vypadala jako řádky, které k sobě nepatří.
 */
private val BUTTON_MIN_HEIGHT = 48.dp
private val BUTTON_PADDING_H = 16.dp
private val BUTTON_PADDING_V = 12.dp
private val PRESSING_INDICATOR_SIZE = 28.dp
private val CORNER_ICON_SIZE = 12.dp
private val CORNER_ICON_PADDING = 5.dp
private const val DISABLED_ALPHA = 0.38f
