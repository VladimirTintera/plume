package eu.tintera.plume.features.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.StoryAudience
import eu.tintera.plume.core.telegram.StoryDraft
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.StoryPhoto
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.story_audience_pick_someone
import eu.tintera.plume.resources.story_post_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Kdo příběh uvidí, jak se vybírá na obrazovce (vybraní lidé jsou zvlášť v [StoryComposerState.selected]). */
enum class AudienceChoice { Everyone, Contacts, Selected }

data class StoryComposerState(
    /** Fotka vyplní plátno 9 : 16 (kraje se oříznou); jinak se vejde celá na černé. */
    val fill: Boolean = true,
    val caption: String = "",
    /** Výchozí jsou kontakty: příběh je osobní a „všichni" je krok, který má udělat uživatel. */
    val audience: AudienceChoice = AudienceChoice.Contacts,
    val selected: Set<Long> = emptySet(),
    val keepOnProfile: Boolean = false,
    val allowScreenshots: Boolean = true,
    val posting: Boolean = false,
    val error: UiText? = null,
    /** Zveřejnění přijato — obrazovka se zavře, výsledek nahrávání ohlásí seznam chatů. */
    val posted: Boolean = false,
)

/**
 * Nový vlastní příběh z fotky [photoPath] (už otočené podle EXIF, z výběru příloh). Fotka se
 * převede na plátno 1080 × 1920, které TDLib vyžaduje, až při zveřejnění — náhled ji jen kreslí.
 */
class StoryComposerViewModel(
    val photoPath: String,
    private val repository: StoryRepository,
    private val storyPhoto: StoryPhoto,
) : ViewModel() {

    private val logger = Logger.withTag("Stories")

    private val _state = MutableStateFlow(StoryComposerState())
    val state: StateFlow<StoryComposerState> = _state.asStateFlow()

    fun setFill(fill: Boolean) = _state.update { it.copy(fill = fill) }

    fun setCaption(caption: String) = _state.update { it.copy(caption = caption) }

    fun setAudience(audience: AudienceChoice) = _state.update { it.copy(audience = audience) }

    fun setSelected(userIds: Set<Long>) = _state.update {
        it.copy(selected = userIds, audience = if (userIds.isEmpty()) it.audience else AudienceChoice.Selected)
    }

    fun setKeepOnProfile(keep: Boolean) = _state.update { it.copy(keepOnProfile = keep) }

    fun setAllowScreenshots(allow: Boolean) = _state.update { it.copy(allowScreenshots = allow) }

    fun errorShown() = _state.update { it.copy(error = null) }

    fun post() {
        val current = _state.value
        if (current.posting) return
        val audience = when (current.audience) {
            AudienceChoice.Everyone -> StoryAudience.Everyone
            AudienceChoice.Contacts -> StoryAudience.Contacts
            AudienceChoice.Selected -> {
                // Prázdný výběr by zveřejnil příběh, který nikdo neuvidí.
                if (current.selected.isEmpty()) {
                    _state.update { it.copy(error = UiText(Res.string.story_audience_pick_someone)) }
                    return
                }
                StoryAudience.Selected(current.selected.toList())
            }
        }
        _state.update { it.copy(posting = true) }
        viewModelScope.launch {
            val rendered = runCatching { storyPhoto.render(photoPath, current.fill) }
                .onFailure { logger.w(it) { "Fotku pro příběh se nepodařilo připravit" } }
                .getOrNull()
            if (rendered == null) {
                _state.update { it.copy(posting = false, error = UiText(Res.string.story_post_failed)) }
                return@launch
            }
            repository.post(
                StoryDraft(
                    photoPath = rendered,
                    caption = current.caption,
                    audience = audience,
                    keepOnProfile = current.keepOnProfile,
                    protectContent = !current.allowScreenshots,
                ),
            )
                .onSuccess { _state.update { it.copy(posting = false, posted = true) } }
                .onFailure { failure ->
                    _state.update { it.copy(posting = false, error = failure.userMessage(Res.string.story_post_failed)) }
                }
        }
    }
}
