package dev.moonmc.moon

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object Github {
    private const val API = "https://api.github.com"

    fun whoami(token: String): String =
        JSONObject(Http.request("$API/user", null, token)).getString("login")

    /** Создаёт приватный репозиторий, если его ещё нет. */
    fun ensurePrivateRepo(token: String, name: String, login: String): String {
        try {
            Http.request("$API/user/repos", """{"name":"$name","private":true,"auto_init":true}""", token)
        } catch (e: Exception) {
            // 422 = уже существует, это нормально
            if (!e.message.orEmpty().contains("422")) throw e
        }
        return "$login/$name"
    }

    /** Кладёт файл в репозиторий (Contents API), перезаписывая старый. */
    fun putFile(token: String, repo: String, path: String, content: String, message: String) {
        val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        val existing = try {
            JSONObject(Http.request("$API/repos/$repo/contents/$path", null, token)).getString("sha")
        } catch (_: Exception) {
            null
        }
        val body = buildString {
            append("{\"message\":\"$message\",\"content\":\"$encoded\"")
            if (existing != null) append(",\"sha\":\"$existing\"")
            append("}")
        }
        Http.request("$API/repos/$repo/contents/$path", body, token, "PUT")
    }

    data class Run(val id: Long, val status: String, val conclusion: String?)

    /** Есть ли такой репозиторий и доступен ли он этому токену. */
    fun repoExists(token: String, repo: String): Boolean =
        try {
            Http.request("$API/repos/$repo", null, token)
            true
        } catch (e: Exception) {
            !e.message.orEmpty().contains("404")
        }

    /** Файл в каталоге репозитория. */
    data class Entry(val name: String, val size: Long, val sha: String)

    /** Содержимое каталога репозитория (пусто, если каталога нет). */
    fun listDir(token: String, repo: String, path: String): List<Entry> {
        val text = try {
            Http.request("$API/repos/$repo/contents/$path", null, token)
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("404")) return emptyList()
            throw e
        }
        val arr = try {
            org.json.JSONArray(text)
        } catch (_: Exception) {
            return emptyList()
        }
        val out = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("type") != "file") continue
            out += Entry(o.getString("name"), o.optLong("size"), o.optString("sha"))
        }
        return out.sortedBy { it.name.lowercase() }
    }

    /** Кладёт двоичный файл (например .hnmod) в репозиторий, перезаписывая старый. */
    fun putBytes(token: String, repo: String, path: String, bytes: ByteArray, message: String) {
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val existing = try {
            JSONObject(Http.request("$API/repos/$repo/contents/$path", null, token)).getString("sha")
        } catch (_: Exception) {
            null
        }
        val body = buildString {
            append("{\"message\":\"$message\",\"content\":\"$encoded\"")
            if (existing != null) append(",\"sha\":\"$existing\"")
            append("}")
        }
        Http.request("$API/repos/$repo/contents/$path", body, token, "PUT")
    }

    fun dispatch(token: String, repo: String, workflow: String, inputs: Map<String, String>) {
        val body = JSONObject().put("ref", "main").put("inputs", JSONObject(inputs as Map<*, *>)).toString()
        Http.request("$API/repos/$repo/actions/workflows/$workflow/dispatches", body, token)
    }

    fun latestRun(token: String, repo: String, workflow: String): Run? {
        val root = JSONObject(Http.request("$API/repos/$repo/actions/workflows/$workflow/runs?per_page=1", null, token))
        val arr = root.optJSONArray("workflow_runs") ?: return null
        if (arr.length() == 0) return null
        val r = arr.getJSONObject(0)
        return Run(r.getLong("id"), r.optString("status"), r.optString("conclusion").ifBlank { null })
    }

    /** Ссылка на скачивание артефакта (нужен токен: приватный репозиторий). */
    fun artifactUrl(token: String, repo: String, runId: Long, name: String): String? {
        val root = JSONObject(Http.request("$API/repos/$repo/actions/runs/$runId/artifacts", null, token))
        val arr = root.optJSONArray("artifacts") ?: return null
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            if (a.getString("name") == name) return a.getString("archive_download_url")
        }
        return null
    }
}

object Install {
    fun digest(f: File, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        f.inputStream().use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = s.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(f: File) = digest(f, "SHA-256")
    fun sha1(f: File) = digest(f, "SHA-1")

    /** Скачивает файл и сверяет хеш из каталога. */
    fun downloadVerified(url: String, target: File, hash: String, algorithm: String = "SHA-256") {
        if (hash.isBlank()) error("в каталоге нет хеша файла")
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        Http.download(url, tmp)
        val got = digest(tmp, algorithm)
        if (!got.equals(hash, ignoreCase = true)) {
            tmp.delete()
            error("$algorithm не совпал: ожидали ${hash.take(12)}…, получили ${got.take(12)}…")
        }
        tmp.renameTo(target)
    }

    /** Устанавливает APK через системный установщик. */
    fun installApk(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun openUrl(ctx: Context, url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
