package eu.tintera.plume.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_input_password
import eu.tintera.plume.resources.auth_password_hide
import eu.tintera.plume.resources.auth_password_show
import eu.tintera.plume.resources.visibility_24dp
import eu.tintera.plume.resources.visibility_off_24dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.KeyboardCapitalization
import eu.tintera.plume.resources.text_counter

/** Tloušťka rámečku tónovaného vstupu — políčka kódu, číslo i heslo. */
val BLOCK_OUTLINE = 2.dp

/**
 * Barva rámečku tónovaného vstupu (přihlášení, dvoufázové ověření v Nastavení). Jedno místo pro
 * políčka kódu i pro heslo — dvě kopie téhož pravidla by se rozešly a z jedné obrazovky by byly
 * dva jazyky.
 *
 * Průhledná, dokud se nic neděje: rámeček kolem každého pole pořád je kresba navíc, která nic
 * neříká.
 */
@Composable
fun blockOutlineColor(isError: Boolean, active: Boolean): Color = animateColorAsState(
    targetValue = when {
        isError -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    },
    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    label = "auth-outline",
).value

/**
 * Heslo dvoufázového ověření — to stávající i nové, při přihlášení i v Nastavení. Vytažené
 * z přihlášení do `core/ui`, aby heslo všude vypadalo a chovalo se stejně.
 *
 * **Tónovaný blok jako číslo, ne obrysové pole s plovoucím labelem.** Na jedné obrazovce nemají
 * být dvě různá pojetí vstupu; label navíc jen opakoval nadpis kroku, stejně jako u země.
 *
 * **Odkrytí okem je to hlavní.** Cloud heslo se píše jednou za dlouho a naslepo, takže překlep
 * se pozná až z hlášky „Heslo nesedí" — a uživatel ani potom neví, co vlastně napsal. Ikona
 * pojmenovává AKCI, ne stav: se skrytým heslem je to oko („zobrazit"), s odkrytým přeškrtnuté.
 * U NOVÉHO hesla je to ještě důležitější. Při obnově v přihlášení je oko jediná kontrola;
 * Nastavení má navíc druhé pole na zopakování (viz `TwoStepScreen`).
 */
@Composable
fun PasswordField(
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: StringResource = Res.string.auth_input_password,
    // Nové heslo správce hesel nabídne vygenerovat a uložit; stávající doplnit.
    contentType: ContentType = ContentType.Password,
    imeAction: ImeAction = ImeAction.Go,
    onNext: (() -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    BlockTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        isError = isError,
        keyboardType = KeyboardType.Password,
        contentType = contentType,
        imeAction = imeAction,
        onSubmit = onSubmit,
        onNext = onNext,
        fieldModifier = fieldModifier,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
    ) {
        IconButton(
            onClick = { revealed = !revealed },
            modifier = Modifier.padding(end = 4.dp),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                painterResource(
                    if (revealed) Res.drawable.visibility_off_24dp else Res.drawable.visibility_24dp,
                ),
                contentDescription = stringResource(
                    if (revealed) Res.string.auth_password_hide else Res.string.auth_password_show,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Tónovaný blok s jedním polem — tentýž jazyk jako číslo a heslo, pro kód z e-mailu a nápovědu
 * k novému heslu. Obrysové obrysové pole by na obrazovce, kde se oba vstupy střídají s heslem,
 * byl druhý vzhled pro totéž.
 *
 * **Fokus a chybu nese rámeček kolem celého bloku**, ne podtržení `TextField`u: to je široké jen
 * jako pole, takže vedle ikony končilo v půlce karty a vypadalo jako chyba vykreslení.
 */
@Composable
fun BlockTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: StringResource,
    isError: Boolean,
    keyboardType: KeyboardType,
    onSubmit: () -> Unit,
    contentType: ContentType? = null,
    imeAction: ImeAction = ImeAction.Go,
    /**
     * „Další" na klávesnici. **Výchozí přesun fokusu nestačí, když má blok ikonu**: fokus skočí
     * na ni (oko u hesla), ne do dalšího pole — ověřeno na emulátoru, nápověda pod novým heslem
     * se tak psala naprázdno.
     */
    onNext: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    fieldModifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(BLOCK_OUTLINE, blockOutlineColor(isError = isError, active = focused)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(placeholder)) },
                singleLine = true,
                isError = isError,
                visualTransformation = visualTransformation,
                // Heslo ani kód se neopravují a nedoplňují; klávesnice by z nich udělala jiné slovo.
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { onSubmit() },
                    onNext = onNext?.let { next -> { next() } },
                ),
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                ),
                interactionSource = interaction,
                modifier = fieldModifier
                    .weight(1f)
                    .semantics { contentType?.let { this.contentType = it } },
            )
            trailing()
        }
    }
}

/**
 * **Pole formuláře** (jméno, uživatelské jméno, bio, název skupiny, proxy, kontakt…) — tentýž
 * jazyk jako [BlockTextField]: tónový blok s velkým zaoblením, fokus a chybu nese rámeček kolem
 * celého bloku. Navíc umí to, co formulář potřebuje: plovoucí popisek, předponu („@"),
 * víc řádků a pomocný text POD blokem (nápověda, počítadlo znaků, chyba).
 *
 * Dřív měly formuláře `OutlinedTextField` a přihlášení s dvoufázovým ověřením tónovaný blok —
 * na jedné obrazovce úpravy profilu tak vedle sebe stály dva vzhledy vstupu (obrysová pole
 * nahoře, tónované bloky u e-mailu a hesla o krok dál). Parametry jsou tytéž jako
 * u `OutlinedTextField`, aby výměna nic neztratila.
 *
 * Výplň je `surfaceContainerHighest` (výchozí výplň M3 vyplněného pole): pole leží na ploše
 * obrazovky, ve sheetu (`surfaceContainerLow`) i v dialogu a na všech je vidět i bez fokusu.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(BLOCK_OUTLINE, blockOutlineColor(isError = isError, active = focused && enabled)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = textStyle,
                label = label,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                prefix = prefix,
                suffix = suffix,
                isError = isError,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                // Výplň i podtržení nese blok kolem, pole samo je průhledné.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                ),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Pomocný text pod blokem, ne v něm — počítadlo a nápověda nejsou součást vstupu.
        if (supportingText != null) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodySmall,
                LocalContentColor provides if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) { supportingText() }
            }
        }
    }
}

/**
 * **Pole jako řádek bloku** (otázka a odpovědi ankety, úkoly seznamu): tónovaná plocha řádku
 * s tvarem podle pozice ([groupItemShape]), bez podtržení — podtržení `TextField`u je jazyk,
 * kterého se aplikace vzdala (viz přihlášení a „Jeden prvek pro hledání"). Na rozdíl od
 * [FormTextField] nemá rámeček ani vlastní zaoblení: pole skládaná pod sebe tvoří jeden blok,
 * jako řádky [GroupedColumn].
 *
 * Počítadlo se ukáže až v posledních [COUNTER_FROM] znacích a přes strop [max] zčervená. Text
 * se nad strop smí kousek pustit (ořez je na volajícím), aby šlo vložit delší text a zkrátit
 * ho; odeslání je do té doby zašedlé.
 */
@Composable
fun GroupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    max: Int,
    shape: Shape,
    singleLine: Boolean,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            placeholder = { Text(placeholder, style = style) },
            singleLine = singleLine,
            leadingIcon = leading,
            trailingIcon = trailing,
            supportingText = counterText(value.length, max)?.let { { it() } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Počítadlo znaků — až v posledních [COUNTER_FROM] znacích, přes strop chybovou barvou. */
private fun counterText(length: Int, max: Int): (@Composable () -> Unit)? {
    if (length < max - COUNTER_FROM) return null
    return {
        Text(
            text = stringResource(Res.string.text_counter, length, max),
            color = if (length > max) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Počítadlo se ukáže, až zbývá tolik znaků. */
private const val COUNTER_FROM = 20
