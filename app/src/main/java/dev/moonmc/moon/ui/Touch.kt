package dev.moonmc.moon.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.moonmc.moon.I18n
import dev.moonmc.moon.Prefs
import dev.moonmc.moon.ProfileIO
import dev.moonmc.moon.TouchEditorActivity
import dev.moonmc.moon.TouchProfiles
import java.io.File

/**
 * Список профилей экранного управления. Сама правка открывается на весь экран
 * (TouchEditorActivity) — как будто ты уже в игре.
 */
@Composable
fun TouchScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    var profiles by remember { mutableStateOf(listOf<File>()) }
    var active by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }

    // Импорт чужого/своего профиля: файл -> профиль в папке игры + активный
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val parsed = ProfileIO.parse(text) ?: error(I18n.t("в файле нет знакомых кнопок"))
            val name = (uri.lastPathSegment ?: "import").substringAfterLast('/').substringBeforeLast('.')
            ProfileIO.install(prefs, "import-" + name.take(24), parsed)
        }.onSuccess { file ->
            profiles = TouchProfiles.list(prefs)
            active = TouchProfiles.active(prefs)
            tick++
            status = I18n.t("Импортировано: ") + file.nameWithoutExtension
        }.onFailure { status = I18n.t("Ошибка импорта: ") + (it.message ?: "") }
    }

    LaunchedEffect(prefs.home, tick) {
        TouchProfiles.ensureDefault(prefs)
        profiles = TouchProfiles.list(prefs)
        active = TouchProfiles.active(prefs)
    }

    fun edit(file: File) {
        ctx.startActivity(
            Intent(ctx, TouchEditorActivity::class.java).putExtra(TouchEditorActivity.EXTRA_PATH, file.path)
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                I18n.t("Экранное управление"),
                "${I18n.t("Раскладка кнопок в игре")} · ${TouchProfiles.dir(prefs).path}",
            )
        }
        item {
            Text(
                "${I18n.t("Игра читает эти файлы при запуске")}; " +
                    "${I18n.t("активный профиль")}: ${active.ifBlank { I18n.t("нет") }}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (profiles.isEmpty()) {
            item { Text(I18n.t("Профилей нет — нажми «Новый», создам стандартный.")) }
        }

        items(profiles, key = { it.path }) { file ->
            val name = file.nameWithoutExtension
            ShapeCard() {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = active == name,
                        onClick = {
                            TouchProfiles.setActive(prefs, name)
                            active = name
                        },
                    )
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${TouchProfiles.read(file).size} ${I18n.t("кнопок")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ExpressiveButton(I18n.t("Правка"), Icons.Rounded.Gamepad) { edit(file) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        var n = 1
                        var target = File(TouchProfiles.dir(prefs), "$name-copy.json")
                        while (target.exists()) target = File(TouchProfiles.dir(prefs), "$name-copy${n++}.json")
                        target.writeText(file.readText())
                        tick++
                    }) { Text(I18n.t("Копия")) }
                    OutlinedButton(onClick = {
                        file.delete()
                        if (active == name) TouchProfiles.setActive(prefs, "default")
                        TouchProfiles.ensureDefault(prefs)
                        tick++
                    }) { Text(I18n.t("Удалить")) }
                    ExpressiveButton(I18n.t("Отправить"), Icons.Rounded.Share) {
                        ProfileIO.share(ctx, file)
                    }
                }
            }
        }

        item {
            ShapeCard {
                Text(I18n.t("Встроенные профили"), style = MaterialTheme.typography.titleMedium)
                Text(
                    I18n.t("Раскладки как в Bedrock: джойстик и хотбар рисует игра, кнопки — из профиля"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TouchProfiles.presets().forEach { (name, controls) ->
                        ExpressiveButton(name, Icons.Rounded.Gamepad) {
                            TouchProfiles.installPreset(prefs, name, controls)
                            profiles = TouchProfiles.list(prefs)
                            active = TouchProfiles.active(prefs)
                            tick++
                            status = I18n.t("Профиль поставлен: ") + name
                        }
                    }
                }
                status?.let {
                    Spacer(Modifier.height(6.dp))
                    StatusPill(it)
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveButton(I18n.t("Новый"), Icons.Rounded.Add) {
                    var n = 1
                    var target = File(TouchProfiles.dir(prefs), "profile-$n.json")
                    while (target.exists()) target = File(TouchProfiles.dir(prefs), "profile-${n++}.json")
                    TouchProfiles.write(target, target.nameWithoutExtension, TouchProfiles.defaults())
                    tick++
                    edit(target)
                }
            }
        }
        item {
            Text(
                "jump · sneak · sprint · inventory · pause · view · mode · debug · chat · lock · attack — " +
                    I18n.pick(
                        "builtin buttons; any other id is your own control (key, hold, macro, command, profile switch).",
                        "штатные кнопки; любой другой id — своя кнопка (клавиша, удержание, макрос, команда, переключение профиля).",
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
