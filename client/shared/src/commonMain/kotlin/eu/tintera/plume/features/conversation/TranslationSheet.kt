package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.GroupActionText
import eu.tintera.plume.core.ui.GroupAction
import eu.tintera.plume.core.ui.ActionButtonGroup
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.foundation.lazy.itemsIndexed
import eu.tintera.plume.resources.search_off_24dp
import eu.tintera.plume.core.ui.GroupEmptyRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.resources.translation_device_downloading
import eu.tintera.plume.resources.translation_device_download_text
import eu.tintera.plume.resources.translation_device_download
import eu.tintera.plume.resources.translation_on_server
import eu.tintera.plume.resources.translation_on_device_hint
import eu.tintera.plume.resources.translation_on_device
import eu.tintera.plume.core.ui.IconSwitch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.expand_more_24dp
import eu.tintera.plume.resources.translation_change_language
import eu.tintera.plume.resources.translation_copy
import eu.tintera.plume.resources.translation_language_search
import eu.tintera.plume.resources.translation_language_title
import eu.tintera.plume.resources.translation_title
import eu.tintera.plume.resources.server_summary_copy
import eu.tintera.plume.resources.server_summary_note
import eu.tintera.plume.resources.server_summary_title
import eu.tintera.plume.resources.translation_to
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Překlad zprávy: kam se překládá, začátek originálu a pod ním překlad s tlačítkem na kopírování.
 * Tentýž sheet ukazuje i shrnutí od serveru ([TranslationSheet.summary]) — jen s jinými texty
 * a s poznámkou, že ho napsal model.
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
    onOnDeviceChange: (Boolean) -> Unit = {},
    onDownload: () -> Unit = {},
    onServer: () -> Unit = {},
) {
    val sheetState = rememberExpandedSheetState()
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
            SheetTitle(stringResource(if (sheet.summary) Res.string.server_summary_title else Res.string.translation_title))
            // Cílový jazyk je tlačítko, ne popisek: ťuknutí otevře výběr jazyka. Ze šipky je
            // poznat, že se dá změnit — samotný text „Do: Čeština" by vypadal jako informace.
            TextButton(
                onClick = { picking = true },
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                modifier = Modifier.offset(x = -ButtonDefaults.TextButtonWithIconContentPadding.calculateLeftPadding(LayoutDirection.Ltr)),
                shapes = ButtonDefaults.shapes(),
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

            sheet.onDevice?.let { onDevice -> OnDeviceRow(onDevice, onOnDeviceChange) }

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
                    sheet.error != null -> Column(Modifier.padding(16.dp)) {
                        Text(
                            text = sheet.error.resolve(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        // V telefonu to nejde (neznámý jazyk, nepodporovaný pár) — server ano.
                        if (sheet.onDevice == true) {
                            TextButton(onClick = onServer, modifier = Modifier.padding(top = 4.dp), shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(Res.string.translation_on_server))
                            }
                        }
                    }
                    sheet.needsDownload -> Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.translation_device_download_text),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val server = stringResource(Res.string.translation_on_server)
                        val download = stringResource(Res.string.translation_device_download)
                        ActionButtonGroup(
                            listOf(
                                GroupAction(server, onServer, compressionLimit = ButtonDefaults.TextButtonContentPadding) { modifier, source ->
                                    TextButton(onClick = onServer, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) { GroupActionText(server) }
                                },
                                GroupAction(download, onDownload) { modifier, source ->
                                    FilledTonalButton(onClick = onDownload, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) { GroupActionText(download) }
                                },
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                    else -> Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LoadingIndicator()
                        // Stažení balíčků trvá desítky vteřin; bez věty by to vypadalo jako zaseknutý překlad.
                        if (sheet.downloading) {
                            Text(
                                text = stringResource(Res.string.translation_device_downloading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            if (sheet.summary && sheet.body != null) {
                // Shrnutí píše model na serveru, ne odesílatel — a model umí vynechat nebo splést.
                Text(
                    text = stringResource(Res.string.server_summary_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (sheet.body != null && sheet.canCopy) {
                FilledTonalButton(onClick = onCopy, modifier = Modifier.padding(top = 16.dp).align(Alignment.End), shapes = ButtonDefaults.shapes()) {
                    Icon(
                        painterResource(Res.drawable.content_copy_24dp),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(if (sheet.summary) Res.string.server_summary_copy else Res.string.translation_copy))
                }
            }
        }
    }
}

/**
 * „Překládat v telefonu": text zprávy pak neodchází na server. Platí pro další překlady
 * (`AppSettings.translateOnDevice`) a otevřená zpráva se přeloží znovu. Celý řádek nese
 * klikání, přepínač je jen ukazatel stavu (viz `IconSwitch`).
 */
@Composable
private fun OnDeviceRow(onDevice: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .toggleable(value = onDevice, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(Res.string.translation_on_device), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.translation_on_device_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconSwitch(checked = onDevice)
    }
}

/**
 * Výběr cílového jazyka překladu: sheet NAD sheetem překladu, jako detail sady nad výběrem
 * samolepek. Gesto zpět zavře jen výběr a překlad zůstane otevřený; obsah vyměněný uvnitř
 * jednoho sheetu by zpět zavřelo celé. Totéž pro překlad konceptu v sheetu přepisu
 * ([DraftRewriteSheet]).
 *
 * Napřed jazyk aplikace, pak ostatní abecedně ([translationLanguageOptions]).
 */
@Composable
internal fun TranslationLanguageSheet(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Které jazyky nabídnout (překlad v telefonu jich umí jen část); výchozí všechny. */
    available: (String) -> Boolean = { true },
) {
    val sheetState = rememberExpandedSheetState()
    var query by rememberSaveable { mutableStateOf("") }
    val options = remember(query) {
        translationLanguageOptions(platformLanguageCode, query, ::localizedLanguageName).filter { available(it.code) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            SheetTitle(stringResource(Res.string.translation_language_title), modifier = Modifier.padding(horizontal = 24.dp))
            SearchPill(
                initialQuery = "",
                onQueryChange = { query = it },
                placeholder = stringResource(Res.string.translation_language_search),
                clearDescription = stringResource(Res.string.chats_search_clear),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (options.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(LANGUAGE_LIST_HEIGHT)) {
                    GroupEmptyRow(
                        stringResource(Res.string.chats_search_empty),
                        icon = Res.drawable.search_off_24dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(LANGUAGE_LIST_HEIGHT)) {
                    // Jazyky jsou blok zaoblených řádků; vybraný se z něj vyčlení tvarem i barvou,
                    // jako země při přihlášení.
                    itemsIndexed(options, key = { _, language -> language.code }) { index, language ->
                        val isSelected = language.code == selected
                        LanguageRow(
                            language,
                            isSelected = isSelected,
                            onClick = { onPick(language.code) },
                            modifier = Modifier.groupedItem(
                                index = index,
                                count = options.size,
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                selected = isSelected,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(language: TranslationLanguage, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(language.name) },
        trailingContent = {
            if (isSelected) Icon(painterResource(Res.drawable.check_24dp), contentDescription = null)
        },
        // Popředí vybraného řádku se předává i ListItem — sám barví text `onSurface`, což na
        // světlém `secondaryContainer` v paletě se zvýšeným kontrastem není vidět.
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.fillMaxWidth().selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
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
