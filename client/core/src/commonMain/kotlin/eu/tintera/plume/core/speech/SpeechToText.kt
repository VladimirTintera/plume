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
    suspend fun canTranscribe(languageCode: String): Boolean = language(languageCode) == SpeechLanguage.Installed

    /** Jak je na tom jazyk [languageCode] pro rozpoznávání v zařízení. Levné, nic nestahuje. */
    suspend fun language(languageCode: String): SpeechLanguage

    /**
     * Požádá systém o stažení jazyka [languageCode] pro rozpoznávání v zařízení a počká, jak to
     * dopadne. Stahuje systém, ne aplikace; může se zeptat uživatele.
     */
    suspend fun downloadLanguage(languageCode: String): SpeechDownload

    /**
     * Přepíše zvukový soubor [path] (hlasovka OGG/Opus, kolečko MP4). Vydává text, jak přibývá;
     * poslední hodnota je celý přepis. Selhání = výjimka.
     */
    fun transcribe(path: String, languageCode: String): Flow<String>
}

/** Jazyk pro rozpoznávání řeči v zařízení. */
enum class SpeechLanguage {
    /** Stažený, přepis jde hned. */
    Installed,

    /** Systém ho umí, ale stažený není ([SpeechToText.downloadLanguage]). */
    Downloadable,

    /** Systém ho právě stahuje. */
    Downloading,

    /** Systém ho v zařízení neumí (nebo rozpoznávání v zařízení nemá vůbec). */
    Unsupported,
}

/** Jak dopadla žádost o stažení jazyka ([SpeechToText.downloadLanguage]). */
enum class SpeechDownload {
    /** Stažený, přepis jde hned. */
    Done,

    /** Systém stažení přijal a dokončí ho sám (třeba až na Wi-Fi); jak dopadne, se nedozvíme. */
    Started,

    /** Nepovedlo se (systém odmítl, jazyk neumí). */
    Failed,
}
