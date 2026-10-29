package com.rachav2.civiclauncher

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.speech.RecognizerIntent
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MP = ViewGroup.LayoutParams.MATCH_PARENT
private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT

// Rejilla base de 1280x720: todo se calcula a partir de estas medidas para que nada se superponga.
private const val TABS_Y = 12f
private const val TABS_H = 52f
private const val CONTENT_Y = 78f
private const val CARD_H = 380f
private const val DOCK_Y = 472f
private const val DOCK_H = 150f
private const val DOCK_W = 862f
private const val BAR_Y = 636f
private const val BAR_H = 68f
private const val VEHICLE_X = 20f
private const val VEHICLE_W = 390f
private const val MID_X = 425f
private const val MID_W = 430f
private const val CONNECT_X = 870f
private const val CONNECT_W = 390f
private const val MEDIA_X = CONNECT_X
private const val MEDIA_W = CONNECT_W
private const val MEDIA_H = 544f
private const val DOCK_SLOTS = 4

private data class PanelRegion(val id: String, val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Launcher para pantallas Android de auto, con el diseño "Honda Civic 2012".
 *
 * El diseño base es de 1280x720 y se escala al tamaño real de la ventana.
 * La interfaz se construye por código para no depender de la densidad (dpi) de cada unidad.
 */
class MainActivity : Activity() {

    // ---- Paleta ----
    private val blue = 0xFF2F8CFF.toInt()
    private val lightBlue = 0xFF6CB4FF.toInt()
    private val textMuted = 0xFF9AA9BF.toInt()
    private val strokeColor = 0xFF3A4658.toInt()

    // ---- Escala ----
    private var sx = 1f
    private var sy = 1f
    private var ss = 1f
    private var uiW = 0
    private var uiH = 0
    private var uiReady = false
    private var resumed = false

    private lateinit var root: FrameLayout
    private lateinit var prefs: SharedPreferences
    private lateinit var tripInfoRepository: TripInfoRepository
    private val handler = Handler(Looper.getMainLooper())

    // ---- Vistas ----
    private lateinit var ring: SpeedRingView
    private lateinit var speedText: TextView
    private lateinit var greetingText: TextView
    private lateinit var weatherConditionText: TextView
    private lateinit var weatherIcon: TextView
    private lateinit var dateText: TextView
    private lateinit var locationText: TextView
    private lateinit var carConnectIcon: ImageView
    private lateinit var carConnectTitle: TextView
    private lateinit var carConnectHint: TextView
    private lateinit var carConnectButton: TextView
    private lateinit var artView: ImageView
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var playBtn: ImageView
    private lateinit var nightBtn: ImageView
    private lateinit var nightOverlay: View
    private lateinit var blackout: View
    private lateinit var drawer: FrameLayout
    private lateinit var drawerGrid: GridView
    private lateinit var recentDock: LinearLayout
    private lateinit var editOverlay: FrameLayout
    private lateinit var tabsBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var mediaCardView: LinearLayout
    private lateinit var mediaEditButton: TextView
    private lateinit var carConnectCard: LinearLayout
    private lateinit var tripSpeedometer: SpeedometerView
    private lateinit var tripLimitCard: View
    private lateinit var tripLimitSign: TextView
    private lateinit var tripLimitStatus: TextView
    private lateinit var tripLimitSource: TextView
    private lateinit var tripMaxText: TextView
    private lateinit var tripPageLocation: TextView
    private lateinit var tripPageCoords: TextView
    private lateinit var tripPageWeather: TextView
    private lateinit var tripStatusChip: TextView
    private lateinit var tripFromText: TextView
    private lateinit var tripToText: TextView
    private lateinit var tripDistanceValue: TextView
    private lateinit var tripTimeValue: TextView
    private lateinit var tripActionButton: TextView
    private lateinit var weatherValue: TextView
    private var selectedTab = "HOME"
    private val tabs = mutableListOf("HOME", "VIAJE", "MULTIMEDIA", "AJUSTES COCHE")
    private val homeViews = mutableListOf<View>()
    private var pendingPanelId: String? = null
    private var panelDragging = false
    private var panelDownX = 0f
    private var panelDownY = 0f
    private var panelStartOffsetX = 0f
    private var panelStartOffsetY = 0f
    private var panelDragViews: List<Triple<View, Int, Int>> = emptyList()
    private var panelLongPressTask: Runnable? = null
    private var moveModePanelId: String? = null

    // ---- Estado ----
    private var nightMode = false
    private var lastDate = ""
    private var userName = ""
    private var currentLocation: Location? = null
    private var currentSpeedKmh = 0
    private var currentSpeedLimitKmh = 90
    private var speedLimitFromMap = false
    private var lastSpeedLimitLocation: Location? = null
    private var lastSpeedLimitLookupAt = 0L
    private var speedLimitLookupInFlight = false
    private var tripActive = false
    private var tripDistanceMeters = 0f
    private var tripLastPoint: Location? = null
    private var movingGpsFixes = 0
    private var stationarySince = 0L
    private var overLimit = false
    private var tripMaxSpeedKmh = 0
    private var tripStartMs = 0L
    private var tripEndMs = 0L
    private val fallbackSpeedLimitKmh = 90

    private val panelRegions = listOf(
        PanelRegion("vehicle", VEHICLE_X, CONTENT_Y, VEHICLE_W, CARD_H),
        PanelRegion("clock", MID_X, CONTENT_Y, MID_W, CARD_H),
        PanelRegion("connect", CONNECT_X, CONTENT_Y, CONNECT_W, CARD_H),
        PanelRegion("dock", VEHICLE_X, DOCK_Y, DOCK_W, DOCK_H),
        PanelRegion("media", MEDIA_X, CONTENT_Y, MEDIA_W, MEDIA_H),
        PanelRegion("bottom", 20f, BAR_Y, 1240f, BAR_H)
    )

    // ---- Ubicación y clima ----
    private var placeName: String? = null
    private var weatherLabel: String? = null

    // ---- Medios ----
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var lastState: PlaybackState? = null
    private var durationMs = 0L
    private var isPlaying = false

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMeta(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlayState(state)
        }

        override fun onSessionDestroyed() {
            setController(null)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (uiReady) {
                updateProgress()
                updateDate()
                if (selectedTab == "VIAJE") renderTabContent("VIAJE") // el cronómetro del recorrido
            }
            handler.postDelayed(this, 1000L)
        }
    }

    // ---- GPS ----
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    private val locListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!uiReady) return
            val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            val shown = if (kmh < 2f) 0 else kmh.roundToInt()
            currentLocation = Location(location)
            currentSpeedKmh = shown
            speedText.text = shown.toString()
            ring.setSpeed(shown)
            updateAutomaticTrip(location)
            if (tripActive && shown > tripMaxSpeedKmh) {
                tripMaxSpeedKmh = shown
                prefs.edit().putInt("trip_max_kmh", shown).apply()
            }
            maybeLookupSpeedLimit(location)
            updateSpeedWarning(shown)
            if (::tripSpeedometer.isInitialized) tripSpeedometer.setReading(shown, currentSpeedLimitKmh)
            if (uiReady) updateTripViews()
            tripInfoRepository.refresh(location)
        }
    }

    // ---- Apps instaladas ----
    private val appCatalog by lazy { InstalledAppsCatalog(this) }
    private var apps: List<InstalledApp> = emptyList()

    // =====================================================================
    // Ciclo de vida
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("civic", Context.MODE_PRIVATE)
        migrateLayoutPrefs()
        nightMode = prefs.getBoolean("night", false)
        placeName = prefs.getString("place", null)
        weatherLabel = prefs.getString("weather", null)
        userName = prefs.getString("user_name", "") ?: ""
        tripActive = prefs.getBoolean("trip_active", false)
        tripDistanceMeters = prefs.getFloat("trip_distance_m", 0f)
        tripMaxSpeedKmh = prefs.getInt("trip_max_kmh", 0)
        tripStartMs = prefs.getLong("trip_start_ms", 0L)
        tripEndMs = prefs.getLong("trip_end_ms", 0L)
        tripInfoRepository = TripInfoRepository(
            context = this,
            onPlaceUpdated = { value ->
                placeName = value
                prefs.edit().putString("place", value).apply()
                if (uiReady) updateTripViews()
            },
            onWeatherUpdated = { value ->
                weatherLabel = value
                prefs.edit().putString("weather", value).apply()
                if (uiReady) updateTripViews()
            }
        )

        root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF05090F.toInt(), 0xFF0F1E2E.toInt(), 0xFF070D14.toInt())
        )
        setContentView(root)

        // Se construye con el tamaño real de la ventana (algunas unidades reservan una barra propia)
        root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != uiW || h != uiH)) {
                uiW = w
                uiH = h
                root.post { buildUi() }
            }
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        hideSystemUi()
        startGps()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        if (uiReady) refreshDynamic()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        handler.removeCallbacks(ticker)
        stopGps()
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            // nada que hacer
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    override fun onDestroy() {
        if (::tripInfoRepository.isInitialized) tripInfoRepository.close()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeDrawer() // al pulsar Home se vuelve a la pantalla principal
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Un launcher no debe cerrarse; solo cierra lo que esté abierto.
        if (uiReady && blackout.visibility == View.VISIBLE) {
            wakeScreen()
        } else if (uiReady && drawer.visibility == View.VISIBLE) {
            closeDrawer()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startGps()
    }

    /** El diseño cambió de posiciones: se descartan los desplazamientos guardados del diseño anterior. */
    private fun migrateLayoutPrefs() {
        if (prefs.getInt("layout_version", 0) >= LAYOUT_VERSION) return
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("panel.") }.forEach { editor.remove(it) }
        editor.putInt("layout_version", LAYOUT_VERSION).apply()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    // =====================================================================
    // Helpers de interfaz
    // =====================================================================

    private fun px(v: Float): Int = (v * ss).roundToInt()
    private fun xx(v: Float): Int = (v * sx).roundToInt()
    private fun yy(v: Float): Int = (v * sy).roundToInt()

    private fun lp(l: Float, t: Float, w: Float, h: Float): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(xx(w), yy(h)).apply {
            leftMargin = xx(l)
            topMargin = yy(t)
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun rounded(color: Int, radius: Float, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius * ss
            setColor(color)
            if (stroke != 0) setStroke(max(1, px(1.5f)), stroke)
        }

    private fun panelBg(
        radius: Float = 24f,
        top: Int = 0xE62C343E.toInt(),
        bottom: Int = 0xE61B2129.toInt()
    ): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        g.cornerRadius = radius * ss
        g.setStroke(max(1, px(1.5f)), strokeColor)
        return g
    }

    /** Resplandor azul suave detrás de los iconos de la barra inferior. */
    private fun glow(): GradientDrawable = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        gradientRadius = px(38f).toFloat()
        colors = intArrayOf(0x472F8CFF, 0x002F8CFF)
    }

    /** Fondo rojizo de la tarjeta de límite cuando se supera la velocidad permitida. */
    private fun alertPanelBg(): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xE63E1B24.toInt(), 0xE6240F15.toInt())
        )
        g.cornerRadius = 24f * ss
        g.setStroke(max(2, px(2f)), 0xFFFF5A5A.toInt())
        return g
    }

    /** Parámetros de una tarjeta que ya incluyen el desplazamiento guardado por el usuario. */
    private fun panelParams(id: String, l: Float, t: Float, w: Float, h: Float): FrameLayout.LayoutParams =
        lp(l, t, w, h).apply {
            leftMargin += xx(prefs.getFloat("panel.$id.x", 0f))
            topMargin += yy(prefs.getFloat("panel.$id.y", 0f))
        }

    /** Las capas de noche y de pantalla apagada siempre van por encima de todo lo demás. */
    private fun restackOverlays() {
        if (::nightOverlay.isInitialized) nightOverlay.bringToFront()
        if (::blackout.isInitialized) blackout.bringToFront()
    }

    private fun card(l: Float, t: Float, w: Float, h: Float): View {
        val v = View(this)
        v.background = panelBg()
        root.addView(v, lp(l, t, w, h))
        return v
    }

    private fun label(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * ss)
        tv.setTextColor(color)
        if (bold) tv.typeface = Typeface.DEFAULT_BOLD
        tv.includeFontPadding = false
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        return tv
    }

    private fun styleClock(c: TextClock, size: Float, color: Int, bold: Boolean) {
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * ss)
        c.setTextColor(color)
        if (bold) c.typeface = Typeface.DEFAULT_BOLD
        c.includeFontPadding = false
        c.maxLines = 1
    }

    private fun iconView(res: Int, size: Float, bg: Int?, pad: Float, tint: Int = Color.WHITE): ImageView {
        val iv = ImageView(this)
        iv.setImageResource(res)
        iv.setColorFilter(tint)
        if (bg != null) iv.background = rounded(bg, size * 0.26f)
        val p = px(pad)
        iv.setPadding(p, p, p, p)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        return iv
    }

    private fun roundClip(v: View, fraction: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.width * fraction)
            }
        }
        v.clipToOutline = true
    }

    // =====================================================================
    // Construcción de la pantalla
    // =====================================================================

    private fun buildUi() {
        root.removeAllViews()
        sx = uiW / 1280f
        sy = uiH / 720f
        ss = min(sx, sy)

        buildVehicleCard()
        buildTabs()
        buildClockCard()
        buildCarConnectCard()
        buildDockCard()
        buildMediaCard()
        buildDrawer()
        buildBottomBar()
        configureMovablePanels()
        buildOverlays()
        buildEditOverlay()
        homeViews.clear()
        for (i in 0 until root.childCount) {
            val view = root.getChildAt(i)
            if (view !== tabsBar && view !== bottomBar && view !== drawer && view !== editOverlay &&
                view !== nightOverlay && view !== blackout) homeViews.add(view)
        }
        showSelectedTab()
        updateCarConnectCard()
        restackOverlays()

        uiReady = true
        showIdle()
        if (resumed) refreshDynamic()
    }

    private fun refreshDynamic() {
        refreshRecentDock()
        setupMedia()
        updateCarConnectCard()
        applyNight()
        updateTripViews()
        updateDate()
        seedLocation()
    }

    // ---- Tarjeta del vehículo (velocímetro) ----

    private fun buildVehicleCard() {
        card(VEHICLE_X, CONTENT_Y, VEHICLE_W, CARD_H)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(
            iconView(R.drawable.ic_car, 28f, null, 0f, lightBlue),
            LinearLayout.LayoutParams(px(28f), px(28f)).apply { rightMargin = px(10f) }
        )
        greetingText = label(greeting(), 24f, lightBlue, true)
        head.addView(greetingText, LinearLayout.LayoutParams(0, WC, 1f))
        root.addView(head, lp(VEHICLE_X + 20f, CONTENT_Y + 14f, VEHICLE_W - 40f, 36f))

        // El velocímetro queda por encima de la foto: nunca se montan.
        val ringSize = 160f
        val ringX = VEHICLE_X + (VEHICLE_W - ringSize) / 2f
        ring = SpeedRingView(this, 11f * ss)
        root.addView(ring, lp(ringX, 134f, ringSize, ringSize))

        speedText = label("0", 60f, Color.WHITE, true)
        speedText.gravity = Gravity.CENTER
        root.addView(speedText, lp(ringX, 166f, ringSize, 66f))

        val unit = label("km/h", 20f, textMuted)
        unit.gravity = Gravity.CENTER
        root.addView(unit, lp(ringX, 232f, ringSize, 26f))

        val car = ImageView(this)
        car.setImageResource(R.drawable.car)
        car.scaleType = ImageView.ScaleType.FIT_CENTER
        root.addView(car, lp(VEHICLE_X + 12f, 304f, VEHICLE_W - 24f, 148f))
    }

    private fun greeting(): String =
        if (userName.isBlank()) "Hola, Bienvenido" else "Hola, Bienvenido $userName"

    private fun tabsParams(): FrameLayout.LayoutParams = lp(20f, TABS_Y, 1240f, TABS_H)

    /** Línea fina bajo toda la barra de pestañas, como en el diseño de referencia. */
    private fun tabsBarBackground(): Drawable {
        val line = ColorDrawable(0x40FFFFFF)
        return LayerDrawable(arrayOf<Drawable>(line)).apply {
            setLayerInset(0, 0, yy(TABS_H) - max(1, px(1.5f)), 0, 0)
        }
    }

    /** Pestaña activa: panel con esquinas superiores redondeadas y subrayado azul. */
    private fun activeTabBackground(): Drawable {
        val r = 16f * ss
        val panel = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF3C4651.toInt(), 0xFF2B333C.toInt())
        ).apply { cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
        val underline = ColorDrawable(lightBlue)
        return LayerDrawable(arrayOf<Drawable>(panel, underline)).apply {
            setLayerInset(1, 0, yy(TABS_H) - max(3, px(3f)), 0, 0)
        }
    }

    private fun tabDivider(): View = View(this).apply { setBackgroundColor(0x33FFFFFF) }

    private fun buildTabs() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = tabsBarBackground()
        }
        tabsBar = bar
        tabs.forEachIndexed { index, tab ->
            if (index > 0) bar.addView(tabDivider(), LinearLayout.LayoutParams(max(1, px(1.5f)), px(30f)))
            val isCurrent = tab == selectedTab
            val button = TextView(this).apply {
                text = tab
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 24f * ss)
                setTextColor(if (isCurrent) Color.WHITE else textMuted)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                isClickable = true
                setOnClickListener { selectTab(tab) }
                if (isCurrent) background = activeTabBackground()
            }
            bar.addView(button, LinearLayout.LayoutParams(0, MP, 1f))
        }
        bar.addView(tabDivider(), LinearLayout.LayoutParams(max(1, px(1.5f)), px(30f)))
        val add = TextView(this).apply {
            text = "+"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 42f * ss)
            setTextColor(0xFFC9D3E3.toInt())
            isClickable = true
            setOnClickListener {
                if (selectedTab == "HOME") showManager()
                else showManagerDialog()
            }
        }
        bar.addView(add, LinearLayout.LayoutParams(px(76f), MP))
        root.addView(bar, tabsParams())
    }

    private fun selectTab(tab: String) {
        selectedTab = tab
        showSelectedTab()
        val index = root.indexOfChild(tabsBar)
        root.removeView(tabsBar)
        buildTabs()
        root.removeView(tabsBar)
        root.addView(tabsBar, index, tabsParams())
        bottomBar.bringToFront()
        tabsBar.bringToFront()
        restackOverlays()
    }

    private fun showTabContent(tab: String) {
        editOverlay.removeAllViews()
        if (tab == "MULTIMEDIA" || tab == "VIAJE") {
            // Páginas con varias tarjetas propias: se dibujan directamente sobre el fondo.
            editOverlay.setBackgroundColor(Color.TRANSPARENT)
            editOverlay.isClickable = false
            if (tab == "MULTIMEDIA") buildMultimediaPage() else buildTripPage()
            editOverlay.visibility = View.VISIBLE
            return
        }
        editOverlay.setBackgroundColor(0xAA02060E.toInt())
        editOverlay.isClickable = true
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = panelBg(24f)
            setPadding(px(28f), px(20f), px(28f), px(20f))
        }
        val heading = label(tab, 30f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        panel.addView(heading, LinearLayout.LayoutParams(MP, px(48f)))
        when (tab) {
            "MULTIMEDIA" -> Unit
            "AJUSTES COCHE" -> buildCarSettingsPage(panel)
            else -> buildGenericPage(panel, tab)
        }
        editOverlay.addView(panel, lp(240f, CONTENT_Y, 800f, MEDIA_H))
        editOverlay.visibility = View.VISIBLE
    }

    private fun showSelectedTab() {
        editOverlay.visibility = View.GONE
        if (selectedTab == "HOME") {
            homeViews.forEach { it.visibility = View.VISIBLE }
            mediaCardView.layoutParams = panelParams("media", MEDIA_X, CONTENT_Y, MEDIA_W, MEDIA_H)
            mediaCardView.visibility = View.GONE
            mediaEditButton.visibility = View.GONE
            applyPanelVisibility()
        } else {
            homeViews.forEach { it.visibility = View.GONE }
            if (selectedTab == "MULTIMEDIA") {
                mediaCardView.layoutParams = lp(VEHICLE_X, CONTENT_Y, 570f, MEDIA_H)
                mediaCardView.visibility = View.VISIBLE
                mediaEditButton.visibility = View.GONE
            }
            showTabContent(selectedTab)
        }
    }

    /** Vuelve a ocultar los paneles que el usuario decidió esconder. */
    private fun applyPanelVisibility() {
        for (region in panelRegions) {
            if (prefs.getBoolean("panel.${region.id}.hidden", false)) {
                root.childrenForPanel(region.id).forEach { it.visibility = View.GONE }
            }
        }
    }

    private fun <T : View> place(view: T, l: Float, t: Float, w: Float, h: Float): T {
        editOverlay.addView(view, lp(l, t, w, h))
        return view
    }

    private fun pageCardBg(l: Float, t: Float, w: Float, h: Float): View =
        place(View(this).apply { background = panelBg() }, l, t, w, h)

    /**
     * Pestaña VIAJE: cuatro tarjetas en cuadrícula 2x2.
     * Velocímetro, límite de velocidad, ubicación actual y recorrido actual.
     */
    private fun buildTripPage() {
        val leftX = VEHICLE_X
        val rightX = 647f
        val colW = 613f
        val topY = CONTENT_Y
        val botY = CONTENT_Y + 279f
        val rowH = 265f

        // 1) Velocímetro con tacómetro
        pageCardBg(leftX, topY, colW, rowH)
        place(label("VELOCÍMETRO", 16f, textMuted, true), leftX + 24f, topY + 16f, 300f, 24f)
        tripSpeedometer = SpeedometerView(this)
        tripSpeedometer.setReading(currentSpeedKmh, currentSpeedLimitKmh)
        place(tripSpeedometer, leftX + (colW - 440f) / 2f, topY + 38f, 440f, 220f)

        // 2) Límite de velocidad (avisa cuando se excede)
        tripLimitCard = pageCardBg(rightX, topY, colW, rowH)
        place(label("LÍMITE DE VELOCIDAD", 16f, textMuted, true), rightX + 24f, topY + 16f, 400f, 24f)
        tripLimitSign = TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 62f * ss)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(px(13f), 0xFFE53935.toInt())
            }
        }
        place(tripLimitSign, rightX + 36f, topY + 70f, 150f, 150f)
        tripLimitStatus = place(label("", 26f, Color.WHITE, true), rightX + 214f, topY + 78f, colW - 236f, 40f)
        tripLimitSource = place(label("", 17f, textMuted), rightX + 214f, topY + 124f, colW - 236f, 26f)
        tripMaxText = place(label("", 21f, lightBlue, true), rightX + 214f, topY + 168f, colW - 236f, 32f)

        // 3) Ubicación actual
        pageCardBg(leftX, botY, colW, rowH)
        place(label("UBICACIÓN ACTUAL", 16f, textMuted, true), leftX + 24f, botY + 16f, 300f, 24f)
        tripPageLocation = place(label("Buscando ubicación GPS…", 26f, Color.WHITE, true).apply { maxLines = 3 },
            leftX + 24f, botY + 52f, 290f, 108f)
        tripPageCoords = place(label("", 16f, textMuted), leftX + 24f, botY + 168f, 290f, 22f)
        tripPageWeather = place(label("", 19f, lightBlue, true), leftX + 24f, botY + 200f, 290f, 28f)
        val map = ImageView(this).apply {
            setImageResource(R.drawable.static_trip_map)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Mapa estático en azul oscuro"
        }
        roundClip(map, 0.06f)
        place(map, leftX + 336f, botY + 24f, 253f, 217f)
        place(iconView(R.drawable.ic_pin, 52f, null, 4f, lightBlue),
            leftX + 336f + (253f - 52f) / 2f, botY + 24f + (217f - 52f) / 2f - 10f, 52f, 52f)

        // 4) Recorrido actual, todo en una sola tarjeta
        pageCardBg(rightX, botY, colW, rowH)
        place(label("RECORRIDO ACTUAL", 16f, textMuted, true), rightX + 24f, botY + 16f, 300f, 24f)
        tripStatusChip = place(TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 14f * ss)
        }, rightX + colW - 24f - 150f, botY + 14f, 150f, 28f)

        place(label("●  DESDE", 14f, lightBlue, true), rightX + 24f, botY + 58f, 290f, 20f)
        tripFromText = place(label("—", 20f, Color.WHITE, true), rightX + 24f, botY + 80f, 290f, 28f)
        place(label("●  HASTA", 14f, 0xFFFFC857.toInt(), true), rightX + 24f, botY + 122f, 290f, 20f)
        tripToText = place(label("—", 20f, Color.WHITE, true), rightX + 24f, botY + 144f, 290f, 28f)

        place(View(this).apply { setBackgroundColor(0x26FFFFFF) }, rightX + 326f, botY + 60f, 1.5f, 118f)

        place(label("DISTANCIA", 14f, textMuted, true), rightX + 350f, botY + 58f, 240f, 20f)
        tripDistanceValue = place(label("0.0 km", 40f, Color.WHITE, true), rightX + 350f, botY + 78f, 240f, 50f)
        place(label("TIEMPO", 14f, textMuted, true), rightX + 350f, botY + 132f, 240f, 20f)
        tripTimeValue = place(label("--:--", 36f, lightBlue, true), rightX + 350f, botY + 152f, 240f, 46f)

        tripActionButton = place(TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f * ss)
            background = rounded(0xFF2F4A78.toInt(), 18f, strokeColor)
            isClickable = true
            setOnClickListener { toggleTrip() }
        }, rightX + 24f, botY + 208f, colW - 48f, 44f)

        renderTabContent("VIAJE")
    }

    private fun buildMultimediaPage() {
        val radioCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = panelBg()
            setPadding(px(22f), px(16f), px(22f), px(16f))
        }
        radioCard.addView(label("RADIO FM", 24f, Color.WHITE, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(MP, px(38f)))
        radioCard.addView(label("Sintoniza desde la app de radio del vehículo", 17f, textMuted).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(MP, px(40f)))
        val radioButton = TextView(this).apply {
            text = "ABRIR RADIO"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 20f * ss)
            background = rounded(0xFF2F4A78.toInt(), 18f, strokeColor)
            isClickable = true
            setOnClickListener { openRadio() }
        }
        radioCard.addView(radioButton, LinearLayout.LayoutParams(MP, px(56f)))
        editOverlay.addView(radioCard, lp(604f, CONTENT_Y, 656f, 190f))

        val appsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg()
            setPadding(px(18f), px(14f), px(18f), px(14f))
        }
        appsCard.addView(label("APPS MULTIMEDIA", 20f, textMuted, true),
            LinearLayout.LayoutParams(MP, px(32f)))
        val appRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val keywords = listOf("music", "radio", "spotify", "youtube", "video", "vlc", "podcast", "fm")
        val mediaApps = appCatalog.loadLauncherApps()
            .filter { app -> app.component.packageName != packageName && keywords.any { key ->
                app.label.contains(key, ignoreCase = true) || app.component.packageName.contains(key, ignoreCase = true)
            } }
            .distinctBy { it.component.packageName }
            .take(6)
        if (mediaApps.isEmpty()) {
            appRow.addView(label("No se encontraron apps multimedia instaladas", 17f, textMuted).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(MP, MP))
        } else {
            mediaApps.forEach { app ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = true
                    setOnClickListener { launchComponent(app.component) }
                    contentDescription = "Abrir ${app.label}"
                }
                val icon = ImageView(this).apply {
                    setImageDrawable(app.icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                roundClip(icon, 0.22f)
                cell.addView(icon, LinearLayout.LayoutParams(px(72f), px(72f)))
                cell.addView(label(app.label, 16f, Color.WHITE).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(8f) })
                appRow.addView(cell, LinearLayout.LayoutParams(0, MP, 1f))
            }
        }
        appsCard.addView(appRow, LinearLayout.LayoutParams(MP, 0, 1f))
        editOverlay.addView(appsCard, lp(604f, CONTENT_Y + 204f, 656f, 340f))
    }

    private fun togglePlayback() {
        val activeController = controller
        if (activeController == null) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        } else if (isPlaying) {
            activeController.transportControls.pause()
        } else {
            activeController.transportControls.play()
        }
    }

    private fun buildCarSettingsPage(panel: LinearLayout) {
        panel.addView(label("Nombre para el saludo de HOME", 18f, textMuted), LinearLayout.LayoutParams(MP, px(28f)))
        val nameInput = android.widget.EditText(this).apply {
            setSingleLine(true)
            setText(userName)
            hint = "Escribe tu nombre"
            setTextColor(Color.WHITE)
            setHintTextColor(textMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 20f * ss)
            background = rounded(0xFF152746.toInt(), 12f, strokeColor)
            setPadding(px(12f), 0, px(12f), 0)
        }
        panel.addView(nameInput, LinearLayout.LayoutParams(MP, px(48f)))
        val saveName = TextView(this).apply {
            text = "GUARDAR NOMBRE"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFF304E81.toInt(), 16f)
            setOnClickListener {
                userName = nameInput.text.toString().trim()
                prefs.edit().putString("user_name", userName).apply()
                if (::greetingText.isInitialized) greetingText.text = greeting()
                toast("Nombre guardado")
            }
        }
        panel.addView(saveName, LinearLayout.LayoutParams(MP, px(42f)).apply { topMargin = px(6f) })
        listOf("Aviso de límite de velocidad (90 km/h)", "Modo nocturno", "Mantener pantalla activa", "Usar ubicación GPS").forEachIndexed { index, caption ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(label(caption, 19f, Color.WHITE), LinearLayout.LayoutParams(0, MP, 1f))
            val toggle = android.widget.Switch(this@MainActivity).apply {
                isChecked = when (index) { 0, 3 -> true; 1 -> nightMode; else -> false }
                if (index == 1) setOnCheckedChangeListener { _, checked -> if (nightMode != checked) toggleNight() }
            }
            row.addView(toggle)
            panel.addView(row, LinearLayout.LayoutParams(MP, 0, 1f))
        }
    }

    private fun buildGenericPage(panel: LinearLayout, tab: String) {
        panel.addView(label("Sección: $tab", 20f, textMuted), LinearLayout.LayoutParams(MP, px(44f)))
    }

    private fun tripDurationText(): String {
        if (tripStartMs <= 0L) return "--:--"
        val end = if (tripActive) System.currentTimeMillis() else tripEndMs
        if (end < tripStartMs) return "--:--"
        val total = (end - tripStartMs) / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0L) "%d:%02d:%02d".format(Locale.US, h, m, s) else "%02d:%02d".format(Locale.US, m, s)
    }

    private fun renderTabContent(tab: String) {
        if (tab != "VIAJE" || !::tripPageLocation.isInitialized) return

        // Velocímetro y límite de velocidad
        val over = currentSpeedKmh > currentSpeedLimitKmh
        if (::tripSpeedometer.isInitialized) tripSpeedometer.setReading(currentSpeedKmh, currentSpeedLimitKmh)
        tripLimitSign.text = currentSpeedLimitKmh.toString()
        tripLimitCard.background = if (over) alertPanelBg() else panelBg()
        tripLimitStatus.text = if (over) "⚠ Exceso de velocidad" else "Dentro del límite"
        tripLimitStatus.setTextColor(if (over) 0xFFFF6868.toInt() else 0xFF7BE0A8.toInt())
        tripLimitSource.text = if (speedLimitFromMap) "Dato del mapa · © OpenStreetMap" else "Límite de referencia"
        tripMaxText.text = "Máx. del recorrido  ·  $tripMaxSpeedKmh km/h"

        // Ubicación actual
        val gps = currentLocation
        tripPageLocation.text = placeName
            ?: gps?.let { "%.5f, %.5f".format(Locale.US, it.latitude, it.longitude) }
            ?: "Buscando ubicación GPS…"
        tripPageCoords.text = gps?.let { "GPS  %.5f, %.5f".format(Locale.US, it.latitude, it.longitude) }
            ?: "Esperando señal GPS…"
        val weather = (weatherLabel ?: "--°C · Obteniendo clima…").split(" · ", limit = 2)
        tripPageWeather.text = "${weatherEmoji(weather.getOrNull(1) ?: "")}  ${weather.joinToString(" · ")}"

        // Recorrido actual
        tripStatusChip.text = if (tripActive) "●  EN CURSO" else "EN ESPERA"
        tripStatusChip.setTextColor(if (tripActive) 0xFF7BE0A8.toInt() else textMuted)
        tripStatusChip.background = rounded(if (tripActive) 0x3328C76F else 0x22FFFFFF, 14f)
        tripFromText.text = prefs.getString("trip_start_place", null) ?: "—"
        tripToText.text = when {
            tripActive -> "En curso · ${placeName ?: "ubicación GPS"}"
            else -> prefs.getString("trip_end_place", null) ?: "—"
        }
        tripDistanceValue.text = "${"%.1f".format(Locale.US, tripDistanceMeters / 1000f)} km"
        tripTimeValue.text = tripDurationText()
        tripActionButton.text = if (tripActive) "FINALIZAR RECORRIDO" else "ESPERANDO MOVIMIENTO GPS"
    }

    private fun toggleTrip() {
        if (!tripActive) {
            val point = currentLocation
            if (point == null) {
                toast("Esperando una ubicación GPS para iniciar")
                return
            }
            startTrip(point)
        } else finishTrip(currentLocation)
        renderTabContent("VIAJE")
    }

    private fun startTrip(location: Location) {
        tripActive = true
        tripDistanceMeters = 0f
        tripLastPoint = Location(location)
        stationarySince = 0L
        val start = placeName ?: locationLabel(location)
        tripMaxSpeedKmh = 0
        tripStartMs = System.currentTimeMillis()
        tripEndMs = 0L
        prefs.edit().putBoolean("trip_active", true).putFloat("trip_distance_m", 0f)
            .putInt("trip_max_kmh", 0).putLong("trip_start_ms", tripStartMs).putLong("trip_end_ms", 0L)
            .putString("trip_start_place", start).remove("trip_end_place").apply()
        renderTabContent("VIAJE")
    }

    private fun finishTrip(location: Location?) {
        tripActive = false
        tripLastPoint = null
        movingGpsFixes = 0
        stationarySince = 0L
        val end = placeName ?: location?.let { locationLabel(it) } ?: "Ubicación GPS actual"
        tripEndMs = System.currentTimeMillis()
        prefs.edit().putBoolean("trip_active", false).putString("trip_end_place", end)
            .putLong("trip_end_ms", tripEndMs)
            .putFloat("trip_distance_m", tripDistanceMeters).apply()
        renderTabContent("VIAJE")
    }

    private fun locationLabel(location: Location): String =
        "GPS %.5f, %.5f".format(Locale.US, location.latitude, location.longitude)

    private fun updateAutomaticTrip(location: Location) {
        val now = SystemClock.elapsedRealtime()
        when {
            currentSpeedKmh >= 5 -> {
                movingGpsFixes++
                stationarySince = 0L
                if (!tripActive && movingGpsFixes >= 3) startTrip(location)
            }
            currentSpeedKmh < 2 -> {
                movingGpsFixes = 0
                if (tripActive) {
                    if (stationarySince == 0L) stationarySince = now
                    else if (now - stationarySince >= TRIP_STOP_AFTER_STATIONARY_MS) finishTrip(location)
                }
            }
            else -> {
                movingGpsFixes = 0
                stationarySince = 0L
            }
        }
        if (tripActive) updateTripTracking(location)
    }

    private fun updateTripTracking(location: Location) {
        if (!tripActive) return
        val previous = tripLastPoint
        if (previous != null) {
            val segment = previous.distanceTo(location)
            if (segment in 5f..500f) tripDistanceMeters += segment
        }
        tripLastPoint = Location(location)
        prefs.edit().putFloat("trip_distance_m", tripDistanceMeters).apply()
    }

    private fun updateSpeedWarning(speed: Int) {
        val nowOver = speed > currentSpeedLimitKmh
        if (nowOver && !overLimit) toast("Exceso sobre el límite aproximado de $currentSpeedLimitKmh km/h")
        overLimit = nowOver
    }

    private fun maybeLookupSpeedLimit(location: Location) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastSpeedLimitLocation
        if (speedLimitLookupInFlight) return
        if (previous != null) {
            val age = now - lastSpeedLimitLookupAt
            val moved = location.distanceTo(previous) >= SPEED_LIMIT_REFRESH_DISTANCE_M
            if (age < SPEED_LIMIT_MIN_INTERVAL_MS || (!moved && age < SPEED_LIMIT_MAX_INTERVAL_MS)) return
        }

        lastSpeedLimitLocation = Location(location)
        lastSpeedLimitLookupAt = now
        speedLimitLookupInFlight = true
        tripInfoRepository.lookupSpeedLimit(location) { limit ->
            speedLimitLookupInFlight = false
            currentSpeedLimitKmh = limit ?: fallbackSpeedLimitKmh
            speedLimitFromMap = limit != null
            updateSpeedWarning(currentSpeedKmh)
            if (::tripSpeedometer.isInitialized) tripSpeedometer.setReading(currentSpeedKmh, currentSpeedLimitKmh)
            if (selectedTab == "VIAJE") renderTabContent("VIAJE")
        }
    }

    private fun showManagerDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Gestionar launcher")
            .setItems(arrayOf("Añadir pestaña", "Gestionar widgets y carpetas")) { _, which ->
                if (which == 0) addTabDialog() else showManager()
            }
            .setNegativeButton("Cerrar", null).show()
    }

    private fun showManager() {
        buildEditOverlay()
        editOverlay.visibility = View.VISIBLE
        tabsBar.bringToFront()
        bottomBar.bringToFront()
        tabsBar.bringToFront()
        restackOverlays()
    }

    // ---- Tarjeta central: hora, fecha, ubicación y clima ----

    private fun buildClockCard() {
        card(MID_X, CONTENT_Y, MID_W, CARD_H)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.BOTTOM

        val time = TextClock(this)
        time.format12Hour = "h:mm"
        time.format24Hour = "HH:mm"
        styleClock(time, 66f, Color.WHITE, true)
        row.addView(time)

        val ampm = TextClock(this)
        ampm.format12Hour = "a"
        ampm.format24Hour = " "
        styleClock(ampm, 20f, textMuted, false)
        row.addView(
            ampm,
            LinearLayout.LayoutParams(WC, WC).apply {
                leftMargin = px(8f)
                bottomMargin = px(10f)
            }
        )
        root.addView(row, lp(MID_X + 24f, CONTENT_Y + 18f, MID_W - 48f, 86f))

        dateText = label("", 21f, textMuted)
        root.addView(dateText, lp(MID_X + 24f, CONTENT_Y + 105f, MID_W - 48f, 32f))
        root.addView(View(this).apply { setBackgroundColor(0x33FFFFFF) },
            lp(MID_X + 24f, CONTENT_Y + 150f, MID_W - 48f, 1f))

        root.addView(label("Estamos en", 17f, textMuted, true),
            lp(MID_X + 24f, CONTENT_Y + 166f, MID_W - 48f, 24f))
        locationText = label("Buscando ubicación GPS…", 24f, Color.WHITE, true)
        locationText.maxLines = 2
        root.addView(locationText, lp(MID_X + 24f, CONTENT_Y + 194f, MID_W - 48f, 50f))

        root.addView(label("El clima está:", 17f, textMuted, true),
            lp(MID_X + 24f, CONTENT_Y + 254f, MID_W - 48f, 24f))
        weatherIcon = label("⛅", 54f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        root.addView(weatherIcon, lp(MID_X + 26f, CONTENT_Y + 282f, 74f, 62f))
        weatherValue = label("--°C", 48f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(weatherValue, lp(MID_X + 112f, CONTENT_Y + 282f, MID_W - 136f, 62f))
        weatherConditionText = label("Obteniendo clima…", 18f, Color.WHITE)
        weatherConditionText.gravity = Gravity.CENTER
        root.addView(weatherConditionText, lp(MID_X + 24f, CONTENT_Y + 342f, MID_W - 48f, 28f))
    }

    private fun updateDate() {
        val s = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("es")).format(Date())
            .replaceFirstChar { it.uppercase() }
        if (s != lastDate) {
            lastDate = s
            dateText.text = s
        }
    }

    private fun buildCarConnectCard() {
        carConnectCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = panelBg()
            setPadding(px(22f), px(18f), px(22f), px(18f))
        }
        carConnectCard.addView(label("CARPLAY  ·  ANDROID AUTO", 19f, textMuted, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(MP, px(32f)))

        carConnectIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = rounded(0xFF1C2A3D.toInt(), 28f)
            setPadding(px(18f), px(18f), px(18f), px(18f))
            setImageResource(R.drawable.ic_car)
            setColorFilter(lightBlue)
            contentDescription = "Icono de la aplicación de conexión del vehículo"
        }
        carConnectCard.addView(carConnectIcon,
            LinearLayout.LayoutParams(px(148f), px(148f)).apply { topMargin = px(16f) })

        carConnectTitle = label("ZLINK5 / CPLINK", 23f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
        }
        carConnectCard.addView(carConnectTitle,
            LinearLayout.LayoutParams(MP, px(38f)).apply { topMargin = px(12f) })
        carConnectHint = label("Buscando app de conexión…", 15f, textMuted).apply {
            gravity = Gravity.CENTER
        }
        carConnectCard.addView(carConnectHint, LinearLayout.LayoutParams(MP, px(28f)))

        carConnectButton = TextView(this).apply {
            text = "CONECTAR"
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 23f * ss)
            setTextColor(Color.WHITE)
            background = rounded(0xFF2F4A78.toInt(), 20f, lightBlue)
            isClickable = true
            setOnClickListener { openAndroidAuto() }
        }
        carConnectCard.addView(carConnectButton,
            LinearLayout.LayoutParams(MP, px(64f)).apply { topMargin = px(12f) })
        root.addView(carConnectCard, panelParams("connect", CONNECT_X, CONTENT_Y, CONNECT_W, CARD_H))
    }

    private fun updateCarConnectCard() {
        if (!::carConnectIcon.isInitialized) return
        val component = findCarIntegration()
        if (component == null) {
            carConnectIcon.setImageResource(R.drawable.ic_car)
            carConnectIcon.setColorFilter(lightBlue)
            carConnectTitle.text = "ZLINK5 / CPLINK"
            carConnectHint.text = "No se encontró una app instalada"
            carConnectButton.text = "BUSCAR CONEXIÓN"
            return
        }
        try {
            carConnectIcon.clearColorFilter()
            carConnectIcon.setImageDrawable(packageManager.getApplicationIcon(component.packageName))
            val info = packageManager.getApplicationInfo(component.packageName, 0)
            carConnectTitle.text = packageManager.getApplicationLabel(info)
        } catch (_: PackageManager.NameNotFoundException) {
            carConnectIcon.setImageResource(R.drawable.ic_car)
            carConnectIcon.setColorFilter(lightBlue)
            carConnectTitle.text = "ZLINK / CPLINK"
        }
        carConnectHint.text = "Listo para conectar con el vehículo"
        carConnectButton.text = "CONECTAR"
    }

    private fun weatherEmoji(condition: String): String {
        val c = condition.lowercase(Locale.ROOT)
        return when {
            c.contains("tormenta") -> "⛈"
            c.contains("nieve") || c.contains("nevada") -> "❄"
            c.contains("lluvia") || c.contains("llovizna") || c.contains("chubasco") -> "🌧"
            c.contains("niebla") -> "🌫"
            c.contains("parcialmente") -> "⛅"
            c.contains("nublado") -> "☁"
            c.contains("mayormente despejado") -> "🌤"
            c.contains("despejado") -> "☀"
            else -> "⛅"
        }
    }

    private fun updateTripViews() {
        val gpsPlace = currentLocation?.let { "%.5f, %.5f".format(Locale.US, it.latitude, it.longitude) }
        locationText.text = placeName ?: gpsPlace ?: "Buscando ubicación GPS…"
        val weather = (weatherLabel ?: "--°C · Obteniendo clima…").split(" · ", limit = 2)
        val temperature = weather.firstOrNull() ?: "--°C"
        val condition = weather.getOrNull(1) ?: "Obteniendo clima…"
        weatherValue.text = temperature
        weatherConditionText.text = condition
        weatherIcon.text = weatherEmoji(condition)
        if (selectedTab == "VIAJE") renderTabContent("VIAJE")
    }

    /** Muestra enseguida la última posición conocida, sin esperar al primer fix del GPS. */
    private fun seedLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { p ->
                    try {
                        locationManager.getLastKnownLocation(p)
                    } catch (e: Exception) {
                        null
                    }
                }
                .maxByOrNull { it.time }
            if (last != null) tripInfoRepository.refresh(last)
        } catch (e: Exception) {
            // sin posición conocida
        }
    }

    // ---- Dock de apps ----

    private fun dockCell(name: String, icon: ImageView, action: () -> Unit): LinearLayout {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER
        cell.isClickable = true
        cell.setOnClickListener { action() }
        cell.addView(icon, LinearLayout.LayoutParams(px(72f), px(72f)))
        val t = label(name, 20f, Color.WHITE)
        t.gravity = Gravity.CENTER
        cell.addView(t, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(10f) })
        return cell
    }

    private fun buildDockCard() {
        val dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        dock.background = panelBg(24f, 0xE63A4550.toInt(), 0xE62A323C.toInt())

        recentDock = LinearLayout(this)
        recentDock.orientation = LinearLayout.HORIZONTAL
        recentDock.gravity = Gravity.CENTER
        dock.addView(recentDock, LinearLayout.LayoutParams(0, MP, DOCK_SLOTS.toFloat()))

        val appsIcon = iconView(R.drawable.ic_apps, 72f, 0xFF2F4A78.toInt(), 16f)
        dock.addView(dockCell("Apps", appsIcon) { toggleDrawer() }, LinearLayout.LayoutParams(0, MP, 1f))
        root.addView(dock, lp(VEHICLE_X, DOCK_Y, DOCK_W, DOCK_H))
        refreshRecentDock()
    }

    /**
     * El dock muestra las últimas apps abiertas desde el launcher y, si faltan, completa con
     * Spotify, YouTube, Mensajes y Android Auto (ZLINK). El último espacio siempre abre el cajón.
     */
    private fun refreshRecentDock() {
        if (!::recentDock.isInitialized) return
        recentDock.removeAllViews()
        val saved = prefs.getString("recent_packages", "")!!.split("|").filter { it.isNotBlank() }
        val launcherApps = appCatalog.loadLauncherApps().associateBy { it.component.packageName }
        val carIntegrationPkg = findCarIntegration()?.packageName
        val recent = saved.distinct().mapNotNull { launcherApps[it] }
        val defaults = mutableListOf<InstalledApp>()
        launcherApps["com.spotify.music"]?.let { defaults.add(it) }
        launcherApps["com.google.android.youtube"]?.let { defaults.add(it) }
        defaultSmsPackage()?.let { launcherApps[it] }?.let { defaults.add(it) }
        carIntegrationPkg?.let { launcherApps[it] }?.let { defaults.add(it) }
        val chosen = (recent.take(DOCK_SLOTS) + defaults)
            .distinctBy { it.component.packageName }
            .take(DOCK_SLOTS)
            .map { if (it.component.packageName == carIntegrationPkg) it.copy(label = "Android Auto") else it }
        if (chosen.isEmpty()) {
            val hint = label("Abre una app para fijarla aquí", 18f, textMuted)
            hint.gravity = Gravity.CENTER
            recentDock.addView(hint, LinearLayout.LayoutParams(MP, MP))
            return
        }
        for (slot in 0 until DOCK_SLOTS) {
            val app = chosen.getOrNull(slot)
            if (app == null) {
                // Espacio vacío: mantiene las columnas alineadas con el botón "Apps".
                recentDock.addView(View(this), LinearLayout.LayoutParams(0, MP, 1f))
                continue
            }
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.isClickable = true
            cell.setOnClickListener { launchComponent(app.component) }
            val icon = ImageView(this)
            icon.setImageDrawable(app.icon)
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            roundClip(icon, 0.22f)
            cell.addView(icon, LinearLayout.LayoutParams(px(72f), px(72f)))
            val title = label(app.label, 20f, Color.WHITE)
            title.gravity = Gravity.CENTER
            cell.addView(title, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(10f) })
            recentDock.addView(cell, LinearLayout.LayoutParams(0, MP, 1f))
        }
    }

    private fun rememberRecentPackage(pkg: String) {
        if (pkg == packageName) return
        val current = prefs.getString("recent_packages", "")!!.split("|").filter { it.isNotBlank() }
        val updated = (listOf(pkg) + current.filterNot { it == pkg }).take(12)
        prefs.edit().putString("recent_packages", updated.joinToString("|")).apply()
        if (uiReady) refreshRecentDock()
    }

    private fun bindAppIcon(v: ImageView, pkg: String?, fallbackRes: Int, fallbackBg: Int) {
        val icon: Drawable? = if (pkg == null) {
            null
        } else {
            try {
                packageManager.getApplicationIcon(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
        if (icon != null) {
            v.clearColorFilter()
            v.setPadding(0, 0, 0, 0)
            v.background = null
            v.setImageDrawable(icon)
            roundClip(v, 0.22f)
        } else {
            v.clipToOutline = false
            v.setColorFilter(Color.WHITE)
            val p = px(16f)
            v.setPadding(p, p, p, p)
            v.background = rounded(fallbackBg, 76f * 0.26f)
            v.setImageResource(fallbackRes)
        }
    }

    private fun defaultSmsPackage(): String? = try {
        Telephony.Sms.getDefaultSmsPackage(this)
    } catch (e: Exception) {
        null
    }

    // ---- Reproductor ----

    private fun buildMediaCard() {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        mediaCardView = card
        card.gravity = Gravity.CENTER
        card.background = panelBg()
        val pad = px(22f)
        card.setPadding(pad, pad, pad, pad)
        card.setOnClickListener {
            if (!isListenerEnabled()) {
                if (!startSafe(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
                    toast("Ve a Ajustes > Notificaciones > Acceso a notificaciones y activa Civic Launcher")
                }
            } else {
                openMusic()
            }
        }

        artView = ImageView(this)
        roundClip(artView, 0.16f)
        card.addView(artView, LinearLayout.LayoutParams(px(ART_SIZE), px(ART_SIZE)))

        titleText = label("", 28f, Color.WHITE, true)
        titleText.gravity = Gravity.CENTER
        card.addView(titleText, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(18f) })

        artistText = label("", 22f, textMuted)
        artistText.gravity = Gravity.CENTER
        card.addView(artistText, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(4f) })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.max = 1000
        progressBar.progressTintList = ColorStateList.valueOf(blue)
        progressBar.progressBackgroundTintList = ColorStateList.valueOf(strokeColor)
        card.addView(progressBar, LinearLayout.LayoutParams(MP, px(8f)).apply { topMargin = px(18f) })

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER

        val prev = iconView(R.drawable.ic_prev, 64f, null, 14f)
        prev.setOnClickListener {
            controller?.transportControls?.skipToPrevious() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        controls.addView(prev, LinearLayout.LayoutParams(px(64f), px(64f)))

        playBtn = iconView(R.drawable.ic_play, 88f, null, 20f, blue)
        playBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x00000000)
            setStroke(max(2, px(3f)), blue)
        }
        playBtn.setOnClickListener { togglePlayback() }
        controls.addView(
            playBtn,
            LinearLayout.LayoutParams(px(88f), px(88f)).apply {
                leftMargin = px(26f)
                rightMargin = px(26f)
            }
        )

        val next = iconView(R.drawable.ic_next, 64f, null, 14f)
        next.setOnClickListener {
            controller?.transportControls?.skipToNext() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        controls.addView(next, LinearLayout.LayoutParams(px(64f), px(64f)))

        card.addView(controls, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(22f) })

        // Ocupa toda la columna derecha, a la altura de las tarjetas y del dock.
        root.addView(card, lp(MEDIA_X, CONTENT_Y, MEDIA_W, MEDIA_H))
        val edit = TextView(this).apply {
            text = "✎"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 24f * ss)
            setTextColor(Color.WHITE)
            background = rounded(0xAA45536B.toInt(), 20f)
            isClickable = true
            setOnClickListener {
                if (selectedTab == "HOME") showManager() else showManagerDialog()
            }
        }
        mediaEditButton = edit
        root.addView(edit, lp(MEDIA_X + MEDIA_W - 52f, CONTENT_Y + 12f, 40f, 40f))
    }

    /** Respaldo para apps de música Bluetooth que no publican una sesión de medios. */
    private fun sendMediaKey(code: Int) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (e: Exception) {
            // nada que hacer
        }
    }

    // ---- Barra inferior ----

    private fun barButton(res: Int, tint: Int, description: String, action: () -> Unit): ImageView {
        val b = iconView(res, 40f, null, 14f, tint)
        b.background = glow()
        b.isClickable = true
        b.contentDescription = description
        b.setOnClickListener { action() }
        return b
    }

    private fun buildBottomBar() {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        bar.background = panelBg(34f, 0xE60F1A24.toInt(), 0xE60A1219.toInt())

        val tint = lightBlue
        bottomBar = bar
        nightBtn = barButton(R.drawable.ic_moon, tint, "Modo nocturno") { toggleNight() }

        val buttons = listOf(
            barButton(R.drawable.ic_power, tint, "Apagar pantalla") { screenOff() },
            barButton(R.drawable.ic_mic, tint, "Asistente de voz") { openAssistant() },
            barButton(R.drawable.ic_settings, tint, "Ajustes") { openSettings() },
            barButton(R.drawable.ic_phone, tint, "Teléfono") { openPhone() },
            barButton(R.drawable.ic_pin, tint, "Mapas") { openMaps() },
            barButton(R.drawable.ic_apps, tint, "Todas las apps") { toggleDrawer() },
            nightBtn
        )
        for (b in buttons) bar.addView(b, LinearLayout.LayoutParams(0, MP, 1f))
        root.addView(bar, lp(20f, BAR_Y, 1240f, BAR_H))
    }

    private fun buildEditOverlay() {
        if (::editOverlay.isInitialized) root.removeView(editOverlay)
        editOverlay = FrameLayout(this).apply {
            setBackgroundColor(0xAA02060E.toInt())
            visibility = View.GONE
            isClickable = true
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = panelBg(22f)
            setPadding(px(16f), px(12f), px(16f), px(12f))
        }
        val folders = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf("GESTIONAR CARPETAS Y WIDGETS", "📁  VIAJE", "♫  MULTIMEDIA", "⚙  AJUSTES COCHE", "+  NUEVA PESTAÑA / WIDGET").forEachIndexed { index, text ->
            val item = TextView(this).apply {
                this.text = text
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (index == 1) lightBlue else Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, if (index == 0) 17f * ss else 20f * ss)
                setPadding(px(12f), 0, px(8f), 0)
                setOnClickListener { if (index == 4) addTabDialog() }
            }
            folders.addView(item, LinearLayout.LayoutParams(MP, 0, if (index == 0) 0.8f else 1f))
        }
        panel.addView(folders, LinearLayout.LayoutParams(0, MP, 0.38f))
        val options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        options.addView(label(selectedTab, 25f, Color.WHITE, true), LinearLayout.LayoutParams(MP, px(42f)))
        listOf("Ubicación actual y mapa", "Registro de viaje", "Zoom automático", "Tráfico", "Favoritos", "Duración", "Distancia", "Consumo", "Restricciones de velocidad").forEach { name ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label(name, 17f, Color.WHITE), LinearLayout.LayoutParams(0, MP, 1f))
                val toggle = android.widget.Switch(this@MainActivity).apply {
                    isChecked = name == "Ubicación actual y mapa" || name == "Duración" || name == "Distancia"
                }
                addView(toggle)
            }
            options.addView(row, LinearLayout.LayoutParams(MP, 0, 1f))
        }
        panel.addView(options, LinearLayout.LayoutParams(0, MP, 0.62f))
        editOverlay.addView(panel, lp(40f, CONTENT_Y, 820f, 500f))
        val close = TextView(this).apply {
            text = "LISTO   ×"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (selectedTab == "HOME") editOverlay.visibility = View.GONE else showTabContent(selectedTab)
            }
        }
        editOverlay.addView(close, lp(760f, 590f, 100f, 42f))
        root.addView(editOverlay, FrameLayout.LayoutParams(MP, MP))
    }

    private fun addTabDialog() {
        val input = android.widget.EditText(this).apply { hint = "Nombre de pestaña" }
        android.app.AlertDialog.Builder(this).setTitle("Nueva pestaña / widget").setView(input)
            .setPositiveButton("Añadir") { _, _ ->
                val name = input.text.toString().trim().uppercase(Locale.getDefault())
                if (name.isNotEmpty() && name !in tabs) tabs.add(tabs.lastIndex, name)
                uiReady = false
                buildUi()
            }.setNegativeButton("Cancelar", null).show()
    }


    // ---- Personalización de paneles ----

    private fun configureMovablePanels() {
        // Etiqueta las vistas superiores según su tarjeta para mover cada grupo como una unidad.
        for (i in 0 until root.childCount) {
            val view = root.getChildAt(i)
            val lp = view.layoutParams as? FrameLayout.LayoutParams ?: continue
            if (lp.width < 0 || lp.height < 0) continue
            val centerX = (lp.leftMargin + lp.width / 2f) / sx
            val centerY = (lp.topMargin + lp.height / 2f) / sy
            val region = panelRegions.firstOrNull {
                centerX >= it.left && centerX <= it.left + it.width &&
                    centerY >= it.top && centerY <= it.top + it.height
            } ?: continue
            view.tag = "movable-panel:${region.id}"
        }
        for (region in panelRegions) {
            val dx = xx(prefs.getFloat("panel.${region.id}.x", 0f))
            val dy = yy(prefs.getFloat("panel.${region.id}.y", 0f))
            if (dx == 0 && dy == 0) continue
            for (view in root.childrenForPanel(region.id)) {
                val lp = view.layoutParams as FrameLayout.LayoutParams
                lp.leftMargin += dx
                lp.topMargin += dy
                view.layoutParams = lp
            }
        }
    }

    private fun ViewGroup.childrenForPanel(id: String): List<View> =
        (0 until childCount).map { getChildAt(it) }.filter { it.tag == "movable-panel:$id" }

    private fun panelAt(x: Float, y: Float): PanelRegion? = panelRegions.firstOrNull { region ->
        if (prefs.getBoolean("panel.${region.id}.hidden", false)) return@firstOrNull false
        val left = region.left + prefs.getFloat("panel.${region.id}.x", 0f)
        val top = region.top + prefs.getFloat("panel.${region.id}.y", 0f)
        x >= left && x <= left + region.width && y >= top && y <= top + region.height
    }

    /** Solo se editan paneles en HOME y sin ninguna capa encima. */
    private fun panelEditingAllowed(): Boolean = uiReady && selectedTab == "HOME" &&
        drawer.visibility != View.VISIBLE && blackout.visibility != View.VISIBLE &&
        editOverlay.visibility != View.VISIBLE

    private fun panelTitle(id: String): String = when (id) {
        "vehicle" -> "Vehículo y velocímetro"
        "clock" -> "Hora, fecha, ubicación y clima"
        "connect" -> "Conexión del vehículo"
        "dock" -> "Accesos rápidos"
        "media" -> "Reproductor multimedia"
        "bottom" -> "Barra inferior"
        else -> "Panel"
    }

    private fun showPanelEditor(id: String) {
        if (panelRegions.none { it.id == id }) return
        android.app.AlertDialog.Builder(this)
            .setTitle(panelTitle(id))
            .setItems(arrayOf("Mover panel", "Ocultar panel", "Restaurar este panel")) { _, which ->
                when (which) {
                    0 -> {
                        moveModePanelId = id
                        toast("Toca y arrastra el panel para moverlo.")
                    }
                    1 -> {
                        prefs.edit().putBoolean("panel.$id.hidden", true).apply()
                        root.childrenForPanel(id).forEach { it.visibility = View.GONE }
                        toast("Panel oculto. Mantén pulsada un área vacía para recuperarlo.")
                    }
                    2 -> {
                        val oldDx = xx(prefs.getFloat("panel.$id.x", 0f))
                        val oldDy = yy(prefs.getFloat("panel.$id.y", 0f))
                        root.childrenForPanel(id).forEach { view ->
                            val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return@forEach
                            lp.leftMargin -= oldDx
                            lp.topMargin -= oldDy
                            view.layoutParams = lp
                            view.visibility = View.VISIBLE
                        }
                        prefs.edit()
                            .putBoolean("panel.$id.hidden", false)
                            .putFloat("panel.$id.x", 0f)
                            .putFloat("panel.$id.y", 0f)
                            .apply()
                        toast("Panel restaurado a su posición original.")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showHiddenPanelsDialog() {
        val hidden = panelRegions.filter { prefs.getBoolean("panel.${it.id}.hidden", false) }
        if (hidden.isEmpty()) {
            toast("Mantén pulsado un panel para editarlo.")
            return
        }
        val labels = hidden.map { panelTitle(it.id) }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Recuperar panel")
            .setItems(labels) { _, which ->
                val id = hidden[which].id
                prefs.edit().putBoolean("panel.$id.hidden", false).apply()
                root.childrenForPanel(id).forEach { it.visibility = View.VISIBLE }
                toast("Panel recuperado.")
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                panelLongPressTask?.let { handler.removeCallbacks(it) }
                panelDragging = false
                panelDragViews = emptyList()
                panelDownX = event.rawX
                panelDownY = event.rawY
                pendingPanelId = if (panelEditingAllowed()) panelAt(event.x / sx, event.y / sy)?.id else null
                val selectedMoveId = moveModePanelId
                if (selectedMoveId != null && pendingPanelId == selectedMoveId) {
                    panelDragging = true
                    panelStartOffsetX = prefs.getFloat("panel.$selectedMoveId.x", 0f)
                    panelStartOffsetY = prefs.getFloat("panel.$selectedMoveId.y", 0f)
                    panelDragViews = root.childrenForPanel(selectedMoveId).mapNotNull { view ->
                        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return@mapNotNull null
                        Triple(view, lp.leftMargin, lp.topMargin)
                    }
                    moveModePanelId = null
                }
                val id = pendingPanelId
                if (id != null) {
                    panelLongPressTask = Runnable {
                        if (pendingPanelId == id && uiReady && !panelDragging) showPanelEditor(id)
                    }
                    handler.postDelayed(panelLongPressTask!!, ViewConfiguration.getLongPressTimeout().toLong())
                } else if (panelEditingAllowed()) {
                    panelLongPressTask = Runnable {
                        if (pendingPanelId == null && uiReady) showHiddenPanelsDialog()
                    }
                    handler.postDelayed(panelLongPressTask!!, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (panelDragging) {
                    val dx = (event.rawX - panelDownX).roundToInt()
                    val dy = (event.rawY - panelDownY).roundToInt()
                    for ((view, left, top) in panelDragViews) {
                        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: continue
                        lp.leftMargin = left + dx
                        lp.topMargin = top + dy
                        view.layoutParams = lp
                    }
                    return true
                }
                if (pendingPanelId != null &&
                    (kotlin.math.abs(event.rawX - panelDownX) > ViewConfiguration.get(this).scaledTouchSlop ||
                     kotlin.math.abs(event.rawY - panelDownY) > ViewConfiguration.get(this).scaledTouchSlop)) {
                    panelLongPressTask?.let { handler.removeCallbacks(it) }
                    panelLongPressTask = null
                    pendingPanelId = null
                }
            }
            MotionEvent.ACTION_UP -> {
                panelLongPressTask?.let { handler.removeCallbacks(it) }
                panelLongPressTask = null
                if (panelDragging) {
                    val id = pendingPanelId
                    if (id != null) {
                        val dx = (event.rawX - panelDownX) / sx
                        val dy = (event.rawY - panelDownY) / sy
                        prefs.edit()
                            .putFloat("panel.$id.x", panelStartOffsetX + dx)
                            .putFloat("panel.$id.y", panelStartOffsetY + dy)
                            .apply()
                    }
                    panelDragging = false
                    pendingPanelId = null
                    panelDragViews = emptyList()
                    toast("Posición guardada")
                    return true
                }
                pendingPanelId = null
            }
            MotionEvent.ACTION_CANCEL -> {
                panelLongPressTask?.let { handler.removeCallbacks(it) }
                panelLongPressTask = null
                val wasDragging = panelDragging
                panelDragging = false
                pendingPanelId = null
                panelDragViews = emptyList()
                if (wasDragging) return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    // ---- Modo nocturno y pantalla negra ----

    private fun buildOverlays() {
        nightOverlay = View(this)
        nightOverlay.setBackgroundColor(0x8C000000.toInt())
        nightOverlay.visibility = View.GONE
        root.addView(nightOverlay, FrameLayout.LayoutParams(MP, MP))

        blackout = View(this)
        blackout.setBackgroundColor(Color.BLACK)
        blackout.isClickable = true
        blackout.visibility = View.GONE
        blackout.setOnClickListener { wakeScreen() }
        root.addView(blackout, FrameLayout.LayoutParams(MP, MP))
    }

    private fun toggleNight() {
        nightMode = !nightMode
        prefs.edit().putBoolean("night", nightMode).apply()
        applyNight()
    }

    private fun applyNight() {
        nightOverlay.visibility = if (nightMode) View.VISIBLE else View.GONE
        nightBtn.setColorFilter(if (nightMode) 0xFFFFC857.toInt() else lightBlue)
        applyBrightness()
    }

    private fun applyBrightness() {
        val attrs = window.attributes
        attrs.screenBrightness = when {
            blackout.visibility == View.VISIBLE -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
            nightMode -> 0.25f
            else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = attrs
    }

    /** "Apagar pantalla": pantalla negra con el brillo al mínimo. Toca la pantalla para despertarla. */
    private fun screenOff() {
        closeDrawer()
        blackout.visibility = View.VISIBLE
        restackOverlays()
        applyBrightness()
    }

    private fun wakeScreen() {
        blackout.visibility = View.GONE
        applyBrightness()
    }

    // ---- Cajón de aplicaciones ----

    private fun buildDrawer() {
        drawer = FrameLayout(this)
        drawer.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xF405090F.toInt(), 0xF40F1E2E.toInt(), 0xF4070D14.toInt())
        )
        drawer.visibility = View.GONE
        drawer.isClickable = true

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(
            iconView(R.drawable.ic_apps, 34f, null, 0f, lightBlue),
            LinearLayout.LayoutParams(px(34f), px(34f)).apply { rightMargin = px(14f) }
        )
        head.addView(label("Todas las apps", 30f, Color.WHITE, true))
        drawer.addView(head, lp(40f, 14f, 700f, 48f))

        val close = iconView(R.drawable.ic_close, 52f, 0x33FFFFFF, 12f, Color.WHITE)
        close.setOnClickListener { closeDrawer() }
        drawer.addView(close, lp(1196f, 12f, 52f, 52f))

        drawerGrid = GridView(this)
        drawerGrid.numColumns = 7
        drawerGrid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        drawerGrid.verticalSpacing = px(8f)
        drawerGrid.isVerticalScrollBarEnabled = false
        drawerGrid.overScrollMode = View.OVER_SCROLL_NEVER
        drawerGrid.setSelector(ColorDrawable(Color.TRANSPARENT))
        drawerGrid.setOnItemClickListener { _, _, position, _ ->
            val app = apps.getOrNull(position)
            if (app != null) {
                val i = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component)
                if (startSafe(i)) {
                    rememberRecentPackage(app.component.packageName)
                    closeDrawer()
                }
            }
        }
        drawer.addView(drawerGrid, lp(40f, 76f, 1200f, 546f))

        root.addView(drawer, FrameLayout.LayoutParams(MP, MP))
    }

    private inner class AppAdapter(private val list: List<InstalledApp>) : BaseAdapter() {
        override fun getCount(): Int = list.size
        override fun getItem(position: Int): Any = list[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val app = list[position]
            val cell: LinearLayout = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).also { c ->
                c.orientation = LinearLayout.VERTICAL
                c.gravity = Gravity.CENTER_HORIZONTAL
                c.setPadding(px(6f), px(10f), px(6f), px(10f))

                val icon = ImageView(this@MainActivity)
                icon.id = ICON_ID
                roundClip(icon, 0.22f)
                c.addView(icon, LinearLayout.LayoutParams(px(72f), px(72f)))

                val name = label("", 19f, Color.WHITE)
                name.id = NAME_ID
                name.gravity = Gravity.CENTER
                c.addView(name, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(8f) })
            }
            cell.findViewById<ImageView>(ICON_ID).setImageDrawable(app.icon)
            cell.findViewById<TextView>(NAME_ID).text = app.label
            return cell
        }
    }

    private fun openDrawer() {
        apps = appCatalog.loadLauncherApps().filter { it.component.packageName != packageName }
        drawerGrid.adapter = AppAdapter(apps)
        drawer.visibility = View.VISIBLE
        drawer.bringToFront()
        // La barra inferior sigue visible para volver a tocar "Todas las apps" y cerrar el cajón.
        bottomBar.bringToFront()
        restackOverlays()
    }

    private fun closeDrawer() {
        if (uiReady) drawer.visibility = View.GONE
    }

    private fun toggleDrawer() {
        if (drawer.visibility == View.VISIBLE) closeDrawer() else openDrawer()
    }

    // =====================================================================
    // Accesos
    // =====================================================================

    private fun startSafe(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    private fun launchPackage(pkg: String): Boolean {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        val launched = startSafe(i)
        if (launched) rememberRecentPackage(pkg)
        return launched
    }

    private fun findMusicBt(): ComponentName? = appCatalog.find(InstalledAppsCatalog.MUSIC_BT_EXACT, InstalledAppsCatalog.MUSIC_BT_CONTAINS)
    private fun findPhoneBt(): ComponentName? = appCatalog.find(InstalledAppsCatalog.PHONE_BT_EXACT)
    private fun findZlink(): ComponentName? = appCatalog.find(InstalledAppsCatalog.ZLINK_EXACT, InstalledAppsCatalog.ZLINK_CONTAINS, "zlink")

    /** Prioriza ZLINK5 y acepta otras versiones de ZLINK antes de probar CPLINK. */
    private fun findCarIntegration(): ComponentName? = findZlink() ?: appCatalog.find(
        exactNames = listOf("cplink", "cp link", "carplay link"),
        partialNames = listOf("cplink", "cp link"),
        packageNameContains = "cplink"
    )

    private fun launchComponent(c: ComponentName): Boolean {
        val launched = startSafe(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(c))
        if (launched) rememberRecentPackage(c.packageName)
        return launched
    }

    /** Música: primero "Music BT"; si no está instalada, Spotify y luego cualquier reproductor. */
    private fun openMusic() {
        findMusicBt()?.let { if (launchComponent(it)) return }
        if (launchPackage("com.spotify.music")) return
        val music = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        if (startSafe(music)) return
        openDrawer()
        toast("No encontré la app Music BT. Elige una de la lista.")
    }

    private fun openRadio() {
        val radio = appCatalog.find(
            listOf("radio", "fm radio", "radio fm", "car radio"),
            listOf("radio", "fm"),
            "radio"
        )
        if (radio != null && launchComponent(radio)) return
        openDrawer()
        toast("No encontré una app de radio instalada. Elige una de la lista.")
    }

    private fun openSpotify() {
        if (launchPackage("com.spotify.music")) return
        openDrawer()
        toast("Spotify no está instalado. Elige una app de la lista.")
    }

    private fun openYoutube() {
        if (launchPackage("com.google.android.youtube")) return
        openDrawer()
        toast("YouTube no está instalado. Elige una app de la lista.")
    }

    private fun openMessages() {
        val pkg = defaultSmsPackage()
        if (pkg != null && launchPackage(pkg)) return
        val sms = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
        if (startSafe(sms)) return
        openDrawer()
        toast("No encontré una app de mensajes. Elige una de la lista.")
    }

    /** Teléfono: abre la app "BT" de la unidad (la de llamadas por Bluetooth). */
    private fun openPhone() {
        findPhoneBt()?.let { if (launchComponent(it)) return }
        openDrawer()
        toast("No encontré la app BT. Elige una de la lista.")
    }

    /** Abre ZLINK (cualquier versión) o, si no existe, CPLINK. */
    private fun openAndroidAuto() {
        findCarIntegration()?.let { if (launchComponent(it)) return }
        openDrawer()
        toast("No encontré ZLINK ni CPLINK. Elige una app de conexión de la lista.")
    }

    /** Mapas: Google Maps y, si no está, cualquier app de mapas o navegación. */
    private fun openMaps() {
        if (launchPackage("com.google.android.apps.maps")) return
        val maps = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)
        if (startSafe(maps)) return
        openDrawer()
        toast("No encontré Google Maps. Elige una app de navegación de la lista.")
    }

    private fun openAssistant() {
        val tries = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND),
            Intent(Intent.ACTION_ASSIST),
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        )
        for (i in tries) {
            if (startSafe(i)) return
        }
        toast("No encontré un asistente de voz. Instala o activa Google Asistente.")
    }

    private fun openSettings() {
        if (!startSafe(Intent(Settings.ACTION_SETTINGS))) toast("No se pudo abrir Ajustes")
    }

    // =====================================================================
    // Reproductor de música (sesiones de medios)
    // =====================================================================

    private fun isListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabled?.contains(packageName) == true
    }

    private fun setupMedia() {
        if (!isListenerEnabled()) {
            setController(null)
            return
        }
        try {
            val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            sessionManager = mgr
            val comp = ComponentName(this, MediaListenerService::class.java)
            mgr.addOnActiveSessionsChangedListener(sessionsListener, comp)
            pickController(mgr.getActiveSessions(comp))
        } catch (e: SecurityException) {
            setController(null)
        }
    }

    private fun pickController(list: List<MediaController>?) {
        val btPkg = findMusicBt()?.packageName
        fun playing(c: MediaController) = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val pick = list?.firstOrNull { it.packageName == btPkg && playing(it) }
            ?: list?.firstOrNull { playing(it) }
            ?: list?.firstOrNull { it.packageName == btPkg }
            ?: list?.firstOrNull()
        setController(pick)
    }

    private fun setController(c: MediaController?) {
        controller?.unregisterCallback(controllerCallback)
        controller = c
        if (c == null) {
            if (uiReady) showIdle()
            return
        }
        c.registerCallback(controllerCallback)
        updateMeta(c.metadata)
        updatePlayState(c.playbackState)
    }

    private fun showIdle() {
        titleText.text = "Sin reproducción"
        artistText.text = when {
            !isListenerEnabled() -> "Toca para activar el acceso"
            findMusicBt() != null -> "Toca para abrir Music BT"
            else -> "Toca para abrir tu música"
        }
        durationMs = 0L
        lastState = null
        isPlaying = false
        playBtn.setImageResource(R.drawable.ic_play)
        setArt(null)
        updateProgress()
    }

    private fun updateMeta(md: MediaMetadata?) {
        if (!uiReady) return
        if (md == null) {
            showIdle()
            return
        }
        titleText.text = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Sin título"
        artistText.text = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: ""
        durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val bmp: Bitmap? = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        setArt(bmp)
        updateProgress()
    }

    private fun updatePlayState(st: PlaybackState?) {
        if (!uiReady) return
        lastState = st
        isPlaying = st?.state == PlaybackState.STATE_PLAYING
        playBtn.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        updateProgress()
    }

    private fun setArt(bmp: Bitmap?) {
        if (bmp != null) {
            artView.clearColorFilter()
            artView.setPadding(0, 0, 0, 0)
            artView.background = null
            artView.scaleType = ImageView.ScaleType.CENTER_CROP
            artView.setImageBitmap(bmp)
        } else {
            val p = px(56f)
            artView.setPadding(p, p, p, p)
            artView.background = rounded(0xFF12294D.toInt(), ART_SIZE * 0.16f)
            artView.scaleType = ImageView.ScaleType.FIT_CENTER
            artView.setColorFilter(textMuted)
            artView.setImageResource(R.drawable.ic_music)
        }
    }

    private fun updateProgress() {
        val st = lastState
        if (st == null || durationMs <= 0L) {
            progressBar.progress = 0
            return
        }
        var pos = st.position
        if (pos < 0L) pos = 0L
        if (st.state == PlaybackState.STATE_PLAYING) {
            val dt = SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
            pos += (dt * st.playbackSpeed).toLong()
        }
        pos = pos.coerceIn(0L, durationMs)
        progressBar.progress = ((pos * 1000L) / durationMs).toInt()
    }

    // =====================================================================
    // GPS
    // =====================================================================

    private fun startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener)
        } catch (e: Exception) {
            // Sin GPS disponible: la velocidad se queda en 0
        }
    }

    private fun stopGps() {
        try {
            locationManager.removeUpdates(locListener)
        } catch (e: Exception) {
            // nada que hacer
        }
    }

    private companion object {
        const val ICON_ID = 7001
        const val NAME_ID = 7002
        const val ART_SIZE = 250f
        const val LAYOUT_VERSION = 2
        const val TRIP_STOP_AFTER_STATIONARY_MS = 5 * 60 * 1000L
        const val SPEED_LIMIT_MIN_INTERVAL_MS = 60 * 1000L
        const val SPEED_LIMIT_MAX_INTERVAL_MS = 5 * 60 * 1000L
        const val SPEED_LIMIT_REFRESH_DISTANCE_M = 500f
    }
}
