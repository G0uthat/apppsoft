package com.plan.ingsoft

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

val DIAS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
val LOCALE_ES: Locale = Locale.forLanguageTag("es")
val FMT_CORTO: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d/MM", LOCALE_ES)
val FMT_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", LOCALE_ES)
val FMT_LARGO: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", LOCALE_ES)

fun diaNombre(d: LocalDate): String = DIAS[d.dayOfWeek.value - 1]

fun pilarColor(p: String): Color = when (p) {
    "Estudio" -> Color(0xFF2F6FDE)
    "Entrenamiento" -> Color(0xFF2E9E5B)
    "Relación" -> Color(0xFFD6477F)
    else -> Color(0xFF7A8594)
}

fun pilarEmoji(p: String): String = when (p) {
    "Estudio" -> "📚"
    "Entrenamiento" -> "💪"
    "Relación" -> "❤️"
    else -> "🕒"
}

data class Block(
    val idx: Int,
    val dia: String,
    val ini: LocalTime,
    val fin: LocalTime,
    val pilar: String,
    val act: String
) {
    val iniMin: Int get() = ini.hour * 60 + ini.minute
    val finMin: Int
        get() {
            val m = fin.hour * 60 + fin.minute
            return if (m <= iniMin) m + 1440 else m
        }
    val horas: Double get() = (finMin - iniMin) / 60.0
}

data class Curso(
    val nombre: String,
    val pista: String,
    val ciclo: String,
    val area: String,
    val fuentes: String
)

data class Hito(
    val id: String,
    val curso: String,
    val texto: String,
    val donde: String,
    val horas: Double
)

data class Ejercicio(
    val sesion: String,
    val grupo: String,
    val nombre: String,
    val f1: String,
    val f2: String,
    val f3: String,
    val descanso: String,
    val facil: String,
    val dificil: String
) {
    fun serie(fase: Int): String = when (fase) {
        1 -> f1
        2 -> f2
        else -> f3
    }
}

data class CursoEstado(
    val curso: Curso,
    val hitos: List<Hito>,
    val horas: Double,
    val inicio: LocalDate,
    val fin: LocalDate,
    val hechas: Double,
    val pctReal: Double,
    val pctEsperado: Double,
    val estado: String
)

class Plan(
    val inicio: LocalDate,
    val eficiencia: Double,
    val horario: List<Block>,
    val cursos: List<Curso>,
    val hitos: List<Hito>,
    val entreno: List<Ejercicio>
) {
    val hitosPorCurso: Map<String, List<Hito>> = hitos.groupBy { it.curso }

    private fun esPistaB(b: Block) = b.act.startsWith("Segundo curso")

    val horasPlanA: Double =
        horario.filter { it.pilar == "Estudio" && !esPistaB(it) }.sumOf { it.horas }
    val horasPlanB: Double =
        horario.filter { it.pilar == "Estudio" && esPistaB(it) }.sumOf { it.horas }

    /** Horas efectivas de estudio por semana en una pista (plan x eficiencia). */
    fun efectivasSemana(pista: String): Double =
        (if (pista == "A") horasPlanA else horasPlanB) * eficiencia

    fun bloquesDe(d: LocalDate): List<Block> = horario.filter { it.dia == diaNombre(d) }

    fun horasTotalPista(pista: String): Double =
        cursos.filter { it.pista == pista }
            .sumOf { c -> hitosPorCurso[c.nombre].orEmpty().sumOf { h -> h.horas } }

    fun horasTotal(): Double = hitos.sumOf { it.horas }

    fun horasHechas(s: Store): Double = hitos.filter { s.isHitoDone(it.id) }.sumOf { it.horas }

    fun hitosHechos(s: Store): Int = hitos.count { s.isHitoDone(it.id) }

    /** Horas que ya deberías llevar según el plan (igual que la Línea de Tiempo del Excel). */
    fun horasEsperadas(hoy: LocalDate): Double {
        val semanas = max(0.0, (hoy.toEpochDay() - inicio.toEpochDay()) / 7.0)
        return min(horasTotalPista("A"), semanas * efectivasSemana("A")) +
            min(horasTotalPista("B"), semanas * efectivasSemana("B"))
    }

    fun estadoGlobal(s: Store, hoy: LocalDate): String {
        val real = horasHechas(s)
        val esp = horasEsperadas(hoy)
        if (abs(real - esp) < 1) return "● En ritmo"
        if (real > esp) return "▲ Vas ${"%.1f".format(real - esp)} h adelantado"
        val semanal = efectivasSemana("A") + efectivasSemana("B")
        val dias = if (semanal > 0) ceil((esp - real) / semanal * 7).toInt() else 0
        return "▼ Debes ${"%.1f".format(esp - real)} h (≈ $dias días de estudio)"
    }

    fun siguienteHito(s: Store, pista: String): Hito? {
        for (c in cursos.filter { it.pista == pista }) {
            val h = hitosPorCurso[c.nombre].orEmpty().firstOrNull { !s.isHitoDone(it.id) }
            if (h != null) return h
        }
        return null
    }

    fun timeline(s: Store, hoy: LocalDate): List<CursoEstado> {
        val acum = mutableMapOf("A" to 0.0, "B" to 0.0)
        return cursos.map { c ->
            val hs = hitosPorCurso[c.nombre].orEmpty()
            val h = hs.sumOf { it.horas }
            val prev = acum[c.pista] ?: 0.0
            val cum = prev + h
            acum[c.pista] = cum
            val wk = efectivasSemana(c.pista).let { if (it <= 0) 1.0 else it }
            val ini = inicio.plusDays(ceil(prev / wk * 7).toLong())
            val fin = inicio.plusDays(ceil(cum / wk * 7).toLong() - 1)
            val hechas = hs.filter { s.isHitoDone(it.id) }.sumOf { it.horas }
            val pct = if (h > 0) hechas / h else 0.0
            val dur = (fin.toEpochDay() - ini.toEpochDay() + 1).toDouble().coerceAtLeast(1.0)
            val esp = max(0.0, min(1.0, (hoy.toEpochDay() - ini.toEpochDay() + 1) / dur))
            val estado = when {
                pct >= 1.0 -> "✔ Completado"
                hoy.isAfter(fin) -> "⚠ Vencido"
                hoy.isBefore(ini) -> if (pct > 0) "▲ Adelantado" else "Pendiente"
                pct - esp < -0.05 -> "▼ Atrasado"
                pct - esp > 0.05 -> "▲ Adelantado"
                else -> "● En ritmo"
            }
            CursoEstado(c, hs, h, ini, fin, hechas, pct, esp, estado)
        }
    }

    /** Texto corto: bloque actual / siguiente (para el widget). */
    fun textoBloque(now: LocalDateTime): String {
        val hoy = now.toLocalDate()
        if (hoy.isBefore(inicio)) return "El plan empieza el ${inicio.format(FMT_CORTO)}"
        val min = now.hour * 60 + now.minute
        val bl = bloquesDe(hoy).sortedBy { it.iniMin }
        val actual = bl.firstOrNull { min >= it.iniMin && min < it.finMin }
        if (actual != null) return "Ahora ${actual.ini}–${actual.fin} · ${actual.act}"
        val sig = bl.firstOrNull { it.iniMin > min }
        if (sig != null) return "Sigue ${sig.ini} · ${sig.act}"
        val man = bloquesDe(hoy.plusDays(1)).minByOrNull { it.iniMin }
        return if (man != null) "Mañana ${man.ini} · ${man.act}" else "Sin más bloques hoy"
    }

    // ---- Entrenamiento ----
    fun semanaActual(hoy: LocalDate): Int =
        (((hoy.toEpochDay() - inicio.toEpochDay()) / 7) + 1).coerceIn(1, 12).toInt()

    fun fase(semana: Int): Int = if (semana <= 4) 1 else if (semana <= 8) 2 else 3

    /** "A", "B", "CAMINATA", "MOVILIDAD" o null (día de descanso). */
    fun sesionDelDia(d: LocalDate): String? = when (d.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.FRIDAY -> "A"
        DayOfWeek.WEDNESDAY -> "B"
        DayOfWeek.SATURDAY -> "CAMINATA"
        DayOfWeek.SUNDAY -> "MOVILIDAD"
        else -> null
    }

    /** Columna del registro semanal: Lun(A)=0, Mié(B)=1, Vie(A)=2, Sáb=3, Dom=4. */
    fun indiceSesion(d: LocalDate): Int? = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.WEDNESDAY -> 1
        DayOfWeek.FRIDAY -> 2
        DayOfWeek.SATURDAY -> 3
        DayOfWeek.SUNDAY -> 4
        else -> null
    }

    fun ejerciciosDe(sesion: String): List<Ejercicio> = entreno.filter { it.sesion == sesion }

    fun resumenEjercicios(sesion: String, fase: Int): String =
        ejerciciosDe(sesion).joinToString(" · ") { "${it.nombre} ${it.serie(fase)}" }
}

object PlanLoader {
    @Volatile
    private var cached: Plan? = null

    fun get(ctx: Context): Plan {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val p = load(ctx.applicationContext)
            cached = p
            return p
        }
    }

    private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun load(ctx: Context): Plan {
        val txt = ctx.assets.open("plan.json").bufferedReader().use { it.readText() }
        val o = JSONObject(txt)
        val horario = o.getJSONArray("horario").objs().mapIndexed { i, b ->
            Block(
                i, b.getString("dia"),
                LocalTime.parse(b.getString("ini")), LocalTime.parse(b.getString("fin")),
                b.getString("pilar"), b.getString("act")
            )
        }
        val cursos = o.getJSONArray("orden").objs().map {
            Curso(
                it.getString("nombre"), it.getString("pista"), it.optString("ciclo"),
                it.optString("area"), it.optString("fuentes")
            )
        }
        val hitos = o.getJSONArray("hitos").objs().map {
            Hito(
                it.getString("id"), it.getString("curso"), it.getString("hito"),
                it.optString("donde"), it.getDouble("horas")
            )
        }
        val entreno = o.getJSONArray("entreno").objs().map {
            Ejercicio(
                it.getString("sesion"), it.optString("grupo"), it.getString("ej"),
                it.optString("f1"), it.optString("f2"), it.optString("f3"),
                it.optString("desc"), it.optString("facil"), it.optString("dificil")
            )
        }
        return Plan(
            LocalDate.parse(o.getString("inicio")), o.getDouble("eficiencia"),
            horario, cursos, hitos, entreno
        )
    }
}
