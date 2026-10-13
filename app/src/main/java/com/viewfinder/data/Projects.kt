package com.viewfinder.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Project(
    val id: String,
    val name: String = "",
    val director: String = "",
    val dp: String = "",
    val location: String = "",
    val scene: String = "1",
    val shot: String = "1",
    val notes: String = "",
    val autoShot: Boolean = true,
    val created: Long = System.currentTimeMillis(),
) {
    val title get() = name.ifBlank { "Untitled" }
}

/** One exported still, referenced by its MediaStore uri. */
data class Shot(val uri: String, val scene: String, val shot: String, val time: Long)

/**
 * Projects and their shots, persisted as JSON in SharedPreferences on every change (no save step).
 * Observable as Compose state.
 */
class ProjectStore(context: Context) {
    private val p = context.getSharedPreferences("projects", Context.MODE_PRIVATE)

    val projects = mutableStateListOf<Project>()
    private val shots = mutableStateMapOf<String, List<Shot>>()
    var currentId by mutableStateOf("")
        private set

    init {
        // Damaged entries are skipped rather than crashing the app on start.
        p.getString("projects", null)?.let { raw ->
            val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
            for (i in 0 until arr.length()) {
                try {
                    projects += arr.getJSONObject(i).toProject()
                } catch (e: Exception) {
                    // Skip.
                }
            }
        }
        projects.forEach { proj -> shots[proj.id] = readShots(proj.id) }
        currentId = p.getString("current", null)?.takeIf { id -> projects.any { it.id == id } } ?: ""
    }

    /**
     * First run: start with one project, seeded from older single-project settings, holding the
     * stills that were exported before projects existed.
     */
    fun ensureProject(seed: () -> Project, existing: () -> List<Shot>) {
        if (projects.isEmpty()) {
            val first = seed()
            projects += first
            val found = existing()
            shots[first.id] = found
            writeShots(first.id, found)
            saveProjects()
        }
        if (currentId.isEmpty()) select(projects.first().id)
    }

    val current: Project get() = projects.firstOrNull { it.id == currentId } ?: projects.first()

    fun shotsOf(id: String): List<Shot> = shots[id].orEmpty()

    fun select(id: String) {
        currentId = id
        p.edit().putString("current", id).apply()
    }

    fun create(): Project {
        val proj = Project(id = newId())
        projects.add(0, proj)
        shots[proj.id] = emptyList()
        saveProjects()
        select(proj.id)
        return proj
    }

    fun update(proj: Project) {
        val i = projects.indexOfFirst { it.id == proj.id }
        if (i < 0) return
        projects[i] = proj
        saveProjects()
    }

    /** Removes the project record; the image files themselves stay in the phone's gallery. */
    fun delete(id: String) {
        projects.removeAll { it.id == id }
        shots.remove(id)
        p.edit().remove("shots_$id").apply()
        if (projects.isEmpty()) create() else {
            saveProjects()
            if (currentId == id) select(projects.first().id)
        }
    }

    fun addShot(projectId: String, shot: Shot) {
        if (!shot.uri.startsWith(MEDIA_PREFIX)) return
        val list = listOf(shot) + shotsOf(projectId).filterNot { it.uri == shot.uri }
        shots[projectId] = list
        writeShots(projectId, list)
    }

    fun removeShot(projectId: String, uri: String) {
        val list = shotsOf(projectId).filterNot { it.uri == uri }
        shots[projectId] = list
        writeShots(projectId, list)
    }

    private fun saveProjects() {
        val arr = JSONArray()
        projects.forEach { arr.put(it.toJson()) }
        p.edit().putString("projects", arr.toString()).apply()
    }

    private fun readShots(id: String): List<Shot> {
        val raw = p.getString("shots_$id", null) ?: return emptyList()
        val arr = try { JSONArray(raw) } catch (e: Exception) { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val o = arr.getJSONObject(i)
                Shot(o.getString("uri"), o.optString("scene"), o.optString("shot"), o.optLong("time"))
                    .takeIf { it.uri.startsWith(MEDIA_PREFIX) }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun writeShots(id: String, list: List<Shot>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("scene", it.scene).put("shot", it.shot).put("time", it.time))
        }
        p.edit().putString("shots_$id", arr.toString()).apply()
    }

    private fun Project.toJson() = JSONObject()
        .put("id", id).put("name", name).put("director", director).put("dp", dp)
        .put("location", location).put("scene", scene).put("shot", shot).put("notes", notes)
        .put("autoShot", autoShot).put("created", created)

    private fun JSONObject.toProject() = Project(
        id = getString("id"),
        name = optString("name"),
        director = optString("director"),
        dp = optString("dp"),
        location = optString("location"),
        scene = optString("scene", "1"),
        shot = optString("shot", "1"),
        notes = optString("notes"),
        autoShot = optBoolean("autoShot", true),
        created = optLong("created", System.currentTimeMillis()),
    )

    companion object {
        /** Shots may only reference MediaStore images (never arbitrary files or other providers). */
        private const val MEDIA_PREFIX = "content://media/"

        fun newId(): String = UUID.randomUUID().toString()

        /** Stills in Pictures/viewfinder this app can still see; scene/shot parsed from "_sc1_sh22_". */
        fun findExistingStills(context: Context): List<Shot> {
            val result = mutableListOf<Shot>()
            val cols = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
            )
            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols,
                    "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("Pictures/viewfinder%"),
                    "${MediaStore.Images.Media.DATE_ADDED} DESC",
                )?.use { c ->
                    val pattern = Regex("sc([^_]*)_sh([^_]*)_")
                    while (c.moveToNext()) {
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                        val m = pattern.find(c.getString(1) ?: "")
                        result += Shot(uri.toString(), m?.groupValues?.get(1) ?: "", m?.groupValues?.get(2) ?: "", c.getLong(2) * 1000)
                    }
                }
            } catch (e: Exception) {
                // Not readable: start empty.
            }
            return result
        }
    }
}
