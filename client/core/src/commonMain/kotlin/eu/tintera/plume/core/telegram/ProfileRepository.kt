package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration

/**
 * Profil chatu: kdo nebo co to je, o čem je a kdo v něm je.
 *
 * Hranice kolem TDLib jako ostatní repository — obrazovka vidí jen [ChatProfile] a [ProfileMember].
 * Data se čtou při otevření profilu a znovu po každé změně ([profileUpdates], [memberUpdates]);
 * zvlášť živé je ztlumení ([ChatRepository.muted]) a dostupnost ([ConversationRepository.presenceUpdates]).
 */
interface ProfileRepository {
    suspend fun profile(chatId: Long): Result<ChatProfile>

    /**
     * Profilové fotky chatu od nejnovější (té, kterou chat má teď) po nejstarší.
     *
     * U člověka a bota `getUserProfilePhotos`; osobní fotka, kterou si uživatel kontaktu nastavil
     * sám, ani veřejná fotka mezi nimi podle TDLib nejsou. U skupiny a kanálu vlastní historii
     * fotek TDLib nevede — skládá se z aktuální fotky a ze servisních zpráv o změně fotky
     * (`SearchMessagesFilterChatPhoto`), takže fotka, jejíž zprávu někdo smazal, chybí.
     */
    suspend fun profilePhotos(chatId: Long): Result<List<ProfilePhoto>>

    /** Je [chatId] chat s vlastním účtem (Uložené zprávy)? Jen tam jde profilovky spravovat. */
    suspend fun isOwnAccount(chatId: Long): Boolean

    /**
     * Hudba v profilu uživatele [userId] (`getUserProfileAudios`), v pořadí, které si nastavil,
     * nejvýš [PROFILE_AUDIO_LIMIT] skladeb. Prázdné = žádná, nebo ji uživatel soukromím skrývá.
     */
    suspend fun profileAudios(userId: Long): Result<List<MessageMedia.Audio>>

    /** Přidá skladbu na ZAČÁTEK hudby ve vlastním profilu (`addProfileAudio`). */
    suspend fun addProfileAudio(audio: MessageMedia.Audio): Result<Unit>

    /**
     * Nahraje skladbu [file] ze zařízení (název, interpret, délka a obal ze značek) a přidá ji
     * na ZAČÁTEK hudby ve vlastním profilu (`addProfileAudio` s místním souborem).
     */
    suspend fun addProfileAudioFile(file: OutgoingAttachment.Audio): Result<Unit>

    /** Odebere skladbu [fileId] z hudby ve vlastním profilu (`removeProfileAudio`). */
    suspend fun removeProfileAudio(fileId: Int): Result<Unit>

    /**
     * Přesune skladbu [fileId] ve vlastním profilu hned za [afterFileId]; `0` = na začátek
     * (`setProfileAudioPosition`). Celé nové pořadí rozloží na tahy [profileAudioMoves].
     */
    suspend fun moveProfileAudio(fileId: Int, afterFileId: Int): Result<Unit>

    /** Je skladba [fileId] v hudbě vlastního profilu? (`isProfileAudio`; 404 = není). */
    suspend fun isProfileAudio(fileId: Int): Boolean

    /**
     * Starší vlastní profilovka [photoId] bude zase hlavní (`setProfilePhoto` s
     * `InputChatPhotoPrevious`). Jen pro vlastní účet — u skupiny TDLib starší fotku znovu
     * použít neumí.
     */
    suspend fun setMainProfilePhoto(photoId: Long): Result<Unit>

    /** Smaže vlastní profilovku [photoId] z historie (`deleteProfilePhoto`). */
    suspend fun deleteProfilePhoto(photoId: Long): Result<Unit>

    /**
     * Nastaví automatické mazání NOVÝCH zpráv chatu po [seconds] (`setChatMessageAutoDeleteTime`),
     * 0 = vypnout. Platí pro všechny v chatu; do chatu přibude servisní zpráva.
     */
    suspend fun setAutoDeleteTime(chatId: Long, seconds: Int): Result<Unit>

    /**
     * Jedna stránka členů skupiny s rolí. Jen skupiny, kde je server vydá
     * ([ChatProfile.canListMembers]).
     *
     * Bez dotazu jsou na první stránce **vlastník a správci napřed**, pak nedávní členové; další
     * stránky jsou už jen nedávní — a správce z nich volající musí vyřadit sám (`distinctBy`),
     * protože server je v nedávných vrací taky. S [query] hledá server podle jména a uživatelského
     * jména, v pořadí, které si určí sám.
     *
     * Stránkuje se jen supergroup (`getSupergroupMembers` s posunem). Základní skupina má nejvýš
     * pár set členů a vydá je naráz, takže [MemberPage.nextOffset] je u ní vždycky `null`.
     *
     * [filter] omezení a vyhození vydá jen supergroup (a kanál) a jen správci, který smí omezovat
     * — základní skupina vyhozené nevede, vyhození je v ní jen odebrání. Správci napřed tam nejsou.
     */
    /**
     * Skupiny, ve kterých je uživatel [userId] i účet (`getGroupsInCommon`), nejvýš
     * [RELATED_CHATS_LIMIT] — víc se do záložky profilu stejně nikdo dívat nebude.
     */
    suspend fun commonGroups(userId: Long): Result<List<RelatedChat>>

    /**
     * Navrhne uživateli [userId] datum narození (`suggestUserBirthdate`). Přijde mu jako zpráva
     * s „Nastavit"; server to dovolí jen s někým, s kým má účet společné zprávy.
     */
    suspend fun suggestBirthdate(userId: Long, birthdate: Birthdate): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    /**
     * Upraví kontakt: jméno a poznámku, kterou vidí jen uživatel (`addContact`, kontakt podle
     * id se tím přepíše). [note] `null` = poznámku nechat, jak je (i s formátováním).
     */
    suspend fun editContact(userId: Long, firstName: String, lastName: String, note: String?): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    /**
     * Vlastní fotka kontaktu, kterou vidí jen uživatel (`setUserPersonalProfilePhoto`); `null`
     * ji odebere a vrátí se fotka, kterou si kontakt nastavil sám.
     */
    suspend fun setPersonalPhoto(userId: Long, photo: ProfilePhotoFile?): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    /** Jdou fotky chatu nahlásit moderátorům (`Chat.canBeReported`)? */
    suspend fun canReportPhotos(chatId: Long): Boolean = false

    /** Nahlásí profilovou fotku chatu moderátorům Telegramu (`reportChatPhoto`); [text] nepovinný. */
    suspend fun reportPhoto(chatId: Long, fileId: Int, reason: PhotoReportReason, text: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    /** Navrhne uživateli [userId] profilovou fotku (`suggestUserProfilePhoto`), oříznutou jako vlastní. */
    suspend fun suggestPhoto(userId: Long, photo: ProfilePhotoFile): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    /**
     * Kanály podobné kanálu [chatId] (`getChatSimilarChats`). Bez Premium jich server vydá
     * jen pár, a to stačí.
     */
    suspend fun similarChannels(chatId: Long): Result<List<RelatedChat>>

    /** Boti podobní botovi [botUserId] (`getBotSimilarBots`); i tady jich bez Premium server vydá pár. */
    suspend fun similarBots(botUserId: Long): Result<List<RelatedChat>> = Result.success(emptyList())

    /**
     * Uživatel otevřel [openedId] ze záložky Podobné u [sourceId] — kanál (`openChatSimilarChat`),
     * nebo bot ([bots], `openBotSimilarBot`, id jsou id uživatelů). Dokumentace TDLib to po klientovi
     * chce: server podle toho ladí doporučení. Selhání nic nezastaví, jen se zapíše.
     */
    suspend fun similarOpened(sourceId: Long, openedId: Long, bots: Boolean) = Unit

    /**
     * Dárky v profilu člověka nebo kanálu (`getReceivedGifts`), stránka po stránce od [offset]
     * (prázdný = první). Cizím TDLib vydá jen dárky vystavené v profilu.
     */
    suspend fun receivedGifts(chatId: Long, offset: String, collectionId: Int = 0): Result<ProfileGiftsPage> =
        Result.failure(UnsupportedOperationException())

    /**
     * Sbírky dárků, do kterých si majitel dárky v profilu roztřídil (`getGiftCollections`).
     * Prázdný seznam = žádné. Dárky jedné sbírky čte [receivedGifts] s jejím `collectionId`.
     */
    suspend fun giftCollections(chatId: Long): Result<List<GiftCollection>> = Result.success(emptyList())

    suspend fun members(
        chatId: Long,
        query: String = "",
        offset: Int = 0,
        limit: Int = MEMBERS_PAGE_SIZE,
        filter: MemberFilter = MemberFilter.Recent,
    ): Result<MemberPage>

    /**
     * Co jde na chatu upravit a jak to teď vypadá. Čte se až při otevření úpravy — profil
     * z toho potřebuje jen [ChatProfile.editRights], aby věděl, jestli položku nabídnout.
     */
    suspend fun editableChat(chatId: Long): Result<EditableChat>

    suspend fun setTitle(chatId: Long, title: String): Result<Unit>

    suspend fun setDescription(chatId: Long, description: String): Result<Unit>

    /**
     * Připojí ke kanálu [channelId] diskusní skupinu [groupId] — pod příspěvky pak jdou komentáře.
     * `null` diskusi odpojí. Smí správce kanálu s právem měnit údaje (`EditableChat.canSetDiscussion`).
     */
    suspend fun setDiscussionGroup(channelId: Long, groupId: Long?): Result<Unit>

    /**
     * Sada samolepek skupiny [chatId] (`setSupergroupStickerSet`); `null` sadu zruší. Členové ji
     * mají ve výběru samolepek té skupiny, i když ji nainstalovanou nemají. Smí správce, kterému
     * to server dovolí (`EditableChat.canSetStickerSet`).
     */
    suspend fun setStickerSet(chatId: Long, setId: Long?): Result<Unit>

    /**
     * Veřejné jméno supergroupy nebo kanálu (`t.me/…`); prázdné = chat bude soukromý. Smí jen
     * vlastník ([EditableChat.canChangeUsername]).
     */
    suspend fun setUsername(chatId: Long, username: String): Result<Unit>

    /** Dá se [username] použít pro chat [chatId]? Až nad jménem, které projde [usernameFormatError]. */
    suspend fun checkUsername(chatId: Long, username: String): Result<UsernameCheck>

    /**
     * Převede základní skupinu na supergroupu (`upgradeBasicGroupChatToSupergroupChat`) a vrátí
     * id NOVÉHO chatu. Veřejný odkaz ani témata základní skupina nemá; převod je nevratný a stará
     * skupina zůstane jako neaktivní chat s dosavadními zprávami. Smí jen vlastník.
     */
    suspend fun upgradeToSupergroup(chatId: Long): Result<Long>

    /** Nová fotka chatu z lokálního souboru (fotka, nebo krátké čtvercové video). */
    suspend fun setPhoto(chatId: Long, photo: ProfilePhotoFile): Result<Unit>

    suspend fun deletePhoto(chatId: Long): Result<Unit>

    /**
     * Výchozí práva členů. Zapisuje se **celým objektem**, takže co aplikace nenabízí
     * (hudba, video kolečka, ankety, témata…), se přenese z aktuálního stavu — nastavit to
     * mohl jiný klient a přepsat mu to tiše by bylo horší než to nenabízet.
     */
    suspend fun setPermissions(chatId: Long, permissions: GroupPermissions): Result<Unit>

    /**
     * Smaže skupinu nebo kanál pro všechny (`deleteChat`): zmizí zprávy i členové a uvolní se
     * veřejné jméno. Nevratné; jen když [EditableChat.canDeleteForAll].
     */
    suspend fun deleteChatForAll(chatId: Long): Result<Unit>

    /**
     * Převede supergroupu na gigaskupinu (`toggleSupergroupIsBroadcastGroup`): bez stropu členů,
     * psát smějí jen správci. **Nevratné.** Jen vlastník, viz [offersBroadcastGroup].
     */
    suspend fun convertToBroadcastGroup(supergroupId: Long): Result<Unit> = Result.failure(UnsupportedOperationException())

    /**
     * Uvítací zprávy chatu pro nové členy, od první po poslední (`loadChatWelcomeMessages`
     * a `updateChatWelcomeMessages`). Tok se ozve po načtení a po každé změně, i z jiného
     * zařízení. Jen pro vlastníka a správce s právem ([ChatEditRights.canManageWelcomeMessages]).
     */
    fun welcomeMessages(chatId: Long): Flow<List<WelcomeMessage>> = emptyFlow()

    /** Přidá uvítací zprávu s textem [text] (`addChatWelcomeMessage`). */
    suspend fun addWelcomeMessage(chatId: Long, text: String): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Smaže uvítací zprávu [id] (`deleteChatWelcomeMessage`). */
    suspend fun deleteWelcomeMessage(chatId: Long, id: Int): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Stav boostů supergroupy nebo kanálu (`getChatBoostStatus`); správci vidí víc čísel. */
    suspend fun boostStatus(chatId: Long): Result<BoostStatus> = Result.failure(UnsupportedOperationException())

    /**
     * Jednotlivé boosty chatu (`getChatBoosts`), od posledního, po stránkách; [offset] je
     * [ChatBoostsPage.nextOffset] předchozí stránky. Jen pro správce.
     */
    suspend fun boosts(chatId: Long, offset: String = ""): Result<ChatBoostsPage> = Result.failure(UnsupportedOperationException())

    /**
     * Poslední akce ve skupině nebo kanálu (`getChatEventLog`), od nejnovější, stránkované
     * po [CHAT_LOG_PAGE]. Jen pro správce; Telegram je drží 48 hodin. [filter] zužuje hledáním,
     * druhem akce a autory.
     */
    suspend fun eventLog(chatId: Long, fromEventId: Long = 0, filter: ChatLogFilter = ChatLogFilter()): Result<ChatLogPage>

    /**
     * Nahlásí, že agresivní antispam smazal zprávu [messageId] omylem
     * (`reportSupergroupAntiSpamFalsePositive`). Jen u zpráv z [eventLog] s
     * [ChatLogEvent.antiSpamMessageId]; zprávu to nevrátí, Telegram tím ladí antispam skupiny.
     */
    suspend fun reportAntiSpamFalsePositive(chatId: Long, messageId: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    /**
     * Ukázky mini aplikace bota (`getBotMediaPreviews`): fotky a videa, které autor bota nahrál
     * jako náhled aplikace. Prázdné = žádné (nebo bot mini aplikaci nemá).
     */
    suspend fun botMediaPreviews(botUserId: Long): Result<List<MessageMedia>> = Result.success(emptyList())

    /** Kdy a za kolik se sběratelské jméno nebo číslo koupilo na Fragmentu (`getCollectibleItemInfo`). */
    suspend fun collectibleInfo(item: CollectibleItem): Result<CollectibleInfo> =
        Result.failure(UnsupportedOperationException())

    /**
     * Vlastník a správci skupiny nebo kanálu — kdo může v záznamu akcí figurovat jako autor
     * (filtr podle autora). Jen lidé; smazané účty vynechané.
     */
    suspend fun administrators(chatId: Long): Result<List<SenderInfo>>

    /** Pomalý režim; jen hodnoty z [SLOW_MODE_DELAYS], 0 = vypnout. */
    suspend fun setSlowMode(chatId: Long, seconds: Int): Result<Unit>

    /**
     * Jeden přepínač nastavení. Podpisy a profily autorů jsou v TDLib jedno volání — druhou
     * hodnotu repository dočte, a vypnuté podpisy vypnou i profily.
     */
    suspend fun setGroupToggle(chatId: Long, toggle: GroupToggle, value: Boolean): Result<Unit>

    /**
     * Povolené reakce. Vlastní emoji reakce, které aplikace nenabízí, a strop počtu reakcí na
     * zprávu se přenesou z aktuálního stavu.
     */
    suspend fun setReactions(chatId: Long, reactions: ChatReactions): Result<Unit>

    /** Boosty, se kterými člen obejde pomalý režim a omezení; hodnoty z [UNRESTRICT_BOOST_COUNTS]. */
    suspend fun setUnrestrictBoostCount(chatId: Long, count: Int): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Reklamy v kanálu zapnout / vypnout (vypnout jen od úrovně z [SponsoredMessagesSetting]). */
    suspend fun setSponsoredMessages(chatId: Long, enabled: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Hlavní záložka profilu kanálu. */
    suspend fun setMainProfileTab(chatId: Long, tab: ProfileMainTab): Result<Unit> = Result.failure(UnsupportedOperationException())

    /**
     * Jeden člen se vším, co jeho správa potřebuje — role, práva, omezení a hlavně
     * [MemberDetail.actions], tedy co s ním smí právě tenhle uživatel.
     *
     * Čte se až při otevření nabídky nad členem: `getChatMember` je dotaz na server a ptát se
     * na každého ze seznamu dopředu by znamenalo dvě stě dotazů za otevření profilu.
     */
    suspend fun member(chatId: Long, userId: Long): Result<MemberDetail>

    /**
     * Ozve se, kdykoli se v chatu změní postavení některého člena, nebo plné údaje skupiny
     * (počet členů).
     *
     * Seznam se na to ČTE ZNOVU, neodvozuje se z updatu — stejně jako připnuté zprávy: jeden
     * update nese jednoho člena, a co změna doopravdy udělala (a jestli server vůbec poslechl),
     * ví jen server. TDLib `UpdateChatMember` navíc posílá jen správcům, tedy právě těm, kdo
     * členy mění.
     */
    fun memberUpdates(chatId: Long): Flow<Unit>

    /**
     * Ozve se, když se změnilo něco, z čeho se skládá [profile]: název a fotka chatu, jméno,
     * uživatelské jméno, telefon a bio člověka, blokování, veřejný odkaz a počet členů skupiny,
     * stav tajného chatu. Profil se na to čte znovu, celý — stejná úvaha jako u [memberUpdates]:
     * co se změnilo, ví celý objekt od TDLib, ne jednotlivý update. Čtení je lokální.
     */
    fun profileUpdates(chatId: Long): Flow<Unit>

    /**
     * Práva správce. **Prázdná práva ([AdminRights.any] `false`) člena ze správcovství sesadí** —
     * tak to bere TDLib, takže „Sesadit" není vlastní volání.
     *
     * Zapisuje se celým objektem: co aplikace nenabízí (videohovory, stories, témata), se přenese
     * z jeho současných práv, stejně jako u [setPermissions].
     */
    suspend fun setAdminRights(chatId: Long, userId: Long, rights: AdminRights): Result<Unit>

    /**
     * Omezit, co člen smí posílat; [duration] `null` = navždy. Jen v supergroupě — jinde TDLib
     * odpoví „Can't restrict users in basic group chats".
     *
     * [keepUntil]: běžící omezení si nechává svůj konec (unixový čas), [duration] se pak nebere.
     * Přepočítat ho jako „teď + zbytek" by konec posunulo o čas strávený na obrazovce.
     */
    suspend fun restrictMember(
        chatId: Long,
        userId: Long,
        permissions: GroupPermissions,
        duration: Duration?,
        keepUntil: Int? = null,
    ): Result<Unit>

    /** Zrušit omezení: člen se vrátí k výchozím právům skupiny. */
    suspend fun unrestrictMember(chatId: Long, userId: Long): Result<Unit>

    /**
     * Vyhodit ze skupiny; [duration] `null` = navždy. Zprávy po něm zůstanou — mazání historie
     * je jiná věc a v nabídce by se vedle vyhození dalo snadno ťuknout omylem.
     */
    suspend fun banMember(chatId: Long, userId: Long, duration: Duration?): Result<Unit>

    /**
     * Zrušit vyhození. Člověk se tím do skupiny NEVRÁTÍ, jen se do ní smí znovu přidat sám
     * (odkazem) nebo ho někdo přidá — tak to dělají i oficiální klienti.
     */
    suspend fun unbanMember(chatId: Long, userId: Long): Result<Unit>

    /** Vlastní štítek člena; prázdný řetězec ho odebere. */
    suspend fun setMemberTag(chatId: Long, userId: Long, tag: String): Result<Unit>

    /**
     * Jestli účet vůbec smí předávat vlastnictví. Ptát se je potřeba PŘED zadáním hesla:
     * bez dvoufázového ověření (nebo s čerstvě nastaveným heslem) to Telegram nedovolí
     * a uživatel by jinak psal heslo zbytečně.
     */
    suspend fun canTransferOwnership(): Result<OwnershipTransferCheck>

    /**
     * Předat vlastnictví skupiny nebo kanálu. **Nevratné** a vyžaduje heslo dvoufázového
     * ověření — to jde rovnou do TDLib a nikde se nedrží.
     */
    suspend fun transferOwnership(chatId: Long, userId: Long, password: String): Result<Unit>

    /**
     * Přidá [userIds] do skupiny nebo kanálu. Vrací ty, které server nepřidal kvůli JEJICH
     * nastavení soukromí (nedovolují, aby je někdo přidával) — přidaní ostatních tím neselže.
     *
     * Supergroup a kanál berou všechny naráz (`addChatMembers`); základní skupina jen po jednom
     * (`addChatMember`) a novému členovi rovnou přepošle posledních [BASIC_GROUP_HISTORY]
     * zpráv, jako oficiální klienti — jinak by vstoupil do skupiny, ve které nic nevidí.
     */
    suspend fun addMembers(chatId: Long, userIds: List<Long>): Result<List<Long>>

    /**
     * Kteří z [userIds] jsou boti. Do KANÁLU bota přidat jako odběratele nejde (server odpoví
     * `USER_BOT`) — jen rovnou jako správce ([setAdminRights]), takže je výběr musí oddělit.
     */
    suspend fun botsAmong(userIds: List<Long>): List<Long>

    /**
     * Pošle [link] (pozvánku) každému z [userIds] do soukromého chatu. Pro ty, které server
     * přidat nedovolil ([addMembers]): přes pozvánku se přidají sami.
     */
    suspend fun sendInviteLink(userIds: List<Long>, link: String): Result<Unit>
}

/** Kolik posledních zpráv uvidí nový člen základní skupiny (`forwardLimit`, server bere nejvýš 100). */
const val BASIC_GROUP_HISTORY = 100

/** Chat otevřený k úpravě: co se dá změnit a jak to teď vypadá. */
data class EditableChat(
    val chatId: Long,
    val title: String,
    val description: String,
    val photoFileId: Int?,
    val photoThumbnailFileId: Int?,
    val isChannel: Boolean,
    val rights: ChatEditRights,
    /** Výchozí práva členů; u kanálu se nenabízejí a jsou tu jen jako výchozí hodnoty. */
    val permissions: GroupPermissions,
    /** Skupina má témata. */
    val isForum: Boolean = false,
    /**
     * Témata jde zapnout a vypnout: smí jen vlastník skupiny, a diskusní skupina kanálu je
     * mít nemůže (`toggleSupergroupIsForum`). U základní skupiny ([isBasicGroup]) jen zapnout,
     * a to po převodu na supergroupu.
     */
    val canToggleForum: Boolean = false,
    /**
     * Základní skupina: veřejný odkaz i témata chtějí napřed převod na supergroupu
     * ([ProfileRepository.upgradeToSupergroup]) — s jiným id chatu.
     */
    val isBasicGroup: Boolean = false,
    /** Diskusní skupina kanálu (u skupiny propojený kanál); `null` = žádná. */
    val linkedChat: LinkedChat? = null,
    /** Kanálu jde vybrat nebo odpojit diskusní skupinu (správce s právem měnit údaje). */
    val canSetDiscussion: Boolean = false,
    /** Sada samolepek skupiny (`SupergroupFullInfo.stickerSetId`); `null` = žádná. */
    val stickerSet: StickerSetSummary? = null,
    /**
     * Sadu skupiny jde nastavit. Rozhoduje server (`SupergroupFullInfo.canSetStickerSet`) —
     * malým skupinám ji nedovolí ani vlastníkovi, takže se to z práv vypočítat nedá.
     */
    val canSetStickerSet: Boolean = false,
    /** Současné veřejné jméno (`t.me/…`); prázdné = soukromý chat. */
    val username: String = "",
    /**
     * Veřejné jméno jde měnit: jen vlastník (`setSupergroupUsername`). Základní skupině se
     * nabízí taky, uložení ji ale napřed převede na supergroupu ([isBasicGroup]).
     */
    val canChangeUsername: Boolean = false,
    /** Seznamy omezených a vyhozených; `null` = nenabízí se (viz [MemberModeration]). */
    val moderation: MemberModeration? = null,
    /** Pomalý režim, reakce a přepínače; co se nenabízí, v něm není ([groupSettingsOffer]). */
    val settings: GroupSettings = GroupSettings(),
    /** Chat jde smazat pro všechny (`Chat.canBeDeletedForAllUsers`) — vlastník skupiny nebo kanálu. */
    val canDeleteForAll: Boolean = false,
    /** Poslední akce ([ProfileRepository.eventLog]): supergroup a kanál, vlastník a správce. */
    val canViewEventLog: Boolean = false,
    /**
     * Statistiky ([StatisticsRepository.chatStatistics]): `SupergroupFullInfo.canGetStatistics`,
     * tedy správce kanálu nebo skupiny, kterou server za dost velkou považuje.
     */
    val canGetStatistics: Boolean = false,
    /** Supergroup, ke které se dá zeptat na převod na gigaskupinu; `null` u kanálu a základní skupiny. */
    val supergroupId: Long? = null,
    /** Vlastník chatu — jen ten smí skupinu převést na gigaskupinu. */
    val isOwner: Boolean = false,
    /** Skupina už je gigaskupina (`Supergroup.isBroadcastGroup`). */
    val isBroadcastGroup: Boolean = false,
)

/**
 * Nabídnout převod na gigaskupinu (`toggleSupergroupIsBroadcastGroup`)? Jen vlastníkovi běžné
 * supergroupy (ne kanálu, ne už převedené), a jen když ho Telegram sám doporučil
 * (`suggestedActionConvertToBroadcastGroup`, [AccountNotices.broadcastGroupSuggestions]).
 * Server převod dovolí až skupině blízko stropu členů a hranici klientovi neříká jinak než
 * touhle doporučenou akcí — nabízet ho dřív by znamenalo tlačítko, které skončí chybou.
 */
fun offersBroadcastGroup(chat: EditableChat, suggested: Set<Long>): Boolean {
    val supergroupId = chat.supergroupId ?: return false
    return chat.isOwner && !chat.isChannel && !chat.isBroadcastGroup && supergroupId in suggested
}

/**
 * Kolik členů má správce omezených a vyhozených. Jen supergroup a kanál, a jen pro vlastníka nebo správce s právem omezovat:
 * nikomu jinému server seznamy nevydá. Základní skupina je nevede vůbec.
 */
data class MemberModeration(
    /** Omezení; `null` v kanálu — nikdo do něj nepíše, takže se tam jen vyhazuje. */
    val restrictedCount: Int?,
    val bannedCount: Int,
)

/** Propojený chat — diskusní skupina kanálu, nebo kanál, ke kterému skupina patří. */
data class LinkedChat(val chatId: Long, val title: String, val photoFileId: Int?)

/**
 * Výchozí práva členů skupiny, zúžená na to, co aplikace umí a na čem uživateli záleží.
 *
 * Druhy obsahu jsou tytéž jako v [ContentAccess] — tedy ty, které vstupní pole opravdu omezuje.
 * Hudba, video kolečka, ankety, náhledy odkazů a témata tu nejsou: aplikace je neposílá, takže
 * nabízet u nich přepínač by slibovalo vliv na něco, co stejně nejde udělat.
 */
data class GroupPermissions(
    val sendMessages: Boolean,
    val sendPhotos: Boolean,
    val sendVideos: Boolean,
    val sendDocuments: Boolean,
    val sendVoiceNotes: Boolean,
    /** Video kolečka — v TDLib vlastní právo, hlasovky ho nezahrnují. */
    val sendVideoNotes: Boolean,
    /** Samolepky, animovaná emoji a GIFy — TDLib je vede pod jedním `canSendOtherMessages`. */
    val sendStickers: Boolean,
    val sendPolls: Boolean,
    val react: Boolean,
    val inviteUsers: Boolean,
    val pinMessages: Boolean,
    /** Smí člen měnit název, popis a fotku skupiny. */
    val changeInfo: Boolean,
)

data class ChatProfile(
    val chatId: Long,
    val title: String,
    val kind: ProfileKind,
    /** Velká fotka do hlavičky; [photoThumbnailFileId] je malá, ta se ukáže hned. */
    val photoFileId: Int?,
    val photoThumbnailFileId: Int?,
    /** Aktivní uživatelská jména bez `@`, první je hlavní. */
    val usernames: List<String>,
    /** Bio uživatele nebo popis skupiny / kanálu; prázdné = není. */
    val about: String,
    /** Formátování a odkazy v [about] — klikatelné stejně jako ve zprávách. */
    val aboutEntities: List<MessageEntity>,
    /** Telefon, jen když ho uživatel ukazuje (jinak prázdný řetězec od TDLib → `null`). */
    val phone: String?,
    /**
     * Sběratelská jména z Fragmentu: zapnutá jména kromě toho, které si účet nastavil sám
     * (`editableUsername`). TDLib je jinak neoznačuje — vlastní jméno je jen jedno, každé další
     * zapnuté se dá jen koupit.
     */
    val collectibleUsernames: Set<String> = emptySet(),
    /** Datum narození uživatele, jen když ho vidět smím; u skupiny a kanálu `null`. */
    val birthdate: Birthdate? = null,
    /** Záložka, na které se má profil otevřít (zvolil ji člověk nebo správci kanálu); `null` = žádná. */
    val mainTab: ProfileMainTab? = null,
    /** Kanál, který uživatel ukazuje v profilu; jinak `null`. */
    val personalChannel: PersonalChannel? = null,
    /** Počet členů / odběratelů; u soukromého chatu `null`. */
    val memberCount: Int?,
    /** Server vydá seznam členů (skupina, ne kanál; supergroup se skrytými členy ne). */
    val canListMembers: Boolean,
    /**
     * Skupina má zapnuté „skrýt členy" a uživatel v ní není správce — server mu pak vydá jen
     * správce, i při hledání. Velké veřejné skupiny to mívají zapnuté skoro vždycky.
     */
    val membersHidden: Boolean = false,
    /** Uživatel za soukromým chatem; `null` u skupiny a kanálu. */
    val userId: Long? = null,
    /** Profil tajného chatu (jinak soukromého, skupiny nebo kanálu). */
    val isSecretChat: Boolean = false,
    /** Tajný chat ještě běží (čeká na protějšek, nebo je dohodnutý) — jde ukončit. */
    val secretChatOpen: Boolean = false,
    /** Otisk klíče tajného chatu k porovnání s protějškem; `null`, dokud není dohodnutý. */
    val secretChatKey: SecretChatKey? = null,
    /** Uživatel nebo bot je zablokovaný. */
    val isBlocked: Boolean = false,
    /** Uživatel je v adresáři účtu (`User.isContact`); u bota a skupiny `false`. */
    val isContact: Boolean = false,
    /** Hlavní pozvánka do skupiny / kanálu — server ji vydá jen správci s právem zvát. */
    val inviteLink: String? = null,
    /** Uživatel ve skupině / kanálu je (smí odejít); u soukromého chatu `false`. */
    val isMember: Boolean = false,
    /** Co smí uživatel na chatu upravovat; prázdné = položka „Upravit" se nenabízí. */
    val editRights: ChatEditRights = ChatEditRights(),
    /** Ověřený, podvod, falešný (viz [Verification]); `null` u běžného účtu a základní skupiny. */
    val verification: Verification? = null,
    /** Kdo účet ověřil jako ověřovací bot a proč; `null` bez ověření od bota. */
    val botVerification: BotVerificationNote? = null,
    /** Vlastní emoji statusu za jménem (viz [statusEmojiId]), nebo `null`. */
    val emojiStatus: Long? = null,
    /** Po kolika sekundách se nové zprávy chatu samy mažou; 0 = nemažou. */
    val autoDeleteTime: Int = 0,
    /**
     * Smí uživatel automatické mazání měnit? V soukromém chatu ano (i s botem), ve skupině
     * a kanálu jen s právem měnit údaje chatu — tak ho vyžaduje `setChatMessageAutoDeleteTime`.
     */
    val canSetAutoDelete: Boolean = false,
    /** Smí uživatel do skupiny nebo kanálu přidávat lidi ([canInviteMembers]). */
    val canAddMembers: Boolean = false,
    /** Bot má hlavní mini aplikaci (`UserTypeBot.hasMainWebApp`): profil nabídne „Otevřít aplikaci". */
    val hasMainWebApp: Boolean = false,
    /** Diskusní skupina kanálu, u skupiny kanál, ke kterému patří; `null` = žádný. */
    val linkedChat: LinkedChat? = null,
    /**
     * Chat přímých zpráv kanálu (`SupergroupFullInfo.directMessagesChatId`); `null` = kanál je
     * nemá zapnuté. Profil podle něj nabídne „Napsat", správci „Přímé zprávy".
     */
    val directMessagesChatId: Long? = null,
    /** Přímé zprávy kanálu spravuje uživatel (`isAdministeredDirectMessagesGroup`). */
    val directMessagesAdmin: Boolean = false,
    /**
     * První skladba hudby v profilu uživatele (`UserFullInfo.firstProfileAudio`), nebo `null`.
     * Celý seznam se dočítá zvlášť ([ProfileRepository.profileAudios]); podle souboru téhle
     * skladby se pozná, že se seznam změnil.
     */
    val firstProfileAudio: MessageMedia.Audio? = null,
    /** Adresa a otevírací doba firmy (Telegram Business); `null` = nejde o firmu. */
    val business: BusinessDetails? = null,
    /** Dárky vystavené v profilu (`giftCount`); 0 = záložka se nekreslí. */
    val giftCount: Int = 0,
    /** Kontakt, jak ho má uživatel uložený — jméno, poznámka a vlastní fotka; `null` = není kontakt. */
    val contact: ContactDetails? = null,
) {
    /** Odkaz ke sdílení: veřejné jméno, jinak pozvánka; `null` = není co sdílet. */
    val link: String?
        get() = usernames.firstOrNull()?.let { "https://t.me/$it" } ?: inviteLink

    /** Soukromý chat (i s botem) jde smazat, ze skupiny a kanálu se odchází. */
    val isPrivate: Boolean get() = kind == ProfileKind.User || kind == ProfileKind.Bot
}

enum class ProfileKind { User, Bot, SavedMessages, Group, Channel }

data class ProfileMember(
    val sender: SenderInfo,
    val role: MemberRole,
    /**
     * Štítek člena ve skupině (`ChatMember.tag`), třeba „Moderátor". Dřív ho směl mít jen správce
     * jako „custom title"; novější TDLib ho nese u každého člena. Prázdný od serveru → `null`.
     */
    val tag: String? = null,
    val isBot: Boolean = false,
    /**
     * Do kdy platí omezení nebo vyhození (unix). Jen v seznamech [MemberFilter.Restricted]
     * a [MemberFilter.Banned]; `null` = navždy, u běžných členů se nenastavuje.
     */
    val until: Int? = null,
)

enum class MemberRole { Owner, Admin, Member }

/** Chat v záložce Společné skupiny nebo Podobné (kanály u kanálu, boti u bota). */
data class RelatedChat(
    val chatId: Long,
    val title: String,
    val photoFileId: Int?,
    /** Počet členů / odběratelů; `null`, když ho server nevydal. */
    val memberCount: Int?,
    val isChannel: Boolean,
    /** Bot v záložce Podobné u bota; [memberCount] je pak počet uživatelů za měsíc. */
    val isBot: Boolean = false,
)

/** Kolik společných skupin a podobných kanálů se čte — víc jich v záložce nikdo procházet nebude. */
const val RELATED_CHATS_LIMIT = 100

/** Které členy číst: běžný seznam, nebo ty, kterým správce něco zakázal. */
enum class MemberFilter {
    /** Nedávní členové, na první stránce s vlastníkem a správci napřed. */
    Recent,

    /** Omezení — mají vlastní práva místo výchozích. Jen supergroup. */
    Restricted,

    /** Vyhození — dokud vyhození platí, sami se nevrátí. Supergroup i kanál. */
    Banned,
}

/**
 * Stránka členů. [nextOffset] je posun pro další stránku; `null` = konec.
 *
 * **Posun se počítá ze SUROVÉ odpovědi serveru**, ne z [members]: smazané účty a kdo odešel se
 * z výsledku vyřazují, a posun odvozený z vyfiltrovaného seznamu by stejné členy četl znovu.
 */
data class MemberPage(
    val members: List<ProfileMember>,
    val nextOffset: Int?,
)

/** Členů na stránku; víc se jich do první obrazovky stejně nevejde. */
const val MEMBERS_PAGE_SIZE = 50

/** Víc naráz server nevydá (`getSupergroupMembers` i `searchChatMembers`). */
const val MAX_MEMBERS_REQUEST = 200

/**
 * Ověření od bota v profilu: [description] je důvod, který bot k ověření napsal (může chybět),
 * [botName] jméno ověřovacího bota pro větu „Ověřil …", když důvod není.
 */
data class BotVerificationNote(val description: String?, val botName: String)

/**
 * Jedna profilová fotka. [media] je fotka, nebo video u animované profilovky (s fotkou jako
 * náhledem); [addedDate] je, kdy si ji chat nastavil.
 */
data class ProfilePhoto(
    val id: Long,
    val addedDate: Int,
    val media: MessageMedia,
    /** Plná velikost fotky — nahlásit jde jen ji (`reportChatPhoto`), ne náhled ani video. */
    val reportFileId: Int? = null,
)

/**
 * Proč se profilová fotka hlásí (`ReportReason`). Pevný výčet z TDLib — na rozdíl od nahlášení
 * chatu se server na důvod neptá. [Other] chce popis.
 */
enum class PhotoReportReason { Spam, Violence, Pornography, ChildAbuse, Copyright, Fake, IllegalDrugs, PersonalDetails, Other }

/** Kolik skladeb hudby v profilu se načte; víc jich TDLib naráz nevydá. */
const val PROFILE_AUDIO_LIMIT = 100

/**
 * Tahy, kterými se hudba v profilu přeskládá z [current] na [target] (obojí id souborů téže
 * množiny): dvojice „skladba → za kterou" pro `setProfileAudioPosition`, `0` = na začátek.
 *
 * **Co nejméně tahů — každý je dotaz na server.** Skladby, které vůči sobě už stojí ve správném
 * pořadí (nejdelší rostoucí podposloupnost pozic z [current] v pořadí [target]), zůstanou;
 * ostatní se v pořadí [target] přesunou hned za svého předchůdce. Ten v tu chvíli už stojí
 * správně (nehýbal se, nebo se přesunul dřív), takže to vyjde. Přetažení jedné skladby kamkoli
 * je tak jeden tah.
 */
fun profileAudioMoves(current: List<Int>, target: List<Int>): List<Pair<Int, Int>> {
    val positions = target.map { current.indexOf(it) }
    val stays = longestIncreasing(positions).map { target[it] }.toSet()
    return target.mapIndexedNotNull { index, fileId ->
        if (fileId in stays) null else fileId to (if (index == 0) 0 else target[index - 1])
    }
}

/** Indexy nejdelší rostoucí podposloupnosti [values] (O(n²) — skladeb je nejvýš sto). */
private fun longestIncreasing(values: List<Int>): List<Int> {
    if (values.isEmpty()) return emptyList()
    val length = IntArray(values.size) { 1 }
    val previous = IntArray(values.size) { -1 }
    for (i in values.indices) {
        for (j in 0 until i) {
            if (values[j] < values[i] && length[j] + 1 > length[i]) {
                length[i] = length[j] + 1
                previous[i] = j
            }
        }
    }
    var index = length.indices.maxBy { length[it] }
    val result = ArrayDeque<Int>()
    while (index >= 0) {
        result.addFirst(index)
        index = previous[index]
    }
    return result.toList()
}

/**
 * Kontakt v adresáři účtu, jak ho vidí jen uživatel: jméno, pod kterým ho má uložené, poznámka
 * (`UserFullInfo.note`) a jestli mu nastavil vlastní fotku (`personalPhoto`). Ostatní nic z toho
 * nevidí — ani kontakt sám.
 */
data class ContactDetails(
    val firstName: String,
    val lastName: String,
    val note: String = "",
    val noteEntities: List<MessageEntity> = emptyList(),
    /** Nejvyšší délka poznámky (`user_note_text_length_max`); 0 = server poznámky neumí. */
    val noteMaxLength: Int = 0,
    val hasPersonalPhoto: Boolean = false,
)

/**
 * Boosty skupiny nebo kanálu: úroveň a kolik boostů chybí do další (odemyká barvy, vlastní
 * emoji, příběhy…). [premiumMembers] a [premiumPercentage] dostane jen správce, jinak nula.
 */
data class BoostStatus(
    val level: Int,
    val boostCount: Int,
    /** Kolik boostů bylo potřeba na současnou úroveň. */
    val currentLevelBoosts: Int,
    /** Kolik boostů je potřeba na další úroveň; 0 = další úroveň není. */
    val nextLevelBoosts: Int,
    val premiumMembers: Int,
    val premiumPercentage: Double,
    /** Odkaz, přes který členové chat boostnou (`t.me/boost/…`). */
    val boostUrl: String,
) {
    /** Kolik z cesty k další úrovni je hotovo (0..1); u nejvyšší úrovně 1. */
    val progress: Float
        get() {
            val span = nextLevelBoosts - currentLevelBoosts
            if (nextLevelBoosts == 0 || span <= 0) return 1f
            return ((boostCount - currentLevelBoosts).toFloat() / span).coerceIn(0f, 1f)
        }

    /** Kolik boostů chybí do další úrovně; 0 u nejvyšší. */
    val missingBoosts: Int get() = if (nextLevelBoosts == 0) 0 else (nextLevelBoosts - boostCount).coerceAtLeast(0)
}

/**
 * Jeden boost chatu. [booster] je člověk, od kterého boost je; `null` u soutěže bez výherce
 * nebo u smazaného účtu. [count] stejných boostů od téhož (Premium dává víc slotů).
 */
data class ChatBoostEntry(
    val id: String,
    val booster: SenderInfo?,
    val count: Int,
    val source: BoostSource,
    /** Kdy boost vyprší (Unix čas). */
    val expiresAt: Int,
)

/** Odkud boost je: předplatné Premium, dárkový kód od chatu, nebo výhra v jeho soutěži. */
enum class BoostSource { Premium, GiftCode, Giveaway }

/** Stránka boostů; [nextOffset] `null` = dál nic není. */
data class ChatBoostsPage(val boosts: List<ChatBoostEntry>, val nextOffset: String?)

/**
 * Dárek v profilu: obsah jako karta dárku ve zprávě ([MessageMedia.Gift], kreslí ho tatáž
 * `GiftCard`) plus kdy přišel a jestli ho majitel připnul nahoru. [key] je stálý klíč do mřížky.
 */
data class ProfileGift(
    val key: String,
    val gift: MessageMedia.Gift,
    val date: Int,
    val isPinned: Boolean,
)

/** Stránka dárků; [nextOffset] `null` = dál nic není. */
/** Sbírka dárků v profilu: jméno, kolik dárků obsahuje. */
data class GiftCollection(
    val id: Int,
    val name: String,
    val giftCount: Int,
)

data class ProfileGiftsPage(
    val gifts: List<ProfileGift>,
    val total: Int,
    val nextOffset: String?,
)

/**
 * Uvítací zpráva, kterou chat posílá novým členům. [text] je text zprávy nebo popisek média;
 * [description] slovní podoba obsahu bez textu („Fotka", „Samolepka"), aby šel řádek přečíst.
 */
data class WelcomeMessage(
    val id: Int,
    val text: String,
    val description: UiText?,
)
