package com.plan.ingsoft

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Todo el progreso y los ajustes viven en el teléfono (SharedPreferences). */
class Store(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("plan", Context.MODE_PRIVATE)

    // Hitos
    fun isHitoDone(id: String): Boolean = sp.contains("h_$id")
    fun setHito(id: String, done: Boolean) {
        val e = sp.edit()
        if (done) e.putLong("h_$id", LocalDate.now().toEpochDay()) else e.remove("h_$id")
        e.apply()
    }

    // Bloques del horario (por fecha)
    fun isBlockDone(date: LocalDate, idx: Int): Boolean = sp.getBoolean("b_${date}_$idx", false)
    fun setBlock(date: LocalDate, idx: Int, done: Boolean) {
        sp.edit().putBoolean("b_${date}_$idx", done).apply()
    }

    // Entrenamiento (semana 1..12, sesión 0..4)
    fun isTrainDone(week: Int, i: Int): Boolean = sp.getBoolean("t_${week}_$i", false)
    fun setTrain(week: Int, i: Int, done: Boolean) {
        sp.edit().putBoolean("t_${week}_$i", done).apply()
    }

    // Ajustes de notificaciones
    var notifOn: Boolean
        get() = sp.getBoolean("notif_on", true)
        set(v) { sp.edit().putBoolean("notif_on", v).apply() }

    var resumen: Boolean
        get() = sp.getBoolean("resumen", true)
        set(v) { sp.edit().putBoolean("resumen", v).apply() }

    fun notifPilar(p: String): Boolean = sp.getBoolean("n_$p", p != "Personal")
    fun setNotifPilar(p: String, v: Boolean) {
        sp.edit().putBoolean("n_$p", v).apply()
    }
}
