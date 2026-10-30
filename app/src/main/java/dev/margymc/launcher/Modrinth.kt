package dev.margymc.launcher

import org.json.JSONArray
import org.json.JSONObject

/**
 * Modrinth: берём только то, что Horizon реально понимает —
 * ресурспаки, шейдерпаки и датапаки. Java-моды (.jar) не поддерживаются.
 */
object Modrinth {
    private const val API = "https://api.modrinth.com/v2"

    enum class Kind(val facet: String, val title: String, val target: String) {
        RESOURCE_PACK("resourcepack", "Ресурспаки", "resourcepacks"),
        SHADER("shader", "Шейдеры", "shaderpacks"),
        DATAPACK("datapack", "Датапаки", "datapacks"),
    }

    data class Hit(
        val id: String,
        val title: String,
        val description: String,
        val downloads: Long,
        val iconUrl: String?,
        val author: String,
    )

    fun search(kind: Kind, query: String): List<Hit> {
        val facets = "[%5B%22project_type%3A${kind.facet}%22%5D]"
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val body = Http.get("$API/search?limit=30&index=relevance&query=$q&facets=$facets")
        val hits = JSONObject(body).optJSONArray("hits") ?: JSONArray()
        val out = ArrayList<Hit>()
        for (i in 0 until hits.length()) {
            val h = hits.getJSONObject(i)
            out.add(
                Hit(
                    id = h.getString("project_id"),
                    title = h.getString("title"),
                    description = h.optString("description"),
                    downloads = h.optLong("downloads"),
                    iconUrl = h.optString("icon_url").ifBlank { null },
                    author = h.optString("author"),
                )
            )
        }
        return out
    }

    data class File(val name: String, val url: String, val sha1: String, val size: Long)

    /** Главный файл последней версии, подходящей под 26.3. */
    fun file(projectId: String): File? {
        val arr = JSONArray(Http.get("$API/project/$projectId/version"))
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            val games = v.optJSONArray("game_versions")
            val fits = games == null || (0 until games.length()).any { games.getString(it).startsWith("26.") }
            if (!fits) continue
            val files = v.optJSONArray("files") ?: continue
            for (j in 0 until files.length()) {
                val f = files.getJSONObject(j)
                if (!f.optBoolean("primary", false)) continue
                return File(
                    name = f.getString("filename"),
                    url = f.getString("url"),
                    sha1 = f.optJSONObject("hashes")?.optString("sha1").orEmpty(),
                    size = f.optLong("size"),
                )
            }
        }
        return null
    }
}
