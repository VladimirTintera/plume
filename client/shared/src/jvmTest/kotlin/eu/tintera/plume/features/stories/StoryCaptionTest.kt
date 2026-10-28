package eu.tintera.plume.features.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Popisek příběhu: dlouhý se ťuknutím rozbalí a sbalí (s popisem akce pro čtečku), krátký
 * ťuknutí nebere — to patří přepínání příběhů pod ním.
 */
@OptIn(ExperimentalTestApi::class)
class StoryCaptionTest {

    private val previousLocale = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(previousLocale)

    private fun hasClickLabel(label: String) = SemanticsMatcher("onClick label $label") {
        it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == label
    }

    @Test
    fun dlouhyPopisekSeRozbaliASbali() = runComposeUiTest {
        val long = (1..40).joinToString(" ") { "Řádek číslo $it dlouhého popisku příběhu." }
        var expanded by mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    StoryCaption(long, expanded, onExpandedChange = { changes += it; expanded = it })
                }
            }
        }
        onNodeWithText(long).assert(hasClickLabel("Show full caption")).performClick()
        waitForIdle()
        onNodeWithText(long).assert(hasClickLabel("Collapse caption")).performClick()
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun kratkyPopisekTuknutiNebere() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    var expanded by remember { mutableStateOf(false) }
                    StoryCaption("Krátký popisek", expanded, onExpandedChange = { expanded = it })
                }
            }
        }
        onNodeWithText("Krátký popisek").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }
}
