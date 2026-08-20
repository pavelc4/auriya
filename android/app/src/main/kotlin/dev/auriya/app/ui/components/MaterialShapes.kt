package dev.auriya.app.ui.components

// PixelCircle and friends — pure `androidx.graphics.shapes` so there is
// zero dependency on the unstable M3E `MaterialShapes` API and we can
// drop the `material3:1.5.0-alpha20` version pin.
import androidx.compose.runtime.Composable
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
    // Cached singletons. Each Shape is path-pure (no per-instance state),
    // so sharing one instance across the whole UI is safe and avoids
    // rebuilding RoundedPolygon four times per list row.
    val Cookie9: Shape by lazy { polygonShape { RoundedPolygon.star(9, innerRadius = 0.62f, rounding = CornerRounding(0.55f)) } }
    val Scallop12: Shape by lazy { polygonShape { RoundedPolygon.star(12, innerRadius = 0.84f, rounding = CornerRounding(0.6f)) } }
    val Clover6: Shape by lazy {
        polygonShape { RoundedPolygon.star(6, innerRadius = 0.48f, rounding = CornerRounding(0.95f), innerRounding = CornerRounding(0.7f)) }
    }
    val Puffy: Shape by lazy {
        polygonShape { RoundedPolygon.star(6, innerRadius = 0.72f, rounding = CornerRounding(0.85f), innerRounding = CornerRounding(0.4f)) }
    }

    // Authentic 8-bit stepped pixel art circle shape
    val PixelCircle: Shape =
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline {
                val path =
                    Path().apply {
                        val w = size.width
                        val h = size.height
                        moveTo(w * 0.3125f, h * 0.0f)
                        lineTo(w * 0.6875f, h * 0.0f)
                        lineTo(w * 0.6875f, h * 0.0625f)
                        lineTo(w * 0.8125f, h * 0.0625f)
                        lineTo(w * 0.8125f, h * 0.125f)
                        lineTo(w * 0.875f, h * 0.125f)
                        lineTo(w * 0.875f, h * 0.1875f)
                        lineTo(w * 0.9375f, h * 0.1875f)
                        lineTo(w * 0.9375f, h * 0.3125f)
                        lineTo(w * 1.0f, h * 0.3125f)
                        lineTo(w * 1.0f, h * 0.6875f)
                        lineTo(w * 0.9375f, h * 0.6875f)
                        lineTo(w * 0.9375f, h * 0.8125f)
                        lineTo(w * 0.875f, h * 0.8125f)
                        lineTo(w * 0.875f, h * 0.875f)
                        lineTo(w * 0.8125f, h * 0.875f)
                        lineTo(w * 0.8125f, h * 0.9375f)
                        lineTo(w * 0.6875f, h * 0.9375f)
                        lineTo(w * 0.6875f, h * 1.0f)
                        lineTo(w * 0.3125f, h * 1.0f)
                        lineTo(w * 0.3125f, h * 0.9375f)
                        lineTo(w * 0.1875f, h * 0.9375f)
                        lineTo(w * 0.1875f, h * 0.875f)
                        lineTo(w * 0.125f, h * 0.875f)
                        lineTo(w * 0.125f, h * 0.8125f)
                        lineTo(w * 0.0625f, h * 0.8125f)
                        lineTo(w * 0.0625f, h * 0.6875f)
                        lineTo(w * 0.0f, h * 0.6875f)
                        lineTo(w * 0.0f, h * 0.3125f)
                        lineTo(w * 0.0625f, h * 0.3125f)
                        lineTo(w * 0.0625f, h * 0.1875f)
                        lineTo(w * 0.125f, h * 0.1875f)
                        lineTo(w * 0.125f, h * 0.125f)
                        lineTo(w * 0.1875f, h * 0.125f)
                        lineTo(w * 0.1875f, h * 0.0625f)
                        lineTo(w * 0.3125f, h * 0.0625f)
                        close()
                    }
                return Outline.Generic(path)
            }
        }

    fun cookie9(): Shape = Cookie9

    fun scallop12(): Shape = Scallop12

    fun clover6(): Shape = Clover6

    fun puffy(): Shape = Puffy

    fun pixelCircle(): Shape = PixelCircle

    private fun polygonShape(build: () -> RoundedPolygon): Shape =
        object : Shape {
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
                val out = Path()
                val xform =
                    androidx.compose.ui.graphics.Matrix().apply {
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
fun rememberCookie9() = MaterialShapes.Cookie9

@Composable
fun rememberScallop() = MaterialShapes.Scallop12

@Composable
fun rememberClover() = MaterialShapes.Clover6

@Composable
fun rememberPuffy() = MaterialShapes.Puffy

@Composable
fun rememberPixelCircle() = MaterialShapes.PixelCircle
