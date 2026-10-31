package dev.moonmc.moon

import org.json.JSONArray
import org.json.JSONObject

/**
 * Modrinth. Готовые (26.x) берём как есть — ресурспаки, шейдеры, датапаки.
 * Java-моды под 26.3 не выпускаются, поэтому их разбираем и переносим данные
 * в контентный `.hmod` (см. [Analyze]).
 */
object Modrinth {
    private const val API = "https://api.modrinth.com/v2"

    enum class Kind(val facet: String, val title: String, val target: String) {
        RESOURCE_PACK("resourcepack", "Ресурспаки", "resourcepacks"),
        SHADER("shader", "Шейдеры", "shaderpacks"),
        DATAPACK("datapack", "Датапаки", "datapacks"),
        MOD("mod", "Моды", "horizon-mods"),
    }

    data class Hit(
        val id: String,
        val slug: String,
        val title: String,
        val description: String,
        val downloads: Long,
        val iconUrl: String?,
        val author: String,
        val categories: List<String>,
    )

    fun search(kind: Kind, query: String): List<Hit> {
        val facets = "[%5B%22project_type%3A${kind.facet}%22%5D]"
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val body = Http.get("$API/search?limit=30&index=relevance&query=$q&facets=$facets")
        val hits = JSONObject(body).optJSONArray("hits") ?: JSONArray()
        val out = ArrayList<Hit>(hits.length())
        for (i in 0 until hits.length()) {
            val h = hits.getJSONObject(i)
            val cats = h.optJSONArray("categories") ?: JSONArray()
            out.add(
                Hit(
                    id = h.getString("project_id"),
                    slug = h.optString("slug"),
                    title = h.getString("title"),
                    description = h.optString("description"),
                    downloads = h.optLong("downloads"),
                    iconUrl = h.optString("icon_url").ifBlank { null },
                    author = h.optString("author"),
                    categories = (0 until cats.length()).map { cats.getString(it) },
                )
            )
        }
        return out
    }

    data class File(val name: String, val url: String, val sha1: String, val size: Long, val version: String)

    /**
     * Файл для скачивания. Для паков — только версии, поддерживающие 26.x.
     * Для Java-модов — самая свежая сборка (её данные и переносим).
     */
    fun file(projectId: String, kind: Kind): File? {
        val arr = JSONArray(Http.get("$API/project/$projectId/version"))
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            if (kind != Kind.MOD) {
                val games = v.optJSONArray("game_versions")
                val fits = games == null || (0 until games.length()).any { games.getString(it).startsWith("26.") }
                if (!fits) continue
            }
            val files = v.optJSONArray("files") ?: continue
            for (j in 0 until files.length()) {
                val f = files.getJSONObject(j)
                if (!f.optBoolean("primary", false)) continue
                return File(
                    name = f.getString("filename"),
                    url = f.getString("url"),
                    sha1 = f.optJSONObject("hashes")?.optString("sha1").orEmpty(),
                    size = f.optLong("size"),
                    version = v.optString("version_number"),
                )
            }
        }
        return null
    }
}
