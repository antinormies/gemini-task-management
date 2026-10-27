package dev.antinormies.gtm.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.antinormies.gtm.MainViewModel
import dev.antinormies.gtm.Tab
import dev.antinormies.gtm.UiState
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

    // Keep the phone in sync with the server: refresh whenever the app comes
    // back to the foreground (edits made elsewhere while it was backgrounded).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.paired) {
            viewModel.refresh()
            if (state.tab == Tab.SYSTEM) viewModel.loadSystem()
        }
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (!state.paired) {
        SystemBars(darkIcons = !isDarkTheme)
        ConnectingScreen(state, viewModel::retryConnection)
        return
    }

    val darkStage = state.tab == Tab.SPEAK
    SystemBars(darkIcons = !darkStage && !isDarkTheme)

    Box(
        Modifier
            .fillMaxSize()
            .background(if (darkStage) GtmColors.stage else MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { GtmBottomBar(selected = state.tab, onSelect = viewModel::setTab, darkStage = darkStage) }
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

@Composable
private fun ConnectingScreen(state: UiState, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "CALL TASK AI",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            if (state.error == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(14.dp))
                Text(
                    "Connecting to your local server…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    "Can't reach the local server",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    state.error ?: "Check that the server is running and adb reverse is set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

private data class GtmTabItem(val tab: Tab, val label: String, val icon: ImageVector)

@Composable
private fun GtmBottomBar(selected: Tab, onSelect: (Tab) -> Unit, darkStage: Boolean) {
    val items = listOf(
        GtmTabItem(Tab.SPEAK, "Speak", Icons.Rounded.Mic),
        GtmTabItem(Tab.TASKS, "Tasks", Icons.Rounded.Dashboard),
        GtmTabItem(Tab.SYSTEM, "System", Icons.Rounded.Settings)
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        color = if (darkStage) GtmColors.stageCard else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = if (darkStage) 0.dp else 2.dp,
        shadowElevation = if (darkStage) 0.dp else 10.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val isSelected = selected == item.tab
                val background by animateColorAsState(
                    targetValue = when {
                        isSelected && darkStage -> GtmColors.wave.copy(alpha = 0.18f)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color.Transparent
                    },
                    label = "navBackground"
                )
                val contentColor = when {
                    isSelected && darkStage -> GtmColors.wave
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    darkStage -> GtmColors.stageMuted
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(background)
                        .clickable { onSelect(item.tab) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemBars(darkIcons: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = darkIcons
            controller.isAppearanceLightNavigationBars = darkIcons
        }
    }
}
