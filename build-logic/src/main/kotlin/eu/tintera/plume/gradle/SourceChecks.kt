package eu.tintera.plume.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Kontroly zdrojáků na `check`: pravidla z CLAUDE.md, která by se jinak hlídala jen čtením.
 *
 * Každá kontrola projde `.kt` soubory produkčních source setů modulu (`src/<jméno>Main/kotlin`,
 * [VerifyCoreLayering] i testových) a selže se seznamem nálezů `cesta:řádek: …`. Modul, kterému source set chybí (třeba `core` bez
 * UI), ho prostě nemá — kontrola bez nálezu projde.
 */
class SourceChecksPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(LifecycleBasePlugin::class.java)
        val sourceSets = project.layout.projectDirectory.dir("src").asFile
            .listFiles { dir -> dir.isDirectory }
            .orEmpty()
            .sortedBy { it.name }
        val mainSources = sourceSets.filter { it.name.endsWith("Main") }.map { File(it, "kotlin") }
        val allSources = sourceSets.map { File(it, "kotlin") }

        fun register(name: String, type: Class<out SourceCheckTask>, withTests: Boolean = false) {
            val task = project.tasks.register(name, type) {
                this.sources.from(if (withTests) allSources else mainSources)
                baseDirectory.set(project.layout.projectDirectory)
            }
            project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(task) }
        }

        register("verifyNoHardcodedText", VerifyNoHardcodedText::class.java)
        register("verifyResourceReadsOffMain", VerifyResourceReadsOffMain::class.java)
        register("verifyTooltipsThroughRoot", VerifyTooltipsThroughRoot::class.java)
        register("verifyContextClickable", VerifyContextClickable::class.java)
        register("verifyExpandedSheets", VerifyExpandedSheets::class.java)
        register("verifyButtonShapes", VerifyButtonShapes::class.java)
        register("verifyShapeTokens", VerifyShapeTokens::class.java)
        register("verifyMotionSpecs", VerifyMotionSpecs::class.java)
        register("verifyCoreLayering", VerifyCoreLayering::class.java, withTests = true)
    }
}

/** Společný základ: projde soubory, posbírá nálezy a selže s [failureMessage] nad jejich seznamem. */
abstract class SourceCheckTask : DefaultTask() {

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Od čeho se počítají cesty v hlášce — adresář modulu, jako dřív v jeho build skriptu. */
    @get:Internal
    abstract val baseDirectory: DirectoryProperty

    /** Úvod hlášky; za ním jde na dalších řádcích seznam nálezů. */
    protected abstract fun failureMessage(): String

    /** Nálezy v jednom souboru; [path] je cesta pro hlášku. */
    protected abstract fun offenders(file: File, path: String): List<String>

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
    }

    @TaskAction
    fun verify() {
        val base = baseDirectory.get().asFile
        val offenders = sources.files
            .filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
            .flatMap { offenders(it, it.relativeTo(base).path) }
        check(offenders.isEmpty()) { failureMessage() + "\n" + offenders.joinToString("\n") }
    }

    /** Řádky souboru, které odpovídají [pattern] (po oříznutí), mimo soubor [exempt] s obalem pravidla. */
    protected fun matchingLines(
        file: File,
        path: String,
        pattern: Regex,
        exempt: String,
        skipComments: Boolean = false,
    ): List<String> {
        if (file.name == exempt) return emptyList()
        return file.readLines().mapIndexedNotNull { index, line ->
            val code = line.trim()
            val comment = skipComments && (code.startsWith("//") || code.startsWith("*"))
            "$path:${index + 1}: $line".takeIf { !comment && pattern.containsMatchIn(code) }
        }
    }

    /** Konec argumentů volání: index za párovou závorkou k té, která končí na [openParen]. */
    protected fun argumentsEnd(text: String, openParen: Int): Int {
        var depth = 1
        var end = openParen + 1
        while (depth > 0 && end < text.length) {
            when (text[end]) {
                '(' -> depth++
                ')' -> depth--
            }
            end++
        }
        return end
    }

    protected fun lineOf(text: String, index: Int): Int = text.substring(0, index).count { it == '\n' } + 1
}

/**
 * Hlídá, že se do UI nevrátí natvrdo psaný text.
 *
 * Propadlo to dvakrát: první kontrola hledala jen texty s diakritikou, takže „Odebrat soubor"
 * ani „Video" nenašla. Proto pravidla dvě — slot v UI nesmí dostat literál vůbec, a český text
 * nesmí být nikde v kódu mimo komentáře, logy a vnitřní kontroly (`require`/`check`), protože
 * jazykem zdrojáku je od téhle chvíle angličtina v prostředcích.
 */
abstract class VerifyNoHardcodedText : SourceCheckTask() {
    init {
        description = "Ověří, že texty pro uživatele jsou v prostředcích, ne v kódu."
    }

    override fun failureMessage() = "Text pro uživatele patří do composeResources, ne do kódu:"

    // Sloty, do kterých se kreslí text pro uživatele. `label =` tady schválně není:
    // stejný název nese i popisek animace (`AnimatedContent(label = "delivery")`).
    private val uiSlot = Regex("""(?:\bText\(|\btext = |contentDescription = |placeholder = |onClickLabel = |headlineContent|supportingContent)\s*\{?\s*(?:Text\()?"""")
    private val czech = Regex("""\"[^\"]*[áčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][^\"]*\"""")

    // Řádky, kde text pro uživatele není: logy a vnitřní kontroly.
    private val internal = Regex("""logger\.[dewiv]\b|Logger\.withTag|\brequire|\bcheck|\berror\(|Exception\(""")

    // Prompt pro jazykový model taky text pro uživatele není — nejde na obrazovku, ale do
    // modelu v telefonu. Česká slova v něm bývají POTŘEBA: bez výslovného „tykání/vykání"
    // model kamarádovi vykal (viz „Návrhy odpovědí" v docs/poznatky/ai-v-zarizeni.md). Je to tedy stejná kategorie
    // jako logy a vnitřní kontroly výš, a řeší se stejně — výjimkou, ne escape sekvencemi:
    // ty jsou správně u DAT (tabulka diakritiky v `SearchHighlight.kt`), ale prompt je próza,
    // která se ladí čtením, a „tykání" uprostřed anglické věty se číst nedá.
    // Výjimka je proto UŽŠÍ než ty stávající: vypíná JEN pravidlo o diakritice, a jen
    // v souborech, které nenesou nic jiného než prompty. Slot v UI se hlídá i v nich
    // (dvě pravidla jsou tu schválně) a prompt přesunutý jinam kontrolu rozsvítí.
    private val promptFile = Regex("""core/ai/[A-Za-z]+Prompt\.kt$""")

    override fun offenders(file: File, path: String): List<String> {
        val isPrompt = promptFile.containsMatchIn(file.invariantSeparatorsPath)
        val lines = file.readLines()
        val offenders = mutableListOf<String>()
        var inBlockComment = false
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.startsWith("/*")) inBlockComment = true
            if (inBlockComment) {
                if (line.contains("*/")) inBlockComment = false
                return@forEachIndexed
            }
            if (line.startsWith("//") || line.startsWith("*")) return@forEachIndexed

            val context = lines.subList(maxOf(0, index - 3), index + 1).joinToString("\n")
            if (internal.containsMatchIn(context)) return@forEachIndexed

            // Prázdný literál žádný text nenese (`MessageBody(text = "")`).
            val meaningful = line.replace("\"\"", "")
            if (uiSlot.containsMatchIn(meaningful) || (!isPrompt && czech.containsMatchIn(meaningful))) {
                offenders += "$path:${index + 1}: $line"
            }
        }
        return offenders
    }
}

/**
 * Hlídá, že suspend čtení textu z prostředků jde jen přes `readString` / `readPluralString`
 * (`core/ui/ResourceStrings.kt`), které čtou mimo hlavní vlákno.
 *
 * Přímé `getString` z korutiny na `Dispatchers.Main` umí s `stringResource` v kompozici
 * zamknout hlavní vlákno natrvalo — zdůvodnění je v KDoc `readString`. Volání bylo přes
 * šedesát a každé nové by past otevřelo znovu, aniž by si toho kdo všiml: deadlock je souběh,
 * v testech ani při zkoušení se neukáže.
 */
abstract class VerifyResourceReadsOffMain : SourceCheckTask() {
    init {
        description = "Ověří, že se text z prostředků mimo kompozici čte přes readString."
    }

    override fun failureMessage() =
        "Text z prostředků se mimo kompozici čte přes readString / readPluralString (core/ui/ResourceStrings.kt):"

    private val directRead = Regex("""^import org\.jetbrains\.compose\.resources\.(?:getString|getPluralString|getStringArray|\*)\b""")

    override fun offenders(file: File, path: String) = matchingLines(file, path, directRead, exempt = "ResourceStrings.kt")
}

/**
 * Hlídá, že bubliny jdou přes `WithTooltip` / `RootTooltipBox` (`core/ui/WithTooltip.kt`), které je
 * kreslí z kořene kompozice, a nikde jinde se nepoužije holý M3 `TooltipBox`.
 *
 * Holý `TooltipBox` na desktopu shodí vykreslování (`RootNodeOwner is already disposed`), když jeho
 * kotva s viditelnou bublinou zmizí z kompozice během měření — větev `BoxWithConstraints` podle
 * šířky, slot `Scaffold`u, položka lazy seznamu. Při zkoušení se to neukáže: musí se sejít
 * viditelná bublina se změnou rozložení, třeba tahem za okraj okna.
 */
abstract class VerifyTooltipsThroughRoot : SourceCheckTask() {
    init {
        description = "Ověří, že se bubliny kreslí přes WithTooltip / RootTooltipBox, ne holým TooltipBox."
    }

    override fun failureMessage() =
        "Bublina se kreslí přes WithTooltip nebo RootTooltipBox (core/ui/WithTooltip.kt), ne holým TooltipBox:"

    private val rawTooltip = Regex("""^import androidx\.compose\.material3\.(?:TooltipBox|\*)\b|\bandroidx\.compose\.material3\.TooltipBox\(""")

    override fun offenders(file: File, path: String) = matchingLines(file, path, rawTooltip, exempt = "WithTooltip.kt")
}

/**
 * Podržení (`onLongClick`) otevírá v aplikaci nabídky a na desktopu ho má zastoupit pravé tlačítko
 * myši. Umí to jen obal `contextClickable` (core/ui/ContextClickable.kt); holé `combinedClickable`
 * pravé tlačítko nebere, takže by se nabídka na desktopu zase dala otevřít jen dlouhým stiskem.
 */
abstract class VerifyContextClickable : SourceCheckTask() {
    init {
        description = "Ověří, že se podržení zapojuje přes contextClickable (s pravým tlačítkem), ne holým combinedClickable."
    }

    override fun failureMessage() = "Podržení se zapojuje přes contextClickable (core/ui/ContextClickable.kt) — pravé tlačítko myši " +
        "pak udělá totéž. Holé combinedClickable:"

    // Volání, ne zmínka v komentáři („`combinedClickable` si haptiku dělá sám").
    private val rawCombined = Regex("""^import androidx\.compose\.foundation\.combinedClickable\b|(?<!`)\bcombinedClickable\(""")

    override fun offenders(file: File, path: String) =
        matchingLines(file, path, rawCombined, exempt = "ContextClickable.kt", skipComments = true)
}

/**
 * Modální sheet se otevírá rovnou celý (`rememberExpandedSheetState`, core/ui/ExpandedSheet.kt).
 * Napůl rozbalený schovával pod okraj výsledek, tlačítko Uložit nebo poslední položky a opravoval
 * se po jednom; pravidlo platí pro všechny. Kontrola chce, aby každý `ModalBottomSheet` dostal
 * stav (výchozí rozbaluje napůl) a aby stav nevznikal jinde než v pomocníkovi.
 */
abstract class VerifyExpandedSheets : SourceCheckTask() {
    init {
        description = "Ověří, že se modální sheety otevírají celé (rememberExpandedSheetState)."
    }

    override fun failureMessage() = "Modální sheet se otevírá celý: sheetState = rememberExpandedSheetState() (core/ui/ExpandedSheet.kt)."

    private val rawState = Regex("""\b(rememberBottomSheetState|rememberModalBottomSheetState)\(""")
    private val sheetCall = Regex("""\bModalBottomSheet\(""")

    override fun offenders(file: File, path: String): List<String> {
        if (file.name == "ExpandedSheet.kt") return emptyList()
        val text = file.readText()
        val offenders = mutableListOf<String>()
        rawState.findAll(text).forEach { offenders += "$path:${lineOf(text, it.range.first)}: ${it.value} (patří do rememberExpandedSheetState)" }
        sheetCall.findAll(text).forEach { match ->
            // Argumenty volání po párovou závorku; `sheetState` musí být mezi nimi.
            val end = argumentsEnd(text, match.range.last)
            if ("sheetState" !in text.substring(match.range.last + 1, end)) {
                offenders += "$path:${lineOf(text, match.range.first)}: ModalBottomSheet bez sheetState"
            }
        }
        return offenders
    }
}

/**
 * Textová tlačítka mění tvar při stisku jako ikonová (M3 Expressive). Ikonová to měla od #498,
 * textová ze ~350 jen dvě — v jednom dialogu tak jedno tlačítko morfovalo a druhé ne, a nové
 * tlačítko bez tvaru se nepoznalo jinak než ručně. Kontrola chce `shapes` u každého volání
 * `TextButton`/`Button`/`FilledTonalButton`/`OutlinedButton`/`ElevatedButton` z material3.
 * Hlavní akce přes celou šířku jde přes `MediumButton` (core/ui), ten tvar nese sám.
 */
abstract class VerifyButtonShapes : SourceCheckTask() {
    init {
        description = "Ověří, že textová tlačítka mění tvar při stisku (shapes = ButtonDefaults.shapes())."
    }

    override fun failureMessage() = "Tlačítko mění tvar při stisku: shapes = ButtonDefaults.shapes(), hlavní akce přes MediumButton (core/ui)."

    private val names = listOf("TextButton", "Button", "FilledTonalButton", "OutlinedButton", "ElevatedButton")
    private val call = Regex("""(?<![\w.])(${names.joinToString("|")})\(""")

    override fun offenders(file: File, path: String): List<String> {
        val text = file.readText()
        val offenders = mutableListOf<String>()
        call.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            // Jen tlačítka z material3 — Glance i vlastní komponenty mají jiná pravidla.
            if ("import androidx.compose.material3.$name\n" !in text) return@forEach
            val lineStart = text.lastIndexOf('\n', match.range.first) + 1
            val before = text.substring(lineStart, match.range.first).trimStart()
            if (before.startsWith("*") || before.startsWith("//") || before.startsWith("fun ") || "import " in before) return@forEach
            val end = argumentsEnd(text, match.range.last)
            if ("shapes" !in text.substring(match.range.last + 1, end)) {
                offenders += "$path:${lineOf(text, match.range.first)}: $name bez shapes"
            }
        }
        return offenders
    }
}

/**
 * Zaoblení ze škály M3 Expressive se píše tokenem (`ShapeDefaults.Large`, `.LargeIncreased`…),
 * ne číslem. V kódu bylo ~200 `RoundedCornerShape(N.dp)` proti 21 tokenům a kopie téhož čísla
 * se rozcházely (16 vs. 20 u bloků). Hodnoty mimo škálu (tvary bublin, médií) kontrola pouští —
 * jsou to vlastní tvary, ne odchylka od tokenu. Bloky zaoblených řádků mají `GROUP_CORNER`.
 */
abstract class VerifyShapeTokens : SourceCheckTask() {
    init {
        description = "Ověří, že zaoblení ze škály M3 jde přes ShapeDefaults, ne RoundedCornerShape(N.dp)."
    }

    override fun failureMessage() = "Zaoblení ze škály M3 patří do tokenu ShapeDefaults (bloky řádků: GROUP_CORNER)."

    private val tokens = mapOf(
        "4" to "ExtraSmall", "8" to "Small", "12" to "Medium", "16" to "Large",
        "20" to "LargeIncreased", "28" to "ExtraLarge", "32" to "ExtraLargeIncreased", "48" to "ExtraExtraLarge",
    )
    private val literal = Regex("""RoundedCornerShape\((\d+)\.dp\)""")

    override fun offenders(file: File, path: String): List<String> {
        val text = file.readText()
        return literal.findAll(text).mapNotNull { match ->
            val token = tokens[match.groupValues[1]] ?: return@mapNotNull null
            "$path:${lineOf(text, match.range.first)}: ${match.value} → ShapeDefaults.$token"
        }.toList()
    }
}

/**
 * Přechody bez specu (`fadeIn()`, `expandVertically()`, `slideInVertically { … }`) jedou na
 * výchozí pružinu Compose, ne na `AppMotion` — v konverzaci jich bylo přes devadesát a pohyb
 * pruhů, tlačítek a nabídek tak neladil se zbytkem aplikace. Kontrola chce spec z
 * `MaterialTheme.motionScheme` (příchod `default…`, odchod `fast…`; v `transitionSpec`
 * u `AnimatedContent`, která není @Composable, se motionScheme načte předem).
 */
abstract class VerifyMotionSpecs : SourceCheckTask() {
    init {
        description = "Ověří, že přechody AnimatedVisibility/AnimatedContent mají spec z motionScheme."
    }

    override fun failureMessage() =
        "Přechod bez specu jede mimo AppMotion: předej MaterialTheme.motionScheme.default…Spec() / fast…Spec()."

    private val bare = Regex("""(?<![\w.])(fadeIn|fadeOut|expandVertically|shrinkVertically|expandHorizontally|shrinkHorizontally|expandIn|shrinkOut|scaleIn|scaleOut)\(\)|(?<![\w.])slide(In|Out)(Vertically|Horizontally) \{""")

    override fun offenders(file: File, path: String): List<String> {
        val text = file.readText()
        return bare.findAll(text).map { "$path:${lineOf(text, it.range.first)}: ${it.value}" }.toList()
    }
}

/**
 * Vrstva `core` nesahá na `features` ani `app` — ani v testech. Je to příprava na vyčlenění
 * `:client:core` (roadmapa, „Rozdělení `:client:shared`"): tam by se takový odkaz nepřeložil,
 * a dokud je všechno v jednom modulu, kompilátor ho pustí a nikdo si ho nevšimne.
 *
 * - **Typ, který potřebují obě vrstvy, patří do `core`** (uložené výsledky modelu v
 *   `core/settings/StoredModelResults.kt`, `ChatSummary.hasUnread`).
 * - **Volá-li `core` kód z `features`, rozhraní je v `core` a implementace ve `features`**
 *   (`BackgroundPhotoDescription` ← `BackgroundPhotoDescriber`, zapojí ho Koin).
 * - **Testovací náhražky typů z `core` leží v testech `core`** (`FakeChatRepository`,
 *   `testMessage`), test funkce z `features` mezi testy `features`.
 * - Odkaz v komentáři (KDoc „kdo tohle používá") nevadí — po rozdělení se jen nepropojí.
 */
abstract class VerifyCoreLayering : SourceCheckTask() {
    init {
        description = "Ověří, že vrstva core nesahá na features ani app (příprava na :client:core)."
    }

    override fun failureMessage() =
        "Vrstva core nesahá na features ani app: sdílený typ patří do core, volání z core do features jde přes rozhraní v core."

    private val upperLayer = Regex("""\beu\.tintera\.plume\.(?:features|app)\.""")

    override fun offenders(file: File, path: String): List<String> {
        if ("/eu/tintera/plume/core/" !in file.invariantSeparatorsPath) return emptyList()
        return matchingLines(file, path, upperLayer, exempt = "", skipComments = true)
            .filterNot { it.substringAfter(": ").trim().startsWith("/*") }
    }
}
