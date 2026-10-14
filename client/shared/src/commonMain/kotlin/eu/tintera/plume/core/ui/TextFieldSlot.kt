package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * Slot textového pole (`leadingIcon` / `trailingIcon`) s tlačítkem nebo jiným ovládacím prvkem,
 * nad kterým má myš obyčejný kurzor, ne textový.
 *
 * `BasicTextField` (nové i staré API) dává `pointerHoverIcon(PointerIcon.Text)` celému boxu
 * i s dekorací, takže ikony ve slotech kurzor zdědí: na desktopu se nad avatarem účtu v hledání
 * nebo nad křížkem „vymazat" ukazoval textový kurzor, jako by se tam dalo psát. Potomek s vlastní
 * ikonou zděděnou přebije.
 *
 * Jen pro ovládací prvky; čistě ozdobná ikona (lupa) k poli patří a textový kurzor nad ní
 * nevadí — ťuknutí na ni pole zaostří.
 */
@Composable
fun TextFieldSlot(content: @Composable () -> Unit) {
    Box(Modifier.pointerHoverIcon(PointerIcon.Default), propagateMinConstraints = true) {
        content()
    }
}
