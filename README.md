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
- **Google Maps integrado:** desliza la pantalla principal hacia la izquierda o toca el icono de Maps para abrir el mapa dentro de Civic Launcher. Muestra ubicación, tráfico disponible y estilo oscuro azul. El campo de destino abre la navegación giro a giro en la app Google Maps.

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

## Configurar Google Maps

1. Crea o selecciona un proyecto en [Google Cloud Console](https://console.cloud.google.com/).
2. Activa **Maps SDK for Android** y habilita la facturación del proyecto. Google Maps Platform puede aplicar cargos según el uso y las condiciones vigentes.
3. Crea una clave API restringida a Maps SDK for Android. Para una configuración de producción, restringe también la clave al paquete `com.rachav2.civiclauncher` y a la huella SHA-1 del certificado con el que se firma el APK.
4. En GitHub abre **Settings → Secrets and variables → Actions → New repository secret**. Crea el secreto `MAPS_API_KEY` con el valor de la clave. El workflow la lee desde el entorno y no guarda la clave en el código.
5. Vuelve a ejecutar **Actions → Build APK** y descarga el artefacto nuevo. Si el secreto no está configurado, el APK compilará igualmente, pero la pantalla mostrará instrucciones y permitirá abrir Google Maps externo.

El mapa se dibuja dentro de Civic Launcher con Maps SDK. El botón **Navegar** transfiere el destino a la app Google Maps para las indicaciones giro a giro; la navegación completa dentro de la propia interfaz requiere Navigation SDK, configuración y facturación adicionales. Consulta [Maps SDK para Android](https://developers.google.com/maps/documentation/android-sdk/overview), [configuración de la clave](https://developers.google.com/maps/documentation/android-sdk/get-api-key) y [Navigation SDK](https://developers.google.com/maps/documentation/navigation/android-sdk/overview).

## Personalizar

- Nombre del auto: `buildVehicleCard()` en `MainActivity.kt`.
- Foto del auto: reemplaza `app/src/main/res/drawable-nodpi/car.png`.
- Colores: constantes al inicio de `MainActivity.kt`.
- Intensidad del modo nocturno: `0x8C000000` en `buildOverlays()` y `0.25f` en `applyBrightness()`.
