package com.holymeowlabs.catnipkiosk.pin

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.holymeowlabs.catnipkiosk.R
import kotlinx.coroutines.delay

/** 3×4 keypad of real buttons (D-pad friendly); number keys type digits; Back cancels. */
@Composable
fun PinScreen(
    state: PinUi,
    nowMs: () -> Long,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val five = remember { FocusRequester() }
    LaunchedEffect(Unit) { five.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp)
            .onPreviewKeyEvent { e ->
                val code = e.key.nativeKeyCode
                if (e.type == KeyEventType.KeyDown && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                    onDigit('0' + (code - KeyEvent.KEYCODE_0))
                    true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.pin_title), style = MaterialTheme.typography.headlineSmall)
        Text("●".repeat(state.digits).ifEmpty { " " }, style = MaterialTheme.typography.headlineMedium)
        PinMessage(state, nowMs)
        TextButton(onClick = onCancel) { Text(stringResource(R.string.pin_cancel)) }
        for (row in listOf("123", "456", "789")) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (d in row) {
                    val focus = if (d == '5') Modifier.focusRequester(five) else Modifier
                    Key(d.toString(), focus) { onDigit(d) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Key(stringResource(R.string.pin_delete), onClick = onDelete)
            Key("0") { onDigit('0') }
            Key(stringResource(R.string.pin_ok), onClick = onOk)
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.size(width = 96.dp, height = 56.dp)) { Text(label) }
}

@Composable
private fun PinMessage(state: PinUi, nowMs: () -> Long) {
    val until = state.lockedUntilMs
    if (until != null) {
        var now by remember { mutableLongStateOf(nowMs()) }
        LaunchedEffect(until) {
            while (now < until) {
                delay(250)
                now = nowMs()
            }
        }
        if (now < until) {
            val seconds = ((until - now + 999) / 1000).toInt()
            Text(stringResource(R.string.pin_locked, seconds), color = MaterialTheme.colorScheme.error)
            return
        }
    }
    when (val error = state.error) {
        is PinError.Wrong -> {
            val left = error.attemptsLeft
            Text(
                if (left == null) stringResource(R.string.pin_wrong)
                else pluralStringResource(R.plurals.pin_wrong_attempts, left, left),
                color = MaterialTheme.colorScheme.error,
            )
        }
        null -> Text(" ")
    }
}
