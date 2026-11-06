package eu.tintera.plume.core.ai

import android.content.Context
import co.touchlab.kermit.Logger
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallHelper
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import eu.tintera.plume.ai.engine.LlmEngineFactory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android: runtime LiteRT-LM je součást z Google Play stahovaná až na požádání
 * (:client:litertFeature, Play Feature Delivery). Do základu aplikace by přidal 23 MB nativní
 * knihovny na ABI, a model v zařízení si zapne málokdo.
 *
 * - **Instalaci dělá Play**, aplikace jen poslouchá. Když obrazovka odejde, instalace běží dál;
 *   další [install] se na ni napojí (`SplitInstallManager.getSessionStates`) místo druhé, kterou
 *   by Play odmítl.
 * - **Doinstalovaná součást je vidět až po [SplitCompat.install]** — bez něj by ji classloader
 *   neviděl do restartu procesu. Po restartu ji systém načte sám jako běžný split APK.
 * - **Nativní knihovnu načte [SplitInstallHelper.loadLibrary] dřív než LiteRT-LM**: ta si ji
 *   bere `System.loadLibrary`, který v čerstvě doinstalovaném splitu cestu najít nemusí; už
 *   načtenou knihovnu pak linker najde podle jména.
 * - **Mimo Google Play to nejde** (APK z `assembleDebug`, jiný obchod) — instalace selže a model
 *   se nestáhne. Pro vývoj se součást instaluje spolu s aplikací, viz
 *   docs/poznatky/ai-v-zarizeni.md, „Runtime jako součást z Google Play".
 */
class PlayLiteRtRuntime(context: Context) : LiteRtRuntime {

    private val context = context.applicationContext
    private val manager by lazy { SplitInstallManagerFactory.create(this.context) }
    private val logger = Logger.withTag("OnDeviceAi")

    @Volatile
    private var engines: LlmEngineFactory? = null

    override fun installed(): Boolean = MODULE in manager.installedModules

    override fun install(): Flow<ModelDownload> = callbackFlow {
        if (installed()) {
            close()
            awaitClose()
            return@callbackFlow
        }
        var sessionId = 0
        var done = false
        val listener = SplitInstallStateUpdatedListener { state ->
            if (MODULE !in state.moduleNames()) return@SplitInstallStateUpdatedListener
            when (state.status()) {
                SplitInstallSessionStatus.DOWNLOADING ->
                    trySend(ModelDownload(state.bytesDownloaded(), state.totalBytesToDownload()))
                SplitInstallSessionStatus.INSTALLED -> {
                    done = true
                    SplitCompat.install(context)
                    logger.i { "Runtime LiteRT-LM doinstalovaný" }
                    close()
                }
                SplitInstallSessionStatus.FAILED -> {
                    done = true
                    close(RuntimeInstallException(state.errorCode()))
                }
                SplitInstallSessionStatus.CANCELED -> {
                    done = true
                    close(DownloadStoppedException())
                }
                // Play chce souhlas jen u součásti nad 150 MB; tahle má desítky MB. Kdyby
                // přesto přišel, obrazovka by bez aktivity neměla jak ho ukázat.
                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    done = true
                    close(RuntimeInstallException(cause = IllegalStateException("Play requires user confirmation")))
                }
                else -> Unit
            }
        }
        manager.registerListener(listener)

        fun start() {
            manager.startInstall(SplitInstallRequest.newBuilder().addModule(MODULE).build())
                .addOnSuccessListener { id ->
                    sessionId = id
                    // Nula = nic se stahovat nemusí, součást už mezitím je.
                    if (id == 0) {
                        done = true
                        close()
                    }
                }
                .addOnFailureListener { e ->
                    done = true
                    close(RuntimeInstallException(code = (e as? SplitInstallException)?.errorCode, cause = e))
                }
        }
        manager.sessionStates
            .addOnSuccessListener { states ->
                val running = states.firstOrNull { MODULE in it.moduleNames() && it.isActive() }
                if (running != null) sessionId = running.sessionId() else start()
            }
            .addOnFailureListener { start() }

        awaitClose {
            manager.unregisterListener(listener)
            // Zastavil uživatel (nebo odešel celý runtime) — Play by jinak stahoval dál.
            if (!done && sessionId != 0) manager.cancelInstall(sessionId)
        }
    }

    override fun engines(): LlmEngineFactory = engines ?: synchronized(this) {
        engines ?: run {
            SplitCompat.install(context)
            SplitInstallHelper.loadLibrary(context, NATIVE_LIBRARY)
            loadEngineFactory(context.classLoader).also { engines = it }
        }
    }

    private fun SplitInstallSessionState.isActive() = status() in ACTIVE

    private companion object {
        /** Jméno modulu :client:litertFeature (`dist:module` v jeho manifestu bere jméno projektu). */
        const val MODULE = "litertFeature"

        /** `jni/<abi>/liblitertlm_jni.so` v AAR `litertlm-android`. */
        const val NATIVE_LIBRARY = "litertlm_jni"

        val ACTIVE = setOf(
            SplitInstallSessionStatus.PENDING,
            SplitInstallSessionStatus.DOWNLOADING,
            SplitInstallSessionStatus.DOWNLOADED,
            SplitInstallSessionStatus.INSTALLING,
        )
    }
}
