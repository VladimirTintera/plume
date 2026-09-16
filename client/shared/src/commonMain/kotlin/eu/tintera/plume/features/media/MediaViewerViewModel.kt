package eu.tintera.plume.features.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Médium otevřené přes celou obrazovku.
 *
 * Zpráva se **načítá znovu podle id**, nepředává se z konverzace. Routa musí být serializovatelná
 * (jinak by nepřežila zabití procesu) a celá zpráva i s reakcemi a citací do navigačního zásobníku
 * nepatří. Dotaz navíc stojí jedno volání na otevření obrázku, což je vedle stahování samotné
 * fotky zanedbatelné.
 */
class MediaViewerViewModel(
    private val chatId: Long,
    private val messageId: Long,
    private val repository: ConversationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MediaViewerState())
    val state: StateFlow<MediaViewerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.message(chatId, messageId)
                .onSuccess { message ->
                    _state.update {
                        it.copy(
                            photo = message.media as? MessageMedia.Photo,
                            caption = message.text,
                            loading = false,
                            // Jiný typ média zatím neumíme; prázdný stav by vypadal jako chyba
                            // načítání, tak se to řekne rovnou.
                            error = if (message.media is MessageMedia.Photo) {
                                null
                            } else {
                                "Tenhle typ přílohy zatím neumíme otevřít."
                            },
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = failure.message ?: "Fotku se nepodařilo načíst.",
                        )
                    }
                }
        }
    }
}

data class MediaViewerState(
    val photo: MessageMedia.Photo? = null,
    /** Popisek pod fotkou. Prázdný u fotky bez textu. */
    val caption: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)
