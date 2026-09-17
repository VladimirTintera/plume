package eu.tintera.plume.core.connectivity

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Popředí podle počtu spuštěných activit.
 *
 * Schválně bez `ProcessLifecycleOwner`: ten je v `androidx.lifecycle:lifecycle-process`, tedy
 * v androidx větvi knihovny, zatímco projekt jede na multiplatformní `org.jetbrains.androidx.lifecycle`.
 * Míchat dvě verze téže knihovny kvůli jednomu příznaku nestojí za to — `onStart`/`onStop` říkají
 * přesně totéž.
 */
actual class AppForeground(context: Context) {

    private val application = context.applicationContext as Application

    actual val isForeground: Flow<Boolean> = callbackFlow {
        var started = 0

        val callbacks = object : Application.ActivityLifecycleCallbacks {

            override fun onActivityStarted(activity: Activity) {
                started++
                trySend(true)
            }

            override fun onActivityStopped(activity: Activity) {
                started--
                /*
                 * Otočení displeje je `onStop` staré instance a `onStart` nové. Bez téhle
                 * výjimky by mezi nimi aplikace na okamžik spadla „do pozadí" a při návratu
                 * zbytečně znovuotevřela všechna spojení.
                 */
                if (!activity.isChangingConfigurations) trySend(started > 0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }

        /*
         * Výchozí hodnota je „na pozadí": při startu procesu z pushe žádná activity neběží
         * a `onActivityStarted` dorazí, teprve až uživatel aplikaci otevře.
         */
        trySend(false)

        application.registerActivityLifecycleCallbacks(callbacks)
        awaitClose { application.unregisterActivityLifecycleCallbacks(callbacks) }
    }
        .distinctUntilChanged()
}
