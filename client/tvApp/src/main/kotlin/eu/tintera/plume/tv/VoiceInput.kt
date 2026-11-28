package eu.tintera.plume.tv

import androidx.compose.ui.platform.LocalConfiguration
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.mic_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * Hlasové zadání textu přes systém (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`): na Google TV ho
 * obslouží aplikace Google vlastním okénkem a vrátí rozpoznaný text. `null`, když ho na zařízení
 * nic neobslouží — tlačítko pak nemá být vidět.
 *
 * Od Androidu 11 potřebuje `<queries>` v manifestu, jinak `resolveActivity` vrátí `null` vždycky.
 * **Jazyk se předává z aplikace** (`EXTRA_LANGUAGE`): na emulátoru s anglickým systémem a českou
 * aplikací poslouchalo rozpoznávání anglicky.
 */
fun interface VoiceInput {
    fun launch()
}

@Composable
fun rememberVoiceInput(prompt: String, onResult: (String) -> Unit): VoiceInput? {
    val context = LocalContext.current
    // Jazyk z `LocalConfiguration`, ne z `context.resources`: po změně jazyka se záměr složí znovu.
    val locale = LocalConfiguration.current.locales[0]
    val intent = remember(prompt, locale) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
    }
    val available = remember(intent) { intent.resolveActivity(context.packageManager) != null }
    val result by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf(String::isNotBlank)?.let(result)
        }
    }
    return remember(available, intent, launcher) {
        if (available) VoiceInput { runCatching { launcher.launch(intent) } } else null
    }
}

/** Kulaté tlačítko s mikrofonem vedle textového pole (hledání, psaní zprávy). */
@Composable
fun MicButton(voice: VoiceInput, contentDescription: String, modifier: Modifier = Modifier) {
    Surface(
        onClick = voice::launch,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(painterResource(Res.drawable.mic_24dp), contentDescription = contentDescription, modifier = Modifier.padding(12.dp).size(24.dp))
    }
}
