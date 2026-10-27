package eu.tintera.plume.core.ui

import androidx.compose.ui.input.pointer.PointerIcon

/**
 * Kurzor nad obsahem, který jde myší posouvat tahem (přiblížená fotka v prohlížeči). Společné
 * API Compose zná jen šipku, ruku na odkaz, textový kurzor a kříž, takže ho dodává platforma:
 * Android „chytit" (`TYPE_GRAB`), desktop kurzor přesunu z AWT (ruku „chytit" AWT nemá), iOS
 * kurzor nemá vůbec.
 */
expect val PanPointerIcon: PointerIcon
