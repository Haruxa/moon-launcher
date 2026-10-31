package dev.margymc.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Формы M3 Expressive («cookie», «sunny»). Рисуем сами: в стабильном
 * material3 1.4.0 нет набора MaterialShapes, а бета-версия требует AGP 9.1.
 */
enum class Morf { Circle, Cookie9, Cookie12, Sunny }

/**
 * Замкнутый «печеньковый» контур: вершины чередуются между внешним радиусом
 * и внутренним (0..0.5 от размера), между ними — квадратичные сглаживания.
 */
fun blobPath(width: Float, height: Float, lobes: Int, inner: Float, rotation: Float = 0f): Path {
    val path = Path()
    val steps = lobes * 2
    val cx = width / 2f
    val cy = height / 2f
    val points = ArrayList<Offset>(steps)
    for (i in 0 until steps) {
        val angle = -PI / 2 + rotation + i * 2.0 * PI / steps
        val r = if (i % 2 == 0) 0.5 else 0.5 * inner
        points.add(
            Offset(
                (cx + r * width * cos(angle)).toFloat(),
                (cy + r * height * sin(angle)).toFloat(),
            )
        )
    }
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until steps) {
        val cur = points[i]
        val next = points[(i + 1) % steps]
        path.quadraticBezierTo(cur.x, cur.y, (cur.x + next.x) / 2f, (cur.y + next.y) / 2f)
    }
    path.close()
    return path
}

/** Мягкие параметры для карточек: текст не должен попадать в «зубцы». */
private fun params(kind: Morf): Triple<Int, Float, Float> = when (kind) {
    Morf.Circle -> Triple(0, 1f, 0f)
    Morf.Cookie9 -> Triple(9, 0.94f, 0.0f)
    Morf.Cookie12 -> Triple(12, 0.96f, 0.06f)
    Morf.Sunny -> Triple(16, 0.93f, 0f)
}

fun morfShape(kind: Morf): Shape = GenericShape { size, _ ->
    val (lobes, inner, rot) = params(kind)
    if (lobes == 0) {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                0f, 0f, size.width, size.height,
                androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
        )
    } else {
        addPath(blobPath(size.width, size.height, lobes, inner, rot))
    }
}

/** Рисует фон-«печеньку» под контентом (без клипа, поэтому текст не съезжает). */
@Composable
fun MorfBackground(kind: Morf, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val (lobes, inner, rot) = params(kind)
        if (lobes == 0) {
            drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
        } else {
            drawPath(blobPath(size.width, size.height, lobes, inner, rot), color)
        }
    }
}

/** Крупный заголовок экрана. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Карточка с морфной формой. */
@Composable
fun ShapeCard(
    modifier: Modifier = Modifier,
    polygon: Morf = Morf.Cookie9,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth()) {
        MorfBackground(polygon, containerColor, Modifier.matchParentSize())
        Column(
            Modifier.padding(horizontal = contentPadding + 4.dp, vertical = contentPadding),
            content = content,
        )
    }
}

/** Кнопка-действие: пилюля, крупная зона нажатия. */
@Composable
fun ExpressiveButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Статусная «жвачка». */
@Composable
fun StatusPill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(percent = 50), color = containerColor, modifier = modifier) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

/** Волнистый прогресс. */
@Composable
fun WaveProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val w = size.width
        val mid = size.height / 2f
        val amp = size.height * 0.28f
        val stroke = 6.dp.toPx()
        drawLine(track, Offset(0f, mid), Offset(w, mid), strokeWidth = stroke, cap = StrokeCap.Round)
        val done = progress().coerceIn(0f, 1f) * w
        if (done <= 1f) return@Canvas
        clipRect(right = done) {
            val path = Path()
            var x = 0f
            path.moveTo(0f, mid)
            while (x <= w) {
                val y = mid + amp * sin((x / w * 3f + phase) * 2f * PI.toFloat())
                path.lineTo(x, y)
                x += 6f
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }
}

/** Морфный лоадер: вращающаяся пульсирующая «печенька». */
@Composable
fun MorphLoading(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "morph")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.84f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(modifier.size(36.dp)) {
        rotate(spin) {
            drawPath(blobPath(size.width, size.height, 6, pulse), color)
        }
    }
}

/** Строка «идёт работа». */
@Composable
fun LoadingRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MorphLoading(Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Заголовок секции. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}
