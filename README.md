# Civic Launcher

Launcher Android para pantallas chinas de auto, con el tema oscuro azul del **Honda Civic 2012**.
Diseñado para **1280×720** y Android 13 (se escala solo a otras resoluciones).

## Qué incluye

- Barra lateral: inicio, Spotify, Google Maps (usa sus iconos reales si están instaladas), teléfono y cajón de apps.
- Velocidad en km/h leída del **GPS** de la unidad (pide permiso de ubicación la primera vez).
- Reloj y fecha.
- Tarjeta de **mantenimiento**: mantén pulsada la tarjeta para editar "Aceite motor" y "Próximo servicio".
- Reproductor con carátula, título, artista, progreso y controles (Spotify, YouTube Music, etc.).
- Dock inferior: Navegación, Música, Teléfono, Android Auto y Ajustes.
- Sin OBD2 por ahora.

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

- Nombre del auto: `buildTitle()` en `MainActivity.kt`.
- Foto del auto: reemplaza `app/src/main/res/drawable-nodpi/car.png`.
- Colores: constantes al inicio de `MainActivity.kt`.
