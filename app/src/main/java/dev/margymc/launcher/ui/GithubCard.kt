package dev.margymc.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.margymc.launcher.GithubAuth
import dev.margymc.launcher.I18n
import dev.margymc.launcher.Install
import dev.margymc.launcher.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* --------------------------------- GitHub ---------------------------------- */

@Composable
fun GithubCard(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bound by remember { mutableStateOf(prefs.token.isNotBlank()) }
    var login by remember { mutableStateOf(prefs.ghLogin) }
    var showBind by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var repos by remember { mutableStateOf<List<GithubAuth.Repo>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(prefs.repo) }

    fun loadRepos() {
        if (prefs.token.isBlank()) return
        loading = true
        error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { GithubAuth.repos(prefs.token) } }
                .onSuccess {
                    repos = it
                    if (prefs.ghLogin.isBlank()) {
                        runCatching { withContext(Dispatchers.IO) { GithubAuth.login(prefs.token) } }
                            .onSuccess { name -> prefs.ghLogin = name; login = name }
                    }
                }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(bound) { if (bound && repos == null) loadRepos() }

    ShapeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(I18n.t("GitHub"), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (bound) I18n.t("Привязан") + ": " + login.ifBlank { I18n.t("аккаунт") }
                    else I18n.t("Не привязан — нужен для сборок и модов"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showHelp = true }) {
                Icon(Icons.Rounded.HelpOutline, I18n.t("Как привязать"), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(10.dp))

        if (!bound) {
            ExpressiveButton(
                text = if (showBind) I18n.t("Привязка открыта") else I18n.t("Привязать GitHub"),
                icon = Icons.Rounded.Lock,
            ) { showBind = true }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { loadRepos() }, enabled = !loading) {
                    Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(I18n.t("Обновить список"))
                }
                OutlinedButton(onClick = {
                    prefs.token = ""
                    prefs.ghLogin = ""
                    login = ""
                    bound = false
                    repos = null
                }) { Text(I18n.t("Отвязать")) }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(I18n.t("Ошибка: ") + it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (bound) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Text(I18n.t("Репозиторий сборок"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                I18n.t("Можно вписать имя вручную или выбрать из списка"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it; prefs.repo = it.trim() },
                    label = { Text("owner/name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(I18n.t("Поиск по списку репозиториев")) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            if (loading) {
                Spacer(Modifier.height(8.dp))
                LoadingRow(I18n.t("Читаю репозитории…"))
            }
            val list = repos?.filter { query.isBlank() || it.fullName.contains(query, true) }
            if (list != null && list.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    list.take(60).forEach { r ->
                        val active = r.fullName == prefs.repo
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth().clickable {
                                prefs.repo = r.fullName
                                manual = r.fullName
                            },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.fullName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        (if (r.private) I18n.t("приватный") + " · " else "") +
                                            (r.description ?: I18n.t("без описания")),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (active) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            } else if (list != null && query.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(I18n.t("Ничего не найдено"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showBind) {
        BindDialog(
            prefs = prefs,
            onClose = { showBind = false },
            onBound = { name ->
                login = name
                bound = true
                showBind = false
            },
            open = { Install.openUrl(ctx, it) },
        )
    }

    if (showHelp) {
        GitHubHelp(onClose = { showHelp = false }, open = { Install.openUrl(ctx, it) })
    }
}

private fun copyText(ctx: android.content.Context, text: String) {
    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(android.content.ClipData.newPlainText("MargyMC", text))
}

/* ------------------------------- Диалог привязки ---------------------------- */

@Composable
private fun BindDialog(
    prefs: Prefs,
    onClose: () -> Unit,
    onBound: (String) -> Unit,
    open: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var device by remember { mutableStateOf<GithubAuth.Device?>(null) }
    var status by remember { mutableStateOf(I18n.t("Готовлю код…")) }
    var error by remember { mutableStateOf<String?>(null) }
    var tokenField by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Старт device flow.
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { GithubAuth.start(prefs.clientId) } }
            .onSuccess { device = it; status = I18n.t("Жду подтверждения в браузере…") }
            .onFailure { error = it.message; status = I18n.t("Не удалось получить код") }
    }

    // Опрос, пока пользователь не подтвердит.
    LaunchedEffect(device) {
        val d = device ?: return@LaunchedEffect
        repeat((d.expiresIn / d.interval.coerceAtLeast(5)).coerceAtMost(120)) {
            delay(d.interval.coerceAtLeast(5) * 1000L)
            val got = runCatching { withContext(Dispatchers.IO) { GithubAuth.poll(prefs.clientId, d.deviceCode) } }
            got.onFailure { error = it.message; return@LaunchedEffect }
            val token = got.getOrNull() ?: return@repeat
            runCatching {
                val name = withContext(Dispatchers.IO) { GithubAuth.login(token) }
                prefs.token = token
                prefs.ghLogin = name
                name
            }.onSuccess { onBound(it) }.onFailure { error = it.message }
            return@LaunchedEffect
        }
        status = I18n.t("Код устарел — закрой окно и начни заново")
    }


    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(I18n.t("Привязать GitHub")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    I18n.t("1. Нажми «Открыть GitHub» — код скопируется.  2. Войди и вставь его.  3. Разреши доступ."),
                    style = MaterialTheme.typography.bodySmall,
                )
                val d = device
                if (d != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(I18n.t("Код для ввода"), fontSize = 11.sp)
                            Text(
                                d.userCode,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { copyText(ctx, d.userCode) }) {
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(I18n.t("Скопировать"), fontSize = 12.sp)
                                }
                                Button(onClick = {
                                    copyText(ctx, d.userCode)
                                    open(d.uri)
                                }) {
                                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(I18n.t("Открыть GitHub"), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (device != null && error == null) MorphLoading(Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(I18n.t("Ошибка: ") + it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    I18n.t("Если браузер не открылся — вставь токен вручную (Personal access token)"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = tokenField,
                    onValueChange = { tokenField = it },
                    label = { Text("ghp_… / github_pat_…") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = tokenField.isNotBlank() && !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching {
                                    val t = tokenField.trim()
                                    val name = withContext(Dispatchers.IO) { GithubAuth.login(t) }
                                    prefs.token = t
                                    prefs.ghLogin = name
                                    name
                                }.onSuccess { onBound(it) }.onFailure { error = it.message }
                                busy = false
                            }
                        },
                    ) { Text(I18n.t("Проверить токен")) }
                    TextButton(onClick = { open("https://github.com/settings/tokens/new?scopes=repo,workflow&description=MargyMC") }) {
                        Text(I18n.t("Создать токен"), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(I18n.t("Закрыть")) } },
    )
}

/* ---------------------------------- Справка -------------------------------- */

@Composable
private fun GitHubHelp(onClose: () -> Unit, open: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(I18n.t("Как привязать GitHub")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HelpStep("1", I18n.t("Нажми «Привязать GitHub». Приложение покажет короткий код вида ABCD-1234 и сразу скопирует его."))
                HelpStep("2", I18n.t("Нажми «Открыть GitHub» — откроется github.com/login/device. Войди в аккаунт, вставь код, нажми Continue."))
                HelpStep("3", I18n.t("На странице доступа нажми Authorize. Окно в приложении закроется само — токен уже сохранён."))
                HelpStep("4", I18n.t("Теперь впиши имя репозитория (owner/name) или выбери его из списка — он подставится в поле «Репозиторий сборок»."))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    I18n.t("Что даёт доступ: создание и чтение приватных репозиториев и запуск GitHub Actions (сборка APK). Токен хранится только на телефоне."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    I18n.t("Долго не приходит подтверждение? Скопируй код вручную, открой ссылку и введи его сам."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open("https://github.com/login/device") }) { Text("login/device") }
                Button(onClick = onClose) { Text(I18n.t("Понятно")) }
            }
        },
    )
}

@Composable
private fun HelpStep(num: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(num, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/* ----------------------------------- Git ----------------------------------- */

@Composable
fun GitCard(prefs: Prefs) {
    val ctx = LocalContext.current
    val repo = prefs.repo
    val url = if (repo.isBlank()) "" else "https://github.com/$repo"

    ShapeCard {
        Text(I18n.t("Git"), style = MaterialTheme.typography.titleMedium)
        Text(
            I18n.t("Ссылка на выбранный репозиторий и команда клонирования"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        if (repo.isBlank()) {
            Text(I18n.t("Репозиторий не выбран"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(repo, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "git clone " + url + ".git",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyText(ctx, "git clone $url.git") }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(I18n.t("Копировать"), fontSize = 12.sp)
                }
                OutlinedButton(onClick = { Install.openUrl(ctx, url) }) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(I18n.t("Открыть"), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            I18n.t("Папка для репозиториев: ") + prefs.gitDir,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
