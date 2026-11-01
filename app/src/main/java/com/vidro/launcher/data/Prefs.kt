package com.vidro.launcher.data

import android.content.Context

/** Aparência ajustável no painel de configurações. anim: 0 = completas, 1 = suaves, 2 = desligadas. */
data class LookSettings(
    val glass: Float = 0.14f,
    val rim: Float = 0.55f,
    val anim: Int = 0,
    val lite: Boolean = false,
    val wall: String = "aurora",
    val clock24: Boolean = true,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("vidro", Context.MODE_PRIVATE)

    fun loadLook() = LookSettings(
        glass = sp.getFloat("glass", 0.14f),
        rim = sp.getFloat("rim", 0.55f),
        anim = sp.getInt("anim", 0),
        lite = sp.getBoolean("lite", false),
        wall = sp.getString("wall", "aurora") ?: "aurora",
        clock24 = sp.getBoolean("clock24", true),
    )

    fun saveLook(s: LookSettings) = sp.edit()
        .putFloat("glass", s.glass).putFloat("rim", s.rim).putInt("anim", s.anim)
        .putBoolean("lite", s.lite).putString("wall", s.wall).putBoolean("clock24", s.clock24)
        .apply()

    fun loadFavorites(): List<String>? = sp.getString("fav", null)?.split(',')?.filter { it.isNotBlank() }
    fun saveFavorites(list: List<String>) = sp.edit().putString("fav", list.joinToString(",")).apply()

    var autoStart: Boolean
        get() = sp.getBoolean("autoStart", true)
        set(v) = sp.edit().putBoolean("autoStart", v).apply()

    var lastAutoStartBoot: Int
        get() = sp.getInt("lastAutoStartBoot", -2)
        set(v) = sp.edit().putInt("lastAutoStartBoot", v).apply()

    var sortFavFirst: Boolean
        get() = sp.getBoolean("sortFav", false)
        set(v) = sp.edit().putBoolean("sortFav", v).apply()
}
