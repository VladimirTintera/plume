package eu.tintera.plume.core.ai

/**
 * Jedna dávka popisů fotek na pozadí. Spouští ji platforma (Android `PhotoDescriptionWorker`, jen
 * na nabíječce), dělá ji `BackgroundPhotoDescriber` ve `features/media`. Rozhraní je tu proto, aby
 * `core` na `features` nesahalo; jméno třídy workeru se kvůli tomu měnit nesmělo — WorkManager si
 * ho pamatuje v naplánované úloze a přejmenovaný by už nenašel.
 */
fun interface BackgroundPhotoDescription {
    /** Popíše dávku dosud nepopsaných fotek; vrátí, kolik popsal. */
    suspend fun describeBatch(): Int
}
