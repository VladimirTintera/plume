package eu.tintera.plume.core.ui

import androidx.compose.ui.text.TextStyle

/**
 * Číslice stejné šířky. Pro čísla, která se mění na místě (čas hlasovky, počet nepřečtených,
 * „N z M") nebo stojí pod sebou (časy v seznamu chatů) — s proporčními číslicemi text při každé
 * změně poskočí do strany, protože „1" je užší než „0".
 */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
