package eu.tintera.plume.core.telegram

/**
 * Identifikátor důvodu nahlášení od serveru. Je to neprůhledný kus bajtů, který se jen vrací zpátky.
 *
 * Není to data class: `ByteArray` se v ní porovnává podle reference, takže by dvě shodná id
 * vypadala jako různá (stejná past jako u `Minithumbnail`).
 */
class ReportOptionId(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is ReportOptionId && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
}

/** Jedna volba v průvodci nahlášením; [text] je věta od serveru v jazyce účtu. */
data class ReportOption(val id: ReportOptionId, val text: String)

/**
 * Jeden krok nahlášení chatu (`reportChat`).
 *
 * Prázdný požadavek je ÚVODNÍ — server na něj odpoví, co chce dál. Jen u chatu se spamovým
 * action barem ho vezme rovnou jako nahlášení spamu.
 */
data class ReportRequest(
    val optionId: ReportOptionId? = null,
    val messageIds: List<Long> = emptyList(),
    val text: String = "",
)

/**
 * Co server po kroku nahlášení chce. `reportChat` není jedno volání, je to PRŮVODCE — viz
 * [ChatRepository.report].
 */
sealed interface ReportStep {
    /** Nahlášeno. */
    data object Done : ReportStep

    /** Vyber důvod. [title] je otázka od serveru („Proč tohle hlásíš?"). */
    data class ChooseOption(val title: String, val options: List<ReportOption>) : ReportStep

    /** Doplň podrobnosti k důvodu [optionId]; [optional] = smí se odeslat i prázdné. */
    data class EnterText(val optionId: ReportOptionId, val optional: Boolean) : ReportStep

    /**
     * Server chce vědět, KTERÉ zprávy se hlásí. Z nabídky nad chatem je dodat nejde — uživatel
     * je musí nahlásit z nabídky nad zprávou.
     */
    data object NeedsMessages : ReportStep
}

/**
 * Pruh nad konverzací, který server dává chatu od neznámého (`ChatActionBar`). Je to hlavní
 * cesta, kterou Telegram spam řeší: nabídne se sám, jakmile někdo cizí napíše.
 *
 * Nese jen druhy, které aplikace umí. Pruhy pro sdílení čísla, pozvání členů nebo žádost
 * o vstup se nekreslí vůbec — `null` z [ChatRepository.spamBar].
 */
data class SpamBar(
    /** Nahlásit spam jedním voláním, bez průvodce. */
    val canReportSpam: Boolean,
    /** Protějšek jde zablokovat (soukromý chat). */
    val canBlock: Boolean,
    /** Protějšek jde přidat do kontaktů. */
    val canAddContact: Boolean,
)
