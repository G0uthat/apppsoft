package com.plan.ingsoft

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannels(this)
        Notifier.scheduleNext(this)
        PlanWidget.updateAll(this)
        setContent {
            PlanTheme { AppRoot() }
        }
    }
}

@Composable
fun PlanTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF7FB2FF))
    } else {
        lightColorScheme(primary = Color(0xFF1F5FBF))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val plan = remember { PlanLoader.get(ctx) }
    val store = remember { Store(ctx) }
    var tab by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }
    val bump: () -> Unit = {
        tick += 1
        PlanWidget.updateAll(ctx)
    }

    // Refrescar al volver a la app (por si marcaste algo desde una notificación o el widget).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick += 1
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pedirPermiso: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermiso()
        }
    }

    val t = tick
    val tabs = listOf("🏠" to "Hoy", "📚" to "Cursos", "💪" to "Entreno", "⚙️" to "Ajustes")
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (emoji, nombre) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(emoji, fontSize = 20.sp) },
                        label = { Text(nombre) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> HoyScreen(plan, store, t, bump)
                1 -> CursosScreen(plan, store, t, bump)
                2 -> EntrenoScreen(plan, store, t, bump)
                else -> AjustesScreen(store, t, pedirPermiso)
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp)
    }
}

private fun cap(s: String): String = s.replaceFirstChar { it.uppercase() }

// ---------------------------------------------------------------- HOY

@Composable
fun HoyScreen(plan: Plan, st: Store, tick: Int, bump: () -> Unit) {
    val hoy = LocalDate.now()
    val bloques = plan.bloquesDe(hoy)
    val total = plan.horasTotal()
    val hechas = plan.horasHechas(st)
    val pct = if (total > 0) (hechas / total).coerceIn(0.0, 1.0) else 0.0
    val diasParaInicio = plan.inicio.toEpochDay() - hoy.toEpochDay()
    val hA = plan.siguienteHito(st, "A")
    val hB = plan.siguienteHito(st, "B")

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    cap(hoy.format(FMT_LARGO)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                val sub = if (diasParaInicio > 0) {
                    "El plan empieza el ${plan.inicio.format(FMT_CORTO)} (en $diasParaInicio días)"
                } else {
                    "Semana ${(-diasParaInicio / 7) + 1} del plan"
                }
                Text(sub, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Progreso total", style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(
                        progress = { pct.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        "${(pct * 100).toInt()}% · ${"%.1f".format(hechas)} de ${"%.0f".format(total)} h · " +
                            "${plan.hitosHechos(st)}/${plan.hitos.size} hitos"
                    )
                    Text(plan.estadoGlobal(st, hoy), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { HoyTocaCard("A", hA) { hA?.let { st.setHito(it.id, true); bump() } } }
        item { HoyTocaCard("B", hB) { hB?.let { st.setHito(it.id, true); bump() } } }
        item {
            Text(
                "Horario de hoy · ${bloques.count { st.isBlockDone(hoy, it.idx) }}/${bloques.size}",
                style = MaterialTheme.typography.titleMedium
            )
        }
        items(bloques, key = { it.idx }) { b ->
            BloqueRow(b, st.isBlockDone(hoy, b.idx)) { v ->
                st.setBlock(hoy, b.idx, v)
                bump()
            }
        }
    }
}

@Composable
fun HoyTocaCard(pista: String, h: Hito?, onDone: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "► HOY TOCA · Pista $pista",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (h == null) {
                Text("✔ Pista completada", style = MaterialTheme.typography.titleMedium)
            } else {
                Text(h.curso, style = MaterialTheme.typography.labelMedium)
                Text(
                    "${h.id}  ${h.texto}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (h.donde.isNotEmpty()) {
                    Text(h.donde, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${h.horas} h estimadas", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDone) { Text("✔ Hecho") }
                }
            }
        }
    }
}

@Composable
fun BloqueRow(b: Block, done: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = done, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${b.ini}–${b.fin}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Tag(b.pilar, pilarColor(b.pilar))
            }
            Text(b.act, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ------------------------------------------------------------- CURSOS

@Composable
fun CursosScreen(plan: Plan, st: Store, tick: Int, bump: () -> Unit) {
    val hoy = LocalDate.now()
    val estados = plan.timeline(st, hoy)
    var abierto by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Cursos y hitos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        items(estados, key = { it.curso.nombre }) { e ->
            val expandido = abierto == e.curso.nombre
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { abierto = if (expandido) null else e.curso.nombre }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag("Pista ${e.curso.pista}", if (e.curso.pista == "A") Color(0xFF2F6FDE) else Color(0xFF8A4FD6))
                        Spacer(Modifier.width(8.dp))
                        Text(e.curso.ciclo, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        e.curso.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${e.inicio.format(FMT_FECHA)} → ${e.fin.format(FMT_FECHA)} · ${e.estado}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { e.pctReal.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                    val hechos = e.hitos.count { st.isHitoDone(it.id) }
                    Text(
                        "${(e.pctReal * 100).toInt()}% · $hechos/${e.hitos.size} hitos · ${"%.0f".format(e.horas)} h",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (expandido) {
                        if (e.curso.fuentes.isNotEmpty()) {
                            Text(
                                "Fuentes: ${e.curso.fuentes}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        for (h in e.hitos) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = st.isHitoDone(h.id),
                                    onCheckedChange = { v ->
                                        st.setHito(h.id, v)
                                        bump()
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("${h.id}  ${h.texto}", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${h.horas} h · ${h.donde}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ ENTRENO

@Composable
fun EntrenoScreen(plan: Plan, st: Store, tick: Int, bump: () -> Unit) {
    val hoy = LocalDate.now()
    val sem = plan.semanaActual(hoy)
    val fase = plan.fase(sem)
    val ses = plan.sesionDelDia(hoy)
    val idx = plan.indiceSesion(hoy)
    val cols = listOf("Lun A", "Mié B", "Vie A", "Sáb", "Dom")

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    "Entrenamiento",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Semana $sem de 12 · Fase $fase de 3")
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Hoy te toca",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    when (ses) {
                        "A", "B" -> {
                            Text("Calistenia · sesión $ses", style = MaterialTheme.typography.titleMedium)
                            for (e in plan.ejerciciosDe(ses)) {
                                Column(Modifier.padding(top = 8.dp)) {
                                    Text(
                                        "${e.grupo} · ${e.nombre}",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${e.serie(fase)} · descanso ${e.descanso}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "Más fácil: ${e.facil}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Más difícil: ${e.dificil}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        "CAMINATA" -> Text("Caminata rápida 30–40 min + estiramientos (recuperación activa)")
                        "MOVILIDAD" -> Text("Movilidad suave 10–15 min (no hay entrenamiento fuerte)")
                        else -> Text("Hoy es día de descanso. ¡Recupérate!")
                    }
                    if (idx != null) {
                        Row(
                            Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = st.isTrainDone(sem, idx),
                                onCheckedChange = { v ->
                                    st.setTrain(sem, idx, v)
                                    bump()
                                }
                            )
                            Text("Sesión de hoy completada")
                        }
                    }
                }
            }
        }
        item {
            Text("Registro de sesiones", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("", Modifier.weight(0.9f))
                for (c in cols) {
                    Text(
                        c,
                        Modifier.weight(1f),
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        items((1..12).toList(), key = { it }) { w ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Sem $w",
                    Modifier.weight(0.9f),
                    fontWeight = if (w == sem) FontWeight.Bold else FontWeight.Normal
                )
                for (i in 0 until 5) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Checkbox(
                            checked = st.isTrainDone(w, i),
                            onCheckedChange = { v ->
                                st.setTrain(w, i, v)
                                bump()
                            }
                        )
                    }
                }
            }
        }
        item {
            val hechas = (1..12).sumOf { w -> (0 until 5).count { st.isTrainDone(w, it) } }
            Text(
                "Total: $hechas de 60 sesiones (${hechas * 100 / 60}%)",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ------------------------------------------------------------ AJUSTES

@Composable
fun AjustesScreen(st: Store, tick: Int, pedirPermiso: () -> Unit) {
    val ctx = LocalContext.current
    var activadas by remember { mutableStateOf(st.notifOn) }
    var resumen by remember { mutableStateOf(st.resumen) }
    val pilares = listOf("Estudio", "Entrenamiento", "Relación", "Personal")
    var estados by remember { mutableStateOf(pilares.associateWith { st.notifPilar(it) }) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Notificaciones",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            FilaSwitch("Avisos activados", activadas) { v ->
                activadas = v
                st.notifOn = v
                Notifier.scheduleNext(ctx)
            }
        }
        item {
            FilaSwitch("Resumen de tu día a las 08:00", resumen) { v ->
                resumen = v
                st.resumen = v
                Notifier.scheduleNext(ctx)
            }
        }
        item { Text("Avisar al empezar bloques de:", style = MaterialTheme.typography.titleSmall) }
        items(pilares, key = { it }) { p ->
            FilaSwitch("${pilarEmoji(p)} $p", estados[p] == true) { v ->
                estados = estados + (p to v)
                st.setNotifPilar(p, v)
                Notifier.scheduleNext(ctx)
            }
        }
        item {
            Text(
                "El bloque «Marcar el hito y dormir» siempre te avisa.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            OutlinedButton(onClick = pedirPermiso, modifier = Modifier.fillMaxWidth()) {
                Text("Permitir notificaciones")
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + ctx.packageName)
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Permitir alarmas exactas") }
        }
        item {
            OutlinedButton(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Quitar ahorro de batería a esta app") }
        }
        item {
            Button(
                onClick = { Notifier.mostrarPrueba(ctx) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Enviar notificación de prueba") }
        }
        item {
            Text(
                "Consejo: si tu teléfono es Xiaomi, Huawei, Oppo o Samsung, permite «inicio automático» " +
                    "y desactiva el ahorro de batería para esta app; así los avisos llegan siempre.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Surface(Modifier.padding(top = 8.dp)) {
                Text(
                    "Tu progreso se guarda solo en este teléfono.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun FilaSwitch(texto: String, valor: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = onChange)
    }
}
