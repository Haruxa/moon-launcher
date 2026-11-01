package dev.moonmc.moon

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

/**
 * Обмен профилями управления: экспорт/импорт файла и мягкий импорт чужих форматов.
 *
 * Свой формат — как у игры (`touch/profiles/*.json`): `{"controls":[{"id","label","x","y","r",…}]}`.
 * Чужой (Zalith/Pojav-подобный) распознаём по массиву кнопок и координатам в 0..10000:
 * конвертируем позиции в 0..1, а имена — в наши штатные кнопки. Что не узнали, не ломает
 * импорт: такие кнопки просто пропускаются (лучше профиль без одной кнопки, чем ничего).
 */
object ProfileIO {
    /** Наши штатные кнопки по чужим именам. */
    private val known = mapOf(
        "jump" to "jump", "up" to "jump", "space" to "jump", "fly_up" to "jump",
        "sneak" to "sneak", "down" to "sneak", "shift" to "sneak", "fly_down" to "sneak",
        "sprint" to "sprint", "run" to "sprint", "ctrl" to "sprint",
        "attack" to "attack", "left" to "attack", "hit" to "attack",
        "inventory" to "inventory", "inv" to "inventory", "e" to "inventory",
        "chat" to "chat", "t" to "chat",
        "pause" to "pause", "menu" to "pause", "esc" to "pause",
        "view" to "view", "f5" to "view",
        "debug" to "debug", "f3" to "debug",
        "lock" to "lock", "shift_lock" to "lock",
        "mode" to "mode",
    )

    /** Иконка для штатной кнопки. */
    fun iconFor(id: String): String = when (id) {
        "jump", "sneak", "sprint", "attack", "inventory", "chat", "pause", "view", "mode", "debug", "lock" -> id
        else -> ""
    }

    private fun number(obj: JSONObject, vararg keys: String, fallback: Float): Float {
        for (key in keys) {
            if (!obj.has(key)) continue
            val value = obj.optDouble(key, Double.NaN)
            if (!value.isNaN()) return value.toFloat()
        }
        return fallback
    }

    /** Позиция 0..1 из любого разумного вида: 0..1, 0..1000, 0..10000. */
    private fun fraction(value: Float): Float = when {
        value in 0f..1f -> value
        value in 0f..100f -> value / 100f
        value in 0f..1000f -> value / 1000f
        else -> (value / 10_000f).coerceIn(0f, 1f)
    }

    private fun radius(obj: JSONObject): Float {
        val size = number(obj, "r", "size", "radius", "width", "w", fallback = 0f)
        return when {
            size <= 0f -> 18f
            size <= 1f -> 18f
            size <= 100f -> (size / 2f).coerceIn(8f, 60f)
            else -> (size / 20f).coerceIn(8f, 60f)
        }
    }

    /**
     * Разбирает файл профиля: свой формат как есть, чужой — в меру узнаваемости.
     * Возвращает null, если кнопок не нашлось вовсе.
     */
    fun parse(text: String): List<Control>? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        // 1. наш формат
        root.optJSONArray("controls")?.let { list ->
            val controls = (0 until list.length()).mapNotNull { i ->
                list.optJSONObject(i)?.let(TouchProfiles::fromJson)
            }
            if (controls.isNotEmpty()) return controls
        }
        // 2. чужой: ищем любой массив кнопок
        val array = listOf("buttons", "widgets", "elements", "controls", "mControlInfo")
            .firstNotNullOfOrNull { root.optJSONArray(it) }
            ?: return null
        val controls = ArrayList<Control>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val raw = listOf("id", "name", "type", "action", "key")
                .firstNotNullOfOrNull { key -> o.optString(key, "").ifBlank { null } }
                ?.lowercase() ?: continue
            val id = known[raw] ?: known[raw.substringAfterLast('.')] ?: continue
            val x = fraction(number(o, "x", "posX", fallback = 0.5f))
            val y = fraction(number(o, "y", "posY", fallback = 0.5f))
            controls += Control(
                id = id,
                label = id,
                x = x,
                y = y,
                r = radius(o),
                kind = "builtin",
                icon = iconFor(id),
            )
        }
        return controls.ifEmpty { null }
    }

    /** Кладёт профиль в папку игры и включает его. */
    fun install(prefs: Prefs, name: String, controls: List<Control>): File {
        val dir = TouchProfiles.dir(prefs)
        dir.mkdirs()
        var file = File(dir, "$name.json")
        var n = 1
        while (file.exists()) file = File(dir, "$name-${n++}.json")
        TouchProfiles.write(file, file.nameWithoutExtension, controls)
        TouchProfiles.setActive(prefs, file.nameWithoutExtension)
        return file
    }

    /** Делится файлом профиля (мессенджеры, файловый менеджер, GitHub). */
    fun share(ctx: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, file.name))
    }
}
