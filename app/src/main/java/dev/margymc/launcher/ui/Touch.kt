package dev.margymc.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.margymc.launcher.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Экранное управление: профили раскладки кнопок.
 *
 * Игра читает `touch/profiles/ИМЯ.json` и имя активного профиля из `touch/active.txt`.
 * Здесь профили можно смотреть, создавать, копировать, удалять, делать активным и
 * править перетаскиванием кнопок.
 */
private val KINDS = listOf("builtin", "key", "hold", "macro", "command", "switch")
private val KIND_TITLES = mapOf(
    "builtin" to "штатная кнопка",
    "key" to "клавиша",
    "hold" to "удержание",
    "macro" to "макрос",
    "command" to "команда в чат",
    "switch" to "другой профиль",
)
private val BUILTINS = listOf(
    "jump", "sneak", "sprint", "inventory", "pause", "view", "mode", "debug", "chat", "lock", "attack",
)

data class Control(
    var id: String,
    var label: String,
    var x: Float,
    var y: Float,
    var r: Float,
    var modes: List<String> = emptyList(),
    var kind: String = "builtin",
    var value: String = "",
)

private fun defaultControls() = listOf(
    Control("jump", "Jump", 0.95f, 0.89f, 22f),
    Control("sneak", "Sneak", 0.83f, 0.94f, 15f),
    Control("sprint", "Run", 0.83f, 0.83f, 15f),
    Control("inventory", "...", 0.65f, 0.97f, 10f),
    Control("pause", "II", 0.97f, 0.04f, 11f),
    Control("view", "F5", 0.91f, 0.04f, 11f),
    Control("mode", "Tap", 0.85f, 0.04f, 11f),
    Control("debug", "F3", 0.79f, 0.04f, 11f),
    Control("chat", "Chat", 0.50f, 0.04f, 12f),
    Control("lock", "Lock", 0.30f, 0.95f, 11f),
    Control("attack", "Hit", 0.67f, 0.50f, 24f, modes = listOf("aim")),
)

private fun readProfile(file: File): List<Control> = runCatching {
    val root = JSONObject(file.readText())
    val list = root.optJSONArray("controls") ?: JSONArray()
    (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id", "")
        val action = o.optJSONObject("action")
        val kind = when {
            action == null -> "builtin"
            else -> KINDS.firstOrNull { action.has(it) } ?: "builtin"
        }
        val value = when (kind) {
            "macro" -> (action!!.optJSONArray("macro")?.let { arr ->
                (0 until arr.length()).joinToString(" ") { arr.optString(it) }
            }) ?: ""
            "builtin" -> ""
            else -> action?.optString(kind, "") ?: ""
        }
        val modes = o.optJSONArray("modes")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        } ?: emptyList()
        Control(
            id = id,
            label = o.optString("label", ""),
            x = o.optDouble("x", 0.5).toFloat(),
            y = o.optDouble("y", 0.5).toFloat(),
            r = o.optDouble("r", 18.0).toFloat(),
            modes = modes,
            kind = kind,
            value = value,
        )
    }
}.getOrDefault(emptyList())

private fun writeProfile(file: File, name: String, controls: List<Control>) {
    val list = JSONArray()
    controls.forEach { c ->
        val o = JSONObject()
        o.put("id", c.id.ifBlank { "btn_" + (1000..9999).random() })
        if (c.label.isNotBlank()) o.put("label", c.label)
        o.put("x", (c.x * 1000).toInt() / 1000.0)
        o.put("y", (c.y * 1000).toInt() / 1000.0)
        o.put("r", c.r.toDouble())
        if (c.modes.isNotEmpty()) o.put("modes", JSONArray(c.modes))
        when (c.kind) {
            "builtin" -> Unit
            "macro" -> o.put("action", JSONObject().put("macro", JSONArray(c.value.split(' ').filter { it.isNotBlank() })))
            else -> o.put("action", JSONObject().put(c.kind, c.value.ifBlank { "space" }))
        }
        list.put(o)
    }
    val root = JSONObject().put("schema", 1).put("name", name).put("controls", list)
    file.parentFile?.mkdirs()
    file.writeText(root.toString(2))
}

private fun ensureDefault(dir: File, active: File) {
    if (dir.listFiles { f -> f.name.endsWith(".json") }?.isNotEmpty() == true) return
    dir.mkdirs()
    writeProfile(File(dir, "default.json"), "Стандартный", defaultControls())
    active.parentFile?.mkdirs()
    active.writeText("default")
}

@Composable
fun TouchScreen(prefs: Prefs) {
    val dir = remember(prefs.home) { File(prefs.home, "touch/profiles") }
    val activeFile = remember(prefs.home) { File(prefs.home, "touch/active.txt") }
    var profiles by remember { mutableStateOf(listOf<File>()) }
    var active by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<File?>(null) }

    fun reload() {
        profiles = dir.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
        active = runCatching { activeFile.readText().trim() }.getOrDefault("")
    }

    LaunchedEffect(prefs.home) {
        ensureDefault(dir, activeFile)
        reload()
    }

    val editingFile = editing
    if (editingFile != null) {
        TouchEditor(editingFile, onBack = { editing = null; reload() })
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Экранное управление", "Раскладка кнопок в игре · ${dir.path}")
        Text(
            "Игра читает эти файлы при запуске; активный профиль — ${active.ifBlank { "нет" }}.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (profiles.isEmpty()) {
            Text("Профилей нет — нажми «Новый», создам стандартный.", fontSize = 14.sp)
        }
        profiles.forEach { file ->
            val name = file.nameWithoutExtension
            val count = readProfile(file).size
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = active == name,
                    onClick = {
                        activeFile.parentFile?.mkdirs()
                        activeFile.writeText(name)
                        active = name
                    },
                )
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(name, fontSize = 16.sp)
                    Text("$count кнопок", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { editing = file }) { Text("Правка") }
                TextButton(onClick = {
                    File(dir, "$name-копия.json").also { copy ->
                        var n = 1
                        var target = copy
                        while (target.exists()) {
                            target = File(dir, "$name-копия${n++}.json")
                        }
                        target.writeText(file.readText())
                    }
                    reload()
                }) { Text("Копия") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                var n = 1
                var target = File(dir, "мой-$n.json")
                while (target.exists()) target = File(dir, "мой-${n++}.json")
                writeProfile(target, target.nameWithoutExtension, defaultControls())
                reload()
            }) { Text("Новый") }
            OutlinedButton(onClick = {
                val current = profiles.firstOrNull { it.nameWithoutExtension == active }
                if (current != null) {
                    current.delete()
                    reload()
                }
            }) { Text("Удалить активный") }
        }
        Text(
            "Кнопки с id jump/sneak/sprint/inventory/pause/view/mode/debug/chat/lock/attack — штатные. " +
                "Любая другая — своя: действие задаётся в редакторе (клавиша, удержание, макрос, команда).",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TouchEditor(file: File, onBack: () -> Unit) {
    var controls by remember(file.path) { mutableStateOf(readProfile(file).ifEmpty { defaultControls() }) }
    var selected by remember { mutableStateOf(-1) }
    var dirty by remember { mutableStateOf(false) }
    val palette = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text(file.nameWithoutExtension, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Button(onClick = { writeProfile(file, file.nameWithoutExtension, controls); dirty = false }) {
                Text(if (dirty) "Сохранить" else "Сохранено")
            }
        }

        // Холст: соотношение как у экрана телефона (9:19.5), координаты 0..1.
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.extraLarge)
                .background(palette.surfaceContainerLowest),
        ) {
            var dragIndex by remember { mutableStateOf(-1) }
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(controls) {
                        detectTapGestures { pos ->
                            selected = controls.indexOfLast { c ->
                                val cx = c.x * size.width
                                val cy = c.y * size.height
                                val radius = c.r / 640f * size.width
                                (pos - Offset(cx, cy)).getDistance() <= radius + 16f
                            }
                        }
                    }
                    .pointerInput(controls) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                dragIndex = controls.indexOfLast { c ->
                                    val cx = c.x * size.width
                                    val cy = c.y * size.height
                                    val radius = c.r / 640f * size.width
                                    (pos - Offset(cx, cy)).getDistance() <= radius + 24f
                                }
                                if (dragIndex >= 0) selected = dragIndex
                            },
                            onDragEnd = { if (dragIndex >= 0) dirty = true; dragIndex = -1 },
                            onDrag = { change, _ ->
                                val index = dragIndex
                                if (index >= 0) {
                                    val c = controls[index]
                                    val nx = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                                    val ny = (change.position.y / size.height).coerceIn(0.02f, 0.98f)
                                    controls = controls.toMutableList().also {
                                        it[index] = c.copy(x = nx, y = ny)
                                    }
                                    dirty = true
                                }
                            },
                        )
                    },
            ) {
                drawTouchControls(controls, selected, palette)
            }
            Text(
                "тащи кнопку · тап — выбрать",
                fontSize = 11.sp,
                color = palette.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
            )
        }

        val index = selected
        if (index in controls.indices) {
            val control = controls[index]
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = control.label,
                    onValueChange = { v ->
                        controls = controls.toMutableList().also { it[index] = control.copy(label = v) }
                        dirty = true
                    },
                    label = { Text("Подпись") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconToggle(control.r) { r ->
                    controls = controls.toMutableList().also { it[index] = control.copy(r = r) }
                    dirty = true
                }
                TextButton(onClick = {
                    controls = controls.toMutableList().also { it.removeAt(index) }
                    selected = -1
                    dirty = true
                }) { Text("Удалить") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KINDS.forEach { kind ->
                    FilterChip(
                        selected = control.kind == kind,
                        onClick = {
                            controls = controls.toMutableList().also { it[index] = control.copy(kind = kind) }
                            dirty = true
                        },
                        label = { Text(KIND_TITLES[kind] ?: kind, fontSize = 11.sp) },
                    )
                }
            }
            when (control.kind) {
                "builtin" -> FlowRow(builtins = true) {
                    BUILTINS.forEach { id ->
                        FilterChip(
                            selected = control.id == id,
                            onClick = {
                                controls = controls.toMutableList().also { it[index] = control.copy(id = id) }
                                dirty = true
                            },
                            label = { Text(id, fontSize = 11.sp) },
                        )
                    }
                }
                "switch" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("next", "prev").forEach { dir ->
                        FilterChip(
                            selected = control.value == dir,
                            onClick = {
                                controls = controls.toMutableList().also { it[index] = control.copy(value = dir) }
                                dirty = true
                            },
                            label = { Text(dir, fontSize = 11.sp) },
                        )
                    }
                }
                else -> OutlinedTextField(
                    value = control.value,
                    onValueChange = { v ->
                        controls = controls.toMutableList().also { it[index] = control.copy(value = v) }
                        dirty = true
                    },
                    label = {
                        Text(
                            when (control.kind) {
                                "macro" -> "Клавиши через пробел (shift e)"
                                "command" -> "Команда (/gamemode creative)"
                                else -> "Клавиша (e, space, shift)"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("aim", "tap").forEach { mode ->
                    FilterChip(
                        selected = control.modes.contains(mode),
                        onClick = {
                            val modes = if (control.modes.contains(mode)) control.modes - mode else control.modes + mode
                            controls = controls.toMutableList().also { it[index] = control.copy(modes = modes) }
                            dirty = true
                        },
                        label = { Text(if (mode == "aim") "прицел" else "тап", fontSize = 11.sp) },
                    )
                }
            }
        }

        OutlinedButton(onClick = {
            controls = controls + Control("btn_" + (1000..9999).random(), "Кнопка", 0.5f, 0.6f, 20f, kind = "key", value = "e")
            selected = controls.size - 1
            dirty = true
        }) { Text("Добавить кнопку") }
    }
}

@Composable
private fun IconToggle(r: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange((r - 2f).coerceAtLeast(6f)) }) { Text("−") }
        Text("${r.toInt()}", fontSize = 13.sp)
        TextButton(onClick = { onChange(r + 2f) }) { Text("+") }
    }
}

@Composable
private fun FlowRow(builtins: Boolean = true, content: @Composable () -> Unit) {
    // Простая обёртка: две строки по шесть чипов, чтобы не тянуть experimental-API.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

private fun DrawScope.drawTouchControls(controls: List<Control>, selected: Int, palette: ColorScheme) {
    val unit = size.width / 640f
    controls.forEachIndexed { i, c ->
        val center = Offset(c.x * size.width, c.y * size.height)
        val radius = c.r * unit
        val fill = if (i == selected) palette.primary.copy(alpha = 0.35f) else palette.onSurface.copy(alpha = 0.18f)
        drawCircle(fill, radius, center)
        drawCircle(
            if (i == selected) palette.primary else palette.onSurface.copy(alpha = 0.4f),
            radius,
            center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * unit),
        )
        if (c.kind != "builtin") {
            drawCircle(palette.tertiary.copy(alpha = 0.5f), radius * 0.35f, center)
        }
    }
}
