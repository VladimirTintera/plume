package eu.tintera.plume.core.telegram

/**
 * Hovor v historii hovorů (`searchCallMessages`) — zpráva o hovoru z kteréhokoli chatu.
 * [callUserId] je komu jde zavolat zpátky: protějšek soukromého chatu; skupinový hovor ne.
 */
data class CallHistoryEntry(
    val chat: ChatSummary,
    val messageId: Long,
    val outcome: CallOutcome,
    val isVideo: Boolean,
    /** Nula u hovoru, ve kterém se nemluvilo. */
    val durationSeconds: Int,
    val isGroup: Boolean,
    /** Čas zprávy o hovoru, unixové sekundy. */
    val date: Int,
    val callUserId: Long?,
)

/** Stránka historie; prázdný [nextOffset] = další už nic. */
data class CallHistoryPage(val entries: List<CallHistoryEntry>, val nextOffset: String)

/**
 * Server chce po skončeném hovoru hodnocení (`CallStateDiscarded.needRating`). Ptá se jen někdy
 * a jen po hovoru, ve kterém se mluvilo.
 */
data class CallRatingRequest(val callId: Int, val userId: Long, val isVideo: Boolean)

/** Co s hovorem bylo (`CallProblem`); u videa i obraz. */
enum class CallProblem(val videoOnly: Boolean = false) {
    Echo,
    Noise,
    Interruptions,
    DistortedSpeech,
    SilentLocal,
    SilentRemote,
    Dropped,
    DistortedVideo(videoOnly = true),
    PixelatedVideo(videoOnly = true),
}
