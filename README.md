# Civic Launcher

Launcher Android para pantallas chinas de auto, con el tema oscuro azul del **Honda Civic 2012**.
Diseñado para **1280×720** y Android 13 (se escala al tamaño real de la ventana).

## Qué incluye

- **Velocímetro** con anillo azul y velocidad en km/h leída del GPS de la unidad (pide permiso de ubicación la primera vez).
- **Reloj y fecha.**
- **Viaje:** ubicación actual (colonia/ciudad y departamento) y clima actual. Necesita internet en la unidad; el clima viene de Open-Meteo y el nombre del lugar del GPS.
- **Reproductor** con carátula, título, artista, progreso y controles. Prioriza la app **Music BT** (música por Bluetooth); si no hay sesión de Music BT, usa cualquier otra que esté sonando (Spotify, YouTube Music, etc.). Si Music BT no publica sesión de medios, los botones envían teclas multimedia.
- **Fila de apps:** Spotify, YouTube, Mensajes, **Android Auto (abre ZLINK5)** y el cajón con todas las apps (usa los iconos reales si están instaladas).
- **Barra inferior:**
  - Apagar pantalla: deja la pantalla en negro con el brillo al mínimo. Tócala para despertarla. La música sigue sonando.
  - Asistente de voz
  - Ajustes
  - Teléfono (abre la app **BT** de la unidad para llamadas)
  - Google Maps
  - Modo nocturno: oscurece los colores y baja el brillo de la ventana. Vuelve a tocarlo para quitarlo.
- Sin OBD2 por ahora, así que no hay consumo ni temperatura del motor.

## Mejoras del dashboard (rama feature/civic-dashboard-v2)

- **Dock dinámico:** muestra hasta cuatro aplicaciones recientes que se hayan abierto desde el launcher. El acceso **Todas las apps** permanece fijo. Android no permite conocer de forma fiable todas las apps abiertas por otros launchers sin conceder acceso de uso; esta versión registra los lanzamientos realizados desde Civic Launcher.
- **Paneles movibles:** mantén presionada una tarjeta (vehículo, reloj, viaje, dock, reproductor o barra inferior) hasta que se active el arrastre; mueve el dedo y suelta para guardar la posición. Las posiciones se conservan entre reinicios. Los botones siguen funcionando con un toque normal.
- **Mapa GPS offline:** desliza la pantalla principal hacia la izquierda para abrir el visor local. La ubicación se obtiene del GPS sin necesitar datos móviles. Usa **Cargar mapa MBTiles** para elegir el archivo desde Descargas/USB; la app lo copia a `Android/data/com.rachav2.civiclauncher/files/maps/el_salvador.mbtiles`. Debe ser un MBTiles **ráster** (teselas PNG/JPG) que incluya zoom 13; los MBTiles vectoriales PBF no funcionan con este visor sencillo. No se incluye cartografía de terceros en el APK.

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

## Conseguir un mapa offline

- Puedes crear un MBTiles ráster con QGIS/MapTiler a partir de cartografía cuya licencia permita descargas y uso offline. Revisa siempre las condiciones de la fuente; el servidor estándar de teselas de OpenStreetMap prohíbe descargar masivamente teselas para uso offline.
- Recursos para empezar: [BBBike: extractos de OpenStreetMap](https://extract.bbbike.org/?lang=es) y [guía de MBTiles ráster](https://milgps.com/userguide/frequently-asked-questions/creating-offline-maps/). Comprueba el formato antes de usar el archivo: no todo archivo `.mbtiles` es compatible.

## Personalizar

- Nombre del auto: `buildVehicleCard()` en `MainActivity.kt`.
- Foto del auto: reemplaza `app/src/main/res/drawable-nodpi/car.png`.
- Colores: constantes al inicio de `MainActivity.kt`.
- Intensidad del modo nocturno: `0x8C000000` en `buildOverlays()` y `0.25f` en `applyBrightness()`.
