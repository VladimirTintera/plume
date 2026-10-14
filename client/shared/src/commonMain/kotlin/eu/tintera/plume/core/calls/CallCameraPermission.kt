package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.audio.RecordPermission

/**
 * Oprávnění ke kameře pro videohovor (s mikrofonem — videohovor bez zvuku nedává smysl). Na
 * telefonech totéž jako u videokoleček; desktop kolečka nenatáčí (jejich oprávnění tam vždycky
 * odmítne), kameru hovoru ale má.
 */
@Composable
expect fun rememberCallCameraPermission(): RecordPermission

/**
 * Oprávnění k mikrofonu pro hovor. Na telefonech totéž jako u hlasovek; na macOS se ptá systém
 * hned při vytočení / přijetí, ne až uprostřed spojování.
 */
@Composable
expect fun rememberCallMicrophonePermission(): RecordPermission

/**
 * Je kamera pro hovor už povolená? Bez dotazu: náhled u zvonícího videohovoru se kvůli
 * oprávnění neptá — dotaz přijde až s „Přijmout".
 */
@Composable
expect fun rememberCallCameraGranted(): Boolean
