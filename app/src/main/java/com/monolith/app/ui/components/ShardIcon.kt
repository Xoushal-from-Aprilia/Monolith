package com.monolith.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A fragment of the monolith: an uneven rhombus cut into four faces.
 * Filled: the faces in four shades. Outline: the edges and the cuts.
 */
object ShardIcons {
    // corners of the fragment and the point where the cuts meet
    private const val TX = 12.6f; private const val TY = 2.2f
    private const val RX = 19.4f; private const val RY = 9.0f
    private const val BX = 11.6f; private const val BY = 21.8f
    private const val LX = 4.6f; private const val LY = 10.4f
    private const val CX = 11.5f; private const val CY = 10.6f

    val Filled: ImageVector by lazy {
        ImageVector.Builder("ShardFilled", 24.dp, 24.dp, 24f, 24f).apply {
            fun face(alpha: Float, ax: Float, ay: Float, bx: Float, by: Float) =
                path(fill = SolidColor(Color.Black), fillAlpha = alpha) {
                    moveTo(ax, ay); lineTo(bx, by); lineTo(CX, CY); close()
                }
            face(0.55f, TX, TY, RX, RY)
            face(0.8f, RX, RY, BX, BY)
            face(1f, BX, BY, LX, LY)
            face(0.7f, LX, LY, TX, TY)
        }.build()
    }

    val Outline: ImageVector by lazy {
        ImageVector.Builder("ShardOutline", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round, strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(TX, TY); lineTo(RX, RY); lineTo(BX, BY); lineTo(LX, LY); close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeAlpha = 0.6f, strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round, strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(LX, LY); lineTo(CX, CY); lineTo(RX, RY)
                moveTo(TX, TY); lineTo(CX, CY); lineTo(BX, BY)
            }
        }.build()
    }
}
