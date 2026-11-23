package eu.tintera.plume.core.logging

/**
 * Záznamy o chodu aplikace, které si uživatel může sám otevřít a přiložit k hlášení chyby.
 * Má je jen desktop (`DesktopLogs` v `:client:core`); Android a iOS hlásí pády do Crashlytics.
 */
expect val appLogsAvailable: Boolean

/** Otevře složku se záznamy s nejnovějším záznamem o pádu (nebo logem) vybraným. */
expect fun revealAppLogs(): Boolean
