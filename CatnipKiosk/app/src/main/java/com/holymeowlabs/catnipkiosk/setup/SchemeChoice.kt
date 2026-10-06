package com.holymeowlabs.catnipkiosk.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.holymeowlabs.catnipkiosk.R

/** https / http for an address typed without a scheme; https is the default. */
@Composable
fun SchemeChoice(scheme: Scheme, onChange: (Scheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = scheme == Scheme.HTTPS,
            onClick = { onChange(Scheme.HTTPS) },
            label = { Text(stringResource(R.string.scheme_https)) },
            modifier = Modifier.testTag("scheme_https"),
        )
        FilterChip(
            selected = scheme == Scheme.HTTP,
            onClick = { onChange(Scheme.HTTP) },
            label = { Text(stringResource(R.string.scheme_http)) },
            modifier = Modifier.testTag("scheme_http"),
        )
    }
}

@Composable
fun HttpWarning() {
    Text(stringResource(R.string.http_warning), color = MaterialTheme.colorScheme.error)
}
