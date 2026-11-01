package com.vidro.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.vidro.launcher.data.Prefs
import com.vidro.launcher.data.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ao ligar, o app de TV da Philips/MediaTek assume a tela e sintoniza um canal.
 * Aqui o Vidro volta para a frente logo depois (opção "Abrir ao ligar a TV").
 * Exige a permissão "Sobrepor a outros apps", pois o Android 10+ bloqueia abrir telas a partir do segundo plano.
 */
object AutoStart {
    fun bringToFront(ctx: Context, reason: String) {
        if (!Prefs(ctx).autoStart) return
        Log.i(TAG, "Início automático: $reason")
        try {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Início automático falhou: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

/** TV ligada a partir de desligada: aguarda o app de TV assumir e então traz o Vidro (duas tentativas). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // O receptor é público (exigência do aviso de boot): ignora chamadas de outros apps com outra ação
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            try {
                delay(6_000); AutoStart.bringToFront(app, "boot (1)")
                delay(12_000); AutoStart.bringToFront(app, "boot (2)")
            } finally {
                pending.finish()
            }
        }
    }
}

class VidroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val main = Handler(Looper.getMainLooper())

        // Logo após o boot o sistema abre o Vidro como tela inicial e, ~1 s depois, o app de TV toma a frente.
        // O aviso BOOT_COMPLETED só chega ~30 s mais tarde; por isso, se o processo nasceu no boot, já reage aqui.
        val bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val prefs = Prefs(this)
        if (SystemClock.elapsedRealtime() < 180_000 && prefs.lastAutoStartBoot != bootCount) {
            prefs.lastAutoStartBoot = bootCount
            main.postDelayed({ AutoStart.bringToFront(applicationContext, "início do sistema (1)") }, 4_000)
            main.postDelayed({ AutoStart.bringToFront(applicationContext, "início do sistema (2)") }, 10_000)
        }

        // TV saindo do modo de espera: a tela liga sem reiniciar o sistema

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                main.postDelayed({ AutoStart.bringToFront(applicationContext, "tela ligada (1)") }, 4_000)
                main.postDelayed({ AutoStart.bringToFront(applicationContext, "tela ligada (2)") }, 10_000)
            }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
    }
}
