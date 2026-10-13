package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
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
     */
    suspend fun members(
        chatId: Long,
        query: String = "",
        offset: Int = 0,
        limit: Int = MEMBERS_PAGE_SIZE,
    ): Result<MemberPage>

    /**
     * Soukromý chat smaže jen u nás (`deleteChatHistory`, `revoke = false`), ze skupiny a kanálu
     * odejde (`leaveChat`) — stejně jako „Smazat" v seznamu chatů, viz „Akce nad chatem".
     */
    suspend fun deleteOrLeave(profile: ChatProfile): Result<Unit>

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
)

enum class MemberRole { Owner, Admin, Member }

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
data class ProfilePhoto(val id: Long, val addedDate: Int, val media: MessageMedia)
