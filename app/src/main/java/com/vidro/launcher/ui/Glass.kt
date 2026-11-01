package com.vidro.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.vidro.launcher.data.LookSettings
import kotlinx.coroutines.delay

val LocalLook = staticCompositionLocalOf { LookSettings() }

/** false enquanto um painel sobreposto está aberto: a camada de baixo não recebe foco. */
val LocalInteractive = compositionLocalOf { true }

/**
 * Foco por chave: cada item focável tem uma chave estável ("fav:<pacote>", "pulltab"...).
 * Permite devolver o foco ao lugar certo quando um painel fecha. Chaves "ov:" pertencem a painéis.
 */
class FocusBus {
    var current by mutableStateOf<String?>(null)
    var lastMain by mutableStateOf<String?>(null)
    var request by mutableStateOf<String?>(null)
}

val LocalFocusBus = staticCompositionLocalOf { FocusBus() }

/**
 * Vidro: preenchimento translúcido + reflexo especular + borda iluminada.
 * Os fundos são gradientes suaves, então o "fosco" não precisa de desfoque em tempo real
 * (indisponível no Android 11) e o custo de GPU fica baixo.
 */
fun Modifier.glass(shape: Shape, look: LookSettings, solid: Float = 0f): Modifier {
    val rim = look.rim
    return if (look.lite) {
        clip(shape).background(Color(0xEE161A26)).border(1.dp, Color.White.copy(alpha = 0.10f + rim * 0.15f), shape)
    } else {
        clip(shape)
            // Camada própria: degradês e bordas viram uma textura reaproveitada entre quadros.
            // Medido na TV: sem ela (Auto) a navegação nos favoritos sobe de ~12 para ~34 ms por quadro.
            // Por isso painéis com conteúdo animado usam GlassSurface (vidro atrás, conteúdo fora da textura).
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            // Painéis sobrepostos ganham base escura: sem desfoque, o conteúdo de trás embaralharia a leitura
            .then(if (solid > 0f) Modifier.background(Color(0xFF0E1018).copy(alpha = solid)) else Modifier)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = (look.glass * 1.5f).coerceIn(0f, 1f)),
                        Color.White.copy(alpha = look.glass),
                        Color.White.copy(alpha = look.glass * 0.6f),
                    )
                )
            )
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = rim * 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.2f, -size.height * 0.1f),
                        radius = size.maxDimension * 0.6f,
                    )
                )
            }
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = rim),
                        Color.White.copy(alpha = rim * 0.08f),
                        Color.Transparent,
                        Color.White.copy(alpha = rim * 0.45f),
                    )
                ),
                shape,
            )
    }
}

/**
 * Painel de vidro cujo conteúdo se anima (gaveta, configurações): o vidro fica numa textura estática atrás
 * e o conteúdo é desenhado por cima. Assim o brilho de um ícone não força a GPU a refazer o painel inteiro.
 */
@Composable
fun GlassSurface(modifier: Modifier, shape: Shape, solid: Float = 0f, content: @Composable () -> Unit) {
    val look = LocalLook.current
    Box(modifier.clip(shape)) {
        Box(Modifier.matchParentSize().glass(shape, look, solid))
        content()
    }
}

/**
 * Item navegável pelo controle remoto.
 * OK = clique; segurar OK, botão Menu/Opções ou Info = ação secundária (menu do app).
 */
fun Modifier.tvClickable(
    key: String? = null,
    onFocus: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
    onClick: () -> Unit = {},
): Modifier = composed {
    val interactive = LocalInteractive.current
    val bus = LocalFocusBus.current
    val requester = remember { FocusRequester() }
    var downSeen by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }

    if (key != null) {
        LaunchedEffect(bus.request, interactive) {
            if (bus.request == key && interactive) {
                delay(80)
                runCatching { requester.requestFocus() }
                if (bus.request == key) bus.request = null
            }
        }
    }

    this
        .focusRequester(requester)
        .onFocusChanged { st ->
            onFocus(st.isFocused)
            if (st.isFocused && key != null) {
                bus.current = key
                if (!key.startsWith("ov:")) bus.lastMain = key
            }
        }
        .onPreviewKeyEvent { e ->
            if (onKey?.invoke(e) == true) return@onPreviewKeyEvent true
            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            val menu = e.key == Key.Menu || e.key == Key.Info
            when {
                ok && e.type == KeyEventType.KeyDown -> {
                    val repeat = e.nativeKeyEvent.repeatCount
                    if (repeat == 0) {
                        downSeen = true
                        longFired = false
                    } else if (downSeen && !longFired && onLongClick != null) {
                        longFired = true
                        onLongClick()
                    }
                    true
                }
                ok && e.type == KeyEventType.KeyUp -> {
                    // Só conta o clique se o botão foi pressionado neste item (evita "cliques fantasmas")
                    if (downSeen && !longFired) onClick()
                    downSeen = false
                    longFired = false
                    true
                }
                menu && onLongClick != null -> {
                    if (e.type == KeyEventType.KeyUp) onLongClick()
                    true
                }
                else -> false
            }
        }
        .focusable(enabled = interactive)
}

@Composable
fun Txt(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.White,
    maxLines: Int = 1,
    spacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, letterSpacing = spacing, lineHeight = lineHeight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
