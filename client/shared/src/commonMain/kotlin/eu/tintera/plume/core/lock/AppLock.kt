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
import kotlin.time.Clock
import kotlin.time.TimeSource

/** Co zámek právě ukazuje. */
sealed interface AppLockState {

    /** Záznam se ještě čte. Obrazovka do té doby nic z aplikace neukazuje. */
    data object Unknown : AppLockState

    data object Disabled : AppLockState

    data object Unlocked : AppLockState

    /**
     * Zamčeno. [blockedUntil] je okamžik (ms od epochy), do kdy se nesmí zkoušet PIN, 0 = smí se
     * hned; [biometrics] říká, jestli si uživatel zapnul odemykání otiskem nebo obličejem.
     */
    data class Locked(val blockedUntil: Long, val biometrics: Boolean) : AppLockState
}

/** Nastavení zapnutého zámku, jak ho ukazuje obrazovka v Nastavení. */
data class AppLockSettings(val timeoutSeconds: Int, val biometrics: Boolean)

sealed interface PinResult {
    data object Success : PinResult

    /** Špatný PIN; [blockedUntil] je konec brzdy, kterou tenhle pokus spustil (0 = žádná). */
    data class Wrong(val blockedUntil: Long) : PinResult

    /** Pokus se vůbec nezkoušel, brzda ještě běží. */
    data class Blocked(val blockedUntil: Long) : PinResult
}

/** Hodiny zámku; v testech se podstrkují. */
interface LockClock {
    /** Čas na zdi (ms od epochy) — běží i v hlubokém spánku, ale dá se přestavit. */
    fun wallMillis(): Long

    /** Monotónní čas procesu (ms) — přestavit nejde, ale v hlubokém spánku stojí. */
    fun elapsedMillis(): Long
}

object SystemLockClock : LockClock {
    private val start = TimeSource.Monotonic.markNow()
    override fun wallMillis(): Long = Clock.System.now().toEpochMilliseconds()
    override fun elapsedMillis(): Long = start.elapsedNow().inWholeMilliseconds
}

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
 * Do kdy platí brzda. Hodiny na zdi jdou přestavit dozadu a brzda by se tím protáhla na dny —
 * víc, než kolik za daný počet pokusů smí trvat, proto se zkrátí.
 */
internal fun effectiveBlockedUntil(config: LockConfig, now: Long): Long {
    if (config.blockedUntil <= now) return 0
    return config.blockedUntil.coerceAtMost(now + blockFor(config.failures))
}

/**
 * Má se aplikace po návratu do popředí zamknout? Bere se DELŠÍ ze dvou měření: monotónní čas
 * v hlubokém spánku stojí (Android `nanoTime`), čas na zdi jde přestavit dozadu. Každé samo by
 * šlo obejít, obě naráz ne.
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
            foreground.collect { if (it) onForeground() else onBackground() }
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
        val now = clock.wallMillis()
        val blocked = effectiveBlockedUntil(current, now)
        if (blocked > now) return@withLock PinResult.Blocked(blocked)
        val hash = hash(pin, current.salt, current.iterations)
        if (constantTimeEquals(hash, current.hash)) {
            save(current.copy(failures = 0, blockedUntil = 0))
            if (unlock) _state.value = AppLockState.Unlocked
            PinResult.Success
        } else {
            val failures = current.failures + 1
            val block = blockFor(failures)
            val until = if (block > 0) now + block else 0
            save(current.copy(failures = failures, blockedUntil = until))
            if (_state.value is AppLockState.Locked) _state.value = config!!.lockedState()
            PinResult.Wrong(until)
        }
    }

    /**
     * Odemknout po úspěšném ověření biometrií. Jde i během brzdy a brzdu ruší: otisk prstu
     * hádáním nikdo neobejde a ten, kdo PIN zapomněl, se jinak dostane dovnitř jen odhlášením.
     */
    suspend fun unlockWithBiometrics() = mutex.withLock {
        val current = config ?: return@withLock
        if (!current.biometrics) return@withLock
        save(current.copy(failures = 0, blockedUntil = 0))
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
        save(current.copy(iterations = iterations, salt = salt, hash = hash(pin, salt, iterations), failures = 0, blockedUntil = 0))
    }

    suspend fun setTimeout(seconds: Int): Boolean = mutex.withLock {
        val current = config ?: return@withLock false
        save(current.copy(timeoutSeconds = seconds))
    }

    suspend fun setBiometrics(enabled: Boolean): Boolean = mutex.withLock {
        val current = config ?: return@withLock false
        save(current.copy(biometrics = enabled))
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
        AppLockState.Locked(effectiveBlockedUntil(this, clock.wallMillis()), biometrics)
}
