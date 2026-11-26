package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.ChatMessage

/**
 * Citace jako horní díl karty holého snímku (`MediaMessageItem`): tónovaná plocha přes celou
 * šířku karty, nad značkou přeposlání — stejné pořadí a barvy jako u odpovědi v bublině
 * ([QuotedBubble]).
 */
@Composable
internal fun MessageBodyScope.QuotePart(quoted: ChatMessage, partnerName: String?) {
    Box(Modifier.fillMaxWidth().background(insetColor)) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onTertiaryContainer) {
            ReplyQuote(quoted, partnerName)
        }
    }
}
