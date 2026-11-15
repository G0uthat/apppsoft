package com.plan.ingsoft

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Notifier {
    const val CH_BLOQUES = "bloques"
    const val CH_RESUMEN = "resumen"

    data class Evento(val at: LocalDateTime, val date: LocalDate, val idx: Int)

    private data class Msg(
        val title: String,
        val short: String,
        val long: String,
        val channel: String,
        val pista: String?
    )

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val bloques = NotificationChannel(
            CH_BLOQUES, "Bloques del horario", NotificationManager.IMPORTANCE_HIGH
        )
        bloques.description = "Aviso al empezar cada bloque de tu horario"
        nm.createNotificationChannel(bloques)
        val resumen = NotificationChannel(
            CH_RESUMEN, "Resumen diario", NotificationManager.IMPORTANCE_DEFAULT
        )
        resumen.description = "Qué te toca hoy y cómo vas"
        nm.createNotificationChannel(resumen)
    }

    private fun habilitado(st: Store, b: Block): Boolean =
        b.act.startsWith("Marcar") || st.notifPilar(b.pilar)

    /** Próximos eventos de notificación (bloques + resumen de las 08:00). */
    fun eventos(ctx: Context, desde: LocalDateTime, dias: Int = 14): List<Evento> {
        val plan = PlanLoader.get(ctx)
        val st = Store(ctx)
        if (!st.notifOn) return emptyList()
        val out = ArrayList<Evento>()
        var d = if (desde.toLocalDate().isBefore(plan.inicio)) plan.inicio else desde.toLocalDate()
        for (n in 0 until dias) {
            if (st.resumen) out.add(Evento(d.atTime(8, 0), d, -1))
            for (b in plan.bloquesDe(d)) {
                if (habilitado(st, b)) out.add(Evento(d.atTime(b.ini), d, b.idx))
            }
            d = d.plusDays(1)
        }
        return out.filter { it.at.isAfter(desde) }.sortedBy { it.at }
    }

    private fun alarmPI(ctx: Context, ev: Evento?): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
        if (ev != null) {
            i.putExtra("date", ev.date.toString())
            i.putExtra("idx", ev.idx)
        }
        return PendingIntent.getBroadcast(
            ctx, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Programa UNA alarma: la del siguiente evento. Al sonar, programa la siguiente. */
    fun scheduleNext(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val ev = eventos(app, LocalDateTime.now()).firstOrNull()
        if (ev == null) {
            am.cancel(alarmPI(app, null))
            return
        }
        val pi = alarmPI(app, ev)
        val millis = ev.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exacta = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exacta) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun contenido(ctx: Context, date: LocalDate, idx: Int): Msg? {
        val plan = PlanLoader.get(ctx)
        val st = Store(ctx)
        if (idx < 0) {
            val partes = ArrayList<String>()
            val a = plan.siguienteHito(st, "A")
            partes.add("Pista A: " + (a?.let { "${it.id} ${it.texto}" } ?: "completada"))
            val b = plan.siguienteHito(st, "B")
            if (b != null) partes.add("Pista B: ${b.id} ${b.texto}")
            val ses = plan.sesionDelDia(date)
            if (ses == "A" || ses == "B") partes.add("Entreno: sesión $ses")
            if (ses == "CAMINATA") partes.add("Entreno: caminata rápida")
            if (ses == "MOVILIDAD") partes.add("Entreno: movilidad suave")
            val estado = plan.estadoGlobal(st, date)
            return Msg(
                "☀️ Tu día · " + date.format(FMT_CORTO),
                partes.firstOrNull() ?: "Revisa tu plan",
                partes.joinToString("\n") + "\n" + estado,
                CH_RESUMEN, null
            )
        }
        val b = plan.horario.getOrNull(idx) ?: return null
        val title = "${pilarEmoji(b.pilar)} ${b.ini}–${b.fin} · ${b.pilar}"
        if (b.pilar == "Estudio") {
            val pista = if (b.act.startsWith("Segundo curso")) "B" else "A"
            val h = plan.siguienteHito(st, pista)
            if (h != null) {
                val short = "Pista $pista · ${h.curso}: ${h.texto}"
                val long = "$short\nDónde: ${h.donde}\n(${h.horas} h estimadas)"
                return Msg(title, short, long, CH_BLOQUES, pista)
            }
            return Msg(title, b.act, b.act, CH_BLOQUES, null)
        }
        if (b.pilar == "Entrenamiento") {
            val sesion = when {
                b.act.contains("SESIÓN A") -> "A"
                b.act.contains("SESIÓN B") -> "B"
                else -> null
            }
            if (sesion != null) {
                val fase = plan.fase(plan.semanaActual(date))
                return Msg(
                    title, b.act, b.act + "\n" + plan.resumenEjercicios(sesion, fase),
                    CH_BLOQUES, null
                )
            }
        }
        if (b.act.startsWith("Marcar")) {
            val bl = plan.bloquesDe(date)
            val hechos = bl.count { st.isBlockDone(date, it.idx) }
            val long = b.act + "\nHoy completaste $hechos de ${bl.size} bloques.\n" +
                plan.estadoGlobal(st, date)
            return Msg(title, b.act, long, CH_BLOQUES, null)
        }
        return Msg(title, b.act, b.act, CH_BLOQUES, null)
    }

    private fun actionPI(
        ctx: Context, kind: String, date: LocalDate, idx: Int, nid: Int, pista: String?, rc: Int
    ): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java)
        i.action = "plan.$kind.$nid"
        i.putExtra("kind", kind)
        i.putExtra("date", date.toString())
        i.putExtra("idx", idx)
        i.putExtra("nid", nid)
        if (pista != null) i.putExtra("pista", pista)
        return PendingIntent.getBroadcast(
            ctx, rc, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun mostrar(ctx: Context, date: LocalDate, idx: Int) {
        val msg = contenido(ctx, date, idx) ?: return
        val nid = (date.toEpochDay() * 100 + idx + 1).toInt()
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nb = NotificationCompat.Builder(ctx, msg.channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(msg.title)
            .setContentText(msg.short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.long))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (idx >= 0) {
            nb.addAction(0, "✔ Bloque hecho", actionPI(ctx, "block", date, idx, nid, null, nid * 2))
        }
        if (msg.pista != null) {
            nb.addAction(
                0, "🏁 Hito listo", actionPI(ctx, "hito", date, idx, nid, msg.pista, nid * 2 + 1)
            )
        }
        try {
            NotificationManagerCompat.from(ctx).notify(nid, nb.build())
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: no hacemos nada.
        }
    }

    fun mostrarPrueba(ctx: Context) {
        val nb = NotificationCompat.Builder(ctx, CH_BLOQUES)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("🔔 Notificación de prueba")
            .setContentText("Si ves esto, los avisos funcionan.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        try {
            NotificationManagerCompat.from(ctx).notify(999999, nb.build())
        } catch (e: SecurityException) {
            // Sin permiso.
        }
    }

    @Suppress("unused")
    fun ahora(): LocalDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
}
