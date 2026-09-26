package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.localizedLanguageName
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.translation_copy
import eu.tintera.plume.resources.translation_failed
import eu.tintera.plume.resources.translation_title
import eu.tintera.plume.resources.translation_to
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Překlad zprávy: kam se překládá, začátek originálu a pod ním překlad s tlačítkem na kopírování.
 *
 * **Sheet, ne náhrada textu v bublině.** Překlad je jednorázové nahlédnutí jako „Informace"
 * a v bublině by se musel pamatovat u každé zprávy, přežít úpravu i přeposlání. Tak to dělá
 * i oficiální Android klient.
 *
 * **Originál je jen pár řádků** — celý je v konverzaci hned nad sheetem; tady jen připomíná,
 * co se překládá. Překlad v tónované ploše, aby bylo vidět, který z textů je který.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TranslationSheet(
    sheet: TranslationSheet,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text(
                text = stringResource(Res.string.translation_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    Res.string.translation_to,
                    localizedLanguageName(sheet.languageCode) ?: sheet.languageCode,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = sheet.message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = ORIGINAL_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 16.dp),
            )

            Surface(
                shape = RoundedCornerShape(TRANSLATION_CORNER),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                val body = sheet.body
                when {
                    body != null -> {
                        // Odkazy, zmínky a hashtagy v překladu nikam nevedou — sheet nemá konverzaci,
                        // kam by se otevřely. Modrý text, po kterém se nic nestane, je horší než
                        // obyčejný, takže zůstane jen formátování.
                        val entities = remember(body) { body.entities.filterNot { it.type.isClickable } }
                        Box(Modifier.heightIn(max = MAX_TRANSLATION_HEIGHT).verticalScroll(rememberScrollState())) {
                            RichText(
                                text = body.text,
                                entities = entities,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    sheet.failed -> Text(
                        text = stringResource(Res.string.translation_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                    else -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
            }

            if (sheet.body != null) {
                FilledTonalButton(onClick = onCopy, modifier = Modifier.padding(top = 16.dp).align(Alignment.End)) {
                    Icon(
                        painterResource(Res.drawable.content_copy_24dp),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(Res.string.translation_copy))
                }
            }
        }
    }
}

/** Entity, které v textu vedou jinam (odkaz, zmínka, hashtag…), a v sheetu tedy nemají kam. */
private val MessageEntityType.isClickable: Boolean
    get() = this is MessageEntityType.Link ||
        this is MessageEntityType.Mention ||
        this is MessageEntityType.MentionUser ||
        this is MessageEntityType.Hashtag ||
        this is MessageEntityType.BotCommand ||
        this is MessageEntityType.MediaTimestamp

/** Kolik řádků originálu se připomene nad překladem. */
private const val ORIGINAL_LINES = 3

/** Strop výšky překladu; delší se v ploše roluje. */
private val MAX_TRANSLATION_HEIGHT = 360.dp

private val TRANSLATION_CORNER = 16.dp
