package eu.tintera.plume.core.ui

import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_country_choose
import eu.tintera.plume.resources.auth_country_label
import eu.tintera.plume.resources.auth_input_phone
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Země a číslo jako **jeden blok**, ne dvě nesouvisející pole: předvolba bez země nic neznamená
 * a naopak. Stejný jazyk jako skupiny v Nastavení — nadpis a obsah drží pohromadě.
 *
 * **Fokus a chybu nese rámeček kolem celého bloku** ([blockOutlineColor]), stejně jako u hesla
 * a u políček kódu. Dřív to bylo podtržení `TextField`u, tedy čára široká jen jako pole
 * a přilepená přesně na spodní hranu karty — vypadala jako lem, který tam něco zapomnělo,
 * a přihlašovací obrazovka měla pro tutéž věc tři různé projevy.
 */
@Composable
fun PhoneField(
    phone: PhoneEntry,
    isError: Boolean,
    onPhoneChange: (String) -> Unit,
    onPickCountry: () -> Unit,
    onSubmit: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Stav pole (`TextField(value)` s `VisualTransformation` je od Compose 1.13 zastaralý); číslice
    // drží ViewModel jako text, stav se s nimi synchronizuje.
    val state = rememberSyncedTextFieldState(phone.digits, onPhoneChange)
    val output = remember(phone.formatted) { PhoneNumberTransformation(phone.formatted) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.groupRow,
        border = BorderStroke(BLOCK_OUTLINE, blockOutlineColor(isError = isError, active = focused)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickCountry)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // Vlajka je emoji ze systémového písma — žádná vlastní sada ikon pro dvě stě zemí.
                Text(
                    phone.country?.flagEmoji.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.auth_country_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Než dorazí odhad podle IP, stojí tu výzva — ne podruhé slovo „Země",
                    // což vypadalo jako chyba vykreslení.
                    Text(
                        phone.country?.name ?: stringResource(Res.string.auth_country_choose),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (phone.country == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Icon(
                    painterResource(Res.drawable.keyboard_arrow_down_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    phone.callingCode,
                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp),
                )
                TextField(
                    state = state,
                    // Nápověda, ne plovoucí label: ten po vyplnění odskočí nahoru a text
                    // s ním klesne, takže by číslo přestalo lícovat s předvolbou vedle —
                    // a ta label nemá a mít nemůže.
                    placeholder = { Text(stringResource(Res.string.auth_input_phone)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    isError = isError,
                    // Hodnotou jsou syrové číslice a oddělovače dodává až zobrazení — jinak by
                    // kurzor skákal pokaždé, když formátování vloží mezeru.
                    outputTransformation = output,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Go),
                    onKeyboardAction = { onSubmit() },
                    textStyle = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                    ),
                    interactionSource = interaction,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Zobrazí číslice tak, jak je naformátoval TDLib, ale hodnotou pole zůstanou syrové číslice.
 *
 * Oddělovače se do zobrazení VKLÁDAJÍ ([phoneSeparators]) a polohu kurzoru si Compose odvodí
 * z vložených úseků sám — vlastní mapování pozic (`OffsetMapping` u zastaralé
 * `VisualTransformation`) odpadlo. Kdyby formátování nesedělo s číslicemi v poli (jiná země
 * dorazí o snímek později), nevloží se nic — pole, které ukazuje něco jiného, než co v něm je,
 * je horší než pole bez mezer.
 */
internal class PhoneNumberTransformation(private val formatted: String) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val separators = phoneSeparators(formatted, asCharSequence().toString()) ?: return
        separators.forEach { (position, separator) -> insert(position, separator.toString()) }
    }

    override fun equals(other: Any?) = other is PhoneNumberTransformation && other.formatted == formatted

    override fun hashCode() = formatted.hashCode()
}

/**
 * Kam do [digits] vložit oddělovače z [formatted], aby vzniklo právě [formatted]: dvojice
 * (pozice ve VÝSLEDNÉM textu, znak), seřazené zleva — vkládá se v tomhle pořadí. `null`, když
 * číslice ve formátu nejsou přesně [digits] (formát pro jiné číslo nebo jinou zemi) nebo když
 * není co vkládat.
 */
internal fun phoneSeparators(formatted: String, digits: String): List<Pair<Int, Char>>? {
    if (formatted == digits || formatted.filter { it.isDigit() } != digits) return null
    return formatted.withIndex().filter { !it.value.isDigit() }.map { it.index to it.value }
}
