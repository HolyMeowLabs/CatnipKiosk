package com.holymeowlabs.catnipkiosk.settingsui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.holymeowlabs.catnipkiosk.R
import com.holymeowlabs.catnipkiosk.ui.dpadExitsTextField
import kotlinx.coroutines.launch

@Composable
fun ChangePinDialog(change: suspend (old: String, new: String, confirm: String) -> ChangePinResult, onDone: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ChangePinResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.settings_change_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(old, { old = it }, R.string.settings_pin_current)
                PinField(new, { new = it }, R.string.settings_pin_new)
                PinField(confirm, { confirm = it }, R.string.setup_pin_confirm_label)
                val message = when (result) {
                    ChangePinResult.WrongOldPin -> R.string.settings_pin_wrong_old
                    ChangePinResult.Mismatch -> R.string.setup_pin_mismatch
                    ChangePinResult.InvalidNewPin -> R.string.setup_pin_invalid
                    ChangePinResult.Ok, null -> null
                }
                if (message != null) Text(stringResource(message), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val r = change(old, new, confirm)
                        busy = false
                        if (r == ChangePinResult.Ok) onDone() else result = r
                    }
                },
            ) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.pin_cancel)) } },
    )
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(8)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.dpadExitsTextField(),
    )
}
