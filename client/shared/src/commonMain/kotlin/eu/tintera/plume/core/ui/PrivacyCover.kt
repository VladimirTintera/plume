package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Zakryje obsah aplikace v přepínači aplikací, dokud je [enabled] (zapnutý zámek aplikace).
 *
 * iOS: systém si při odchodu do pozadí vyfotí okno a snímek ukazuje v přepínači i při návratu,
 * dřív než aplikace stihne cokoli vykreslit. Zámek naskočí až po `didBecomeActive`, takže by na
 * snímku byla otevřená konverzace. Kryt (plocha v barvě [color]) se proto přidá nad okno už při
 * `willResignActive` a zmizí při `didBecomeActive`.
 *
 * Android: nic, tam stejnou službu dělá `FLAG_SECURE` ([SecureScreen]).
 */
@Composable
expect fun PrivacyCover(enabled: Boolean, color: Color)
