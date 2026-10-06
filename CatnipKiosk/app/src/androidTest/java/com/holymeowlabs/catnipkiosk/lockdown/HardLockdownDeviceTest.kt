package com.holymeowlabs.catnipkiosk.lockdown

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.MainActivity
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Needs a tablet AVD with no accounts (device owner can't be set otherwise), e.g. a fresh
 * `Kiosk_Tablet_NoAccount`. Removal is the recovery path, so it is tested end to end.
 */
@RunWith(AndroidJUnit4::class)
class HardLockdownDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val am = context.getSystemService(ActivityManager::class.java)
    private lateinit var scenario: ActivityScenario<MainActivity>

    private fun shell(command: String): String {
        val fd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).use { String(it.readBytes()) }
    }

    @Before
    fun setUp() = runBlocking {
        val repo = SettingsRepository.get(context)
        repo.clearAll()
        repo.saveSettings(KioskSettings(startUrl = "https://example.com/"))
        repo.saveSecurity(PinHasher(iterations = 1_000).create("1234"))
        val out = shell("dpm set-device-owner ${context.packageName}/.lockdown.KioskDeviceAdminReceiver")
        check(dpm.isDeviceOwnerApp(context.packageName)) { "provisioning failed: $out" }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        delay(1_000)
    }

    @After
    fun tearDown() = runBlocking {
        // Pressing Home may replace the original instance, so remove through a fresh one if needed.
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            ActivityScenario.launch(MainActivity::class.java).use { fresh ->
                fresh.onActivity { HardLockdown(DpmOps(it)).remove() }
            }
        }
        scenario.close()
        SettingsRepository.get(context).clearAll()
    }

    @Test
    fun provisionedDeviceIsLockedToTheKiosk() {
        assertThat(LockdownController(context).tier()).isEqualTo(LockdownTier.HARD)
        assertThat(am.lockTaskModeState).isEqualTo(ActivityManager.LOCK_TASK_MODE_LOCKED)
        device.pressHome()
        assertThat(device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 5_000)).isTrue()
    }

    @Test
    fun afterExitingReturningHomeLocksTheKioskAgain() = runBlocking {
        scenario.onActivity { HardLockdown(DpmOps(it)).suspendForSession() }
        assertThat(am.lockTaskModeState).isEqualTo(ActivityManager.LOCK_TASK_MODE_NONE)
        device.pressHome()
        assertThat(device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 5_000)).isTrue()
        delay(1_000)
        assertThat(am.lockTaskModeState).isEqualTo(ActivityManager.LOCK_TASK_MODE_LOCKED)
    }

    @Test
    fun removalReleasesEverythingSoTheAppCanBeUninstalled() = runBlocking<Unit> {
        scenario.onActivity { HardLockdown(DpmOps(it)).remove() }
        delay(500)
        assertThat(LockdownController(context).tier()).isEqualTo(LockdownTier.SOFT)
        assertThat(am.lockTaskModeState).isEqualTo(ActivityManager.LOCK_TASK_MODE_NONE)
        assertThat(dpm.isDeviceOwnerApp(context.packageName)).isFalse()
        assertThat(dpm.isAdminActive(KioskDeviceAdminReceiver.component(context))).isFalse()
        // Android leaves CatnipKiosk holding the Home role after the persistent preference is cleared;
        // the confirmation text says so. Uninstalling restores the previous launcher.
    }
}
