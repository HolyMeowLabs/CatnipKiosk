package com.holymeowlabs.catnipkiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only: configures the kiosk without the setup wizard (Task 10), e.g.
 * `adb shell am broadcast -n com.holymeowlabs.catnipkiosk/.DebugSeedReceiver --es url http://10.0.2.2:8000/`.
 * Delete once the setup wizard exists.
 */
class DebugSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val repo = SettingsRepository.get(context)
            repo.saveSettings(KioskSettings(startUrl = url))
            // Placeholder security record; there is no PIN screen yet.
            repo.saveSecurity(SecurityState("ZGVidWc=", "ZGVidWc=", 1))
            pending.finish()
        }
    }
}
