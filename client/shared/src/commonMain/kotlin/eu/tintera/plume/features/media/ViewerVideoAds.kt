package eu.tintera.plume.features.media

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.VideoAd
import eu.tintera.plume.features.report.ReportFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reklamy nad videem z kanálu na celé obrazovce (`getVideoMessageAdvertisements`), část
 * [MediaViewerViewModel].
 *
 * - **Ptá se při prvním přehrání videa**, které je mít může (zjistí repository z
 *   `canGetVideoAdvertisements`); jakmile reklamy jsou, platí pro celý prohlížeč — další video
 *   téhož kanálu se neptá znovu ani nezačíná od nuly (specifikace).
 * - **Čas běží jen při přehrávání** ([setPlaying]): tiká po [TICK_MS] a posílá úseky do
 *   [VideoAdSchedule].
 * - **Zhlédnutí se hlásí, jakmile se reklama ukáže** — rámeček ji ukazuje celou, neořezanou —
 *   a jen jednou. Hlášení jede v [appScope]: prohlížeč se může zavřít hned potom.
 */
internal class ViewerVideoAds(
    private val chatId: Long,
    /** Prohlížeč médií chatu; profilové fotky, příběh, anketa ani reklama samotná reklamy nemají. */
    private val enabled: Boolean,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val repository: ConversationRepository,
    private val logger: Logger,
) {
    private val _shown = MutableStateFlow<ViewerVideoAd?>(null)

    /** Reklama nad videem právě teď; `null` = žádná. */
    val shown: StateFlow<ViewerVideoAd?> = _shown.asStateFlow()

    private var schedule: VideoAdSchedule? = null
    private val tried = HashSet<Long>()
    private val viewed = HashSet<Long>()
    private var playing: Long? = null
    /** Nabídka nebo informace o sponzorovi u reklamy jsou otevřené ([setHeld]). */
    private var held = false
    private var ticker: Job? = null
    private var loading: Job? = null

    /**
     * Průvodce nahlášením reklamy — tentýž jako u reklamy v kanálu, jen jiné volání. Id reklamy
     * jede jako jediná „zpráva" požadavku.
     */
    val reportFlow = ReportFlow(scope) { _, request ->
        repository.reportVideoAd(request.messageIds.single(), request.optionId)
    }

    /** Které video právě hraje na celé obrazovce; `null` = žádné (pozastavené, galerie, zavřené). */
    fun setPlaying(messageId: Long?) {
        playing = messageId
        if (messageId == null) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (schedule == null) load(messageId) else startTicker()
    }

    /**
     * Reklama se drží, dokud je u ní otevřená nabídka, informace o sponzorovi nebo průvodce
     * nahlášením — čas mezitím neběží. Jinak by se po `maxDisplayDuration` skryla i s nabídkou,
     * kterou uživatel právě čte (ověřeno na emulátoru: ⋮ zmizelo pod prstem).
     */
    fun setHeld(value: Boolean) {
        held = value
    }

    /** ✕ na reklamě; jde až po `minDisplayDuration`. */
    fun close() {
        if (schedule?.close() == true) publish()
    }

    /** Ťuknutí na reklamu: kliknutí se nahlásí a vrátí se odkaz inzerenta k otevření. */
    fun open(): String? {
        val ad = _shown.value?.ad ?: return null
        appScope.launch {
            repository.reportVideoAdClick(ad.uniqueId)
                .onFailure { logger.w(it) { "Kliknutí na reklamu ve videu ${ad.uniqueId} se nepodařilo nahlásit." } }
        }
        return ad.url
    }

    /** ⋮ → Nahlásit reklamu. Nahlášená reklama zmizí hned, i před minimem. */
    fun report() {
        val ad = _shown.value?.ad ?: return
        reportFlow.start(chatId, messageIds = listOf(ad.uniqueId)) {
            schedule?.drop(ad.uniqueId)
            publish()
        }
    }

    private fun load(messageId: Long) {
        if (!enabled || loading?.isActive == true || !tried.add(messageId)) return
        loading = scope.launch {
            repository.videoAds(chatId, messageId)
                .onSuccess { ads ->
                    if (ads != null && schedule == null) {
                        schedule = VideoAdSchedule(ads)
                        if (playing != null) startTicker()
                    }
                }
                .onFailure { logger.w(it) { "Reklamy k videu $messageId se nepodařilo načíst." } }
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                delay(TICK_MS)
                val current = schedule ?: break
                if (held || reportFlow.wizard.value != null) continue
                current.advance(TICK_MS)
                publish()
                if (current.finished) break
            }
        }
    }

    private fun publish() {
        val current = schedule
        val ad = current?.current
        val next = ad?.let { ViewerVideoAd(it, canClose = current.canClose) }
        if (next != _shown.value) _shown.value = next
        if (ad != null && viewed.add(ad.uniqueId)) {
            appScope.launch {
                repository.reportVideoAdView(ad.uniqueId)
                    .onFailure { logger.w(it) { "Zhlédnutí reklamy ve videu ${ad.uniqueId} se nepodařilo nahlásit." } }
            }
        }
    }

    private companion object {
        /**
         * Krok časovače: zavřít jde nejpozději čtvrt vteřiny po minimu. Pozastavení zahodí
         * rozběhnutý krok, takže se reklama s každou pauzou opozdí nejvýš o tolik — to nikdo nepozná.
         */
        const val TICK_MS = 250L
    }
}

/** Reklama nad videem a jestli ji uživatel smí zavřít. */
data class ViewerVideoAd(val ad: VideoAd, val canClose: Boolean)
