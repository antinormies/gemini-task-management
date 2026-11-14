package id.ai.antaratask.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import id.ai.antaratask.MainViewModel
import id.ai.antaratask.Tab
import id.ai.antaratask.UiState
import id.ai.antaratask.VoiceState
import id.ai.antaratask.data.Task
import id.ai.antaratask.ui.components.BottomItem
import id.ai.antaratask.ui.components.NumoAlertDialog
import id.ai.antaratask.ui.components.NumoBottomBar
import id.ai.antaratask.ui.components.ToastPill
import id.ai.antaratask.ui.screens.LoginScreen
import id.ai.antaratask.ui.screens.ProposalSheet
import id.ai.antaratask.ui.screens.QuickAddDialog
import id.ai.antaratask.ui.screens.SpeakScreen
import id.ai.antaratask.ui.screens.SystemScreen
import id.ai.antaratask.ui.screens.TaskDetailSheet
import id.ai.antaratask.ui.screens.TasksScreen
import id.ai.antaratask.ui.theme.NumoTheme
import id.ai.antaratask.voice.SpeechController
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun GtmApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val colors = NumoTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var showQuickAdd by remember { mutableStateOf(false) }
    var showMicRationale by remember { mutableStateOf(false) }
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
    DisposableEffect(Unit) { onDispose { speech.destroy() } }

    fun beginVoice() {
        viewModel.voiceStarted()
        voiceStart = System.currentTimeMillis()
        speech.start(Locale.getDefault().toLanguageTag())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginVoice()
        } else {
            viewModel.voiceError("Microphone permission is required. Enable it in Android Settings.")
        }
    }

    fun micPermissionGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun startCapture() {
        if (micPermissionGranted()) beginVoice() else showMicRationale = true
    }

    fun onOrb() {
        when (state.voiceState) {
            VoiceState.LISTENING -> speech.stop()
            VoiceState.THINKING -> Unit
            VoiceState.CAPTURED -> {
                viewModel.resetVoice()
                startCapture()
            }
            else -> startCapture()
        }
    }

    // Auto-finish: 10s of silence, 60s hard cap.
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

    // Toast pill: one at a time, 2.6s, then consumed.
    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2600)
            viewModel.consumeToast()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.paired) {
            viewModel.refresh()
            if (state.tab == Tab.SYSTEM) viewModel.loadSystem()
        }
    }

    if (!state.paired) {
        SystemBars(darkStatusIcons = false, darkNavIcons = !colors.isDark)
        LoginScreen(state, onToken = viewModel::loginWithGoogle)
        return
    }

    SystemBars(
        darkStatusIcons = state.tab == Tab.SPEAK || state.tab == Tab.SYSTEM,
        darkNavIcons = !colors.isDark
    )

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (state.tab) {
                    Tab.SPEAK -> SpeakScreen(state, viewModel, ::onOrb)
                    Tab.TASKS -> TasksScreen(
                        state = state,
                        viewModel = viewModel,
                        onAdd = { showQuickAdd = true },
                        onOpen = { viewModel.openTask(it) },
                        onToggle = { viewModel.toggleTask(it) },
                        onVoice = {
                            viewModel.setTab(Tab.SPEAK)
                            startCapture()
                        }
                    )
                    Tab.SYSTEM -> SystemScreen(state, viewModel)
                }
            }
            if (!imeVisible) {
                NumoBottomBar(
                    items = listOf(
                        BottomItem("Speak", Icons.Rounded.Mic),
                        BottomItem("Tasks", Icons.Rounded.Checklist, badge = overdueCount(state)),
                        BottomItem("System", Icons.Rounded.Insights)
                    ),
                    selectedIndex = state.tab.ordinal,
                    onSelect = { index -> viewModel.setTab(Tab.entries[index]) },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }

        AnimatedVisibility(
            visible = state.toast != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        ) {
            ToastPill(state.toast ?: "")
        }
    }

    if (showMicRationale) {
        NumoAlertDialog(
            title = "Use your microphone?",
            body = "AntaraTask turns speech into a task. Audio is handled by your phone's " +
                "speech service; the app receives only the transcript.",
            confirmLabel = "Allow microphone",
            dismissLabel = "Not now",
            onConfirm = {
                showMicRationale = false
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { showMicRationale = false }
        )
    }

    state.selected?.let { task ->
        TaskDetailSheet(
            task = task,
            projects = state.projects,
            onClose = { viewModel.closeTask() },
            onSave = { selected, values -> viewModel.saveTask(selected, values) },
            onToggle = { viewModel.toggleTask(it) },
            onArchive = { viewModel.archiveTask(it) },
            onAddSubtask = { selected, title -> viewModel.addSubtask(selected, title) }
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

private fun overdueCount(state: UiState): Int {
    val now = java.time.LocalDateTime.now()
    return state.tasks.count { task: Task -> !task.isDone && task.dueAt != null && runCatching {
        id.ai.antaratask.ui.components.parseDue(task.dueAt)?.isBefore(now) == true
    }.getOrDefault(false) }
}

@Composable
private fun SystemBars(darkStatusIcons: Boolean, darkNavIcons: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = darkStatusIcons
            controller.isAppearanceLightNavigationBars = darkNavIcons
        }
    }
}
