package dev.auriya.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

object MaterialShapes {
    fun cookie9(): Shape = polygonShape {
        RoundedPolygon.star(
            numVerticesPerRadius = 9,
            innerRadius = 0.62f,
            rounding = CornerRounding(0.55f),
        )
    }

    fun scallop12(): Shape = polygonShape {
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.84f,
            rounding = CornerRounding(0.6f),
        )
    }

    fun clover6(): Shape = polygonShape {
        RoundedPolygon.star(
            numVerticesPerRadius = 6,
            innerRadius = 0.48f,
            rounding = CornerRounding(0.95f),
            innerRounding = CornerRounding(0.7f),
        )
    }

    fun puffy(): Shape = polygonShape {
        RoundedPolygon.star(
            numVerticesPerRadius = 6,
            innerRadius = 0.72f,
            rounding = CornerRounding(0.85f),
            innerRounding = CornerRounding(0.4f),
        )
    }

    private fun polygonShape(build: () -> RoundedPolygon): Shape = object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val polygon = build()
            val basePath: Path = polygon.toPath().asComposePath()
            val bounds = polygon.calculateBounds()
            val srcW = (bounds[2] - bounds[0]).coerceAtLeast(0.0001f)
            val srcH = (bounds[3] - bounds[1]).coerceAtLeast(0.0001f)
            val sx = size.width / srcW
            val sy = size.height / srcH
            val matrix = androidx.compose.ui.graphics.Matrix().apply {
                translate(-bounds[0], -bounds[1])
                scale(sx, sy)
            }
            // Reorder: scale first then translate? Use a fresh transformed path.
            val out = Path()
            val xform = androidx.compose.ui.graphics.Matrix().apply {
                scale(sx, sy)
                translate(-bounds[0], -bounds[1])
            }
            out.addPath(basePath)
            out.transform(xform)
            return Outline.Generic(out)
        }
    }
}

@Composable
fun rememberCookie9() = remember { MaterialShapes.cookie9() }

@Composable
fun rememberScallop() = remember { MaterialShapes.scallop12() }

@Composable
fun rememberClover() = remember { MaterialShapes.clover6() }

@Composable
fun rememberPuffy() = remember { MaterialShapes.puffy() }
