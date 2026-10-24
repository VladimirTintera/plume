package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Zvuky notifikací z Telegramu (`NotificationSound`): tóny, které si účet uložil — nahrané
 * nebo převzaté ze zprávy. Jsou na serveru, takže je mají všechna zařízení účtu, a rozsahy
 * i chaty na ně odkazují id (`soundId`).
 *
 * Id zvuku: [DEFAULT_SOUND_ID] = výchozí zvuk aplikace (kanál bez zásahu), [NO_SOUND_ID] = bez
 * zvuku, cokoli jiného = tón účtu.
 */
interface NotificationSoundRepository {

    /** Uložené tóny, živě (`updateSavedNotificationSounds`). */
    val tones: Flow<List<NotificationTone>>

    /** Strop pro nahrání, jak ho posílá server. */
    suspend fun limits(): ToneLimits

    /** Nahraje zvukový soubor jako nový tón. */
    suspend fun add(path: String): Result<NotificationTone>

    suspend fun remove(id: Long): Result<Unit>

    /** Tón na disku (stáhne ho, když tam ještě není); `null`, když se to nepovedlo. */
    suspend fun tonePath(id: Long): String?

    /** Název tónu podle id; `null`, když ho účet nemá (smazaný, z jiného účtu). */
    suspend fun toneTitle(id: Long): String?
}

data class NotificationTone(val id: Long, val title: String, val durationSeconds: Int)

/** Strop nahrávaného tónu (`notification_sound_*_max`). */
data class ToneLimits(val maxBytes: Long, val maxSeconds: Int, val maxCount: Int)

/** Výchozí zvuk — notifikace jde výchozím kanálem rozsahu, jak si ho uživatel nastavil. */
const val DEFAULT_SOUND_ID: Long = -1

/** Bez zvuku. */
const val NO_SOUND_ID: Long = 0
