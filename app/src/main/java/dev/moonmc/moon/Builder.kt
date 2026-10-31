package dev.moonmc.moon

import android.content.Context
import kotlinx.coroutines.delay
import java.io.File

/**
 * Сборка APK с нативными модами через GitHub Actions — на телефоне ничего компилировать не нужно.
 * 1) готовим приватный репозиторий и рецепты (workflow и вспомогательные скрипты)
 * 2) отправляем workflow с пинами выбранных модов
 * 3) ждём прогон, скачиваем артефакт и отдаём APK установщику
 */
class Builder(
    private val ctx: Context,
    private val prefs: Prefs,
    private val log: (String) -> Unit,
    private val progress: (Float?) -> Unit,
) {
    private val token get() = prefs.token
    private val repo get() = prefs.repo

    suspend fun build(modUrls: List<String>, nativePin: String? = null, mixinsRev: String? = null) {
        require(token.isNotBlank()) { "нет токена GitHub" }
        progress(null); log("Проверяю доступ к GitHub…")
        Github.whoami(token)

        if (repo.isBlank()) {
            // рецепты сборки и архив Minecraft уже лежат в этом приватном репозитории
            prefs.repo = "Haruxa/moonmc-apk"
        }
        log("Репозиторий: ${prefs.repo}")

        log("Обновляю рецепты сборки…")
        runCatching { uploadRecipe() }.onFailure { log("рецепты уже в репозитории: ${it.message}") }
        progress(0.02f)

        log("Запускаю сборку (${modUrls.size} нативных мод(ов))…")
        Github.dispatch(
            token, prefs.repo, "apk.yml",
            mapOf(
                "profile" to "fast",
                "mods" to "true",
                "home_on_sdcard" to "true",
                "native_pin" to (nativePin ?: ""),
                "mixins_rev" to (mixinsRev ?: ""),
                "mod_urls" to modUrls.joinToString("\n"),
            ),
        )

        val runId = awaitRun()
        log("Прогон: https://github.com/${prefs.repo}/actions/runs/$runId")
        progress(0.05f)

        log("Компилирую… это может занять 20–40 минут")
        val conclusion = awaitConclusion(runId)
        if (conclusion != "success") error("сборка закончилась со статусом $conclusion")

        progress(0.97f)
        log("Скачиваю APK…")
        val url = Github.artifactUrl(token, prefs.repo, runId, "horizon-apk")
            ?: error("в прогоне нет артефакта horizon-apk")
        val zip = File(ctx.cacheDir, "horizon-$runId.zip")
        Http.download(url, zip, token)
        val apk = unzipApk(zip)
        progress(1f)
        log("Готово: ${apk.name} (${apk.length() / 1024 / 1024} МБ)")
        Install.installApk(ctx, apk)
    }

    private fun uploadRecipe() {
        val assets = ctx.assets
        fun text(name: String) = assets.open("ci/$name").bufferedReader().readText()
        Github.putFile(token, repo, ".github/workflows/apk.yml", text("apk.yml"), "рецепт сборки")
        Github.putFile(token, repo, "ci/build-apk-modded.sh", text("build-apk-modded.sh"), "скрипт сборки")
        Github.putFile(token, repo, "ci/patch-game-home.py", text("patch-game-home.py"), "патч папки игры")
    }

    private suspend fun awaitRun(): Long {
        repeat(30) {
            delay(3_000)
            val r = Github.latestRun(token, repo, "apk.yml")
            if (r != null) return r.id
        }
        error("GitHub не показал отправленный прогон")
    }

    private suspend fun awaitConclusion(runId: Long): String {
        var lastLog = 0L
        repeat(240) {
            delay(15_000)
            val r = Github.latestRun(token, repo, "apk.yml") ?: return@repeat
            if (r.id == runId && r.status == "completed") return r.conclusion ?: "unknown"
            // раз в минуту напоминаем, что живы
            val now = System.currentTimeMillis()
            if (now - lastLog > 60_000) {
                lastLog = now
                log("…сборка идёт (${r.status})")
            }
        }
        error("прогон не завершился за отведённое время")
    }

    private fun unzipApk(zip: File): File {
        val out = File(ctx.cacheDir, "apk")
        out.mkdirs()
        java.util.zip.ZipFile(zip).use { zf ->
            zf.entries().asSequence().filter { it.name.endsWith(".apk") }.forEach { e ->
                val target = File(out, e.name.substringAfterLast('/'))
                zf.getInputStream(e).use { input -> target.outputStream().use { input.copyTo(it) } }
                return target
            }
        }
        error("в архиве не нашёлся APK")
    }
}
