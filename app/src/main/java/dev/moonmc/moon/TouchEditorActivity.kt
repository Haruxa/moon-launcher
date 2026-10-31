package dev.moonmc.moon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.moonmc.moon.ui.MoonTheme
import java.io.File
import kotlin.math.abs

/**
 * Редактор раскладки на весь экран: кнопки подписаны и тащатся пальцем,
 * панель настроек уезжает на противоположную от кнопки сторону.
 */
class TouchEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        val prefs = Prefs(this)
        I18n.lang = prefs.lang
        setContent {
            key(prefs.lang) {
                MoonTheme {
                    if (path == null) finish() else Editor(File(path))
                }
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "profile_path"
    }
}

@Composable
private fun Editor(file: File) {
    val colors = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val list = remember(file.path) {
        mutableStateListOf<Control>().apply { addAll(TouchProfiles.read(file).ifEmpty { TouchProfiles.defaults() }) }
    }
    var selected by remember { mutableStateOf(-1) }
    var dirty by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    // Жест «взял кнопку»: запоминаем смещение пальца относительно центра.
    var dragIndex by remember { mutableStateOf(-1) }
    var grab by remember { mutableStateOf(Offset.Zero) }

    fun save() {
        TouchProfiles.write(file, file.nameWithoutExtension, list.toList())
        dirty = false
    }

    fun close() = (ctx as? android.app.Activity)?.finish()

    BackHandler { if (dirty) confirmExit = true else close() }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val unit = maxWidth / 640f

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        selected = list.indexOfLast { c ->
                            (pos - Offset(c.x * size.width, c.y * size.height)).getDistance() <= c.r * unit.toPx() + 16f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val hit = list.indexOfLast { c ->
                                (pos - Offset(c.x * size.width, c.y * size.height)).getDistance() <= c.r * unit.toPx() + 24f
                            }
                            if (hit >= 0) {
                                val c = list[hit]
                                grab = pos - Offset(c.x * size.width, c.y * size.height)
                                dragIndex = hit
                                selected = hit
                            } else {
                                dragIndex = -1
                            }
                        },
                        onDragEnd = { dragIndex = -1 },
                        onDragCancel = { dragIndex = -1 },
                        onDrag = { change, _ ->
                            change.consume()
                            val i = dragIndex
                            if (i >= 0 && i < list.size) {
                                val c = list[i]
                                val nx = ((change.position.x - grab.x) / size.width).coerceIn(0.03f, 0.97f)
                                val ny = ((change.position.y - grab.y) / size.height).coerceIn(0.03f, 0.97f)
                                list[i] = c.copy(x = nx, y = ny)
                                dirty = true
                            }
                        },
                    )
                },
        ) {
            drawControls(list, selected, colors, textMeasurer, unit.toPx())
        }

        // Верхняя панель.
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(onClick = { if (dirty) confirmExit = true else close() }) {
                Icon(Icons.Rounded.Close, I18n.t("Выйти"))
            }
            Column(Modifier.weight(1f)) {
                Text(file.nameWithoutExtension, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (dirty) I18n.t("Есть несохранённые изменения") else I18n.t("Тащи кнопки пальцем, коснись — настройки"),
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(onClick = {
                list.add(Control("btn_" + (1000..9999).random(), I18n.t("Кнопка"), 0.5f, 0.5f, 20f, kind = "key", value = "e"))
                selected = list.size - 1
                dirty = true
            }) { Icon(Icons.Rounded.Add, I18n.t("Добавить")) }
            Button(onClick = { save() }) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(6.dp))
                Text(I18n.t("Сохранить"))
            }
        }

        // Панель настроек: напротив выбранной кнопки.
        val index = selected
        if (index in list.indices) {
            val c = list[index]
            val onLeft = c.x >= 0.5f
            Surface(
                modifier = Modifier
                    .align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .fillMaxWidth(0.36f)
                    .fillMaxHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.surfaceContainerHigh,
                tonalElevation = 2.dp,
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(I18n.t("Кнопка"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            list.removeAt(index)
                            selected = -1
                            dirty = true
                        }) { Icon(Icons.Rounded.Delete, I18n.t("Удалить"), tint = colors.error) }
                    }
                    Text(c.id, fontSize = 11.sp, color = colors.onSurfaceVariant)

                    OutlinedTextField(
                        value = c.label,
                        onValueChange = { list[index] = c.copy(label = it); dirty = true },
                        label = { Text(I18n.t("Подпись")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(I18n.t("Иконка"), fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TouchProfiles.icons.forEach { icon ->
                            FilterChip(
                                selected = c.icon == icon,
                                onClick = { list[index] = c.copy(icon = icon); dirty = true },
                                label = { Text(TouchProfiles.iconTitle(icon), fontSize = 11.sp) },
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(I18n.t("Размер"), fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        FilledTonalIconButton(onClick = {
                            list[index] = c.copy(r = (c.r - 2f).coerceAtLeast(6f)); dirty = true
                        }) { Icon(Icons.Rounded.Remove, null) }
                        Text("${c.r.toInt()}", fontSize = 14.sp)
                        FilledTonalIconButton(onClick = {
                            list[index] = c.copy(r = (c.r + 2f).coerceAtMost(80f)); dirty = true
                        }) { Icon(Icons.Rounded.Add, null) }
                    }

                    Text(I18n.t("Нажатие"), fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("aim", "tap").forEach { mode ->
                            FilterChip(
                                selected = c.modes.contains(mode),
                                onClick = {
                                    val m = if (c.modes.contains(mode)) c.modes - mode else c.modes + mode
                                    list[index] = c.copy(modes = m); dirty = true
                                },
                                label = { Text(I18n.t(mode), fontSize = 12.sp) },
                            )
                        }
                    }

                    Text(I18n.t("Действие"), fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TouchProfiles.kinds.forEach { kind ->
                            FilterChip(
                                selected = c.kind == kind,
                                onClick = { list[index] = c.copy(kind = kind); dirty = true },
                                label = { Text(TouchProfiles.kindTitle(kind), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    when (c.kind) {
                        "builtin" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TouchProfiles.builtins.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    row.forEach { id ->
                                        FilterChip(
                                            selected = c.id == id,
                                            onClick = { list[index] = c.copy(id = id); dirty = true },
                                            label = { Text(id, fontSize = 10.sp) },
                                        )
                                    }
                                }
                            }
                        }
                        "switch" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("next", "prev").forEach { dir ->
                                FilterChip(
                                    selected = c.value == dir,
                                    onClick = { list[index] = c.copy(value = dir); dirty = true },
                                    label = { Text(dir, fontSize = 12.sp) },
                                )
                            }
                        }
                        else -> OutlinedTextField(
                            value = c.value,
                            onValueChange = { list[index] = c.copy(value = it); dirty = true },
                            label = {
                                Text(
                                    I18n.t(
                                        when (c.kind) {
                                            "macro" -> "Клавиши через пробел"
                                            "command" -> "Команда без слэша"
                                            else -> "Клавиша (e, space)"
                                        }
                                    ),
                                    fontSize = 11.sp,
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(I18n.t("Есть несохранённые изменения")) },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; save(); close() }) { Text(I18n.t("Сохранить")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false; close() }) { Text(I18n.t("Выйти")) }
            },
        )
    }
}

private fun DrawScope.drawControls(
    controls: List<Control>,
    selected: Int,
    colors: ColorScheme,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    unit: Float,
) {
    controls.forEachIndexed { i, c ->
        val center = Offset(c.x * size.width, c.y * size.height)
        val radius = c.r * unit
        val isSel = i == selected
        drawCircle(
            if (isSel) colors.primary.copy(alpha = 0.28f) else Color(0xFF000000).copy(alpha = 0.06f),
            radius,
            center,
        )
        drawCircle(
            if (isSel) colors.primary else colors.outline.copy(alpha = 0.7f),
            radius,
            center,
            style = Stroke(width = (if (isSel) 3f else 2f) * unit),
        )
        val layout = textMeasurer.measure(
            text = c.label.ifBlank { c.id },
            style = androidx.compose.ui.text.TextStyle(
                fontSize = (c.r / 1.6f).coerceIn(8f, 16f).sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
    }
}
