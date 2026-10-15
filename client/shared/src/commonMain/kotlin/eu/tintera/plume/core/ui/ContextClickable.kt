package eu.tintera.plume.core.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize

/**
 * `combinedClickable`, u kterého pravé tlačítko myši udělá totéž co podržení ([onLongClick]).
 * **Holé `combinedClickable` se v aplikaci nepoužívá** — hlídá to `verifyContextClickable`.
 *
 * Podržení otevírá v aplikaci nabídky (zpráva, chat, složka, téma, samolepka…). Na desktopu je
 * dlouhý stisk myši věc, na kterou nikdo nepřijde, kdežto pravé tlačítko je nabídka odjakživa.
 * `combinedClickable` na myši reaguje jen na levé tlačítko (`awaitFirstDown` ho vyžaduje), takže
 * pravé dřív nedělalo nic. Platí i pro Android s myší (Chromebook, DeX).
 *
 * Parametry i přetížení zrcadlí `combinedClickable`; výměna je tedy jen jméno a import.
 */
fun Modifier.contextClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    hapticFeedbackEnabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this
    .onSecondaryClick(onClick = onLongClick.takeIf { enabled })
    .combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        onClick = onClick,
    )

/** [contextClickable] s vlastním [interactionSource] a [indication]. */
fun Modifier.contextClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    hapticFeedbackEnabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this
    .onSecondaryClick(onClick = onLongClick.takeIf { enabled })
    .combinedClickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        onClick = onClick,
    )

/**
 * Pravé tlačítko myši spustí [onClick]; `null` = nic (a událost jde dál). Pro místa, kde se
 * podržení detekuje vlastním gestem, ne přes [contextClickable] (odkaz v textu, odesílací
 * tlačítko, přepínač chatů).
 *
 * - **Jen myš.** Dotyk a pero pravé tlačítko nemají; podržení prstem zůstává, jak je.
 * - **Spouští se puštěním**, jako kontextová nabídka ve Windows; stisk se jen spotřebuje.
 *   Puštění mimo prvek nic nespustí.
 * - **[accepts] omezí, kde pravé tlačítko platí** (stisk mimo nechá být): odkaz uvnitř textu
 *   chytá pravé tlačítko jen nad odkazem, jinde patří bublině.
 * - **Vnořený prvek má přednost.** Událost dojde napřed k nejhlubšímu prvku (hlavní průchod jde
 *   od listů); kdo ji spotřebuje, ten nabídku otevře a obal kolem už ne — pravé tlačítko na
 *   příloze v bublině tak otevře nabídku přílohy, ne zprávy.
 */
fun Modifier.onSecondaryClick(accepts: ((Offset) -> Boolean)? = null, onClick: (() -> Unit)?): Modifier =
    this then SecondaryClickElement(accepts, onClick)

private class SecondaryClickElement(
    private val accepts: ((Offset) -> Boolean)?,
    private val onClick: (() -> Unit)?,
) : ModifierNodeElement<SecondaryClickNode>() {
    override fun create() = SecondaryClickNode(accepts, onClick)

    override fun update(node: SecondaryClickNode) {
        node.accepts = accepts
        node.onClick = onClick
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "onSecondaryClick"
    }

    override fun equals(other: Any?): Boolean =
        other is SecondaryClickElement && other.onClick === onClick && other.accepts === accepts

    override fun hashCode(): Int = 31 * onClick.hashCode() + accepts.hashCode()
}

private class SecondaryClickNode(
    var accepts: ((Offset) -> Boolean)?,
    var onClick: (() -> Unit)?,
) : Modifier.Node(), PointerInputModifierNode {
    /** Pravé tlačítko je stisknuté nad tímhle prvkem a stisk jsme si vzali. */
    private var pressed = false

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main) return
        val action = onClick
        val change = pointerEvent.changes.firstOrNull()
        if (action == null || change == null || change.type != PointerType.Mouse) {
            pressed = false
            return
        }
        when (pointerEvent.type) {
            PointerEventType.Press -> if (
                pointerEvent.buttons.isSecondaryPressed &&
                !change.isConsumed &&
                accepts?.invoke(change.position) != false
            ) {
                pressed = true
                change.consume()
            }
            PointerEventType.Release -> if (pressed && !pointerEvent.buttons.isSecondaryPressed) {
                pressed = false
                val inside = change.position.x in 0f..bounds.width.toFloat() && change.position.y in 0f..bounds.height.toFloat()
                if (inside && !change.isConsumed) {
                    change.consume()
                    action()
                }
            }
        }
    }

    override fun onCancelPointerInput() {
        pressed = false
    }
}
