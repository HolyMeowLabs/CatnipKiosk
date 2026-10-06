package com.holymeowlabs.catnipkiosk.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.holymeowlabs.catnipkiosk.R
import com.holymeowlabs.catnipkiosk.ui.dpadExitsTextField
import com.holymeowlabs.catnipkiosk.settings.NavMode

/** First-run setup: start page → navigation → PIN. Works with touch or D-pad + on-screen keyboard. */
@Composable
fun SetupWizard(vm: SetupViewModel, state: SetupUi) {
    BackHandler(enabled = state.step != SetupStep.StartUrl, onBack = vm::back)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
            when (state.step) {
                SetupStep.StartUrl -> StartUrlStep(vm, state)
                SetupStep.Navigation -> NavigationStep(vm, state)
                SetupStep.Pin -> PinStep(vm, state)
            }
        }
    }
}

@Composable
private fun StepHeader(number: Int, title: Int) {
    Text(stringResource(R.string.setup_step, number, stringResource(title)), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StartUrlStep(vm: SetupViewModel, state: SetupUi) {
    StepHeader(1, R.string.setup_step_start_page)
    // TV remotes have nothing focused otherwise; the address is the first thing to fill in.
    val urlFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { urlFocus.requestFocus() }
    SchemeChoice(state.scheme, vm::setScheme)
    OutlinedTextField(
        value = state.url,
        onValueChange = vm::setUrl,
        label = { Text(stringResource(R.string.setup_url_label)) },
        singleLine = true,
        isError = state.urlError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.dpadExitsTextField().fillMaxWidth().focusRequester(urlFocus).testTag("setup_url"),
    )
    when {
        state.allowedDomain != null -> Text(stringResource(R.string.setup_allowed_domain, state.allowedDomain))
        state.urlError -> Text(stringResource(R.string.setup_url_error), color = MaterialTheme.colorScheme.error)
    }
    if (state.insecure) HttpWarning()
    Button(onClick = vm::next, modifier = Modifier.testTag("setup_next")) { Text(stringResource(R.string.setup_next)) }
}

@Composable
private fun NavigationStep(vm: SetupViewModel, state: SetupUi) {
    StepHeader(2, R.string.setup_step_navigation)
    NavOption(stringResource(R.string.setup_nav_page_only), state.navMode == NavMode.PAGE_ONLY) {
        vm.setNavMode(NavMode.PAGE_ONLY)
    }
    NavOption(stringResource(R.string.setup_nav_domain), state.navMode == NavMode.DOMAIN) {
        vm.setNavMode(NavMode.DOMAIN)
    }
    if (state.navMode == NavMode.DOMAIN) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = state.includeSubdomains, onCheckedChange = vm::setIncludeSubdomains)
            Text(stringResource(R.string.setup_include_subdomains))
        }
    }
    OutlinedTextField(
        value = state.extraDomains,
        onValueChange = vm::setExtraDomains,
        label = { Text(stringResource(R.string.setup_extra_domains_label)) },
        supportingText = { Text(stringResource(R.string.setup_extra_domains_help)) },
        isError = state.extraDomainsError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.dpadExitsTextField().fillMaxWidth(),
    )
    if (state.extraDomainsError) {
        Text(stringResource(R.string.setup_extra_domains_error), color = MaterialTheme.colorScheme.error)
    }
    BackNext(vm, next = R.string.setup_next)
}

@Composable
private fun NavOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun PinStep(vm: SetupViewModel, state: SetupUi) {
    StepHeader(3, R.string.setup_step_pin)
    Text(stringResource(R.string.setup_pin_help))
    PinField(state.pin, vm::setPin, R.string.setup_pin_label, "setup_pin")
    PinField(state.pinConfirm, vm::setPinConfirm, R.string.setup_pin_confirm_label, "setup_pin_confirm")
    when (state.pinError) {
        PinEntryError.Invalid -> Text(stringResource(R.string.setup_pin_invalid), color = MaterialTheme.colorScheme.error)
        PinEntryError.Mismatch -> Text(stringResource(R.string.setup_pin_mismatch), color = MaterialTheme.colorScheme.error)
        null -> Unit
    }
    BackNext(vm, next = R.string.setup_finish, enabled = !state.saving)
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: Int, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(8)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.dpadExitsTextField().fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun BackNext(vm: SetupViewModel, next: Int, enabled: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::back) { Text(stringResource(R.string.setup_back)) }
        Button(onClick = vm::next, enabled = enabled, modifier = Modifier.testTag("setup_next")) { Text(stringResource(next)) }
    }
}
