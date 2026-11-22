package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import eu.tintera.plume.core.ui.highlightAnchor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.SecurityBadge
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Kostra podstránky Nastavení, která není seznamem voleb, ale stránkou s obsahem: dvoufázové
 * ověření, zámek aplikace, přístupové klíče, přihlašovací e-mail, změna čísla, smazání účtu.
 * Velká hlavička, sloupec s okrajem 16 dp a mezerami 16 dp mezi kusy, spodní inset ve
 * scrollovaném obsahu ([settingsContentPadding]).
 *
 * **Proč jedna kostra:** každá stránka si sloupec skládala sama, a zámek aplikace se rozešel
 * se vším ostatním — vycentrovaný úvod přes celou výšku, okraj 24 dp, bez scrollu a bez
 * sbalování hlavičky, zatímco o řádek vedle stojící dvoufázové ověření mělo hlavičku vlevo
 * a bloky řádků. Vedle sebe to vypadalo jako dvě aplikace.
 *
 * [scrollable] `false` je pro obsah, který má vyplnit výšku a scrollovat nemá (číselník PINu);
 * hlavička se pak nesbaluje, protože není co.
 */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = if (scrollable) settingsScrollBehavior() else null
    Scaffold(
        modifier = modifier.fillMaxSize().settingsScroll(scrollBehavior),
        snackbarHost = snackbarHost,
        topBar = { SettingsTopBar(title = title, onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .settingsContentPadding(padding)
                .then(if (scrollable) Modifier.verticalScroll(rememberRestoringScrollState()) else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = settingsContentBottom(padding, 16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/**
 * Úvod stránky nebo kroku průvodce: odznak zabezpečení (jen na stránce stavu, ne v krocích),
 * nadpis a vysvětlení, zarovnané vlevo jako zbytek stránky. Nadpis říká stav nebo krok
 * („Dvoufázové ověření je zapnuté", „Nové heslo"); jméno stránky už nese hlavička nad ním.
 */
@Composable
internal fun SettingsPageHeader(
    title: String?,
    text: String?,
    icon: DrawableResource? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (icon != null) SecurityBadge(icon, size = 56.dp)
        if (title != null) Text(title, style = MaterialTheme.typography.headlineSmall)
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tlačítka kroku: hlavní akce a vedlejší textová. Tři se na úzký telefon do řádku nevejdou. */
@Composable
internal fun SettingsPageActions(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Načítání celé stránky, dokud není co ukázat. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsPageLoading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

/**
 * Řádek s akcí v bloku stránky (změnit heslo, změnit PIN). Nevratná akce (`destructive`) má
 * chybovou barvu a patří do vlastního bloku za `GroupBreak()` — jako v nabídkách, ne jako
 * barevné tlačítko pod blokem.
 */
@Composable
internal fun SettingsActionRow(
    icon: DrawableResource,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = color,
            leadingContentColor = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        modifier = Modifier.highlightAnchor(title).clickable(enabled = enabled, onClick = onClick),
    ) { Text(title) }
}
