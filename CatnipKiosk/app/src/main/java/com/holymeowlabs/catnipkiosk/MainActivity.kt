package com.holymeowlabs.catnipkiosk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Placeholder until the kiosk and setup screens land.
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0F1410)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.app_name), color = Color(0xFFEEF2EA))
            }
        }
    }
}
