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
        register("verifyContextClickable", VerifyContextClickable::class.java)
        register("verifyExpandedSheets", VerifyExpandedSheets::class.java)
        register("verifyMenuScroll", VerifyMenuScroll::class.java)
        register("verifyRestoringScroll", VerifyRestoringScroll::class.java)
        register("verifyButtonShapes", VerifyButtonShapes::class.java)
        register("verifyChipShapes", VerifyChipShapes::class.java)
        register("verifyShapeTokens", VerifyShapeTokens::class.java)
        register("verifyMotionSpecs", VerifyMotionSpecs::class.java)
        register("verifyCoreLayering", VerifyCoreLayering::class.java, withTests = true)
        register("verifyParameterCount", VerifyParameterCount::class.java)
        register("verifyWindowControls", VerifyWindowControls::class.java)
        register("verifyPaneHeaders", VerifyPaneHeaders::class.java)
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
 * Obsah nabídky roluje (`menuScroll()`, core/ui/ExpressiveMenu.kt). `DropdownMenuPopup`
 * z material3 Expressive to na rozdíl od starého `DropdownMenu` nedělá: okno nabídky je nejvýš
 * tak vysoké jako obrazovka a co přeteče, zůstane pod okrajem bez cesty k němu. Na telefonu
 * naležato tak z menu účtu zmizelo Nastavení. Kontrola chce `menuScroll` mezi argumenty každého
 * volání `DropdownMenuPopup`.
 */
abstract class VerifyMenuScroll : SourceCheckTask() {
    init {
        description = "Ověří, že obsah nabídek roluje (modifier = Modifier.menuScroll())."
    }

    override fun failureMessage() = "Obsah nabídky roluje: DropdownMenuPopup(modifier = Modifier.menuScroll(), …) (core/ui/ExpressiveMenu.kt)."

    private val popupCall = Regex("""\bDropdownMenuPopup\(""")

    override fun offenders(file: File, path: String): List<String> {
        val text = file.readText()
        return popupCall.findAll(text).mapNotNull { match ->
            val end = argumentsEnd(text, match.range.last)
            if ("menuScroll()" in text.substring(match.range.last + 1, end)) {
                null
            } else {
                "$path:${lineOf(text, match.range.first)}: DropdownMenuPopup bez menuScroll()"
            }
        }.toList()
    }
}

/**
 * Svislý obsah roluje přes `rememberRestoringScrollState()` (core/ui/RestoringScrollState.kt),
 * ne `rememberScrollState()`. Obrazovka se po návratu z podstránky skládá znovu a obyčejný
 * `ScrollState` obnovenou pozici při prvním měření TRVALE ořízne; je-li obsah v prvním snímku
 * kratší (řádek, který dorazí z flow o snímek později), seznam zůstane posunutý. Tak uskakovalo
 * Nastavení po návratu z „O aplikaci" i Soukromí po návratu z dvoufázového ověření.
 *
 * Kontrola hledá `verticalScroll(rememberScrollState())` a `val x = rememberScrollState()`,
 * je-li `x` předané do `verticalScroll(`. Vodorovné řady zůstávají na `rememberScrollState()`.
 */
abstract class VerifyRestoringScroll : SourceCheckTask() {
    init {
        description = "Ověří, že svislý obsah drží pozici po návratu (rememberRestoringScrollState())."
    }

    override fun failureMessage() =
        "Svislý obsah roluje přes rememberRestoringScrollState() (core/ui/RestoringScrollState.kt), ne rememberScrollState():"

    private val inline = Regex("""verticalScroll\(\s*rememberScrollState\(\)""")
    private val named = Regex("""\bval (\w+) = rememberScrollState\(\)""")

    override fun offenders(file: File, path: String): List<String> {
        val text = file.readText()
        val inlineHits = inline.findAll(text).map { "$path:${lineOf(text, it.range.first)}: verticalScroll(rememberScrollState())" }
        val namedHits = named.findAll(text).filter { match ->
            Regex("""verticalScroll\(\s*${match.groupValues[1]}\b""").containsMatchIn(text)
        }.map { "$path:${lineOf(text, it.range.first)}: ${it.value} ve verticalScroll" }
        return (inlineHits + namedHits).toList()
    }
}

/**
 * Společný základ kontrol tvarů: každé volání komponenty z [components] (material3) musí mít `shapes`.
 * Jen komponenty importované z material3 — Glance i vlastní komponenty mají jiná pravidla.
 */
abstract class VerifyMaterialShapes : SourceCheckTask() {
    /** Funkce, ne vlastnost: getter úlohy by Gradle chtěl mít s anotací vstupu. */
    protected abstract fun components(): List<String>
    private val call by lazy { Regex("""(?<![\w.])(${components().joinToString("|")})\(""") }

    override fun offenders(file: File, path: String): List<String> {
        val text = file.readText()
        val offenders = mutableListOf<String>()
        call.findAll(text).forEach { match ->
            val name = match.groupValues[1]
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
 * Textová tlačítka mění tvar při stisku jako ikonová (M3 Expressive). Ikonová to měla od #498,
 * textová ze ~350 jen dvě — v jednom dialogu tak jedno tlačítko morfovalo a druhé ne, a nové
 * tlačítko bez tvaru se nepoznalo jinak než ručně. Kontrola chce `shapes` u každého volání
 * `TextButton`/`Button`/`FilledTonalButton`/`OutlinedButton`/`ElevatedButton` z material3.
 * Hlavní akce přes celou šířku jde přes `MediumButton` (core/ui), ten tvar nese sám.
 */
abstract class VerifyButtonShapes : VerifyMaterialShapes() {
    init {
        description = "Ověří, že textová tlačítka mění tvar při stisku (shapes = ButtonDefaults.shapes())."
    }

    override fun failureMessage() = "Tlačítko mění tvar při stisku: shapes = ButtonDefaults.shapes(), hlavní akce přes MediumButton (core/ui)."

    override fun components() = listOf("TextButton", "Button", "FilledTonalButton", "OutlinedButton", "ElevatedButton")
}

/**
 * Čipy mění tvar při stisku a výběru jako tlačítka (M3 Expressive `ChipShapes`: 12 dp, vybraný
 * pilulka, stisknutý 8 dp). Žádné z dvaceti volání `FilterChip` ani `InputChip` je nemělo,
 * takže vedle morfujících tlačítek působily čipy jako nedodělek. Kontrola chce `shapes`
 * u `FilterChip`/`ElevatedFilterChip`/`InputChip` z material3 — jen ty ho v knihovně mají.
 * `AssistChip` a `SuggestionChip` ho nemají, ty jdou přes `ExpressiveAssistChip`
 * / `ExpressiveSuggestionChip` (core/ui/ExpressiveChips.kt) a holé volání z material3 je nález.
 */
abstract class VerifyChipShapes : VerifyMaterialShapes() {
    init {
        description = "Ověří, že čipy mění tvar při stisku a výběru (shapes = FilterChipDefaults.shapes())."
    }

    override fun failureMessage() =
        "Čip mění tvar při stisku a výběru: shapes = FilterChipDefaults.shapes() / InputChipDefaults.shapes(), " +
            "AssistChip / SuggestionChip přes ExpressiveAssistChip / ExpressiveSuggestionChip (core/ui)."

    override fun components() = listOf("FilterChip", "ElevatedFilterChip", "InputChip")

    private val bare = listOf("AssistChip", "ElevatedAssistChip", "SuggestionChip", "ElevatedSuggestionChip")
    private val bareCall = Regex("""(?<![\w.])(${bare.joinToString("|")})\(""")

    override fun offenders(file: File, path: String): List<String> {
        val withoutShapes = super.offenders(file, path)
        if (file.name == "ExpressiveChips.kt") return withoutShapes
        val text = file.readText()
        val imported = bare.filter { "import androidx.compose.material3.$it\n" in text }.toSet()
        if (imported.isEmpty()) return withoutShapes
        return withoutShapes + text.lines().mapIndexedNotNull { index, line ->
            val code = line.trim()
            if (code.startsWith("//") || code.startsWith("*") || code.startsWith("import ")) return@mapIndexedNotNull null
            val name = bareCall.find(code)?.groupValues?.get(1)?.takeIf { it in imported } ?: return@mapIndexedNotNull null
            "$path:${index + 1}: $name z material3 bez tvaru při stisku"
        }
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

/**
 * Funkce nemá víc než [MAX_PARAMETERS] parametrů; co jde nad to, patří do skupin (pravidlo
 * „Parametry funkce se nepřidávají donekonečna" v CLAUDE.md).
 *
 * `MessageInput` dorostl na 79 parametrů, `VideoPage` na 38, `ViewerContent` na 33 — každý
 * přidal „jen jeden" a nikdo si toho nevšiml, dokud se to nemuselo uklízet naráz (#1069). Práh
 * je o kus nad tím, co mají komponenty M3 (`OutlinedTextField` a obal `FormTextField` kolem
 * něj 19), takže obal komponenty projde a seznam callbacků obrazovky ne.
 *
 * Počítají se parametry deklarace, ne volání: komentáře (KDoc u parametru bývá s čárkami)
 * a řetězce se před počítáním vymažou, čárka uvnitř typu (`Map<Int, String>`, `(Int, Int) -> Unit`)
 * ani výchozí hodnoty (`{ _, _ -> }`) se nepočítá.
 */
abstract class VerifyParameterCount : SourceCheckTask() {
    init {
        description = "Ověří, že žádná funkce nemá víc než $MAX_PARAMETERS parametrů."
    }

    override fun failureMessage() =
        "Funkce má víc než $MAX_PARAMETERS parametrů: související seskup do data class (stav se svými callbacky, " +
            "vzor ConversationNavigation; viz „Parametry funkce se nepřidávají donekonečna\" v CLAUDE.md):"

    private val declaration = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(""")

    override fun offenders(file: File, path: String): List<String> {
        val text = codeOnly(file.readText())
        return declaration.findAll(text).mapNotNull { match ->
            val open = match.range.last
            val count = parameterCount(text.substring(open + 1, argumentsEnd(text, open) - 1))
            "$path:${lineOf(text, match.range.first)}: ${match.groupValues[1]} má $count parametrů"
                .takeIf { count > MAX_PARAMETERS }
        }.toList()
    }

    /** Počet parametrů v textu mezi závorkami: čárky na nejvyšší úrovni, bez čárky za posledním. */
    private fun parameterCount(parameters: String): Int {
        var depth = 0
        var count = 0
        var pending = false
        parameters.forEachIndexed { index, char ->
            when {
                char == '(' || char == '[' || char == '{' -> depth++
                char == ')' || char == ']' || char == '}' -> depth--
                // Typový argument (`List<`) začíná hned za jménem; `a < b` ve výchozí hodnotě ne.
                char == '<' && index > 0 && parameters[index - 1].isLetterOrDigit() -> depth++
                // `->` šipku funkčního typu nezavírá.
                char == '>' && parameters.getOrNull(index - 1) != '-' && depth > 0 -> depth--
                char == ',' && depth == 0 -> {
                    if (pending) count++
                    pending = false
                    return@forEachIndexed
                }
            }
            if (!char.isWhitespace()) pending = true
        }
        return if (pending) count + 1 else count
    }

    /** Text bez komentářů a obsahu řetězců; délka i konce řádků zůstávají, aby seděla čísla řádků. */
    private fun codeOnly(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        var blockDepth = 0
        while (i < text.length) {
            val char = text[i]
            val next = text.getOrNull(i + 1)
            when {
                // Blokové komentáře se v Kotlinu vnořují.
                blockDepth > 0 && char == '*' && next == '/' -> { blockDepth--; out.append("  "); i += 2 }
                char == '/' && next == '*' -> { blockDepth++; out.append("  "); i += 2 }
                blockDepth > 0 -> { out.append(if (char == '\n') '\n' else ' '); i++ }
                char == '/' && next == '/' -> {
                    while (i < text.length && text[i] != '\n') { out.append(' '); i++ }
                }
                text.startsWith("\"\"\"", i) -> {
                    val end = text.indexOf("\"\"\"", i + 3).let { if (it < 0) text.length else it + 3 }
                    text.substring(i, end).forEach { out.append(if (it == '\n') '\n' else ' ') }
                    i = end
                }
                char == '"' || char == '\'' -> {
                    var j = i + 1
                    while (j < text.length && text[j] != char && text[j] != '\n') j += if (text[j] == '\\') 2 else 1
                    val end = minOf(j + 1, text.length)
                    repeat(end - i) { out.append(' ') }
                    i = end
                }
                else -> { out.append(char); i++ }
            }
        }
        return out.toString()
    }

    private companion object {
        const val MAX_PARAMETERS = 20
    }
}

/**
 * Každá horní lišta z material3 nechává místo pro tlačítka okna na desktopu: `WindowControlsSpace`
 * v `navigationIcon` a `WindowControlsEndSpace()` na konci `actions` (core/adaptive/WindowControls.kt).
 *
 * S vlastním titulkem (JetBrains Runtime) leží semafory na Macu přes začátek panelu seznamu
 * a tlačítka okna na Windows přes konec nejpravějšího panelu; hlavičky panelů jsou v řádku
 * s nimi. Lišta bez místa by na Windows schovala akce pod zavírací tlačítko, kde nejdou kliknout
 * — a na Macu ani na telefonu by to nikdo nepoznal. Proto kontrola u KAŽDÉ lišty, i u obrazovek
 * přes celé okno (místo tam nezabere nic): do panelu se obrazovka přestěhuje snadno.
 * Vlastní hlavičky bez `TopAppBar` (hledání v konverzaci, kalendář) si místo dávají ručně.
 */
abstract class VerifyWindowControls : SourceCheckTask() {
    init {
        description = "Ověří, že horní lišty nechávají místo pro tlačítka okna na desktopu."
    }

    override fun failureMessage() =
        "Horní lišta nechává místo pro tlačítka okna: navigationIcon = { WindowControlsSpace { … } } a WindowControlsEndSpace() na konci actions (core/adaptive/WindowControls.kt)."

    private val names = listOf(
        "TopAppBar", "LargeFlexibleTopAppBar", "MediumFlexibleTopAppBar", "CenterAlignedTopAppBar",
        "LargeTopAppBar", "MediumTopAppBar", "AppBarWithSearch",
    )
    private val call = Regex("""(?<![\w.])(${names.joinToString("|")})\(""")

    override fun offenders(file: File, path: String): List<String> {
        val text = file.readText()
        val offenders = mutableListOf<String>()
        call.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if ("import androidx.compose.material3.$name\n" !in text) return@forEach
            val lineStart = text.lastIndexOf('\n', match.range.first) + 1
            val before = text.substring(lineStart, match.range.first).trimStart()
            if (before.startsWith("*") || before.startsWith("//") || before.startsWith("fun ") || "import " in before) return@forEach
            val arguments = text.substring(match.range.last + 1, argumentsEnd(text, match.range.last))
            val missing = listOf("WindowControlsSpace", "WindowControlsEndSpace").filter { Regex("""\b$it\b""") !in arguments }
            if (missing.isNotEmpty()) {
                offenders += "$path:${lineOf(text, match.range.first)}: $name bez ${missing.joinToString(" a ")}"
            }
        }
        return offenders
    }
}

/**
 * Hlídá dvě pravidla lišty scény panelů (`PaneScene`, `SceneBar`, `docs/poznatky/navigace.md`,
 * „Vlastní scéna Chatů"), která dřív stála jen v dokumentaci:
 *
 * 1. **Lišta v `PaneHeader` nechá místo pro akce konverzace** (`PaneBandActions()` nebo
 *    `PaneBandSpace()` v argumentech každého `…TopAppBar(`). Akce konverzace kreslí scéna na
 *    pevném místě u pravého okraje (`PaneBandEnd`); lišta bočního sloupce bez místa by pod ně
 *    schovala vlastní akce (Uložit v úpravě kanálu, přepínač roku v kalendáři).
 * 2. **Obrazovka, která počítá s hlavičkou nad kartou** (`PaneBody`, `paneScaffoldColor`), kreslí
 *    hlavičku přes `PaneHeader` nebo `ProfileSubpageTopBar`. Jinak by hlavička zůstala v kartě
 *    pod lištou scény a byly by dvě horní lišty pod sebou — tak dopadla úprava kanálu a kalendář
 *    (VladimirTintera/plume#67).
 *
 * Hledá se v kódu bez komentářů: `PaneHeader` se v KDoc zmiňuje všude, kde se o hlavičkách píše.
 * Soubor s definicemi (`PaneHeader.kt`) se přeskočí. Lišta, která do `PaneHeader` jde z jiného
 * souboru (`ConversationTopBar` z `ConversationScreen`), pravidlem 1 nepokrytá — konverzace je
 * detail, ne boční sloupec, a místo nechává přes `PaneBandSpace()` (viz `PaneBand.kt`).
 */
abstract class VerifyPaneHeaders : SourceCheckTask() {
    init {
        description = "Ověří, že lišty v PaneHeader nechávají místo pro akce konverzace a hlavičky nad kartou jdou přes PaneHeader."
    }

    override fun failureMessage() =
        "Lišta scény panelů (core/adaptive/PaneBand.kt, PaneHeader.kt): lišta v PaneHeader musí mít v actions PaneBandActions() " +
            "(nebo PaneBandSpace()) před WindowControlsEndSpace(), a obrazovka s PaneBody / paneScaffoldColor musí kreslit hlavičku " +
            "přes PaneHeader nebo ProfileSubpageTopBar."

    private val bars = listOf(
        "TopAppBar", "LargeFlexibleTopAppBar", "MediumFlexibleTopAppBar", "CenterAlignedTopAppBar",
        "LargeTopAppBar", "MediumTopAppBar",
    )
    private val barCall = Regex("""(?<![\w.])(${bars.joinToString("|")})\(""")
    private val paneHeaderCall = Regex("""(?<![\w.])PaneHeader\s*[({]""")
    private val outsideBody = Regex("""(?<![\w.])(PaneBody|paneScaffoldColor)\(""")
    private val headerHost = Regex("""(?<![\w.])(PaneHeader\s*[({]|ProfileSubpageTopBar\()""")
    private val bandRoom = Regex("""\bPaneBand(Actions|Space)\(\)""")
    private val declaration = Regex("""\bfun\s+$""")

    override fun offenders(file: File, path: String): List<String> {
        if (file.name == "PaneHeader.kt") return emptyList()
        val text = codeOnly(file.readText())
        val offenders = mutableListOf<String>()
        if (paneHeaderCall.containsMatchIn(text)) {
            barCall.findAll(text).forEach { match ->
                val name = match.groupValues[1]
                if ("import androidx.compose.material3.$name\n" !in text) return@forEach
                val lineStart = text.lastIndexOf('\n', match.range.first) + 1
                if (declaration.containsMatchIn(text.substring(lineStart, match.range.first))) return@forEach
                val arguments = text.substring(match.range.last + 1, argumentsEnd(text, match.range.last))
                if (!bandRoom.containsMatchIn(arguments)) {
                    offenders += "$path:${lineOf(text, match.range.first)}: $name v PaneHeader bez PaneBandActions()"
                }
            }
        }
        outsideBody.find(text)?.let { match ->
            if (!headerHost.containsMatchIn(text)) {
                offenders += "$path:${lineOf(text, match.range.first)}: ${match.groupValues[1]} bez PaneHeader / ProfileSubpageTopBar"
            }
        }
        return offenders
    }

    /**
     * Text bez komentářů, se zachovanými konci řádků (čísla řádků v hlášce sedí). Řetězce se
     * neřeší: `//` v řetězci by zbytek řádku zahodilo, což tu nevadí — hledaná volání v řetězcích nejsou.
     */
    private fun codeOnly(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                    text.substring(i, end).forEach { if (it == '\n') out.append('\n') }
                    i = end
                }
                text.startsWith("//", i) -> {
                    val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    i = end
                }
                else -> {
                    out.append(text[i])
                    i++
                }
            }
        }
        return out.toString()
    }
}
