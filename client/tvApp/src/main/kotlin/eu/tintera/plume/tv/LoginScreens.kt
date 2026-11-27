package eu.tintera.plume.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import co.touchlab.kermit.Logger
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_qr_content_description
import eu.tintera.plume.resources.auth_support_qr
import eu.tintera.plume.resources.tv_login_qr_only
import eu.tintera.plume.resources.tv_login_show_qr
import eu.tintera.plume.resources.tv_login_title
import eu.tintera.plume.resources.tv_password_failed
import eu.tintera.plume.resources.tv_password_hint
import eu.tintera.plume.resources.tv_password_label
import eu.tintera.plume.resources.tv_password_submit
import eu.tintera.plume.resources.tv_password_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val logger = Logger.withTag("TvLogin")

/** Strana QR kódu: z gauče přes pokoj ho telefon přečte, a vejde se i na 720p. */
private val QR_SIZE = 320.dp

/**
 * QR přihlášení: kód potvrdí už přihlášený klient (telefon). Odkaz po vypršení TDLib vymění sám
 * a pošle nový stav, takže obrazovka jen kreslí, co dostane. Kód vlevo, nadpis a návod vedle něj —
 * na širokém displeji se pod sebe nevejdou bez zmenšení kódu.
 */
@Composable
fun QrLoginScreen(link: String) {
    val qr = remember(link) { qrBitmap(link, sizePx = 600) }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bílý okraj kolem kódu: na tmavé ploše by ho čtečka nenašla.
        Image(
            bitmap = qr,
            contentDescription = stringResource(Res.string.auth_qr_content_description),
            modifier = Modifier
                .size(QR_SIZE)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
        )
        Spacer(Modifier.width(64.dp))
        Column(Modifier.widthIn(max = 480.dp)) {
            Text(stringResource(Res.string.tv_login_title), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.auth_support_qr),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Dvoufázové ověření. Heslo se píše systémovou klávesnicí televize (na Google TV i hlasem
 * z ovladače); pole dostane fokus hned, aby stačilo potvrdit OK.
 */
@Composable
fun PasswordScreen(stage: AuthStage.WaitPassword) {
    val session = koinInject<TelegramSession>()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val submit = {
        if (password.isNotEmpty() && !busy) {
            busy = true
            scope.launch {
                failed = session.submitPassword(password)
                    .onFailure { logger.w(it) { "Heslo na televizi neprošlo." } }
                    .isFailure
                busy = false
            }
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.tv_password_title), style = MaterialTheme.typography.displaySmall)
        stage.hint?.takeIf { it.isNotBlank() }?.let { hint ->
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.tv_password_hint, hint),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
        PasswordField(
            value = password,
            onValueChange = {
                password = it
                failed = false
            },
            onDone = submit,
            modifier = Modifier.width(480.dp).focusRequester(focus),
        )
        if (failed) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.tv_password_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = submit, enabled = password.isNotEmpty() && !busy) {
            Text(stringResource(Res.string.tv_password_submit))
        }
    }
}

/**
 * Pole na heslo. tv-material textové pole nemá, takže je to `BasicTextField` v plošce,
 * jejíž rámeček ukazuje fokus — na televizi je fokus jediný kurzor, který uživatel vidí.
 */
@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant, RoundedCornerShape(16.dp))
                    .border(
                        width = if (focused) 3.dp else 1.dp,
                        color = if (focused) colors.primary else colors.border,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        stringResource(Res.string.tv_password_label),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
    )
}

/** Kód, e-mail nebo registrace: na televizi se nepíšou, přihlášení se vrátí k QR kódu. */
@Composable
fun QrOnlyScreen(onShowQr: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.tv_login_qr_only), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onShowQr, modifier = Modifier.focusRequester(focus)) {
            Text(stringResource(Res.string.tv_login_show_qr))
        }
    }
}

/** Neurčité čekání: `LoadingIndicator` (měnící se tvar) jako na telefonu, pod ním co se děje. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingScreen(text: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Celá obrazovka (nebo panel) s jednou zprávou — prázdný seznam, chyba. Ikona nad textem říká,
 * o co jde, dřív než se text dočte. [error] ji obarví chybovou barvou.
 */
@Composable
fun MessageScreen(text: String, icon: DrawableResource, modifier: Modifier = Modifier, error: Boolean = false) {
    Column(
        modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/** QR kód odkazu jako obrázek: černé moduly na bílém, okraj si dělá obrazovka sama. */
private fun qrBitmap(link: String, sizePx: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 0))
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix.get(index % matrix.width, index / matrix.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
