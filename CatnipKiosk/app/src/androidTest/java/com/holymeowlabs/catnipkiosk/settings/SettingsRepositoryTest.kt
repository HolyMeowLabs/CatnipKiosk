package com.holymeowlabs.catnipkiosk.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings = KioskSettings(
        startUrl = "https://example.com/lobby",
        extraDomains = listOf("login.example.org"),
        scheduledReload = ScheduledReload.EveryMinutes(30),
    )
    private val security = SecurityState("aGFzaA==", "c2FsdA==", 120_000, failedAttempts = 2)

    @Before
    fun clear() = runTest { SettingsRepository.get(context).clearAll() }

    @After
    fun tearDown() = runTest { SettingsRepository.get(context).clearAll() }

    @Test
    fun emptyStoreMeansNotConfigured() = runTest {
        val repo = SettingsRepository.get(context)
        assertThat(repo.settings.first()).isNull()
        assertThat(repo.security.first()).isNull()
    }

    @Test
    fun settingsAndSecurityAreStoredIndependently() = runTest {
        val repo = SettingsRepository.get(context)
        repo.saveSettings(settings)
        assertThat(repo.settings.first()).isEqualTo(settings)
        assertThat(repo.security.first()).isNull()

        repo.saveSecurity(security)
        assertThat(repo.security.first()).isEqualTo(security)
        assertThat(repo.settings.first()).isEqualTo(settings)
    }

    @Test
    fun valuesSurviveForAnotherReader() = runTest {
        SettingsRepository.get(context).saveSettings(settings)
        val reread = SettingsRepository.get(context.applicationContext).settings.first()
        assertThat(reread).isEqualTo(settings)
    }

    @Test
    fun clearAllReturnsToNotConfigured() = runTest {
        val repo = SettingsRepository.get(context)
        repo.saveSettings(settings)
        repo.saveSecurity(security)
        repo.clearAll()
        assertThat(repo.settings.first()).isNull()
        assertThat(repo.security.first()).isNull()
    }

    @Test
    fun corruptStoreFileReadsAsNotConfiguredInsteadOfCrashing() = runTest {
        val name = "kiosk-corrupt-test"
        val file = File(context.filesDir, "datastore/$name.preferences_pb")
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0x7f, 0x00, 0x13, 0x37, 0x42))
        try {
            val repo = SettingsRepository.forStoreName(context, name)
            assertThat(repo.settings.first()).isNull()
            repo.saveSettings(settings)
            assertThat(repo.settings.first()).isEqualTo(settings)
        } finally {
            file.delete()
        }
    }

    @Test
    fun savingSecurityDoesNotReEmitUnchangedSettings() = runTest {
        val repo = SettingsRepository.get(context)
        repo.saveSettings(settings)
        val emissions = mutableListOf<KioskSettings?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repo.settings.toList(emissions) }
        withContext(Dispatchers.Default) {
            repo.saveSecurity(security)
            repo.saveSecurity(security.copy(failedAttempts = 3))
            delay(200)
        }
        job.cancel()
        assertThat(emissions).containsExactly(settings)
    }

    @Test
    fun corruptStoredSettingsReadAsNotConfigured() = runTest {
        val repo = SettingsRepository.get(context)
        repo.writeRawSettingsForTest("{not json")
        assertThat(repo.settings.first()).isNull()
    }
}
