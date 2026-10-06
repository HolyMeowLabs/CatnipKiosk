package com.holymeowlabs.catnipkiosk.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/** Text fields keep D-pad Up/Down for themselves; on TV that traps focus, so move it on instead. */
fun Modifier.dpadExitsTextField(): Modifier = composed {
    val focus = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
            Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
            else -> false
        }
    }
}
