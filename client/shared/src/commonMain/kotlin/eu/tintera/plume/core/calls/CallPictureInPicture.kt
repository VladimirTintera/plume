package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Obraz v obraze pro videohovor: po odchodu z aplikace běží obraz dál v okénku nad ostatními
 * aplikacemi (jako u oficiálního klienta). Hlasový hovor okénko nemá — stačí mu notifikace.
 *
 * Android posílá do okénka celou aktivitu (viz `rememberPictureInPicture` v `core/video`),
 * takže obrazovka hovoru během [active] kreslí jen obraz. Jinde hovory nejsou a okénko taky ne.
 */
@Stable
interface CallPictureInPicture {
    /** Aplikace je právě v okénku. */
    val active: Boolean
}

/**
 * Okénko pro probíhající hovor. Do okénka se jde samo při odchodu z aplikace, jen když je
 * [enabled] (hovor běží a je v něm obraz). [aspectRatio] je poměr stran obrazu (šířka / výška),
 * akce v okénku jsou ztlumit / zapnout mikrofon ([muted]) a zavěsit.
 */
@Composable
expect fun rememberCallPictureInPicture(
    enabled: Boolean,
    aspectRatio: Float,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onHangUp: () -> Unit,
): CallPictureInPicture
