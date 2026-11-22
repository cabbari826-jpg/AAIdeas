package com.aaideas.krossvord.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aaideas.krossvord.model.Board
import com.aaideas.krossvord.model.Direction
import com.aaideas.krossvord.model.Pack
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every clue of every pack must fit its grid cell at a readable size, without breaking a word. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ClueFitTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyClueFitsItsCell() {
        lateinit var measurer: TextMeasurer
        rule.setContent { measurer = rememberTextMeasurer() }
        rule.waitForIdle()
        val density = Density(2.75f, fontScale = 1f)
        val px = { dp: Float -> (dp * density.density).toInt() }
        // A small phone: 360 dp wide, 7 columns, minus padding → about 46 dp per cell.
        val cell = 46f
        val packs = java.io.File("src/main/assets/packs").listFiles()!!.filter { it.name.endsWith(".json") }
            .map { Pack.parse(it.readText()) }
        val bad = mutableListOf<String>()
        val sizes = mutableListOf<Float>()
        for (p in packs) for (level in p.levels) {
            val board = Board(level)
            for ((_, clues) in board.cluesAt) for (e in clues) for (t in e.clueLevels.distinct()) {
                val text = cellClue(t) + if (e.direction == Direction.ACROSS) " →" else " ↓"
                // Inner box: cell minus padding, split between the clues of the cell, minus the dots line.
                val w = px(cell - 4f)
                val h = px((cell - 2f) / clues.size - 5f)
                val dp = fitClueDp(measurer, text, w, h, density)
                sizes += dp
                if (dp < CLUE_READABLE_DP) bad += "${e.answer}: \"$t\" (${dp}dp, ${clues.size} in cell)"
            }
        }
        println("clue sizes: " + sizes.groupingBy { it }.eachCount().toSortedMap())
        assertTrue("clues too long for a cell:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
