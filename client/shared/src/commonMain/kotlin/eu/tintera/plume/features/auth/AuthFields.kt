package eu.tintera.plume.features.auth

import eu.tintera.plume.core.ui.BLOCK_OUTLINE
import eu.tintera.plume.core.ui.blockOutlineColor
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_country_choose
import eu.tintera.plume.resources.auth_country_label
import eu.tintera.plume.resources.auth_input_password
import eu.tintera.plume.resources.auth_input_phone
import eu.tintera.plume.resources.auth_password_hide
import eu.tintera.plume.resources.auth_password_show
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.visibility_24dp
import eu.tintera.plume.resources.visibility_off_24dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pole pro kód, který není číslo (slovo nebo fráze z SMS). Číselný kód má vlastní políčka,
 * viz [CodeField], a heslo vlastní blok, viz [PasswordField].
 */
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource,
    keyboardType: KeyboardType,
    isPassword: Boolean,
    isError: Boolean,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        // Do kódu ani do hesla nemá klávesnice co opravovat — slovní kód by z „hokej" udělala
        // „hokeje" a uživatel by nepoznal proč.
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        textStyle = MaterialTheme.typography.bodyLarge.tabularNumbers(),
        modifier = Modifier.fillMaxWidth(),
    )
}


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
internal fun PhoneField(
    phone: PhoneEntry,
    isError: Boolean,
    onPhoneChange: (String) -> Unit,
    onPickCountry: () -> Unit,
    onSubmit: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    value = phone.digits,
                    onValueChange = onPhoneChange,
                    // Nápověda, ne plovoucí label: ten po vyplnění odskočí nahoru a text
                    // s ním klesne, takže by číslo přestalo lícovat s předvolbou vedle —
                    // a ta label nemá a mít nemůže.
                    placeholder = { Text(stringResource(Res.string.auth_input_phone)) },
                    singleLine = true,
                    isError = isError,
                    // Hodnotou jsou syrové číslice a oddělovače dodává až zobrazení — jinak by
                    // kurzor skákal pokaždé, když formátování vloží mezeru.
                    visualTransformation = PhoneNumberTransformation(phone.formatted),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
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
 * Mapování pozic se počítá z [formatted]: `originalToTransformed(n)` je místo ZA n-tou číslicí,
 * `transformedToOriginal(n)` je počet číslic před pozicí n. Kdyby formátování nesedělo
 * s počtem číslic (jiná země dorazí o snímek později), transformace se vypne — pole, které
 * ukazuje něco jiného, než co v něm je, je horší než pole bez mezer.
 */
internal class PhoneNumberTransformation(private val formatted: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (formatted.count { it.isDigit() } != digits.length || formatted == digits) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        return TransformedText(AnnotatedString(formatted), PhoneOffsetMapping(formatted, digits.length))
    }
}

internal class PhoneOffsetMapping(
    private val formatted: String,
    private val digitCount: Int,
) : OffsetMapping {

    override fun originalToTransformed(offset: Int): Int {
        if (offset <= 0) return 0
        if (offset >= digitCount) return formatted.length
        var seen = 0
        formatted.forEachIndexed { index, char ->
            if (char.isDigit()) {
                seen++
                if (seen == offset) return index + 1
            }
        }
        return formatted.length
    }

    override fun transformedToOriginal(offset: Int): Int =
        formatted.take(offset.coerceIn(0, formatted.length)).count { it.isDigit() }
}

