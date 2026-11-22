package eu.tintera.plume.core.update

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Play In-App Updates v režimu **flexible**: stahuje se na pozadí a aplikace se dá dál používat.
 * Celoobrazovkový režim (immediate) schválně ne — přerušit rozepsanou zprávu kvůli aktualizaci
 * je horší než o ni přijít.
 *
 * **Funguje jen u instalace z Play** (i z interního tracku a internal app sharing). Debug build
 * ani APK přes `adb install` aktualizaci nenajdou — dotaz skončí chybou nebo „není k dispozici"
 * a karta se prostě neukáže.
 */
actual class AppUpdates(context: Context) {

    private val logger = Logger.withTag("AppUpdates")

    private val context = context.applicationContext

    private val manager = AppUpdateManagerFactory.create(this.context)

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.None)
    actual val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** Poslední odpověď obchodu — spuštění aktualizace ji potřebuje a verzi z ní bere i průběh. */
    private var info: AppUpdateInfo? = null

    /**
     * Průběh stahování. Registrovaný po celý život procesu: [AppUpdates] je singleton a stahování
     * běží v obchodě dál, i když obrazovka zrovna neexistuje.
     */
    private val listener = InstallStateUpdatedListener { install ->
        val versionCode = info?.availableVersionCode() ?: return@InstallStateUpdatedListener
        when (install.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING ->
                _state.value = AppUpdateState.Downloading(
                    versionCode,
                    progress(install.bytesDownloaded(), install.totalBytesToDownload()),
                )
            InstallStatus.DOWNLOADED -> _state.value = AppUpdateState.Downloaded(versionCode)
            InstallStatus.INSTALLED -> _state.value = AppUpdateState.None
            // Zrušené nebo spadlé stahování: obchod řekne, jestli je aktualizace pořád k mání.
            InstallStatus.FAILED, InstallStatus.CANCELED -> check()
            else -> Unit
        }
    }

    init {
        manager.registerListener(listener)
    }

    actual fun check() {
        manager.appUpdateInfo
            .addOnSuccessListener { current ->
                info = current
                _state.value = current.toState()
            }
            .addOnFailureListener { failure ->
                // Očekávané u všeho, co nepřišlo z Play (debug, adb install) — proto ne `w`.
                logger.i { "Obchod o aktualizaci nic neřekl: ${failure.message}" }
            }
    }

    actual val canCheck: Boolean = true

    /**
     * Instalace mimo Play se pozná napřed podle instalátoru, ne až podle chyby obchodu: obchod
     * na cizí instalaci (debug, `adb install`) někdy odpoví chybou, jindy jen „aktualizace
     * není" — a „Máte nejnovější verzi" by u ladicího buildu lhalo.
     */
    actual suspend fun checkNow(): UpdateCheckResult {
        if (!installedFromPlay()) return UpdateCheckResult.Unsupported
        return suspendCancellableCoroutine { continuation ->
            manager.appUpdateInfo
                .addOnSuccessListener { current ->
                    info = current
                    val state = current.toState()
                    _state.value = state
                    continuation.resume(if (state == AppUpdateState.None) UpdateCheckResult.UpToDate else UpdateCheckResult.Found)
                }
                .addOnFailureListener { failure ->
                    logger.i { "Ruční kontrola aktualizace: obchod odpověděl chybou: ${failure.message}" }
                    val code = (failure as? InstallException)?.errorCode
                    continuation.resume(if (code in NOT_FROM_PLAY) UpdateCheckResult.Unsupported else UpdateCheckResult.Failed)
                }
        }
    }

    private fun installedFromPlay(): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
        return installer == PLAY_STORE
    }

    actual fun complete() {
        manager.completeUpdate()
    }

    internal fun start(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val current = info ?: return
        runCatching {
            manager.startUpdateFlowForResult(
                current,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
            )
        }.onFailure { failure -> logger.w(failure) { "Aktualizaci se nepodařilo spustit." } }
    }

    private fun AppUpdateInfo.toState(): AppUpdateState {
        val versionCode = availableVersionCode()
        return when {
            installStatus() == InstallStatus.DOWNLOADED -> AppUpdateState.Downloaded(versionCode)
            installStatus() == InstallStatus.DOWNLOADING || installStatus() == InstallStatus.PENDING ->
                AppUpdateState.Downloading(versionCode, progress(bytesDownloaded(), totalBytesToDownload()))
            updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateState.Available(versionCode)
            else -> AppUpdateState.None
        }
    }

    private fun progress(downloaded: Long, total: Long): Float? =
        if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null

    private companion object {
        /** Instalátor Google Play — i u interního tracku a internal app sharing. */
        const val PLAY_STORE = "com.android.vending"

        /** Chyby obchodu, které znamenají „tahle instalace není z Play", ne výpadek. */
        val NOT_FROM_PLAY = setOf(
            InstallErrorCode.ERROR_APP_NOT_OWNED,
            InstallErrorCode.ERROR_API_NOT_AVAILABLE,
            InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND,
        )
    }
}

/**
 * Dialog obchodu se spouští přes launcher activity, ne přes activity samotnou — je to cesta,
 * kterou Play doporučuje od verze 2 a která přežije otočení displeje. Výsledek dialogu se
 * nečte: stahování hlásí listener a zrušený dialog nechá stav na „k dispozici".
 */
@Composable
actual fun rememberStartUpdate(updates: AppUpdates): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    return remember(updates, launcher) { { updates.start(launcher) } }
}
