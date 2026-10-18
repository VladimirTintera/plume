package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_country_choose
import eu.tintera.plume.resources.auth_country_empty
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.auth_country_search
import eu.tintera.plume.resources.search_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Výběr země: předvolba při přihlášení a země, ze kterých se smí hlasovat v anketě kanálu.
 * Sheet, ne vlastní obrazovka: přihlášení žádnou navigaci nemá a kvůli jednomu seznamu by se
 * kolem něj musela stavět.
 *
 * **Jeden výběr pro obojí**, proto bydlí v `core/ui`, ne u přihlášení — formulář ankety by jinak
 * závisel na balíčku přihlášení. S [multiSelect] se sheet po ťuknutí nezavírá, řádek místo
 * předvolby nese fajfku a [canPick] zašedí země nad stropem (vybrané jdou odebrat vždycky).
 *
 * **Rovnou celý, bez napůl rozbaleného stavu.** Zemí je přes dvě stě, takže napůl rozbalený
 * sheet ukáže hrstku řádků a pole hledání schová pod okraj — tatáž past jako u výběru samolepek.
 */
@Composable
fun CountryPickerSheet(
    countries: List<Country>,
    selectedCodes: Set<String>,
    onPick: (Country) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(Res.string.auth_country_choose),
    supportingText: String? = null,
    multiSelect: Boolean = false,
    canPick: (Country) -> Boolean = { true },
) {
    val sheetState = rememberExpandedSheetState()
    var query by rememberSaveable { mutableStateOf("") }

    // Hledá se bez ohledu na diakritiku, jako všude jinde v aplikaci: „svycarsko" musí najít
    // „Švýcarsko". Přes `derivedStateOf`, ať psaní nefiltruje seznam v každé rekompozici znovu.
    val matches by remember(countries) {
        derivedStateOf { countries.matching(query) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            SheetTitle(title, modifier = Modifier.padding(horizontal = 24.dp))
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(Res.string.auth_country_search)) },
                leadingIcon = { Icon(painterResource(Res.drawable.search_24dp), contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            )

            HorizontalDivider()

            if (matches.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(LIST_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(Res.string.auth_country_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(LIST_HEIGHT)) {
                    items(matches, key = { it.code }) { country ->
                        val isSelected = country.code in selectedCodes
                        CountryRow(
                            country = country,
                            isSelected = isSelected,
                            multiSelect = multiSelect,
                            enabled = isSelected || canPick(country),
                            onClick = { onPick(country) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(country: Country, isSelected: Boolean, multiSelect: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    }
    ListItem(
        headlineContent = { Text(country.name) },
        // Vlajka je emoji, ne obrázek — kreslí ji systémové písmo, takže se nic nestahuje
        // a nedrží se vlastní sada ikon pro dvě stě zemí.
        leadingContent = { Text(country.flagEmoji, style = MaterialTheme.typography.headlineSmall) },
        trailingContent = {
            when {
                // V anketě jde o zemi, ne o předvolbu — ta by v řádku jen mátla.
                multiSelect -> if (isSelected) Icon(painterResource(Res.drawable.check_24dp), contentDescription = null)
                else -> Text(
                    country.callingCode,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        // Popředí vybraného řádku se musí předat i ListItem — sám barví text `onSurface`, což
        // v tmavém tématu se zvýšeným kontrastem (světlý `secondaryContainer`) není vidět.
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            contentColor = contentColor,
        ),
        // Klikání nese celý řádek, ne jen text — stejně jako řádky v Nastavení. Čtečka obrazovky
        // pozná výběr: jedna země jako přepínač, víc zemí jako zaškrtávátka.
        modifier = Modifier.fillMaxWidth().selectable(
            selected = isSelected,
            enabled = enabled,
            role = if (multiSelect) Role.Checkbox else Role.RadioButton,
            onClick = onClick,
        ),
    )
}

/**
 * Země, které odpovídají dotazu: podle názvu (bez ohledu na diakritiku), podle **anglického
 * názvu** a podle předvolby, ať se dá napsat rovnou „+420". Prázdný dotaz vrací všechno.
 *
 * Anglický název je tam proto, že názvy překládá systém — kdo zná zemi jen anglicky, by ji
 * v českém seznamu jinak nenašel („Germany" vs. „Německo").
 */
internal fun List<Country>.matching(query: String): List<Country> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this

    val folded = foldDiacritics(trimmed).lowercase()
    val digits = trimmed.removePrefix("+").filter { it.isDigit() }
    return filter { country ->
        foldDiacritics(country.name).lowercase().contains(folded) ||
            foldDiacritics(country.englishName).lowercase().contains(folded) ||
            (digits.isNotEmpty() && country.callingCode.removePrefix("+").startsWith(digits))
    }
}

private val LIST_HEIGHT = 420.dp

/** Zašedlá země nad stropem výběru — průhlednost vypnutého obsahu podle M3. */
private const val DISABLED_ALPHA = 0.38f
