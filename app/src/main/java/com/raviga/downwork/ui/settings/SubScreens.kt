package com.raviga.downwork.ui.settings

import com.raviga.downwork.ui.components.SpotIllustration
import com.raviga.downwork.ui.components.Picture
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.push.Notifications
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DestructiveButton
import com.raviga.downwork.ui.components.DwRow
import com.raviga.downwork.ui.components.DwTextField
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.Hairline
import com.raviga.downwork.ui.components.InlineAction
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.SecondaryButton
import com.raviga.downwork.ui.components.SectionHeading
import com.raviga.downwork.ui.emailLink
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.openLink
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink

@Composable
internal fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Ink.surface, checkedTrackColor = Ink.teal, checkedBorderColor = Ink.teal,
    uncheckedThumbColor = Ink.surface, uncheckedTrackColor = Ink.ruleStrong, uncheckedBorderColor = Ink.ruleStrong,
)

/** The bundled fonts and their licence (SIL Open Font License 1.1, shipped in assets/licenses). */
@Composable
fun FontsScreen(nav: NavController) {
    val context = LocalContext.current
    val licence = remember { runCatching { context.assets.open("licenses/OFL.txt").bufferedReader().use { it.readText() } }.getOrDefault("") }
    ScreenScaffold(topBar = { DwTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.height(8.dp))
            Text("Fonts", style = DwType.title, color = Ink.ink)
            Spacer(Modifier.height(12.dp))
            Text(
                "DownWork uses Bricolage Grotesque and Figtree, both under the SIL Open Font License 1.1.",
                style = DwType.body, color = Ink.graphite,
            )
            Spacer(Modifier.height(20.dp))
            Text(licence, style = DwType.caption, color = Ink.graphite)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun DeliveryTargetsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val aws = state.me?.deliveryTargets?.aws

    ScreenScaffold(
        topBar = { DwTopBar(title = "Delivery targets", onBack = { nav.popBackStack() }) },
        bottomBar = {
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                InlineNotice(state.notice, Modifier.padding(bottom = 8.dp), color = Ink.moss)
                PrimaryButton("Save", loading = state.busy, onClick = { vm.saveGithub() })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = Dw.gutter)) {
                Spacer(Modifier.height(8.dp))
                Text("Where finished projects go.", style = DwType.body, color = Ink.graphite)
                Spacer(Modifier.height(24.dp))
                Text("GitHub username", style = DwType.secondary, color = Ink.graphite)
                Spacer(Modifier.height(6.dp))
                DwTextField(
                    value = state.github,
                    onValueChange = { vm.setGithub(it) },
                    placeholder = "octocat",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                )
                Spacer(Modifier.height(6.dp))
                Text("We transfer each finished repository to this account. GitHub emails you to accept it.", style = DwType.caption, color = Ink.graphite)
                Spacer(Modifier.height(24.dp))
            }
            Hairline(Modifier.padding(horizontal = Dw.gutter))
            DwRow(
                title = "AWS account",
                subtitle = when {
                    aws == null -> "Optional. Connect it and we deploy there too."
                    aws.verified -> "Connected, ${aws.accountId}"
                    else -> "${aws.accountId}, not verified yet"
                },
                subtitleColor = if (aws?.verified == true) Ink.moss else Ink.graphite,
                onClick = { nav.navigate(Routes.CONNECT_AWS) },
            )
        }
    }
}

@Composable
fun NotificationsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Notifications.canPost(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    ScreenScaffold(topBar = { DwTopBar(title = "Notifications", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.height(8.dp))
            Text("We send one when the team replies, approves, or delivers, and when a brief or quote is ready. Nothing else.", style = DwType.body, color = Ink.ink)
            Spacer(Modifier.height(24.dp))
            Text(
                when {
                    !vm.pushConfigured -> "Push is not set up on this build yet."
                    granted -> "Notifications are on."
                    else -> "Notifications are off."
                },
                style = DwType.body, color = if (granted && vm.pushConfigured) Ink.moss else Ink.amber,
            )
            Spacer(Modifier.height(24.dp))
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PrimaryButton("Allow notifications", onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) })
                Spacer(Modifier.height(8.dp))
            }
            SecondaryButton("Open system settings", onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                runCatching { context.startActivity(intent) }
            })
        }
    }
}

@Composable
fun PrivacyScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val legal = state.config.legal
    val aiOn = state.me?.consent?.aiGranted == true
    var confirmDelete by remember { mutableStateOf(false) }
    val saveExport = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) vm.saveExport(uri) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) nav.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } }
    }

    ScreenScaffold(topBar = { DwTopBar(onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = Dw.gutter)) {
                SpotIllustration(Picture.Privacy, size = 180.dp, modifier = Modifier.offset(x = (-24).dp))
                Spacer(Modifier.height(20.dp))
                Text("Privacy and data", style = DwType.title, color = Ink.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Dw.gutter, vertical = Dw.rowPadding), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI processing", style = DwType.bodyMedium, color = Ink.ink)
                    Text(
                        if (aiOn) "On. Only the text of your notes and brief is sent, to write and price it: ${state.config.ai.providers.joinToString("; ").ifBlank { "our AI provider" }}." else "Off. DownWork cannot write briefs until this is on.",
                        style = DwType.secondary, color = Ink.graphite,
                    )
                }
                Switch(checked = aiOn, enabled = !state.busy, onCheckedChange = { vm.setAiProcessing(it) }, colors = switchColors())
            }
            Hairline(Modifier.padding(horizontal = Dw.gutter))
            Column(Modifier.padding(horizontal = Dw.gutter)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recordings and files never leave this phone, and drafts are kept only here. A submitted brief is held by DownWork " +
                        "until you accept the delivery (or the project is cancelled), then deleted.",
                    style = DwType.caption, color = Ink.graphite,
                )
                Spacer(Modifier.height(24.dp))
            }
            DwRow(
                title = "Download my data",
                subtitle = state.exportMessage ?: "A zip of your submitted projects, the drafts on this phone, and your credit history",
                onClick = if (!state.busy) ({ vm.export() }) else null,
                chevron = false,
            )
            if (state.exportMessage != null) ProgressRule(Modifier.padding(horizontal = Dw.gutter))
            state.exportUrl?.let {
                Column(Modifier.padding(horizontal = Dw.gutter)) {
                    InlineNotice(state.notice, color = Ink.moss)
                    InlineAction("Save the file", enabled = !state.busy, onClick = {
                        saveExport.launch("DownWork data ${java.time.LocalDate.now()}.zip")
                    })
                }
            }
            if (state.exportUrl == null && state.notice == "Saved.") {
                InlineNotice(state.notice, Modifier.padding(horizontal = Dw.gutter), color = Ink.moss)
            }
            DwRow(
                title = "Delete my data",
                subtitle = "Drafts on this phone, submitted projects and this phone's link. Credits history is kept anonymised, as the law requires.",
                titleColor = Ink.brick,
                onClick = if (!state.busy) ({ confirmDelete = true }) else null,
                chevron = false,
            )
            Column(Modifier.padding(horizontal = Dw.gutter)) {
                InlineNotice(state.error)
                Spacer(Modifier.height(Dw.sectionGap))
                SectionHeading("Grievance officer")
                Spacer(Modifier.height(12.dp))
                Text(legal.grievance.officerName.ifBlank { "To be appointed" }, style = DwType.body, color = Ink.ink)
                if (legal.grievance.email.isNotBlank()) {
                    Text(
                        legal.grievance.email, style = DwType.body, color = Ink.teal,
                        modifier = Modifier.clickable { emailLink(context, legal.grievance.email, "DownWork grievance") }.padding(vertical = 4.dp),
                    )
                }
                if (legal.grievance.address.isNotBlank()) Text(legal.grievance.address, style = DwType.secondary, color = Ink.graphite)
                Spacer(Modifier.height(8.dp))
                Text("We respond within ${legal.grievance.responseDays} days, as the Digital Personal Data Protection Act requires. ${legal.companyName} is the data fiduciary.", style = DwType.caption, color = Ink.graphite)
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Ink.surface,
            title = { Text("Delete everything?", style = DwType.heading, color = Ink.ink) },
            text = {
                Text(
                    "Your drafts on this phone are deleted now. Submitted projects are deleted after ${state.config.retention.deletionGraceDays} days; recovering with your key before then cancels it. Active projects must be finished or cancelled first.",
                    style = DwType.body, color = Ink.graphite,
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteEverything() }) { Text("Delete", style = DwType.button, color = Ink.brick) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = DwType.button, color = Ink.ink) } },
        )
    }
}

@Composable
fun RecoveryScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var otherKey by remember { mutableStateOf("") }
    var confirmMove by remember { mutableStateOf(false) }

    LaunchedEffect(state.recoveryKey) { if (state.recoveryKey != null) vm.markRecoveryKeyShown() }
    LaunchedEffect(state.recovered) {
        if (state.recovered) nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
    }

    ScreenScaffold(topBar = { DwTopBar(title = "Move to another phone", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.height(8.dp))
            Text("There is no account. This key is the only way to get your submitted projects and credits back after a reinstall or on a new phone. Keep it somewhere safe.", style = DwType.body, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            Text("Drafts stay on this phone: they are never sent anywhere, so a key can't move them.", style = DwType.secondary, color = Ink.graphite)
            Spacer(Modifier.height(24.dp))
            SectionHeading("This phone's key")
            Spacer(Modifier.height(12.dp))
            val key = state.recoveryKey
            if (key != null) {
                Text(
                    key,
                    style = DwType.heading,
                    color = Ink.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ink.surface, RoundedCornerShape(Dw.fieldRadius))
                        .border(Dw.hairline, Ink.rule, RoundedCornerShape(Dw.fieldRadius))
                        .padding(Dw.fieldPadding),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    InlineAction("Copy", onClick = { clipboard.setText(AnnotatedString(key)) })
                    InlineAction("Issue a new key", enabled = !state.busy, onClick = { vm.issueRecoveryKey() })
                }
            } else {
                Text("No key on this phone yet.", style = DwType.body, color = Ink.graphite)
                Spacer(Modifier.height(8.dp))
                InlineAction("Issue a key", enabled = !state.busy, onClick = { vm.issueRecoveryKey() })
            }
            InlineNotice(state.notice, color = Ink.moss)
            Spacer(Modifier.height(Dw.sectionGap))
            SectionHeading("Bring projects here")
            Spacer(Modifier.height(12.dp))
            Text("Enter the key from your other phone. This phone then shows that phone's submitted projects and credits instead of its own. Drafts on this phone stay here.", style = DwType.body, color = Ink.graphite)
            Spacer(Modifier.height(12.dp))
            DwTextField(value = otherKey, onValueChange = { otherKey = it }, placeholder = "dwrk-…", singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false))
            InlineNotice(state.error)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Move projects here", enabled = otherKey.isNotBlank() && !state.busy, loading = state.busy, onClick = { confirmMove = true })
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmMove) {
        AlertDialog(
            onDismissRequest = { confirmMove = false },
            containerColor = Ink.surface,
            title = { Text("Switch to the other phone's projects?", style = DwType.heading, color = Ink.ink) },
            text = { Text("Anything created on this phone stays reachable only with this phone's key.", style = DwType.body, color = Ink.graphite) },
            confirmButton = { TextButton(onClick = { confirmMove = false; vm.recoverWith(otherKey) }) { Text("Switch", style = DwType.button, color = Ink.ink) } },
            dismissButton = { TextButton(onClick = { confirmMove = false }) { Text("Keep this phone's", style = DwType.button, color = Ink.ink) } },
        )
    }
}
