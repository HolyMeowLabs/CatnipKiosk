package com.holymeowlabs.catnipkiosk.lockdown

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.holymeowlabs.catnipkiosk.MainActivity
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Starts the kiosk after boot when it would not come back on its own (see [BootDecision]). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SettingsRepository.get(context)
                val settings = repo.settings.first()
                val configured = settings != null && repo.security.first() != null
                val lockdown = LockdownController(context)
                val start = BootDecision.shouldStart(
                    startOnBoot = settings?.startOnBoot == true,
                    configured = configured,
                    isHome = lockdown.startsAsHome(),
                    isDeviceOwner = lockdown.tier() == LockdownTier.HARD,
                    canStartFromBackground = lockdown.canStartFromBackground(),
                )
                if (start) {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
