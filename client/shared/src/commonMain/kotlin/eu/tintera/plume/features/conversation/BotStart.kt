package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatInfo

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
    return showsBotIntro(state)
}

/**
 * Kreslit místo konverzace profil bota ([BotIntroPage])? Jen prázdný chat s botem — tentýž
 * důkaz prázdnoty jako u Startu. Hlavička pak nenabízí hledání ani shrnutí: v prázdném chatu
 * není co hledat a lupa nad profilem působila jako ovládání stránky.
 */
internal fun showsBotIntro(state: ConversationState): Boolean =
    state.botIntro != null && showsEmptyChat(state)

/**
 * Kreslit místo prázdného chatu úvodní stránku firmy ([BusinessIntroPage])? Tentýž důkaz
 * prázdnoty jako u bota; bot má přednost (jeho stránka nese i Start).
 */
internal fun showsBusinessIntro(state: ConversationState): Boolean =
    state.businessIntro != null && state.botIntro == null && showsEmptyChat(state)

/** Stránka místo konverzace (bot, firma): hlavička pak schová avatar i hledání. */
internal fun showsIntroPage(state: ConversationState): Boolean = showsBotIntro(state) || showsBusinessIntro(state)

/**
 * Nabídnout v prázdném chatu samolepku na pozdrav ([GreetingSticker])? Jen soukromý (i tajný)
 * chat s ČLOVĚKEM ([ChatInfo.isWithPerson] — ne bot, ne Uložené zprávy), kam se smí psát
 * i posílat samolepky, a jen tam, kde místo chatu nestojí stránka bota nebo firmy (firma má
 * vlastní uvítací samolepku). Tentýž důkaz prázdnoty jako u bota; s chybou načítání ne —
 * prázdný stav pak hlásí chybu.
 */
internal fun showsGreetingSticker(state: ConversationState): Boolean {
    val info = state.info ?: return false
    return info.isWithPerson && info.content.stickers && state.canWrite && state.error == null &&
        state.botIntro == null && state.businessIntro == null && showsEmptyChat(state)
}

private fun showsEmptyChat(state: ConversationState): Boolean =
    state.messages.isEmpty() && state.reachedStart && !state.loading
