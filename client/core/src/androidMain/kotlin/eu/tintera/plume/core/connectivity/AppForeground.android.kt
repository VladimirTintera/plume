package eu.tintera.plume.core.connectivity

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Popředí podle počtu spuštěných activit.
 *
 * Schválně bez `ProcessLifecycleOwner`: ten je v `androidx.lifecycle:lifecycle-process`, tedy
 * v androidx větvi knihovny, zatímco projekt jede na multiplatformní `org.jetbrains.androidx.lifecycle`.
 * Míchat dvě verze téže knihovny kvůli jednomu příznaku nestojí za to — `onStart`/`onStop` říkají
 * přesně totéž.
 *
 * **Počítá se od vzniku objektu, ne od odběru.** Dřív se callbacky registrovaly až s odběrem
 * a počítadlo začínalo na „žádná activity" — kdo se přihlásil až po `onStart` (zámek aplikace
 * po přečtení záznamu z Keystore), tomu počítadlo při odchodu do pozadí spadlo pod nulu, změna
 * na „v pozadí" se neohlásila a zámek se po návratu nezamkl. Teď je jeden stav pro celý proces,
 * výchozí hodnota se čte ze systému a počítadlo pod nulu nejde.
 */
actual class AppForeground(context: Context) {

    private val application = context.applicationContext as Application

    private val state = MutableStateFlow(isProcessForeground())

    /** Spuštěné activity; jen z hlavního vlákna (callbacky `Application`). */
    private var started = 0

    actual val isForeground: Flow<Boolean> = state.asStateFlow()

    init {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {

            override fun onActivityStarted(activity: Activity) {
                started++
                state.value = true
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                /*
                 * Otočení displeje je `onStop` staré instance a `onStart` nové. Bez téhle
                 * výjimky by mezi nimi aplikace na okamžik spadla „do pozadí" a při návratu
                 * zbytečně znovuotevřela všechna spojení.
                 */
                if (!activity.isChangingConfigurations) state.value = started > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /**
     * Stav v okamžiku vzniku. Koin startuje v `Application.onCreate`, dřív než jakákoli activity,
     * takže na tom většinou nezáleží — ale kdyby objekt vznikl později, nesmí začít lží.
     */
    private fun isProcessForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        // Jen FOREGROUND, ne „visible": to zahrnuje i službu v popředí (sdílení polohy), která
        // žádnou activity nemá. Chybné „v pozadí" opraví první `onActivityStarted`, chybné
        // „v popředí" by nic neopravilo.
        return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}
