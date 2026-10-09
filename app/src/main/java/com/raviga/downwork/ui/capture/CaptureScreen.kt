package com.raviga.downwork.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raviga.downwork.data.screening.SensitiveScan
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.InkLine
import com.raviga.downwork.ui.components.InlineNotice
import com.raviga.downwork.ui.components.MicButton
import com.raviga.downwork.ui.components.PlainEditor
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.SecondaryButton
import com.raviga.downwork.ui.components.Segmented
import com.raviga.downwork.ui.components.TertiaryButton
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink

private const val HINT = "Tell us what it does, who it is for, and anything it must connect to. Ramble is fine."

@Composable
fun CaptureScreen(nav: NavController, projectId: String, mode: String, tab: String = DescribeWith.SPEAK) {
    val container = LocalAppContainer.current
    val vm: CaptureViewModel = viewModel(key = "capture_$projectId$mode") { CaptureViewModel(container, projectId, mode, tab) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val config by container.session.config.collectAsStateWithLifecycle()

    // Upload: the system picker, filtered to what the backend reads. "text/*" catches
    // Markdown files that providers label loosely; anything unreadable is refused politely.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickedFile(uri)
    }
    val pickFile = { picker.launch((config.limits.acceptedFileTypes + "text/*").distinct().toTypedArray()) }
    // Chosen "Upload a document" in the chooser: open the picker straight away, once.
    var autoPicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (tab == DescribeWith.UPLOAD && !autoPicked) {
            autoPicked = true
            pickFile()
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleListening()
    }
    fun onMic() {
        if (state.listening) { vm.toggleListening(); return }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.toggleListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // AI consent round trip.
    val consentResult = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow("ai_consent_granted", false)?.collectAsStateWithLifecycle()
    LaunchedEffect(consentResult?.value) {
        if (consentResult?.value == true) {
            nav.currentBackStackEntry?.savedStateHandle?.set("ai_consent_granted", false)
            vm.consentGranted()
        }
    }
    LaunchedEffect(state.needsConsent) {
        if (state.needsConsent) {
            vm.consentRequested()
            nav.navigate(Routes.AI_CONSENT)
        }
    }

    LaunchedEffect(state.done) {
        val id = state.done ?: return@LaunchedEffect
        nav.navigate(Routes.document(id, reveal = true)) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    BackHandler(enabled = state.phase != CaptureViewModel.Phase.CAPTURE) {
        if (state.phase == CaptureViewModel.Phase.TRANSCRIPT && !state.transcribing) vm.recordMore()
    }

    AnimatedContent(targetState = state.phase, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phase") { phase ->
        when (phase) {
            CaptureViewModel.Phase.CAPTURE -> CapturePhase(nav, vm, state, ::onMic, pickFile, config.limits.fileMaxBytes)
            CaptureViewModel.Phase.TRANSCRIPT -> TranscriptPhase(vm, state)
            CaptureViewModel.Phase.DRAFTING -> DraftingPhase(state)
        }
    }

    state.sensitive?.let { title ->
        SensitiveSheet(
            title = title,
            onRemove = { vm.removeSensitive() },
            onKeep = { vm.keepSensitive() },
            onEdit = { vm.editSensitive() },
        )
    }
}

/** Layer-0 warning: secrets or ID numbers found in what is about to be saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensitiveSheet(title: String, onRemove: () -> Unit, onKeep: () -> Unit, onEdit: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onEdit,
        sheetState = sheet,
        containerColor = Ink.paper,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Dw.gutter).padding(top = 24.dp, bottom = 16.dp).navigationBarsPadding()) {
            Text(title, style = DwType.heading, color = Ink.ink)
            Spacer(Modifier.height(8.dp))
            Text(SensitiveScan.MESSAGE, style = DwType.body, color = Ink.graphite)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Remove them", onClick = onRemove)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Keep as is", onClick = onKeep)
            TertiaryButton("Edit", onClick = onEdit)
        }
    }
}

@Composable
private fun CapturePhase(
    nav: NavController,
    vm: CaptureViewModel,
    state: CaptureViewModel.State,
    onMic: () -> Unit,
    onPickFile: () -> Unit,
    fileMaxBytes: Long,
) {
    val canFinish = when (state.tab) {
        CaptureViewModel.TAB_SPEAK -> state.hasSpeech || (state.usesRecorder && state.elapsedSec > 0)
        CaptureViewModel.TAB_TYPE -> state.typed.isNotBlank()
        else -> false
    }
    ScreenScaffold(
        topBar = {
            DwTopBar(
                title = if (vm.isAppend) "Add more" else null,
                onBack = { if (state.listening) vm.stopListening(); nav.popBackStack() },
                actions = { if (canFinish && !state.listening) com.raviga.downwork.ui.components.InlineAction("Done", onClick = { vm.finishCapture() }) },
            )
        },
        bottomBar = {
            when (state.tab) {
                CaptureViewModel.TAB_TYPE -> BottomBar { PrimaryButton("Done", enabled = canFinish, onClick = { vm.finishCapture() }) }
                CaptureViewModel.TAB_UPLOAD -> BottomBar { PrimaryButton("Choose a file", onClick = onPickFile) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dw.gutter)) {
            Segmented(listOf("Speak", "Type", "Upload"), state.tab, onSelect = { vm.selectTab(it) })
            Spacer(Modifier.height(24.dp))
            if (state.tab == CaptureViewModel.TAB_UPLOAD) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text("Upload a requirements document", style = DwType.heading, color = Ink.ink)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "A PDF, Word, RTF or text file up to ${fileMaxBytes / 1_048_576} MB. We read the text, you check it, and the brief is written from it.",
                        style = DwType.body,
                        color = Ink.graphite,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(FILE_NOTE, style = DwType.caption, color = Ink.graphite)
                    InlineNotice(state.error)
                }
            } else if (state.tab == CaptureViewModel.TAB_SPEAK) {
                // Dictation lands above the ink line; the newest sentence in ink, earlier ones in graphite.
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = true)) {
                    if (!state.hasSpeech && !state.listening) {
                        Text(HINT, style = DwType.dictation.copy(fontStyle = FontStyle.Italic), color = Ink.ash)
                    } else if (state.usesRecorder) {
                        Text(
                            if (state.listening) "Listening. Speak freely; we will write it down when you stop." else "Recorded ${formatTime(state.elapsedSec)}. Tap Done to hear it back as text, or record more.",
                            style = DwType.dictation, color = if (state.listening) Ink.ink else Ink.graphite,
                        )
                    } else {
                        state.committed.dropLast(if (state.partial.isBlank()) 1 else 0).forEach {
                            Text(it, style = DwType.dictation, color = Ink.graphite)
                            Spacer(Modifier.height(8.dp))
                        }
                        val latest = if (state.partial.isNotBlank()) state.partial else state.committed.lastOrNull()
                        if (latest != null) Text(latest, style = DwType.dictation, color = Ink.ink)
                    }
                }
                InkLine(level = state.level, active = state.listening)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(state.elapsedSec), style = DwType.caption, color = if (state.listening) Ink.cobalt else Ink.graphite)
                }
                InlineNotice(state.error)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MicButton(recording = state.listening, onClick = onMic, icon = Icons.Outlined.Mic)
                }
                Spacer(Modifier.height(8.dp))
                when {
                    canFinish && !state.listening -> TertiaryButton("Done, show me the text", onClick = { vm.finishCapture() })
                    vm.offersSample && !state.listening -> TertiaryButton("Use a sample description", onClick = { vm.useSample() }, color = Ink.graphite)
                    else -> Spacer(Modifier.height(Dw.buttonHeight))
                }
                Spacer(Modifier.height(16.dp))
            } else {
                PlainEditor(
                    value = state.typed,
                    onValueChange = { vm.setTyped(it) },
                    placeholder = HINT,
                    minLines = 10,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
                InlineNotice(state.error)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TranscriptPhase(vm: CaptureViewModel, state: CaptureViewModel.State) {
    val fromFile = state.tab == CaptureViewModel.TAB_UPLOAD
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.focusEditor) { if (state.focusEditor > 0) runCatching { focus.requestFocus() } }
    ScreenScaffold(
        topBar = {
            DwTopBar(
                title = when (state.tab) {
                    CaptureViewModel.TAB_UPLOAD -> "Here's what we read"
                    CaptureViewModel.TAB_TYPE -> "Here's your description"
                    else -> "Here's what we heard"
                },
                onBack = { if (!state.transcribing) vm.recordMore() },
            )
        },
        bottomBar = {
            BottomBar {
                InlineNotice(state.error, Modifier.padding(bottom = 8.dp))
                PrimaryButton(
                    if (vm.isAppend) "Looks right, add it to the brief" else "Looks right, write the brief",
                    enabled = !state.transcribing && state.transcript.isNotBlank(),
                    onClick = { vm.writeBrief() },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    when (state.tab) {
                        CaptureViewModel.TAB_SPEAK -> "Record more"
                        CaptureViewModel.TAB_UPLOAD -> "Choose another file"
                        else -> "Edit what I typed"
                    },
                    enabled = !state.transcribing,
                    onClick = { vm.recordMore() },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.height(8.dp))
            if (state.transcribing) {
                ProgressRule()
                Spacer(Modifier.height(12.dp))
                Text(state.progressMessage ?: "Listening back", style = DwType.secondary, color = Ink.graphite)
            } else {
                val file = state.file
                if (fromFile && file != null) {
                    Text(fileSource(file), style = DwType.secondary, color = Ink.graphite)
                    Spacer(Modifier.height(4.dp))
                    Text(FILE_NOTE, style = DwType.caption, color = Ink.graphite)
                    val notice = file.notice ?: if (file.truncated) "Only the first part fit. Add the rest as another note." else null
                    if (notice != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(notice, style = DwType.caption, color = Ink.amber)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to fix anything before we write the brief.", style = DwType.secondary, color = Ink.graphite)
                } else if (state.tab == CaptureViewModel.TAB_TYPE) {
                    Text("Tap to edit before we write the brief.", style = DwType.secondary, color = Ink.graphite)
                } else {
                    Text("Tap to fix anything that was misheard.", style = DwType.secondary, color = Ink.graphite)
                }
                Spacer(Modifier.height(16.dp))
                PlainEditor(
                    value = state.transcript,
                    onValueChange = { vm.setTranscript(it) },
                    placeholder = HINT,
                    minLines = 8,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).focusRequester(focus),
                )
            }
        }
    }
}

@Composable
private fun DraftingPhase(state: CaptureViewModel.State) {
    ScreenScaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dw.gutter)) {
            Spacer(Modifier.weight(1f))
            Text(state.progressMessage ?: "Writing the brief", style = DwType.heading, color = Ink.ink)
            Spacer(Modifier.height(20.dp))
            ProgressRule(progress = state.progress?.takeIf { it > 0f })
            Spacer(Modifier.height(12.dp))
            Text("This takes a minute or two.", style = DwType.caption, color = Ink.graphite)
            Spacer(Modifier.weight(1f))
        }
    }
}

private const val FILE_NOTE = "Only the text is kept. The file is deleted as soon as it's read, and is never shared with the team."

/** "From brief.pdf, 4 pages"; pages are left out when the backend can't count them. */
private fun fileSource(file: CaptureViewModel.FileRead): String = buildString {
    append("From ${file.fileName}")
    file.pageCount?.let { append(", $it ${if (it == 1) "page" else "pages"}") }
}

private fun formatTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
