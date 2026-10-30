package id.ai.antaratask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.ai.antaratask.data.ApiException
import id.ai.antaratask.data.Device
import id.ai.antaratask.data.GtmRepository
import id.ai.antaratask.data.Health
import id.ai.antaratask.data.Project
import id.ai.antaratask.data.Proposal
import id.ai.antaratask.data.Task
import id.ai.antaratask.data.Usage
import id.ai.antaratask.voice.TtsPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Tab { SPEAK, TASKS, SYSTEM }
enum class VoiceState { IDLE, CONNECTING, LISTENING, THINKING, CAPTURED, ERROR }

data class UiState(
    val paired: Boolean = false,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val tab: Tab = Tab.SPEAK,
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val selected: Task? = null,
    val proposal: Proposal? = null,
    val showProposal: Boolean = false,
    val voiceState: VoiceState = VoiceState.IDLE,
    val transcript: String = "",
    val error: String? = null,
    val toast: String? = null,
    val health: Health? = null,
    val usage: Usage? = null,
    val devices: List<Device> = emptyList()
)

class MainViewModel(
    private val repository: GtmRepository,
    private val tts: TtsPlayer
) : ViewModel() {
    private val _state = MutableStateFlow(UiState(paired = repository.hasToken))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (repository.hasToken) refresh()
    }

    fun setTab(tab: Tab) {
        _state.update { it.copy(tab = tab) }
        if (tab == Tab.SYSTEM) loadSystem()
    }

    fun retryConnection() {
        if (repository.hasToken) refresh()
    }

    fun loginWithGoogle(idToken: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.loginWithGoogle(idToken) }
                .onSuccess { ok ->
                    if (ok) {
                        _state.update { it.copy(paired = true, loading = false, error = null) }
                        refresh()
                    } else {
                        _state.update {
                            it.copy(loading = false, error = "Google sign-in did not return a session.")
                        }
                    }
                }
                .onFailure { fail(it) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { repository.logout() }
            repository.setToken(null)
            _state.value = UiState(paired = false)
        }
    }

    fun refresh() {
        if (!repository.hasToken) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val projects = repository.projects()
                val bundle = repository.tasks("board")
                Triple(projects, bundle.items, bundle.counts)
            }.onSuccess { (projects, tasks, counts) ->
                _state.update {
                    it.copy(
                        paired = true,
                        loading = false,
                        projects = projects,
                        tasks = tasks,
                        counts = counts,
                        error = null
                    )
                }
            }.onFailure { fail(it) }
        }
    }

    fun loadSystem() {
        if (!repository.hasToken) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching {
                val health = repository.health()
                val usage = repository.usage()
                val devices = repository.devices()
                Triple(health, usage, devices)
            }.onSuccess { (health, usage, devices) ->
                _state.update {
                    it.copy(busy = false, health = health, usage = usage, devices = devices)
                }
            }.onFailure { fail(it) }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            refresh()
            return
        }
        viewModelScope.launch {
            runCatching { repository.search(query) }
                .onSuccess { bundle ->
                    _state.update { it.copy(tasks = bundle.items, counts = bundle.counts) }
                }
                .onFailure { fail(it) }
        }
    }

    fun moveTask(task: Task, status: String) {
        if (task.status == status) return
        val target = status
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.updateTask(task, mapOf("status" to target)) }
                .onSuccess {
                    _state.update { state -> state.copy(busy = false) }
                    toast("Moved to " + target.replace("_", " ") + ".")
                    refresh()
                }
                .onFailure { fail(it) }
        }
    }

    fun addSubtask(task: Task, title: String) {
        if (title.isBlank()) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.createSubtask(task.id, title.trim()) }
                .onSuccess {
                    _state.update { state -> state.copy(busy = false) }
                    toast("Subtask added.")
                    openTask(task.id)
                }
                .onFailure { fail(it) }
        }
    }

    fun openTask(id: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.task(id) }
                .onSuccess { task -> _state.update { it.copy(busy = false, selected = task) } }
                .onFailure { fail(it) }
        }
    }

    fun closeTask() = _state.update { it.copy(selected = null) }

    fun createTask(title: String, projectId: String?, priority: String, dueAt: String?) {
        if (title.isBlank()) {
            toast("A title is required.")
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.createTask(title.trim(), projectId, priority, dueAt) }
                .onSuccess {
                    _state.update { state -> state.copy(busy = false) }
                    toast("Task captured.")
                    refresh()
                }
                .onFailure { fail(it) }
        }
    }

    fun saveTask(task: Task, values: Map<String, Any?>) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.updateTask(task, values) }
                .onSuccess {
                    _state.update { state -> state.copy(busy = false, selected = null) }
                    toast("Task saved.")
                    refresh()
                }
                .onFailure { fail(it) }
        }
    }

    fun toggleTask(task: Task) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { if (task.isDone) repository.reopen(task) else repository.complete(task) }
                .onSuccess {
                    _state.update { state -> state.copy(busy = false, selected = null) }
                    toast(if (task.isDone) "Task reopened." else "Task completed.")
                    refresh()
                }
                .onFailure { fail(it) }
        }
    }

    fun archiveTask(task: Task) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.archive(task) }
                .onSuccess {
                    _state.update { state -> state.copy(busy = false, selected = null) }
                    toast("Task archived.")
                    refresh()
                }
                .onFailure { fail(it) }
        }
    }

    fun voiceStarted() {
        _state.update {
            it.copy(
                tab = Tab.SPEAK,
                voiceState = VoiceState.LISTENING,
                transcript = "",
                error = null,
                proposal = null,
                showProposal = false
            )
        }
    }

    fun voicePartial(text: String) {
        _state.update { it.copy(transcript = text) }
    }

    fun voiceError(message: String) {
        _state.update { it.copy(voiceState = VoiceState.ERROR, error = message) }
        tts.speak("Nothing was saved. Try again.")
    }

    fun voiceFinished(transcript: String, durationMs: Int) {
        if (transcript.isBlank()) {
            voiceError("I did not catch that. Check the microphone and try again.")
            return
        }
        _state.update { it.copy(voiceState = VoiceState.THINKING, transcript = transcript, error = null) }
        viewModelScope.launch {
            runCatching { repository.voice(transcript, durationMs) }
                .onSuccess { proposal ->
                    if (proposal == null) {
                        voiceError("AI returned no proposal.")
                    } else {
                        _state.update {
                            it.copy(
                                voiceState = VoiceState.CAPTURED,
                                proposal = proposal,
                                showProposal = proposal.intent != "create_task" || proposal.requiresConfirmation,
                                error = null
                            )
                        }
                        tts.speak(proposal.message.take(180))
                    }
                }
                .onFailure {
                    _state.update { state ->
                        state.copy(voiceState = VoiceState.ERROR, error = it.message)
                    }
                }
        }
    }

    fun resetVoice() {
        _state.update { it.copy(voiceState = VoiceState.IDLE, transcript = "", proposal = null, showProposal = false, error = null) }
    }

    fun reviewProposal() {
        _state.update { it.copy(showProposal = true) }
    }

    fun applyProposal() {
        val proposal = _state.value.proposal ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.apply(proposal) }
                .onSuccess {
                    _state.update {
                        it.copy(busy = false, proposal = null, showProposal = false, voiceState = VoiceState.IDLE, transcript = "")
                    }
                    tts.speak("Changes applied.")
                    toast("Changes applied.")
                    refresh()
                }
                .onFailure { fail(it) }
        }
    }

    fun rejectProposal() {
        val proposal = _state.value.proposal ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.reject(proposal) }
                .onSuccess {
                    _state.update {
                        it.copy(busy = false, proposal = null, showProposal = false, voiceState = VoiceState.IDLE, transcript = "")
                    }
                    toast("Proposal discarded.")
                }
                .onFailure { fail(it) }
        }
    }

    fun backup() {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.backup() }
                .onSuccess { path ->
                    _state.update { it.copy(busy = false) }
                    toast("Backup: " + path)
                }
                .onFailure { fail(it) }
        }
    }

    fun rebuildSearch() {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.rebuildSearch() }
                .onSuccess {
                    _state.update { state -> state.copy(busy = false) }
                    toast("Search index rebuilt.")
                }
                .onFailure { fail(it) }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
    fun clearError() = _state.update { it.copy(error = null) }

    private fun toast(message: String) = _state.update { it.copy(toast = message) }

    private fun fail(error: Throwable) {
        if (error is ApiException && error.status == 401) {
            repository.setToken(null)
            _state.update {
                it.copy(
                    paired = false,
                    loading = false,
                    busy = false,
                    error = "Your session expired. Sign in again."
                )
            }
            return
        }
        _state.update { it.copy(loading = false, busy = false, error = error.message ?: "Something went wrong") }
    }

    override fun onCleared() {
        tts.shutdown()
        super.onCleared()
    }
}
