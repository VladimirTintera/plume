package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Ohlásí systému, že obrazovka po startu ukazuje skutečný obsah, jakmile [ready] vrátí `true`
 * (Android `reportFullyDrawn` přes `ReportDrawnWhen`). První snímek bývá ještě bez dat; z tohohle
 * hlášení počítá „Fully drawn" v logu, Play vitals i Macrobenchmark (`StartupTimingMetric`).
 * Platí jen první hlášení za start aktivity. iOS nic takového nemá.
 */
@Composable
expect fun ReportDrawnWhen(ready: () -> Boolean)
