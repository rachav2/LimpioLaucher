# Civic Launcher

Launcher Android para pantallas de auto, con tema azul oscuro inspirado en el Honda Civic 2012. La interfaz se diseñó para 1280×720 y se adapta al tamaño de la ventana. El proyecto requiere Android 10 o posterior (minSdk 29) y está configurado para compilar con Java 17.

## Funciones

- Velocímetro en km/h basado en la ubicación GPS de la unidad. Solicita permiso de ubicación al primer inicio.
- Reloj y fecha en español.
- Panel de viaje con ubicación aproximada y clima actual. Usa el geocodificador del dispositivo y, como respaldo, Nominatim; el clima viene de Open-Meteo. Requiere conexión a internet.
- Reproductor con carátula, título, artista, progreso y controles. Prioriza Music BT y usa otras sesiones multimedia activas si hace falta. El acceso a notificaciones debe habilitarse para leer la sesión.
- Dock dinámico con hasta cuatro aplicaciones iniciadas desde el launcher y un acceso fijo al cajón de aplicaciones. Android no informa de forma fiable qué aplicaciones abrió otro launcher sin conceder acceso de uso.
- Paneles movibles y ocultables. Mantén pulsada una tarjeta para editarla; las posiciones se guardan entre reinicios.
- Barra inferior para apagar la pantalla, abrir el asistente, ajustes, teléfono Bluetooth y modo nocturno.

El launcher no incluye lectura OBD2; no muestra consumo ni temperatura del motor.

## Compilar el APK

### GitHub Actions

1. Abre la pestaña **Actions** del repositorio.
2. Selecciona **Build APK** y pulsa **Run workflow**. El workflow es manual; no se ejecuta con cada push.
3. Al terminar, descarga el artefacto **civic-launcher-apk**, que contiene `app-debug.apk`.

### Android Studio

Abre el proyecto y ejecuta la configuración `app`, o compílalo con `gradle assembleDebug` usando Gradle 8.7 y Java 17.

## Instalar en la unidad

Copia `app-debug.apk` a una memoria USB y ábrelo desde el explorador de archivos de la unidad, o instala con `adb install app-debug.apk`. Luego pulsa Home y elige **Civic Launcher** como launcher predeterminado.

## Activar la música

Android requiere acceso especial para leer la canción que suena:

1. Toca la tarjeta del reproductor y abre los ajustes de acceso a notificaciones.
2. Activa **Civic Launcher**. Si Android muestra «Ajuste restringido», permite los ajustes restringidos desde la ficha de la aplicación y repite el paso.

## Organización del código

- `MainActivity.kt`: pantalla principal, ciclo de vida, controles y gestión de paneles.
- `TripInfoRepository.kt`: consultas de geocodificación y clima fuera del hilo de interfaz, con espera progresiva entre reintentos.
- `InstalledAppsCatalog.kt`: detección de aplicaciones instaladas y selección de Music BT, BT y ZLINK.
- `SpeedRingView.kt`: dibujo del anillo del velocímetro.

## Personalizar

- Nombre del vehículo y disposición: `buildVehicleCard()` en `MainActivity.kt`.
- Foto del vehículo: `app/src/main/res/drawable-nodpi/car.png`.
- Colores: constantes de `MainActivity.kt`.
