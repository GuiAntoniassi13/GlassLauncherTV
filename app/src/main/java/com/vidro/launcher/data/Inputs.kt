package com.vidro.launcher.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

data class TvSource(val id: String, val label: String, val passthrough: Boolean, val type: Int)

/** Entradas da TV (antena, HDMI) pela API pública TvInputManager. */
class InputRepository(private val context: Context) {
    fun load(): List<TvSource> = try {
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        tim.tvInputList
            // Só entradas físicas do sistema (antena, HDMI); apps como Globoplay também registram "entradas"
            // (entradas de software são classificadas como TYPE_TUNER, por isso o filtro pelo serviço de hardware)
            .filter { !it.isHidden(context) && (it.serviceInfo.packageName == HW_INPUT_PKG || it.type in PHYSICAL_TYPES) }
            .map { TvSource(it.id, if (it.type == TvInputInfo.TYPE_TUNER) "TV" else it.loadLabel(context).toString(), it.isPassthroughInput, it.type) }
            .distinctBy { it.label }
            .sortedWith(compareBy({ it.passthrough }, { it.label }))
    } catch (e: Exception) {
        emptyList()
    }

    /** Só para entradas físicas (HDMI etc.); a antena é aberta pelo app de TV ao vivo. */
    fun intentFor(s: TvSource): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(s.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        private const val HW_INPUT_PKG = "com.mediatek.tvinput" // serviço de antena/HDMI desta TV
        private val PHYSICAL_TYPES = setOf(
            TvInputInfo.TYPE_HDMI, TvInputInfo.TYPE_COMPOSITE, TvInputInfo.TYPE_COMPONENT, TvInputInfo.TYPE_SVIDEO,
            TvInputInfo.TYPE_SCART, TvInputInfo.TYPE_VGA, TvInputInfo.TYPE_DVI, TvInputInfo.TYPE_DISPLAY_PORT,
        )

        fun describe(s: TvSource) = when (s.type) {
            TvInputInfo.TYPE_HDMI -> "Entrada HDMI"
            TvInputInfo.TYPE_TUNER -> "Canais de TV"
            TvInputInfo.TYPE_COMPOSITE -> "Vídeo composto"
            TvInputInfo.TYPE_COMPONENT -> "Vídeo componente"
            else -> "Entrada"
        }
    }
}
