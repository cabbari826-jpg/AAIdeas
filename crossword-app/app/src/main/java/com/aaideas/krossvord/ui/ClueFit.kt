package com.aaideas.krossvord.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Largest and smallest clue text in a grid cell, in dp: the cell never grows, so the
 *  phone's font-size setting is not applied here (big text has its own line under the grid). */
const val CLUE_MAX_DP = 12f
const val CLUE_MIN_DP = 6f
/** Smallest size we accept as readable when checking clue texts in tests. */
const val CLUE_READABLE_DP = 7.5f

/** True if a line ends in the middle of a word (the text wrapped inside a word). */
fun breaksWord(r: TextLayoutResult, text: String): Boolean =
    (0 until r.lineCount - 1).any { i ->
        val end = r.getLineEnd(i)
        end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] != '-'
    }

fun clueStyle(color: Color, size: TextUnit) =
    TextStyle(color = color, fontSize = size, lineHeight = size * 1.05f, textAlign = TextAlign.Center)

/**
 * Biggest font (in dp, from [CLUE_MAX_DP] down to [CLUE_MIN_DP]) at which [text] fits
 * [widthPx] x [heightPx] without breaking a word. Returns the size in dp.
 */
fun fitClueDp(measurer: TextMeasurer, text: String, widthPx: Int, heightPx: Int, density: Density): Float {
    var dp = CLUE_MAX_DP
    while (dp > CLUE_MIN_DP) {
        val size = with(density) { dp.dp.toSp() }
        val r = measurer.measure(text, clueStyle(Color.Black, size), constraints = Constraints(maxWidth = widthPx), density = density)
        if (r.size.height <= heightPx && !breaksWord(r, text)) return dp
        dp -= 0.5f
    }
    return CLUE_MIN_DP
}

/** Clue text that picks its own size to fill the space it is given. */
@Composable
fun FitClueText(text: String, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val dp = remember(text, w, h, density) { fitClueDp(measurer, text, w, h, density) }
        BasicText(text, style = clueStyle(color, with(density) { dp.dp.toSp() }))
    }
}
