package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GroupEmptyRow
import eu.tintera.plume.core.ui.GroupTitle
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.about_version
import eu.tintera.plume.resources.licenses_intro
import eu.tintera.plume.resources.licenses_load_failed
import eu.tintera.plume.resources.licenses_open_terms
import eu.tintera.plume.resources.licenses_title
import eu.tintera.plume.resources.licenses_website
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private sealed interface LicensesState {
    data object Loading : LicensesState
    data class Loaded(val libraries: List<LibraryEntry>) : LicensesState
    data object Failed : LicensesState
}

/**
 * Licence knihoven: podstránka nad sekcí O aplikaci. Jeden blok řádků (jméno, verze, licence),
 * ťuknutí otevře sheet s textem licence.
 *
 * - **Žádný ViewModel**: seznam je neměnný soubor v aplikaci, stav nedrží nic, co by mělo přežít
 *   víc než obrazovku. Načte se znovu při každém otevření (desítky milisekund).
 * - **Otevřený sheet drží `remember` tady**, ne ViewModel ani `rememberSaveable` — viz
 *   „Otevřený sheet je stav obrazovky" v `docs/poznatky/seznam-chatu.md`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val scrollBehavior = settingsScrollBehavior()
    var state by remember { mutableStateOf<LicensesState>(LicensesState.Loading) }
    var opened by remember { mutableStateOf<LibraryEntry?>(null) }

    LaunchedEffect(Unit) {
        state = runCatching { loadLicenses() }
            .onFailure { Logger.withTag("Licenses").w(it) { "Seznam licencí se nepodařilo načíst." } }
            .fold(onSuccess = { LicensesState.Loaded(it) }, onFailure = { LicensesState.Failed })
    }

    Scaffold(
        modifier = Modifier.settingsScroll(scrollBehavior),
        topBar = { SettingsTopBar(title = stringResource(Res.string.licenses_title), onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        when (val current = state) {
            LicensesState.Loading -> Box(Modifier.fillMaxSize().settingsContentPadding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            LicensesState.Failed -> Column(Modifier.settingsContentPadding(padding).padding(16.dp)) {
                GroupEmptyRow(stringResource(Res.string.licenses_load_failed))
            }
            is LicensesState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize().settingsContentPadding(padding),
                contentPadding = PaddingValues(top = 8.dp, bottom = settingsContentBottom(padding, 24.dp)),
            ) {
                item(key = "intro") {
                    Text(
                        stringResource(Res.string.licenses_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 12.dp),
                    )
                }
                val libraries = current.libraries
                itemsIndexed(libraries, key = { _, library -> library.id }) { index, library ->
                    LibraryRow(
                        library = library,
                        modifier = Modifier.groupedItem(index, libraries.size, MaterialTheme.colorScheme.groupRow),
                        onClick = { opened = library },
                    )
                }
            }
        }
    }

    opened?.let { library -> LicenseSheet(library, onDismiss = { opened = null }) }
}

@Composable
private fun LibraryRow(library: LibraryEntry, modifier: Modifier, onClick: () -> Unit) {
    // Verze a licence pod jménem: kdo hledá, pod čím knihovna je, nemusí otevírat sheet.
    val summary = listOfNotNull(library.version, library.licenses.joinToString { it.name }.takeIf { it.isNotEmpty() })
        .joinToString(SUMMARY_SEPARATOR)
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        supportingContent = summary.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        modifier = modifier.clickable(onClick = onClick),
    ) { Text(library.name) }
}

/**
 * Knihovna a text jejích licencí. Licence bez textu (proprietární podmínky Googlu) nabídne odkaz;
 * text se nezkracuje — LGPL i s přiloženou GPL má desítky kB a právě ten se tu má dát přečíst.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LicenseSheet(library: LibraryEntry, onDismiss: () -> Unit) {
    val urlOpener = koinInject<UrlOpener>()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetTitle(library.name)
            library.version?.let {
                Text(
                    stringResource(Res.string.about_version, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            library.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            library.website?.let { website ->
                TextButton(onClick = { urlOpener.open(website) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.licenses_website))
                }
            }
            library.licenses.forEach { license ->
                Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING * 4)) {
                    GroupTitle(license.name, Modifier.padding(top = 8.dp))
                    val text = license.text
                    val url = license.url
                    when {
                        text != null -> Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                        url != null -> FilledTonalButton(
                            onClick = { urlOpener.open(url) },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) { Text(stringResource(Res.string.licenses_open_terms)) }
                    }
                }
            }
        }
    }
}

private const val SUMMARY_SEPARATOR = " · "
