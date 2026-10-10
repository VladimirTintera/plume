package eu.tintera.plume.features.lock

import androidx.compose.runtime.Composable

/**
 * Vlastní okno pro obrazovku zámku, nad VŠÍM, co aplikace právě ukazuje.
 *
 * Vrstva v hlavním okně by nestačila: `Dialog`, `ModalBottomSheet` i `Popup` mají vlastní okno
 * (na iOS vlastní vrstvu) nad tím hlavním. Sheet otevřený před odchodem do pozadí by po návratu
 * ležel NAD zámkem a dal se dál ovládat — třeba výběr chatu pro přeposlání. Okno zámku vzniká až
 * při zamčení, takže je nad nimi.
 *
 * [onBack] je systémové zpět (Android); minimalizuje aplikaci, zámek se zpět obejít nesmí.
 */
@Composable
expect fun LockWindow(onBack: () -> Unit, content: @Composable () -> Unit)
