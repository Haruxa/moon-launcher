package dev.margymc.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Одна кнопка раскладки: положение 0..1 по ширине/высоте и радиус в игровых единицах. */
data class Control(
    var id: String,
    var label: String,
    var x: Float,
    var y: Float,
    var r: Float,
    var modes: List<String> = emptyList(),
    var kind: String = "builtin",
    var value: String = "",
)

/** Профили экранного управления: формат совпадает с тем, что читает игра. */
object TouchProfiles {
    val kinds = listOf("builtin", "key", "hold", "macro", "command", "switch")
    val builtins = listOf(
        "jump", "sneak", "sprint", "inventory", "pause", "view", "mode", "debug", "chat", "lock", "attack",
    )

    fun kindTitle(kind: String): String = I18n.t(
        when (kind) {
            "builtin" -> "штатная кнопка"
            "key" -> "клавиша"
            "hold" -> "удержание"
            "macro" -> "макрос"
            "command" -> "команда в чат"
            else -> "другой профиль"
        }
    )

    fun dir(prefs: Prefs) = File(prefs.home, "touch/profiles")
    fun activeFile(prefs: Prefs) = File(prefs.home, "touch/active.txt")

    fun defaults(): List<Control> = listOf(
        Control("jump", "Jump", 0.95f, 0.89f, 22f),
        Control("sneak", "Sneak", 0.83f, 0.94f, 15f),
        Control("sprint", "Run", 0.83f, 0.83f, 15f),
        Control("inventory", "...", 0.65f, 0.97f, 10f),
        Control("pause", "II", 0.97f, 0.04f, 11f),
        Control("view", "F5", 0.91f, 0.04f, 11f),
        Control("mode", "Tap", 0.85f, 0.04f, 11f),
        Control("debug", "F3", 0.79f, 0.04f, 11f),
        Control("chat", "Chat", 0.50f, 0.04f, 12f),
        Control("lock", "Lock", 0.30f, 0.95f, 11f),
        Control("attack", "Hit", 0.67f, 0.50f, 24f, modes = listOf("aim")),
    )

    fun read(file: File): List<Control> = runCatching {
        val root = JSONObject(file.readText())
        val list = root.optJSONArray("controls") ?: JSONArray()
        (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val action = o.optJSONObject("action")
            val kind = if (action == null) "builtin" else kinds.firstOrNull { action.has(it) } ?: "builtin"
            val value = when (kind) {
                "macro" -> action?.optJSONArray("macro")?.let { arr ->
                    (0 until arr.length()).joinToString(" ") { arr.optString(it) }
                } ?: ""
                "builtin" -> ""
                else -> action?.optString(kind, "") ?: ""
            }
            val modes = o.optJSONArray("modes")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            } ?: emptyList()
            Control(
                id = o.optString("id", ""),
                label = o.optString("label", ""),
                x = o.optDouble("x", 0.5).toFloat(),
                y = o.optDouble("y", 0.5).toFloat(),
                r = o.optDouble("r", 18.0).toFloat(),
                modes = modes,
                kind = kind,
                value = value,
            )
        }
    }.getOrDefault(emptyList())

    fun write(file: File, name: String, controls: List<Control>) {
        val list = JSONArray()
        controls.forEach { c ->
            val o = JSONObject()
            o.put("id", c.id.ifBlank { "btn_" + (1000..9999).random() })
            if (c.label.isNotBlank()) o.put("label", c.label)
            o.put("x", (c.x * 1000).toInt() / 1000.0)
            o.put("y", (c.y * 1000).toInt() / 1000.0)
            o.put("r", c.r.toDouble())
            if (c.modes.isNotEmpty()) o.put("modes", JSONArray(c.modes))
            when (c.kind) {
                "builtin" -> Unit
                "macro" -> o.put(
                    "action",
                    JSONObject().put("macro", JSONArray(c.value.split(' ').filter { it.isNotBlank() })),
                )
                else -> o.put("action", JSONObject().put(c.kind, c.value.ifBlank { "space" }))
            }
            list.put(o)
        }
        val root = JSONObject().put("schema", 1).put("name", name).put("controls", list)
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
    }

    fun ensureDefault(prefs: Prefs) {
        val dir = dir(prefs)
        if (dir.listFiles { f -> f.name.endsWith(".json") }?.isNotEmpty() == true) return
        dir.mkdirs()
        write(File(dir, "default.json"), "Default", defaults())
        activeFile(prefs).parentFile?.mkdirs()
        activeFile(prefs).writeText("default")
    }

    fun list(prefs: Prefs): List<File> =
        dir(prefs).listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()

    fun active(prefs: Prefs): String =
        runCatching { activeFile(prefs).readText().trim() }.getOrDefault("")

    fun setActive(prefs: Prefs, name: String) {
        activeFile(prefs).parentFile?.mkdirs()
        activeFile(prefs).writeText(name)
    }
}
