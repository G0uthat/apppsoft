package com.plan.ingsoft

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate

/**
 * Vigila qué app tienes abierta. Si es una app con límite: avisa al 80%, y al 100% avisa
 * y (si el límite es de bloqueo) tapa la pantalla. Para pedir más tiempo hay que resolver retos.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var actual: String? = null
    private var ultimaConsulta = 0L
    private var basePkg: String? = null
    private var baseMs = 0L
    private var baseAt = 0L
    private var overlay: View? = null
    private var overlayPkg: String? = null

    private val tick = object : Runnable {
        override fun run() {
            try {
                revisar()
            } catch (e: Exception) {
                // No dejamos que un error pare la vigilancia.
            }
            handler.postDelayed(this, 4000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        val n = NotificationCompat.Builder(this, Notifier.CH_MONITOR)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Foco activo")
            .setContentText("Vigilando tus límites de tiempo en apps")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 20, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        ultimaConsulta = System.currentTimeMillis() - 90_000
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ocultar()
        super.onDestroy()
    }

    private fun revisar() {
        val st = Store(this)
        val limites = st.limites()
        if (!st.monitorOn || limites.isEmpty() || !Usage.hasAccess(this)) {
            stopSelf()
            return
        }
        val now = System.currentTimeMillis()
        actual = Usage.primerPlano(this, ultimaConsulta, now, actual)
        ultimaConsulta = now

        val pkg = actual
        val lim = if (pkg != null) limites.firstOrNull { it.pkg == pkg } else null
        if (pkg == null || lim == null) {
            if (overlay != null) ocultar()
            return
        }

        if (basePkg != pkg || now - baseAt > 60_000) {
            baseMs = Usage.hoyMs(this, pkg)
            baseAt = now
            basePkg = pkg
        }
        val usadoMs = baseMs + (now - baseAt)
        val limiteMin = lim.minutos + st.extraMin(pkg)
        val limiteMs = limiteMin * 60_000L
        val usadoMin = (usadoMs / 60_000).toInt()
        val hoy = LocalDate.now()

        if (usadoMs >= limiteMs) {
            if (!st.flag("av100_${pkg}_${hoy}_$limiteMin")) {
                st.setFlag("av100_${pkg}_${hoy}_$limiteMin", true)
                Notifier.avisoApp(this, lim.label, usadoMin, limiteMin, true, lim.bloquear)
            }
            if (lim.bloquear) mostrar(pkg, lim.label, usadoMin, limiteMin) else if (overlay != null) ocultar()
        } else {
            if (overlay != null) ocultar()
            if (usadoMs >= limiteMs * 0.8 && !st.flag("av80_${pkg}_${hoy}_$limiteMin")) {
                st.setFlag("av80_${pkg}_${hoy}_$limiteMin", true)
                Notifier.avisoApp(this, lim.label, usadoMin, limiteMin, false, lim.bloquear)
            }
        }
    }

    private fun irAlInicio() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (e: Exception) {
            // Sin permiso para abrir desde segundo plano.
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun mostrar(pkg: String, label: String, usadoMin: Int, limiteMin: Int) {
        if (overlay != null && overlayPkg == pkg) return
        if (!Settings.canDrawOverlays(this)) {
            // Sin permiso de superposición: lo mejor que podemos hacer es mandarte al inicio.
            irAlInicio()
            return
        }
        ocultar()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(0xF21E1F22.toInt())
        root.setPadding(dp(32), dp(32), dp(32), dp(32))

        val emoji = TextView(this)
        emoji.text = "⏱"
        emoji.textSize = 56f
        emoji.gravity = Gravity.CENTER
        root.addView(emoji)

        val titulo = TextView(this)
        titulo.text = "Tiempo agotado en $label"
        titulo.textSize = 24f
        titulo.setTextColor(0xFFF2F3F5.toInt())
        titulo.gravity = Gravity.CENTER
        titulo.setPadding(0, dp(12), 0, dp(8))
        root.addView(titulo)

        val msg = TextView(this)
        msg.text = "Hoy llevas $usadoMin min (límite: $limiteMin min).\n" +
            "Tu plan de estudio y entrenamiento te está esperando."
        msg.textSize = 16f
        msg.setTextColor(0xFFB5BAC1.toInt())
        msg.gravity = Gravity.CENTER
        root.addView(msg)

        val volver = Button(this)
        volver.text = "Volver al inicio"
        volver.setOnClickListener {
            irAlInicio()
            ocultar()
        }
        val lpB = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpB.topMargin = dp(28)
        root.addView(volver, lpB)

        val mas = Button(this)
        mas.text = "Necesito 10 min más (resolver retos)"
        mas.setOnClickListener {
            val i = Intent(this, PuzzleActivity::class.java)
                .putExtra("pkg", pkg).putExtra("label", label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(i)
            } catch (e: Exception) {
                // Nada.
            }
        }
        val lpM = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpM.topMargin = dp(8)
        root.addView(mas, lpM)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(root, lp)
            overlay = root
            overlayPkg = pkg
        } catch (e: Exception) {
            irAlInicio()
        }
    }

    private fun ocultar() {
        val v = overlay ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
            // Ya no estaba.
        }
        overlay = null
        overlayPkg = null
    }

    companion object {
        const val NOTIF_ID = 4242

        /** Enciende o apaga la vigilancia según haya límites, permiso y ajuste activado. */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext
            val st = Store(app)
            val i = Intent(app, MonitorService::class.java)
            if (st.monitorOn && st.limites().isNotEmpty() && Usage.hasAccess(app)) {
                try {
                    ContextCompat.startForegroundService(app, i)
                } catch (e: Exception) {
                    // Android no deja arrancarlo desde segundo plano; se reintenta al abrir la app.
                }
            } else {
                app.stopService(i)
            }
        }
    }
}
