package eu.tintera.plume.features.stories

import eu.tintera.plume.core.telegram.StoryPoster
import eu.tintera.plume.core.telegram.StoryPostCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import eu.tintera.plume.core.ui.VideoTrim
import eu.tintera.plume.core.ui.VideoCropControl
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
    /** Kanály a skupiny, za které jde příběh zveřejnit; prázdné = jen vlastní účet. */
    val posters: List<StoryPoster> = emptyList(),
    /** Za koho se zveřejní; `null` = vlastní účet. */
    val poster: StoryPoster? = null,
    /** Za vybraný kanál to teď nejde (boosty…); věta pro uživatele, `null` = jde. */
    val posterDenied: UiText? = null,
)

/**
 * Úprava zveřejněného příběhu místo nového: editor vymění fotku nebo video příběhu
 * [storyId] autora [posterChatId] (`editStory`). [caption] je současný popisek, předvyplní se.
 */
data class StoryEditTarget(val posterChatId: Long, val storyId: Int, val caption: String)

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
    /** Vyměnit médium zveřejněného příběhu; `null` = nový příběh. */
    val edit: StoryEditTarget? = null,
) : ViewModel() {

    private val logger = Logger.withTag("Stories")

    private val _state = MutableStateFlow(StoryComposerState(previewPath = path.takeUnless { isVideo }, caption = edit?.caption.orEmpty()))
    val state: StateFlow<StoryComposerState> = _state.asStateFlow()

    /**
     * Úsek a obálka videa pro pás snímků ([VideoTrimBar]); `null` u fotky, nebo dokud se nezná
     * délka. Compose stav: pás se tahem mění po snímcích a čte ho přímo.
     */
    var video by mutableStateOf<VideoCropControl?>(null)
        private set

    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            repository.closeFriends().onSuccess { ids -> _state.update { it.copy(closeFriends = ids.toSet()) } }
        }
        // Za kanál jen nový příběh; úprava patří tomu, kdo příběh zveřejnil.
        if (edit == null) {
            viewModelScope.launch {
                repository.postTargets().onSuccess { found -> _state.update { it.copy(posters = found) } }
            }
        }
        if (isVideo) {
            viewModelScope.launch {
                runCatching { storyVideo.frame(path, atMs = 0) to storyVideo.durationMs(path) }
                    .onSuccess { (preview, durationMs) ->
                        val trim = VideoTrim(durationMs = durationMs, maxSegmentMs = STORY_VIDEO_MAX_SECONDS * 1000L)
                        video = Control(trim)
                        _state.update { it.copy(previewPath = preview, trimmed = trim.canMove) }
                    }
                    .onFailure { logger.w(it) { "Náhled videa pro příběh nejde" } }
            }
        }
    }

    /**
     * Náhled ukazuje vybranou obálku — tak bude příběh vypadat v seznamu i v profilu. Po tahu
     * se snímek dočte až po krátké pauze, ne po každém pixelu.
     */
    private fun showCover(coverMs: Long) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(COVER_PREVIEW_DELAY_MS)
            runCatching { storyVideo.frame(path, coverMs) }
                .onSuccess { frame -> _state.update { it.copy(previewPath = frame) } }
        }
    }

    private inner class Control(initial: VideoTrim) : VideoCropControl {
        override var trim by mutableStateOf(initial)
            private set

        override fun moveTo(startMs: Long) {
            val before = trim.coverMs
            trim = trim.movedTo(startMs)
            if (trim.coverMs != before) showCover(trim.coverMs)
        }

        override fun chooseCover(atMs: Long) {
            trim = trim.withCover(atMs)
            showCover(trim.coverMs)
        }

        override suspend fun frame(atMs: Long, edge: Int): String = storyVideo.frame(path, atMs, edge)
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

    /**
     * Zveřejnit za kanál (nebo skupinu), `null` = za sebe. Jestli to za kanál teď jde, se ptá
     * hned (`canPostStory`) — kanál bez boostů příběh nezveřejní a lepší to vědět předem.
     */
    fun setPoster(poster: StoryPoster?) {
        _state.update { it.copy(poster = poster, posterDenied = null) }
        poster ?: return
        viewModelScope.launch {
            val check = repository.canPostAs(poster.chatId)
            _state.update { state ->
                if (state.poster != poster) return@update state
                state.copy(posterDenied = (check as? StoryPostCheck.Denied)?.reason)
            }
        }
    }

    fun setAllowScreenshots(allow: Boolean) = _state.update { it.copy(allowScreenshots = allow) }

    fun errorShown() = _state.update { it.copy(error = null) }

    fun post() {
        val current = _state.value
        if (current.posting) return
        current.posterDenied?.let { reason ->
            _state.update { it.copy(error = reason) }
            return
        }
        // Kanálu viditelnost nenastavuje (TDLib ji u příběhu za chat ignoruje): vidí ho odběratelé.
        val audience = if (edit != null || current.poster != null) StoryAudience.Everyone else when (current.audience) {
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
                    val trim = video?.trim
                    storyVideo.render(path, current.fill, startMs = trim?.startMs ?: 0).let {
                        StoryMedia.Video(it.path, it.durationSeconds, it.hasAudio, coverSeconds = trim?.mainFrameSeconds ?: 0.0)
                    }
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
            val result = if (edit != null) {
                repository.replaceMedia(edit.posterChatId, edit.storyId, rendered, current.caption)
            } else {
                repository.post(
                    StoryDraft(
                        media = rendered,
                        caption = current.caption,
                        audience = audience,
                        keepOnProfile = current.keepOnProfile,
                        protectContent = !current.allowScreenshots,
                        posterChatId = current.poster?.chatId,
                    ),
                )
            }
            result
                .onSuccess { _state.update { it.copy(posting = false, posted = true) } }
                .onFailure { failure ->
                    _state.update { it.copy(posting = false, error = failure.userMessage(Res.string.story_post_failed)) }
                }
        }
    }
}

/** Pauza po tahu obálky, než se dočte snímek pro náhled. */
private const val COVER_PREVIEW_DELAY_MS = 150L
