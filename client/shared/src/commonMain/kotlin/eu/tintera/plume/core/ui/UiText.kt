package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Text pro uživatele, který vzniká **mimo kompozici** — ve ViewModelu, v doménovém stavu nebo
 * při převodu zpráv v `core/telegram`.
 *
 * `stringResource` je `@Composable`, takže se ve ViewModelu zavolat nedá. `StringResource` je
 * ale jen odkaz do prostředků, ne hotový řetězec: nepotřebuje Compose runtime a dá se nosit
 * ve stavu. Přeloží se až v UI přes [resolve], tedy v jazyce, který zrovna platí — text
 * poskládaný dřív by po změně jazyka zůstal v tom starém. Přesně to se stávalo servisním
 * zprávám a náhledům v seznamu chatů: proces změnu jazyka přežije a hotový řetězec v paměti
 * s ním.
 *
 * Argumenty ([Resource.args], [Plural.args]) se drží zvlášť, protože je nemá smysl formátovat
 * dřív, než se ví, která jazyková varianta se použije. **Argument smí být sám [UiText]** —
 * přeloží se spolu s větou, do které patří („Automatické mazání zpráv: 1 den").
 */
sealed interface UiText {

    /** Věta z prostředků. */
    data class Resource(val resource: StringResource, val args: List<Any> = emptyList()) : UiText

    /** Věta s počtem; čeština má tři tvary, angličtina dva — rozhodne jazyk při překladu. */
    data class Plural(
        val resource: PluralStringResource,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /**
     * Text, který se nepřekládá — napsal ho uživatel, nebo přišel ze serveru (popisek fotky,
     * důvod omezení obsahu). Stojí tu proto, aby jedno pole mohlo nést „popisek, a když není,
     * slovo Fotka" a nemuselo se rozpadnout na dvě.
     */
    data class Raw(val text: String) : UiText
}

/** Věta z prostředků; zkratka pro [UiText.Resource]. */
fun UiText(resource: StringResource, vararg args: Any): UiText = UiText.Resource(resource, args.toList())

/** Věta s počtem; zkratka pro [UiText.Plural]. */
fun UiText(resource: PluralStringResource, quantity: Int, vararg args: Any): UiText =
    UiText.Plural(resource, quantity, args.toList())

/** Přeloží [UiText] na řetězec v jazyce, který zrovna platí. */
@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Resource -> stringResource(resource, *args.map { (it as? UiText)?.resolve() ?: it }.toTypedArray())
    is UiText.Plural -> pluralStringResource(resource, quantity, *args.map { (it as? UiText)?.resolve() ?: it }.toTypedArray())
    is UiText.Raw -> text
}

/**
 * Přeloží [UiText] mimo kompozici — pro snackbar, notifikaci, schránku nebo prompt modelu.
 * Výsledek je v jazyce platném TEĎ, a proto se nesmí nikam ukládat na později.
 */
suspend fun UiText.resolveNow(): String = when (this) {
    is UiText.Resource -> readString(resource, *args.map { (it as? UiText)?.resolveNow() ?: it }.toTypedArray())
    is UiText.Plural -> readPluralString(resource, quantity, *args.map { (it as? UiText)?.resolveNow() ?: it }.toTypedArray())
    is UiText.Raw -> text
}

/** Tenhle text, a když je prázdný, [fallback] z prostředků. */
fun String.orText(fallback: StringResource): UiText = if (isBlank()) UiText(fallback) else UiText.Raw(this)
