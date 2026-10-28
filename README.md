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
- **Widget CANbus modular:** `CanBusGateway.kt` recibe estados de solo lectura y el widget puede moverse/redimensionarse manteniéndolo presionado; la posición y tamaño se guardan. `DashboardWidgetHost.kt` sirve de contenedor reutilizable para futuros widgets.
- **Importante sobre CANbus:** el adaptador escucha el broadcast `com.rachav2.civiclauncher.CANBUS_STATE` con extras booleanos `door_fl`, `door_fr`, `door_rl`, `door_rr`, `trunk`, `hood`, `lights` y `handbrake` (también admite algunos alias). Muchas pantallas usan una API propietaria diferente. Hasta identificar el servicio, broadcast, proveedor o protocolo real del decodificador de esta unidad, el estado permanecerá en “esperando datos”; no se inventan valores ni se transmite al bus del vehículo.
- **Mapa GPS offline:** desliza la pantalla principal hacia la izquierda para abrir el visor local. La ubicación se obtiene del GPS sin necesitar datos móviles, pero las teselas del mapa deben instalarse aparte. Coloca un archivo MBTiles compatible en `Android/data/com.rachav2.civiclauncher/files/maps/el_salvador.mbtiles`. No se incluye cartografía de terceros en el APK; usa un archivo de una fuente cuya licencia permita el uso offline. El visor lee el esquema raster estándar MBTiles a zoom 13.

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
