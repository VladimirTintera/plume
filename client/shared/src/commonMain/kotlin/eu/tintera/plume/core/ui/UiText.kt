package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Text pro uživatele, který vzniká **mimo kompozici** — ve ViewModelu nebo v doménovém stavu.
 *
 * `stringResource` je `@Composable`, takže se ve ViewModelu zavolat nedá. `StringResource` je
 * ale jen odkaz do prostředků, ne hotový řetězec: nepotřebuje Compose runtime a dá se nosit
 * ve stavu. Přeloží se až v UI přes [resolve], tedy v jazyce, který zrovna platí — text
 * poskládaný ve ViewModelu by po změně jazyka zůstal v tom starém.
 *
 * [args] jsou hodnoty do `%1$s` / `%1$d`. Drží se zvlášť právě proto, že je nemá smysl formátovat
 * dřív, než se ví, která jazyková varianta se použije.
 */
data class UiText(
    val resource: StringResource,
    val args: List<Any> = emptyList(),
) {
    constructor(resource: StringResource, vararg args: Any) : this(resource, args.toList())
}

/** Přeloží [UiText] na řetězec v jazyce, který zrovna platí. */
@Composable
fun UiText.resolve(): String = stringResource(resource, *args.toTypedArray())
