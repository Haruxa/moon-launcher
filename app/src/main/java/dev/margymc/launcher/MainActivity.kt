package dev.margymc.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val storage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            storage.launch(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:$packageName"),
                )
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    HORIZON("Моды Horizon", Icons.Filled.Extension),
    MODRINTH("Modrinth", Icons.Filled.Public),
    BUILD("Сборка", Icons.Filled.Build),
    SETTINGS("Настройки", Icons.Filled.Settings),
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var tab by remember { mutableStateOf(Tab.HORIZON) }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Spacer(Modifier.height(8.dp))
            Tab.entries.forEach { t ->
                NavigationRailItem(
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = { Icon(t.icon, contentDescription = t.title) },
                    label = { Text(t.title, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        VerticalDivider()
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                Tab.HORIZON -> HorizonTab(prefs)
                Tab.MODRINTH -> ModrinthTab(prefs)
                Tab.BUILD -> BuildTab(prefs)
                Tab.SETTINGS -> SettingsTab(prefs)
            }
        }
    }
}

@Composable
private fun Screen(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun HorizonTab(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<HorizonIndex.Project>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                projects = HorizonIndex.load().sortedBy { it.name.lowercase() }
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
        }
    }

    Screen("Моды Horizon", "Официальный каталог · установка и сборка") {
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            error != null -> Text("Не удалось загрузить каталог: $error")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects) { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold)
                                    Text(p.summary, style = MaterialTheme.typography.bodySmall)
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(if (p.isNative) "нативный" else "мод") },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val v = p.latest
                                if (!p.isNative && v != null) {
                                    Button(onClick = {
                                        scope.launch {
                                            status = "Ставлю ${p.name}…"
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    val target = File(prefs.modsDir, v.fileName)
                                                    Install.downloadVerified(v.fileUrl, target, v.sha256)
                                                    target
                                                }
                                            }.onSuccess { status = "${p.name}: установлен в ${prefs.modsDir}" }
                                                .onFailure { status = "${p.name}: ${it.message}" }
                                        }
                                    }) { Text("Установить") }
                                } else {
                                    OutlinedButton(onClick = {
                                        status = "${p.name}: нажми «Сборка», чтобы вшить в игру"
                                    }) { Text("Вшить в игру") }
                                }
                                TextButton(onClick = { Install.openUrl(ctx, p.source.ifBlank { "https://github.com" }) }) {
                                    Text("Исходники")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModrinthTab(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(Modrinth.Kind.RESOURCE_PACK) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Modrinth.Hit>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run() {
        scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { Modrinth.search(kind, query) } }
                .onSuccess { hits = it; status = null }
                .onFailure { status = it.message }
            busy = false
        }
    }
    LaunchedEffect(kind) { run() }

    Screen("Modrinth", "Ресурспаки, шейдеры и датапаки. Java-моды не подходят") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Modrinth.Kind.entries.forEach { k ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.title) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Поиск") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { run() }) { Icon(Icons.Filled.Search, null) }
            },
        )
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall); }
        Spacer(Modifier.height(8.dp))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(hits) { h ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(h.title, fontWeight = FontWeight.SemiBold)
                        Text(h.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${h.downloads} загрузок", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                scope.launch {
                                    status = "Скачиваю ${h.title}…"
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val f = Modrinth.file(h.id) ?: error("нет подходящей версии для 26.3")
                                            val dir = File(prefs.home, kind.target)
                                            Install.downloadVerified(f.url, File(dir, f.name), f.sha1, "SHA-1")
                                        }
                                    }.onSuccess { status = "${h.title}: установлено" }
                                        .onFailure { status = "${h.title}: ${it.message}" }
                                }
                            }) { Text("Установить") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildTab(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("Репозиторий сборки: ${prefs.repo.ifBlank { "не создан" }}") }

    Screen("Сборка", "Нативные моды (.hnmod) вшиваются в игру на сервере") {
        if (prefs.token.isBlank()) {
            Text("Сначала вставь токен GitHub в настройках.")
            return@Screen
        }
        Button(onClick = {
            scope.launch {
                log = "Проверяю GitHub…"
                runCatching {
                    withContext(Dispatchers.IO) {
                        val login = Github.whoami(prefs.token)
                        val repo = prefs.repo.ifBlank {
                            Github.ensurePrivateRepo(prefs.token, "margymc-builds", login)
                        }
                        prefs.repo = repo
                        "Репозиторий: $repo"
                    }
                }.onSuccess { log = it }.onFailure { log = "Ошибка: ${it.message}" }
            }
        }) { Text("Создать репозиторий сборки") }
        Spacer(Modifier.height(12.dp))
        Text(log, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingsTab(prefs: Prefs) {
    val ctx = LocalContext.current
    var token by remember { mutableStateOf(prefs.token) }
    var home by remember { mutableStateOf(prefs.home) }
    var msg by remember { mutableStateOf<String?>(null) }

    Screen("Настройки", "GitHub, папка игры, права") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text("GitHub token (repo + workflow)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = home, onValueChange = { home = it },
                label = { Text("Папка игры") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                prefs.token = token.trim()
                prefs.home = home.trim()
                msg = "Сохранено. Папка модов: ${prefs.modsDir}"
            }) { Text("Сохранить") }
            Spacer(Modifier.height(12.dp))
            Text(msg ?: "", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Права и папки", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Лаунчер ставит файлы в папку игры и обновляет игру APK-файлом. " +
                    "Для этого нужен доступ ко всем файлам и разрешение на установку приложений.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                ctx.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:${ctx.packageName}"),
                    )
                )
            }) { Text("Доступ к файлам") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                ctx.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:${ctx.packageName}"),
                    )
                )
            }) { Text("Установка APK") }
        }
    }
}
