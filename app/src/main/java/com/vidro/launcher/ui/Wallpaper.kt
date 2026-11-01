package com.vidro.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope

data class WallDef(val id: String, val name: String, val accent: Color, val black: Boolean = false)

/** 6 fundos escuros + Total Black. */
val WALLS = listOf(
    WallDef("black", "Total Black", Color(0xFF333333), black = true),
    WallDef("aurora", "Aurora noturna", Color(0xFF6D4AFF)),
    WallDef("meianoite", "Meia-noite", Color(0xFF3B5BDB)),
    WallDef("onix", "Ônix", Color(0xFF8A94A6)),
    WallDef("brasa", "Brasa", Color(0xFFE2553A)),
    WallDef("abismo", "Abismo", Color(0xFF0EA5A4)),
    WallDef("crepusculo", "Crepúsculo escuro", Color(0xFFC2477A)),
)

fun wallOf(id: String) = WALLS.firstOrNull { it.id == id } ?: WALLS[1]

/** Mancha de luz: centro e raio em frações da tela. */
private data class Blob(val x: Float, val y: Float, val r: Float, val color: Color)

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r, g, b, (a * 255).toInt())

private fun baseBrush(id: String): Brush = when (id) {
    "meianoite" -> Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF030716), Color(0xFF060C24)))
    "onix" -> Brush.linearGradient(listOf(Color(0xFF0B0B0E), Color(0xFF000000), Color(0xFF0D0E12)))
    "abismo" -> Brush.verticalGradient(listOf(Color.Black, Color(0xFF01161A)))
    "aurora" -> SolidColor(Color(0xFF010104))
    else -> SolidColor(Color.Black)
}

private fun blobsOf(id: String): List<Blob> = when (id) {
    "aurora" -> listOf(Blob(0.18f, 0.10f, 0.36f, rgba(109, 74, 255, .42f)), Blob(0.78f, 0.28f, 0.30f, rgba(20, 184, 166, .24f)), Blob(0.50f, 0.92f, 0.28f, rgba(190, 24, 93, .20f)))
    "meianoite" -> listOf(Blob(0.76f, 0.05f, 0.32f, rgba(37, 64, 170, .38f)), Blob(0.16f, 0.98f, 0.28f, rgba(14, 30, 90, .5f)))
    "onix" -> listOf(Blob(0.18f, 0.08f, 0.30f, rgba(160, 170, 190, .13f)))
    "brasa" -> listOf(Blob(0.20f, 0.95f, 0.36f, rgba(220, 60, 30, .34f)), Blob(0.38f, 1.0f, 0.25f, rgba(255, 140, 40, .16f)), Blob(0.86f, 0.08f, 0.28f, rgba(120, 20, 40, .22f)))
    "abismo" -> listOf(Blob(0.26f, 0.90f, 0.33f, rgba(6, 148, 148, .30f)), Blob(0.82f, 0.12f, 0.28f, rgba(30, 64, 175, .22f)))
    "crepusculo" -> listOf(Blob(0.13f, 0.02f, 0.25f, rgba(140, 90, 255, .45f)))
    else -> emptyList()
}

private val STARS = listOf(0.12f to 0.18f, 0.27f to 0.09f, 0.41f to 0.26f, 0.58f to 0.12f, 0.73f to 0.22f, 0.88f to 0.08f,
    0.18f to 0.44f, 0.64f to 0.38f, 0.92f to 0.33f, 0.35f to 0.52f, 0.80f to 0.55f, 0.06f to 0.30f)

/**
 * Fundo desenhado num único Canvas dentro de uma camada "offscreen": a GPU guarda o resultado
 * como textura e só o refaz quando o papel de parede muda. Sem animação contínua (economia de energia).
 */
@Composable
fun Wallpaper(id: String, modifier: Modifier = Modifier, vignette: Boolean = false) {
    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        drawRect(baseBrush(id))
        if (id == "crepusculo") drawDusk()
        if (id == "meianoite") {
            STARS.forEach { (x, y) -> drawCircle(Color.White.copy(alpha = .7f), size.minDimension * 0.0025f + 0.6f, Offset(x * size.width, y * size.height)) }
        }
        blobsOf(id).forEach { b ->
            val c = Offset(b.x * size.width, b.y * size.height)
            val r = b.r * size.width
            drawCircle(Brush.radialGradient(listOf(b.color, b.color.copy(alpha = 0f)), center = c, radius = r), r, c)
        }
        if (id == "onix") drawRect(Brush.linearGradient(0.38f to Color.Transparent, 0.5f to Color.White.copy(alpha = .045f), 0.62f to Color.Transparent))
        if (id == "crepusculo") drawRect(Color.Black.copy(alpha = .58f))
        if (vignette) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .5f)), Offset(size.width / 2, size.height * .3f), size.width * .75f))
    }
}

private fun DrawScope.drawDusk() {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF1B1442), 0.3f to Color(0xFF4A2470), 0.52f to Color(0xFFC2477A), 0.64f to Color(0xFFFF9A62),
            0.70f to Color(0xFFFFCF8A), 0.71f to Color(0xFF2A1F4D), 1f to Color(0xFF120F2E),
        )
    )
    val sun = Offset(w * 0.69f, h * 0.5f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3C4), Color(0xFFFFB36B), Color(0x00FF7850)), sun, w * 0.12f), w * 0.12f, sun)
    fun ridge(points: List<Pair<Float, Float>>, color: Color) {
        val p = Path().apply {
            moveTo(0f, points.first().second * h)
            points.forEach { (x, y) -> lineTo(x * w, y * h) }
            lineTo(w, h * 0.716f); lineTo(0f, h * 0.716f); close()
        }
        drawPath(p, color)
    }
    ridge(listOf(0f to 0.655f, 0.09f to 0.552f, 0.16f to 0.603f, 0.235f to 0.483f, 0.32f to 0.595f, 0.39f to 0.517f, 0.45f to 0.586f,
        0.54f to 0.448f, 0.62f to 0.569f, 0.70f to 0.509f, 0.78f to 0.595f, 0.86f to 0.526f, 0.94f to 0.603f, 1f to 0.56f), Color(0xE62B1B4F))
    ridge(listOf(0f to 0.69f, 0.12f to 0.603f, 0.21f to 0.655f, 0.30f to 0.586f, 0.41f to 0.664f, 0.50f to 0.603f, 0.60f to 0.672f,
        0.71f to 0.595f, 0.82f to 0.664f, 0.91f to 0.62f, 1f to 0.664f), Color(0xFF1A1238))
    drawRect(
        Brush.verticalGradient(listOf(Color(0x8CFF9A62), Color(0x807A3A8A), Color(0xFF0C0A22)), startY = h * 0.71f, endY = h),
        topLeft = Offset(0f, h * 0.71f), size = Size(w, h * 0.29f),
    )
}
