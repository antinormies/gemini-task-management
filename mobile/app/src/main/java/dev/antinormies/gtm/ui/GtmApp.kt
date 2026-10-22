package dev.antinormies.gtm.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.antinormies.gtm.MainViewModel
import dev.antinormies.gtm.Tab
import dev.antinormies.gtm.VoiceState
import dev.antinormies.gtm.ui.screens.ProposalSheet
import dev.antinormies.gtm.ui.screens.QuickAddDialog
import dev.antinormies.gtm.ui.screens.SpeakScreen
import dev.antinormies.gtm.ui.screens.SystemScreen
import dev.antinormies.gtm.ui.screens.TaskDetailSheet
import dev.antinormies.gtm.ui.screens.TasksScreen
import dev.antinormies.gtm.ui.theme.GtmColors
import dev.antinormies.gtm.voice.SpeechController
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun GtmApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showQuickAdd by remember { mutableStateOf(false) }
    var voiceStart by remember { mutableLongStateOf(0L) }

    val speech = remember {
        SpeechController(
            context = context,
            onPartial = { text -> viewModel.voicePartial(text) },
            onFinal = { text ->
                val duration = (System.currentTimeMillis() - voiceStart).toInt().coerceAtLeast(0)
                viewModel.voiceFinished(text, duration)
            },
            onError = { message -> viewModel.voiceError(message) },
            onReady = {}
        )
    }
    DisposableEffect(Unit) {
        onDispose { speech.destroy() }
    }

    fun beginVoice() {
        viewModel.voiceStarted()
        voiceStart = System.currentTimeMillis()
        speech.start(Locale.getDefault().toLanguageTag())
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginVoice() else viewModel.voiceError("Microphone permission is required. Enable it in Android Settings.")
    }

    fun onOrb() {
        when (state.voiceState) {
            VoiceState.LISTENING -> speech.stop()
            VoiceState.THINKING -> Unit
            VoiceState.CAPTURED -> {
                viewModel.resetVoice()
                beginVoice()
            }
            else -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) beginVoice() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
    }
    LaunchedEffect(state.error) {
        if (state.paired && state.error != null) snackbar.showSnackbar(state.error!!)
    }
    LaunchedEffect(state.voiceState, state.transcript) {
        if (state.voiceState == VoiceState.LISTENING) {
            delay(10_000)
            if (state.voiceState == VoiceState.LISTENING) speech.stop()
        }
    }
    LaunchedEffect(state.voiceState) {
        if (state.voiceState == VoiceState.LISTENING) {
            delay(60_000)
            if (state.voiceState == VoiceState.LISTENING) speech.stop()
        }
    }

    if (!state.paired) {
        PairingScreen(viewModel, state)
        return
    }

    Scaffold(
        containerColor = GtmColors.surfaceSoft,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = GtmColors.surface, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = state.tab == Tab.SPEAK,
                    onClick = { viewModel.setTab(Tab.SPEAK) },
                    icon = { Text("◉", color = if (state.tab == Tab.SPEAK) GtmColors.accent else GtmColors.muted) },
                    label = { Text("Speak") }
                )
                NavigationBarItem(
                    selected = state.tab == Tab.TASKS,
                    onClick = { viewModel.setTab(Tab.TASKS) },
                    icon = { Text("▦", color = if (state.tab == Tab.TASKS) GtmColors.accent else GtmColors.muted) },
                    label = { Text("Tasks") }
                )
                NavigationBarItem(
                    selected = state.tab == Tab.SYSTEM,
                    onClick = { viewModel.setTab(Tab.SYSTEM) },
                    icon = { Text("◔", color = if (state.tab == Tab.SYSTEM) GtmColors.accent else GtmColors.muted) },
                    label = { Text("System") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.tab) {
                Tab.SPEAK -> SpeakScreen(state, viewModel, ::onOrb)
                Tab.TASKS -> TasksScreen(
                    state = state,
                    viewModel = viewModel,
                    onAdd = { showQuickAdd = true },
                    onOpen = { viewModel.openTask(it) },
                    onToggle = { viewModel.toggleTask(it) }
                )
                Tab.SYSTEM -> SystemScreen(state, viewModel)
            }
        }
    }

    state.selected?.let { task ->
        TaskDetailSheet(
            task = task,
            projects = state.projects,
            onClose = { viewModel.closeTask() },
            onSave = { selected, values -> viewModel.saveTask(selected, values) },
            onToggle = { viewModel.toggleTask(it) },
            onArchive = { viewModel.archiveTask(it) }
        )
    }

    if (state.proposal != null && state.showProposal) {
        ProposalSheet(
            proposal = state.proposal!!,
            onApply = { viewModel.applyProposal() },
            onDiscard = { viewModel.rejectProposal() }
        )
    }

    if (showQuickAdd) {
        QuickAddDialog(
            projects = state.projects,
            onDismiss = { showQuickAdd = false },
            onCreate = { title, projectId, priority, due ->
                viewModel.createTask(title, projectId, priority, due)
                showQuickAdd = false
            }
        )
    }
}
