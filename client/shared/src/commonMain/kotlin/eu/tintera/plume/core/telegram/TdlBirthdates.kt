package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.Birthdate as TdlBirthdate

/** TDLib má neuvedený rok jako 0. */
internal fun TdlBirthdate.toDomain(): Birthdate = Birthdate(day = day, month = month, year = year.takeIf { it > 0 })

internal fun Birthdate.toTdl(): TdlBirthdate = TdlBirthdate(day = day, month = month, year = year ?: 0)
