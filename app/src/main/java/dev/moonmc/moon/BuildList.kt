package dev.moonmc.moon

import org.json.JSONArray
import org.json.JSONObject

/**
 * Список сборки: какие моды вшить в APK. Живёт в настройках (JSON), чтобы порядок,
 * выключение и удаление переживали перезапуск лаунчера.
 *
 * Мод бывает двух видов:
 *  - бандл из нашего репозитория (`ci/bundles/<bundle>.hnmod`) — тогда [bundle] не пустой;
 *  - файл из каталога Horizon по ссылке — тогда [url] не пустой.
 */
data class BuildEntry(
    val id: String,
    val title: String,
    val pin: String = "",
    val mixins: String = "",
    val bundle: String = "",
    val url: String = "",
    val enabled: Boolean = true,
) {
    val subtitle: String
        get() = when {
            bundle.isNotEmpty() -> "ci/bundles/$bundle.hnmod"
            url.isNotEmpty() -> url.substringAfter("//").take(48)
            else -> "—"
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("pin", pin)
        .put("mixins", mixins)
        .put("bundle", bundle)
        .put("url", url)
        .put("enabled", enabled)

    companion object {
        fun from(json: JSONObject) = BuildEntry(
            id = json.optString("id"),
            title = json.optString("title"),
            pin = json.optString("pin"),
            mixins = json.optString("mixins"),
            bundle = json.optString("bundle"),
            url = json.optString("url"),
            enabled = json.optBoolean("enabled", true),
        )
    }
}

object BuildList {
    fun load(prefs: Prefs): List<BuildEntry> {
        val raw = prefs.buildList
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { BuildEntry.from(arr.getJSONObject(i)) }.getOrNull()
            }.filter { it.id.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun save(prefs: Prefs, entries: List<BuildEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        prefs.buildList = arr.toString()
    }

    /** Добавляет мод, если его ещё нет (по [BuildEntry.id]). Возвращает новый список. */
    fun add(prefs: Prefs, entry: BuildEntry): List<BuildEntry> {
        val now = load(prefs).filterNot { it.id == entry.id } + entry
        save(prefs, now)
        return now
    }

    fun remove(prefs: Prefs, id: String): List<BuildEntry> {
        val now = load(prefs).filterNot { it.id == id }
        save(prefs, now)
        return now
    }

    fun toggle(prefs: Prefs, id: String): List<BuildEntry> {
        val now = load(prefs).map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        save(prefs, now)
        return now
    }

    fun move(prefs: Prefs, id: String, up: Boolean): List<BuildEntry> {
        val now = load(prefs).toMutableList()
        val from = now.indexOfFirst { it.id == id }
        if (from < 0) return now
        val to = if (up) from - 1 else from + 1
        if (to !in now.indices) return now
        val item = now.removeAt(from)
        now.add(to, item)
        save(prefs, now)
        return now
    }

    fun clear(prefs: Prefs) = save(prefs, emptyList())

    /** Пин движка, общий для включённых модов (пусто — если пины различаются). */
    fun commonPin(entries: List<BuildEntry>): String =
        entries.filter { it.enabled && it.pin.isNotBlank() }.map { it.pin }.distinct().singleOrNull()
            ?: ""

    fun distinctPins(entries: List<BuildEntry>): List<String> =
        entries.filter { it.enabled && it.pin.isNotBlank() }.map { it.pin }.distinct()
}
