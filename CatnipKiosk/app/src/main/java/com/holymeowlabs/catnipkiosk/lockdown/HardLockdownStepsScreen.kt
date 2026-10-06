package com.holymeowlabs.catnipkiosk.lockdown

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.holymeowlabs.catnipkiosk.R

const val SET_DEVICE_OWNER_COMMAND =
    "adb shell dpm set-device-owner com.holymeowlabs.catnipkiosk/.lockdown.KioskDeviceAdminReceiver"

@Composable
fun HardLockdownStepsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
            .widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.hard_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.hard_what))
        Text(stringResource(R.string.hard_prereq_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.hard_prereq_accounts))
        Text(stringResource(R.string.hard_prereq_usb))
        Text(stringResource(R.string.hard_run_title), style = MaterialTheme.typography.titleMedium)
        SelectionContainer {
            Text(
                SET_DEVICE_OWNER_COMMAND,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            )
        }
        Text(stringResource(R.string.hard_after))
        Text(stringResource(R.string.hard_warning), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Button(onClick = onBack) { Text(stringResource(R.string.setup_back)) }
    }
}
