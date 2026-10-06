package com.holymeowlabs.catnipkiosk.settingsui

import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.security.PinRules
import com.holymeowlabs.catnipkiosk.settings.DomainInput
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import com.holymeowlabs.catnipkiosk.setup.Scheme
import com.holymeowlabs.catnipkiosk.setup.StartUrlInput
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ChangePinResult { Ok, WrongOldPin, Mismatch, InvalidNewPin }

/** Edits apply immediately and are persisted in order; the newest state is always the last write. */
class SettingsViewModel(
    initial: KioskSettings,
    /** The stored security state, read when changing the PIN. */
    private val security: () -> SecurityState?,
    private val hasher: PinHasher,
    private val saveSettings: suspend (KioskSettings) -> Unit,
    private val saveSecurity: suspend (SecurityState) -> Unit,
    scope: CoroutineScope,
    /** PIN hashing is deliberately slow; it runs here, off the main thread. */
    private val work: CoroutineDispatcher,
) {
    private val _settings = MutableStateFlow(initial)
    val settings: StateFlow<KioskSettings> = _settings

    init {
        // One sequential writer: StateFlow conflates bursts, and the latest value is written last.
        // Subscribed immediately so edits made before the scope's first dispatch aren't skipped.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var written = initial
            _settings.collect {
                if (it != written) {
                    saveSettings(it)
                    written = it
                }
            }
        }
    }

    fun update(transform: (KioskSettings) -> KioskSettings) = _settings.update(transform)

    /** False if [input] is not a usable host; adding one already present is a no-op. */
    fun addExtraDomain(input: String): Boolean {
        val host = DomainInput.normalize(input) ?: return false
        update { if (host in it.extraDomains) it else it.copy(extraDomains = it.extraDomains + host) }
        return true
    }

    fun removeExtraDomain(host: String) = update { it.copy(extraDomains = it.extraDomains - host) }

    fun setStartUrl(input: String, scheme: Scheme = Scheme.HTTPS): Boolean {
        val url = (StartUrlInput.normalize(input, scheme) as? StartUrlInput.Result.Ok)?.url ?: return false
        update { it.copy(startUrl = url) }
        return true
    }

    fun setZoom(percent: Int) = update {
        it.copy(zoomPercent = ((percent / ZOOM_STEP.toFloat()).roundToInt() * ZOOM_STEP).coerceIn(ZOOM_MIN, ZOOM_MAX))
    }

    suspend fun changePin(old: String, new: String, confirm: String): ChangePinResult {
        val current = security() ?: return ChangePinResult.WrongOldPin
        if (!PinRules.isValid(new)) return ChangePinResult.InvalidNewPin
        if (new != confirm) return ChangePinResult.Mismatch
        val replacement = withContext(work) {
            if (hasher.matches(old, current)) hasher.create(new) else null
        } ?: return ChangePinResult.WrongOldPin
        saveSecurity(replacement)
        return ChangePinResult.Ok
    }

    companion object {
        const val ZOOM_MIN = 50
        const val ZOOM_MAX = 200
        const val ZOOM_STEP = 10
        val EVERY_MINUTES_CHOICES = listOf(15, 30, 60, 120, 240)
    }
}
