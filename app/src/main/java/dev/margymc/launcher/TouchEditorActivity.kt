package dev.margymc.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import dev.margymc.launcher.ui.MargyTheme
import java.io.File

/**
 * Редактор раскладки на весь экран: выглядит как сама игра, кнопки подписаны,
 * перетаскиваются пальцем. Сверху — выход и сохранение, снизу — настройки кнопки.
 */
class TouchEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        val prefs = Prefs(this)
        I18n.lang = prefs.lang
        setContent {
            key(prefs.lang) {
                MargyTheme {
                    if (path == null) {
                        finish()
                    } else {
                        Editor(File(path), prefs)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "profile_path"
    }
}

@Composable
private fun Editor(file: File, prefs: Prefs) {
    var controls by remember(file.path) { mutableStateOf(TouchProfiles.read(file).ifEmpty { TouchProfiles.defaults() }) }
    var selected by remember { mutableStateOf(-1) }
    var dirty by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val land = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    fun save() {
        TouchProfiles.write(file, file.nameWithoutExtension, controls)
        dirty = false
    }

    BackHandler {
        if (dirty) confirmExit = true else (ctx as? android.app.Activity)?.finish()
    }

    Box(Modifier.fillMaxSize().background(scheme.surfaceContainerLowest)) {
        // Игровое полотно на весь экран.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val textMeasurer = rememberTextMeasurer()
            var dragIndex by remember { mutableStateOf(-1) }
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(controls) {
                        detectTapGestures { pos ->
                            selected = controls.indexOfLast { c ->
                                val cx = c.x * size.width
                                val cy = c.y * size.height
                                (pos - Offset(cx, cy)).getDistance() <= c.r / 640f * size.width + 18f
                            }
                        }
                    }
                    .pointerInput(controls) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                dragIndex = controls.indexOfLast { c ->
                                    val cx = c.x * size.width
                                    val cy = c.y * size.height
                                    (pos - Offset(cx, cy)).getDistance() <= c.r / 640f * size.width + 26f
                                }
                                if (dragIndex >= 0) selected = dragIndex
                            },
                            onDragEnd = { dragIndex = -1 },
                            onDrag = { change, _ ->
                                val i = dragIndex
                                if (i >= 0) {
                                    val c = controls[i]
                                    val nx = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                                    val ny = (change.position.y / size.height).coerceIn(0.02f, 0.98f)
                                    controls = controls.toMutableList().also { it[i] = c.copy(x = nx, y = ny) }
                                    dirty = true
                                }
                            },
                        )
                    },
            ) {
                drawControls(controls, selected, scheme, textMeasurer)
            }
        }

        // Верхняя панель: выйти / сохранить.
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = { if (dirty) confirmExit = true else (ctx as? android.app.Activity)?.finish() },
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Rounded.Close, I18n.t("Выйти")) }
            Column(Modifier.weight(1f)) {
                Text(
                    file.nameWithoutExtension,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (dirty) I18n.t("Изменения не сохранены") else I18n.t("Перетаскивай кнопки по экрану"),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(
                onClick = {
                    controls = controls + Control(
                        "btn_" + (1000..9999).random(),
                        I18n.t("Кнопка"),
                        0.5f,
                        0.55f,
                        20f,
                        kind = "key",
                        value = "e",
                    )
                    selected = controls.size - 1
                    dirty = true
                },
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Rounded.Add, I18n.t("Добавить кнопку")) }
            Button(onClick = { save() }, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(6.dp))
                Text(I18n.t("Сохранить"))
            }
        }

        // Панель выбранной кнопки.
        val index = selected
        if (index in controls.indices) {
            val control = controls[index]
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().fillMaxHeight(0.42f),
                shape = MaterialTheme.shapes.extraLarge,
                color = scheme.surfaceContainerHigh.copy(alpha = 0.97f),
                tonalElevation = 3.dp,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(I18n.t("Настройки кнопки"), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        Text(control.id, fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        TextButton(onClick = {
                            controls = controls.toMutableList().also { it.removeAt(index) }
                            selected = -1
                            dirty = true
                        }) { Text(I18n.t("Удалить"), color = scheme.error) }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = control.label,
                            onValueChange = { v ->
                                controls = controls.toMutableList().also { it[index] = control.copy(label = v) }
                                dirty = true
                            },
                            label = { Text(I18n.t("Подпись")) },
                            singleLine = true,
                            modifier = Modifier.width(200.dp),
                        )
                        FilledTonalIconButton(onClick = {
                            controls = controls.toMutableList()
                                .also { it[index] = control.copy(r = (control.r - 2f).coerceAtLeast(6f)) }
                            dirty = true
                        }) { Icon(Icons.Rounded.Remove, null) }
                        Text("${control.r.toInt()}", fontSize = 14.sp)
                        FilledTonalIconButton(onClick = {
                            controls = controls.toMutableList().also { it[index] = control.copy(r = control.r + 2f) }
                            dirty = true
                        }) { Icon(Icons.Rounded.Add, null) }
                        listOf("aim", "tap").forEach { mode ->
                            FilterChip(
                                selected = control.modes.contains(mode),
                                onClick = {
                                    val modes = if (control.modes.contains(mode)) control.modes - mode else control.modes + mode
                                    controls = controls.toMutableList().also { it[index] = control.copy(modes = modes) }
                                    dirty = true
                                },
                                label = { Text(I18n.t(mode), fontSize = 12.sp) },
                            )
                        }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TouchProfiles.kinds.forEach { kind ->
                            FilterChip(
                                selected = control.kind == kind,
                                onClick = {
                                    controls = controls.toMutableList().also { it[index] = control.copy(kind = kind) }
                                    dirty = true
                                },
                                label = { Text(TouchProfiles.kindTitle(kind), fontSize = 12.sp) },
                            )
                        }
                    }
                    when (control.kind) {
                        "builtin" -> Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TouchProfiles.builtins.forEach { id ->
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
                                    label = { Text(dir, fontSize = 12.sp) },
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
                                    I18n.t(
                                        when (control.kind) {
                                            "macro" -> "Клавиши через пробел (shift e)"
                                            "command" -> "Команда (/gamemode creative)"
                                            else -> "Клавиша (e, space, shift)"
                                        }
                                    )
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default,
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
            title = { Text(I18n.t("Изменения не сохранены")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    save()
                    (ctx as? android.app.Activity)?.finish()
                }) { Text(I18n.t("Сохранить")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmExit = false
                    (ctx as? android.app.Activity)?.finish()
                }) { Text(I18n.t("Выйти")) }
            },
        )
    }
}

private fun DrawScope.drawControls(
    controls: List<Control>,
    selected: Int,
    scheme: ColorScheme,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val unit = size.width / 640f
    controls.forEachIndexed { i, c ->
        val center = Offset(c.x * size.width, c.y * size.height)
        val radius = c.r * unit
        val isSel = i == selected
        drawCircle(
            if (isSel) scheme.primary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.22f),
            radius,
            center,
        )
        drawCircle(
            if (isSel) scheme.primary else Color.White.copy(alpha = 0.55f),
            radius,
            center,
            style = Stroke(width = if (isSel) 3f * unit else 2f * unit),
        )
        val label = c.label.ifBlank { c.id }
        val layout = textMeasurer.measure(
            text = label,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = (radius / unit).coerceIn(9f, 18f).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
        drawText(
            layout,
            topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
        )
    }
}
