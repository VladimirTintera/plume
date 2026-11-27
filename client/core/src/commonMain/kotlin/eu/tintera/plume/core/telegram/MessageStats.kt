package eu.tintera.plume.core.telegram

/**
 * Druh zprávy pro statistiku chatu — hrubé škatulky, ne celý [MessageMedia]: statistika počítá
 * tisíce zpráv a převod každé na [ChatMessage] (jména, náhledy, popisy) by byl práce navíc.
 */
enum class MessageStatKind { Text, Photo, Video, Gif, Sticker, Voice, VideoNote, Music, File, Poll, Location, Other }

/** Reakce u zprávy: klíč jako [MessageReaction.emoji] (emoji, nebo vlastní emoji) a kolikrát. */
data class ReactionCount(val key: String, val count: Int)

/**
 * Otisk jedné zprávy pro statistiku chatu ([ConversationRepository.messageStats]): kdo, kdy, co
 * a jak dlouhé, s reakcemi. Servisní oznámení se do statistiky nepočítají a sem se nedostanou.
 */
data class MessageStat(
    val id: Long,
    /** Sekundy epochy. */
    val timestamp: Int,
    /** Odesílatel jako u [ChatMessage.senderId] — uživatel, nebo chat (kanál, anonymní správce). */
    val senderId: Long,
    val isOutgoing: Boolean,
    val kind: MessageStatKind,
    /** Délka vlastního textu nebo popisku ve znacích; `0` u zprávy bez textu. */
    val characters: Int,
    val reactions: List<ReactionCount> = emptyList(),
)

/** Úsek historie pro statistiku: zprávy od nejnovější ke starším a kam až úsek sahá. */
data class MessageStatsPage(
    val messages: List<MessageStat>,
    /** Nejstarší prošlá zpráva (i servisní) — odtud se pokračuje; `null`, když nepřišla žádná. */
    val oldestMessageId: Long?,
    /** Jako [HistoryCounts.reachedEnd]: z databáze = dál nic není v zařízení, ze serveru = začátek chatu. */
    val reachedEnd: Boolean,
)
