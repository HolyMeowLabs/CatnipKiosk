package com.holymeowlabs.catnipkiosk.lockdown

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import com.holymeowlabs.catnipkiosk.isTv
import org.junit.Test
import org.junit.runner.RunWith

/** Run on the tablet AVD (Google TV usually has no Home role to request). */
@RunWith(AndroidJUnit4::class)
class HomeRoleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val lockdown = LockdownController(context)
    private val pkg = context.packageName

    private fun shell(command: String) {
        val fd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    @Before
    fun setUp() = runBlocking {
        val repo = SettingsRepository.get(context)
        repo.clearAll()
        repo.saveSettings(KioskSettings(startUrl = "https://example.com/"))
        repo.saveSecurity(PinHasher(iterations = 1_000).create("1234"))
    }

    @After
    fun tearDown() = runBlocking {
        shell("cmd role remove-role-holder android.app.role.HOME $pkg")
        SettingsRepository.get(context).clearAll()
    }

    @Test
    fun homeRoleCanBeRequestedOnTheTablet() {
        assertThat(lockdown.homeRoleRequestIntent()).isNotNull()
    }

    @Test
    fun asHomeAppPressingHomeReturnsToTheKiosk() {
        shell("cmd role add-role-holder android.app.role.HOME $pkg")
        // Google TV keeps the Home role for its own launcher; this scenario is tablet-only.
        assumeTrue("Home key ignores the Home role on TV", lockdown.isHomeApp() && !isTv(context))
        device.pressHome()
        assertThat(device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 10_000)).isTrue()
    }

    @Test
    fun withoutTheRoleItIsNotHome() {
        shell("cmd role remove-role-holder android.app.role.HOME $pkg")
        assertThat(lockdown.isHomeApp()).isFalse()
    }
}
