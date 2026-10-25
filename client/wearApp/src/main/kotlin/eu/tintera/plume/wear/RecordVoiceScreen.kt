package eu.tintera.plume.wear

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import eu.tintera.plume.resources.mic_off_24dp
import eu.tintera.plume.resources.wear_confirm_not_sent
import eu.tintera.plume.resources.wear_confirm_sent
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.audio.encodeWaveform
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.stop_24dp
import eu.tintera.plume.resources.wear_voice_failed
import eu.tintera.plume.resources.wear_voice_no_permission
import eu.tintera.plume.resources.wear_voice_record
import eu.tintera.plume.resources.wear_voice_stop
import eu.tintera.plume.resources.wear_voice_tap_to_record
import eu.tintera.plume.resources.wear_voice_tap_to_send
import eu.tintera.plume.resources.wear_voice_too_short
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val logger = Logger.withTag("WearVoice")

/** Jak často se čte úroveň mikrofonu pro waveform. */
private const val LEVEL_TICK_MS = 50L

/**
 * Nahrání hlasovky: ťuknutí spustí, druhé zastaví a pošle. Zahodit jde tahem zpět — nahrávka se
 * při odchodu z obrazovky smaže. O mikrofon se žádá až tady, ne při startu aplikace.
 */
@Composable
fun RecordVoiceScreen(chatId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val conversations = koinInject<ConversationRepository>()
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }
    var permitted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        denied = !granted
    }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<StringResource?>(null) }
    // Výsledek odeslání jako potvrzovací dialog; po jeho zmizení se obrazovka zavře. Dřív se
    // zavřela hned a nezdar šel jen do logu — hlasovka zmizela beze stopy.
    var sent by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) { if (!permitted) permission.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    LaunchedEffect(recording) {
        while (recording) {
            delay(LEVEL_TICK_MS)
            recorder.sampleLevel()
            elapsed = recorder.elapsedSeconds()
        }
    }

    if (denied) {
        MessageScreen(stringResource(Res.string.wear_voice_no_permission), Res.drawable.mic_off_24dp)
        return
    }

    fun toggle() {
        if (!recording) {
            problem = null
            runCatching { recorder.start() }
                .onSuccess {
                    elapsed = 0
                    recording = true
                }
                .onFailure {
                    logger.w(it) { "Nahrávání hlasovky na hodinkách se nespustilo." }
                    problem = Res.string.wear_voice_failed
                }
            return
        }
        recording = false
        val voice = recorder.stop()
        if (voice == null) {
            problem = Res.string.wear_voice_too_short
            return
        }
        scope.launch {
            val result = conversations.sendVoiceNote(
                chatId = chatId,
                path = voice.path,
                durationSeconds = voice.durationSeconds,
                waveform = encodeWaveform(voice.levels),
                replyToMessageId = null,
            ).onFailure { logger.w(it) { "Hlasovka z hodinek do chatu $chatId neodešla." } }
            sent = result.isSuccess
        }
    }

    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (recording) formatDuration(elapsed) else stringResource(Res.string.wear_voice_record),
                style = MaterialTheme.typography.titleMedium,
            )
            FilledIconButton(
                onClick = { if (permitted) toggle() else permission.launch(Manifest.permission.RECORD_AUDIO) },
                shapes = IconButtonDefaults.animatedShapes(),
                // Po odeslání (pod dialogem s výsledkem) už nic nespouštět.
                enabled = sent == null,
                modifier = Modifier.padding(vertical = 12.dp).size(72.dp),
            ) {
                Icon(
                    painterResource(if (recording) Res.drawable.stop_24dp else Res.drawable.mic_24dp),
                    contentDescription = stringResource(if (recording) Res.string.wear_voice_stop else Res.string.wear_voice_record),
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                stringResource(problem ?: if (recording) Res.string.wear_voice_tap_to_send else Res.string.wear_voice_tap_to_record),
                style = MaterialTheme.typography.bodySmall,
                color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    SuccessConfirmation(visible = sent == true, text = stringResource(Res.string.wear_confirm_sent), onDismiss = onDone)
    FailureConfirmation(visible = sent == false, text = stringResource(Res.string.wear_confirm_not_sent), onDismiss = onDone)
}

/** Délka „0:07"; číslice textem pro uživatele nejsou, překlad nepotřebují. */
internal fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
