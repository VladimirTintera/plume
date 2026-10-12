package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Smí Plume otevírat odkazy `t.me` z jiných aplikací a z fotoaparátu? [enabled] je stav systémové
 * volby, [openSettings] do ní vede — přepnout ji aplikace sama nesmí.
 */
class TelegramLinkHandling(val enabled: Boolean, val openSettings: () -> Unit)

/**
 * Systémová volba „Otevírat podporované odkazy", nebo `null`, kde žádná není.
 *
 * Jen Android 12+: Plume doménu `t.me` ověřit nemůže (`assetlinks.json` na ní vydává Telegram),
 * a neověřený webový odkaz otevírá od Androidu 12 prohlížeč, dokud ho uživatel aplikaci v systému
 * výslovně nepovolí. Do Androidu 11 systém nabídne výběr aplikací sám. Schéma `tg://` žádné
 * povolení nepotřebuje.
 */
@Composable
expect fun rememberTelegramLinkHandling(): TelegramLinkHandling?
