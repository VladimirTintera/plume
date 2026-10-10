package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.SpeechTranscript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Přepisy hlasovek a koleček z modelu v telefonu. Server přepis ukládá do zprávy
 * (`speechRecognitionResult`), tenhle ne — drží se tady, v paměti procesu.
 *
 * Jeden na proces (Koin `single`), ne ViewModel: odchod z chatu a návrat přepis nezahodí.
 * Restart aplikace ano — přepis jde udělat znovu a do zprávy na serveru ho zapsat nejde.
 */
class LocalTranscripts {
    private val _state = MutableStateFlow<Map<Pair<Long, Long>, SpeechTranscript>>(emptyMap())
    val state: StateFlow<Map<Pair<Long, Long>, SpeechTranscript>> = _state.asStateFlow()

    fun get(chatId: Long, messageId: Long): SpeechTranscript? = _state.value[chatId to messageId]

    fun set(chatId: Long, messageId: Long, transcript: SpeechTranscript) =
        _state.update { it + ((chatId to messageId) to transcript) }
}
