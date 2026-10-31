package dev.moonmc.moon

import org.json.JSONArray

/**
 * Движок: пин коммита Horizon, на котором собирается APK.
 *
 * Horizon выпускает ночные сборки (Nightly), и каждый ночник навсегда закреплён
 * тегом `nightly-source/<дата>-<короткий sha>` — по нему любой пин переводится в
 * дату. Это единственный надёжный способ понять, «насколько новый» движок:
 * сам main двигается на тысячи коммитов в сутки.
 */
object Engine {
    private const val REPO = "horizon-rust/horizon"

    data class Nightly(val date: String, val sha: String) {
        /** `Nightly 2026-10-31 · aa27eca` */
        val label: String get() = "Nightly $date · $sha"
    }

    private val tagPrefix = "refs/tags/nightly-source/"

    /** Все известные ночники, от новых к старым. */
    fun nightlies(token: String? = null): List<Nightly> {
        val text = Http.request(
            "https://api.github.com/repos/$REPO/git/matching-refs/tags/nightly-source",
            null,
            token,
        )
        val arr = JSONArray(text)
        val out = ArrayList<Nightly>(arr.length())
        for (i in 0 until arr.length()) {
            val ref = arr.getJSONObject(i).optString("ref")
            if (!ref.startsWith(tagPrefix)) continue
            // 2026-10-31-aa27eca
            val rest = ref.removePrefix(tagPrefix)
            val date = rest.take(10)
            val sha = rest.substringAfterLast('-')
            if (date.length == 10 && sha.length >= 7) out += Nightly(date, sha)
        }
        return out.sortedWith(compareByDescending<Nightly> { it.date }.thenByDescending { it.sha })
    }

    /** Матрица совместимости модов с пином: ci/compat/<pin7>.json из репозитория сборки. */
    data class Compat(
        val pin: String,
        val engine: String,
        val generated: String,
        val results: Map<String, Result>,
    ) {
        data class Result(val ok: Boolean, val stage: String, val note: String)

        fun of(modId: String): Result? = results[modId]
    }

    private const val COMPAT_BASE =
        "https://raw.githubusercontent.com/Haruxa/moonmc-apk/main/ci/compat"

    /** null — матрицы для этого пина ещё нет. */
    fun compat(pin: String, token: String? = null): Compat? {
        if (pin.isBlank()) return null
        val text = try {
            Http.request("$COMPAT_BASE/${pin.take(7)}.json", null, token)
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("404")) return null
            throw e
        }
        val root = org.json.JSONObject(text)
        val arr = root.optJSONArray("mods") ?: return null
        val map = LinkedHashMap<String, Compat.Result>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            if (id.isBlank()) continue
            map[id] = Compat.Result(
                ok = o.optBoolean("ok", false),
                stage = o.optString("stage"),
                note = o.optString("note"),
            )
        }
        return Compat(
            pin = root.optString("pin"),
            engine = root.optString("engine"),
            generated = root.optString("generated"),
            results = map,
        )
    }

    /** Полный sha коммита по короткому (пин должен быть полным: его сверяют манифесты модов). */
    fun resolve(token: String?, ref: String): String =
        org.json.JSONObject(
            Http.request("https://api.github.com/repos/$REPO/commits/$ref", null, token)
        ).getString("sha")

    /** Ночник, к которому относится [pin] (или null, если коммит не из ночников). */
    fun nightlyOf(pin: String, all: List<Nightly>): Nightly? {
        if (pin.isBlank()) return null
        val short = pin.take(7)
        return all.firstOrNull { it.sha.startsWith(short) || pin.startsWith(it.sha) }
    }

    /** Подпись для поля «Пин движка»: `Nightly 2026-10-28` или `commit 6f8c7a0`. */
    fun label(pin: String, all: List<Nightly>): String {
        if (pin.isBlank()) return "upstream main"
        val nightly = nightlyOf(pin, all)
        return nightly?.let { "Nightly ${it.date}" } ?: "commit ${pin.take(7)}"
    }

    /** Ночники новее, чем [pin]: по дате, если пин известен. */
    fun newerThan(pin: String, all: List<Nightly>, limit: Int = 8): List<Nightly> {
        if (pin.isBlank()) return all.take(limit)
        val current = nightlyOf(pin, all) ?: return all.take(limit)
        return all.filter { it.date > current.date && it.sha != current.sha }.take(limit)
    }
}
