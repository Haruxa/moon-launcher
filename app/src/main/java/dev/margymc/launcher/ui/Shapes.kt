package dev.margymc.launcher.ui

import android.graphics.Matrix
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon

/**
 * Превращает морфную фигуру Material 3 Expressive в Compose-Shape.
 * RoundedPolygon задан в нормализованных координатах [-1..1], поэтому
 * масштабируем его под размер компонента.
 */
fun RoundedPolygon.asShape(): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = toPath()
        val m = Matrix().apply {
            setScale(size.width / 2f, size.height / 2f)
            postTranslate(size.width / 2f, size.height / 2f)
        }
        path.transform(m)
        val compose: Path = path.asComposePath()
        return Outline.Generic(compose)
    }
}
