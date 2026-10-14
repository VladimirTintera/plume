package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.adaptive.columnInset

/**
 * Zprávy ve sloupci [ContentWidth.Conversation] uprostřed panelu. [content] dostane vodorovný
 * okraj, který si přidá do `contentPadding` seznamu: seznam sám zůstává přes celou plochu, takže
 * se kolečkem i tahem scrolluje kdekoli, ne jen nad sloupcem. Na ploše užší než sloupec je okraj
 * nula.
 *
 * Vlastní funkce, ne logika uvnitř `ConversationScreen` — ta je v dexu na hraně počtu registrů
 * (viz Gotchas v `CLAUDE.md`).
 */
@Composable
internal fun ConversationColumn(content: @Composable (inset: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        content(columnInset(maxWidth, ContentWidth.Conversation))
    }
}
