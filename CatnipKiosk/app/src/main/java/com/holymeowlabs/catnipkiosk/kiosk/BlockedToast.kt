package com.holymeowlabs.catnipkiosk.kiosk

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.holymeowlabs.catnipkiosk.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

private const val SHOW_MS = 3_000L

/** "That page isn't available" for ~3 s after each blocked navigation. */
@Composable
fun BlockedToast(events: Flow<Unit>) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(events) {
        events.collectLatest {
            visible = true
            delay(SHOW_MS)
            visible = false
        }
    }
    if (!visible) return
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            stringResource(R.string.blocked_message),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
