package eu.tintera.plume.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.Verification
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.verification_fake
import eu.tintera.plume.resources.verification_scam
import eu.tintera.plume.resources.verification_verified
import eu.tintera.plume.resources.verified_filled_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Značka za jménem chatu: ověřený účet ikonou, podvod a falešný účet štítkem se slovem.
 *
 * **Podvod je slovo, ne ikona.** Ikona s vykřičníkem by se dala přečíst jako „něco se
 * nepovedlo" nebo „nepřečtené"; slovo v chybové barvě řekne přesně, co Telegram o účtu ví.
 * Tak to dělají i oficiální klienti. Obrys místo plné plochy: štítek stojí v řádku vedle
 * jména a plná chybová plocha by přebila všechno ostatní na obrazovce.
 *
 * [accent] je barva ikony ověření — na tónované ploše (vybraný chat) ji volající ověří proti
 * ní, jako každý akcent (viz `accentOn`).
 */
@Composable
fun VerificationBadge(
    verification: Verification,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = VERIFIED_ICON_SIZE,
) {
    when (verification) {
        Verification.Verified -> Icon(
            painter = painterResource(Res.drawable.verified_filled_24dp),
            contentDescription = stringResource(Res.string.verification_verified),
            tint = accent,
            modifier = modifier.size(iconSize),
        )
        Verification.Scam, Verification.Fake -> {
            val color = MaterialTheme.colorScheme.error
            Text(
                text = stringResource(
                    if (verification == Verification.Scam) Res.string.verification_scam else Res.string.verification_fake,
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                modifier = modifier
                    .border(1.dp, color, RoundedCornerShape(BADGE_CORNER))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

private val VERIFIED_ICON_SIZE = 16.dp

private val BADGE_CORNER = 4.dp
