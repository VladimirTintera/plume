package eu.tintera.plume.features.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_country_choose
import eu.tintera.plume.resources.auth_country_empty
import eu.tintera.plume.resources.auth_country_search
import eu.tintera.plume.resources.search_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Výběr země pro předvolbu. Sheet, ne vlastní obrazovka: přihlášení žádnou navigaci nemá
 * a kvůli jednomu seznamu by se kolem něj musela stavět.
 *
 * **Rovnou celý, bez napůl rozbaleného stavu.** Zemí je přes dvě stě, takže napůl rozbalený
 * sheet ukáže hrstku řádků a pole hledání schová pod okraj — tatáž past jako u výběru samolepek.
 */
@Composable
fun CountryPickerSheet(
    countries: List<Country>,
    selected: Country?,
    onPick: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var query by rememberSaveable { mutableStateOf("") }

    // Hledá se bez ohledu na diakritiku, jako všude jinde v aplikaci: „svycarsko" musí najít
    // „Švýcarsko". Přes `derivedStateOf`, ať psaní nefiltruje seznam v každé rekompozici znovu.
    val matches by remember(countries) {
        derivedStateOf { countries.matching(query) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.auth_country_choose),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

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
                        CountryRow(
                            country = country,
                            isSelected = country.code == selected?.code,
                            onClick = { onPick(country) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(country: Country, isSelected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(country.name) },
        // Vlajka je emoji, ne obrázek — kreslí ji systémové písmo, takže se nic nestahuje
        // a nedrží se vlastní sada ikon pro dvě stě zemí.
        leadingContent = { Text(country.flagEmoji, style = MaterialTheme.typography.headlineSmall) },
        trailingContent = {
            Text(
                country.callingCode,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        // Klikání nese celý řádek, ne jen text — stejně jako řádky v Nastavení.
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/**
 * Země, které odpovídají dotazu: podle názvu (bez ohledu na diakritiku) nebo podle předvolby,
 * ať se dá napsat rovnou „+420". Prázdný dotaz vrací všechno.
 */
internal fun List<Country>.matching(query: String): List<Country> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this

    val folded = foldDiacritics(trimmed).lowercase()
    val digits = trimmed.removePrefix("+").filter { it.isDigit() }
    return filter { country ->
        foldDiacritics(country.name).lowercase().contains(folded) ||
            (digits.isNotEmpty() && country.callingCode.removePrefix("+").startsWith(digits))
    }
}

private val LIST_HEIGHT = 420.dp
