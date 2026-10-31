package dev.margymc.launcher

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Разбор Java-мода и перенос его данных в формат Horizon.
 *
 * Как это работает в самом Horizon (docs/mods.md, docs/modpacks.md):
 * - Java-код исполнить нельзя: у Horizon нет классов Minecraft;
 * - переносится «содержимое» — assets/ и data/ из jar, в виде контентного
 *   `.hmod` (api = "0.2", capability `registries`, docs/../content.rs);
 * - мод на оптимизацию в Horizon не нужен — движок сам заменяет Sodium/Lithium;
 * - чисто кодовые моды (библиотеки, машины, API) перенести нечем.
 */
object Analyze {

    enum class Level(val title: String, val short: String) {
        FULL("Полностью портируется", "полный порт"),
        PARTIAL("Наполовину портируется", "частично"),
        NOT_NEEDED("Не нужен в Horizon", "не нужен"),
        IMPOSSIBLE("Запустить нельзя", "только Java"),
    }

    data class Result(
        val level: Level,
        val classes: Int,
        val assets: Int,
        val dataFiles: Int,
        val mixins: Boolean,
        val natives: Int,
        val note: String,
    ) {
        val detail: String
            get() = buildString {
                append("${classes} классов · ${assets + dataFiles} файлов данных")
                if (mixins) append(" · миксины")
                if (natives > 0) append(" · $natives бинарников")
            }
    }

    /** Явные признаки модов на оптимизацию: в Horizon они не нужны. */
    private val performance = listOf(
        "sodium", "lithium", "iris", "starlight", "phosphor", "ferritecore", "krypton",
        "modernfix", "immediatelyfast", "embeddium", "memoryleakfix", "c2me", "noisium",
        "entityculling", "dynamic-fps", "fastanim", "fastload", "clumps", "debugify",
        "canary", "radium", "vulkanmod", "optifine", "sodiumextra", "indium", "reeses-sodium",
        "moreculling", "particle-core", "exordium", "async-locator", "chunkyshader",
    )

    fun analyze(jar: File, projectSlug: String, categories: List<String>): Result {
        var classes = 0
        var assets = 0
        var dataFiles = 0
        var natives = 0
        var mixins = false
        ZipFile(jar).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                when {
                    name.startsWith("META-INF/versions/") -> Unit
                    name.endsWith(".class") -> classes++
                    name.endsWith(".mixins.json") || name.endsWith(".mixin.json") -> mixins = true
                    name.startsWith("assets/") && !name.endsWith("/") -> assets++
                    name.startsWith("data/") && !name.endsWith("/") -> dataFiles++
                    name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib") -> natives++
                }
            }
        }

        val slug = projectSlug.lowercase()
        val performanceMod = performance.any { slug.contains(it) } ||
            categories.any { it.equals("optimization", true) } ||
            categories.any { it in setOf("performance", "optimization") }

        val level = when {
            performanceMod -> Level.NOT_NEEDED
            classes == 0 && (assets + dataFiles) > 0 -> Level.FULL
            classes == 0 -> Level.IMPOSSIBLE
            (assets + dataFiles) >= 16 && (assets + dataFiles) * 4 >= classes -> Level.PARTIAL
            else -> Level.IMPOSSIBLE
        }
        val note = when (level) {
            Level.FULL -> "кода нет — данные переносятся целиком"
            Level.PARTIAL -> "данные перенесём, поведение из кода — нет"
            Level.NOT_NEEDED -> "оптимизация: Horizon заменяет такие моды сам"
            Level.IMPOSSIBLE -> "почти всё — Java-код, переносить нечего"
        }
        return Result(level, classes, assets, dataFiles, mixins, natives, note)
    }

    data class Converted(val file: File, val files: Int, val skipped: Int, val namespaces: List<String>)

    private val segment = Regex("[a-z0-9_.-]+")
    private const val MAX_FILE = 16L shl 20          // 16 МиБ — лимит Horizon
    private const val MAX_TOTAL = 512L shl 20        // 512 МиБ — лимит Horizon
    private const val MAX_FILES = 65_536

    /**
     * Собирает контентный `.hmod`: манифест api 0.2 + assets/ и data/ из jar.
     * Файлы чужих неймспейсов (minecraft, c) объявляются в `namespaces`.
     */
    fun convert(
        jar: File,
        id: String,
        name: String,
        version: String,
        description: String,
        authors: String,
        out: File,
    ): Converted {
        val namespaces = sortedSetOf<String>()
        val entries = ArrayList<Pair<String, ZipEntry>>()
        var total = 0L
        var skipped = 0

        ZipFile(jar).use { zip ->
            val it = zip.entries()
            while (it.hasMoreElements()) {
                val e = it.nextElement()
                val path = e.name
                if (e.isDirectory) continue
                val content = path.startsWith("assets/") || path.startsWith("data/")
                if (!content) continue
                if (e.size > MAX_FILE) { skipped++; continue }
                val parts = path.split('/')
                if (parts.size < 3 || !parts.all { segment.matches(it) }) { skipped++; continue }
                total += e.size
                if (total > MAX_TOTAL || entries.size >= MAX_FILES) { skipped++; continue }
                namespaces.add(parts[1])
                entries.add(path to e)
            }

            out.parentFile?.mkdirs()
            ZipOutputStream(out.outputStream().buffered()).use { zos ->
                zos.putNextEntry(ZipEntry("horizon-mod.toml"))
                zos.write(manifest(id, name, version, description, authors, namespaces.toList()).toByteArray())
                zos.closeEntry()
                for ((path, e) in entries) {
                    zos.putNextEntry(ZipEntry(path))
                    zip.getInputStream(e).use { input -> input.copyTo(zos, 1 shl 16) }
                    zos.closeEntry()
                }
            }
        }
        return Converted(out, entries.size, skipped, namespaces.toList())
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun manifest(
        id: String,
        name: String,
        version: String,
        description: String,
        authors: String,
        namespaces: List<String>,
    ): String = buildString {
        appendLine("id = \"${escape(id)}\"")
        appendLine("name = \"${escape(name)}\"")
        appendLine("version = \"${escape(version)}\"")
        appendLine("description = \"${escape(description.take(300))}\"")
        appendLine("authors = [\"${escape(authors)}\"]")
        appendLine("api = \"0.2\"")
        appendLine("side = \"client\"")
        appendLine("capabilities = [\"registries\"]")
        // чужой неймспейс (minecraft, c) — это переопределение ванильных ресурсов
        val extra = namespaces.filter { it != id }
        if (extra.isNotEmpty()) {
            appendLine("namespaces = [${extra.joinToString(", ") { "\"${escape(it)}\"" }}]")
        }
    }
}
