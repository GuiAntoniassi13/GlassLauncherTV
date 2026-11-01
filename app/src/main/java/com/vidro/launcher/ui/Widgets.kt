package com.vidro.launcher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidro.launcher.data.AppInfo
import com.vidro.launcher.data.Images
import com.vidro.launcher.data.PreviewItem
import com.vidro.launcher.data.Sky
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

val INK = Color(0xFF10121A)

@Composable
fun focusSpec(): AnimationSpec<Float> = when (LocalLook.current.anim) {
    0 -> spring(dampingRatio = 0.62f, stiffness = 380f)
    1 -> tween(140)
    else -> snap()
}

/** Ícone de app com acabamento de vidro líquido: cor do app, lente brilhante, aro iluminado e reflexo ao focar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppInfo,
    tileSize: Dp,
    key: String,
    state: LauncherState,
    modifier: Modifier = Modifier,
    downTo: String? = null, // chave que recebe o foco com ▼ (a busca automática do Compose às vezes falha após rolar a fileira)
    restShadow: Dp = 5.dp,  // sombra sem foco; 0 na gaveta (fundo escuro, e 19 sombras pesam na GPU da TV)
    onFocused: () -> Unit = {},
) {
    val look = LocalLook.current
    var focused by remember { mutableStateOf(false) }
    val moving = state.movingPkg == app.pkg
    val spec = focusSpec()
    val scale by animateFloatAsState(if (focused) 1.17f else 1f, spec, label = "scale")
    val lift by animateFloatAsState(if (focused) 1f else 0f, spec, label = "lift")
    val shape = RoundedCornerShape(tileSize * 0.27f)
    val shine = remember { Animatable(-1f) }
    LaunchedEffect(focused) {
        if (focused && look.anim == 0 && !look.lite) {
            shine.snapTo(-0.7f)
            delay(120)
            shine.animateTo(1.4f, tween(1000))
        } else shine.snapTo(-1f)
    }

    // Ao mudar de lugar, o foco não muda de ícone, então a fileira não rolaria sozinha:
    // a cada troca de posição, pede para mostrar o ícone com um vizinho visível de cada lado
    val reveal = remember { BringIntoViewRequester() }
    var tileArea by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(moving, state.favorites) {
        if (!moving) return@LaunchedEffect
        withFrameNanos { } // espera o layout com a nova ordem
        val margin = tileArea.width * 1.25f
        reveal.bringIntoView(Rect(-margin, 0f, tileArea.width + margin, tileArea.height.toFloat()))
    }

    Column(
        modifier.width(tileSize + 24.dp).bringIntoViewRequester(reveal).onSizeChanged { tileArea = it },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .tvClickable(
                    key = key,
                    onFocus = { focused = it; if (it) onFocused() },
                    onLongClick = { if (!moving) state.openMenu(app.pkg) },
                    onKey = { e ->
                        if (moving && e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) {
                            state.moveFav(app.pkg, if (e.key == Key.DirectionLeft) -1 else 1); true
                        } else if (downTo != null && !moving && e.key == Key.DirectionDown) {
                            if (e.type == KeyEventType.KeyDown) state.bus.request = downTo
                            true
                        } else false
                    },
                    onClick = { if (moving) state.endMove() else state.launch(app.pkg) },
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = -lift * tileSize.toPx() * 0.08f
                }
                .size(tileSize)
                .shadow(
                    if (focused) 16.dp else restShadow, shape,
                    ambientColor = if (focused) app.color else Color.Black,
                    spotColor = if (focused) app.color else Color.Black,
                )
                .clip(shape)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } // ícone + vidro em cache
                .background(Brush.linearGradient(listOf(lerp(app.color, Color.White, 0.12f), lerp(app.color, Color.Black, 0.55f))))
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(if (app.fullBleed) 1f else 0.64f)
                    .clip(if (app.fullBleed) shape else RoundedCornerShape(tileSize * 0.12f)),
            )
            // Lente de vidro + brilho que atravessa o ícone ao receber foco
            Box(
                Modifier.matchParentSize().drawBehind {
                    if (!look.lite) {
                        drawRect(
                            Brush.radialGradient(
                                0f to Color.White.copy(alpha = .50f), 0.52f to Color.White.copy(alpha = .10f), 0.53f to Color.Transparent,
                                center = Offset(size.width / 2, -size.height * 0.18f), radius = size.width * 0.95f,
                            )
                        )
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = .18f), Color.Transparent),
                                center = Offset(size.width / 2, size.height * 1.2f), radius = size.width * 0.6f,
                            )
                        )
                    }
                    val s = shine.value
                    if (s > -0.6f && s < 1.3f) {
                        val x = s * size.width
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = .45f), Color.Transparent),
                                start = Offset(x - size.width * 0.25f, 0f), end = Offset(x + size.width * 0.25f, size.height * 0.3f),
                            )
                        )
                    }
                }
            )
            Box(
                Modifier.matchParentSize().border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) SolidColor(Color.White)
                    else Brush.linearGradient(listOf(Color.White.copy(alpha = .7f), Color.White.copy(alpha = .06f), Color.Transparent, Color.White.copy(alpha = .3f))),
                    shape,
                )
            )
            if (moving) {
                Txt("◀  ▶", 11.sp, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), weight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Txt(
            app.label, 10.sp,
            weight = FontWeight.SemiBold,
            color = if (focused) Color.White else Color.White.copy(alpha = .66f),
        )
    }
}

@Composable
fun PillButton(text: String, key: String, play: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (f) 1.08f else 1f, focusSpec(), label = "pill")
    val fg = if (f) INK else Color.White
    Row(
        Modifier
            .tvClickable(key, onFocus = { f = it }, onLongClick = onLongClick, onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (f) Color.White else Color.White.copy(alpha = .14f))
            .border(1.dp, Color.White.copy(alpha = if (f) 0f else .18f), CircleShape)
            .padding(horizontal = 17.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (play) {
            Canvas(Modifier.size(9.dp)) {
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }, fg)
            }
            Spacer(Modifier.width(6.dp))
        }
        Txt(text, 12.sp, weight = FontWeight.Bold, color = fg)
    }
}

@Composable
fun ChipButton(text: String, key: String, selected: Boolean = false, dot: Boolean = false, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (f) 1.08f else 1f, focusSpec(), label = "chip")
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .tvClickable(key, onFocus = { f = it }, onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (f) Color.White else Color.White.copy(alpha = if (selected) .22f else .08f))
            .border(1.dp, Color.White.copy(alpha = if (f) 0f else if (selected) .4f else .14f), shape)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF34D399)))
            Spacer(Modifier.width(5.dp))
        }
        Txt(text, 10.5.sp, weight = FontWeight.SemiBold, color = if (f) INK else Color.White)
    }
}

/** Ícones do tempo desenhados em código (64 unidades), sem imagens externas. */
@Composable
fun SkyIcon(sky: Sky, dim: Dp) {
    Canvas(Modifier.size(dim)) {
        val u = size.width / 64f
        val sunColor = Color(0xFFFCD34D)
        fun sun(cx: Float, cy: Float, r: Float) {
            drawCircle(sunColor, r * u, Offset(cx * u, cy * u))
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                drawLine(
                    sunColor,
                    Offset((cx + cos(a) * (r + 5)).toFloat() * u, (cy + sin(a) * (r + 5)).toFloat() * u),
                    Offset((cx + cos(a) * (r + 10)).toFloat() * u, (cy + sin(a) * (r + 10)).toFloat() * u),
                    strokeWidth = 3 * u, cap = StrokeCap.Round,
                )
            }
        }
        fun cloud(dx: Float, dy: Float) {
            val c = Color.White.copy(alpha = .92f)
            drawCircle(c, 10 * u, Offset((24 + dx) * u, (36 + dy) * u))
            drawCircle(c, 13 * u, Offset((36 + dx) * u, (29 + dy) * u))
            drawCircle(c, 9 * u, Offset((46 + dx) * u, (37 + dy) * u))
            drawRoundRect(c, Offset((14 + dx) * u, (34 + dy) * u), Size(40 * u, 12 * u), CornerRadius(6 * u))
        }
        when (sky) {
            Sky.SUN -> sun(32f, 32f, 11f)
            Sky.PARTLY -> { sun(24f, 24f, 9f); cloud(4f, 6f) }
            Sky.CLOUD -> cloud(0f, 2f)
            Sky.FOG -> {
                cloud(0f, -4f)
                val g = Color.White.copy(alpha = .6f)
                drawLine(g, Offset(14 * u, 50 * u), Offset(50 * u, 50 * u), 3 * u, StrokeCap.Round)
                drawLine(g, Offset(20 * u, 57 * u), Offset(44 * u, 57 * u), 3 * u, StrokeCap.Round)
            }
            Sky.RAIN -> {
                cloud(0f, -6f)
                for (x in listOf(24f, 34f, 44f)) drawLine(Color(0xFF7DD3FC), Offset(x * u, 46 * u), Offset((x - 3) * u, 54 * u), 3 * u, StrokeCap.Round)
            }
            Sky.STORM -> {
                cloud(0f, -6f)
                val bolt = Path().apply {
                    moveTo(35 * u, 42 * u); lineTo(27 * u, 53 * u); lineTo(33 * u, 53 * u); lineTo(30 * u, 62 * u)
                    lineTo(40 * u, 49 * u); lineTo(34 * u, 49 * u); lineTo(37 * u, 42 * u); close()
                }
                drawPath(bolt, Color(0xFFFDE047))
            }
        }
    }
}

/** Capa de um destaque publicado pelo app (título, imagem e atalho para abrir direto). */
@Composable
fun PreviewCard(item: PreviewItem, w: Dp, h: Dp, key: String, accent: Color, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var f by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (f) 1.08f else 1f, focusSpec(), label = "card")
    val img by produceState<ImageBitmap?>(null, item.posterUri) { value = item.posterUri?.let { Images.load(ctx, it) } }
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier
            .tvClickable(key, onFocus = { f = it }, onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(w, h)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, .7f))))
    ) {
        img?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f))))
                .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 7.dp)
        ) {
            Column {
                Txt(item.title, 10.sp, weight = FontWeight.Bold, maxLines = 2)
                if (item.subtitle.isNotBlank()) Txt(item.subtitle, 7.sp, color = Color.White.copy(alpha = .7f))
            }
        }
        Box(Modifier.matchParentSize().border(if (f) 2.dp else 1.dp, if (f) Color.White else Color.White.copy(alpha = .25f), shape))
    }
}
