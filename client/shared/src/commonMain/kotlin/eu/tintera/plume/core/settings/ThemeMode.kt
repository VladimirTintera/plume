package eu.tintera.plume.core.settings

/**
 * Motiv aplikace, jak si ho volí uživatel.
 *
 * Bydlí v nastavení, ne u tématu: je to uložená hodnota a `core` nesmí sahat do `app`.
 * Co z něj plyne pro vykreslení (`resolveIsDark`, `defaultThemeMode`), zůstalo u tématu —
 * závisí to na platformě a na systémovém tmavém režimu.
 *
 * `DYNAMIC` je Material You (Android 12+); na platformách bez něj se nenabízí.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK, DYNAMIC }
