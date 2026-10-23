package eu.tintera.plume.core.files

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import co.touchlab.kermit.Logger

/**
 * Správce stahování do veřejných Stažených (s notifikací průběhu i hotového souboru). Od Androidu
 * 10 to žádné oprávnění nechce; starší by k zápisu do Stažených chtěl `WRITE_EXTERNAL_STORAGE`
 * za běhu, a tam se proto adresa otevře v prohlížeči, který si stahování obstará sám.
 */
actual class WebFileDownloader(private val context: Context) {

    private val logger = Logger.withTag("WebFileDownloader")

    actual fun download(url: String, fileName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return@runCatching
        }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        context.getSystemService(DownloadManager::class.java).enqueue(request)
    }
        .onFailure { logger.w(it) { "Stahování $url nejde spustit." } }
        .isSuccess
}
