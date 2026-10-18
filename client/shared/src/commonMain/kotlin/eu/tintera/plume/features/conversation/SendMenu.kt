package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.Presence

/**
 * Co nabídne podržení odesílacího tlačítka. Nic, když se upravuje zpráva — úprava se nikam
 * „neposílá", jen potvrzuje.
 */
internal data class SendMenuOffer(
    /** Bez zvuku: protějšek nedostane zvuk ani vibraci. V Uložených zprávách nemá komu. */
    val silent: Boolean,
    /** Naplánovat na čas. Ve vlákně komentářů ne — naplánované patří celé skupině. */
    val schedule: Boolean,
    /**
     * Až bude online — jen v soukromém chatu s člověkem, jehož dostupnost známe
     * (`MessageSchedulingStateSendWhenOnline`: „the user's online status must be known").
     * Bot dostupnost nemá, u skryté („naposledy nedávno") server volbu odmítne.
     */
    val whenOnline: Boolean,
    /**
     * S efektem — jen v soukromém chatu (i s botem): TDLib efekt jinde nepřijme. V Uložených
     * zprávách ho nemá kdo vidět a ve vlákně komentářů jde o skupinu.
     */
    val effects: Boolean = false,
    /**
     * Navrhnout jako příspěvek — jen odběratel v přímých zprávách kanálu (`suggestedPostInfo`
     * jde jen do chatu přímých zpráv). Správce kanálu publikuje sám, návrh nepotřebuje.
     */
    val suggestPost: Boolean = false,
) {
    val any: Boolean get() = silent || schedule || whenOnline || effects || suggestPost
}

/** Volba z nabídky odeslání mimo vstupní pole (podržená samolepka ve výběru). */
internal enum class SendMenuChoice { Silent, Schedule, WhenOnline }

internal fun sendMenuOffer(state: ConversationState): SendMenuOffer {
    if (state.editing != null) return SendMenuOffer(silent = false, schedule = false, whenOnline = false)
    val info = state.info
    val saved = info?.isSavedMessages == true
    // Naplánované zprávy patří celé skupině; ve vlákně ani v tématu by nebylo kde je ukázat.
    val schedule = state.threadRootId == null && state.forumTopicId == null
    val privateChat = info != null && !info.isGroup && !info.isChannel && !saved
    return SendMenuOffer(
        // V chatu s výchozím tichem (zvoneček v kanálu) „bez zvuku" nic nezmění: ticho přidá
        // TDLib sám a „se zvukem" ho přebít neumí (`ConversationRepository.setDefaultSilent`).
        silent = !saved && info?.defaultSilent != true,
        schedule = schedule,
        whenOnline = schedule && privateChat && state.presence is Presence.Offline,
        effects = schedule && privateChat,
        suggestPost = info?.isDirectMessages == true && !info.isDirectMessagesAdmin,
    )
}

/**
 * Smí zpráva odejít v [at]? Nejdřív za minutu (čas vybraný na minuty by jinak mohl minout
 * dřív, než se pošle) a nejpozději za rok — dál server plánovat nedovolí.
 */
internal fun scheduleValid(at: Long, now: Long): Boolean = at - now in MIN_SCHEDULE_AHEAD_SECONDS..MAX_SCHEDULE_AHEAD_SECONDS

internal const val MIN_SCHEDULE_AHEAD_SECONDS = 60L
internal const val MAX_SCHEDULE_AHEAD_SECONDS = 365L * 24 * 60 * 60
