package eu.tintera.plume.core.ui

import androidx.compose.ui.window.PopupProperties

/**
 * Vlastnosti okna nabídky: fokusovatelné, ale klávesnici si NEBERE. Výchozí pro [ExpressiveMenu];
 * kdo kreslí `DropdownMenuPopup` sám, předá je taky.
 *
 * **Proč:** popup je na Androidu samostatné okno a fokusovatelné okno (výchozí M3) převezme
 * fokus od vstupního pole. Systém pak klávesnici schová — a animaci insetů už dostane okno
 * nabídky, takže hlavní okno dostane rovnou konečné insety a konverzace skokem spadne dolů.
 * Stávalo se to u každé nabídky nad otevřenou klávesnicí: sponka, podržení odesílacího tlačítka,
 * tlačítka dolů a ke zmínkám.
 *
 * **Android:** `FLAG_ALT_FOCUSABLE_IM` u fokusovatelného okna znamená „klávesnici nepotřebuje" —
 * totéž, co `PopupWindow.INPUT_METHOD_NOT_NEEDED` u systémových nabídek. Okno fokus dostane
 * (zpět zavře nabídku, dotyk mimo ni se nikam nepropíše, hardwarová klávesnice v ní chodí),
 * ale cílem klávesnice zůstane okno s polem, takže klávesnice zůstane i s psaním do pole.
 * Nabídka žádné pole nemá, takže o nic nepřichází.
 *
 * Dřív to řešila jen nabídka sponky nefokusovatelným oknem (`focusable = false`) a s ním si
 * musela dovyrobit zpět (nefokusovatelné okno ho nedostane) a pojistku proti znovuotevření
 * (dotyk mimo okno se propsal i do sponky pod ním). Tady obojí funguje samo.
 *
 * **iOS:** výchozí M3 (fokusovatelné); jen přeložené, chování klávesnice ověřené není.
 */
expect val MenuPopupProperties: PopupProperties

/**
 * Popup, který smí ležet i přes pruh titulku okna: rozbalené hledání nad pilulkou seznamu chatů.
 *
 * Na desktopu s vlastním titulkem je titulek horní inset okna (`DesktopTitleBar`) a popup ho
 * ve výchozím stavu respektuje (`usePlatformInsets`): nesmí nad něj, a hledání, které se má
 * rozbalit přesně přes pilulku v řádku se semafory, se posunulo o výšku titulku níž — pod
 * pilulkou zůstalo druhé pole. Telefon a iOS popup nemění.
 */
expect fun PopupProperties.overTitleBar(): PopupProperties
