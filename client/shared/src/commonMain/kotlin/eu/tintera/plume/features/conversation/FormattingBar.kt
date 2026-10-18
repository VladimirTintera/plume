package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.horizontalScrollWithWheel
import eu.tintera.plume.resources.unfold_less_24dp
import eu.tintera.plume.resources.format_quote_expandable
import eu.tintera.plume.resources.format_code_language_hint
import eu.tintera.plume.resources.format_code_language
import eu.tintera.plume.core.telegram.codeLanguageOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import eu.tintera.plume.core.telegram.linkTargetOf
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.format_link
import eu.tintera.plume.resources.format_link_hint
import eu.tintera.plume.resources.format_link_remove
import eu.tintera.plume.resources.format_link_save
import eu.tintera.plume.resources.link_24dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import eu.tintera.plume.core.ui.WithTooltip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.TextFormat
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.code_24dp
import eu.tintera.plume.resources.format_bold
import eu.tintera.plume.resources.format_bold_24dp
import eu.tintera.plume.resources.format_clear
import eu.tintera.plume.resources.format_clear_24dp
import eu.tintera.plume.resources.format_code
import eu.tintera.plume.resources.format_italic
import eu.tintera.plume.resources.format_italic_24dp
import eu.tintera.plume.resources.format_quote
import eu.tintera.plume.resources.format_quote_24dp
import eu.tintera.plume.resources.format_spoiler
import eu.tintera.plume.resources.format_strikethrough
import eu.tintera.plume.resources.format_strikethrough_24dp
import eu.tintera.plume.resources.format_underline
import eu.tintera.plume.resources.format_underlined_24dp
import eu.tintera.plume.resources.visibility_off_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Lišta formátování pod polem: tučné, kurzíva, podtržení, přeškrtnutí, odkaz, spoiler, kód,
 * citace a „bez formátování". Vyjede sama, dokud je v poli označený text; bez výběru ji otevře
 * ikona „Aa" v poli a formát se pak použije na další psaní od kurzoru (`PendingFormats`).
 *
 * **Lišta, ne položky v nabídce výběru.** Ikony u vlastních položek nabídka výběru na telefonu
 * nezobrazí: Android kreslí lištu výběru systémovým `ActionMode` a `leadingIcon` z naší položky
 * zahodí (kreslí ho jen rozbalovací nabídka pro myš), na iOS ji kreslí `UIEditMenu`. Lišta navíc
 * ukáže, co už na výběru je — zapnuté tlačítko —, což textová položka neumí.
 *
 * Tlačítka jsou přepínače jako filtry v seznamu chatů (`toggleableShapes`, zapnuté se zakulatí
 * do čtverce), popisek je v tooltipu po podržení a v `contentDescription`. Na úzkém displeji se
 * řada posouvá do strany: tlačítka mají minimální dotykovou plochu 48 dp a osm se jich vedle
 * pole na složeném telefonu nevejde.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FormattingBar(
    composer: ComposerDraft,
    onChanged: () -> Unit,
    /** „Odkaz": dialog s adresou pro výběr. Drží ho obrazovka — lišta s otevřením dialogu zmizí. */
    onLink: () -> Unit,
    /** Jazyk bloku kódu u kurzoru: dialog, ze stejného důvodu jako odkaz na obrazovce. */
    onCodeLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().horizontalScrollWithWheel().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Odkaz hned za písmem: na úzkém displeji se řada posouvá, a odkaz se hledá častěji
        // než spoiler nebo kód na jejím konci.
        FORMAT_BUTTONS.take(4).forEach { FormatToggle(composer, it, onChanged) }
        // Odkaz je přepínač jen na pohled: zapnutý = výběr už odkazuje, ťuknutí otevře dialog
        // (adresu jde změnit i odebrat), ne vypnutí naslepo.
        Labelled(stringResource(Res.string.format_link)) { text ->
            IconToggleButton(
                checked = composer.selectionLink != null,
                onCheckedChange = { onLink() },
                // Odkaz potřebuje popisek — bez výběru není na čem ho nastavit.
                enabled = !composer.value.selection.collapsed,
                shapes = IconButtonDefaults.toggleableShapes(),
                colors = toggleColors(),
            ) {
                Icon(painterResource(Res.drawable.link_24dp), contentDescription = text)
            }
        }
        FORMAT_BUTTONS.drop(4).forEach { button ->
            FormatToggle(composer, button, onChanged)
            // Doplňky formátu hned za ním, jen když je kurzor nebo výběr v bloku kódu či citaci:
            // jinde by nabízely něco, co nemá na čem být.
            when (button.first) {
                TextFormat.Code -> AnimatedVisibility(
                    visible = composer.codeBlock != null,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
                ) {
                    // Popisek je jazyk sám („kotlin"): ikona by neřekla, co je nastavené.
                    Labelled(stringResource(Res.string.format_code_language)) { text ->
                        TextButton(onClick = onCodeLanguage, modifier = Modifier.semantics { contentDescription = text }, shapes = ButtonDefaults.shapes()) {
                            Text(composer.codeBlock?.language ?: text)
                        }
                    }
                }
                TextFormat.Quote -> AnimatedVisibility(
                    visible = composer.quote != null,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
                ) {
                    val expandable = composer.quote?.format == TextFormat.ExpandableQuote
                    Labelled(stringResource(Res.string.format_quote_expandable)) { text ->
                        IconToggleButton(
                            checked = expandable,
                            onCheckedChange = {
                                composer.toggleExpandableQuote()
                                haptics.toggle(!expandable)
                                onChanged()
                            },
                            shapes = IconButtonDefaults.toggleableShapes(),
                            colors = toggleColors(),
                        ) {
                            Icon(painterResource(Res.drawable.unfold_less_24dp), contentDescription = text)
                        }
                    }
                }
                else -> Unit
            }
        }
        // „Bez formátování" je akce, ne stav — obyčejné tlačítko, zašedlé, když není co sundat.
        Labelled(stringResource(Res.string.format_clear)) { text ->
            IconButton(
                onClick = {
                    composer.clearSelection()
                    haptics.tick()
                    onChanged()
                },
                enabled = composer.selectionFormatted,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(painterResource(Res.drawable.format_clear_24dp), contentDescription = text)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FormatToggle(
    composer: ComposerDraft,
    button: Triple<TextFormat, DrawableResource, StringResource>,
    onChanged: () -> Unit,
) {
    val (format, icon, label) = button
    val haptics = LocalHapticFeedback.current
    val applied = composer.isApplied(format)
    Labelled(stringResource(label)) { text ->
        IconToggleButton(
            checked = applied,
            onCheckedChange = {
                composer.toggle(format)
                haptics.toggle(!applied)
                onChanged()
            },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = toggleColors(),
        ) {
            Icon(painterResource(icon), contentDescription = text)
        }
    }
}

/**
 * Plná `primary`, ne `secondaryContainer` jako u filtrů seznamu chatů: karta pole je přitónovaná
 * do `primaryContainer` a světlý kontejner na ní zanikal.
 */
@Composable
private fun toggleColors() = IconButtonDefaults.iconToggleButtonColors(
    checkedContainerColor = MaterialTheme.colorScheme.primary,
    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun Labelled(label: String, content: @Composable (String) -> Unit) {
    WithTooltip(label) {
        content(label)
    }
}

private val FORMAT_BUTTONS: List<Triple<TextFormat, DrawableResource, StringResource>> = listOf(
    Triple(TextFormat.Bold, Res.drawable.format_bold_24dp, Res.string.format_bold),
    Triple(TextFormat.Italic, Res.drawable.format_italic_24dp, Res.string.format_italic),
    Triple(TextFormat.Underline, Res.drawable.format_underlined_24dp, Res.string.format_underline),
    Triple(TextFormat.Strikethrough, Res.drawable.format_strikethrough_24dp, Res.string.format_strikethrough),
    Triple(TextFormat.Spoiler, Res.drawable.visibility_off_24dp, Res.string.format_spoiler),
    Triple(TextFormat.Code, Res.drawable.code_24dp, Res.string.format_code),
    Triple(TextFormat.Quote, Res.drawable.format_quote_24dp, Res.string.format_quote),
)

/**
 * Jazyk bloku kódu ([current] je ten nastavený). Prázdné pole jazyk sundá; uložit jde jen jedno
 * slovo ([codeLanguageOf]).
 */
@Composable
internal fun CodeLanguageDialog(current: String?, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.orEmpty()) }
    val language = codeLanguageOf(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.format_code_language)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(Res.string.format_code_language_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            // Prázdné = bez jazyka, to jde uložit taky; mezera uprostřed ne.
            TextButton(enabled = text.isBlank() || language != null, onClick = { onConfirm(language) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.format_link_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/**
 * Adresa odkazu s popiskem. [current] je adresa, na kterou výběr už odkazuje — pak jde změnit
 * i odebrat. Uložit jde jen platnou adresu ([linkTargetOf]); bez schématu se doplní `https://`.
 */
@Composable
internal fun LinkDialog(current: String?, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.orEmpty()) }
    val url = linkTargetOf(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.format_link)) },
        text = {
            // Jako pole v ostatních dialozích (odkaz v anketě, štítek člena).
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(Res.string.format_link_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = url != null, onClick = { url?.let(onConfirm) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.format_link_save))
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onConfirm(null) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.format_link_remove)) }
                }
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
            }
        },
    )
}
