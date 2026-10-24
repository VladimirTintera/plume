package eu.tintera.plume.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Text
import androidx.wear.remote.interactions.RemoteActivityHelper
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.BotButton
import eu.tintera.plume.core.telegram.BotButtonKind
import eu.tintera.plume.core.telegram.BotKeyboard
import java.util.concurrent.Executors

private val logger = Logger.withTag("WearBotButtons")

/**
 * Tlačítka bota pod zprávou. Na kulatém displeji se vedle sebe nevejdou, proto jdou všechna pod
 * sebe přes šířku bubliny — v pořadí, jak je bot poskládal po řádcích. **Žádné se neschovává**
 * (i to, co hodinky neumí): schované by z klávesnice vykouslo kus a změnilo, co bot nabízí.
 *
 * Obrysová, ne tónová: tónová výplň má barvu příchozí bubliny (viz ankety). Na stisknutém se
 * točí indikátor a naráz se čeká jen u jednoho ([pressing]) — druhé ťuknutí by zpětné volání
 * poslalo podruhé a u kontrolní otázky s jedním pokusem by spálilo odpověď.
 */
@Composable
fun MessageBotKeyboard(keyboard: BotKeyboard, pressing: Pair<Int, Int>?, onPress: (row: Int, column: Int, button: BotButton) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keyboard.rows.forEachIndexed { row, buttons ->
            buttons.forEachIndexed { column, button ->
                val busy = pressing == row to column
                CompactButton(
                    onClick = { if (pressing == null) onPress(row, column, button) },
                    enabled = button.kind != BotButtonKind.Disabled,
                    colors = ButtonDefaults.outlinedButtonColors(),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = button.kind != BotButtonKind.Disabled),
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else Text(button.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
    }
}

/**
 * Otevře odkaz v prohlížeči spárovaného telefonu (`RemoteActivityHelper`) — web se na hodinkách
 * neotevírá. [onDone] dostane na hlavním vlákně, jestli to prošlo: potvrzení „Otevřeno
 * v telefonu" patří jen k úspěchu. Bez spárovaného telefonu (emulátor) budoucnost selže
 * s `RemoteIntentException`.
 */
fun openOnPhone(context: Context, url: String, onDone: (Boolean) -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    val main = ContextCompat.getMainExecutor(context)
    runCatching {
        val executor = Executors.newSingleThreadExecutor()
        val future = RemoteActivityHelper(context, executor).startRemoteActivity(intent, null)
        future.addListener(
            {
                val opened = runCatching { future.get() }
                    .onFailure { logger.w(it) { "Odkaz se v telefonu neotevřel." } }
                    .isSuccess
                main.execute { onDone(opened) }
                executor.shutdown()
            },
            executor,
        )
    }.onFailure {
        logger.w(it) { "Odkaz se do telefonu neposlal." }
        onDone(false)
    }
}
