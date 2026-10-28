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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
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
    private val textMuted = 0xFF8FA6C9.toInt()
    private val strokeColor = 0xFF1E3A66.toInt()

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
    private lateinit var dateText: TextView
    private lateinit var locationText: TextView
    private lateinit var weatherText: TextView
    private lateinit var artView: ImageView
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var playBtn: ImageView
    private lateinit var dockSpotify: ImageView
    private lateinit var dockYoutube: ImageView
    private lateinit var dockMessages: ImageView
    private lateinit var dockAuto: ImageView
    private lateinit var nightBtn: ImageView
    private lateinit var nightOverlay: View
    private lateinit var blackout: View
    private lateinit var drawer: FrameLayout
    private lateinit var drawerGrid: GridView
    private lateinit var recentDock: LinearLayout
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
    private var swipeDownX = 0f
    private var swipeDownY = 0f

    private val panelRegions = listOf(
        PanelRegion("vehicle", 20f, 20f, 500f, 440f),
        PanelRegion("clock", 540f, 20f, 340f, 170f),
        PanelRegion("trip", 540f, 210f, 340f, 250f),
        PanelRegion("dock", 20f, 480f, 860f, 140f),
        PanelRegion("media", 900f, 20f, 360f, 600f),
        PanelRegion("bottom", 20f, 636f, 1240f, 68f)
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
            speedText.text = shown.toString()
            ring.setSpeed(shown)
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
        nightMode = prefs.getBoolean("night", false)
        placeName = prefs.getString("place", null)
        weatherLabel = prefs.getString("weather", null)
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
            intArrayOf(0xFF02060E.toInt(), 0xFF0A1B3A.toInt(), 0xFF030A16.toInt())
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

    private fun panelBg(radius: Float = 28f): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xCC12294F.toInt(), 0xCC081528.toInt())
        )
        g.cornerRadius = radius * ss
        g.setStroke(max(1, px(1.5f)), strokeColor)
        return g
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
        buildClockCard()
        buildTripCard()
        buildDockCard()
        buildMediaCard()
        buildDrawer()
        buildBottomBar()
        configureMovablePanels()
        buildOverlays()

        uiReady = true
        showIdle()
        if (resumed) refreshDynamic()
    }

    private fun refreshDynamic() {
        refreshRecentDock()
        setupMedia()
        applyNight()
        updateTripViews()
        updateDate()
        seedLocation()
    }

    // ---- Tarjeta del vehículo (velocímetro) ----

    private fun buildVehicleCard() {
        card(20f, 20f, 500f, 440f)

        val title = LinearLayout(this)
        title.orientation = LinearLayout.HORIZONTAL
        title.gravity = Gravity.CENTER_VERTICAL
        title.addView(
            iconView(R.drawable.ic_car, 36f, null, 0f, lightBlue),
            LinearLayout.LayoutParams(px(36f), px(36f)).apply { rightMargin = px(12f) }
        )
        title.addView(label("Civic 2012", 30f, lightBlue, true))
        root.addView(title, lp(44f, 36f, 260f, 44f))

        // El velocímetro queda arriba y el carro debajo, sin que se toquen
        // (anillo: y 84..284; carro: y 294..450).
        ring = SpeedRingView(this, 12f * ss)
        root.addView(ring, lp(170f, 84f, 200f, 200f))

        speedText = label("0", 80f, Color.WHITE, true)
        speedText.gravity = Gravity.CENTER
        root.addView(speedText, lp(170f, 128f, 200f, 86f))

        val unit = label("km/h", 26f, textMuted)
        unit.gravity = Gravity.CENTER
        root.addView(unit, lp(170f, 214f, 200f, 30f))

        val car = ImageView(this)
        car.setImageResource(R.drawable.car)
        car.scaleType = ImageView.ScaleType.FIT_CENTER
        root.addView(car, lp(70f, 294f, 400f, 156f))
    }

    // ---- Reloj ----

    private fun buildClockCard() {
        card(540f, 20f, 340f, 170f)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.BOTTOM

        val time = TextClock(this)
        time.format12Hour = "h:mm"
        time.format24Hour = "HH:mm"
        styleClock(time, 88f, Color.WHITE, true)
        row.addView(time)

        val ampm = TextClock(this)
        ampm.format12Hour = "a"
        ampm.format24Hour = " "
        styleClock(ampm, 28f, textMuted, false)
        row.addView(
            ampm,
            LinearLayout.LayoutParams(WC, WC).apply {
                leftMargin = px(10f)
                bottomMargin = px(12f)
            }
        )
        root.addView(row, lp(564f, 30f, 300f, 100f))

        dateText = label("", 26f, textMuted)
        root.addView(dateText, lp(564f, 134f, 300f, 34f))
    }

    private fun updateDate() {
        val s = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("es")).format(Date())
            .replaceFirstChar { it.uppercase() }
        if (s != lastDate) {
            lastDate = s
            dateText.text = s
        }
    }

    // ---- Viaje: ubicación y clima actuales ----

    private fun infoBlock(name: String, value: TextView): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(name, 20f, textMuted))
        value.maxLines = 2
        col.addView(value, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(4f) })
        return col
    }

    private fun buildTripCard() {
        card(540f, 210f, 340f, 250f)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(
            iconView(R.drawable.ic_nav, 30f, null, 0f, blue),
            LinearLayout.LayoutParams(px(30f), px(30f)).apply { rightMargin = px(12f) }
        )
        head.addView(label("Viaje", 28f, Color.WHITE, true))
        root.addView(head, lp(564f, 228f, 290f, 38f))

        locationText = label("", 26f, Color.WHITE, true)
        weatherText = label("", 26f, Color.WHITE, true)

        val rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        rows.addView(
            infoBlock("Ubicación actual", locationText),
            LinearLayout.LayoutParams(MP, px(84f))
        )
        rows.addView(
            infoBlock("Clima actual", weatherText),
            LinearLayout.LayoutParams(MP, px(84f))
        )
        root.addView(rows, lp(564f, 280f, 292f, 170f))
    }

    private fun updateTripViews() {
        locationText.text = placeName ?: "Buscando ubicación…"
        weatherText.text = weatherLabel ?: "Obteniendo clima…"
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
        cell.addView(icon, LinearLayout.LayoutParams(px(76f), px(76f)))
        val t = label(name, 22f, Color.WHITE)
        t.gravity = Gravity.CENTER
        cell.addView(t, LinearLayout.LayoutParams(WC, WC).apply { topMargin = px(10f) })
        return cell
    }

    private fun buildDockCard() {
        val dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        dock.background = panelBg()

        recentDock = LinearLayout(this)
        recentDock.orientation = LinearLayout.HORIZONTAL
        recentDock.gravity = Gravity.CENTER
        dock.addView(recentDock, LinearLayout.LayoutParams(0, MP, 1f))

        val appsIcon = iconView(R.drawable.ic_apps, 76f, 0xFF2A3F66.toInt(), 18f)
        dock.addView(dockCell("Todas las apps", appsIcon) { toggleDrawer() },
            LinearLayout.LayoutParams(px(170f), MP))
        root.addView(dock, lp(20f, 480f, 860f, 140f))
        refreshRecentDock()
    }

    /** El dock recuerda apps abiertas desde el launcher y siempre reserva el último espacio al cajón. */
    private fun refreshRecentDock() {
        if (!::recentDock.isInitialized) return
        recentDock.removeAllViews()
        val saved = prefs.getString("recent_packages", "")!!.split("|").filter { it.isNotBlank() }
        val launcherApps = appCatalog.loadLauncherApps().associateBy { it.component.packageName }
        val recent = saved.distinct().mapNotNull { launcherApps[it] }.take(4)
        val fallback = listOf("com.spotify.music", "com.google.android.youtube")
            .filter { pkg -> launcherApps.containsKey(pkg) && recent.none { it.component.packageName == pkg } }
        val chosen = (recent + fallback.mapNotNull { launcherApps[it] }).take(4)
        if (chosen.isEmpty()) {
            val hint = label("Abre una app para fijarla aquí", 17f, textMuted)
            hint.gravity = Gravity.CENTER
            recentDock.addView(hint, LinearLayout.LayoutParams(MP, MP))
            return
        }
        chosen.forEach { app ->
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.isClickable = true
            cell.setOnClickListener { launchComponent(app.component) }
            val icon = ImageView(this)
            icon.setImageDrawable(app.icon)
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            cell.addView(icon, LinearLayout.LayoutParams(px(58f), px(58f)))
            val title = label(app.label, 16f, Color.WHITE)
            title.gravity = Gravity.CENTER
            cell.addView(title, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(5f) })
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
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.background = panelBg()
        val pad = px(24f)
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

        titleText = label("", 30f, Color.WHITE, true)
        titleText.gravity = Gravity.CENTER
        card.addView(titleText, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(22f) })

        artistText = label("", 22f, textMuted)
        artistText.gravity = Gravity.CENTER
        card.addView(artistText, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(8f) })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.max = 1000
        progressBar.progressTintList = ColorStateList.valueOf(blue)
        progressBar.progressBackgroundTintList = ColorStateList.valueOf(strokeColor)
        card.addView(progressBar, LinearLayout.LayoutParams(MP, px(8f)).apply { topMargin = px(24f) })

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER

        val prev = iconView(R.drawable.ic_prev, 56f, null, 6f)
        prev.setOnClickListener {
            controller?.transportControls?.skipToPrevious() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        controls.addView(prev, LinearLayout.LayoutParams(px(56f), px(56f)))

        playBtn = iconView(R.drawable.ic_play, 72f, null, 17f, blue)
        playBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x00000000)
            setStroke(max(2, px(3f)), blue)
        }
        playBtn.setOnClickListener {
            val c = controller
            if (c == null) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            } else if (isPlaying) {
                c.transportControls.pause()
            } else {
                c.transportControls.play()
            }
        }
        controls.addView(
            playBtn,
            LinearLayout.LayoutParams(px(72f), px(72f)).apply {
                leftMargin = px(28f)
                rightMargin = px(28f)
            }
        )

        val next = iconView(R.drawable.ic_next, 56f, null, 6f)
        next.setOnClickListener {
            controller?.transportControls?.skipToNext() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        controls.addView(next, LinearLayout.LayoutParams(px(56f), px(56f)))

        card.addView(controls, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(26f) })

        // Ocupa toda la columna derecha (antes compartía el espacio con Mantenimiento)
        root.addView(card, lp(900f, 20f, 360f, 600f))
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

    private fun barButton(res: Int, tint: Int, action: () -> Unit): ImageView {
        val b = iconView(res, 40f, null, 14f, tint)
        b.isClickable = true
        b.setOnClickListener { action() }
        return b
    }

    private fun buildBottomBar() {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        bar.background = panelBg(30f)

        val tint = lightBlue
        nightBtn = barButton(R.drawable.ic_moon, tint) { toggleNight() }

        val buttons = listOf(
            barButton(R.drawable.ic_power, tint) { screenOff() },
            barButton(R.drawable.ic_mic, tint) { openAssistant() },
            barButton(R.drawable.ic_settings, tint) { openSettings() },
            barButton(R.drawable.ic_phone, tint) { openPhone() },
            barButton(R.drawable.ic_pin, tint) { openNavigation() },
            nightBtn
        )
        for (b in buttons) bar.addView(b, LinearLayout.LayoutParams(0, MP, 1f))
        root.addView(bar, lp(20f, 636f, 1240f, 68f))
    }


    // ---- Personalización de paneles ----

    private fun configureMovablePanels() {
        // Etiqueta las vistas superiores según su tarjeta para mover cada grupo como una unidad.
        for (i in 0 until root.childCount) {
            val view = root.getChildAt(i)
            val lp = view.layoutParams as? FrameLayout.LayoutParams ?: continue
            if (lp.width < 0 || lp.height < 0 || lp.width >= uiW * 0.9f || lp.height >= uiH * 0.9f) continue
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
        for (region in panelRegions) {
            val hidden = prefs.getBoolean("panel.${region.id}.hidden", false)
            root.childrenForPanel(region.id).forEach { it.visibility = if (hidden) View.GONE else View.VISIBLE }
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

    private fun showPanelEditor(id: String) {
        if (panelRegions.none { it.id == id }) return
        val title = when (id) {
            "vehicle" -> "Vehículo y velocímetro"
            "clock" -> "Reloj y fecha"
            "trip" -> "Viaje y clima"
            "dock" -> "Accesos rápidos"
            "media" -> "Reproductor multimedia"
            "bottom" -> "Barra inferior"
            else -> "Panel"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
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
        val labels = hidden.map { region ->
            when (region.id) {
                "vehicle" -> "Vehículo y velocímetro"
                "clock" -> "Reloj y fecha"
                "trip" -> "Viaje y clima"
                "dock" -> "Accesos rápidos"
                "media" -> "Reproductor multimedia"
                "bottom" -> "Barra inferior"
                else -> region.id
            }
        }.toTypedArray()
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
                swipeDownX = event.rawX
                swipeDownY = event.rawY
                panelLongPressTask?.let { handler.removeCallbacks(it) }
                panelDragging = false
                panelDragViews = emptyList()
                panelDownX = event.rawX
                panelDownY = event.rawY
                pendingPanelId = if (uiReady && drawer.visibility != View.VISIBLE &&
                    blackout.visibility != View.VISIBLE) panelAt(event.x / sx, event.y / sy)?.id else null
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
                } else if (uiReady && drawer.visibility != View.VISIBLE && blackout.visibility != View.VISIBLE) {
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
                val dx = event.rawX - swipeDownX
                val dy = event.rawY - swipeDownY
                if (uiReady && drawer.visibility != View.VISIBLE &&
                    blackout.visibility != View.VISIBLE &&
                    kotlin.math.abs(dx) > px(110f) &&
                    kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.35f && dx < 0f) {
                    startSafe(Intent(this, GoogleMapActivity::class.java))
                    return true
                }
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
        applyBrightness()
    }

    private fun wakeScreen() {
        blackout.visibility = View.GONE
        applyBrightness()
    }

    // ---- Cajón de aplicaciones ----

    private fun buildDrawer() {
        drawer = FrameLayout(this)
        drawer.setBackgroundColor(0xF2030812.toInt())
        drawer.visibility = View.GONE
        drawer.isClickable = true

        drawer.addView(label("Aplicaciones", 32f, Color.WHITE, true), lp(60f, 30f, 600f, 44f))

        val close = iconView(R.drawable.ic_close, 56f, null, 12f, Color.WHITE)
        close.setOnClickListener { closeDrawer() }
        drawer.addView(close, lp(1180f, 20f, 64f, 64f))

        drawerGrid = GridView(this)
        drawerGrid.numColumns = 7
        drawerGrid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        drawerGrid.verticalSpacing = px(8f)
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
        drawer.addView(drawerGrid, lp(60f, 92f, 1160f, 530f))

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
                c.setPadding(0, px(10f), 0, px(10f))

                val icon = ImageView(this@MainActivity)
                icon.id = ICON_ID
                c.addView(icon, LinearLayout.LayoutParams(px(76f), px(76f)))

                val name = label("", 20f, Color.WHITE)
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
        apps = appCatalog.loadLauncherApps()
        drawerGrid.adapter = AppAdapter(apps)
        drawer.visibility = View.VISIBLE
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

    private fun openNavigation() {
        if (startSafe(Intent(this, GoogleMapActivity::class.java))) return
        if (launchPackage("com.google.android.apps.maps")) return
        if (startSafe(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")))) return
        openDrawer()
        toast("No encontré una app de mapas. Elige una de la lista.")
    }

    private fun findMusicBt(): ComponentName? = appCatalog.find(InstalledAppsCatalog.MUSIC_BT_EXACT, InstalledAppsCatalog.MUSIC_BT_CONTAINS)
    private fun findPhoneBt(): ComponentName? = appCatalog.find(InstalledAppsCatalog.PHONE_BT_EXACT)
    private fun findZlink(): ComponentName? = appCatalog.find(InstalledAppsCatalog.ZLINK_EXACT, InstalledAppsCatalog.ZLINK_CONTAINS, "zlink")

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

    /** Android Auto: abre ZLINK5. */
    private fun openAndroidAuto() {
        findZlink()?.let { if (launchComponent(it)) return }
        openDrawer()
        toast("No encontré la app ZLINK5. Elige una de la lista.")
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
        const val ART_SIZE = 220f
    }
}
