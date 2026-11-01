package com.vidro.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vidro.launcher.ui.LauncherScreen
import com.vidro.launcher.ui.LauncherState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var state: LauncherState

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = state.reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = LauncherState(applicationContext, lifecycleScope)
        state.reloadApps()
        state.loadSources()
        state.startWeather()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        setContent { LauncherScreen(state) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Botão Home com o launcher já aberto: volta ao início
        if (intent.hasCategory(Intent.CATEGORY_HOME)) state.onHome()
    }

    private var firstWindowFocus = true

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // O Compose foca o primeiro item da tela ao ganhar a janela; levamos o foco aos favoritos
        if (hasFocus && firstWindowFocus) {
            firstWindowFocus = false
            lifecycleScope.launch { delay(300); state.onHome() }
        }
    }

    override fun onResume() {
        super.onResume()
        state.refreshPreviews() // os apps atualizam seus destaques enquanto estão abertos
        state.loadSources() // no boot o serviço de entradas pode ainda não ter registrado todos os HDMI
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}
