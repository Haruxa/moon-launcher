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
