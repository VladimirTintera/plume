package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.adaptive.PaneHeader
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_pick_chat
import eu.tintera.plume.resources.conversation_placeholder_title
import eu.tintera.plume.resources.forum_filled_24dp
import org.jetbrains.compose.resources.stringResource

/**
 * Detail panel, když je místa na dva panely, ale žádný chat není vybraný. Bez něj by tam zela
 * prázdná plocha, což vypadá jako chyba vykreslení.
 *
 * Úvodní animace ([IntroEmptyState]) běží při každém zobrazení — i po zavření chatu, kdy je to
 * příjemná tečka za konverzací.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier) {
        // Prázdná hlavička výšky hlavičky konverzace: karta tak má stejnou geometrii jako otevřený
        // chat (začíná pod hlavičkou, viz `Panes.headerOutside`) a při otevření chatu neposkočí.
        PaneHeader { Spacer(Modifier.fillMaxWidth().height(TopAppBarDefaults.TopAppBarExpandedHeight)) }
        IntroEmptyState(
            icon = Res.drawable.forum_filled_24dp,
            title = stringResource(Res.string.conversation_placeholder_title),
            text = stringResource(Res.string.conversation_pick_chat),
            modifier = Modifier.weight(1f),
        )
    }
}
