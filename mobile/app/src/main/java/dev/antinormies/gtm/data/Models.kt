package dev.antinormies.gtm.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

data class Tag(val id: String, val name: String, val color: String) {
    companion object {
        fun from(json: JSONObject) = Tag(json.optString("id"), json.optString("name"), json.optString("color"))
    }
}

data class TaskEvent(val eventType: String, val actor: String, val createdAt: String) {
    companion object {
        fun from(json: JSONObject) = TaskEvent(
            json.optString("event_type"),
            json.optString("actor"),
            json.optString("created_at")
        )
    }
}

data class Task(
    val id: String,
    val shortKey: String,
    val title: String,
    val description: String,
    val status: String,
    val priority: String,
    val dueAt: String?,
    val dueAllDay: Boolean,
    val estimateMinutes: Int?,
    val projectId: String?,
    val projectName: String?,
    val version: Int,
    val tags: List<Tag> = emptyList(),
    val subtasks: List<Task> = emptyList(),
    val events: List<TaskEvent> = emptyList()
) {
    val isDone: Boolean get() = status == "done"

    companion object {
        fun from(json: JSONObject): Task {
            val tags = mutableListOf<Tag>()
            json.optJSONArray("tags")?.let { array ->
                for (i in 0 until array.length()) tags.add(Tag.from(array.getJSONObject(i)))
            }
            val subtasks = mutableListOf<Task>()
            json.optJSONArray("subtasks")?.let { array ->
                for (i in 0 until array.length()) subtasks.add(from(array.getJSONObject(i)))
            }
            val events = mutableListOf<TaskEvent>()
            json.optJSONArray("events")?.let { array ->
                for (i in 0 until array.length()) events.add(TaskEvent.from(array.getJSONObject(i)))
            }
            return Task(
                id = json.optString("id"),
                shortKey = json.optString("short_key", "GTM"),
                title = json.optString("title"),
                description = json.optString("description"),
                status = json.optString("status", "inbox"),
                priority = json.optString("priority", "none"),
                dueAt = json.nullableString("due_at"),
                dueAllDay = json.optBoolean("due_all_day", false),
                estimateMinutes = if (json.isNull("estimate_minutes")) null else json.optInt("estimate_minutes"),
                projectId = json.nullableString("project_id"),
                projectName = json.nullableString("project_name"),
                version = json.optInt("version", 1),
                tags = tags,
                subtasks = subtasks,
                events = events
            )
        }
    }
}

data class Project(
    val id: String,
    val name: String,
    val color: String,
    val activeCount: Int
) {
    companion object {
        fun from(json: JSONObject) = Project(
            json.optString("id"),
            json.optString("name"),
            json.optString("color", "#64748b"),
            json.optInt("active_count", 0)
        )
    }
}

data class Preview(val op: String, val title: String, val summary: String) {
    companion object {
        fun from(json: JSONObject): Preview {
            val op = json.optString("op", "change")
            val title = json.optString("title", json.optString("parent_title", op))
            val summary = when (op) {
                "create" -> "Create task"
                "update" -> json.optJSONObject("changes")?.toString() ?: "Update task"
                "archive" -> "Archive task"
                "complete" -> "Complete task"
                "query" -> "Found " + json.optInt("count", 0) + " tasks"
                "plan_day" -> "Plan " + json.optString("date")
                "create_subtasks" -> json.optInt("count", 0).toString() + " subtasks"
                else -> json.toString()
            }
            return Preview(op, title, summary)
        }
    }
}

data class Proposal(
    val id: String,
    val intent: String,
    val message: String,
    val preview: List<Preview>,
    val requiresConfirmation: Boolean,
    val confidence: Double
) {
    companion object {
        fun from(json: JSONObject): Proposal {
            val preview = mutableListOf<Preview>()
            json.optJSONArray("preview")?.let { array ->
                for (i in 0 until array.length()) preview.add(Preview.from(array.getJSONObject(i)))
            }
            return Proposal(
                id = json.optString("id"),
                intent = json.optString("intent", "change"),
                message = json.optString("assistant_message"),
                preview = preview,
                requiresConfirmation = json.optBoolean("requires_confirmation", true),
                confidence = json.optDouble("confidence", 0.0)
            )
        }
    }
}

data class Device(
    val id: String,
    val name: String,
    val lastSeenAt: String?,
    val revoked: Boolean
) {
    companion object {
        fun from(json: JSONObject) = Device(
            json.optString("id"),
            json.optString("name"),
            json.nullableString("last_seen_at"),
            !json.isNull("revoked_at")
        )
    }
}

data class Health(
    val dbOk: Boolean,
    val provider: String,
    val agyInstalled: Boolean,
    val agyModel: String,
    val openCodeConfigured: Boolean,
    val openCodeModel: String,
    val voiceEnabled: Boolean,
    val voiceProvider: String
) {
    companion object {
        fun from(json: JSONObject): Health {
            val db = json.optJSONObject("database")
            val ai = json.optJSONObject("ai")
            val voice = json.optJSONObject("voice")
            return Health(
                dbOk = db?.optBoolean("ok", false) ?: false,
                provider = ai?.optString("provider", "auto") ?: "auto",
                agyInstalled = ai?.optBoolean("agy_installed", false) ?: false,
                agyModel = ai?.optString("agy_model", "") ?: "",
                openCodeConfigured = ai?.optBoolean("opencode_configured", false) ?: false,
                openCodeModel = ai?.optString("opencode_model", "") ?: "",
                voiceEnabled = voice?.optBoolean("enabled", false) ?: false,
                voiceProvider = voice?.optString("provider", "auto") ?: "auto"
            )
        }
    }
}

data class StorageStat(val dbBytes: Long, val backupsBytes: Long, val logsBytes: Long, val workspaceBytes: Long) {
    val total: Long get() = dbBytes + backupsBytes + logsBytes + workspaceBytes
}

data class Usage(
    val runs: Int,
    val voiceRuns: Int,
    val tokens: Long,
    val estimatedCost: Double,
    val tasks: Int,
    val projects: Int,
    val devices: Int,
    val storage: StorageStat
) {
    companion object {
        fun from(json: JSONObject): Usage {
            val counts = json.optJSONObject("counts")
            val storage = json.optJSONObject("storage")
            return Usage(
                runs = json.optInt("runs", 0),
                voiceRuns = json.optInt("voice_runs", 0),
                tokens = json.optLong("tokens", 0),
                estimatedCost = json.optDouble("estimated_cost_usd", 0.0),
                tasks = counts?.optInt("tasks", 0) ?: 0,
                projects = counts?.optInt("projects", 0) ?: 0,
                devices = counts?.optInt("devices", 0) ?: 0,
                storage = StorageStat(
                    storage?.optLong("db_bytes", 0) ?: 0,
                    storage?.optLong("backups_bytes", 0) ?: 0,
                    storage?.optLong("logs_bytes", 0) ?: 0,
                    storage?.optLong("workspace_bytes", 0) ?: 0
                )
            )
        }
    }
}
