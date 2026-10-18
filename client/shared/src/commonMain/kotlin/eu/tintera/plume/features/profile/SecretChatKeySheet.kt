package eu.tintera.plume.features.profile

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SecretChatKey
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.secret_chat_key_explanation
import eu.tintera.plume.resources.secret_chat_key_title
import org.jetbrains.compose.resources.stringResource

/**
 * Šifrovací klíč tajného chatu: obrázek a šestnáctková čísla k porovnání s protějškem. Když se
 * u obou shodují, nikdo mezi nimi klíč nepodvrhl. Věta říká, s kým a co porovnat — bez ní je to
 * jen ozdobný čtvereček.
 */
@Composable
internal fun SecretChatKeySheet(
    key: SecretChatKey,
    partnerName: String,
    onDismiss: () -> Unit,
) {
    // Celý hned: věta, s kým a co porovnat, je dole a napůl otevřený list ji schoval.
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 32.dp),
        ) {
            SheetTitle(stringResource(Res.string.secret_chat_key_title), modifier = Modifier.fillMaxWidth())
            SecretChatIdenticon(key, size = KEY_IMAGE_SIZE)
            // Šestnáctkově pevnou šířkou: skupiny pod sebou lícují a dají se číst po sloupcích.
            Text(
                text = key.hexLines.joinToString("\n"),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.secret_chat_key_explanation, partnerName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Obrázek otisku klíče. Barvy jsou z protokolu ([SecretChatKey.IDENTICON_PALETTE]), ne z motivu
 * — výjimka z pravidla „barvy jen z `colorScheme`": porovnává se s obrázkem v jiné aplikaci a ten
 * musí vypadat stejně. Čtečce obrazovky obrázek nic neřekne, čísla pod ním ano.
 */
@Composable
internal fun SecretChatIdenticon(key: SecretChatKey, size: Dp, modifier: Modifier = Modifier) {
    val palette = SecretChatKey.IDENTICON_PALETTE.map { Color(it) }
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 12))
            .clearAndSetSemantics { },
    ) {
        val cell = this.size.width / key.side
        key.pixels.forEachIndexed { index, color ->
            drawRect(
                color = palette[color],
                topLeft = Offset((index % key.side) * cell, (index / key.side) * cell),
                // Přesah o kousek: bez něj prosvítají mezi buňkami vlasové spáry z vyhlazování.
                size = Size(cell + 0.5f, cell + 0.5f),
            )
        }
    }
}

private val KEY_IMAGE_SIZE = 216.dp
