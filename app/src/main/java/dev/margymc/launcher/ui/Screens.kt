package dev.margymc.launcher.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.margymc.launcher.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val PagePadding = 20.dp

/* ------------------------------- Моды Horizon ------------------------------- */

@Composable
fun HorizonScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<HorizonIndex.Project>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var showNative by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { HorizonIndex.load().sortedBy { it.name.lowercase() } }
                .onSuccess { projects = it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    val visible = projects.filter { showNative || !it.isNative }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Моды Horizon", "Каталог модов и нативных расширений для игры 26.3")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !showNative, onClick = { showNative = false }, label = { Text("Готовые") })
                FilterChip(selected = showNative, onClick = { showNative = true }, label = { Text("Нативные") })
                Spacer(Modifier.weight(1f))
                if (loading) MorphLoading()
            }
        }
        status?.let { item { StatusPill(it, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) } }
        error?.let { item { StatusPill("Ошибка: $it", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) } }

        items(visible, key = { it.id }) { p ->
            ShapeCard(polygon = Morf.Cookie9) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    p.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    p.latest?.let { v ->
                        StatusPill("v${v.version} · ${v.game}")
                    }
                    Spacer(Modifier.weight(1f))
                    if (!p.isNative) {
                        ExpressiveButton("Установить", Icons.Rounded.Download) {
                            scope.launch {
                                status = "${p.name}: скачиваю…"
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val v = p.latest ?: error("нет версии для 26.3")
                                        Install.downloadVerified(v.fileUrl, File(prefs.modsDir, v.fileName), v.sha256)
                                    }
                                }.onSuccess { status = "${p.name}: установлен" }
                                    .onFailure { status = "${p.name}: ${it.message}" }
                            }
                        }
                    } else {
                        ExpressiveButton("Собрать APK", Icons.Rounded.Build) {
                            scope.launch {
                                val v = p.latest
                                if (v == null) {
                                    status = "${p.name}: нет версии"
                                } else if (prefs.token.isBlank()) {
                                    status = "Нужен токен GitHub — вкладка «Ещё»"
                                } else {
                                    status = "${p.name}: запускаю сборку…"
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            Builder(
                                                ctx, prefs,
                                                log = { status = it },
                                                progress = { _ -> },
                                            ).build(listOf(v.fileUrl), v.nativePin, v.mixinsPin)
                                        }
                                    }.onFailure { status = "Ошибка: ${it.message}" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* --------------------------------- Modrinth -------------------------------- */

@Composable
fun ModrinthScreen(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(Modrinth.Kind.RESOURCE_PACK) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Modrinth.Hit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val analyzed = remember { mutableStateMapOf<String, Analyze.Result>() }
    val jars = remember { mutableStateMapOf<String, File>() }

    fun refresh() {
        scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { Modrinth.search(kind, query) } }
                .onSuccess { hits = it }
                .onFailure { status = it.message }
            busy = false
        }
    }
    LaunchedEffect(kind) { refresh() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Modrinth", "Пакеты — как есть, Java-моды переносим данными в .hmod")
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Modrinth.Kind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.title) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { refresh() }) { Icon(Icons.Rounded.Search, null) }
            }
        }
        status?.let { item { StatusPill(it) } }
        if (busy) item { WaveProgress({ 0.35f }) }

        items(hits, key = { it.id }) { h ->
            val res = analyzed[h.id]
            ShapeCard(polygon = Morf.Cookie9) {
                Text(
                    h.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    h.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (kind == Modrinth.Kind.MOD) {
                        when {
                            res == null -> StatusPill("нужен анализ")
                            else -> CompatBadge(res.level)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${h.downloads} · ${h.author}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (res != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${res.detail} — ${res.note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind == Modrinth.Kind.MOD) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    status = "${h.title}: качаю jar для анализа…"
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val f = Modrinth.file(h.id, kind) ?: error("нет файла мода")
                                            val jar = jars[h.id] ?: File(
                                                ctx.cacheDir,
                                                "jars/${h.slug.ifBlank { h.id }}-${f.version}.jar",
                                            ).also { Install.downloadVerified(f.url, it, f.sha1, "SHA-1") }
                                            jars[h.id] = jar
                                            Analyze.analyze(jar, h.slug.ifBlank { h.id }, h.categories)
                                                .also { analyzed[h.id] = it }
                                        }
                                        status = "${h.title}: ${analyzed[h.id]?.level?.title}"
                                    }.onFailure { status = "${h.title}: ${it.message}" }
                                }
                            },
                        ) { Text("Анализ") }

                        if (res != null && res.level != Analyze.Level.IMPOSSIBLE && res.level != Analyze.Level.NOT_NEEDED) {
                            ExpressiveButton("В .hmod", Icons.Rounded.Build) {
                                scope.launch {
                                    status = "${h.title}: собираю .hmod…"
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val jar = jars[h.id] ?: error("сначала анализ")
                                            val f = Modrinth.file(h.id, kind)
                                            val id = h.slug.ifBlank { h.id }
                                            Analyze.convert(
                                                jar = jar,
                                                id = id,
                                                name = h.title,
                                                version = f?.version ?: "1.0",
                                                description = h.description,
                                                authors = h.author,
                                                out = File(prefs.modsDir, "$id.hmod"),
                                            )
                                        }
                                    }.onSuccess { c ->
                                        status = "${h.title}: ${c.files} файлов, пропущено ${c.skipped} → ${c.file.name}"
                                    }.onFailure { status = "${h.title}: ${it.message}" }
                                }
                            }
                        }
                    } else {
                        ExpressiveButton("Ставить", Icons.Rounded.Download) {
                            scope.launch {
                                status = "${h.title}: скачиваю…"
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val f = Modrinth.file(h.id, kind) ?: error("нет сборки под 26.x")
                                        val dir = File(prefs.home, kind.target)
                                        Install.downloadVerified(f.url, File(dir, f.name), f.sha1, "SHA-1")
                                    }
                                }.onSuccess { status = "${h.title}: установлено в ${kind.target}" }
                                    .onFailure { status = "${h.title}: ${it.message}" }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------------------------- Сборка --------------------------------- */

@Composable
fun BuildScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf(listOf<String>()) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var running by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Сборка APK", "Нативные моды (.hnmod) вшиваются в игру в GitHub Actions")
        }
        item {
            ShapeCard(polygon = Morf.Sunny) {
                Text("Как это работает", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Лаунчер кладёт рецепт сборки в твой приватный репозиторий, запускает " +
                        "GitHub Actions и через 20–40 минут присылает готовый APK. " +
                        "На телефоне ничего не компилируется.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ExpressiveButton(
                text = if (running) "Собирается…" else "Собрать APK с модами",
                icon = Icons.Rounded.Build,
                enabled = !running,
            ) {
                if (prefs.token.isBlank()) {
                    lines = listOf("Сначала вставь токен GitHub на вкладке «Ещё»")
                    return@ExpressiveButton
                }
                scope.launch {
                    running = true
                    lines = emptyList()
                    val builder = Builder(
                        ctx, prefs,
                        log = { msg -> lines = lines + msg },
                        progress = { p -> progress = p },
                    )
                    runCatching { builder.build(emptyList()) }
                        .onFailure { lines = lines + "Ошибка: ${it.message}" }
                    running = false
                }
            }
        }
        item {
            progress?.let { WaveProgress({ it }) }
            if (running) LoadingRow("Идёт сборка — можно свернуть приложение")
        }
        items(lines) { line ->
            Text(line, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/* -------------------------------- Настройки -------------------------------- */

@Composable
fun SettingsScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    var token by remember { mutableStateOf(prefs.token) }
    var home by remember { mutableStateOf(prefs.home) }
    var repo by remember { mutableStateOf(prefs.repo) }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Ещё", "Подключение, папки и права") }
        item {
            ShapeCard(polygon = Morf.Cookie9) {
                Text("GitHub", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; saved = false },
                    label = { Text("Токен (repo + workflow)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it; saved = false },
                    label = { Text("Репозиторий сборок (owner/name)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            ShapeCard(polygon = Morf.Cookie9) {
                Text("Папка игры", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = home,
                    onValueChange = { home = it; saved = false },
                    label = { Text("Путь") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Моды: ${prefs.modsDir}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ExpressiveButton("Сохранить", Icons.Rounded.Check) {
                prefs.token = token.trim()
                prefs.repo = repo.trim()
                prefs.home = home.trim()
                saved = true
            }
            if (saved) StatusPill("Сохранено")
        }
        item {
            ShapeCard(polygon = Morf.Circle) {
                Text("Права", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Лаунчер пишет файлы в папку игры и отдаёт APK системному установщику.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openAllFiles(ctx) }) { Text("Все файлы") }
                    OutlinedButton(onClick = { openUnknownSources(ctx) }) { Text("Установка APK") }
                }
            }
        }
    }
}

private fun openAllFiles(ctx: Context) {
    if (android.os.Build.VERSION.SDK_INT < 30) return
    ctx.startActivity(
        android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            android.net.Uri.parse("package:${ctx.packageName}"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun openUnknownSources(ctx: Context) {
    ctx.startActivity(
        android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:${ctx.packageName}"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Бейдж «насколько мод переносится в Horizon». */
@Composable
fun CompatBadge(level: Analyze.Level) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (level) {
        Analyze.Level.FULL -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Analyze.Level.PARTIAL -> scheme.secondaryContainer to scheme.onSecondaryContainer
        Analyze.Level.NOT_NEEDED -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        Analyze.Level.IMPOSSIBLE -> scheme.errorContainer to scheme.onErrorContainer
    }
    StatusPill(level.short, bg, fg)
}
