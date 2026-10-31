package dev.margymc.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Официальный каталог Horizon: index.json с GitHub Pages. */
object HorizonIndex {
    const val URL_INDEX = "https://horizon-rust.github.io/horizon-mods/index.json"

    data class Version(
        val version: String,
        val game: String,
        val api: String,
        val fileName: String,
        val fileUrl: String,
        val size: Long,
        val sha256: String,
    ) {
        /** Для нативных модов поле api = native-1/<пин движка>/<пин миксинов>. */
        val nativePin: String? get() = api.split('/').getOrNull(1)
        val mixinsPin: String? get() = api.split('/').getOrNull(2)
    }

    data class Project(
        val id: String,
        val kind: String,
        val name: String,
        val summary: String,
        val authors: List<String>,
        val icon: String?,
        val source: String,
        val versions: List<Version>,
    ) {
        val isNative get() = kind == "native-mod"
        val latest get() = versions.firstOrNull()
    }

    fun load(): List<Project> {
        val body = Http.get(URL_INDEX)
        val root = JSONObject(body)
        val out = ArrayList<Project>()
        val projects = root.optJSONArray("projects") ?: return out
        for (i in 0 until projects.length()) {
            val p = projects.getJSONObject(i)
            val vs = ArrayList<Version>()
            val arr = p.optJSONArray("versions") ?: continue
            for (j in 0 until arr.length()) {
                val v = arr.getJSONObject(j)
                val f = v.optJSONObject("file") ?: continue
                vs.add(
                    Version(
                        version = v.optString("version"),
                        game = v.optString("game"),
                        api = v.optString("api"),
                        fileName = f.optString("name"),
                        fileUrl = f.optString("url"),
                        size = f.optLong("size"),
                        sha256 = f.optString("sha256"),
                    )
                )
            }
            out.add(
                Project(
                    id = p.optString("id"),
                    kind = p.optString("kind"),
                    name = p.optString("name"),
                    summary = p.optString("summary"),
                    authors = p.optJSONArray("authors")?.let { a ->
                        (0 until a.length()).map { a.getString(it) }
                    } ?: emptyList(),
                    icon = p.optString("icon").ifBlank { null },
                    source = p.optString("source"),
                    versions = vs,
                )
            )
        }
        return out
    }
}

object Http {
    fun get(url: String): String = request(url, null, null)
    fun post(url: String, body: String, token: String?): String =
        request(url, body, token, "POST")

    fun request(
        url: String,
        body: String?,
        token: String?,
        method: String = if (body == null) "GET" else "POST",
    ): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 30_000
        c.readTimeout = 60_000
        c.requestMethod = method
        c.setRequestProperty("User-Agent", "MargyMC-launcher/0.1")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) error("HTTP $code: ${text.take(300)}")
        return text
    }

    fun download(url: String, target: File, token: String? = null) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 30_000
        c.readTimeout = 120_000
        c.setRequestProperty("User-Agent", "MargyMC-launcher/0.1")
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
    }
}

/** Куда ставим файлы и что помним о пользователе. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var token: String
        get() = p.getString("token", "") ?: ""
        set(v) = p.edit().putString("token", v).apply()

    var repo: String
        get() = p.getString("repo", "") ?: ""
        set(v) = p.edit().putString("repo", v).apply()

    /** GitHub-логин, к которому привязан токен (пусто = не привязан). */
    var ghLogin: String
        get() = p.getString("gh_login", "") ?: ""
        set(v) = p.edit().putString("gh_login", v).apply()

    /** Свой OAuth client_id, если стандартный не подходит. */
    var ghClientId: String
        get() = p.getString("gh_client_id", "") ?: ""
        set(v) = p.edit().putString("gh_client_id", v).apply()

    val clientId get() = ghClientId.ifBlank { GithubAuth.DEFAULT_CLIENT_ID }

    /** Язык интерфейса: "en" по умолчанию, "ru" — русский. */
    var lang: String
        get() = p.getString("lang", "en") ?: "en"
        set(v) = p.edit().putString("lang", v).apply()

    /** Папка игры (та же, что у MargyMC на /sdcard). */
    var home: String
        get() = p.getString("home", "/sdcard/MargyMC/horizon") ?: "/sdcard/MargyMC/horizon"
        set(v) = p.edit().putString("home", v).apply()

    val modsDir get() = File(home, "horizon-mods")

    /** Папка, куда лаунчер клонирует/скачивает гит-репозитории. */
    val gitDir get() = File(home, "git")
    val resourcePacksDir get() = File(home, "resourcepacks")
    val shaderPacksDir get() = File(home, "shaderpacks")
    val downloadsDir get() = File(home, "launcher")
}
