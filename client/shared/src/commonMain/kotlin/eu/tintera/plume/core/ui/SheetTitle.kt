package eu.tintera.plume.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * **Nadpis modálního sheetu.** Jedna role pro všechny: `titleLargeEmphasized`.
 *
 * Do října 2026 měl každý sheet nadpis po svém — 25 `titleLarge`, 24 `titleMedium` — takže dva
 * sheety otevřené po sobě vypadaly jako z různých aplikací a menší z nich se ztrácel v obsahu.
 * Zvýrazněný řez (M3 Expressive) nadpis odliší od řádků pod ním, i když mají stejnou velikost.
 *
 * Odsazení si určuje sheet (jeho sloupec má vlastní okraje), tady je jen písmo. Výjimka jsou
 * sheety, kde je velký text hlavním obsahem (náhled pozvánky, Můj QR kód, složka s ikonou) —
 * ty mají `headline*`, protože to není hlavička, ale to, kvůli čemu se sheet otevřel.
 */
@Composable
fun SheetTitle(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLargeEmphasized,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
