package com.holymeowlabs.catnipkiosk.setup

import com.holymeowlabs.catnipkiosk.security.PinHasher
import com.holymeowlabs.catnipkiosk.security.PinRules
import com.holymeowlabs.catnipkiosk.settings.DomainInput
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.NavMode
import com.holymeowlabs.catnipkiosk.settings.SecurityState
import java.net.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SetupStep { StartUrl, Navigation, Pin }

enum class PinEntryError { Invalid, Mismatch }

data class SetupUi(
    val step: SetupStep = SetupStep.StartUrl,
    val url: String = "",
    val urlError: Boolean = false,
    /** Host the start URL allows, shown under the field once the URL is valid. */
    val allowedDomain: String? = null,
    val navMode: NavMode = NavMode.DOMAIN,
    val includeSubdomains: Boolean = true,
    val extraDomains: String = "",
    val extraDomainsError: Boolean = false,
    val pin: String = "",
    val pinConfirm: String = "",
    val pinError: PinEntryError? = null,
    val saving: Boolean = false,
)

/** First-run wizard: start page → navigation → PIN. Nothing is saved until the PIN step passes. */
class SetupViewModel(
    private val isTv: Boolean,
    private val hasher: PinHasher,
    private val saveSettings: suspend (KioskSettings) -> Unit,
    private val saveSecurity: suspend (SecurityState) -> Unit,
    private val scope: CoroutineScope,
    /** PIN hashing is deliberately slow; it runs here, off the main thread. */
    private val work: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(SetupUi())
    val state: StateFlow<SetupUi> = _state

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done

    private var startUrl: String? = null

    fun setUrl(text: String) {
        startUrl = (StartUrlInput.normalize(text) as? StartUrlInput.Result.Ok)?.url
        _state.update { it.copy(url = text, urlError = false, allowedDomain = startUrl?.let { u -> URI(u).host }) }
    }

    fun setNavMode(mode: NavMode) = _state.update { it.copy(navMode = mode) }

    fun setIncludeSubdomains(include: Boolean) = _state.update { it.copy(includeSubdomains = include) }

    fun setExtraDomains(text: String) = _state.update { it.copy(extraDomains = text, extraDomainsError = false) }

    fun setPin(text: String) = _state.update { it.copy(pin = text, pinError = null) }

    fun setPinConfirm(text: String) = _state.update { it.copy(pinConfirm = text, pinError = null) }

    fun next() {
        when (_state.value.step) {
            SetupStep.StartUrl ->
                if (startUrl == null) _state.update { it.copy(urlError = true) }
                else _state.update { it.copy(step = SetupStep.Navigation) }
            SetupStep.Navigation ->
                if (parseExtraDomains() == null) _state.update { it.copy(extraDomainsError = true) }
                else _state.update { it.copy(step = SetupStep.Pin) }
            SetupStep.Pin -> finish()
        }
    }

    fun back() = _state.update {
        when (it.step) {
            SetupStep.StartUrl -> it
            SetupStep.Navigation -> it.copy(step = SetupStep.StartUrl)
            SetupStep.Pin -> it.copy(step = SetupStep.Navigation, pin = "", pinConfirm = "", pinError = null)
        }
    }

    fun finish() {
        val ui = _state.value
        val url = startUrl
        val extras = parseExtraDomains()
        if (ui.step != SetupStep.Pin || ui.saving || url == null || extras == null) return
        if (!PinRules.isValid(ui.pin)) return _state.update { it.copy(pinError = PinEntryError.Invalid) }
        if (ui.pin != ui.pinConfirm) return _state.update { it.copy(pinError = PinEntryError.Mismatch) }

        _state.update { it.copy(saving = true) }
        val settings = KioskSettings.defaults(url, isTv).copy(
            navMode = ui.navMode,
            includeSubdomains = ui.includeSubdomains,
            extraDomains = extras,
        )
        scope.launch {
            val security = withContext(work) { hasher.create(ui.pin) }
            saveSettings(settings)
            saveSecurity(security)
            _done.tryEmit(Unit)
        }
    }

    /** Comma- or line-separated hosts, normalised; null if any entry is unusable. */
    private fun parseExtraDomains(): List<String>? =
        _state.value.extraDomains.split(',', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { DomainInput.normalize(it) ?: return null }
            .distinct()
}
