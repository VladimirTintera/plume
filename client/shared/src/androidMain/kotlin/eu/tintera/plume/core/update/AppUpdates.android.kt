package eu.tintera.plume.core.update

import android.content.Context
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
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val manager = AppUpdateManagerFactory.create(context.applicationContext)

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
