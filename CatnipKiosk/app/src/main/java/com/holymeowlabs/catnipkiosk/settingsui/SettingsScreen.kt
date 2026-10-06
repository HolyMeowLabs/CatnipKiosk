package com.holymeowlabs.catnipkiosk.settingsui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.holymeowlabs.catnipkiosk.R
import com.holymeowlabs.catnipkiosk.ui.dpadExitsTextField
import com.holymeowlabs.catnipkiosk.lockdown.HomeOption
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.NavMode
import com.holymeowlabs.catnipkiosk.settings.ScheduledReload

enum class SettingsSection(val title: Int) {
    StartPage(R.string.settings_section_start_page),
    Navigation(R.string.setup_step_navigation),
    Display(R.string.settings_section_display),
    Reliability(R.string.settings_section_reliability),
    Startup(R.string.settings_section_startup),
    Security(R.string.settings_section_security),
}

/** What Settings shows about soft lockdown; refreshed whenever the screen resumes. */
data class StartupStatus(
    val home: HomeOption = HomeOption.UNAVAILABLE,
    val isHardLockdown: Boolean = false,
)

/** TV: a focusable side rail with one section at a time. Tablet: every section in one scroll. */
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    isTv: Boolean,
    onBack: () -> Unit,
    onExitApp: () -> Unit,
    onReloadNow: () -> Unit,
    startup: StartupStatus = StartupStatus(),
    onSetHome: () -> Unit = {},
    onShowHardSteps: () -> Unit = {},
    onRemoveHard: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val settings by vm.settings.collectAsState()
    var changingPin by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onExitApp) { Text(stringResource(R.string.settings_exit_app)) }
            Button(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) { Text(stringResource(R.string.settings_back_to_kiosk)) }
        }
        val sectionContent: @Composable ColumnScope.(SettingsSection) -> Unit = { section ->
            Section(section, vm, settings, onReloadNow, startup, onSetHome, onShowHardSteps, { confirmingRemove = true }) {
                changingPin = true
            }
        }
        if (isTv) {
            var selected by rememberSaveable { mutableStateOf(SettingsSection.StartPage) }
            Row(Modifier.fillMaxSize().padding(top = 16.dp)) {
                Column(Modifier.width(240.dp).fillMaxHeight()) {
                    SettingsSection.entries.forEach { section ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(section.title)) },
                            selected = section == selected,
                            onClick = { selected = section },
                            modifier = Modifier.testTag("rail_${section.name}"),
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { sectionContent(selected) }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp).widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsSection.entries.forEach { section ->
                    Text(stringResource(section.title), style = MaterialTheme.typography.titleLarge)
                    sectionContent(section)
                    HorizontalDivider()
                }
            }
        }
    }
    if (changingPin) ChangePinDialog(change = vm::changePin, onDone = { changingPin = false })
    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            text = { Text(stringResource(R.string.settings_hard_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmingRemove = false; onRemoveHard() }) {
                    Text(stringResource(R.string.settings_hard_remove_yes))
                }
            },
            dismissButton = { TextButton(onClick = { confirmingRemove = false }) { Text(stringResource(R.string.pin_cancel)) } },
        )
    }
}

@Composable
private fun ColumnScope.Section(
    section: SettingsSection,
    vm: SettingsViewModel,
    s: KioskSettings,
    onReloadNow: () -> Unit,
    startup: StartupStatus,
    onSetHome: () -> Unit,
    onShowHardSteps: () -> Unit,
    onRemoveHard: () -> Unit,
    onChangePin: () -> Unit,
) {
    when (section) {
        SettingsSection.StartPage -> {
            var url by remember(s.startUrl) { mutableStateOf(s.startUrl) }
            var invalid by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; invalid = false },
                label = { Text(stringResource(R.string.setup_url_label)) },
                singleLine = true,
                isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.dpadExitsTextField().fillMaxWidth(),
            )
            if (invalid) Text(stringResource(R.string.setup_url_error), color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { invalid = !vm.setStartUrl(url) }) { Text(stringResource(R.string.settings_save)) }
                OutlinedButton(onClick = onReloadNow) { Text(stringResource(R.string.settings_reload_now)) }
            }
        }
        SettingsSection.Navigation -> {
            Choice(stringResource(R.string.setup_nav_page_only), s.navMode == NavMode.PAGE_ONLY) {
                vm.update { it.copy(navMode = NavMode.PAGE_ONLY) }
            }
            Choice(stringResource(R.string.setup_nav_domain), s.navMode == NavMode.DOMAIN) {
                vm.update { it.copy(navMode = NavMode.DOMAIN) }
            }
            Toggle(R.string.setup_include_subdomains, s.includeSubdomains, "include_subdomains") { on ->
                vm.update { it.copy(includeSubdomains = on) }
            }
            Toggle(R.string.settings_show_blocked_message, s.showBlockedMessage) { on ->
                vm.update { it.copy(showBlockedMessage = on) }
            }
            Text(stringResource(R.string.setup_extra_domains_label))
            s.extraDomains.forEach { host ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(host, Modifier.weight(1f))
                    TextButton(onClick = { vm.removeExtraDomain(host) }) { Text(stringResource(R.string.settings_remove)) }
                }
            }
            var newDomain by remember { mutableStateOf("") }
            var invalid by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newDomain,
                    onValueChange = { newDomain = it; invalid = false },
                    label = { Text(stringResource(R.string.settings_add_domain)) },
                    singleLine = true,
                    isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.dpadExitsTextField().weight(1f),
                )
                Button(onClick = {
                    if (vm.addExtraDomain(newDomain)) newDomain = "" else invalid = true
                }) { Text(stringResource(R.string.settings_add)) }
            }
            if (invalid) Text(stringResource(R.string.setup_extra_domains_error), color = MaterialTheme.colorScheme.error)
        }
        SettingsSection.Display -> {
            Text(stringResource(R.string.settings_zoom))
            Stepper(
                value = stringResource(R.string.settings_percent, s.zoomPercent),
                onMinus = { vm.setZoom(s.zoomPercent - SettingsViewModel.ZOOM_STEP) },
                onPlus = { vm.setZoom(s.zoomPercent + SettingsViewModel.ZOOM_STEP) },
            )
            Toggle(R.string.settings_keep_screen_on, s.keepScreenOn) { on -> vm.update { it.copy(keepScreenOn = on) } }
            Toggle(R.string.settings_cursor, s.cursorEnabled) { on -> vm.update { it.copy(cursorEnabled = on) } }
        }
        SettingsSection.Reliability -> {
            Toggle(R.string.settings_reload_on_failure, s.reloadOnFailure) { on -> vm.update { it.copy(reloadOnFailure = on) } }
            Text(stringResource(R.string.settings_scheduled_reload))
            val schedule = s.scheduledReload
            Choice(stringResource(R.string.settings_reload_off), schedule == ScheduledReload.Off) {
                vm.update { it.copy(scheduledReload = ScheduledReload.Off) }
            }
            Choice(stringResource(R.string.settings_reload_every), schedule is ScheduledReload.EveryMinutes) {
                vm.update { it.copy(scheduledReload = ScheduledReload.EveryMinutes(SettingsViewModel.EVERY_MINUTES_CHOICES[2])) }
            }
            if (schedule is ScheduledReload.EveryMinutes) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsViewModel.EVERY_MINUTES_CHOICES.forEach { minutes ->
                        FilterChip(
                            selected = schedule.minutes == minutes,
                            onClick = { vm.update { it.copy(scheduledReload = ScheduledReload.EveryMinutes(minutes)) } },
                            label = { Text(stringResource(R.string.settings_minutes, minutes)) },
                        )
                    }
                }
            }
            Choice(stringResource(R.string.settings_reload_daily), schedule is ScheduledReload.DailyAt) {
                vm.update { it.copy(scheduledReload = ScheduledReload.DailyAt(3, 0)) }
            }
            if (schedule is ScheduledReload.DailyAt) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Stepper(
                        value = "%02d".format(schedule.hour),
                        onMinus = { vm.update { it.copy(scheduledReload = schedule.copy(hour = (schedule.hour + 23) % 24)) } },
                        onPlus = { vm.update { it.copy(scheduledReload = schedule.copy(hour = (schedule.hour + 1) % 24)) } },
                    )
                    Text(":")
                    Stepper(
                        value = "%02d".format(schedule.minute),
                        onMinus = { vm.update { it.copy(scheduledReload = schedule.copy(minute = (schedule.minute + 55) % 60)) } },
                        onPlus = { vm.update { it.copy(scheduledReload = schedule.copy(minute = (schedule.minute + 5) % 60)) } },
                    )
                }
            }
        }
        SettingsSection.Startup -> {
            Toggle(R.string.settings_start_on_boot, s.startOnBoot) { on -> vm.update { it.copy(startOnBoot = on) } }
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            when (startup.home) {
                HomeOption.IS_HOME -> Text(stringResource(R.string.settings_is_home))
                HomeOption.CAN_REQUEST -> {
                    Button(onClick = onSetHome) { Text(stringResource(R.string.settings_set_home)) }
                    Text(stringResource(R.string.settings_not_home_limitation), color = muted)
                }
                HomeOption.UNAVAILABLE -> Text(stringResource(R.string.settings_home_unavailable), color = muted)
                HomeOption.TV_HOME_KEY_LEAVES -> Text(stringResource(R.string.settings_home_tv), color = muted)
            }
            HorizontalDivider()
            if (startup.isHardLockdown) {
                Text(stringResource(R.string.settings_hard_on))
                OutlinedButton(onClick = onRemoveHard) { Text(stringResource(R.string.settings_hard_remove)) }
            } else {
                Text(stringResource(R.string.settings_hard_off), color = muted)
                OutlinedButton(onClick = onShowHardSteps) { Text(stringResource(R.string.settings_hard_show_steps)) }
            }
        }
        SettingsSection.Security -> {
            Button(onClick = onChangePin) { Text(stringResource(R.string.settings_change_pin)) }
            Toggle(R.string.settings_pin_lockout, s.pinLockoutEnabled) { on -> vm.update { it.copy(pinLockoutEnabled = on) } }
        }
    }
}

@Composable
private fun Toggle(label: Int, checked: Boolean, tag: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(value, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}
