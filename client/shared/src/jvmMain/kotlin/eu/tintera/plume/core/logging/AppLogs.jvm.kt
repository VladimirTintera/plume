package eu.tintera.plume.core.logging

import eu.tintera.plume.core.files.revealInFolder

actual val appLogsAvailable: Boolean = true

actual fun revealAppLogs(): Boolean = revealInFolder(DesktopLogs.fileToReveal().path)
