package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/** Delší zpráva se modelu nedává — termín v ní hledat stejně nemá smysl a prompt by se natahoval. */
const val MAX_REMINDER_MESSAGE_CHARS = 1_000

/**
 * Prompt pro termín připomínky ze zprávy, když ho nenajdou pravidla (`reminderTimeFromText`):
 * volnější formulace jako „za týden po obědě" nebo „až skončí víkend".
 *
 * - **Výstup je jeden řádek `YYYY-MM-DD HH:MM`, nebo `NONE`** — ne věta. Z věty by se termín
 *   dolovat musel a model by ho rád doplnil omluvou. Na formát se ale nespoléhá:
 *   [parseReminderTime] hledá datum a čas kdekoli ve výstupu.
 * - **Den a datum odeslání jdou do promptu výslovně** i s jménem dne: „v pátek" a „za týden"
 *   se počítají od nich a model kalendář nezná.
 * - Instrukce anglicky ze stejného důvodu jako u [summaryPrompt].
 */
fun reminderPrompt(text: String, sentAt: LocalDateTime): String = buildString {
    val day = sentAt.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    appendLine("You find the moment a chat message refers to, so the user can be reminded of it.")
    appendLine("The message was sent on $day ${sentAt.date} at ${twoDigits(sentAt.hour)}:${twoDigits(sentAt.minute)}.")
    appendLine()
    appendLine("Message:")
    appendLine("\"\"\"")
    appendLine(text.take(MAX_REMINDER_MESSAGE_CHARS))
    appendLine("\"\"\"")
    appendLine()
    appendLine("If the message mentions when something will happen (a meeting, a deadline, an appointment, a call),")
    appendLine("answer with that moment as YYYY-MM-DD HH:MM in 24-hour time.")
    appendLine("Work out relative words (tomorrow, next week, on Friday, after the weekend) from the date the message was sent.")
    // Bez téhle věty model „po obědě" přešel a dal výchozí 08:00 (Gemma 4 E2B, ověřeno).
    appendLine("Turn parts of the day into times: morning 08:00, before lunch 11:00, lunch 12:00, after lunch 13:00,")
    appendLine("afternoon 15:00, evening 19:00, night 21:00 (also in other languages, e.g. Czech \"po obědě\" is after lunch).")
    appendLine("If only a day is mentioned and no part of the day, use 08:00. If the message mentions no such moment, answer NONE.")
    append("Answer with the date and time or NONE only, nothing else.")
}

/**
 * Termín z výstupu modelu, nebo `null`. Tolerantní k formátu (model přidá větu, uvozovky,
 * „T" místo mezery), ale **přísný k obsahu**: termín musí být po odeslání zprávy ([sentAt])
 * a nejvýš rok po něm. Model, který si termín vymyslí, se tak spíš projeví jako „nic" než jako
 * připomínka na rok 1970.
 */
fun parseReminderTime(output: String, sentAt: LocalDateTime): LocalDateTime? {
    val match = DATE_TIME.find(output) ?: return null
    val (year, month, day, hour, minute) = match.destructured
    val moment = runCatching {
        LocalDate(year.toInt(), month.toInt(), day.toInt()).atTime(LocalTime(hour.toInt(), minute.toInt()))
    }.getOrNull() ?: return null
    val latest = sentAt.date.plus(DatePeriod(years = 1)).atTime(sentAt.time)
    return moment.takeIf { it > sentAt && it <= latest }
}

private val DATE_TIME = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})[ T]+(\\d{1,2}):(\\d{2})")

private fun twoDigits(value: Int) = value.toString().padStart(2, '0')
