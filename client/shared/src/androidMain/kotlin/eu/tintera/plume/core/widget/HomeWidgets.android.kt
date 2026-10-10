package eu.tintera.plume.core.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

class AndroidHomeWidgets(private val context: Context) : HomeWidgets {

    private val manager get() = AppWidgetManager.getInstance(context)

    // Launcher bez podpory (některé starší nebo cizí) žádost tiše zahodí — položku pak nenabízet.
    override val canPin: Boolean get() = manager.isRequestPinAppWidgetSupported

    override fun pinChats() {
        manager.requestPinAppWidget(ComponentName(context, ChatsWidgetReceiver::class.java), null, null)
    }
}
