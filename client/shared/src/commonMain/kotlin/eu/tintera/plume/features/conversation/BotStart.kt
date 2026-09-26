package eu.tintera.plume.features.conversation

/**
 * Nabídnout místo vstupního pole tlačítko Start?
 *
 * Jen v chatu s botem ([ConversationState.botIntro]), a to když je chat PRÁZDNÝ (bot ještě
 * nespuštěný — psát mu nemá smysl, dokud nedostane `/start`), nebo když čeká parametr ze
 * startovacího odkazu: ten se botovi dostane jen tlačítkem, i v chatu, kde už se psalo.
 * Prázdnou historii dokazuje až [ConversationState.reachedStart] (načítání došlo na začátek
 * a nic nenašlo): stav před prvním načtením je taky prázdný a `loading` v něm ještě neplatí,
 * takže by se u každého bota na okamžik objevil Start.
 */
internal fun showsBotStart(state: ConversationState): Boolean {
    if (state.botIntro == null) return false
    if (state.botStartParameter != null) return true
    return state.messages.isEmpty() && state.reachedStart && !state.loading
}
