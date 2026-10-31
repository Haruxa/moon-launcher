package dev.moonmc.moon

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
    /** Встроенная пиксельная иконка вместо подписи ("" — рисовать подпись). */
    var icon: String = "",
)

/** Профили экранного управления: формат совпадает с тем, что читает игра. */
object TouchProfiles {
    val kinds = listOf("builtin", "key", "hold", "macro", "command", "switch")
    val builtins = listOf(
        "jump", "sneak", "sprint", "inventory", "pause", "view", "mode", "debug", "chat", "lock", "attack",
    )

    /** Иконки, которые умеет рисовать движок (см. ci/touch_layout.rs). */
    val icons = listOf(
        "", "jump", "sneak", "sprint", "attack", "inventory",
        "chat", "pause", "view", "mode", "debug", "lock",
    )

    fun iconTitle(icon: String): String = if (icon.isBlank()) I18n.t("подпись") else icon

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

    fun defaults(): List<Control> = presets().first().second

    /**
     * Встроенные раскладки в духе Minecraft Bedrock: джойстик и хотбар рисует игра,
     * здесь — кнопки. `x`/`y` — доля экрана, `r` — радиус в игровых единицах.
     */
    fun presets(): List<Pair<String, List<Control>>> = listOf(
        "Bedrock" to listOf(
            Control("jump", "Jump", 0.77f, 0.63f, 21f, icon = "jump"),
            Control("sneak", "Sneak", 0.77f, 0.76f, 14f, icon = "sneak"),
            Control("sprint", "Run", 0.67f, 0.41f, 14f, icon = "sprint"),
            Control("attack", "Hit", 0.67f, 0.55f, 20f, modes = listOf("aim"), icon = "attack"),
            Control("inventory", "...", 0.375f, 0.05f, 10f, icon = "inventory"),
            Control("chat", "Chat", 0.425f, 0.05f, 10f, icon = "chat"),
            Control("pause", "II", 0.475f, 0.05f, 10f, icon = "pause"),
        ),
        "Bedrock (левая рука)" to listOf(
            Control("jump", "Jump", 0.23f, 0.63f, 21f, icon = "jump"),
            Control("sneak", "Sneak", 0.23f, 0.76f, 14f, icon = "sneak"),
            Control("sprint", "Run", 0.33f, 0.41f, 14f, icon = "sprint"),
            Control("attack", "Hit", 0.33f, 0.55f, 20f, modes = listOf("aim"), icon = "attack"),
            Control("inventory", "...", 0.625f, 0.05f, 10f, icon = "inventory"),
            Control("chat", "Chat", 0.575f, 0.05f, 10f, icon = "chat"),
            Control("pause", "II", 0.525f, 0.05f, 10f, icon = "pause"),
        ),
        "Bedrock + отладка" to (
            listOf(
                Control("jump", "Jump", 0.77f, 0.63f, 21f, icon = "jump"),
                Control("sneak", "Sneak", 0.77f, 0.76f, 14f, icon = "sneak"),
                Control("sprint", "Run", 0.67f, 0.41f, 14f, icon = "sprint"),
                Control("attack", "Hit", 0.67f, 0.55f, 20f, modes = listOf("aim"), icon = "attack"),
                Control("inventory", "...", 0.375f, 0.05f, 10f, icon = "inventory"),
                Control("chat", "Chat", 0.425f, 0.05f, 10f, icon = "chat"),
                Control("pause", "II", 0.475f, 0.05f, 10f, icon = "pause"),
                Control("view", "F5", 0.06f, 0.06f, 9f, icon = "view"),
                Control("mode", "Tap", 0.13f, 0.06f, 9f, icon = "mode"),
                Control("debug", "F3", 0.20f, 0.06f, 9f, icon = "debug"),
                Control("lock", "Lock", 0.27f, 0.93f, 10f, icon = "lock"),
            )
        ),
    )

    /** Ставит встроенный профиль в папку игры (перезаписывая файл с тем же именем). */
    fun installPreset(prefs: Prefs, name: String, controls: List<Control>) {
        val file = File(dir(prefs), name.replace(' ', '-').lowercase() + ".json")
        write(file, name, controls)
        setActive(prefs, file.nameWithoutExtension)
    }

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
                icon = o.optString("icon", ""),
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
            if (c.icon.isNotBlank()) o.put("icon", c.icon)
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
