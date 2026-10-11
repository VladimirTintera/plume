package eu.tintera.plume.core.calendar

/** Kalendář systému z JVM nedosáhne; volající uživateli řekne, že událost nejde přidat. */
actual class CalendarEvents {
    actual fun add(event: CalendarEvent): Boolean = false
}
