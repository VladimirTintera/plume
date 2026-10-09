package eu.tintera.plume.core.ui

import androidx.compose.ui.input.key.KeyEvent

/**
 * Přišla klávesa z fyzické klávesnice? Enter z ní odesílá zprávu, kdežto Enter ze softwarové
 * klávesnice dál dělá nový řádek — na telefonu je odeslání tlačítko vedle pole. Některé
 * softwarové klávesnice (Gboard ve víceřádkovém poli) Enter posílají jako klávesu, ne jako
 * text, takže samotný kód klávesy nestačí.
 */
expect val KeyEvent.isFromHardwareKeyboard: Boolean
