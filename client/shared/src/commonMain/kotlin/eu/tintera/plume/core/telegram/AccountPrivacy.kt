package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Soukromí účtu: kdo vidí naposledy viděn, telefon, fotku… a kdo smí volat nebo zvát do skupin.
 *
 * **Je to nastavení ÚČTU, ne aplikace** — TDLib ho drží na serveru a platí na všech zařízeních.
 * Změna z jiného klienta přijde updatem (`UpdateUserPrivacySettingRules`), proto je [rules] živý.
 */
interface PrivacyRepository {

    /**
     * Pravidla jednoho nastavení, živě: odběr updatů stojí PŘED prvním čtením, takže se změna
     * mezi nimi neztratí. Selhání čtení přijde jako `Result.failure` (obrazovka řekne proč).
     */
    fun rules(setting: PrivacySetting): Flow<Result<List<PrivacyRule>>>

    /** Zapíše pravidla CELÁ — TDLib jiný zápis nemá. Skládá je [privacyRulesOf]. */
    suspend fun setRules(setting: PrivacySetting, rules: List<PrivacyRule>): Result<Unit>

    /** „Ukazovat čas přečtení" (`ReadDatePrivacySettings`). Update na něj TDLib neposílá. */
    suspend fun showsReadDate(): Result<Boolean>

    suspend fun setShowsReadDate(show: Boolean): Result<Unit>

    /**
     * Po kolika dnech bez přihlášení se účet sám smaže (`getAccountTtl`). Update na to TDLib
     * neposílá.
     */
    suspend fun accountTtlDays(): Result<Int> = Result.failure(UnsupportedOperationException())

    suspend fun setAccountTtlDays(days: Int): Result<Unit> = Result.failure(UnsupportedOperationException())

    /**
     * Jména a fotky výjimek. Čte se lokálně; kdo se dočíst nedá (smazaný účet), dostane prázdné
     * jméno a UI dosadí vlastní — z výjimek ho nevyhazujeme, server ho v pravidlech drží dál.
     */
    suspend fun members(userIds: List<Long>, chatIds: List<Long>): List<PrivacyMember>

    /**
     * Chat z výběru jako výjimka: soukromý (i tajný) chat je ČLOVĚK s id uživatele, skupina je
     * skupina s id chatu. Kanál výjimkou být nemůže (TDLib bere jen členy skupin).
     */
    suspend fun memberOf(chatId: Long): Result<PrivacyMember>
}

/**
 * Nastavení soukromí, která aplikace umí. Pořadí je pořadí řádků na obrazovce.
 *
 * @property bases které základní volby TDLib u nastavení bere.
 * @property exceptions jestli nastavení bere výjimky (lidi a skupiny). Hledání podle čísla ne:
 *   TDLib u něj podporuje jen `AllowAll` a `AllowContacts`.
 */
enum class PrivacySetting(
    val bases: List<PrivacyBase> = PrivacyBase.entries,
    val exceptions: Boolean = true,
) {
    LastSeen,
    PhoneNumber,
    FindByPhone(bases = listOf(PrivacyBase.Everybody, PrivacyBase.Contacts), exceptions = false),
    ProfilePhoto,
    Bio,
    Birthdate,
    /** Kdo vidí hudbu v profilu (`UserPrivacySettingShowProfileAudio`). */
    ProfileAudio,
    ForwardLink,
    Calls,
    PeerToPeer,
    ChatInvites,
    /** Omezit může jen účet s Premium — bez něj obrazovka stav jen ukáže. */
    VoiceMessages,
}

enum class PrivacyBase { Everybody, Contacts, Nobody }

/**
 * Doménová podoba pravidla TDLib (`UserPrivacySettingRule`). **Pořadí v seznamu je význam:**
 * TDLib bere PRVNÍ pravidlo, které na uživatele sedí, a když nesedí žádné, je to zákaz.
 *
 * Všechny druhy z TDLib mají obdobu, i ty, které obrazovka neumí (boti, Premium) — při zápisu
 * se přenášejí ([privacyRulesOf]). Převod z TDLib je `when` bez `else`, takže nový druh rozbije
 * překlad, místo aby se tiše ztratil.
 */
sealed interface PrivacyRule {
    data object AllowAll : PrivacyRule
    data object AllowContacts : PrivacyRule
    data object AllowBots : PrivacyRule
    data object AllowPremium : PrivacyRule
    data class AllowUsers(val userIds: List<Long>) : PrivacyRule
    data class AllowChatMembers(val chatIds: List<Long>) : PrivacyRule
    data object RestrictAll : PrivacyRule
    data object RestrictContacts : PrivacyRule
    data object RestrictBots : PrivacyRule
    data class RestrictUsers(val userIds: List<Long>) : PrivacyRule
    data class RestrictChatMembers(val chatIds: List<Long>) : PrivacyRule
}

/**
 * Co obrazovka z pravidel ukazuje: základní volbu a výjimky „Vždy povolit" a „Nikdy nepovolit".
 *
 * Seznamy výjimek se drží i tehdy, když k základní volbě nepatří (u „Všichni" nemá smysl
 * „Vždy", u „Nikdo" „Nikdy") — přepnutí tam a zpátky je nesmaže. Do pravidel jdou jen ty,
 * které platí ([alwaysApplies], [neverApplies]).
 */
data class PrivacyChoice(
    val base: PrivacyBase,
    val alwaysUsers: List<Long> = emptyList(),
    val alwaysChats: List<Long> = emptyList(),
    val neverUsers: List<Long> = emptyList(),
    val neverChats: List<Long> = emptyList(),
) {
    val alwaysApplies: Boolean get() = base != PrivacyBase.Everybody
    val neverApplies: Boolean get() = base != PrivacyBase.Nobody

    /** Kolik výjimek opravdu platí — číslo v řádku „Kontakty · 2 výjimky". */
    val exceptionCount: Int
        get() = (if (alwaysApplies) alwaysUsers.size + alwaysChats.size else 0) +
            (if (neverApplies) neverUsers.size + neverChats.size else 0)

    fun withBase(base: PrivacyBase): PrivacyChoice = copy(base = base)

    /**
     * Přidá výjimky na jednu stranu a z té druhé je vyřadí — tentýž člověk nemůže být zároveň
     * „vždy" a „nikdy" (TDLib by vzal ten, který stojí v pravidlech dřív, a to by uživatel nepoznal).
     */
    fun withAdded(always: Boolean, users: List<Long>, chats: List<Long>): PrivacyChoice =
        if (always) {
            copy(
                alwaysUsers = (alwaysUsers + users).distinct(),
                alwaysChats = (alwaysChats + chats).distinct(),
                neverUsers = neverUsers - users.toSet(),
                neverChats = neverChats - chats.toSet(),
            )
        } else {
            copy(
                neverUsers = (neverUsers + users).distinct(),
                neverChats = (neverChats + chats).distinct(),
                alwaysUsers = alwaysUsers - users.toSet(),
                alwaysChats = alwaysChats - chats.toSet(),
            )
        }

    fun withRemoved(always: Boolean, id: Long, isGroup: Boolean): PrivacyChoice = when {
        always && isGroup -> copy(alwaysChats = alwaysChats - id)
        always -> copy(alwaysUsers = alwaysUsers - id)
        isGroup -> copy(neverChats = neverChats - id)
        else -> copy(neverUsers = neverUsers - id)
    }
}

/** Výjimka, jak ji obrazovka kreslí. [id] je id uživatele, u skupiny id chatu. */
data class PrivacyMember(
    val id: Long,
    val isGroup: Boolean,
    /** Prázdné u smazaného nebo neznámého účtu — jméno pak dosadí UI. */
    val title: String,
    val avatarFileId: Int?,
    val kind: ChatKind,
)

/** Pravidla, která základní volbu ukončují: za nimi už nic nesedí. */
private fun PrivacyRule.isTerminal() = this == PrivacyRule.AllowAll || this == PrivacyRule.RestrictAll

/** Pravidla, která obrazovka neumí a při zápisu se přenášejí, jak stála. */
private fun PrivacyRule.isPreserved() = when (this) {
    PrivacyRule.AllowBots, PrivacyRule.AllowPremium, PrivacyRule.RestrictBots, PrivacyRule.RestrictContacts -> true
    else -> false
}

/**
 * Pravidla → co ukázat. Čte se jako TDLib: první shoda vyhrává.
 *
 * - Základní volba je první z `AllowAll` / `AllowContacts` / `RestrictAll`; bez žádné je to
 *   „Nikdo" (nic nesedí = zákaz). Za `AllowAll` a `RestrictAll` už nic nesedí, takže se dál
 *   nečte; za `AllowContacts` ano — výjimky pak platí pro ty, kdo kontakty nejsou.
 * - Člověk nebo skupina, kteří jsou v pravidlech dvakrát, patří tam, kde stojí DŘÍV.
 */
fun privacyChoiceOf(rules: List<PrivacyRule>): PrivacyChoice {
    var base: PrivacyBase? = null
    val users = LinkedHashMap<Long, Boolean>()
    val chats = LinkedHashMap<Long, Boolean>()
    for (rule in rules) {
        when (rule) {
            PrivacyRule.AllowAll -> if (base == null) base = PrivacyBase.Everybody
            PrivacyRule.RestrictAll -> if (base == null) base = PrivacyBase.Nobody
            PrivacyRule.AllowContacts -> if (base == null) base = PrivacyBase.Contacts
            is PrivacyRule.AllowUsers -> rule.userIds.forEach { users.getOrPut(it) { true } }
            is PrivacyRule.RestrictUsers -> rule.userIds.forEach { users.getOrPut(it) { false } }
            is PrivacyRule.AllowChatMembers -> rule.chatIds.forEach { chats.getOrPut(it) { true } }
            is PrivacyRule.RestrictChatMembers -> rule.chatIds.forEach { chats.getOrPut(it) { false } }
            PrivacyRule.AllowBots, PrivacyRule.AllowPremium, PrivacyRule.RestrictBots, PrivacyRule.RestrictContacts -> Unit
        }
        if (rule.isTerminal()) break
    }
    return PrivacyChoice(
        base = base ?: PrivacyBase.Nobody,
        alwaysUsers = users.filterValues { it }.keys.toList(),
        alwaysChats = chats.filterValues { it }.keys.toList(),
        neverUsers = users.filterValues { !it }.keys.toList(),
        neverChats = chats.filterValues { !it }.keys.toList(),
    )
}

/**
 * Co obrazovka ukazuje → pravidla k zápisu. Zápis je vždycky CELÝ objekt, takže co obrazovka
 * neumí (boti, Premium, „kromě kontaktů"), se vezme z [previous] a přenese.
 *
 * Pořadí (první shoda vyhrává):
 * 1. výjimky LIDÍ — konkrétní člověk má přednost před skupinou, ve které je, i před vším ostatním,
 * 2. výjimky SKUPIN,
 * 3. přenesená pravidla v pořadí, v jakém stála (jen ta, která stála před ukončující volbou —
 *    za ní nic nesedělo, takže by po přesunu najednou začala platit),
 * 4. základní volba: `AllowAll`, `AllowContacts`, nebo `RestrictAll`.
 *
 * Výjimky, které k základní volbě nepatří, se nezapisují; u nastavení bez výjimek
 * ([PrivacySetting.exceptions]) žádné.
 */
fun privacyRulesOf(choice: PrivacyChoice, previous: List<PrivacyRule>, exceptions: Boolean = true): List<PrivacyRule> {
    val rules = mutableListOf<PrivacyRule>()
    if (exceptions) {
        if (choice.alwaysApplies && choice.alwaysUsers.isNotEmpty()) rules += PrivacyRule.AllowUsers(choice.alwaysUsers)
        if (choice.neverApplies && choice.neverUsers.isNotEmpty()) rules += PrivacyRule.RestrictUsers(choice.neverUsers)
        if (choice.alwaysApplies && choice.alwaysChats.isNotEmpty()) rules += PrivacyRule.AllowChatMembers(choice.alwaysChats)
        if (choice.neverApplies && choice.neverChats.isNotEmpty()) rules += PrivacyRule.RestrictChatMembers(choice.neverChats)
    }
    val terminal = previous.indexOfFirst { it.isTerminal() }.let { if (it < 0) previous.size else it }
    rules += previous.take(terminal).filter { it.isPreserved() }
    rules += when (choice.base) {
        PrivacyBase.Everybody -> PrivacyRule.AllowAll
        PrivacyBase.Contacts -> PrivacyRule.AllowContacts
        PrivacyBase.Nobody -> PrivacyRule.RestrictAll
    }
    return rules
}

/**
 * Server vrátí po zápisu jen PLATNÉ výjimky: kdo přepnul „Kontakty" → „Všichni", přijde zpátky
 * bez „Vždy povolit". Ty se ale z [local] drží dál, ať se po přepnutí zpátky vrátí — stejně jako
 * v oficiálních klientech. Platné seznamy bere vždycky ze serveru.
 */
fun mergeHiddenExceptions(server: PrivacyChoice, local: PrivacyChoice?): PrivacyChoice {
    if (local == null) return server
    return server.copy(
        alwaysUsers = if (server.alwaysApplies) server.alwaysUsers else local.alwaysUsers,
        alwaysChats = if (server.alwaysApplies) server.alwaysChats else local.alwaysChats,
        neverUsers = if (server.neverApplies) server.neverUsers else local.neverUsers,
        neverChats = if (server.neverApplies) server.neverChats else local.neverChats,
    )
}
