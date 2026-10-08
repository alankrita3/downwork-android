package com.raviga.downwork.ui.quote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.data.api.ApiException
import com.raviga.downwork.data.api.AwsConnectInfo
import com.raviga.downwork.data.api.AwsTarget
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DestructiveButton
import com.raviga.downwork.ui.components.DwTextField
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.InlineAction
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.KeyValueRow
import com.raviga.downwork.ui.components.NumberedSteps
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.SecondaryButton
import com.raviga.downwork.ui.components.SectionHeading
import com.raviga.downwork.ui.openLink
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import com.raviga.downwork.ui.userLine
import com.raviga.downwork.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectAwsViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val accountId: String = "",
        val target: AwsTarget? = null,
        val info: AwsConnectInfo? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val disconnected: Boolean = false,
    ) {
        val saved get() = target != null && target.accountId == accountId.filter { it.isDigit() }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        val aws = container.session.me.value?.deliveryTargets?.aws
        _state.update { it.copy(accountId = aws?.accountId.orEmpty(), target = aws) }
        if (aws != null) loadInfo()
    }

    fun setAccountId(v: String) = _state.update { it.copy(accountId = v.filter { c -> c.isDigit() }.take(12), error = null, notice = null) }

    fun save() {
        val id = _state.value.accountId
        if (id.length != 12) { _state.update { it.copy(error = "An AWS account ID is 12 digits.") }; return }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching { container.session.setDeliveryTargets(null, id) }
                .onSuccess { t -> _state.update { it.copy(target = t.aws) }; loadInfo() }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    private fun loadInfo() {
        viewModelScope.launch {
            runCatching { container.session.awsConnect() }
                .onSuccess { info -> _state.update { it.copy(info = info) } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
        }
    }

    fun verify() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching { container.session.awsVerify() }
                .onSuccess { t -> _state.update { it.copy(target = t, notice = if (t.verified) "Connected. We can deploy to this account." else t.lastError) } }
                .onFailure { e ->
                    val reason = (e as? ApiException)?.takeIf { it.code == ApiException.AWS_ROLE_NOT_READY }?.detailString("reason")
                    _state.update { it.copy(error = if (reason != null) "AWS hasn't finished creating the role yet ($reason). Give it a minute and check again." else e.userLine()) }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { container.session.clearDeliveryTarget(aws = true) }
                .onSuccess { _state.update { it.copy(disconnected = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.userLine()) } }
            _state.update { it.copy(busy = false) }
        }
    }
}

@Composable
fun ConnectAwsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: ConnectAwsViewModel = viewModel { ConnectAwsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val config by container.session.config.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(state.disconnected) { if (state.disconnected) nav.popBackStack() }

    ScreenScaffold(
        topBar = { DwTopBar(title = "Connect AWS", onBack = { nav.popBackStack() }) },
        bottomBar = {
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                InlineNotice(state.notice, Modifier.padding(bottom = 8.dp), color = if (state.target?.verified == true) Ink.moss else Ink.graphite)
                if (!state.saved) {
                    PrimaryButton("Save account ID", loading = state.busy, enabled = state.accountId.length == 12, onClick = { vm.save() })
                } else {
                    PrimaryButton("Open AWS to create the role", enabled = state.info != null && !state.busy, onClick = { state.info?.let { openLink(context, it.quickCreateUrl) } })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Check connection", enabled = !state.busy, onClick = { vm.verify() })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "We create one role in your AWS account, named ${config.delivery.awsRoleName}, that lets the DownWork team deploy your project there. It trusts only DownWork's account and only with the external ID below. Delete the stack any time to revoke it.",
                style = DwType.body, color = Ink.ink,
            )
            Spacer(Modifier.height(24.dp))
            Text("AWS account ID", style = DwType.secondary, color = Ink.graphite)
            Spacer(Modifier.height(6.dp))
            DwTextField(
                value = state.accountId,
                onValueChange = { vm.setAccountId(it) },
                placeholder = "123456789012",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.busy,
                trailing = if (state.saved) ({ InlineAction("Change", onClick = { vm.setAccountId("") }) }) else null,
            )
            Spacer(Modifier.height(6.dp))
            Text("Find it in the AWS console under your account name, top right.", style = DwType.caption, color = Ink.graphite)

            val target = state.target
            if (state.saved && target != null) {
                Spacer(Modifier.height(Dw.sectionGap))
                SectionHeading("The role")
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Role name", target.roleName ?: config.delivery.awsRoleName)
                KeyValueRow("Trusted account", state.info?.trustedAccountId?.ifBlank { null } ?: config.delivery.awsTrustedAccountId)
                KeyValueRow("External ID", target.externalId ?: "")
                InlineAction("Copy external ID", onClick = { clipboard.setText(AnnotatedString(target.externalId.orEmpty())) }, modifier = Modifier.padding(top = 4.dp))
                KeyValueRow(
                    "Status",
                    if (target.verified) "Connected ${Time.shortDate(target.verifiedAt)}" else "Not verified yet",
                    valueColor = if (target.verified) Ink.moss else Ink.amber,
                )

                Spacer(Modifier.height(Dw.sectionGap))
                SectionHeading("Steps")
                Spacer(Modifier.height(8.dp))
                NumberedSteps(
                    state.info?.instructions?.takeIf { it.isNotEmpty() } ?: listOf(
                        "Sign in to the AWS account you want the project deployed to.",
                        "Review the stack: it creates one IAM role.",
                        "Tick the IAM acknowledgement and choose Create stack.",
                        "Come back here and check the connection.",
                    ),
                )
                Spacer(Modifier.height(24.dp))
                DestructiveButton("Disconnect AWS", enabled = !state.busy, onClick = { vm.disconnect() })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
