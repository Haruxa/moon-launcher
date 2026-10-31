package dev.moonmc.moon.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import dev.moonmc.moon.*
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
    var buildEntries by remember { mutableStateOf(BuildList.load(prefs)) }
    // матрица совместимости для выбранного пина (ci/compat/<pin>.json)
    var compat by remember { mutableStateOf<Engine.Compat?>(null) }
    // результаты анализа нативных модов (кнопка «Анализ», без сборки)
    val analyses = remember { mutableStateMapOf<String, NativeAnalyze.Result>() }

    LaunchedEffect(prefs.nativePin) {
        runCatching {
            withContext(Dispatchers.IO) {
                Engine.compat(prefs.nativePin, prefs.token.ifBlank { null })
            }
        }.onSuccess { compat = it }
    }

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
            ScreenHeader(I18n.t("Моды Horizon"), I18n.t("Каталог модов и нативных расширений для игры 26.3"))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !showNative, onClick = { showNative = false }, label = { Text(I18n.t("Готовые")) })
                FilterChip(selected = showNative, onClick = { showNative = true }, label = { Text(I18n.t("Нативные")) })
                Spacer(Modifier.weight(1f))
                if (loading) MorphLoading()
            }
        }
        status?.let { item { StatusPill(it, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) } }
        error?.let { item { StatusPill(I18n.t("Ошибка: ") + it, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) } }

        items(visible, key = { it.id }) { p ->
            ShapeCard() {
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
                    if (p.isNative) {
                        compat?.of(p.id)?.let { verdict ->
                            if (verdict.ok) {
                                StatusPill(
                                    I18n.t("на этом движке ✓"),
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            } else {
                                StatusPill(
                                    I18n.t("на этом движке не собирается"),
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!p.isNative) {
                        ExpressiveButton(I18n.t("Установить"), Icons.Rounded.Download) {
                            scope.launch {
                                status = "${p.name}: " + I18n.t("скачиваю…")
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val v = p.latest ?: error(I18n.t("нет версии для 26.3"))
                                        Install.downloadVerified(v.fileUrl, File(prefs.modsDir, v.fileName), v.sha256)
                                    }
                                }.onSuccess { status = "${p.name}: " + I18n.t("установлен") }
                                    .onFailure { status = "${p.name}: ${it.message}" }
                            }
                        }
                    } else {
                        val v = p.latest
                        val id = v?.let { "url:" + it.fileUrl }
                        val inList = id != null && buildEntries.any { it.id == id }
                        val analysis = v?.let { analyses[p.id] }
                        analysis?.let {
                            // Компактно: длинная строка раздувала карточку — только 2 строки.
                            Text(
                                it.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.verdict == NativeAnalyze.Verdict.OK)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 320.dp),
                            )
                        }
                        if (v != null) {
                            ExpressiveButton(I18n.t("Анализ"), Icons.Rounded.Search) {
                                scope.launch {
                                    status = "${p.name}: " + I18n.t("анализирую…")
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            NativeAnalyze.analyze(
                                                url = v.fileUrl,
                                                modId = p.id,
                                                version = v.version,
                                                pin = prefs.nativePin,
                                                token = prefs.token.ifBlank { null },
                                            )
                                        }
                                    }.onSuccess {
                                        analyses[p.id] = it
                                        status = "${p.name}: " + I18n.t(it.summary)
                                    }.onFailure {
                                        status = "${p.name}: " + I18n.t("Ошибка: ") + it.message
                                    }
                                }
                            }
                        }
                        ExpressiveButton(
                            text = if (inList) I18n.t("В списке") else I18n.t("В список сборки"),
                            icon = if (inList) Icons.Rounded.Check else Icons.Rounded.Add,
                        ) {
                            if (v == null) {
                                status = "${p.name}: " + I18n.t("нет версии")
                            } else if (inList) {
                                buildEntries = BuildList.remove(prefs, id!!)
                                status = "${p.name}: " + I18n.t("убрано из списка")
                            } else {
                                buildEntries = BuildList.add(
                                    prefs,
                                    BuildEntry(
                                        id = "url:" + v.fileUrl,
                                        title = p.name,
                                        pin = v.nativePin ?: "",
                                        mixins = v.mixinsPin ?: "",
                                        url = v.fileUrl,
                                    ),
                                )
                                status = "${p.name}: " + I18n.t("добавлено в список — собери на вкладке «Сборка»")
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
    val ctx = LocalContext.current
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
            ScreenHeader("Modrinth", I18n.t("Пакеты — как есть, Java-моды переносим данными в .hmod"))
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Modrinth.Kind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(I18n.t(k.title)) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(I18n.t("Поиск")) },
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
            ShapeCard() {
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
                            res == null -> StatusPill(I18n.t("нужен анализ"))
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
                                    status = "${h.title}: " + I18n.t("качаю jar для анализа…")
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val f = Modrinth.file(h.id, kind) ?: error(I18n.t("нет файла мода"))
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
                        ) { Text(I18n.t("Анализ")) }

                        if (res != null && res.level != Analyze.Level.IMPOSSIBLE && res.level != Analyze.Level.NOT_NEEDED) {
                            ExpressiveButton(I18n.t("В .hmod"), Icons.Rounded.Build) {
                                scope.launch {
                                    status = "${h.title}: " + I18n.t("собираю .hmod…")
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val jar = jars[h.id] ?: error(I18n.t("сначала анализ"))
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
                                        status = "${h.title}: ${c.files}" + I18n.t(" файлов, пропущено ") + "${c.skipped} → ${c.file.name}"
                                    }.onFailure { status = "${h.title}: ${it.message}" }
                                }
                            }
                        }
                    } else {
                        ExpressiveButton(I18n.t("Ставить"), Icons.Rounded.Download) {
                            scope.launch {
                                status = "${h.title}: " + I18n.t("скачиваю…")
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val f = Modrinth.file(h.id, kind) ?: error(I18n.t("нет сборки под 26.x"))
                                        val dir = File(prefs.home, kind.target)
                                        Install.downloadVerified(f.url, File(dir, f.name), f.sha1, "SHA-1")
                                    }
                                }.onSuccess { status = "${h.title}: " + I18n.t("установлено в ") + kind.target }
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

/**
 * Сборка APK: управляемый список модов (включить/выключить, удалить), пин движка
 * и кнопка запуска GitHub Actions. Вшить можно и бандлы из `ci/bundles`, и моды
 * каталога Horizon по ссылке — всё это лежит в одном списке.
 */
@Composable
fun BuildScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf(listOf<String>()) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var running by remember { mutableStateOf(false) }

    var entries by remember { mutableStateOf(BuildList.load(prefs)) }
    var bundles by remember { mutableStateOf<List<Github.Entry>>(emptyList()) }
    var listing by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf(prefs.nativePin) }

    // библиотека ночников Horizon: пин -> дата Nightly, «новее/старее»
    var nightlies by remember { mutableStateOf<List<Engine.Nightly>>(emptyList()) }
    var engineBusy by remember { mutableStateOf(false) }
    var engineError by remember { mutableStateOf<String?>(null) }
    var showNewer by remember { mutableStateOf(false) }
    var engineNote by remember { mutableStateOf<String?>(null) }
    var compat by remember { mutableStateOf<Engine.Compat?>(null) }

    fun loadNightlies() {
        scope.launch {
            engineBusy = true
            engineError = null
            runCatching {
                withContext(Dispatchers.IO) { Engine.nightlies(prefs.token.ifBlank { null }) }
            }.onSuccess { nightlies = it }.onFailure { engineError = it.message }
            engineBusy = false
        }
    }

    val repo = prefs.repo.ifBlank { "Haruxa/moonmc-apk" }
    val enabled = entries.filter { it.enabled }
    val pins = BuildList.distinctPins(entries)

    fun loadBundles() {
        if (prefs.token.isBlank()) {
            listError = I18n.t("Нужен токен GitHub — вкладка «Ещё»")
            return
        }
        scope.launch {
            listing = true
            listError = null
            runCatching {
                withContext(Dispatchers.IO) {
                    Github.listDir(prefs.token, repo, "ci/bundles")
                        .filter { it.name.endsWith(".hnmod") }
                }
            }.onSuccess { bundles = it }.onFailure { listError = it.message }
            listing = false
        }
    }

    // Свой файл .hnmod: кладём в ci/bundles репозитория и сразу добавляем в список
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            note = null
            runCatching {
                withContext(Dispatchers.IO) {
                    var name = "mod.hnmod"
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
                    }
                    name = name.substringAfterLast('/').trim()
                    if (!name.endsWith(".hnmod")) name = name.removeSuffix(".zip") + ".hnmod"
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    Github.putBytes(prefs.token, repo, "ci/bundles/$name", bytes, "мод: $name")
                    name.removeSuffix(".hnmod")
                }
            }.onSuccess { name ->
                note = I18n.t("Добавлено: ") + name
                entries = BuildList.add(
                    prefs,
                    BuildEntry(id = "bundle:$name", title = name, bundle = name),
                )
                loadBundles()
            }.onFailure { note = I18n.t("Ошибка: ") + (it.message ?: "") }
        }
    }

    LaunchedEffect(Unit) { loadBundles(); loadNightlies() }
    LaunchedEffect(pin) {
        runCatching {
            withContext(Dispatchers.IO) { Engine.compat(pin, prefs.token.ifBlank { null }) }
        }.onSuccess { compat = it }.onFailure { compat = null }
    }
    // пин движка подтягиваем из выбранных модов, если он у них один
    LaunchedEffect(entries) {
        val common = BuildList.commonPin(entries)
        if (common.isNotBlank() && common != pin) {
            pin = common
            prefs.nativePin = common
        }
        // миксины каталога тоже подтягиваем, если у включённых модов они одинаковые
        entries.filter { it.enabled && it.mixins.isNotBlank() }
            .map { it.mixins }.distinct().singleOrNull()
            ?.let { prefs.mixinsRev = it }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                I18n.t("Сборка APK"),
                I18n.t("Нативные моды (.hnmod) вшиваются в игру в GitHub Actions"),
            )
        }

        /* ---------------------------- список сборки ---------------------------- */
        item {
            ShapeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(I18n.t("Список сборки"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    StatusPill(I18n.t("включено: ") + enabled.size + " / " + entries.size)
                }
                if (entries.isEmpty()) {
                    Text(
                        I18n.t("Пусто. Добавь мод из каталога («Моды» → «Нативные») или бандл ниже."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entries.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { entries = BuildList.toggle(prefs, entry.id) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (entry.enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                if (entry.enabled) entry.subtitle
                                else I18n.t("выключен") + " · " + entry.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val verdict = compat?.of(entry.id.removePrefix("url:"))
                            if (verdict != null) {
                                Text(
                                    if (verdict.ok) I18n.t("на этом движке ✓")
                                    else I18n.t("на этом движке не собирается: ") + verdict.stage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (verdict.ok) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { entries = BuildList.move(prefs, entry.id, true) }) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = I18n.t("выше"))
                        }
                        IconButton(onClick = { entries = BuildList.move(prefs, entry.id, false) }) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = I18n.t("ниже"))
                        }
                        IconButton(onClick = { entries = BuildList.remove(prefs, entry.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = I18n.t("удалить"))
                        }
                    }
                }
                if (entries.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    ExpressiveButton(I18n.t("Очистить список"), Icons.Rounded.Delete) {
                        BuildList.clear(prefs)
                        entries = emptyList()
                    }
                }
                if (pins.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        I18n.t("Выбранные моды требуют разные пины движка: ") +
                            pins.joinToString(", ") { it.take(8) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        /* ---------------------- бандлы .hnmod в репозитории --------------------- */
        item {
            ShapeCard {
                Text(I18n.t("Бандлы в репозитории"), style = MaterialTheme.typography.titleMedium)
                Text(
                    I18n.t("ci/bundles — файлы .hnmod, которые вшиваются на сборке"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (listing) {
                    Spacer(Modifier.height(8.dp))
                    LoadingRow(I18n.t("Читаю список…"))
                }
                listError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (!listing && bundles.isEmpty() && listError == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(I18n.t("В репозитории пока нет .hnmod"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                bundles.forEach { bundle ->
                    val name = bundle.name.removeSuffix(".hnmod")
                    val id = "bundle:$name"
                    val inList = entries.any { it.id == id }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = inList,
                            onCheckedChange = { on ->
                                entries = if (on) {
                                    BuildList.add(
                                        prefs,
                                        BuildEntry(id = id, title = name, bundle = name),
                                    )
                                } else {
                                    BuildList.remove(prefs, id)
                                }
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                bundle.name + " · " + (bundle.size / 1024) + " " + I18n.t("КБ"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton(I18n.t("Обновить список"), Icons.Rounded.Refresh) {
                        loadBundles()
                    }
                    ExpressiveButton(I18n.t("Добавить .hnmod"), Icons.Rounded.Add) {
                        if (prefs.token.isBlank()) {
                            note = I18n.t("Нужен токен GitHub — вкладка «Ещё»")
                        } else {
                            picker.launch(arrayOf("*/*"))
                        }
                    }
                }
                note?.let {
                    Spacer(Modifier.height(6.dp))
                    StatusPill(it)
                }
            }
        }

        /* ------------------------------ пин движка ----------------------------- */
        item {
            ShapeCard {
                Text(I18n.t("Пин движка"), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it; prefs.nativePin = it.trim() },
                    label = { Text(I18n.t("Коммит horizon")) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    I18n.t("Нативные моды жёстко привязаны к коммиту движка"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            ShapeCard {
                Text(I18n.t("Движок"), style = MaterialTheme.typography.titleMedium)
                Text(
                    Engine.label(pin, nightlies),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val newer = Engine.newerThan(pin, nightlies)
                if (engineBusy) {
                    Spacer(Modifier.height(8.dp))
                    LoadingRow(I18n.t("Читаю ночники…"))
                }
                engineError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                engineNote?.let {
                    Spacer(Modifier.height(6.dp))
                    StatusPill(it)
                }
                if (!engineBusy && nightlies.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    if (newer.isEmpty()) {
                        Text(
                            I18n.t("Пин на самом свежем ночнике"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            I18n.t("Новее есть: ") + newer.size,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (showNewer) {
                            newer.forEach { nightly ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(nightly.label,
                                            style = MaterialTheme.typography.bodyMedium)
                                    }
                                    ExpressiveButton(I18n.t("Поставить"), Icons.Rounded.Check) {
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    Engine.resolve(
                                                        prefs.token.ifBlank { null },
                                                        nightly.sha,
                                                    )
                                                }
                                            }.onSuccess { full ->
                                                pin = full
                                                prefs.nativePin = full
                                                engineNote =
                                                    I18n.t("Пин движка: ") + nightly.label
                                            }.onFailure {
                                                engineNote = I18n.t("Ошибка: ") + it.message
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        ExpressiveButton(
                            text = if (showNewer) I18n.t("Скрыть") else I18n.t("Проверить новее"),
                            icon = Icons.Rounded.Refresh,
                        ) { showNewer = !showNewer }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        I18n.t("Ночник — это main плюс неотревьюенные PR: бери, когда нужен мод или фикс под него"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            ExpressiveButton(
                text = if (running) I18n.t("Собирается…")
                else I18n.t("Собрать APK") + " (" + enabled.size + ")",
                icon = Icons.Rounded.Build,
                enabled = !running,
            ) {
                if (prefs.token.isBlank()) {
                    lines = listOf(I18n.t("Сначала вставь токен GitHub на вкладке «Ещё»"))
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
                    runCatching {
                        builder.build(
                            modUrls = enabled.filter { it.url.isNotBlank() }.map { it.url },
                            nativePin = pin.trim().takeIf { it.isNotEmpty() },
                            mixinsRev = prefs.mixinsRev,
                            bundles = enabled.filter { it.bundle.isNotBlank() }.map { it.bundle },
                        )
                    }.onFailure { lines = lines + I18n.t("Ошибка: ") + (it.message ?: "") }
                    running = false
                }
            }
        }
        item {
            progress?.let { WaveProgress({ it }) }
            if (running) LoadingRow(I18n.t("Идёт сборка — можно свернуть приложение"))
        }
        items(lines) { line ->
            Text(line, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/* ---------------------------------- О нас ---------------------------------- */

/** Кто сделал, где следить за проектом и чья лицензия внутри. */
@Composable
fun AboutScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(I18n.t("О нас"), I18n.t("Проект, автор и лицензии")) }

        item {
            ShapeCard {
                Text("Moon", style = MaterialTheme.typography.headlineSmall)
                Text(
                    I18n.t("Лаунчер и клиент MoonMC — Android-порт Horizon") +
                        (if (version.isBlank()) "" else " · v$version"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    I18n.t("Движок: ") + Engine.label(prefs.nativePin, emptyList()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            ShapeCard {
                Text(I18n.t("Разработчик"), style = MaterialTheme.typography.titleMedium)
                Text("Haruxa", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                ExpressiveButton(I18n.t("GitHub"), Icons.Rounded.OpenInNew) {
                    Install.openUrl(ctx, "https://github.com/Haruxa")
                }
            }
        }

        item {
            ShapeCard {
                Text(I18n.t("Следить за проектом"), style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton("Telegram · @MooN_MCL", Icons.Rounded.Send) {
                        Install.openUrl(ctx, "https://t.me/MooN_MCL")
                    }
                    ExpressiveButton(I18n.t("Репозиторий лаунчера"), Icons.Rounded.Code) {
                        Install.openUrl(ctx, "https://github.com/Haruxa/moon-launcher")
                    }
                    ExpressiveButton(I18n.t("Сборка игры"), Icons.Rounded.Build) {
                        Install.openUrl(ctx, "https://github.com/Haruxa/moonmc-apk")
                    }
                }
            }
        }

        item {
            ShapeCard {
                Text(I18n.t("Авторы и лицензии"), style = MaterialTheme.typography.titleMedium)
                Text(
                    I18n.t(
                        "Мы стоим на чужой работе, и это указано честно:"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                CreditRow("Horizon", "MIT · Ludovic Marie and the Horizon contributors")
                CreditRow("MargyMC", "MIT · narezany — оригинальный Android-порт")
                CreditRow("Material Design Icons", "Apache 2.0 · Google LLC")
                CreditRow("ZalithLauncher 2 JRE", "GPL-2.0 with Classpath Exception")
                CreditRow("Kotlin · Jetpack Compose", "Apache 2.0 · JetBrains, Google")
                Spacer(Modifier.height(8.dp))
                Text(
                    I18n.t("Сам лаунчер — MIT. Файлы Minecraft не распространяются: " +
                        "игра берёт твою собственную копию."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(
                I18n.t("Minecraft — торговая марка Mojang Studios. Проект не связан с Mojang и Microsoft."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreditRow(name: String, license: String) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(
            license,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* -------------------------------- Настройки -------------------------------- */

@Composable
fun SettingsScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    var home by remember { mutableStateOf(prefs.home) }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(I18n.t("Ещё"), I18n.t("Подключение, папки и права")) }
        item { GithubCard(prefs) }
        item {
            ShapeCard() {
                Text(I18n.t("Папка игры"), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = home,
                    onValueChange = { home = it; saved = false },
                    label = { Text(I18n.t("Путь")) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    I18n.t("Моды: ") + prefs.modsDir,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ExpressiveButton(I18n.t("Сохранить"), Icons.Rounded.Check) {
                prefs.home = home.trim()
                saved = true
            }
            if (saved) StatusPill(I18n.t("Сохранено"))
        }
        item {
            ShapeCard() {
                Text(I18n.t("Язык"), style = MaterialTheme.typography.titleMedium)
                Text(
                    I18n.t("Интерфейс лаунчера"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    I18n.languages.forEach { (code, title) ->
                        FilterChip(
                            selected = prefs.lang == code,
                            onClick = {
                                prefs.lang = code
                                (ctx as? android.app.Activity)?.recreate()
                            },
                            label = { Text(title) },
                        )
                    }
                }
            }
        }
        item {
            ShapeCard() {
                Text(I18n.t("Права"), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Лаунчер пишет файлы в папку игры и отдаёт APK системному установщику.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openAllFiles(ctx) }) { Text(I18n.t("Все файлы")) }
                    OutlinedButton(onClick = { openUnknownSources(ctx) }) { Text(I18n.t("Установка APK")) }
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
    StatusPill(I18n.t(level.short), bg, fg)
}
