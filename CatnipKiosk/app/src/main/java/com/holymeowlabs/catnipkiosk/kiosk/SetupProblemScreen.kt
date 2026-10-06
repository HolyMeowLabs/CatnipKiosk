package com.holymeowlabs.catnipkiosk.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.holymeowlabs.catnipkiosk.R

/** The start page leads off the allowed hosts; names only the host for the admin. */
@Composable
fun SetupProblemScreen(blockedHost: String) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.setup_problem_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.setup_problem_body, blockedHost), style = MaterialTheme.typography.bodyLarge)
    }
}
