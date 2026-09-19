package eu.tintera.plume.core.settings

/**
 * Motiv aplikace, jak si ho volí uživatel.
 *
 * Bydlí v nastavení, ne u tématu: je to uložená hodnota a `core` nesmí sahat do `app`.
 * Co z něj plyne pro vykreslení (`resolveIsDark`), zůstalo u tématu —
 * závisí to na platformě a na systémovém tmavém režimu.
 *
 * Barvy z tapety (Material You) sem NEPATŘÍ — jsou samostatná volba
 * ([AppSettings.dynamicColor]), protože se kombinují s kterýmkoli z těchto tří. Dřív to byla
 * čtvrtá hodnota výčtu a Material You tím šel jen se systémovým tmavým režimem. Uložené `DYNAMIC`
 * ze starší verze se čte jako nevybraný motiv, tedy podle systému — a barvy z tapety zůstanou,
 * protože i ty jsou nevybrané, tedy výchozí.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
