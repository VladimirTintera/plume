package eu.tintera.plume.core.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.AudioClips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

/**
 * Systémové rozpoznávání řeči V ZAŘÍZENÍ (`SpeechRecognizer.createOnDeviceSpeechRecognizer`),
 * se zvukem ze souboru.
 *
 * - **Až od Androidu 13**: zvuk z jiného zdroje než mikrofonu (`EXTRA_AUDIO_SOURCE`, roura
 *   s PCM) a dotaz na jazyky (`checkRecognitionSupport`) jsou z API 33. Starší telefon přepis
 *   systémem nemá.
 * - **Jen nainstalovaný jazyk** (`installedOnDeviceLanguages`): s podporovaným, ale nestaženým
 *   by rozpoznávání skončilo chybou — a kvůli přepisu se nic nestahuje.
 * - **Zvuk jako pro Gemmu**: PCM 16 kHz mono 16 bitů z [AudioClips] (dekóduje systém, OGG/Opus
 *   i MP4). Do roury se píše celý; zápis zbrzdí plná roura, takže tempo udává rozpoznávač.
 * - **Segmentovaná relace** (`EXTRA_SEGMENTED_SESSION` = zdroj zvuku): rozpoznávač přepisuje
 *   po větách až do konce zvuku, místo aby skončil první pauzou. Hotové úseky se skládají
 *   za sebe, rozpracovaný se k nim přidává jako průběh.
 * - **Všechno na hlavním vlákně** (`flowOn(Main)`): `SpeechRecognizer` se smí vytvářet a volat
 *   jen z něj.
 */
class SystemSpeechToText(private val context: Context) : SpeechToText {
    private val logger = Logger.withTag("SystemSpeech")

    override suspend fun canTranscribe(languageCode: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return withContext(Dispatchers.Main) {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                logger.d { "Rozpoznávání v zařízení systém nemá" }
                return@withContext false
            }
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            try {
                withTimeoutOrNull(SUPPORT_TIMEOUT_MS) { installed(recognizer, languageCode) } ?: false
            } finally {
                recognizer.destroy()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun installed(recognizer: SpeechRecognizer, languageCode: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            recognizer.checkRecognitionSupport(
                intent(languageCode),
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        logger.d {
                            "Jazyky: nainstalované ${support.installedOnDeviceLanguages}, " +
                                "podporované ${support.supportedOnDeviceLanguages}, stahují se ${support.pendingOnDeviceLanguages}"
                        }
                        val found = support.installedOnDeviceLanguages.any { it.startsWith(languageCode, ignoreCase = true) }
                        if (continuation.isActive) continuation.resume(found)
                    }

                    override fun onError(error: Int) {
                        logger.i { "Podporu jazyka $languageCode se nepodařilo zjistit (chyba $error)" }
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
        }

    override fun transcribe(path: String, languageCode: String): Flow<String> = callbackFlow {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { "Přepis ze souboru chce Android 13" }
        val pcm = withContext(Dispatchers.Default) { AudioClips.pcm16k(path) }
        val (read, write) = ParcelFileDescriptor.createPipe()
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val done = StringBuilder()

        fun Bundle.best(): String? =
            getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        fun withDone(part: String) = if (done.isEmpty()) part else "$done $part"

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle) {
                partialResults.best()?.let { trySend(withDone(it)) }
            }

            override fun onSegmentResults(segmentResults: Bundle) {
                segmentResults.best()?.let {
                    if (done.isNotEmpty()) done.append(' ')
                    done.append(it)
                    trySend(done.toString())
                }
            }

            override fun onEndOfSegmentedSession() {
                close()
            }

            override fun onResults(results: Bundle) {
                results.best()?.let {
                    if (done.isNotEmpty()) done.append(' ')
                    done.append(it)
                }
                trySend(done.toString())
                close()
            }

            override fun onError(error: Int) {
                // „Nic nerozpoznáno" na konci zvuku po už přepsaných úsecích není selhání.
                if (error == SpeechRecognizer.ERROR_NO_MATCH && done.isNotEmpty()) close() else close(SpeechToTextException(error))
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer.startListening(
            intent(languageCode)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioClips.SAMPLE_RATE)
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
        )

        launch(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                    val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    pcm.forEach { bytes.putShort(it) }
                    out.write(bytes.array())
                }
            }.onFailure { logger.i(it) { "Zvuk do rozpoznávače se nepodařilo zapsat" } }
        }

        awaitClose {
            recognizer.destroy()
            runCatching { read.close() }
        }
    }.flowOn(Dispatchers.Main)

    private fun intent(languageCode: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag(languageCode))

    private fun languageTag(code: String) = when (code) {
        "cs" -> "cs-CZ"
        "en" -> "en-US"
        else -> code
    }

    private companion object {
        const val SUPPORT_TIMEOUT_MS = 5_000L
    }
}

/** Rozpoznávač skončil chybou ([SpeechRecognizer] `ERROR_*`). */
class SpeechToTextException(val error: Int) : Exception("SpeechRecognizer error $error")
