package dev.moonmc.moon

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Привязка GitHub через Device Flow: приложение показывает короткий код,
 * пользователь подтверждает его в браузере, приложение получает токен само.
 */
object GithubAuth {
    /**
     * Публичный client_id GitHub CLI. Он не секретный (лежит в открытых исходниках
     * gh), но если GitHub его заблокирует — вписывай свой в поле «client_id».
     */
    const val DEFAULT_CLIENT_ID = "178c6fc778ccc68e1d6a"
    const val SCOPE = "repo workflow read:user"

    data class Device(
        val deviceCode: String,
        val userCode: String,
        val uri: String,
        val interval: Int,
        val expiresIn: Int,
    )

    data class Repo(
        val fullName: String,
        val private: Boolean,
        val description: String?,
        val updated: String,
    )

    /** Шаг 1: просим у GitHub пару кодов. */
    fun start(clientId: String): Device {
        val body = form(
            "client_id" to clientId,
            "scope" to SCOPE,
        )
        val json = JSONObject(postForm("https://github.com/login/device/code", body))
        return Device(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            uri = json.optString("verification_uri", "https://github.com/login/device"),
            interval = json.optInt("interval", 5),
            expiresIn = json.optInt("expires_in", 900),
        )
    }

    /**
     * Шаг 2: опрос. Возвращает токен, когда пользователь подтвердил код,
     * или null, пока ждём. Бросает исключение на реальные ошибки.
     */
    fun poll(clientId: String, deviceCode: String): String? {
        val body = form(
            "client_id" to clientId,
            "device_code" to deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
        )
        val json = JSONObject(postForm("https://github.com/login/oauth/access_token", body))
        json.optString("access_token").takeIf { it.isNotBlank() }?.let { return it }
        return when (json.optString("error")) {
            "", "authorization_pending", "slow_down" -> null
            "access_denied" -> error("доступ отклонён в браузере")
            "expired_token" -> error("код устарел — начни заново")
            else -> error(json.optString("error_description", json.optString("error")))
        }
    }

    /** Проверка токена: возвращает логин или бросает исключение. */
    fun login(token: String): String =
        JSONObject(Http.request("https://api.github.com/user", null, token)).getString("login")

    data class Me(val login: String, val name: String, val repos: Int)

    fun me(token: String): Me {
        val j = JSONObject(Http.request("https://api.github.com/user", null, token))
        return Me(j.getString("login"), j.optString("name"), j.optInt("public_repos"))
    }

    /** Репозитории пользователя (свои и где он соавтор), свежие сверху. */
    fun repos(token: String): List<Repo> {
        val raw = Http.request(
            "https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator",
            null,
            token,
        )
        val arr: JSONArray = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Repo(
                fullName = o.getString("full_name"),
                private = o.optBoolean("private"),
                description = o.optString("description").takeIf { it.isNotBlank() && it != "null" },
                updated = o.optString("updated_at").take(10),
            )
        }
    }

    private fun form(vararg pairs: Pair<String, String>) = pairs.joinToString("&") {
        URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
    }

    private fun postForm(url: String, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 30_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "Moon-launcher/0.6")
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("GitHub: HTTP $code")
        return text
    }
}
