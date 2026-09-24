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
    /**
     * Server chat archivoval SÁM (chat od neznámého s automatickou archivací) a pruh místo
     * nahlášení nabízí vrácení mezi chaty — viz [ChatRepository.unarchiveFromSpamBar].
     */
    val canUnarchive: Boolean = false,
    /** Co server o protějšku prozradil, když napsal poprvé; `null` = nic. */
    val account: CounterpartAccount? = null,
)

/**
 * Údaje o cizím účtu, který napsal poprvé (`AccountInfo` u `ChatActionBarReportAddBlock`).
 * Telegram je posílá, aby šel poznat spam nebo ukradený účet: čerstvá registrace, číslo
 * z nečekané země, jméno nebo fotka změněné před pár dny.
 *
 * Neznámé hodnoty drží TDLib jako nulu nebo prázdný řetězec; tady jsou `null`, aby je
 * obrazovka nemusela znát ([lines]).
 */
data class CounterpartAccount(
    /** Měsíc (1–12) a rok registrace; `null`, když server jedno z nich nezná. */
    val registered: RegistrationMonth?,
    /** Země podle telefonního čísla, ISO 3166-1 alpha-2 („CZ"). */
    val phoneCountryCode: String?,
    /** Kdy naposledy změnil jméno (Unix čas). */
    val nameChangedAt: Int?,
    /** Kdy naposledy změnil fotku (Unix čas). */
    val photoChangedAt: Int?,
) {
    /** Řádky k vykreslení v pevném pořadí; co server nezná, se vynechá. */
    val lines: List<CounterpartAccountLine>
        get() = listOfNotNull(
            registered?.let { CounterpartAccountLine.Registered(it) },
            phoneCountryCode?.let { CounterpartAccountLine.PhoneCountry(it) },
            nameChangedAt?.let { CounterpartAccountLine.NameChanged(it) },
            photoChangedAt?.let { CounterpartAccountLine.PhotoChanged(it) },
        )

    companion object {
        /**
         * Z hodnot, jak je posílá TDLib. Vrací `null`, když není co ukázat — prázdný rámeček
         * s údaji by vypadal jako chyba načítání.
         */
        fun of(month: Int, year: Int, phoneCountryCode: String, nameChangedAt: Int, photoChangedAt: Int) =
            CounterpartAccount(
                registered = if (month in 1..12 && year > 0) RegistrationMonth(month, year) else null,
                phoneCountryCode = phoneCountryCode.trim().uppercase().takeIf { it.length == 2 && it.all(Char::isLetter) },
                nameChangedAt = nameChangedAt.takeIf { it > 0 },
                photoChangedAt = photoChangedAt.takeIf { it > 0 },
            ).takeIf { it.lines.isNotEmpty() }
    }
}

data class RegistrationMonth(val month: Int, val year: Int)

/** Jeden řádek údajů o protějšku. */
sealed interface CounterpartAccountLine {
    data class Registered(val month: RegistrationMonth) : CounterpartAccountLine
    data class PhoneCountry(val code: String) : CounterpartAccountLine
    data class NameChanged(val at: Int) : CounterpartAccountLine
    data class PhotoChanged(val at: Int) : CounterpartAccountLine
}

/**
 * Vlajka země jako emoji: každé písmeno kódu země se posune na regionální indikátor
 * (U+1F1E6 = „A"). Systémové písmo dvojici složí do vlajky; kde vlajky nemá (Windows),
 * ukáže dvě písmena v rámečku, což je pořád čitelné.
 */
fun flagEmoji(countryCode: String): String? {
    val code = countryCode.uppercase()
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return null
    return buildString {
        code.forEach { letter ->
            val codePoint = 0x1F1E6 + (letter - 'A')
            // Regionální indikátor je mimo BMP, takže do UTF-16 jde jako dvojice surrogate.
            val offset = codePoint - 0x10000
            append((0xD800 + (offset shr 10)).toChar())
            append((0xDC00 + (offset and 0x3FF)).toChar())
        }
    }
}
