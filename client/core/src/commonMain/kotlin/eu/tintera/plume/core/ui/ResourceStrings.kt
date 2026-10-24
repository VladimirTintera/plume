package eu.tintera.plume.core.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Text z prostředků mimo kompozici. **Jediná cesta k suspend `getString` / `getPluralString`**
 * z Compose Resources — kontrola `verifyResourceReadsOffMain` jiný import nepustí.
 *
 * Čte se na [Dispatchers.Default], ne tam, odkud se volá. Compose Resources 1.12 v kompozici
 * čte `stringResource` přes `runBlocking` na hlavním vlákně a jeho mezipaměť (`AsyncCache`)
 * hlídá `Mutex` z kotlinx. Ten při odemčení PŘEDÁ zámek čekající korutině a probudí ji přes její
 * dispatcher. Čekala-li korutina na `Dispatchers.Main` (snackbar z `LaunchedEffect`, ViewModel),
 * zámek je její, ale rozběhnout se nemůže — hlavní vlákno drží `runBlocking`, který čeká právě
 * na ten zámek. Aplikace zamrzne natrvalo (ANR `stringResource` → `runBlocking`). Upstream
 * CMP-6615, opravené v 1.13.0-alpha01; až tam projekt dojde, může tenhle soubor zmizet.
 */
suspend fun readString(resource: StringResource, vararg args: Any): String =
    withContext(Dispatchers.Default) { getString(resource, *args) }

/** Věta s počtem; viz [readString], proč mimo hlavní vlákno. */
suspend fun readPluralString(resource: PluralStringResource, quantity: Int, vararg args: Any): String =
    withContext(Dispatchers.Default) { getPluralString(resource, quantity, *args) }
