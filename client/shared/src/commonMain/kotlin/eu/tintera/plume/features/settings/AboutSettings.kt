package eu.tintera.plume.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.logging.appLogsAvailable
import eu.tintera.plume.core.logging.revealAppLogs
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.platformDeviceModel
import eu.tintera.plume.core.telegram.platformSystemVersion
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.TitledGroup
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.update.AppUpdateState
import eu.tintera.plume.core.update.AppUpdates
import eu.tintera.plume.core.update.UpdateCheckResult
import eu.tintera.plume.core.update.rememberStartUpdate
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.about_app_logs
import eu.tintera.plume.resources.about_app_logs_summary
import eu.tintera.plume.resources.about_check_update
import eu.tintera.plume.resources.about_disclaimer
import eu.tintera.plume.resources.about_license
import eu.tintera.plume.resources.about_license_summary
import eu.tintera.plume.resources.about_open_source
import eu.tintera.plume.resources.about_privacy
import eu.tintera.plume.resources.about_privacy_summary
import eu.tintera.plume.resources.about_report_bug
import eu.tintera.plume.resources.about_report_bug_summary
import eu.tintera.plume.resources.about_source
import eu.tintera.plume.resources.about_tagline
import eu.tintera.plume.resources.about_tdlib
import eu.tintera.plume.resources.about_tdlib_summary
import eu.tintera.plume.resources.license_24dp
import eu.tintera.plume.resources.licenses_summary
import eu.tintera.plume.resources.licenses_title
import eu.tintera.plume.resources.database_24dp
import eu.tintera.plume.resources.about_update_checking
import eu.tintera.plume.resources.about_update_failed
import eu.tintera.plume.resources.about_update_idle
import eu.tintera.plume.resources.about_update_unsupported
import eu.tintera.plume.resources.about_update_up_to_date
import eu.tintera.plume.resources.about_version
import eu.tintera.plume.resources.about_version_copied
import eu.tintera.plume.resources.about_version_copy
import eu.tintera.plume.resources.bug_report_24dp
import eu.tintera.plume.resources.code_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.info_24dp
import eu.tintera.plume.resources.open_in_new_24dp
import eu.tintera.plume.resources.plume_logo_24dp
import eu.tintera.plume.resources.shield_person_24dp
import eu.tintera.plume.resources.system_update_24dp
import eu.tintera.plume.resources.update_action_restart
import eu.tintera.plume.resources.update_action_update
import eu.tintera.plume.resources.update_available_text
import eu.tintera.plume.resources.update_available_title
import eu.tintera.plume.resources.update_downloaded_text
import eu.tintera.plume.resources.update_downloaded_title
import eu.tintera.plume.resources.update_downloading_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * „O aplikaci" ve skupině Pomoc: logo, verze aplikace a TDLib, ruční kontrola aktualizace, odkazy
 * na zdrojáky, hlášení chyb (na desktopu i složka se záznamy o pádech), soukromí a licenci a podstránka Licence knihoven. Na konci věta, že Plume není oficiální produkt Telegramu —
 * podmínky Telegram API to po neoficiálních klientech chtějí a README ji má taky.
 *
 * - **Verze jde zkopírovat i s telefonem a systémem**, ve stejném tvaru jako hlavička deníku
 *   hovorů. Kdo hlásí chybu, potřebuje právě tohle, a z obrazovky by to přepisoval ručně.
 * - **Kontrola aktualizace jen tam, kde to jde zjistit** ([AppUpdates.canCheck]): iOS API nemá
 *   a ladicí běh desktopu se s vydáními nemá s čím porovnat. Řádek prochází týmiž stavy jako
 *   karta v seznamu chatů (nabídka, stahování, restart), takže zavřená karta tu nabídku nezruší.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AboutSettings(snackbarHostState: SnackbarHostState, onOpenLicenses: () -> Unit) {
    val paths = koinInject<TelegramPaths>()
    val session = koinInject<TelegramSession>()
    // Verzi hlásí klient TDLib sám; do té doby (a když ji neřekne) řádek není.
    val tdlib by produceState<String?>(initialValue = null, session) { value = session.tdlibVersion() }
    val urlOpener = koinInject<UrlOpener>()
    val clipboard = koinInject<TextClipboard>()
    val updates = koinInject<AppUpdates>()
    val scope = rememberCoroutineScope()

    AboutHeader()

    GroupedColumn(Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.info_24dp), contentDescription = null) },
            supportingContent = { Text(stringResource(Res.string.about_version_copy)) },
            modifier = Modifier.clickable {
                scope.launch {
                    val library = tdlib?.let { ", TDLib $it" }.orEmpty()
                    clipboard.copy("$APP_NAME ${paths.applicationVersion}$library, $platformDeviceModel, $platformSystemVersion")
                    if (!clipboard.confirmsCopy) snackbarHostState.showSnackbar(getString(Res.string.about_version_copied))
                }
            },
        ) { Text(stringResource(Res.string.about_version, paths.applicationVersion)) }
        tdlib?.let { version ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(Res.drawable.database_24dp), contentDescription = null) },
                supportingContent = { Text(stringResource(Res.string.about_tdlib_summary)) },
            ) { Text(stringResource(Res.string.about_tdlib, version)) }
        }
        if (updates.canCheck) UpdateRow(updates)
    }

    TitledGroup(Res.string.about_open_source) {
        LinkRow(Res.drawable.code_24dp, Res.string.about_source, SOURCE_LABEL) { urlOpener.open(SOURCE_URL) }
        LinkRow(Res.drawable.bug_report_24dp, Res.string.about_report_bug, stringResource(Res.string.about_report_bug_summary)) {
            urlOpener.open(ISSUES_URL)
        }
        // Desktop: složka s logem a záznamy o pádech (DesktopLogs), Crashlytics tam není.
        if (appLogsAvailable) {
            LinkRow(Res.drawable.folder_24dp, Res.string.about_app_logs, stringResource(Res.string.about_app_logs_summary)) {
                revealAppLogs()
            }
        }
        LinkRow(Res.drawable.shield_person_24dp, Res.string.about_privacy, stringResource(Res.string.about_privacy_summary)) {
            urlOpener.open(PRIVACY_URL)
        }
        LinkRow(Res.drawable.description_24dp, Res.string.about_license, stringResource(Res.string.about_license_summary)) {
            urlOpener.open(LICENSE_URL)
        }
        // Podstránka v aplikaci, ne odkaz ven — proto bez ikony vpravo.
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.license_24dp), contentDescription = null) },
            supportingContent = { Text(stringResource(Res.string.licenses_summary)) },
            modifier = Modifier.clickable(onClick = onOpenLicenses),
        ) { Text(stringResource(Res.string.licenses_title)) }
    }

    Text(
        stringResource(Res.string.about_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/**
 * Pírko z ikony aplikace v sušence barev motivu, jméno a čím aplikace je. Devítistranná sušenka
 * jinde v aplikaci nic neznamená (dvanáctistranná je „vybrané", viz `ShapeMorphs`).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutHeader() {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .padding(bottom = 12.dp)
                .size(LOGO_SIZE)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(Res.drawable.plume_logo_24dp),
                // Jméno stojí hned pod logem, čtečka by ho četla dvakrát.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(LOGO_SIZE / 2),
            )
        }
        Text(APP_NAME, style = MaterialTheme.typography.headlineMediumEmphasized)
        Text(
            stringResource(Res.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Řádek aktualizace. Ťuknutí udělá, co stav nabízí: zkontrolovat, stáhnout, nebo restartovat;
 * u nabídky a restartu je pro jistotu i tlačítko, ať je vidět, že se ťuknutím něco spustí.
 *
 * Výsledek kontroly se nepamatuje přes odchod z obrazovky — „máš nejnovější verzi" platí teď,
 * ne zítra. Stav aktualizace (nabídka, stahování) drží [AppUpdates] sám.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateRow(updates: AppUpdates) {
    val state by updates.state.collectAsStateWithLifecycle()
    val startUpdate = rememberStartUpdate(updates)
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateCheckResult?>(null) }

    val check: () -> Unit = {
        if (!checking) {
            checking = true
            scope.launch {
                result = updates.checkNow()
                checking = false
            }
        }
    }

    val current = state
    val title: StringResource
    val summary: StringResource?
    val onClick: () -> Unit
    when (current) {
        is AppUpdateState.Available -> {
            title = Res.string.update_available_title
            summary = Res.string.update_available_text
            onClick = startUpdate
        }
        is AppUpdateState.Downloading -> {
            title = Res.string.update_downloading_title
            summary = null
            onClick = {}
        }
        is AppUpdateState.Downloaded -> {
            title = Res.string.update_downloaded_title
            summary = Res.string.update_downloaded_text
            onClick = updates::complete
        }
        AppUpdateState.None -> {
            title = Res.string.about_check_update
            summary = when {
                checking -> Res.string.about_update_checking
                result == UpdateCheckResult.UpToDate -> Res.string.about_update_up_to_date
                result == UpdateCheckResult.Unsupported -> Res.string.about_update_unsupported
                result == UpdateCheckResult.Failed -> Res.string.about_update_failed
                else -> Res.string.about_update_idle
            }
            onClick = check
        }
    }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.system_update_24dp), contentDescription = null) },
        supportingContent = summary?.let {
            // Výsledek kontroly přijde až po chvíli — čtečka obrazovky ho má oznámit sama.
            { Text(stringResource(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        },
        trailingContent = when (current) {
            is AppUpdateState.Available -> {
                { ActionButton(Res.string.update_action_update, startUpdate) }
            }
            is AppUpdateState.Downloaded -> {
                { ActionButton(Res.string.update_action_restart, updates::complete) }
            }
            // Průběh v kolečku: z neurčitého čekání se plynule stane průběh, tvar by přeskočil.
            is AppUpdateState.Downloading -> {
                {
                    val progress = current.progress
                    if (progress == null) {
                        CircularProgressIndicator(Modifier.size(PROGRESS_SIZE))
                    } else {
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(PROGRESS_SIZE))
                    }
                }
            }
            AppUpdateState.None -> if (checking) {
                { LoadingIndicator(Modifier.size(LOADING_SIZE)) }
            } else {
                null
            }
        },
        modifier = Modifier.clickable(enabled = current !is AppUpdateState.Downloading, onClick = onClick),
    ) { Text(stringResource(title)) }
}

@Composable
private fun ActionButton(label: StringResource, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) { Text(stringResource(label)) }
}

/** Odkaz ven: ikona vpravo říká, že se otevře prohlížeč, ne další obrazovka Nastavení. */
@Composable
private fun LinkRow(icon: DrawableResource, title: StringResource, summary: String, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(painterResource(Res.drawable.open_in_new_24dp), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    ) { Text(stringResource(title)) }
}

/** Jméno aplikace se nepřekládá. */
private const val APP_NAME = "Plume"

private const val SOURCE_URL = "https://github.com/VladimirTintera/plume"
private const val SOURCE_LABEL = "github.com/VladimirTintera/plume"
private const val ISSUES_URL = "$SOURCE_URL/issues"

/** Oddíl Privacy v README: s jakými službami aplikace mluví. Vlastní zásady soukromí nemá. */
private const val PRIVACY_URL = "$SOURCE_URL#privacy"
private const val LICENSE_URL = "$SOURCE_URL/blob/main/LICENSE"

private val LOGO_SIZE = 96.dp
private val PROGRESS_SIZE = 24.dp
private val LOADING_SIZE = 32.dp
