package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Jestli má popup zavírat zpět sám (`PopupProperties.dismissOnBackPress`), nebo to obstará
 * [PopupBackHandler].
 *
 * Android 13+ ne: popup si tam zpět registruje s nejvyšší prioritou (`PRIORITY_OVERLAY`), takže
 * předběhne i klávesnici — první zpět zavřel popup i s ní, místo aby schoval jen klávesnici.
 * A registruje ho jen při připojení okna, pozdější změnu vlastností už nepřeregistruje, takže
 * přepínat `dismissOnBackPress` podle klávesnice nejde.
 */
expect val popupDismissesOnBack: Boolean

/**
 * Zpět v okně popupu, kde [popupDismissesOnBack] neplatí. Volá se UVNITŘ obsahu popupu — jen
 * tam se dá sáhnout na jeho okno; `NavigationBackHandler` jde přes dispatcher aktivity, ke
 * kterému se zpět z okna popupu nedostane.
 */
@Composable
expect fun PopupBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * Zpět v popupu se vstupním polem: napřed schová klávesnici, teprve další zpět zavolá [onBack].
 *
 * Callback je zapnutý pořád a rozhoduje až ve chvíli zpět (klávesnice vidět → schovat, jinak
 * [onBack]). Nezáleží tak na tom, do kterého okna si klávesnice registruje vlastní zpět, ani na
 * tom, kdy během gesta zhasne `isImeVisible`. Klávesa zpět jde napřed do klávesnice sama.
 *
 * iOS zpět nemá, tam se nedělá nic.
 */
@Composable
expect fun PopupBackAfterKeyboard(onBack: () -> Unit)
