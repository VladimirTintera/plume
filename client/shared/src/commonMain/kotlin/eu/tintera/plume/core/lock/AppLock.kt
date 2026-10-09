package eu.tintera.plume.core.lock

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.secrets.AppLockStore
import eu.tintera.plume.core.telegram.AuthStage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/** Co zámek právě ukazuje. */
sealed interface AppLockState {

    /** Záznam se ještě čte. Obrazovka do té doby nic z aplikace neukazuje. */
    data object Unknown : AppLockState

    data object Disabled : AppLockState

    data object Unlocked : AppLockState

    /**
     * Zamčeno. [blockedUntil] je okamžik v [LockClock.elapsedMillis], do kdy se nesmí zkoušet PIN,
     * 0 = smí se hned; [biometrics] říká, jestli si uživatel zapnul odemykání otiskem nebo obličejem.
     */
    data class Locked(val blockedUntil: Long, val biometrics: Boolean) : AppLockState
}

/** Nastavení zapnutého zámku, jak ho ukazuje obrazovka v Nastavení. */
data class AppLockSettings(val timeoutSeconds: Int, val biometrics: Boolean)

sealed interface PinResult {
    data object Success : PinResult

    /**
     * Špatný PIN; [blockedUntil] je konec brzdy, kterou tenhle pokus spustil, v
     * [LockClock.elapsedMillis] (0 = žádná).
     */
    data class Wrong(val blockedUntil: Long) : PinResult

    /** Pokus se vůbec nezkoušel, brzda ještě běží. */
    data class Blocked(val blockedUntil: Long) : PinResult
}

/** Hodiny zámku; v testech se podstrkují. */
interface LockClock {
    /** Čas na zdi (ms od epochy) — běží i v hlubokém spánku, ale dá se přestavit. */
    fun wallMillis(): Long

    /**
     * Čas od startu systému (ms) včetně hlubokého spánku — přestavit nejde, ale restart ho vynuluje.
     * Proto ne `TimeSource.Monotonic`: ten na Androidu stojí ve spánku (`nanoTime`) a telefon,
     * který ležel přes noc, by hlásil „uběhlo pár vteřin".
     */
    fun elapsedMillis(): Long
}

object SystemLockClock : LockClock {
    override fun wallMillis(): Long = Clock.System.now().toEpochMilliseconds()
    override fun elapsedMillis(): Long = bootElapsedMillis()
}

/** Android `SystemClock.elapsedRealtime`, iOS `CLOCK_MONOTONIC` (na Darwinu běží i ve spánku). */
internal expect fun bootElapsedMillis(): Long

/** Doby, po kterých se aplikace v pozadí zamkne. „Hned" není: zamkl by ji každý výběr fotky. */
val APP_LOCK_TIMEOUTS: List<Int> = listOf(60, 5 * 60, 60 * 60, 5 * 60 * 60)

const val DEFAULT_APP_LOCK_TIMEOUT: Int = 60

/** Délka PINu. Pevná, takže se odesílá sám po poslední číslici. */
const val APP_LOCK_PIN_LENGTH: Int = 4

/** Kolik chybných pokusů projde bez brzdy. */
internal const val FREE_ATTEMPTS: Int = 5

private const val FIRST_BLOCK_MS: Long = 30_000
private const val MAX_BLOCK_MS: Long = 60 * 60 * 1000L
private const val PIN_ITERATIONS: Int = 10_000

/**
 * Jak dlouho se po [failures] chybných pokusech nesmí zkoušet další (ms). Prvních [FREE_ATTEMPTS]
 * projde, pak 30 s a každý další pokus dvojnásobek, nejvýš hodina.
 */
internal fun blockFor(failures: Int): Long {
    if (failures < FREE_ATTEMPTS) return 0
    val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(20)
    return (FIRST_BLOCK_MS shl doublings).coerceAtMost(MAX_BLOCK_MS)
}

/**
 * Kolik ms brzdy ještě zbývá v čase [now] ([LockClock.elapsedMillis]).
 *
 * Měří se čas od startu systému, ne čas na zdi: ten jde přestavit DOPŘEDU a každé posunutí
 * hodin by dalo další pokus. Čas od startu přestavit nejde, jen ho vynuluje restart — a to se
 * pozná podle toho, že je menší než při poslední chybě. Kolik času uběhlo před restartem, pak
 * nevíme, jistě ale aspoň [now]; počítá se tedy, jako by chyba přišla těsně před ním. Restart tak
 * brzdu nezruší a nikdy ji neprotáhne přes to, kolik za daný počet chyb smí trvat.
 */
internal fun blockRemaining(config: LockConfig, now: Long): Long {
    val block = blockFor(config.failures)
    if (block == 0L) return 0
    val since = if (now >= config.failedAt) now - config.failedAt else now
    return (block - since).coerceAtLeast(0)
}

/** Konec brzdy v [LockClock.elapsedMillis], 0 = žádná. */
internal fun blockedUntil(config: LockConfig, now: Long): Long =
    blockRemaining(config, now).let { if (it > 0) now + it else 0 }

/**
 * Má se aplikace po návratu do popředí zamknout? Rozhoduje čas od startu systému, který přestavit
 * nejde; čas na zdi se bere, když je delší — kdyby hodiny někdo posunul dopředu, zamkne se jen
 * dřív.
 */
internal fun shouldLockAfter(awayWallMs: Long, awayElapsedMs: Long, timeoutSeconds: Int): Boolean =
    maxOf(awayWallMs, awayElapsedMs) >= timeoutSeconds * 1000L

/**
 * Zámek aplikace PINem a biometrií. **Zamyká jen obrazovku**: klíč k databázi i push jdou
 * dál bez ověření (viz `DatabaseKeyStore`), takže notifikace chodí i se zamčenou aplikací.
 *
 * Studený start se zapnutým zámkem začíná zamčený; v pozadí se zamkne po [AppLockSettings.timeoutSeconds].
 * Odhlášení zámek zahodí — patří účtu, který tu už není.
 */
class AppLock(
    private val store: AppLockStore,
    private val scope: CoroutineScope,
    private val clock: LockClock = SystemLockClock,
    private val iterations: Int = PIN_ITERATIONS,
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Na hlavním vlákně a `immediate`: návrat do popředí ohlásí `onStart` activity (iOS
     * `didBecomeActive`) a zámek se tak nastaví ještě v tomtéž volání — dřív, než se vykreslí
     * první snímek. Na jiném vlákně by se mohl stihnout vykreslit starý obsah.
     */
    private val foregroundContext: CoroutineContext = Dispatchers.Main.immediate,
) {
    private val logger = Logger.withTag("AppLock")
    private val mutex = Mutex()
    private var config: LockConfig? = null

    private val _state = MutableStateFlow<AppLockState>(AppLockState.Unknown)
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    private val _settings = MutableStateFlow<AppLockSettings?>(null)

    /** `null` = zámek vypnutý. */
    val settings: StateFlow<AppLockSettings?> = _settings.asStateFlow()

    private var backgroundWall: Long? = null
    private var backgroundElapsed: Long = 0

    /** Načte záznam a pak sleduje popředí; [foreground] se ozve hned aktuálním stavem. */
    fun start(foreground: Flow<Boolean>, authStage: Flow<AuthStage>) {
        scope.launch {
            mutex.withLock { load() }
            launch {
                authStage.collect {
                    if (it is AuthStage.LoggingOut || it is AuthStage.WaitPhoneNumber) reset()
                }
            }
            withContext(foregroundContext) {
                foreground.collect { if (it) onForeground() else onBackground() }
            }
        }
    }

    private suspend fun load() {
        val bytes = runCatching { store.load() }.getOrNull()
        val loaded = bytes?.let(::decodeLockConfig)
        if (bytes != null && loaded == null) {
            logger.w { "Záznam zámku nejde přečíst, zahazuji ho." }
            runCatching { store.clear() }
        }
        config = loaded
        _settings.value = loaded?.settings()
        _state.value = loaded?.lockedState() ?: AppLockState.Disabled
    }

    private suspend fun onBackground() = mutex.withLock {
        if (config != null && _state.value == AppLockState.Unlocked) {
            backgroundWall = clock.wallMillis()
            backgroundElapsed = clock.elapsedMillis()
        }
    }

    private suspend fun onForeground() = mutex.withLock {
        val config = config
        val wall = backgroundWall
        backgroundWall = null
        if (config == null || wall == null || _state.value != AppLockState.Unlocked) return@withLock
        val away = shouldLockAfter(
            awayWallMs = clock.wallMillis() - wall,
            awayElapsedMs = clock.elapsedMillis() - backgroundElapsed,
            timeoutSeconds = config.timeoutSeconds,
        )
        if (away) _state.value = config.lockedState()
    }

    /** Odemknout PINem. */
    suspend fun unlock(pin: String): PinResult = attempt(pin, unlock = true)

    /**
     * Ověřit PIN před změnou nastavení. Počítá do brzdy stejně jako odemykání — jinak by se PIN
     * dal hádat přes Nastavení odemčené aplikace —, ale zámek tím nemění.
     */
    suspend fun verify(pin: String): PinResult = attempt(pin, unlock = false)

    private suspend fun attempt(pin: String, unlock: Boolean): PinResult = mutex.withLock {
        val current = config ?: return@withLock PinResult.Success
        val now = clock.elapsedMillis()
        val blocked = blockedUntil(current, now)
        if (blocked > 0) return@withLock PinResult.Blocked(blocked)
        val hash = hash(pin, current.salt, current.iterations)
        if (constantTimeEquals(hash, current.hash)) {
            save(current.copy(failures = 0, failedAt = 0))
            if (unlock) _state.value = AppLockState.Unlocked
            PinResult.Success
        } else {
            val next = current.copy(failures = current.failures + 1, failedAt = now)
            save(next)
            if (_state.value is AppLockState.Locked) _state.value = config!!.lockedState()
            PinResult.Wrong(blockedUntil(next, now))
        }
    }

    /**
     * Odemknout po úspěšném ověření biometrií. Jde i během brzdy a brzdu ruší: otisk prstu
     * hádáním nikdo neobejde a ten, kdo PIN zapomněl, se jinak dostane dovnitř jen odhlášením.
     */
    suspend fun unlockWithBiometrics() = mutex.withLock {
        val current = config ?: return@withLock
        if (!current.biometrics) return@withLock
        save(current.copy(failures = 0, failedAt = 0))
        _state.value = AppLockState.Unlocked
    }

    /** Zapne zámek s novým PINem; aplikace zůstane odemčená. */
    suspend fun enable(pin: String): Boolean = mutex.withLock {
        val salt = store.generate()
        val next = LockConfig(
            iterations = iterations,
            salt = salt,
            hash = hash(pin, salt, iterations),
            timeoutSeconds = DEFAULT_APP_LOCK_TIMEOUT,
            biometrics = false,
        )
        save(next).also { if (it) _state.value = AppLockState.Unlocked }
    }

    suspend fun changePin(pin: String): Boolean = mutex.withLock {
        val current = config ?: return@withLock false
        val salt = store.generate()
        save(current.copy(iterations = iterations, salt = salt, hash = hash(pin, salt, iterations), failures = 0, failedAt = 0))
    }

    suspend fun setTimeout(seconds: Int): Boolean = mutex.withLock {
        val current = config ?: return@withLock false
        save(current.copy(timeoutSeconds = seconds))
    }

    suspend fun setBiometrics(enabled: Boolean): Boolean = mutex.withLock {
        val current = config ?: return@withLock false
        save(current.copy(biometrics = enabled))
    }

    /**
     * Vypne biometrii, protože se v telefonu změnily otisky nebo tváře ([BiometricResult.Invalidated]).
     * Na rozdíl od [setBiometrics] mění i stav zamčené aplikace — zámek pak ikonu otisku nenabízí
     * a zbývá PIN.
     */
    suspend fun revokeBiometrics() = mutex.withLock {
        val current = config ?: return@withLock
        if (!current.biometrics) return@withLock
        logger.i { "Seznam otisků v telefonu se změnil, vypínám odemykání biometrií." }
        val next = current.copy(biometrics = false)
        if (save(next) && _state.value is AppLockState.Locked) _state.value = next.lockedState()
    }

    /** Vypne zámek (z Nastavení, po ověření PINu). */
    suspend fun disable() = mutex.withLock { clear() }

    /** Zahodí zámek při odhlášení: patří účtu, ne zařízení. */
    suspend fun reset() = mutex.withLock {
        if (config != null) logger.i { "Odhlášení, zahazuji zámek aplikace." }
        clear()
    }

    private suspend fun clear() {
        runCatching { store.clear() }.onFailure { logger.w(it) { "Záznam zámku nejde smazat." } }
        config = null
        backgroundWall = null
        _settings.value = null
        _state.value = AppLockState.Disabled
    }

    private suspend fun save(next: LockConfig): Boolean =
        runCatching { store.store(next.encode()) }
            .onFailure { logger.w(it) { "Záznam zámku nejde uložit." } }
            .onSuccess {
                config = next
                _settings.value = next.settings()
            }
            .isSuccess

    private suspend fun hash(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        withContext(hashDispatcher) { pbkdf2Sha256(pin.encodeToByteArray(), salt, iterations) }

    private fun LockConfig.settings() = AppLockSettings(timeoutSeconds, biometrics)

    private fun LockConfig.lockedState() =
        AppLockState.Locked(blockedUntil(this, clock.elapsedMillis()), biometrics)
}
