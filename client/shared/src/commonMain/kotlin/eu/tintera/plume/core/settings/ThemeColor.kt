package eu.tintera.plume.core.settings

/**
 * Barva, ze které se dopočítá paleta aplikace, když neplatí barvy z tapety (iOS, Android do 11,
 * nebo je uživatel vypnul).
 *
 * Ukládá se NÁZVEM, jako [ThemeMode]: nová barva uprostřed výčtu by jinak tiše změnila význam
 * uložených hodnot. Konkrétní odstín bydlí u tématu (`ThemeColor.seed`), ne tady — `core`
 * nesahá na Compose a odstín se smí doladit bez migrace.
 *
 * [BLUE] je značková barva a výchozí, dokud si uživatel nevybral.
 */
enum class ThemeColor { BLUE, TEAL, GREEN, YELLOW, ORANGE, RED, PINK, PURPLE }
