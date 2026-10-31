package dev.moonmc.moon

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Быстрый анализ нативного мода (`.hnmod`) под выбранный пин движка — без сборки.
 *
 * Сборка даёт точный ответ, но стоит минуты. Анализ стоит десяток HTTP-запросов и
 * ловит то, что ломается чаще всего:
 *  - цепляется ли мод к функциям, которые в этом пине есть (по `[[injection]] target`);
 *  - на месте ли модуль мода (`[[module]] file` + `fn name`);
 *  - лежат ли helper-файлы внутри бандла;
 *  - совпадает ли заявленный модом пин с выбранным.
 *
 * Итог: OK (ничего не сломано по формальным признакам), BROKEN (видно, что не соберётся)
 * или UNKNOWN (не смогли достать исходники движка — например, нет сети).
 */
object NativeAnalyze {
    enum class Verdict { OK, BROKEN, UNKNOWN }

    data class Check(val name: String, val ok: Boolean, val detail: String = "")

    data class Result(
        val modId: String,
        val version: String,
        val pinned: String,
        val mixins: String,
        val checks: List<Check>,
    ) {
        val verdict: Verdict
            get() = when {
                checks.any { !it.ok && it.name != "пин" } -> Verdict.BROKEN
                checks.none { it.name == "цели хуков" || it.name == "модуль" } -> Verdict.UNKNOWN
                else -> Verdict.OK
            }

        val problems: List<Check> get() = checks.filter { !it.ok }
        /** Коротко: длинные пути в интерфейсе не нужны. */
        val summary: String
            get() = when (verdict) {
                Verdict.OK -> "анализ: всё на месте"
                Verdict.BROKEN -> "анализ: " + problems.first().let { it.detail }.let { detail ->
                    val short = detail.substringAfterLast('/').ifBlank { detail }
                    if (short.length > 60) short.take(59) + "…" else short
                }
                Verdict.UNKNOWN -> "анализ: нужны исходники движка"
            }
    }

    private const val RAW = "https://raw.githubusercontent.com/horizon-rust/horizon"
    private val files = HashMap<String, String>()

    fun analyze(
        url: String,
        modId: String,
        version: String,
        pin: String,
        token: String? = null,
    ): Result {
        val checks = mutableListOf<Check>()
        val bundle = unzip(Http.bytes(url, token))
        val meta = bundle["horizon-native.json"]?.let { runCatching { JSONObject(String(it)) }.getOrNull() }
        val declared = meta?.optString("horizon").orEmpty()
        val mixins = meta?.optString("mixins").orEmpty()

        checks += if (declared.isNotBlank() && pin.isNotBlank() && !pin.startsWith(declared.take(7))) {
            Check("пин", false, "мод собран под ${declared.take(7)}, выбран ${pin.take(7)}")
        } else {
            Check("пин", true, declared.take(7))
        }

        val manifests = bundle.entries.filter { it.key.endsWith("horizon-mixin.toml") }
        if (manifests.isEmpty()) {
            checks += Check("манифест", false, "в бандле нет horizon-mixin.toml")
            return Result(modId, version, declared, mixins, checks)
        }

        var targetsChecked = 0
        var targetsBad = 0
        val missing = mutableListOf<String>()
        for ((name, bytes) in manifests) {
            val parsed = parseManifest(String(bytes))
            // helper-файлы должны лежать рядом с манифестом
            val dir = name.substringBeforeLast('/')
            for (helper in parsed.helpers) {
                if (bundle.keys.none { it == "$dir/$helper" || it.endsWith("/$helper") }) {
                    checks += Check("helper", false, "$name: нет $helper")
                }
            }
            // модуль мода: файл движка + имя функции
            for (module in parsed.modules) {
                val text = source(module.file, pin, token)
                if (text == null) {
                    checks += Check("модуль", false, "не прочитал ${module.file}")
                    continue
                }
                val ok = Regex("""\bfn\s+${Regex.escape(module.name)}\s*[<(]""").containsMatchIn(text)
                checks += Check("модуль", ok, "${module.file}: fn ${module.name}")
            }
            // цели хуков
            for (target in parsed.targets) {
                targetsChecked++
                val fn = target.substringAfterLast("::")
                val path = modulePathOf(target)
                if (path == null) {
                    targetsBad++
                    missing += "$target (не понял путь)"
                    continue
                }
                val text = source(path, pin, token)
                if (text == null) {
                    targetsBad++
                    missing += "$path (нет файла)"
                    continue
                }
                val ok = Regex("""\bfn\s+${Regex.escape(fn)}\s*[<(]""").containsMatchIn(text)
                if (!ok) {
                    targetsBad++
                    missing += "$fn (нет в $path)"
                }
            }
        }
        checks += Check(
            "цели хуков",
            targetsBad == 0 && targetsChecked > 0,
            if (targetsBad == 0) "$targetsChecked целей на месте"
            else missing.take(3).joinToString("; "),
        )
        return Result(modId, version, declared, mixins, checks)
    }

    /* ------------------------------- разбор ------------------------------- */

    private class Manifest(
        val targets: List<String>,
        val helpers: List<String>,
        val modules: List<Module>,
    ) {
        class Module(val file: String, val name: String)
    }

    /** Очень простой TOML: только строки `key = "value"` внутри блоков. */
    private fun parseManifest(text: String): Manifest {
        val targets = mutableListOf<String>()
        val helpers = mutableListOf<String>()
        val modules = mutableListOf<Manifest.Module>()
        var block = ""
        var file = ""
        var name = ""
        fun flush() {
            if (block == "module" && file.isNotBlank() && name.isNotBlank()) {
                modules += Manifest.Module(file, name)
            }
            file = ""
            name = ""
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("[[")) {
                flush()
                block = line.removePrefix("[[").substringBefore("]]").trim()
                continue
            }
            if (line.startsWith("[")) {
                flush()
                block = ""
                continue
            }
            val key = line.substringBefore('=').trim()
            val value = line.substringAfter('=', "").trim().trim('"')
            when {
                block == "injection" && key == "target" -> targets += value
                block == "injection" && key == "helper" -> helpers += value
                block == "module" && key == "file" -> file = value
                block == "module" && key == "name" -> name = value
                block == "module" && key == "source" -> helpers += value
            }
        }
        flush()
        return Manifest(targets, helpers.distinct(), modules)
    }

    /**
     * `horizon_client::game::native_hooks::Game<'_>::native_mods`
     * → `crates/horizon-client/src/game/native_hooks.rs`
     */
    private fun modulePathOf(target: String): String? {
        val segments = target.split("::")
        if (segments.size < 2) return null
        val crate = segments.first().trimStart('&').trim()
        val dir = "crates/" + crate.replace('_', '-')
        val modules = segments.drop(1).dropLast(1)
            .map { it.trim() }
            .filter { Regex("""^[a-z_][a-z0-9_]*$""").matches(it) }
        if (modules.isEmpty()) return null
        return "$dir/src/" + modules.joinToString("/") + ".rs"
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                out[entry.name] = zip.readBytes()
            }
        }
        return out
    }

    /** Исходник движка на пине (кэш в памяти: один файл — один запрос). */
    private fun source(path: String, pin: String, token: String?): String? {
        if (pin.isBlank()) return null
        val key = "$pin/$path"
        files[key]?.let { return it }
        val text = runCatching { Http.request("$RAW/$pin/$path", null, token) }.getOrNull() ?: return null
        files[key] = text
        return text
    }
}
