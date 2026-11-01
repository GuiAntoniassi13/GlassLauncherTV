package com.vidro.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Color as AColor

data class AppInfo(
    val pkg: String,
    val label: String,
    val category: String,
    val icon: ImageBitmap,
    val fullBleed: Boolean, // ícone já ocupa o quadrado inteiro (cantos opacos)
    val color: Color,       // cor dominante, usada no vidro e no brilho do foco
)

/** Lista e abre apps usando apenas o PackageManager (sem permissões especiais). */
class AppRepository(private val context: Context) {
    private val pm = context.packageManager

    fun load(): List<AppInfo> {
        val seen = LinkedHashMap<String, AppInfo>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || seen.containsKey(pkg)) continue
                // Apps de sistema só entram se forem feitos para TV; os do celular (LAUNCHER) são internos da Philips/MediaTek
                val system = ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                if (cat == Intent.CATEGORY_LAUNCHER && system) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val bmp = try {
                    ri.loadIcon(pm).toBitmap(192, 192, Bitmap.Config.ARGB_8888)
                } catch (e: Exception) {
                    continue
                }
                seen[pkg] = AppInfo(
                    pkg, label, categoryName(ri.activityInfo.applicationInfo),
                    bmp.asImageBitmap(), opaqueCorners(bmp), dominantColor(bmp),
                )
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    fun launchIntent(pkg: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun categoryName(ai: ApplicationInfo) = when (ai.category) {
        ApplicationInfo.CATEGORY_VIDEO -> "Vídeo"
        ApplicationInfo.CATEGORY_AUDIO -> "Música e áudio"
        ApplicationInfo.CATEGORY_GAME -> "Jogos"
        ApplicationInfo.CATEGORY_IMAGE -> "Fotos"
        ApplicationInfo.CATEGORY_NEWS -> "Notícias"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Produtividade"
        else -> "Aplicativo"
    }

    private fun opaqueCorners(b: Bitmap): Boolean {
        val m = 6
        return listOf(b.getPixel(m, m), b.getPixel(b.width - m, m), b.getPixel(m, b.height - m), b.getPixel(b.width - m, b.height - m))
            .all { AColor.alpha(it) > 240 }
    }

    private fun dominantColor(b: Bitmap): Color {
        val s = Bitmap.createScaledBitmap(b, 24, 24, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        val hsv = FloatArray(3)
        for (x in 0 until 24) for (y in 0 until 24) {
            val p = s.getPixel(x, y)
            if (AColor.alpha(p) < 200) continue
            AColor.colorToHSV(p, hsv)
            if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue // ignora cinzas, preto e branco
            r += AColor.red(p); g += AColor.green(p); bl += AColor.blue(p); n++
        }
        if (n < 8) return Color(0xFF64748B)
        return Color((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }
}
