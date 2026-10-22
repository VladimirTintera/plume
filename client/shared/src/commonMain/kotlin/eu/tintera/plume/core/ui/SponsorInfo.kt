package eu.tintera.plume.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.sponsored_info
import org.jetbrains.compose.resources.stringResource

/**
 * „Informace o sponzorovi" u reklamy: kdo ji platí (`sponsor.info`) a doplňující text od
 * Telegramu (`additional_info`, třeba na čem je reklama založená). Podle
 * [specifikace](https://core.telegram.org/api/sponsored-messages) je klient musí nabídnout, kdykoli
 * je aspoň jedno vyplněné. Prázdný výsledek = volba se nenabízí.
 */
fun sponsorInfoText(sponsorInfo: String?, additionalInfo: String?): String =
    listOfNotNull(sponsorInfo, additionalInfo).filter { it.isNotBlank() }.joinToString("\n\n")

/** Dialog s [sponsorInfoText] — společný pro reklamu v kanálu, u bota i ve videu. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SponsorInfoDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sponsored_info)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.common_close))
            }
        },
    )
}
