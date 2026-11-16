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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Objects

object Notifier {
    const val CH_BLOQUES = "bloques"
    const val CH_RESUMEN = "resumen"
    const val CH_FOCO = "foco"
    const val CH_MONITOR = "monitor"

    /** kind: 0 = empieza el bloque, 1 = pre-aviso, 2 = "¿terminaste?", 3 = resumen del día. */
    data class Evento(val at: LocalDateTime, val date: LocalDate, val id: Int, val kind: Int)

    private data class Msg(
        val title: String,
        val short: String,
        val long: String,
        val channel: String,
        val pista: String?,
        val conBloque: Boolean
    )

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val bloques = NotificationChannel(
            CH_BLOQUES, "Bloques del horario", NotificationManager.IMPORTANCE_HIGH
        )
        bloques.description = "Avisos al empezar, antes y al terminar cada bloque"
        nm.createNotificationChannel(bloques)
        val resumen = NotificationChannel(
            CH_RESUMEN, "Resumen diario", NotificationManager.IMPORTANCE_DEFAULT
        )
        resumen.description = "Qué te toca hoy y cómo vas"
        nm.createNotificationChannel(resumen)
        val foco = NotificationChannel(
            CH_FOCO, "Tiempo en apps", NotificationManager.IMPORTANCE_HIGH
        )
        foco.description = "Avisos cuando te acercas o pasas el límite de una app"
        nm.createNotificationChannel(foco)
        val mon = NotificationChannel(
            CH_MONITOR, "Vigilancia en segundo plano", NotificationManager.IMPORTANCE_MIN
        )
        mon.description = "Mantiene activa la medición del tiempo en pantalla"
        nm.createNotificationChannel(mon)
    }

    private fun habilitado(st: Store, b: Block): Boolean =
        b.notif && (b.act.startsWith("Marcar") || st.notifPilar(b.pilar))

    /** Próximos eventos de notificación. */
    fun eventos(ctx: Context, desde: LocalDateTime, dias: Int = 14): List<Evento> {
        val plan = PlanLoader.get(ctx)
        val st = Store(ctx)
        if (!st.notifOn) return emptyList()
        val out = ArrayList<Evento>()
        var d = if (desde.toLocalDate().isBefore(plan.inicio)) plan.inicio else desde.toLocalDate()
        for (n in 0 until dias) {
            if (st.resumen) out.add(Evento(d.atTime(st.resumenTime()), d, -1, 3))
            for (b in plan.bloquesDe(d)) {
                if (!habilitado(st, b)) continue
                val inicio = d.atTime(b.ini)
                out.add(Evento(inicio, d, b.id, 0))
                if (st.preMin > 0 && b.checkable()) {
                    out.add(Evento(inicio.minusMinutes(st.preMin.toLong()), d, b.id, 1))
                }
                if (st.askEnd && (b.pilar == "Estudio" || b.pilar == "Entrenamiento")) {
                    out.add(Evento(inicio.plusMinutes((b.finMin - b.iniMin + 5).toLong()), d, b.id, 2))
                }
            }
            d = d.plusDays(1)
        }
        return out.filter { it.at.isAfter(desde) }.sortedBy { it.at }
    }

    private fun alarmPI(ctx: Context, ev: Evento?): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
        if (ev != null) {
            i.putExtra("date", ev.date.toString())
            i.putExtra("idx", ev.id)
            i.putExtra("kind", ev.kind)
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

    private fun dur(b: Block): String = fmtDur(b.finMin - b.iniMin)

    private fun lugar(b: Block): String = if (b.lugar.isBlank()) "" else "\n📍 ${b.lugar}"

    private fun sesionDe(b: Block): String? = when {
        b.act.contains("SESIÓN A") -> "A"
        b.act.contains("SESIÓN B") -> "B"
        else -> null
    }

    private fun contenido(ctx: Context, date: LocalDate, id: Int, kind: Int): Msg? {
        val plan = PlanLoader.get(ctx)
        val st = Store(ctx)

        // ---- Resumen del día ----
        if (kind == 3) {
            val bl = plan.bloquesDe(date).filter { it.checkable() }
            val lineas = ArrayList<String>()
            val a = plan.siguienteHito(st, "A")
            lineas.add("📚 A: " + (a?.let { "${it.id} ${it.texto}" } ?: "pista completada"))
            val b = plan.siguienteHito(st, "B")
            if (b != null && plan.bloquesDe(date).any { it.pista == "B" }) {
                lineas.add("📚 B: ${b.id} ${b.texto}")
            }
            val ses = plan.sesionDelDia(date)
            when (ses) {
                "A", "B" -> lineas.add("💪 Calistenia sesión $ses (semana ${plan.semanaActual(date)})")
                "CAMINATA" -> lineas.add("💪 Caminata rápida 30–40 min")
                "MOVILIDAD" -> lineas.add("💪 Movilidad suave 10–15 min")
            }
            val primero = bl.firstOrNull()
            if (primero != null) lineas.add("⏰ Primer bloque: ${primero.ini} · ${primero.act}")
            val racha = plan.racha(st, date)
            if (racha > 0) lineas.add("🔥 Racha: $racha días")
            lineas.add(plan.estadoGlobal(st, date))
            return Msg(
                "☀️ Tu día · " + date.format(FMT_CORTO),
                "${bl.size} bloques hoy" + (primero?.let { " · empiezas a las ${it.ini}" } ?: ""),
                lineas.joinToString("\n"), CH_RESUMEN, null, false
            )
        }

        val b = plan.bloquePorId(id) ?: return null
        val hito = plan.hitoDeBloque(st, b)
        val pista = if (b.pilar == "Estudio" && hito != null) (if (b.pista == "B") "B" else "A") else null

        // ---- ¿Terminaste? ----
        if (kind == 2) {
            if (st.isBlockDone(date, b.id)) return null
            val extra = if (hito != null) "\nPendiente: ${hito.id} ${hito.texto}" else ""
            return Msg(
                "✅ ¿Terminaste? · ${b.pilar}",
                b.act,
                "Tu bloque de ${b.ini} a ${b.fin} ya terminó.\n${b.act}$extra\n" +
                    "Si lo hiciste, márcalo ahora; si no, ¿qué te frenó?",
                CH_BLOQUES, pista, true
            )
        }

        val pre = kind == 1
        val prefijo = if (pre) "⏳ En ${st.preMin} min" else "${pilarEmoji(b.pilar)} ${b.ini}"

        // ---- Estudio ----
        if (b.pilar == "Estudio" && hito != null) {
            val corto = if (b.esGenerica) "${hito.id} ${hito.texto}" else b.act
            val long = buildString {
                if (!b.esGenerica) append(b.act).append("\n")
                append("Pista $pista · ${hito.curso}\n")
                append("▶ ${hito.id} ${hito.texto}\n")
                append("⏱ Hito de ${hito.horas} h · este bloque: ${dur(b)}\n")
                if (hito.donde.isNotEmpty()) append("📖 ${hito.donde}")
                append(lugar(b))
                append("\n").append(plan.estadoGlobal(st, date))
            }
            return Msg("$prefijo · ${hito.curso}", corto, long, CH_BLOQUES, pista, !pre)
        }

        // ---- Entrenamiento ----
        if (b.pilar == "Entrenamiento") {
            val ses = sesionDe(b)
            if (ses != null) {
                val sem = plan.semanaActual(date)
                val fase = plan.fase(sem)
                val lista = plan.ejerciciosDe(ses).mapIndexed { i, e ->
                    "${i + 1}. ${e.nombre} · ${e.serie(fase)}"
                }.joinToString("\n")
                val prep = if (pre) "\nPrepara: agua, espacio libre y una silla o mesa firme." else ""
                return Msg(
                    "$prefijo · Calistenia $ses · sem $sem",
                    "${plan.ejerciciosDe(ses).size} ejercicios · ${dur(b)}",
                    lista + lugar(b) + prep, CH_BLOQUES, null, !pre
                )
            }
        }

        // ---- Cierre del día ----
        if (b.act.startsWith("Marcar")) {
            val bl = plan.bloquesDe(date).filter { it.checkable() }
            val hechos = bl.count { st.isBlockDone(date, it.id) }
            val faltan = bl.filter { !st.isBlockDone(date, it.id) }.joinToString(", ") { it.ini.toString() }
            val long = b.act + "\nHoy marcaste $hechos de ${bl.size} bloques." +
                (if (faltan.isNotEmpty()) "\nSin marcar: $faltan" else "\n¡Día completo! 🎉") +
                "\n" + plan.estadoGlobal(st, date)
            return Msg("🌙 ${b.ini} · Cierra tu día", "$hechos de ${bl.size} bloques marcados", long, CH_BLOQUES, null, false)
        }

        // ---- Resto ----
        return Msg("$prefijo · ${b.pilar} (${dur(b)})", b.act, b.act + lugar(b), CH_BLOQUES, null, !pre && b.checkable())
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

    private fun abrirApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun mostrar(ctx: Context, date: LocalDate, id: Int, kind: Int) {
        val msg = contenido(ctx, date, id, kind) ?: return
        val nid = Objects.hash(date.toString(), id, kind)
        val nb = NotificationCompat.Builder(ctx, msg.channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(msg.title)
            .setContentText(msg.short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.long))
            .setContentIntent(abrirApp(ctx))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (msg.conBloque && id >= 0) {
            nb.addAction(0, "✔ Hecho", actionPI(ctx, "block", date, id, nid, null, nid * 2))
        }
        if (msg.pista != null) {
            nb.addAction(0, "🏁 Hito listo", actionPI(ctx, "hito", date, id, nid, msg.pista, nid * 2 + 1))
        }
        try {
            NotificationManagerCompat.from(ctx).notify(nid, nb.build())
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones.
        }
    }

    /** Aviso de tiempo en una app: al 80% del límite o al superarlo. */
    fun avisoApp(ctx: Context, label: String, usadoMin: Int, limiteMin: Int, pasado: Boolean, bloquea: Boolean) {
        val faltan = (limiteMin - usadoMin).coerceAtLeast(0)
        val title = if (pasado) "⛔ Límite de $label alcanzado" else "⚠️ Cuidado con $label"
        val text = if (pasado) {
            "Llevas $usadoMin min hoy (límite $limiteMin)." + if (bloquea) " Te tapo la pantalla." else ""
        } else {
            "Llevas $usadoMin de $limiteMin min. Te quedan $faltan min."
        }
        val nb = NotificationCompat.Builder(ctx, CH_FOCO)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                text + "\nTu plan de hoy te está esperando: abre la app y marca el siguiente bloque."
            ))
            .setContentIntent(abrirApp(ctx))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        try {
            NotificationManagerCompat.from(ctx).notify(Objects.hash("foco", label, pasado), nb.build())
        } catch (e: SecurityException) {
            // Sin permiso.
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
}
