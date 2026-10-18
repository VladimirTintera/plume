package eu.tintera.plume.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

/**
 * Stav modálního sheetu, který se otevírá **rovnou celý** — do výšky obsahu, u vyššího obsahu
 * přes celou obrazovku. Žádný sheet v aplikaci se neotevírá napůl; hlídá to
 * `verifyExpandedSheets` (každý `ModalBottomSheet` dostane stav odsud).
 *
 * Napůl rozbalený sheet schovával pod spodní okraj právě to, kvůli čemu se otevřel: výsledek
 * přepisu, tlačítko Uložit, delší doby pomalého režimu, poslední položky nabídky. Opravovalo
 * se to po jednom v osmi sheetech, než se z toho stalo pravidlo. Obsah, který v sheetu teprve
 * přibude (výsledek po ťuknutí), rozbalený sheet zvětší sám; napůl rozbalený zůstal stát.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberExpandedSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
)
