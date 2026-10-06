package com.holymeowlabs.catnipkiosk.cursor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.holymeowlabs.catnipkiosk.R
import kotlinx.coroutines.delay

private const val HINT_AFTER_ENABLE_MS = 3_000L
private const val HINT_AFTER_IDLE_MS = 10_000L

/**
 * Draws the cursor at [position] (view pixels) and the usage hint: for 3 s when the cursor first
 * appears, and again after 10 s without a move. [lastMoveMs] changes on every move.
 */
@Composable
fun CursorOverlay(position: Offset, lastMoveMs: Long) {
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(HINT_AFTER_ENABLE_MS)
        hint = false
    }
    LaunchedEffect(lastMoveMs) {
        if (lastMoveMs == 0L) return@LaunchedEffect
        hint = false
        delay(HINT_AFTER_IDLE_MS)
        hint = true
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val r = 14.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.85f), radius = r, center = position)
            drawCircle(Color.Black, radius = r, center = position, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color.Black, radius = 2.dp.toPx(), center = position)
        }
        if (hint) {
            Text(
                stringResource(R.string.cursor_hint),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}
