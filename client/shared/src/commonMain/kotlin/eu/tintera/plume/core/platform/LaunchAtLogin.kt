package eu.tintera.plume.core.platform

/**
 * Spouštění aplikace po přihlášení do systému. Má smysl jen na desktopu: push tam není,
 * oznámení chodí, jen dokud aplikace běží — bez spuštění po přihlášení po restartu počítače
 * nepřijde nic, dokud ji člověk sám neotevře.
 *
 * Stav drží systém, ne nastavení aplikace: vypnout se dá i v systému (Windows: Nastavení →
 * Aplikace → Po spuštění), takže se čte pokaždé znovu.
 */
expect class LaunchAtLogin {

    /** Umí to platforma a tahle instalace? Ladicí běh ne — znovu spustit by nešel. */
    val supported: Boolean

    fun isEnabled(): Boolean

    /** Zapne nebo vypne; `false` = nepovedlo se (stav je pak, jaký byl). */
    fun setEnabled(enabled: Boolean): Boolean
}
