package com.vidro.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

enum class Sky { SUN, PARTLY, CLOUD, FOG, RAIN, STORM }

data class DayForecast(val date: LocalDate, val sky: Sky, val max: Int, val min: Int, val rain: Int)

data class Weather(
    val temp: Int, val sky: Sky, val condition: String,
    val todayMax: Int, val todayMin: Int, val days: List<DayForecast>,
)

/** Previsão de Maringá/PR pelo Open-Meteo (gratuito, sem chave, sem dados pessoais). */
object WeatherRepository {
    private const val URL_MARINGA = "https://api.open-meteo.com/v1/forecast?latitude=-23.4205&longitude=-51.9333" +
        "&current=temperature_2m,weather_code" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
        "&timezone=America%2FSao_Paulo&forecast_days=6"

    fun sky(code: Int): Pair<Sky, String> = when {
        code == 0 -> Sky.SUN to "Céu limpo"
        code <= 2 -> Sky.PARTLY to "Parcialmente nublado"
        code == 3 -> Sky.CLOUD to "Nublado"
        code == 45 || code == 48 -> Sky.FOG to "Neblina"
        code in 51..57 -> Sky.RAIN to "Garoa"
        code in 61..67 || code in 80..82 -> Sky.RAIN to "Chuva"
        code >= 95 -> Sky.STORM to "Tempestade"
        else -> Sky.CLOUD to "Nublado"
    }

    suspend fun fetch(): Weather? = withContext(Dispatchers.IO) {
        try {
            val c = URL(URL_MARINGA).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            val body = try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
            val j = JSONObject(body)
            val cur = j.getJSONObject("current")
            val d = j.getJSONObject("daily")
            val (sky, cond) = sky(cur.getInt("weather_code"))
            val time = d.getJSONArray("time")
            val code = d.getJSONArray("weather_code")
            val mx = d.getJSONArray("temperature_2m_max")
            val mn = d.getJSONArray("temperature_2m_min")
            val pp = d.optJSONArray("precipitation_probability_max")
            val days = (1..5).map { i ->
                DayForecast(
                    LocalDate.parse(time.getString(i)), sky(code.getInt(i)).first,
                    Math.round(mx.getDouble(i)).toInt(), Math.round(mn.getDouble(i)).toInt(), pp?.optInt(i, 0) ?: 0,
                )
            }
            Weather(
                Math.round(cur.getDouble("temperature_2m")).toInt(), sky, cond,
                Math.round(mx.getDouble(0)).toInt(), Math.round(mn.getDouble(0)).toInt(), days,
            )
        } catch (e: Exception) {
            null
        }
    }
}
