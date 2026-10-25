package eu.tintera.plume.core.speech

import kotlinx.coroutines.flow.Flow

/**
 * Přepis řeči systémem zařízení, bez jazykového modelu — pro telefony bez Gemmy (přepis
 * modelem je `OnDeviceAi.transcribe` a má přednost) a pro hodinky. Na Androidu `SpeechRecognizer`
 * v zařízení, iOS zatím nemá nic (`SFSpeechRecognizer` je v roadmapě). V `:client:core`, aby ho
 * hodinky (stojí jen na `core`) mohly použít taky.
 */
interface SpeechToText {
    /** Umí telefon přepsat řeč v jazyce [languageCode] (`cs`, `en`) bez sítě? Levné, nic nestahuje. */
    suspend fun canTranscribe(languageCode: String): Boolean

    /**
     * Přepíše zvukový soubor [path] (hlasovka OGG/Opus, kolečko MP4). Vydává text, jak přibývá;
     * poslední hodnota je celý přepis. Selhání = výjimka.
     */
    fun transcribe(path: String, languageCode: String): Flow<String>
}
