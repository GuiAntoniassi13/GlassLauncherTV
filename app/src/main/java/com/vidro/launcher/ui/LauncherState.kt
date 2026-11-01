package com.vidro.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.*
import com.vidro.launcher.data.*
import kotlinx.coroutines.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class LauncherState(private val ctx: Context, private val scope: CoroutineScope) {
    private val prefs = Prefs(ctx)
    private val appsRepo = AppRepository(ctx)
    val previewsRepo = PreviewRepository(ctx)
    private val inputsRepo = InputRepository(ctx)
    val bus = FocusBus()

    var apps by mutableStateOf<List<AppInfo>>(emptyList()); private set
    var byPkg by mutableStateOf<Map<String, AppInfo>>(emptyMap()); private set
    var favorites by mutableStateOf<List<String>>(emptyList()); private set
    var look by mutableStateOf(prefs.loadLook()); private set
    var weather by mutableStateOf<Weather?>(null); private set
    var weatherTime by mutableStateOf(""); private set
    var sources by mutableStateOf<List<TvSource>>(emptyList()); private set
    var heroPkg by mutableStateOf<String?>(null); private set
    var heroPreviews by mutableStateOf<List<PreviewItem>>(emptyList()); private set
    var page by mutableIntStateOf(0)
    var drawerOpen by mutableStateOf(false); private set
    var settingsOpen by mutableStateOf(false); private set
    var menuPkg by mutableStateOf<String?>(null); private set
    var movingPkg by mutableStateOf<String?>(null); private set
    var sortFavFirst by mutableStateOf(prefs.sortFavFirst); private set
    var autoStart by mutableStateOf(prefs.autoStart); private set
    var toast by mutableStateOf<String?>(null); private set

    private var menuReturn: String? = null
    private var settingsReturn: String? = null
    private var previewJob: Job? = null
    private var heroJob: Job? = null

    val overlayOpen get() = drawerOpen || settingsOpen || menuPkg != null

    val drawerApps: List<AppInfo>
        get() = if (sortFavFirst) favorites.mapNotNull { byPkg[it] } + apps.filter { it.pkg !in favorites } else apps

    // ---------- Carregamento ----------

    fun reloadApps() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { appsRepo.load() }
            apps = list
            byPkg = list.associateBy { it.pkg }
            val saved = if (favorites.isEmpty()) prefs.loadFavorites() else favorites
            favorites = (saved ?: DEFAULT_FAVS.filter { p -> list.any { it.pkg == p } }.ifEmpty { list.take(6).map { it.pkg } })
                .filter { it in byPkg }
            if (heroPkg == null || heroPkg !in byPkg) favorites.firstOrNull()?.let { focusApp(it) }
            // App desinstalado com o menu dele aberto: fecha o menu para não deixar a tela sem foco
            if (menuPkg != null && menuPkg !in byPkg) { menuPkg = null; bus.request = favorites.firstOrNull()?.let { "fav:$it" } ?: "pulltab" }
            if (bus.current == null) bus.request = favorites.firstOrNull()?.let { "fav:$it" } ?: "pulltab"
        }
    }

    fun loadSources() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { inputsRepo.load() }
            if (list != sources) sources = list // evita recompor a tela a cada retorno ao launcher
        }
    }

    fun startWeather() {
        scope.launch {
            while (isActive) {
                WeatherRepository.fetch()?.let {
                    weather = it
                    weatherTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                }
                delay(if (weather == null) 60_000L else 30 * 60_000L)
            }
        }
    }

    /**
     * Favorito recebeu foco. Ao abrir/fechar painéis o Compose passa o foco de forma transitória pelos ícones;
     * por isso o destaque só muda se o foco continuar no mesmo favorito um instante depois.
     */
    fun onFavFocused(pkg: String) {
        if (overlayOpen) return
        heroJob?.cancel()
        heroJob = scope.launch {
            delay(90)
            if (bus.current == "fav:$pkg" && !overlayOpen) focusApp(pkg)
        }
    }

    fun focusApp(pkg: String) {
        if (pkg == heroPkg) return
        heroPkg = pkg
        heroPreviews = emptyList()
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(250) // só consulta se o foco parar no app
            heroPreviews = previewsRepo.forPackage(pkg)
        }
    }

    fun refreshPreviews() {
        previewsRepo.invalidate()
        val pkg = heroPkg ?: return
        heroPkg = null
        focusApp(pkg)
    }

    // ---------- Ações ----------

    fun launch(pkg: String) {
        val intent = appsRepo.launchIntent(pkg) ?: return showToast("Este app não pode ser aberto daqui")
        try { ctx.startActivity(intent) } catch (e: Exception) { showToast("Não foi possível abrir o app") }
    }

    fun launchPreview(item: PreviewItem) {
        val uri = item.intentUri
        if (uri != null) {
            try {
                ctx.startActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) { }
        }
        heroPkg?.let { launch(it) }
    }

    fun launchSource(s: TvSource?) {
        // Antena: abre o app de TV ao vivo, que volta ao último canal. Enviar a URI de lista de canais
        // (content://android.media.tv/channel?input=...) derrubava o tvcenter da Philips
        // (NumberFormatException: "channel" em TvSurfaceView.getCurrentChannelId; visto em 31/10/2026).
        if (s == null || !s.passthrough) return launch(LIVE_TV_PKG)
        try {
            ctx.startActivity(inputsRepo.intentFor(s))
        } catch (e: Exception) {
            launch(LIVE_TV_PKG)
        }
    }

    fun openAppInfo(pkg: String) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { showToast("Informações indisponíveis") }
    }

    fun openSystemSettings() {
        try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
    }

    /** Na Philips abre "Rede e Internet" (com.android.tv.settings/.connectivity.NetworkActivity). */
    fun openWifiSettings() {
        try { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { showToast("Ajustes de Wi-Fi indisponíveis") }
    }

    fun toggleFav(pkg: String) {
        if (pkg in favorites) {
            favorites = favorites - pkg
            if (menuReturn == "fav:$pkg") menuReturn = favorites.firstOrNull()?.let { "fav:$it" } ?: "pulltab"
            showToast("${byPkg[pkg]?.label} removido dos favoritos")
        } else {
            favorites = favorites + pkg
            showToast("${byPkg[pkg]?.label} adicionado aos favoritos")
        }
        prefs.saveFavorites(favorites)
    }

    fun startMove(pkg: String) {
        menuPkg = null
        drawerOpen = false
        page = 0
        movingPkg = pkg
        bus.request = "fav:$pkg"
        showToast("◀ ▶ para mover · OK para soltar")
    }

    fun moveFav(pkg: String, delta: Int) {
        val i = favorites.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in favorites.indices) return
        favorites = favorites.toMutableList().apply { this[i] = this[j]; this[j] = pkg }
        bus.request = "fav:$pkg"
    }

    fun endMove() {
        if (movingPkg == null) return
        movingPkg = null
        prefs.saveFavorites(favorites)
        showToast("Posição salva")
    }

    fun setSort(favFirst: Boolean) {
        sortFavFirst = favFirst
        prefs.sortFavFirst = favFirst
    }

    fun toggleAutoStart() {
        autoStart = !autoStart
        prefs.autoStart = autoStart
    }

    fun updateLook(change: (LookSettings) -> LookSettings) {
        look = change(look)
        prefs.saveLook(look)
    }

    // ---------- Painéis ----------

    fun openMenu(pkg: String) {
        menuReturn = bus.current
        menuPkg = pkg
        bus.request = "ov:menu:0"
    }

    fun closeMenu() {
        menuPkg = null
        bus.request = menuReturn
    }

    fun openDrawer() {
        drawerOpen = true
        bus.request = drawerApps.firstOrNull()?.let { "ov:drw:${it.pkg}" }
    }

    fun closeDrawer() {
        drawerOpen = false
        bus.request = "pulltab"
    }

    fun openSettings() {
        // Guarda a origem agora: ao abrir o painel, a lista de favoritos (montada depois) recebe foco passageiro
        settingsReturn = bus.current?.takeIf { !it.startsWith("ov:") } ?: bus.lastMain
        settingsOpen = true
        bus.request = "ov:set:glass"
    }

    fun closeSettings() {
        settingsOpen = false
        bus.request = settingsReturn ?: "pulltab"
    }

    /** Voltar: fecha o que estiver aberto; na tela inicial, volta aos favoritos. */
    fun back() {
        when {
            menuPkg != null -> closeMenu()
            settingsOpen -> closeSettings()
            drawerOpen -> closeDrawer()
            movingPkg != null -> endMove()
            page != 0 -> { page = 0; bus.request = "tab:0" }
            else -> bus.request = favorites.firstOrNull()?.let { "fav:$it" }
        }
    }

    /** Botão Home pressionado com o launcher já aberto. */
    fun onHome() {
        menuPkg = null
        settingsOpen = false
        drawerOpen = false
        movingPkg?.let { endMove() }
        page = 0
        bus.request = favorites.firstOrNull()?.let { "fav:$it" } ?: "pulltab"
    }

    fun showToast(msg: String) {
        toast = msg
        scope.launch {
            delay(2200)
            if (toast == msg) toast = null
        }
    }

    companion object {
        const val LIVE_TV_PKG = "com.mediatek.wwtv.tvcenter"
        val DEFAULT_FAVS = listOf(
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
            "com.globo.globotv", "com.spotify.tv.android", "com.google.android.youtube.tvmusic", "com.digitalseva.iptvplayer",
        )
    }
}
