package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/** Nadpisy bloků a sheetů jsou pro čtečku obrazovky nadpisy — dá se mezi nimi skákat. */
@OptIn(ExperimentalTestApi::class)
class HeadingSemanticsTest {

    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun nadpisBlokuASheetuJeNadpis() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column {
                    SheetTitle("Upozornění")
                    GroupTitle("Soukromí")
                }
            }
        }

        onNodeWithText("Upozornění").assert(heading)
        onNodeWithText("Soukromí").assert(heading)
    }
}
