package eu.tintera.plume.core.calls

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Zvonění hovoru.
 *
 * - **Příchozí hovor zvoní vyzváněcím tónem telefonu ve smyčce** a vibruje, dokud se nepřijme,
 *   neodmítne nebo nezruší. Notifikace hovoru je proto tichá — zvuk notifikace by zazněl
 *   jednou a krátce, jako u zprávy, a s tónem by se zdvojil.
 * - **Řídí se režimem zvonění**: tichý = nic, vibrace = jen vibrace, normální = tón (a vibrace,
 *   když je v systému zapnutá „Vibrace při vyzvánění"). **Režim Nerušit** (jiný filtr než
 *   „vše") zvonění vypne — o výjimkách pro volající rozhoduje systém u notifikace, a ta zůstává.
 * - **Odchozí hovor má vyzváněcí tón protistrany** („tůů") ve sluchátku, dokud protějšek
 *   nezvedne — bez něj nebylo poznat, že se vůbec vytáčí.
 */
class CallRinger(
    private val context: Context,
    private val calls: CallRepository,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("CallRinger")
    private val audio = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var vibrating = false
    private var ringback: ToneGenerator? = null

    fun start() {
        scope.launch {
            calls.active
                .map { call -> call?.phase?.takeIf { it == CallPhase.Ringing || it == CallPhase.Calling } }
                .distinctUntilChanged()
                .collect { phase ->
                    stopAll()
                    when (phase) {
                        CallPhase.Ringing -> ring()
                        CallPhase.Calling -> startRingback()
                        else -> Unit
                    }
                }
        }
    }

    private fun ring() {
        val filter = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        if (filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            logger.i { "Nerušit: hovor nezvoní." }
            return
        }
        when (audio?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> return
            AudioManager.RINGER_MODE_VIBRATE -> vibrate()
            else -> {
                playRingtone()
                if (vibrateWhenRinging()) vibrate()
            }
        }
    }

    private fun playRingtone() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: Settings.System.DEFAULT_RINGTONE_URI
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { logger.w(it) { "Vyzváněcí tón nejde přehrát." } }.getOrNull()
    }

    /** „Vibrace při vyzvánění" ze systému; bez ní (nebo nečitelné) se nevibruje. */
    private fun vibrateWhenRinging(): Boolean =
        runCatching { Settings.System.getInt(context.contentResolver, VIBRATE_WHEN_RINGING) == 1 }.getOrDefault(false)

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private fun vibrate() {
        val vibrator = vibrator()?.takeIf { it.hasVibrator() } ?: return
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()
        @Suppress("DEPRECATION") // VibrationAttributes až od API 33; pro vyzvánění stačí AudioAttributes.
        vibrator.vibrate(VibrationEffect.createWaveform(RING_PATTERN, 0), attributes)
        vibrating = true
    }

    private fun startRingback() {
        ringback = runCatching {
            // Režim hovoru už zapnula zvuková relace (`CallMedia.startAudio` při vytáčení), takže
            // tón jde do zvoleného výstupu hlasitostí hovoru. Mimo režim hovoru by ho systém
            // pustil jako tón DTMF a telefon na vibracích by ho ztlumil úplně (stopa s -inf dB).
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, RINGBACK_VOLUME).also { it.startTone(ToneGenerator.TONE_SUP_RINGTONE) }
        }.onFailure { logger.w(it) { "Vyzváněcí tón protistrany nejde přehrát." } }.getOrNull()
    }

    private fun stopAll() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        if (vibrating) vibrator()?.cancel()
        vibrating = false
        ringback?.let { runCatching { it.stopTone() }; it.release() }
        ringback = null
    }

    private companion object {
        /** `Settings.System.VIBRATE_WHEN_RINGING` je skryté API; klíč se čte jménem. */
        const val VIBRATE_WHEN_RINGING = "vibrate_when_ringing"

        /** Vteřinu vibrace, vteřinu pauza, dokola (index opakování 0). */
        val RING_PATTERN = longArrayOf(0, 1_000, 1_000)

        const val RINGBACK_VOLUME = 80
    }
}
