package com.vidro.launcher.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

const val TAG = "VidroLauncher"

/** aspect segue TvContract.PreviewPrograms.ASPECT_RATIO_* (-1 = desconhecido). */
data class PreviewItem(val title: String, val subtitle: String, val posterUri: String?, val aspect: Int, val intentUri: String?)

/**
 * Destaques que cada app publica no Android TV ("canais" e "Assistir a seguir").
 * Usa apenas READ_EPG_DATA (permissão normal). Se o sistema não liberar, devolve lista vazia.
 */
class PreviewRepository(private val context: Context) {
    private val cache = HashMap<String, List<PreviewItem>>()

    suspend fun forPackage(pkg: String): List<PreviewItem> = withContext(Dispatchers.IO) {
        cache[pkg]?.let { return@withContext it }
        val items = query(TvContract.WatchNextPrograms.CONTENT_URI, pkg) + query(TvContract.PreviewPrograms.CONTENT_URI, pkg)
        val result = items.distinctBy { it.title }.take(4)
        cache[pkg] = result
        result
    }

    fun invalidate() = cache.clear()

    private fun query(uri: Uri, pkg: String): List<PreviewItem> {
        // Sem filtro na consulta: o TvProvider recusa "selection" para apps sem ACCESS_ALL_EPG_DATA
        val cols = arrayOf(
            TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
            TvContract.PreviewPrograms.COLUMN_TITLE,
            TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            TvContract.PreviewPrograms.COLUMN_INTENT_URI,
        )
        return try {
            context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
                val out = ArrayList<PreviewItem>()
                while (c.moveToNext() && out.size < 8) {
                    if (c.getString(0) != pkg) continue
                    val title = c.getString(1) ?: continue
                    out += PreviewItem(title, c.getString(2) ?: "", c.getString(3), if (c.isNull(4)) -1 else c.getInt(4), c.getString(5))
                }
                out
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Consulta $uri ($pkg) falhou: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }
}

/** Carregador mínimo de imagens (capas) com cache em memória. */
object Images {
    private val cache = LruCache<String, ImageBitmap>(24)

    suspend fun load(context: Context, uri: String, maxSide: Int = 480): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(uri)?.let { return@withContext it }
        try {
            val bytes = if (uri.startsWith("http")) {
                val c = URL(uri).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
            } else {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } ?: return@withContext null
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@withContext null
            bmp.asImageBitmap().also { cache.put(uri, it) }
        } catch (e: Exception) {
            null
        }
    }
}
