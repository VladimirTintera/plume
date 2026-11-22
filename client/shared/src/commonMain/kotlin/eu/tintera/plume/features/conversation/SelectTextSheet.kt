package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.menu_select_text
import org.jetbrains.compose.resources.stringResource

/**
 * Text zprávy k výběru úryvku.
 *
 * **Proč sheet, a ne výběr přímo v bublině:** `SelectionContainer` zahajuje výběr dlouhým
 * stiskem, a ten v konverzaci už patří nabídce nad zprávou. Kdyby se o něj obě funkce dělily,
 * jedna by přestala fungovat — a nabídka je ta častější. Sheet je stejná cesta jako
 * „Informace": jednorázová akce z nabídky, která si otevře vlastní plochu.
 *
 * **Text je holý, ne `RichText`.** Odkazy v něm mají vlastní klikání a v `SelectionContainer`
 * by se o gesta praly s výběrem. Bere se tentýž text, jaký by dalo „Kopírovat"
 * ([ChatMessage.copyableText]), takže výběr nabízí přesně to, co zpráva opravdu nese —
 * formátování se do schránky stejně nepřenáší.
 *
 * Kopírování obstará systémová nabídka nad vybraným úsekem; vlastní tlačítko by jen zdvojovalo,
 * co platforma umí sama (a na každé z nich vypadá, jak je uživatel zvyklý).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectTextSheet(message: ChatMessage, onDismiss: () -> Unit) {
    // Modální sheet začíná skrytý a rozjede se sám — jako u „Informace".
    val sheetState = rememberExpandedSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            SheetTitle(stringResource(Res.string.menu_select_text), modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp))
            // Dlouhá zpráva se scrolluje; strop drží sheet v rozumné výšce i u pár set řádků.
            SelectionContainer {
                Text(
                    text = message.copyableText(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .heightIn(max = MAX_TEXT_HEIGHT)
                        .verticalScroll(rememberRestoringScrollState())
                        .padding(bottom = 24.dp),
                )
            }
        }
    }
}

/** Nad tuhle výšku se text sheetu scrolluje; jinak by dlouhá zpráva zabrala celou obrazovku. */
private val MAX_TEXT_HEIGHT = 420.dp
