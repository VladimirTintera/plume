package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.localizedLanguageName
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.SearchPill
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.expand_more_24dp
import eu.tintera.plume.resources.translation_change_language
import eu.tintera.plume.resources.translation_copy
import eu.tintera.plume.resources.translation_failed
import eu.tintera.plume.resources.translation_language_search
import eu.tintera.plume.resources.translation_language_title
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
    onLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var picking by rememberSaveable { mutableStateOf(false) }

    if (picking) {
        TranslationLanguageSheet(
            selected = sheet.languageCode,
            onPick = { code ->
                picking = false
                if (code != sheet.languageCode) onLanguageChange(code)
            },
            onDismiss = { picking = false },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text(
                text = stringResource(Res.string.translation_title),
                style = MaterialTheme.typography.titleMedium,
            )
            // Cílový jazyk je tlačítko, ne popisek: ťuknutí otevře výběr jazyka. Ze šipky je
            // poznat, že se dá změnit — samotný text „Do: Čeština" by vypadal jako informace.
            TextButton(
                onClick = { picking = true },
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                modifier = Modifier.offset(x = -ButtonDefaults.TextButtonWithIconContentPadding.calculateLeftPadding(LayoutDirection.Ltr)),
            ) {
                Text(
                    stringResource(
                        Res.string.translation_to,
                        localizedLanguageName(sheet.languageCode) ?: sheet.languageCode,
                    ),
                )
                Icon(
                    painterResource(Res.drawable.expand_more_24dp),
                    contentDescription = stringResource(Res.string.translation_change_language),
                    modifier = Modifier.padding(start = 4.dp).size(ButtonDefaults.IconSize),
                )
            }

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

/**
 * Výběr cílového jazyka překladu: sheet NAD sheetem překladu, jako detail sady nad výběrem
 * samolepek. Gesto zpět zavře jen výběr a překlad zůstane otevřený; obsah vyměněný uvnitř
 * jednoho sheetu by zpět zavřelo celé.
 *
 * Napřed jazyk aplikace, pak ostatní abecedně ([translationLanguageOptions]).
 */
@Composable
private fun TranslationLanguageSheet(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var query by rememberSaveable { mutableStateOf("") }
    val options = remember(query) { translationLanguageOptions(platformLanguageCode, query, ::localizedLanguageName) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.translation_language_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SearchPill(
                initialQuery = "",
                onQueryChange = { query = it },
                placeholder = stringResource(Res.string.translation_language_search),
                clearDescription = stringResource(Res.string.chats_search_clear),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (options.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(LANGUAGE_LIST_HEIGHT), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.chats_search_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(LANGUAGE_LIST_HEIGHT)) {
                    items(options, key = { it.code }) { language ->
                        LanguageRow(language, isSelected = language.code == selected, onClick = { onPick(language.code) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(language: TranslationLanguage, isSelected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(language.name) },
        trailingContent = {
            if (isSelected) Icon(painterResource(Res.drawable.check_24dp), contentDescription = null)
        },
        // Popředí vybraného řádku se předává i ListItem — sám barví text `onSurface`, což na
        // světlém `secondaryContainer` v paletě se zvýšeným kontrastem není vidět.
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
    )
}

/** Výška seznamu jazyků — pevná, aby sheet při hledání neskákal. */
private val LANGUAGE_LIST_HEIGHT = 480.dp

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
