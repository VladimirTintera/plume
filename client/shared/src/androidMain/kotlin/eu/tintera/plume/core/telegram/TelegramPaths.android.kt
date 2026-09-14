package eu.tintera.plume.core.telegram

import android.content.Context
import android.os.Build

/**
 * `filesDir` je privátní pro aplikaci a systém ho nezálohuje do cloudu bez našeho svolení —
 * pro šifrovanou databázi zpráv je to správné místo. Cache adresář by byl špatně: systém ho
 * může kdykoli vymazat a TDLib by přišel o stav.
 */
actual class TelegramPaths(context: Context) {
    actual val databaseDirectory: String = context.filesDir.resolve("tdlib").absolutePath
    actual val filesDirectory: String = context.filesDir.resolve("tdlib-files").absolutePath
}

actual val platformDeviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"

actual val platformSystemVersion: String = "Android ${Build.VERSION.RELEASE}"
