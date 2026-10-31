package dev.margymc.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/** Крупный «expressive» заголовок экрана. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Карточка с морфной формой Material 3 Expressive. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeCard(
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon? = MaterialShapes.Cookie9Sided,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape: Shape = polygon?.asShape() ?: RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Кнопка-действие с морфной формой и крупной типографикой. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    polygon: RoundedPolygon? = MaterialShapes.Cookie9Sided,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    onClick: () -> Unit,
) {
    val shape = polygon?.asShape() ?: RoundedCornerShape(28.dp)
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Маленький статусный «жвачок» (чип). */
@Composable
fun StatusPill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(999.dp), color = containerColor, modifier = modifier) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

/** Волнистый индикатор прогресса — фирменная деталь Expressive. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WaveProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(
        progress = { progress().coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        gapSize = 0.dp,
        stopSize = 4.dp,
    )
}

/** Морфный индикатор загрузки (LoadingIndicator из M3 Expressive). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphLoading(modifier: Modifier = Modifier) {
    LoadingIndicator(modifier = modifier.size(36.dp))
}

/** Строка «идёт работа» с морфным индикатором. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator(modifier = Modifier.size(28.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Заголовок секции внутри экрана. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}
