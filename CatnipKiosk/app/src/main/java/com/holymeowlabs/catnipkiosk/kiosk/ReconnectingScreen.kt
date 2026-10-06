package com.holymeowlabs.catnipkiosk.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.holymeowlabs.catnipkiosk.R
import kotlinx.coroutines.delay

/** Shown over the page while retrying. Never shows the URL: viewers may be the public. */
@Composable
fun ReconnectingScreen(state: KioskUi.Reconnecting, networkAvailable: Boolean, nowMs: () -> Long) {
    var now by remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(state) {
        while (true) {
            now = nowMs()
            delay(250)
        }
    }
    val seconds = ((state.retryAtMs - now + 999) / 1000).coerceAtLeast(0)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.reconnect_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.reconnect_body), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.reconnect_countdown, seconds.toInt(), state.attempt))
        val network = stringResource(if (networkAvailable) R.string.network_connected else R.string.network_offline)
        Text(
            stringResource(R.string.reconnect_status, network),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
