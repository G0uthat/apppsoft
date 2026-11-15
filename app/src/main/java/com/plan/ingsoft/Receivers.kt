package com.plan.ingsoft

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate

/** Suena la alarma: muestra el aviso y programa el siguiente. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra("date")?.let { LocalDate.parse(it) }
        val idx = intent.getIntExtra("idx", -2)
        if (date != null && idx >= -1) {
            Notifier.mostrar(context, date, idx)
        }
        Notifier.scheduleNext(context)
        PlanWidget.updateAll(context)
    }
}

/** Botones de las notificaciones: "Bloque hecho" y "Hito listo". */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val st = Store(context)
        val plan = PlanLoader.get(context)
        when (intent.getStringExtra("kind")) {
            "block" -> {
                val date = LocalDate.parse(intent.getStringExtra("date"))
                st.setBlock(date, intent.getIntExtra("idx", 0), true)
            }
            "hito" -> {
                val pista = intent.getStringExtra("pista") ?: "A"
                plan.siguienteHito(st, pista)?.let { st.setHito(it.id, true) }
            }
        }
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
        PlanWidget.updateAll(context)
    }
}

/** Reinicio del teléfono, cambio de hora/zona o actualización de la app: reprograma. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.createChannels(context)
        Notifier.scheduleNext(context)
        PlanWidget.updateAll(context)
    }
}
