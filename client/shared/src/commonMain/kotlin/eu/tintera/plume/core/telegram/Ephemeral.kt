package eu.tintera.plume.core.telegram

/**
 * Mizející zpráva ve skupině: vidí ji jen jeden člověk a bot. Posílají je boti (odpověď na
 * příkaz, který si o to řekne, nebo na ťuknutí na tlačítko) a uživatel jen boti zpátky —
 * mizející příkaz a odpověď na mizející zprávu bota. Ostatní členové ji nevidí vůbec.
 *
 * [receiverName] `null` = vidí ji jen uživatel sám (zpráva od bota); jinak jméno toho, komu
 * šla (vlastní mizející příkaz vidí jen bot). [replacesOriginal]: bot obsah běžné zprávy
 * nahradil jen pro uživatele (po ťuknutí na tlačítko) a ostatní vidí dál původní — ten jde
 * vrátit ([ConversationRepository.showOriginalContent]).
 */
data class Ephemeral(
    val receiverName: String? = null,
    val replacesOriginal: Boolean = false,
)
