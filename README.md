# Plan Ing. Software · v2 (Android)

Compilación: igual que antes. Sube el CONTENIDO de esta carpeta a GitHub (incluida `.github/workflows/build-apk.yml`),
ve a Actions → Build APK → Run workflow y descarga el artifact `PlanIngSoftware-apk`.

## Pestañas
- **Hoy** (principal): franja de la semana, agenda del día tipo calendario y check rápido (toca el círculo).
  Toca un bloque para editarlo (protegido con candado).
- **Estudio**: HOY TOCA de las pistas A y B y los cursos con sus hitos.
- **Workout**: sesión del día según tu semana y fase, y registro de sesiones.
- **Progreso**: sección Estudio y sección Ejercicio.
- **Foco**: tiempo en pantalla por app, límites diarios por app y bloqueo al llegar al límite.
- **⚙ (en Hoy)**: notificaciones, fecha de inicio del plan, eficiencia, contraseña y editor del horario.

## Candado
Cambiar horario, fecha de inicio o límites pide contraseña o resolver retos (cálculo mental,
apaga las luces, torres de Hanói). Al resolverlo, se puede editar durante unos minutos.

## Permisos que pide
- Notificaciones, alarmas exactas y quitar ahorro de batería (para que lleguen siempre).
- Foco: "Acceso al uso" (para medir) y "Mostrar sobre otras apps" (solo para bloquear).

## Nota sobre Rust
Las notificaciones, el widget, el tiempo en pantalla y el bloqueo de apps solo existen en las APIs de Android
(Kotlin/Java), por eso esta versión sigue en Kotlin.
