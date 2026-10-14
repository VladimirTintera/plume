package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable

/**
 * Oprávnění kreslit ukazovátko protistrany přes jiné aplikace (Android: „Zobrazení přes
 * jiné aplikace", zapíná se v Nastavení systému, ne dotazem). `null` = tady není potřeba
 * nebo není k dispozici.
 */
interface PointerOverlayPermission {
    val granted: Boolean

    /**
     * Otevře nastavení oprávnění; [onDone] po návratu, ať je povolené, nebo ne. Od Androidu 11
     * systém balíček v intentu ignoruje a otevře seznam všech aplikací — Plume v něm uživatel
     * najde sám (říká to text dialogu).
     */
    fun request(onDone: () -> Unit)
}

@Composable
expect fun rememberPointerOverlayPermission(): PointerOverlayPermission?

/** Ptát se na oprávnění jednou za běh aplikace: kdo ho odmítl, nemá ho dostávat u každého sdílení. */
internal object PointerOverlayAsk {
    var asked = false
}
