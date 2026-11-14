package eu.tintera.plume.core.accounts

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.Koin
import org.koin.core.KoinApplication
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Přihlášené účty a jejich kontejnery (návrh `docs/navrh-vice-uctu.md`, „Architektura:
 * kontejner účtů").
 *
 * - **Každý účet má izolovaný kontejner Koinu** se vším, co mu patří (`accountModule`), i se
 *   scope, který se při odebrání účtu zruší.
 * - **Seznam účtů je uložený** ([AccountRegistry]): které sloty, jejich user id a výchozí účet.
 *   Instalace z doby před víc účty soubor nemá a dostane jediný, primární slot.
 * - **Odhlášený účet zmizí, jsou-li jiné.** Po odhlášení TDLib vyrobí nového klienta u zadání
 *   čísla ([AuthStage.WaitPhoneNumber]); vedle jiného účtu ho nikdo nepotřebuje, takže se
 *   ukončí a jeho data smažou. Poslední účet zůstává a aplikace ukáže přihlášení jako dřív.
 * - **Nový účet se přihlašuje bokem** ([adding]): do seznamu přijde až v [AuthStage.Ready]
 *   a jen tehdy, když v aplikaci ještě není. Do té doby ho nic mimo přihlašovací obrazovku
 *   nevidí.
 *
 * Kontejnery vznikají hned v konstruktoru, protože v nich vznikají i eager repository
 * (`createdAtStart`): odběry updatů musí běžet dřív, než TDLib pošle první dávku (CLAUDE.md,
 * „Repository seznamu chatů MUSÍ vznikat eagerně"). Proto je i `Accounts` v Koinu eager.
 *
 * @param open vyrobí izolovaný kontejner Koinu pro slot (`openAccount`), i s eager instancemi;
 *   druhý argument je scope účtu, který se v kontejneru podává jako scope aplikace.
 * @param registry kam se seznam účtů ukládá; `null` = nikam (hodinky, testy).
 * @param scope scope aplikace pro práci, která přežije účet (odebrání, uložení seznamu).
 */
class Accounts internal constructor(
    private val open: (AccountSlot, CoroutineScope) -> KoinApplication,
    private val registry: AccountRegistry?,
    private val scope: CoroutineScope,
    private val newSlotId: () -> String = ::randomSlotId,
    /** Na čem běží scope účtů; test ho nahradí svým. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val logger = Logger.withTag("Accounts")

    /** Mění seznam, výchozí účet i přidávaný účet: kroky přidání a odebrání se nesmí proplést. */
    private val mutex = Mutex()

    /** Kdo chce vědět o každém otevřeném účtu ([forEachOpened]). */
    private val listeners = MutableStateFlow<List<(AccountSession) -> Unit>>(emptyList())

    /** Účty s kontejnerem: přihlášené i přidávaný. */
    private val opened = MutableStateFlow<List<AccountSession>>(emptyList())

    private val _all: MutableStateFlow<List<AccountSession>>

    /** Přihlášené účty v pořadí, ve kterém přibyly. Nikdy prázdné: poslední účet zůstává i odhlášený. */
    val all: StateFlow<List<AccountSession>>

    private val _default: MutableStateFlow<AccountSession>

    /**
     * Výchozí účet: ten, jehož chaty ukazuje seznam, a který dostanou akce bez zdroje (nová zpráva,
     * odkaz, služba bez slotu). Nastaví ho uživatel v menu účtu; nový účet se jím stane sám.
     */
    val default: StateFlow<AccountSession>

    private val _adding = MutableStateFlow<AccountSession?>(null)

    /** Účet, který se právě přihlašuje přes „Přidat účet"; `null`, když se nepřidává. */
    val adding: StateFlow<AccountSession?> = _adding.asStateFlow()

    private val _alreadySignedIn = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Přidávaný účet už v aplikaci je; přihlášení se zahodilo a UI to má říct. */
    val alreadySignedIn: SharedFlow<Unit> = _alreadySignedIn.asSharedFlow()

    init {
        val saved = registry?.load()
        val entries = saved?.accounts.orEmpty()
            .filter { entry -> AccountSlot.parse(entry.slot) != null }
            .distinctBy { it.slot }
            .ifEmpty { listOf(SavedAccount(AccountSlot.Primary.id)) }
        val sessions = entries.map { entry ->
            openSession(AccountSlot(entry.slot), entry.userId).also { it.pushReceiverId.value = entry.pushReceiverId }
        }
        _all = MutableStateFlow(sessions)
        all = _all.asStateFlow()
        val defaultSlot = saved?.default?.let(AccountSlot::parse)
        _default = MutableStateFlow(sessions.firstOrNull { it.slot == defaultSlot } ?: sessions.first())
        default = _default.asStateFlow()
        sessions.forEach(::watch)
        registry?.removeOrphans(sessions.map { it.slot.id }.toSet())
    }

    /** Účet podle slotu. Neznámý slot je chyba v kódu, ne stav: slot se dostane jen od účtu. */
    operator fun get(slot: AccountSlot): AccountSession =
        find(slot) ?: error("Neznámý slot účtu: ${slot.id}")

    /**
     * Účet podle slotu ze vstupního bodu nebo routy: notifikace, zástupce i otevřená obrazovka
     * můžou přežít odhlášení svého účtu. Přidávaný účet se taky najde, jeho přihlašovací
     * obrazovka v něm žije.
     */
    fun find(slot: AccountSlot): AccountSession? =
        _all.value.firstOrNull { it.slot == slot } ?: _adding.value?.takeIf { it.slot == slot }

    /** User id ostatních přihlášených účtů než [slot] (QR přihlášení je posílá serveru). */
    fun otherUserIds(slot: AccountSlot): List<Long> =
        _all.value.filter { it.slot != slot }.mapNotNull { it.userId.value }

    /**
     * Účet, kterému patří push s příjemcem [receiverId] (`NotificationRepository.pushReceiverId`):
     * podle id z registrace pushů, u nešifrovaného pushe podle user id. S jediným účtem ten účet
     * i bez známého příjemce, jako před víc účty. `null` = push nepatří nikomu z přihlášených
     * (účet odhlášený jinde) a zahodí se.
     */
    fun pushTarget(receiverId: Long?): AccountSession? {
        val accounts = _all.value
        if (receiverId != null) {
            accounts.firstOrNull { it.pushReceiverId.value == receiverId || it.userId.value == receiverId }?.let { return it }
        }
        return accounts.singleOrNull()
    }

    /** Zapamatuje si id příjemce pushů účtu z registrace (`registerDevice`). */
    fun rememberPushReceiver(slot: AccountSlot, receiverId: Long) {
        scope.launch {
            mutex.withLock {
                val account = _all.value.firstOrNull { it.slot == slot } ?: _adding.value?.takeIf { it.slot == slot } ?: return@withLock
                if (account.pushReceiverId.value == receiverId) return@withLock
                account.pushReceiverId.value = receiverId
                if (account in _all.value) save()
            }
        }
    }

    /**
     * [block] pro každý otevřený účet: hned pro ty, které už jsou, a pro každý další, jakmile
     * vznikne jeho kontejner (i přidávaný). Tudy aplikace startuje, co má běžet s účtem
     * (notifikace, hlídání spojení, úklid úložiště).
     *
     * Volá se při startu procesu, dřív než může přibýt účet: souběh s přidáním účtu by mohl
     * [block] pro nový účet zavolat dvakrát.
     */
    fun forEachOpened(block: (AccountSession) -> Unit) {
        listeners.update { it + block }
        opened.value.forEach(block)
    }

    /** Nastaví výchozí účet; slot, který mezitím zmizel, se přeskočí. */
    fun setDefault(slot: AccountSlot) {
        scope.launch {
            mutex.withLock {
                val account = _all.value.firstOrNull { it.slot == slot } ?: return@withLock
                if (_default.value === account) return@withLock
                _default.value = account
                save()
            }
        }
    }

    /**
     * Začne přidávat účet: nový slot s vlastním klientem TDLib, který jde rovnou k přihlášení.
     * Vrací přidávaný účet (i rozdělaný z dřívějška), nebo `null`, když je účtů už [MAX_ACCOUNTS].
     */
    suspend fun add(): AccountSession? = mutex.withLock {
        _adding.value?.let { return@withLock it }
        if (_all.value.size >= MAX_ACCOUNTS) return@withLock null
        val taken = _all.value.map { it.slot.id }.toSet() + AccountSlot.Primary.id
        val slot = generateSequence { newSlotId() }.first { it !in taken }.let(::AccountSlot)
        logger.i { "Přidávám účet ve slotu ${slot.id}." }
        openSession(slot, userId = null).also { account ->
            _adding.value = account
            watchAdding(account)
        }
    }

    /**
     * Zruší rozdělané přidání účtu (zpět z přihlašovací obrazovky): klient i data jdou pryč.
     *
     * Úklid běží ve scope aplikace, ne volajícího: volá to přihlašovací obrazovka, která
     * s přidáváním zmizí, a s ní by se úklid přerušil uprostřed čekání na zavření klienta
     * (ověřeno na zařízení: adresář slotu zůstal).
     */
    suspend fun cancelAdd() {
        val account = mutex.withLock { _adding.value?.also { _adding.value = null } } ?: return
        logger.i { "Přidání účtu ${account.slot.id} zrušeno." }
        scope.launch { discard(account, destroy = true) }
    }

    private fun openSession(slot: AccountSlot, userId: Long?): AccountSession {
        val accountScope = CoroutineScope(SupervisorJob() + dispatcher)
        val account = AccountSession(slot, open(slot, accountScope), accountScope, userId)
        opened.update { it + account }
        // Klient TDLib se startuje hned, ne až s první obrazovkou: push umí probudit proces, ve
        // kterém se žádné UI neskládá, a nenastartovaný klient by ho neměl čím rozšifrovat.
        accountScope.launch(start = CoroutineStart.UNDISPATCHED) { account.session.start() }
        listeners.value.forEach { it(account) }
        return account
    }

    /**
     * Hlídá přihlášený účet: po přihlášení si zapamatuje user id, a když skončí odhlášený vedle
     * jiných účtů, odebere ho. Běží ve scope účtu, takže s ním skončí.
     */
    private fun watch(account: AccountSession) {
        account.scope.launch {
            account.session.authStage.collect { stage ->
                when (stage) {
                    is AuthStage.Ready -> rememberUserId(account)
                    AuthStage.WaitPhoneNumber -> remove(account)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun rememberUserId(account: AccountSession) {
        val id = account.session.me().getOrNull()?.id ?: return
        if (account.userId.value == id) return
        account.userId.value = id
        mutex.withLock { if (account in _all.value) save() }
    }

    /**
     * Odebere odhlášený účet, je-li vedle něj jiný. Posledního se nedotkne: aplikace bez účtu
     * ukáže přihlášení v jeho slotu, jako to dělala odjakživa.
     */
    private suspend fun remove(account: AccountSession) {
        val removed = mutex.withLock {
            val remaining = _all.value - account
            if (remaining.size == _all.value.size || remaining.isEmpty()) return@withLock false
            _all.value = remaining
            if (_default.value === account) _default.value = remaining.first()
            save()
            true
        }
        if (!removed) return
        logger.i { "Účet ${account.slot.id} odhlášený, odebírám ho." }
        discard(account, destroy = true)
    }

    /**
     * Čeká, až se přidávaný účet přihlásí. Účet, který v aplikaci už je, se zase odhlásí: dva
     * klienti téhož účtu by si notifikace i stav přečtení přetahovali. QR kód tomu zabrání sám
     * (`other_user_ids`), přihlášení číslem se pozná až tady.
     */
    private fun watchAdding(account: AccountSession) {
        account.scope.launch {
            account.session.authStage.first { it is AuthStage.Ready }
            val id = account.session.me().getOrNull()?.id
            val accepted = mutex.withLock {
                if (_adding.value !== account) return@launch
                _adding.value = null
                if (id != null && _all.value.any { it.userId.value == id }) return@withLock false
                account.userId.value = id
                _all.value = _all.value + account
                _default.value = account
                save()
                true
            }
            if (accepted) {
                logger.i { "Účet ${account.slot.id} přidán." }
                watch(account)
            } else {
                logger.i { "Přidávaný účet už v aplikaci je, odhlašuji ho." }
                _alreadySignedIn.tryEmit(Unit)
                // Relace na serveru vznikla, tak ji odhlásit, ne jen zahodit klienta. Po odhlášení
                // TDLib vyrobí nového klienta u zadání čísla, a teprve ten se ukončí.
                account.session.logOut()
                withTimeoutOrNull(LOGOUT_TIMEOUT) { account.session.authStage.first { it == AuthStage.WaitPhoneNumber } }
                discard(account, destroy = true)
            }
        }
    }

    /**
     * Ukončí klienta, smaže data účtu (adresáře TDLib, klíč databáze) a zruší jeho scope.
     *
     * **Kontejner se nezavírá** (`KoinApplication.close`): obrazovka, která účet ještě skládá
     * (konverzace otevřená z notifikace), by si z něj při další rekompozici brala instance
     * a spadla. Bez odkazů ho uklidí garbage collector; běžící práci zastaví zrušený scope.
     */
    private suspend fun discard(account: AccountSession, destroy: Boolean) {
        account.session.shutdown(destroy)
        account.deleteData()
        opened.update { it - account }
        account.scope.cancel()
    }

    private fun save() {
        val registry = registry ?: return
        val saved = SavedAccounts(
            accounts = _all.value.map { SavedAccount(it.slot.id, it.userId.value, it.pushReceiverId.value) },
            default = _default.value.slot.id,
        )
        runCatching { registry.save(saved) }.onFailure { logger.w(it) { "Seznam účtů se nepodařilo uložit." } }
    }

    companion object {
        /** Nejvýš tolik účtů naráz (návrh, „Rozhodnuto"); zvednout jde podle změřené paměti. */
        const val MAX_ACCOUNTS = 3

        private val LOGOUT_TIMEOUT = 30.seconds
    }
}

/**
 * Jeden účet: jeho slot a izolovaný kontejner Koinu se vším, co mu patří (`accountModule`).
 *
 * Izolovaný kontejner ([KoinApplication] mimo globální `startKoin`), ne Koin scope: scope by
 * byl uvnitř globálního Koinu, takže by `get()` bez scope tiše vrátil globální instanci,
 * a `createdAtStart` ve scope nefunguje (návrh, „Proč ne Koin scopes").
 */
class AccountSession internal constructor(
    val slot: AccountSlot,
    val application: KoinApplication,
    /** Scope účtu: v kontejneru je to scope aplikace, s odebráním účtu se zruší. */
    internal val scope: CoroutineScope,
    userId: Long?,
) {

    /** Kontejner účtu. Co v něm není, hledá v globálním Koinu (`openAccount`). */
    val koin: Koin get() = application.koin

    /** User id účtu, jakmile je přihlášený (a z uloženého seznamu hned po startu). */
    val userId: MutableStateFlow<Long?> = MutableStateFlow(userId)

    /**
     * Id příjemce pushů z poslední registrace (`registerDevice`). Uložené, protože push budí
     * i mrtvý proces, a ten se ještě nestihl zaregistrovat znovu.
     */
    val pushReceiverId: MutableStateFlow<Long?> = MutableStateFlow(null)

    val session: TelegramSession get() = koin.get()

    inline fun <reified T : Any> get(): T = koin.get()

    /** Adresáře TDLib a klíč databáze. U dalšího slotu i jeho adresář v `accounts/`. */
    internal suspend fun deleteData() {
        koin.getOrNull<TelegramPaths>()?.let { paths ->
            val fs = FileSystem.SYSTEM
            runCatching {
                fs.deleteRecursively(paths.databaseDirectory.toPath(), mustExist = false)
                fs.deleteRecursively(paths.filesDirectory.toPath(), mustExist = false)
                if (!slot.isPrimary) paths.databaseDirectory.toPath().parent?.let { fs.deleteRecursively(it, mustExist = false) }
            }.onFailure { Logger.withTag("Accounts").w(it) { "Data účtu ${slot.id} se nepodařilo smazat." } }
        }
        runCatching { koin.getOrNull<DatabaseKeyStore>()?.clear() }
    }
}

/** Kontejner výchozího účtu pro vstupní bod bez kompozice a bez slotu (služba, přijímač, úloha). */
fun Koin.defaultAccount(): Koin = get<Accounts>().default.value.koin

/** Kontejner účtu [slot] pro vstupní bod bez kompozice; `null`, když už není přihlášený. */
fun Koin.account(slot: AccountSlot): Koin? = get<Accounts>().find(slot)?.koin

private fun randomSlotId(): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return CharArray(6) { alphabet[Random.nextInt(alphabet.length)] }.concatToString()
}
