package eu.tintera.plume.features.lockedchats

import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.resources.locked_chat_notification_title
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.core.adaptive.WindowControlsEndSpace
import eu.tintera.plume.core.adaptive.WindowControlsSpace
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.lock.APP_LOCK_PIN_LENGTH
import eu.tintera.plume.core.lock.AppLock
import eu.tintera.plume.core.lock.AppLockState
import eu.tintera.plume.core.lock.BiometricResult
import eu.tintera.plume.core.lock.LockedChatsSession
import eu.tintera.plume.core.lock.PinResult
import eu.tintera.plume.core.lock.SystemLockClock
import eu.tintera.plume.core.lock.rememberBiometricAuth
import eu.tintera.plume.core.settings.AccountSettings
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.features.settings.PinStep
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.app_lock_biometrics_revoked
import eu.tintera.plume.resources.app_lock_blocked
import eu.tintera.plume.resources.app_lock_wrong_pin
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.locked_chats_biometrics_prompt
import eu.tintera.plume.resources.locked_chats_need_app_lock
import eu.tintera.plume.resources.locked_chats_need_app_lock_title
import eu.tintera.plume.resources.locked_chats_unlock_title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Brána před obrazovkou jednoho chatu: zamčený chat ([AccountSettings.lockedChats]) se ukáže až
 * po odemčení ([LockedChatsSession]), jinak je místo něj číselník zámku aplikace. Jde přes ni
 * KAŽDÁ obrazovka chatu (konverzace, témata, profil, bublina — `ChatColorTheme`), takže je
 * jedno, odkud se chat otevírá: z notifikace, widgetu, odkazu, zkratky, nebo z obnoveného
 * zásobníku po restartu. Po odchodu do pozadí se sezení zamkne a otevřená konverzace se tu
 * schová znovu.
 *
 * - **Dokud se sada nenačte (`null`), není vidět nic** — ani chat, ani číselník.
 * - **Obsah se pod zamčenou bránou vůbec neskládá**: ViewModel konverzace nevznikne, nic se
 *   nenačte a hlavně nic nehlásí přečtení.
 * - **Bez zámku aplikace se zamčený chat otevřít nedá** (není čím ověřit): vysvětlení, kde
 *   zámek zapnout. Stane se to, když se zámek vypne, nebo zahodí odhlášením jiného účtu.
 * - **[onBack] zavře obrazovku**: šipka v hlavičce brány (bez ní šlo zpět jen gestem) — a když
 *   se chat zamkne, zatímco je otevřený (⋮ → Zamknout chat). Konverzace pak odejde, místo aby
 *   uživateli, který ji právě zamkl, ukázala číselník. Návrat z pozadí obrazovku nezavírá:
 *   zamkne se sezení, ne chat, a po PINu je uživatel tam, kde byl.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedChatGate(chatId: Long, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    val settings = koinInject<AccountSettings>()
    val session = koinInject<LockedChatsSession>()
    val locked by settings.lockedChats.collectAsStateWithLifecycle()
    val unlocked by session.unlocked.collectAsStateWithLifecycle()
    val currentBack by rememberUpdatedState(onBack)
    val isLocked = locked?.let { chatId in it }
    // Jak byl chat zamčený naposledy; `null` = sada se ještě nenačetla, nic se neporovnává.
    var lastLocked by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isLocked) {
        if (isLocked == null) return@LaunchedEffect
        if (isLocked && lastLocked == false) currentBack?.invoke()
        lastLocked = isLocked
    }
    if (isLocked == null) return
    if (!isLocked || unlocked) {
        content()
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (onBack != null) {
            TopAppBar(
                title = { Text(stringResource(Res.string.locked_chat_notification_title)) },
                navigationIcon = {
                    WindowControlsSpace {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                        }
                    }
                },
                actions = { WindowControlsEndSpace() },
            )
        }
        LockedChatsUnlock(onUnlocked = session::unlock, modifier = Modifier.weight(1f))
    }
}

/**
 * Číselník pro odemčení zamčených chatů — PIN a biometrie zámku aplikace, se stejnou brzdou
 * pokusů ([AppLock.verify] počítá do brzdy taky). Volá ho brána i seznam zamčených chatů.
 */
@Composable
fun LockedChatsUnlock(onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val appLock = koinInject<AppLock>()
    val lockState by appLock.state.collectAsStateWithLifecycle()
    val lockSettings by appLock.settings.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val surface = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)

    if (lockState is AppLockState.Disabled) {
        Box(modifier.then(surface)) {
            IntroEmptyState(
                icon = Res.drawable.lock_24dp,
                title = stringResource(Res.string.locked_chats_need_app_lock_title),
                text = stringResource(Res.string.locked_chats_need_app_lock),
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    val biometricAuth = rememberBiometricAuth()
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var revoked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorTrigger by remember { mutableIntStateOf(0) }
    var blockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemLockClock.elapsedMillis()) }
    val promptTitle = stringResource(Res.string.locked_chats_biometrics_prompt)

    LaunchedEffect(blockedUntil) {
        while (blockedUntil > SystemLockClock.elapsedMillis()) {
            now = SystemLockClock.elapsedMillis()
            delay(500L)
        }
        now = SystemLockClock.elapsedMillis()
    }
    val isBlocked = blockedUntil > now

    val biometrics: (() -> Unit)? = if (lockSettings?.biometrics == true && biometricAuth.isSupported) {
        {
            scope.launch {
                when (biometricAuth.authenticate(promptTitle)) {
                    BiometricResult.Success -> {
                        haptics.confirm()
                        onUnlocked()
                    }
                    // Změněné otisky biometrii vypnou i tady, jako na obrazovce zámku.
                    BiometricResult.Invalidated -> {
                        appLock.revokeBiometrics()
                        revoked = true
                    }
                    BiometricResult.Failed -> Unit
                }
            }
        }
    } else {
        null
    }

    // Biometrie se nabídne sama, jako na obrazovce zámku aplikace.
    LaunchedEffect(Unit) { biometrics?.invoke() }

    PinStep(
        title = stringResource(Res.string.locked_chats_unlock_title),
        errorText = when {
            isBlocked -> stringResource(Res.string.app_lock_blocked, ((blockedUntil - now + 999) / 1000).coerceAtLeast(1))
            revoked -> stringResource(Res.string.app_lock_biometrics_revoked)
            wrong -> stringResource(Res.string.app_lock_wrong_pin)
            else -> null
        },
        filledCount = pin.length,
        errorTrigger = errorTrigger,
        isError = isBlocked || wrong || revoked,
        onDigit = { digit ->
            if (busy || pin.length >= APP_LOCK_PIN_LENGTH) return@PinStep
            pin += digit
            wrong = false
            if (pin.length == APP_LOCK_PIN_LENGTH) {
                busy = true
                val entered = pin
                scope.launch {
                    when (val result = appLock.verify(entered)) {
                        PinResult.Success -> {
                            haptics.confirm()
                            onUnlocked()
                        }
                        is PinResult.Wrong -> {
                            haptics.reject()
                            wrong = true
                            errorTrigger++
                            blockedUntil = result.blockedUntil
                        }
                        is PinResult.Blocked -> {
                            haptics.reject()
                            errorTrigger++
                            blockedUntil = result.blockedUntil
                        }
                    }
                    pin = ""
                    busy = false
                }
            }
        },
        onDelete = { if (!busy) pin = pin.dropLast(1) },
        onBiometrics = biometrics,
        enabled = !isBlocked && !busy,
        modifier = modifier.then(surface).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    )
}
