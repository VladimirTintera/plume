package eu.tintera.plume.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import eu.tintera.plume.core.settings.AutoDownloadLevel
import eu.tintera.plume.core.settings.AutoDownloadLimits
import eu.tintera.plume.core.settings.limits

/**
 * Co se smí stáhnout samo právě teď (`AutoDownloadPolicy`). Dodává ho kořen přihlášené části;
 * výchozí hodnota (úroveň „Běžně", tedy chování z doby před nastavením) platí pro náhledy
 * a obrazovky mimo něj.
 *
 * Statický: mění se se sítí nebo v Nastavení, tedy zřídka, a pak má překreslit všechno.
 */
val LocalAutoDownload = staticCompositionLocalOf<AutoDownloadLimits> { AutoDownloadLevel.Medium.limits }
