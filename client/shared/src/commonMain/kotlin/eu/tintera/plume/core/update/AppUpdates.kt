package eu.tintera.plume.core.update

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * Aktualizace aplikace z obchodu.
 *
 * Hranice stejně jako kolem TDLib a Firebase: API obchodu (`com.google.android.play.*`) je
 * importované jen v platformní implementaci, zbytek aplikace vidí [AppUpdateState]. Na iOS žádné
 * API na aktualizace není, takže tam stav zůstává [AppUpdateState.None].
 */
expect class AppUpdates {

    /** Co obchod o aktualizaci ví. Mění se po [check] a během stahování. */
    val state: StateFlow<AppUpdateState>

    /**
     * Zeptá se obchodu. Volá se při každém návratu na seznam chatů (resume) — dotaz jde do
     * aplikace obchodu v telefonu, ne na server, a právě tak se po restartu aplikace připomene
     * stažená, ale nenainstalovaná aktualizace.
     */
    fun check()

    /**
     * Jestli tahle instalace umí aktualizace vůbec zjistit. iOS ne (API nemá), desktop jen
     * zabalená aplikace, ne ladicí běh. Android ano — jestli je instalace z Play, řekne až
     * [checkNow]. „O aplikaci" podle toho řádek s kontrolou vůbec ukáže.
     */
    val canCheck: Boolean

    /**
     * Ruční kontrola z „O aplikaci": na rozdíl od [check] počká na odpověď a řekne, jak dopadla —
     * uživatel, který ťukl na „Zkontrolovat", chce slyšet i „máte nejnovější verzi". Obejde
     * interval, kterým se šetří dotazy při návratech na seznam chatů. [state] se mění stejně
     * jako po [check], takže nabídku pak ukáže i karta v seznamu chatů.
     */
    suspend fun checkNow(): UpdateCheckResult

    /** Nainstaluje staženou aktualizaci. Aplikace se tím restartuje. */
    fun complete()
}

/**
 * Spuštění aktualizace. Na Androidu otevře potvrzovací dialog obchodu, který potřebuje activity
 * — proto je to composable, ne metoda [AppUpdates], které žije v grafu závislostí bez ní.
 */
@Composable
expect fun rememberStartUpdate(updates: AppUpdates): () -> Unit

/** Jak dopadla ruční kontrola ([AppUpdates.checkNow]). */
enum class UpdateCheckResult {
    /** Novější verze není. */
    UpToDate,

    /** Je — podrobnosti (k mání, stahuje se, staženo) nese [AppUpdates.state]. */
    Found,

    /** Tahle instalace se aktualizací nedozví: mimo Google Play (debug, `adb install`), iOS. */
    Unsupported,

    /** Obchod nebo GitHub neodpověděl (bez sítě, výpadek). */
    Failed,
}

sealed interface AppUpdateState {

    /** Nic k aktualizaci, nebo se to nedá zjistit (debug build, instalace mimo obchod, iOS). */
    data object None : AppUpdateState

    /** V obchodě je novější verze. */
    data class Available(val versionCode: Int) : AppUpdateState

    /** Stahuje se na pozadí; [progress] je `null`, dokud obchod nezná velikost. */
    data class Downloading(val versionCode: Int, val progress: Float?) : AppUpdateState

    /** Staženo, čeká na restart. */
    data class Downloaded(val versionCode: Int) : AppUpdateState
}

/**
 * Co ukázat v seznamu chatů, nebo `null` pro nic.
 *
 * Zavřít jde jen nabídku ([AppUpdateState.Available]), a zavřená se pamatuje podle
 * `versionCode` — tatáž verze se znovu nevnucuje, další vydaná ano. Rozběhnuté stahování
 * a stažená aktualizace se ukazují vždycky: o ty si uživatel řekl sám a stažená aktualizace,
 * o které se neví, by na restart čekala napořád.
 */
fun updateCard(state: AppUpdateState, dismissedVersionCode: Int?): AppUpdateState? = when (state) {
    AppUpdateState.None -> null
    is AppUpdateState.Available -> state.takeIf { it.versionCode != dismissedVersionCode }
    is AppUpdateState.Downloading, is AppUpdateState.Downloaded -> state
}
