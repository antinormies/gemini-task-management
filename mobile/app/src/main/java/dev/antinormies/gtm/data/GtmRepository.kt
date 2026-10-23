package dev.antinormies.gtm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

data class TaskBundle(val items: List<Task>, val counts: Map<String, Int>)

class GtmRepository(
    private val api: ApiClient,
    private val store: TokenStore
) {
    val serverUrl: String get() = store.serverUrl
    val hasToken: Boolean get() = !store.token.isNullOrBlank()

    fun setServerUrl(value: String) {
        store.serverUrl = value
        api.baseUrl = store.serverUrl
    }

    fun setToken(value: String?) {
        store.token = value
        api.token = value
    }

    suspend fun pair(code: String, deviceName: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("code", code).put("device_name", deviceName)
        val response = api.request("POST", "/api/auth/pair", body)
        val token = response?.optString("token").orEmpty()
        if (token.isBlank()) return@withContext false
        setToken(token)
        true
    }

    suspend fun tasks(view: String, projectId: String? = null): TaskBundle = withContext(Dispatchers.IO) {
        var path = "/api/tasks?view=" + url(view) + "&include_done=true"
        if (!projectId.isNullOrBlank()) path += "&project_id=" + url(projectId)
        val json = api.request("GET", path) ?: JSONObject()
        val items = mutableListOf<Task>()
        json.optJSONArray("items")?.let { array ->
            for (i in 0 until array.length()) items.add(Task.from(array.getJSONObject(i)))
        }
        val counts = mutableMapOf<String, Int>()
        json.optJSONObject("counts")?.let { obj ->
            obj.keys().forEach { key -> counts[key] = obj.optInt(key) }
        }
        TaskBundle(items, counts)
    }

    suspend fun search(query: String): TaskBundle = withContext(Dispatchers.IO) {
        val json = api.request("GET", "/api/search?q=" + url(query)) ?: JSONObject()
        val items = mutableListOf<Task>()
        json.optJSONArray("items")?.let { array ->
            for (i in 0 until array.length()) items.add(Task.from(array.getJSONObject(i)))
        }
        val counts = mutableMapOf<String, Int>()
        json.optJSONObject("counts")?.let { obj ->
            obj.keys().forEach { key -> counts[key] = obj.optInt(key) }
        }
        TaskBundle(items, counts)
    }

    suspend fun createSubtask(parentId: String, title: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("title", title)
            .put("status", "todo")
            .put("parent_id", parentId)
        api.request("POST", "/api/tasks", body)
        true
    }

    suspend fun projects(): List<Project> = withContext(Dispatchers.IO) {
        val json = api.request("GET", "/api/projects") ?: JSONObject()
        val result = mutableListOf<Project>()
        json.optJSONArray("items")?.let { array ->
            for (i in 0 until array.length()) result.add(Project.from(array.getJSONObject(i)))
        }
        result
    }

    suspend fun task(id: String): Task? = withContext(Dispatchers.IO) {
        val json = api.request("GET", "/api/tasks/" + url(id))?.optJSONObject("task") ?: return@withContext null
        Task.from(json)
    }

    suspend fun createTask(title: String, projectId: String?, priority: String, dueAt: String?): Task? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("title", title)
                .put("status", "inbox")
                .put("priority", priority)
            if (!projectId.isNullOrBlank()) body.put("project_id", projectId)
            if (!dueAt.isNullOrBlank()) body.put("due_at", dueAt)
            api.request("POST", "/api/tasks", body)?.optJSONObject("task")?.let { Task.from(it) }
        }

    suspend fun updateTask(task: Task, values: Map<String, Any?>): Task? = withContext(Dispatchers.IO) {
        val body = JSONObject()
        body.put("version", task.version)
        values.forEach { (key, value) ->
            if (value == null) body.put(key, JSONObject.NULL) else body.put(key, value)
        }
        api.request("PATCH", "/api/tasks/" + url(task.id), body)?.optJSONObject("task")?.let { Task.from(it) }
    }

    suspend fun complete(task: Task): Task? = withContext(Dispatchers.IO) {
        api.request("POST", "/api/tasks/" + url(task.id) + "/complete")?.optJSONObject("task")?.let { Task.from(it) }
    }

    suspend fun reopen(task: Task): Task? = withContext(Dispatchers.IO) {
        api.request("POST", "/api/tasks/" + url(task.id) + "/reopen")?.optJSONObject("task")?.let { Task.from(it) }
    }

    suspend fun archive(task: Task): Boolean = withContext(Dispatchers.IO) {
        api.request("DELETE", "/api/tasks/" + url(task.id))
        true
    }

    suspend fun voice(transcript: String, durationMs: Int): Proposal? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("transcript", transcript)
            .put("duration_ms", durationMs)
            .put("view", "board")
            .put("language", "en-US")
        val json = api.request("POST", "/api/voice/interpret", body) ?: return@withContext null
        json.optJSONObject("proposal")?.let { Proposal.from(it) }
    }

    suspend fun apply(proposal: Proposal): Boolean = withContext(Dispatchers.IO) {
        api.request("POST", "/api/ai/proposals/" + url(proposal.id) + "/apply")
        true
    }

    suspend fun reject(proposal: Proposal): Boolean = withContext(Dispatchers.IO) {
        api.request("POST", "/api/ai/proposals/" + url(proposal.id) + "/reject")
        true
    }

    suspend fun health(): Health? = withContext(Dispatchers.IO) {
        api.request("GET", "/api/health")?.let { Health.from(it) }
    }

    suspend fun usage(): Usage? = withContext(Dispatchers.IO) {
        api.request("GET", "/api/usage?days=7")?.let { Usage.from(it) }
    }

    suspend fun devices(): List<Device> = withContext(Dispatchers.IO) {
        val json = api.request("GET", "/api/auth/devices") ?: JSONObject()
        val result = mutableListOf<Device>()
        json.optJSONArray("items")?.let { array ->
            for (i in 0 until array.length()) result.add(Device.from(array.getJSONObject(i)))
        }
        result
    }

    suspend fun backup(): String = withContext(Dispatchers.IO) {
        val json = api.request("POST", "/api/maintenance/backup") ?: JSONObject()
        json.optString("path", "Backup complete")
    }

    suspend fun rebuildSearch(): Boolean = withContext(Dispatchers.IO) {
        api.request("POST", "/api/maintenance/rebuild-search")
        true
    }

    suspend fun unpair() = withContext(Dispatchers.IO) {
        runCatching { api.request("POST", "/api/auth/revoke") }
        setToken(null)
        store.clear()
    }

    private fun url(value: String): String = URLEncoder.encode(value, "UTF-8")
}
