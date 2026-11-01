package com.vidro.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidro.launcher.data.InputRepository
import com.vidro.launcher.data.LookSettings
import com.vidro.launcher.data.TvSource
import com.vidro.launcher.data.WeatherRepository
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR = Locale("pt", "BR")

@Composable
fun LauncherScreen(state: LauncherState) {
    val wall = wallOf(state.look.wall)
    val look = if (wall.black) state.look.copy(glass = state.look.glass * 0.5f) else state.look
    val accent = animateColorAsState(
        state.heroPkg?.let { state.byPkg[it]?.color } ?: wall.accent,
        tween(if (look.anim == 2) 0 else 600), label = "accent",
    )

    BackHandler { state.back() }

    // Com a gaveta aberta, a página por baixo fica quase toda coberta: deixa de ser desenhada
    // (alfa 0 mantém o estado e o foco) assim que a gaveta termina de subir. A GPU da TV agradece.
    var pageHidden by remember { mutableStateOf(false) }
    LaunchedEffect(state.drawerOpen) {
        if (state.drawerOpen) { delay(340); pageHidden = true } else pageHidden = false
    }

    CompositionLocalProvider(LocalLook provides look, LocalFocusBus provides state.bus) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Fundo estático numa camada própria (com vinheta): a GPU só o redesenha ao trocar de papel de parede
            Wallpaper(wall.id, vignette = !wall.black)

            CompositionLocalProvider(LocalInteractive provides !state.overlayOpen) {
                Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 22.dp)) {
                    Header(state)
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.graphicsLayer { alpha = if (pageHidden) 0f else 1f }) {
                        when (state.page) {
                            0 -> HomePage(state, accent)
                            else -> InputsPage(state)
                        }
                    }
                }
            }

            if (state.overlayOpen) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)))

            CompositionLocalProvider(LocalInteractive provides (state.menuPkg == null && !state.settingsOpen)) {
                AnimatedVisibility(
                    state.drawerOpen,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(320)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(260)) { it } + fadeOut(),
                ) { Drawer(state) }
            }
            CompositionLocalProvider(LocalInteractive provides true) {
                AnimatedVisibility(
                    state.settingsOpen,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(tween(320)) { it } + fadeIn(),
                    exit = slideOutHorizontally(tween(260)) { it } + fadeOut(),
                ) { SettingsPanel(state) }
                state.menuPkg?.let { AppMenu(state, it) }
            }
            ToastView(state, Modifier.align(Alignment.BottomCenter))
        }
    }
}

// ---------------- Cabeçalho ----------------

@Composable
private fun Header(state: LauncherState) {
    val look = LocalLook.current
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Clock(look.clock24, Modifier.width(170.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.glass(CircleShape, look).padding(4.dp)) {
            listOf("Início", "Entradas").forEachIndexed { i, label -> TabChip(label, i, state) }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.width(170.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("wifi", wifi = true) { state.openWifiSettings() }
            Spacer(Modifier.width(10.dp))
            RoundIconButton("settings", onLongClick = { state.openSystemSettings() }) { state.openSettings() }
        }
    }
}

@Composable
private fun Clock(h24: Boolean, modifier: Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L * (60 - now.second).coerceAtLeast(1))
        }
    }
    Column(modifier) {
        Txt(now.format(DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a", PT_BR)), 32.sp, weight = FontWeight.Light, spacing = (-1).sp)
        Txt(now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)).replaceFirstChar { it.uppercase() }, 10.5.sp, color = Color.White.copy(alpha = .72f), weight = FontWeight.Medium)
    }
}

@Composable
private fun TabChip(label: String, index: Int, state: LauncherState) {
    var f by remember { mutableStateOf(false) }
    val active = state.page == index
    val scale by animateFloatAsState(if (f) 1.06f else 1f, focusSpec(), label = "tab")
    Box(
        Modifier
            .tvClickable("tab:$index", onFocus = { f = it }) { state.page = index }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (f) Color.White.copy(alpha = .94f) else if (active) Color.White.copy(alpha = .16f) else Color.Transparent)
            .padding(horizontal = 17.dp, vertical = 8.dp)
    ) {
        Txt(label, 12.sp, weight = FontWeight.SemiBold, color = if (f) INK else if (active) Color.White else Color.White.copy(alpha = .72f))
    }
}

@Composable
private fun RoundIconButton(key: String, wifi: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val look = LocalLook.current
    val scale by animateFloatAsState(if (f) 1.14f else 1f, focusSpec(), label = "round")
    Box(
        Modifier
            .tvClickable(key, onFocus = { f = it }, onLongClick = onLongClick, onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(34.dp)
            .then(if (f) Modifier.clip(CircleShape).background(Color.White) else Modifier.glass(CircleShape, look)),
        contentAlignment = Alignment.Center,
    ) {
        val c = if (f) INK else Color.White
        if (wifi) Canvas(Modifier.size(16.dp)) { // leque de Wi-Fi: três arcos e um ponto
            val w = size.width
            val base = Offset(w / 2, w * .86f)
            for (k in 1..3) {
                val r = w * (.17f + .23f * k)
                drawArc(c, 225f, 90f, false, Offset(base.x - r, base.y - r), Size(r * 2, r * 2), style = Stroke(w * .12f, cap = StrokeCap.Round))
            }
            drawCircle(c, w * .09f, base)
        } else Canvas(Modifier.size(15.dp)) { // engrenagem simplificada
            val r = size.width / 2
            val center = Offset(r, r)
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                drawLine(c, Offset(center.x + (r * .55f) * kotlin.math.cos(a).toFloat(), center.y + (r * .55f) * kotlin.math.sin(a).toFloat()),
                    Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat()), size.width * .16f, StrokeCap.Round)
            }
            drawCircle(c, r * .58f, center, style = Stroke(size.width * .14f))
        }
    }
}

// ---------------- Início ----------------

@Composable
private fun HomePage(state: LauncherState, accent: State<Color>) {
    val look = LocalLook.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(215.dp)) {
            Hero(state, accent, Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(15.dp))
            Column(Modifier.width(264.dp).fillMaxHeight()) {
                SourcesCard(state)
                Spacer(Modifier.height(10.dp))
                WeatherCard(state, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Txt("Favoritos", 13.sp, weight = FontWeight.Bold)
            Spacer(Modifier.width(9.dp))
            Txt("OK abrir · segure OK ou ☰ para opções", 9.5.sp, color = Color.White.copy(alpha = .5f))
        }
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Box(Modifier.padding(top = 50.dp).fillMaxWidth().height(85.dp).glass(RoundedCornerShape(20.dp), look))
            val favApps = state.favorites.mapNotNull { state.byPkg[it] }
            // Fileira comum (não "lazy"): são poucos favoritos, e a LazyRow descartava ícones ao rolar,
            // o que fazia a navegação ▼ falhar depois de ir e voltar na fileira
            Row(
                Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(start = 6.dp, end = 30.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                favApps.forEach { app ->
                    key(app.pkg) {
                        AppTile(app, 89.dp, "fav:${app.pkg}", state, downTo = "pulltab", onFocused = { state.onFavFocused(app.pkg) })
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PullTab(state) }
    }
}

@Composable
private fun Hero(state: LauncherState, accent: State<Color>, modifier: Modifier) {
    val look = LocalLook.current
    val wall = wallOf(state.look.wall)
    val app = state.heroPkg?.let { state.byPkg[it] }
    val previews = state.heroPreviews
    Box(modifier.glass(RoundedCornerShape(22.dp), look)) {
        if (!wall.black) {
            Box(Modifier.matchParentSize().drawBehind {
                drawRect(Brush.radialGradient(listOf(accent.value.copy(alpha = .5f), Color.Transparent), Offset(size.width * .85f, size.height * .5f), size.width * .45f))
            })
        }
        if (app == null) {
            Txt("Carregando apps…", 14.sp, Modifier.align(Alignment.Center), color = Color.White.copy(alpha = .6f))
            return@Box
        }
        Column(Modifier.padding(start = 30.dp, top = 28.dp).width(250.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (previews.isNotEmpty()) {
                    Image(app.icon, null, Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(7.dp))
                }
                Txt(if (previews.isNotEmpty()) "EM DESTAQUE" else "FAVORITO", 9.sp, weight = FontWeight.Bold, spacing = 1.5.sp, color = Color.White.copy(alpha = .72f))
            }
            Spacer(Modifier.height(6.dp))
            Txt(app.label, 38.sp, weight = FontWeight.ExtraBold, maxLines = 2, spacing = (-1).sp, lineHeight = 40.sp)
            Txt(app.category, 13.sp, color = Color.White.copy(alpha = .72f), weight = FontWeight.Medium)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Abrir", "hero:open", play = true) { state.launch(app.pkg) }
                PillButton("Opções", "hero:menu") { state.openMenu(app.pkg) }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 22.dp)) {
            if (previews.isNotEmpty()) PreviewArea(state, previews, app.color)
            else BigIcon(app, look)
        }
    }
}

@Composable
private fun PreviewArea(state: LauncherState, items: List<com.vidro.launcher.data.PreviewItem>, color: Color) {
    val aspect = items.first().aspect
    val portrait = aspect == 4 || aspect == 5
    val square = aspect == 3
    when {
        portrait || square -> Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            items.take(3).forEachIndexed { i, it ->
                PreviewCard(it, if (square) 100.dp else 98.dp, if (square) 100.dp else 147.dp, "pv:$i", color) { state.launchPreview(it) }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items.take(4).chunked(2).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEachIndexed { c, it -> PreviewCard(it, 145.dp, 80.dp, "pv:${r * 2 + c}", color) { state.launchPreview(it) } }
                }
            }
        }
    }
}

@Composable
private fun BigIcon(app: com.vidro.launcher.data.AppInfo, look: LookSettings) {
    val shape = RoundedCornerShape(36.dp)
    Box(
        Modifier
            .padding(end = 40.dp)
            .graphicsLayer { rotationZ = -6f }
            .size(132.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(app.color, Color.White, .12f), lerp(app.color, Color.Black, .55f))))
            .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .7f), Color.Transparent, Color.White.copy(alpha = .3f))), shape)
    ) {
        Image(app.icon, null, Modifier.align(Alignment.Center).fillMaxSize(if (app.fullBleed) 1f else .64f).clip(if (app.fullBleed) shape else RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
        if (!look.lite) Box(Modifier.matchParentSize().drawBehind {
            drawRect(Brush.radialGradient(0f to Color.White.copy(alpha = .45f), .52f to Color.White.copy(alpha = .08f), .53f to Color.Transparent,
                center = Offset(size.width / 2, -size.height * .18f), radius = size.width * .95f))
        })
    }
}

@Composable
private fun SourcesCard(state: LauncherState) {
    val look = LocalLook.current
    val shown: List<TvSource?> = state.sources.take(3).ifEmpty { listOf(null) }
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), look).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("ENTRADA", 8.5.sp, weight = FontWeight.Bold, spacing = 1.2.sp, color = Color.White.copy(alpha = .72f))
        Spacer(Modifier.width(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEachIndexed { i, s ->
                ChipButton(s?.label ?: "TV", "chip:${s?.id ?: "tv"}", selected = i == 0, dot = i == 0) { state.launchSource(s) }
            }
        }
    }
}

@Composable
private fun WeatherCard(state: LauncherState, modifier: Modifier) {
    val look = LocalLook.current
    var f by remember { mutableStateOf(false) }
    val w = state.weather
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .tvClickable("weather", onFocus = { f = it }) {
                state.showToast(if (w != null) "Maringá · atualizado às ${state.weatherTime}" else "Sem conexão com o serviço do tempo")
            }
            .glass(shape, look)
            .then(if (f) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (w == null) {
            Txt("Maringá, PR", 12.sp, weight = FontWeight.Bold)
            Txt("Carregando previsão…", 10.sp, color = Color.White.copy(alpha = .6f))
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkyIcon(w.sky, 44.dp)
            Spacer(Modifier.width(6.dp))
            Txt("${w.temp}°", 40.sp, weight = FontWeight.Light, spacing = (-1).sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Txt("Maringá, PR", 12.sp, weight = FontWeight.Bold)
                Txt(w.condition, 10.sp, color = Color.White.copy(alpha = .72f))
                Txt("Máx ${w.todayMax}° · Mín ${w.todayMin}°", 8.5.sp, color = Color.White.copy(alpha = .5f))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .12f)))
        Row(Modifier.fillMaxWidth()) {
            w.days.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, PT_BR).replace(".", "").replaceFirstChar { it.uppercase() },
                        8.5.sp, weight = FontWeight.Bold, color = Color.White.copy(alpha = .72f))
                    SkyIcon(d.sky, 24.dp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Txt("${d.max}°", 9.5.sp, weight = FontWeight.Bold)
                        Spacer(Modifier.width(2.dp))
                        Txt("${d.min}°", 8.sp, color = Color.White.copy(alpha = .5f))
                    }
                    Txt("${d.rain}%", 7.5.sp, weight = FontWeight.SemiBold, color = Color(0xFF7DD3FC))
                }
            }
        }
    }
}

@Composable
private fun PullTab(state: LauncherState) {
    val look = LocalLook.current
    var f by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (f) 1.08f else 1f, focusSpec(), label = "pull")
    val fg = if (f) INK else Color.White.copy(alpha = .8f)
    Row(
        Modifier
            .tvClickable("pulltab", onFocus = { f = it }) { state.openDrawer() }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (f) Modifier.clip(CircleShape).background(Color.White.copy(alpha = .94f)) else Modifier.glass(CircleShape, look))
            .padding(horizontal = 17.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(12.dp)) {
            drawPath(Path().apply { moveTo(size.width * .15f, size.height * .65f); lineTo(size.width / 2, size.height * .3f); lineTo(size.width * .85f, size.height * .65f) },
                fg, style = Stroke(size.width * .16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.width(7.dp))
        Txt("Todos os apps", 11.sp, weight = FontWeight.Bold, color = fg)
        Spacer(Modifier.width(7.dp))
        Box(Modifier.clip(CircleShape).background(if (f) Color.Black.copy(alpha = .1f) else Color.White.copy(alpha = .14f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
            Txt("${state.apps.size}", 8.sp, weight = FontWeight.Bold, color = fg)
        }
    }
}

// ---------------- Entradas ----------------

@Composable
private fun InputsPage(state: LauncherState) {
    val look = LocalLook.current
    Column {
        Txt("Entradas", 22.sp, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        val list: List<TvSource?> = state.sources.ifEmpty { listOf(null) }
        list.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { s ->
                    var f by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(if (f) 1.05f else 1f, focusSpec(), label = "inp")
                    val shape = RoundedCornerShape(20.dp)
                    Column(
                        Modifier
                            .tvClickable("src:${s?.id ?: "tv"}", onFocus = { f = it }) { state.launchSource(s) }
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .size(276.dp, 118.dp)
                            .glass(shape, look)
                            .then(if (f) Modifier.border(2.dp, Color.White, shape) else Modifier)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Canvas(Modifier.size(26.dp)) {
                            val sw = size.width * .07f
                            drawRoundRect(Color.White, Offset(0f, size.height * .12f), androidx.compose.ui.geometry.Size(size.width, size.height * .6f),
                                androidx.compose.ui.geometry.CornerRadius(size.width * .1f), style = Stroke(sw))
                            drawLine(Color.White, Offset(size.width * .3f, size.height * .92f), Offset(size.width * .7f, size.height * .92f), sw, StrokeCap.Round)
                        }
                        Column {
                            Txt(s?.label ?: "TV", 18.sp, weight = FontWeight.ExtraBold)
                            Txt(s?.let { InputRepository.describe(it) } ?: "Canais de TV", 10.sp, color = Color.White.copy(alpha = .72f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------- Gaveta "Todos os apps" ----------------

@Composable
private fun Drawer(state: LauncherState) {
    GlassSurface(
        Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(430.dp), // três fileiras inteiras (até 24 apps) sem rolagem cortando ícones
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), solid = .92f,
    ) { Column(Modifier.padding(top = 18.dp)) {
        Row(Modifier.padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Todos os apps", 20.sp, weight = FontWeight.ExtraBold)
            Spacer(Modifier.width(16.dp))
            ChipButton("A–Z", "ov:sort:az", selected = !state.sortFavFirst) { state.setSort(false) }
            Spacer(Modifier.width(7.dp))
            ChipButton("Favoritos primeiro", "ov:sort:fav", selected = state.sortFavFirst) { state.setSort(true) }
            Spacer(Modifier.weight(1f))
            Txt("← Voltar para recolher", 9.sp, color = Color.White.copy(alpha = .5f))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.drawerApps, key = { it.pkg }) { app ->
                Box(contentAlignment = Alignment.Center) { AppTile(app, 70.dp, "ov:drw:${app.pkg}", state, restShadow = 0.dp) }
            }
        }
    } }
}

// ---------------- Configurações ----------------

@Composable
private fun SettingsPanel(state: LauncherState) {
    val look = state.look
    GlassSurface(Modifier.padding(24.dp).width(360.dp).fillMaxHeight(), RoundedCornerShape(24.dp), solid = .92f) { Column(
        Modifier.fillMaxHeight().padding(horizontal = 18.dp, vertical = 22.dp)
    ) {
        Txt("Aparência", 22.sp, weight = FontWeight.ExtraBold)
        Txt("◀ ▶ ajusta · aplicado na hora", 9.5.sp, color = Color.White.copy(alpha = .5f))
        Spacer(Modifier.height(12.dp))
        SettingRow("ov:set:glass", "Transparência do vidro", onAdjust = { d -> state.updateLook { it.copy(glass = (it.glass + d * .02f).coerceIn(.04f, .34f)) } }) {
            SliderBar((look.glass - .04f) / .30f, "${((look.glass - .04f) / .30f * 100).toInt()}%")
        }
        SettingRow("ov:set:rim", "Brilho das bordas", onAdjust = { d -> state.updateLook { it.copy(rim = (it.rim + d * .05f).coerceIn(0f, 1f)) } }) {
            SliderBar(look.rim, "${(look.rim * 100).toInt()}%")
        }
        SettingRow("ov:set:anim", "Animações", onAdjust = { d -> state.updateLook { it.copy(anim = (it.anim + d + 3) % 3) } }) {
            Segmented(listOf("Completas", "Suaves", "Desligadas"), look.anim)
        }
        SettingRow("ov:set:lite", "Modo leve", "Painéis opacos e sem reflexos, para máxima fluidez", onAdjust = { state.updateLook { it.copy(lite = !it.lite) } }) {
            Toggle(look.lite)
        }
        SettingRow("ov:set:wall", "Papel de parede", wallOf(look.wall).name, onAdjust = { d ->
            state.updateLook { s -> val i = WALLS.indexOfFirst { it.id == s.wall }; s.copy(wall = WALLS[(i + d + WALLS.size) % WALLS.size].id) }
        }) {}
        Row(Modifier.padding(start = 13.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WALLS.forEach { w ->
                val shape = RoundedCornerShape(6.dp)
                Box(Modifier.size(36.dp, 20.dp).clip(shape).border(if (w.id == look.wall) 2.dp else 1.dp, Color.White.copy(alpha = if (w.id == look.wall) 1f else .25f), shape)) {
                    Wallpaper(w.id)
                }
            }
        }
        SettingRow("ov:set:clock", "Relógio 24 horas", onAdjust = { state.updateLook { it.copy(clock24 = !it.clock24) } }) {
            Toggle(look.clock24)
        }
        SettingRow("ov:set:autostart", "Abrir ao ligar a TV", "Volta para o Vidro quando a TV liga num canal", onAdjust = { state.toggleAutoStart() }) {
            Toggle(state.autoStart)
        }
        SettingRow("ov:set:system", "Configurações da TV", "Abre os ajustes do Android TV", onClick = { state.closeSettings(); state.openSystemSettings() }) {}
        Spacer(Modifier.weight(1f))
        Txt("Vidro Launcher ${com.vidro.launcher.BuildConfig.VERSION_NAME}", 8.5.sp, color = Color.White.copy(alpha = .35f))
    } }
}

@Composable
private fun SettingRow(
    key: String,
    label: String,
    sub: String? = null,
    onAdjust: ((Int) -> Unit)? = null,
    onClick: () -> Unit = { onAdjust?.invoke(1) },
    content: @Composable RowScope.() -> Unit,
) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .tvClickable(
                key, onFocus = { f = it },
                onKey = { e ->
                    if (onAdjust != null && e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) {
                        onAdjust(if (e.key == Key.DirectionLeft) -1 else 1); true
                    } else false
                },
                onClick = onClick,
            )
            .clip(shape)
            .background(if (f) Color.White.copy(alpha = .16f) else Color.Transparent)
            .then(if (f) Modifier.border(1.5.dp, Color.White.copy(alpha = .75f), shape) else Modifier)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(label, 11.5.sp, weight = FontWeight.SemiBold)
            sub?.let { Txt(it, 8.sp, color = Color.White.copy(alpha = .5f), maxLines = 2) }
        }
        content()
    }
}

@Composable
private fun SliderBar(fraction: Float, value: String) {
    val f = fraction.coerceIn(0f, 1f)
    Box(Modifier.width(100.dp).height(14.dp)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)))
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(f.coerceAtLeast(.001f)).height(5.dp).clip(CircleShape).background(Color.White))
        Box(Modifier.align(Alignment.CenterStart).offset(x = 100.dp * f - 7.dp).size(14.dp).clip(CircleShape).background(Color.White))
    }
    Txt(value, 10.sp, Modifier.width(40.dp).padding(start = 8.dp), color = Color.White.copy(alpha = .72f), weight = FontWeight.SemiBold)
}

@Composable
private fun Segmented(options: List<String>, selected: Int) {
    Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .1f)).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            Box(Modifier.clip(CircleShape).background(if (i == selected) Color.White else Color.Transparent).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Txt(o, 9.sp, weight = FontWeight.SemiBold, color = if (i == selected) INK else Color.White.copy(alpha = .72f))
            }
        }
    }
}

@Composable
private fun Toggle(on: Boolean) {
    val x by animateDpAsState(if (on) 16.dp else 0.dp, label = "toggle")
    Box(Modifier.size(36.dp, 20.dp).clip(CircleShape).background(if (on) Color(0xFF34C759) else Color.White.copy(alpha = .2f)).padding(2.dp)) {
        Box(Modifier.offset(x = x).size(16.dp).clip(CircleShape).background(Color.White))
    }
}

// ---------------- Menu do app ----------------

@Composable
private fun AppMenu(state: LauncherState, pkg: String) {
    val app = state.byPkg[pkg] ?: return
    val isFav = pkg in state.favorites
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(240.dp).glass(RoundedCornerShape(18.dp), LocalLook.current, solid = .88f).padding(8.dp)) {
            Txt(app.label.uppercase(), 9.sp, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), weight = FontWeight.Bold, spacing = 1.5.sp, color = Color.White.copy(alpha = .5f))
            MenuItem("ov:menu:0", "Abrir") { state.closeMenu(); state.launch(pkg) }
            MenuItem("ov:menu:1", if (isFav) "Remover dos favoritos" else "Adicionar aos favoritos") { state.toggleFav(pkg); state.closeMenu() }
            if (isFav) MenuItem("ov:menu:2", "Mudar de lugar") { state.startMove(pkg) }
            MenuItem("ov:menu:3", "Informações do app") { state.closeMenu(); state.openAppInfo(pkg) }
        }
    }
}

@Composable
private fun MenuItem(key: String, text: String, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .tvClickable(key, onFocus = { f = it }, onClick = onClick)
            .clip(RoundedCornerShape(11.dp))
            .background(if (f) Color.White else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Txt(text, 11.5.sp, weight = FontWeight.SemiBold, color = if (f) INK else Color.White)
    }
}

// ---------------- Aviso ----------------

@Composable
private fun ToastView(state: LauncherState, modifier: Modifier) {
    var last by remember { mutableStateOf("") }
    state.toast?.let { last = it }
    AnimatedVisibility(state.toast != null, modifier.padding(bottom = 34.dp), enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
        Box(Modifier.glass(CircleShape, LocalLook.current, solid = .8f).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Txt(last, 12.sp, weight = FontWeight.SemiBold)
        }
    }
}
