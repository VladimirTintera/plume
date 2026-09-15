package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Přístup k obsahu jedné konverzace.
 *
 * Rozhraní je **záměrně bezstavové**. Stránkovací stav (kde jsme v historii, co už je načtené)
 * drží `ConversationViewModel`, ne tohle — jednak je ta logika tím testovatelná bez TDLib,
 * jednak je stav přirozeně vázaný na životnost obrazovky, ne na aplikaci.
 *
 * Protějšek je [ChatRepository], který je naopak stavový: seznam chatů je jeden pro celou
 * aplikaci a musí žít i když na něj nikdo nekouká.
 */
interface ConversationRepository {

    /**
     * Načte [limit] zpráv **starších** než [fromMessageId].
     *
     * `fromMessageId = 0` znamená „od nejnovější". Prázdný výsledek znamená konec historie —
     * TDLib nevrací žádný explicitní příznak, pozná se to jen podle toho, že nic nepřišlo.
     */
    suspend fun history(chatId: Long, fromMessageId: Long, limit: Int = 30): Result<List<ChatMessage>>

    /** Nové zprávy v tomhle chatu, jak přicházejí. */
    fun newMessages(chatId: Long): Flow<ChatMessage>

    /** Id smazaných zpráv. */
    fun deletedMessages(chatId: Long): Flow<List<Long>>

    /** Zprávy, jimž se změnil obsah (editace). */
    fun editedMessages(chatId: Long): Flow<ChatMessage>

    /** Reakce, které se u zprávy změnily. Klíč je `messageId`. */
    fun reactionUpdates(chatId: Long): Flow<MessageReactions>

    /**
     * Přidá nebo odebere reakci. TDLib má na to dvě samostatná volání, protože „přepnutí"
     * na jeho úrovni neexistuje — stav si musí pamatovat klient.
     */
    suspend fun setReaction(
        chatId: Long,
        messageId: Long,
        emoji: String,
        add: Boolean,
    ): Result<Unit>

    /** Emoji nabízená u téhle zprávy. Liší se chat od chatu — kanály si sadu omezují. */
    suspend fun availableReactions(chatId: Long, messageId: Long): Result<List<String>>

    /** Jedna konkrétní zpráva. Pro doplnění citace, která není v načtené historii. */
    suspend fun message(chatId: Long, messageId: Long): Result<ChatMessage>

    /**
     * Odešle zprávu a **okamžitě vrátí její rozepsanou podobu**.
     *
     * TDLib vrací zprávu hned, s dočasným id a stavem [SendState.Sending]; skutečné id přijde
     * až potvrzením v [sentMessageUpdates]. Díky tomu se dá bublina zobrazit bez čekání na server.
     *
     * [replyToMessageId] je `null` u běžné zprávy.
     */
    suspend fun send(chatId: Long, text: String, replyToMessageId: Long?): Result<ChatMessage>

    /**
     * Potvrzení nebo selhání odeslané zprávy.
     *
     * Nese **staré i nové id**: TDLib po potvrzení zprávě id změní, takže ji nestačí aktualizovat,
     * musí se v seznamu nahradit.
     */
    fun sentMessageUpdates(chatId: Long): Flow<SentMessageUpdate>

    /** Posun ukazatele „protějšek přečetl až sem". */
    fun readOutboxUpdates(chatId: Long): Flow<Long>

    /** Označí zprávy za přečtené. Bez tohohle zůstane odznak nepřečtených viset napořád. */
    suspend fun markRead(chatId: Long, messageIds: List<Long>): Result<Unit>

    suspend fun chatInfo(chatId: Long): Result<ChatInfo>

    /**
     * Sponzorované zprávy pro daný chat. Mimo kanály vrací prázdný seznam.
     *
     * Zobrazení je podle Telegram ToS **povinné** — výsledek se nesmí filtrovat ani skrývat.
     */
    suspend fun sponsoredMessages(chatId: Long): Result<List<SponsoredMessage>>

    /**
     * Nahlásí, že uživatel sponzorovanou zprávu viděl.
     *
     * Volá se teprve, když je na obrazovce **celý text** reklamy (bez tlačítka) — tak to
     * vyžaduje TDLib. Hlásit ji při prvním pixelu by znamenalo vykazovat zhlédnutí, která
     * se nestala.
     */
    suspend fun reportSponsoredView(chatId: Long, messageId: Long): Result<Unit>

    /** Nahlásí kliknutí. Musí odejít dřív, než se otevře odkaz. */
    suspend fun reportSponsoredClick(chatId: Long, messageId: Long): Result<Unit>
}

/** Hlavička konverzace. */
data class ChatInfo(
    val id: Long,
    val title: String,
    /**
     * Id poslední zprávy, kterou si protějšek přečetl.
     *
     * Stav „přečteno" **není ve zprávě samotné** — je to posuvný ukazatel na chatu. Všechny
     * odchozí zprávy s menším nebo rovným id jsou přečtené. Mění se updatem, ne přeposláním zpráv.
     */
    val lastReadOutboxMessageId: Long,
    /** `fileId` malé varianty avataru, nebo `null` když chat obrázek nemá. */
    val avatarFileId: Int?,
    /**
     * U skupin a kanálů se nad zprávami zobrazuje jméno odesílatele; v soukromém chatu by to byl
     * šum, protože odesílatelé jsou právě dva.
     */
    val isGroup: Boolean,
)

/**
 * Zpráva v konverzaci.
 *
 * [text] je text zprávy, u médií popisek — může být prázdný, když má zpráva jen obrázek.
 * [media] je vyplněné jen u zpráv s přílohou.
 */
data class ChatMessage(
    val id: Long,
    val isOutgoing: Boolean,
    /** `null` u odchozích zpráv a v soukromých chatech, kde jméno nedává smysl zobrazovat. */
    val senderName: String?,
    val senderId: Long,
    val text: String,
    val media: MessageMedia?,
    val reactions: List<MessageReaction>,
    /**
     * Stav odeslání. `null` u příchozích zpráv — tam nedává smysl.
     *
     * Pozor, nezahrnuje „přečteno": to se odvozuje z [ChatInfo.lastReadOutboxMessageId], protože
     * se mění nezávisle na zprávě.
     */
    val sendState: SendState?,
    /**
     * Id zprávy, na kterou tahle odpovídá.
     *
     * Schválně jen id, ne celá citovaná zpráva: v konverzaci se odpovídá skoro vždy na něco,
     * co už je načtené, takže náhled si poskládá `ConversationViewModel` z vlastního seznamu
     * a ušetří jedno volání na každou odpověď.
     */
    val replyToMessageId: Long?,
    /** Unixový čas v sekundách, jak ho dává TDLib. */
    val timestamp: Int,
    val isEdited: Boolean,
)

/**
 * Reakce u zprávy.
 *
 * Zatím jen **standardní emoji**. Telegram umí i vlastní (placená) emoji, ta jsou ale samolepky —
 * vykreslují se jako stažitelný soubor a mají vlastní animaci, takže patří k médiím, ne sem.
 * Neznámé typy se prostě přeskočí, aby zbytek reakcí u zprávy nezmizel.
 */
data class MessageReaction(
    val emoji: String,
    val count: Int,
    /** `true`, když tuhle reakci dal přihlášený uživatel. */
    val isChosen: Boolean,
    /**
     * Několik posledních odesílatelů, jak je dává TDLib (`recentSenderIds`).
     *
     * Je jich **nejvýš pár, ne všichni** — u příspěvku se stovkou reakcí Telegram kompletní
     * seznam v datech zprávy neposílá. Proto se podle délky tohohle seznamu nedá poznat
     * celkový počet; ten je v [count].
     */
    val recentSenders: List<ReactionSender>,
)

/** Ten, kdo reagoval. Uživatel i kanál — u příspěvků v kanálech reaguje jménem kanálu. */
data class ReactionSender(
    val id: Long,
    val title: String,
    val avatarFileId: Int?,
)

/** Kompletní sada reakcí jedné zprávy, jak dorazila updatem. */
data class MessageReactions(
    val messageId: Long,
    val reactions: List<MessageReaction>,
)

/** Výsledek odeslání zprávy, jak ho oznámí TDLib. */
sealed interface SentMessageUpdate {

    /** [oldMessageId] je dočasné id, pod kterým zpráva vznikla; [message] má už skutečné. */
    data class Succeeded(val oldMessageId: Long, val message: ChatMessage) : SentMessageUpdate

    data class Failed(val oldMessageId: Long, val message: ChatMessage) : SentMessageUpdate
}

/**
 * Stav odeslání odchozí zprávy.
 *
 * Čtvrtý stav — „přečteno" — tu schválně není. Nepatří zprávě, ale chatu
 * ([ChatInfo.lastReadOutboxMessageId]); kdyby byl tady, musely by se při každém přečtení
 * přemapovat všechny zprávy v historii.
 */
enum class SendState {
    /** Odesílá se; server ještě nepotvrdil. */
    Sending,

    /** Doručeno na server. */
    Sent,

    Failed,
}

/** Příloha zprávy. */
sealed interface MessageMedia {

    /**
     * Fotka vykreslovaná přímo v bublině.
     *
     * [thumbnailFileId] je nejmenší varianta (TDLib jich drží několik) — stáhne se v řádu
     * milisekund a poslouží jako okamžitý náhled, než dojde [fileId]. [width] a [height]
     * jsou rozměry zobrazované varianty; drží se kvůli poměru stran, aby bublina neposkočila,
     * až se obrázek načte.
     */
    data class Photo(
        val fileId: Int,
        val thumbnailFileId: Int?,
        val width: Int,
        val height: Int,
    ) : MessageMedia

    /**
     * Ostatní přílohy — video, GIF, dokument, hlasovka.
     *
     * Zatím se nepřehrávají ani neotevírají; ukáže se náhledový obrázek (pokud ho zpráva má)
     * a popisek typu. Vykreslování a přehrávání je samostatná fáze.
     */
    data class Attachment(
        val label: String,
        val thumbnailFileId: Int?,
    ) : MessageMedia
}
