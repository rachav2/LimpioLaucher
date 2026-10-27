# Civic Launcher

Launcher Android para pantallas chinas de auto, con el tema oscuro azul del **Honda Civic 2012**.
Diseñado para **1280×720** y Android 13 (se escala al tamaño real de la ventana).

## Qué incluye

- **Velocímetro** con anillo azul y velocidad en km/h leída del GPS de la unidad (pide permiso de ubicación la primera vez).
- **Reloj y fecha.**
- **Viaje** (datos reales del GPS): distancia, velocidad máxima y rumbo. Mantén pulsada la tarjeta para reiniciarlo.
- **Mantenimiento:** aceite, frenos y próximo servicio. Toca la tarjeta para editar los valores.
- **Reproductor** con carátula, título, artista, progreso y controles (Spotify, YouTube Music, etc.).
- **Fila de apps:** Spotify, YouTube, Mensajes, Android Auto y el cajón con todas las apps (usa los iconos reales si están instaladas).
- **Barra inferior:**
  - Apagar pantalla: deja la pantalla en negro con el brillo al mínimo. Tócala para despertarla. La música sigue sonando.
  - Asistente de voz
  - Ajustes
  - Teléfono (marcador)
  - Google Maps
  - Modo nocturno: oscurece los colores y baja el brillo de la ventana. Vuelve a tocarlo para quitarlo.
- Sin OBD2 por ahora, así que no hay consumo ni temperatura del motor.

## Compilar el APK (sin instalar nada)

1. Sube este proyecto a GitHub (rama `main`).
2. Ve a la pestaña **Actions**. El workflow **Build APK** se ejecuta solo con cada push (o con **Run workflow**).
3. Al terminar, entra a la ejecución y descarga el artefacto **civic-launcher-apk**: dentro está `app-debug.apk`.

También puedes abrir la carpeta en Android Studio y pulsar **Run**.

## Instalar en la pantalla

1. Copia `app-debug.apk` a un pendrive/USB y ábrelo desde el explorador de archivos de la unidad (o usa `adb install app-debug.apk`).
2. Pulsa el botón Home de la unidad y elige **Civic Launcher** → **Siempre**.
   Si tu unidad no lo permite: Ajustes > Aplicaciones > Aplicaciones predeterminadas > Launcher.

## Activar la música en el reproductor

Android exige un permiso especial para leer la canción que suena:

1. Ajustes > Notificaciones > **Acceso a notificaciones** > activa **Civic Launcher**.
   (Toca la tarjeta del reproductor y te lleva ahí.)
2. En Android 13, si aparece "Ajuste restringido" porque el APK se instaló fuera de Play Store:
   Ajustes > Aplicaciones > Civic Launcher > menú ⋮ > **Permitir ajustes restringidos**, y vuelve al paso anterior.

## Personalizar

- Nombre del auto: `buildVehicleCard()` en `MainActivity.kt`.
- Foto del auto: reemplaza `app/src/main/res/drawable-nodpi/car.png`.
- Colores: constantes al inicio de `MainActivity.kt`.
- Intensidad del modo nocturno: `0x8C000000` en `buildOverlays()` y `0.25f` en `applyBrightness()`.
