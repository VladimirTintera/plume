package eu.tintera.plume.features.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.StoryAudience
import eu.tintera.plume.core.telegram.StoryDraft
import eu.tintera.plume.core.telegram.StoryMedia
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.STORY_VIDEO_MAX_SECONDS
import eu.tintera.plume.core.ui.StoryPhoto
import eu.tintera.plume.core.ui.StoryVideo
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.story_audience_pick_someone
import eu.tintera.plume.resources.story_close_friends_empty
import eu.tintera.plume.resources.story_close_friends_failed
import eu.tintera.plume.resources.story_close_friends_saving
import eu.tintera.plume.resources.story_post_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Kdo příběh uvidí, jak se vybírá na obrazovce (vybraní lidé jsou zvlášť v [StoryComposerState.selected]). */
enum class AudienceChoice { Everyone, Contacts, CloseFriends, Selected }

data class StoryComposerState(
    /** Fotka vyplní plátno 9 : 16 (kraje se oříznou); jinak se vejde celá na černé. */
    val fill: Boolean = true,
    val caption: String = "",
    /** Výchozí jsou kontakty: příběh je osobní a „všichni" je krok, který má udělat uživatel. */
    val audience: AudienceChoice = AudienceChoice.Contacts,
    val selected: Set<Long> = emptySet(),
    /** Seznam blízkých přátel účtu; `null`, dokud není načtený. */
    val closeFriends: Set<Long>? = null,
    /** Ukládá se změněný seznam blízkých přátel. */
    val savingCloseFriends: Boolean = false,
    val keepOnProfile: Boolean = false,
    val allowScreenshots: Boolean = true,
    val posting: Boolean = false,
    /** Náhled: u fotky ona sama, u videa jeho první snímek (`null`, dokud není). */
    val previewPath: String? = null,
    /** Video je delší než [STORY_VIDEO_MAX_SECONDS] — vezme se jen začátek a obrazovka to řekne. */
    val trimmed: Boolean = false,
    val error: UiText? = null,
    /** Zveřejnění přijato — obrazovka se zavře, výsledek nahrávání ohlásí seznam chatů. */
    val posted: Boolean = false,
)

/**
 * Nový vlastní příběh z fotky nebo videa [path] (z výběru příloh; fotka už otočená podle EXIF).
 * Převádí se až při zveřejnění — fotka na plátno 1080 × 1920, video na 720 × 1280 H.265, jak
 * TDLib vyžaduje. Náhled jen kreslí (u videa první snímek) a vypadá stejně.
 */
class StoryComposerViewModel(
    private val path: String,
    val isVideo: Boolean,
    private val repository: StoryRepository,
    private val storyPhoto: StoryPhoto,
    private val storyVideo: StoryVideo,
) : ViewModel() {

    private val logger = Logger.withTag("Stories")

    private val _state = MutableStateFlow(StoryComposerState(previewPath = path.takeUnless { isVideo }))
    val state: StateFlow<StoryComposerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.closeFriends().onSuccess { ids -> _state.update { it.copy(closeFriends = ids.toSet()) } }
        }
        if (isVideo) {
            viewModelScope.launch {
                runCatching { storyVideo.preview(path) to storyVideo.durationMs(path) }
                    .onSuccess { (preview, durationMs) ->
                        _state.update {
                            it.copy(previewPath = preview, trimmed = durationMs > STORY_VIDEO_MAX_SECONDS * 1000L)
                        }
                    }
                    .onFailure { logger.w(it) { "Náhled videa pro příběh nejde" } }
            }
        }
    }

    fun setFill(fill: Boolean) = _state.update { it.copy(fill = fill) }

    fun setCaption(caption: String) = _state.update { it.copy(caption = caption) }

    fun setAudience(audience: AudienceChoice) = _state.update { it.copy(audience = audience) }

    fun setSelected(userIds: Set<Long>) = _state.update {
        it.copy(selected = userIds, audience = if (userIds.isEmpty()) it.audience else AudienceChoice.Selected)
    }

    /**
     * Uloží nový seznam blízkých přátel (je to nastavení účtu, ne jen tohohle příběhu) a vybere
     * je jako publikum. Seznam se v obrazovce přepne hned a při chybě vrátí.
     */
    fun saveCloseFriends(userIds: Set<Long>) {
        val previous = _state.value.closeFriends
        _state.update {
            it.copy(
                closeFriends = userIds,
                savingCloseFriends = true,
                audience = if (userIds.isEmpty()) it.audience else AudienceChoice.CloseFriends,
            )
        }
        viewModelScope.launch {
            repository.setCloseFriends(userIds.toList())
                .onSuccess { _state.update { it.copy(savingCloseFriends = false) } }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            closeFriends = previous,
                            savingCloseFriends = false,
                            error = failure.userMessage(Res.string.story_close_friends_failed),
                        )
                    }
                }
        }
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
            AudienceChoice.CloseFriends -> {
                // Prázdný seznam = příběh, který nikdo neuvidí.
                if (current.closeFriends.isNullOrEmpty() || current.savingCloseFriends) {
                    _state.update {
                        it.copy(error = UiText(if (current.savingCloseFriends) Res.string.story_close_friends_saving else Res.string.story_close_friends_empty))
                    }
                    return
                }
                StoryAudience.CloseFriends
            }
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
            val rendered = runCatching {
                if (isVideo) {
                    storyVideo.render(path, current.fill).let { StoryMedia.Video(it.path, it.durationSeconds, it.hasAudio) }
                } else {
                    StoryMedia.Photo(storyPhoto.render(path, current.fill))
                }
            }
                .onFailure { logger.w(it) { "Příběh se nepodařilo připravit (video: $isVideo)" } }
                .getOrNull()
            if (rendered == null) {
                _state.update { it.copy(posting = false, error = UiText(Res.string.story_post_failed)) }
                return@launch
            }
            repository.post(
                StoryDraft(
                    media = rendered,
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
