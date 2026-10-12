package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/** Odkazy `t.me` z jiných aplikací Plume tady zatím nepřijímá, takže není co povolovat. */
@Composable
actual fun rememberTelegramLinkHandling(): TelegramLinkHandling? = null
