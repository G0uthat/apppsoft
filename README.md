# Plan Ing. Software (app Android)

App con widget de pantalla principal y notificaciones diarias, hecha a partir de tu Excel
`Plan_Ingenieria_Software_v2.xlsx`.

## Cómo obtener el APK (sin instalar nada en tu PC)
1. Crea una cuenta gratis en https://github.com y un repositorio nuevo (privado está bien).
2. Sube TODO el contenido de esta carpeta al repositorio (botón "Add file → Upload files").
   Importante: debe quedar la carpeta oculta `.github/workflows/build-apk.yml`.
   Si no se sube, crea ese archivo a mano: "Add file → Create new file" con el nombre
   `.github/workflows/build-apk.yml` y pega su contenido.
3. Ve a la pestaña **Actions** → "Build APK" → **Run workflow**. Tarda 3–6 minutos.
4. Entra a la ejecución terminada y descarga **PlanIngSoftware-apk** (un zip con `app-debug.apk`).
5. Pasa el APK al teléfono, ábrelo y permite "instalar apps desconocidas".

## Primera vez en el teléfono
- Acepta el permiso de notificaciones.
- En la pestaña **Ajustes**: "Permitir alarmas exactas" y "Quitar ahorro de batería".
- Pulsa "Enviar notificación de prueba".
- Widget: mantén pulsada la pantalla principal → Widgets → "Plan Ing. Software".

## Qué hace
- **Hoy**: hito de hoy (pistas A y B), progreso total y horario del día con checks.
- **Cursos**: los 27 cursos con fechas del plan, % y estado; toca uno para ver y marcar sus 207 hitos.
- **Entreno**: sesión de hoy según la semana/fase, y registro de 12 semanas.
- **Notificaciones**: al empezar cada bloque (con el hito que toca), resumen a las 08:00 y
  recordatorio de cierre del día. Botones "Bloque hecho" y "Hito listo" dentro de la notificación.
- **Widget**: bloque actual/siguiente, hito A y B, barra de progreso y botón "✔ Hito A".

El plan empieza el lunes 16/11/2026 (las notificaciones arrancan ese día).
Los datos viven en `app/src/main/assets/plan.json`.
