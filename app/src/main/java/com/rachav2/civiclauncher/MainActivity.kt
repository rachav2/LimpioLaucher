package com.rachav2.civiclauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
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
    private val handler = Handler(Looper.getMainLooper())

    // ---- Vistas ----
    private lateinit var ring: SpeedRingView
    private lateinit var speedText: TextView
    private lateinit var dateText: TextView
    private lateinit var tripDistText: TextView
    private lateinit var tripMaxText: TextView
    private lateinit var headingText: TextView
    private lateinit var oilKmText: TextView
    private lateinit var oilMonthsText: TextView
    private lateinit var brakesText: TextView
    private lateinit var serviceText: TextView
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

    // ---- Estado ----
    private var nightMode = false
    private var tripMeters = 0f
    private var maxKmh = 0
    private var lastLoc: Location? = null
    private var lastDate = ""

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

            val prev = lastLoc
            if (prev != null && kmh >= 2f && location.accuracy < 40f) {
                tripMeters += prev.distanceTo(location)
            }
            lastLoc = location
            if (shown > maxKmh) maxKmh = shown
            if (location.hasBearing() && kmh >= 5f) headingText.text = compass(location.bearing)
            updateTripViews()
        }
    }

    // ---- Apps instaladas ----
    private class AppEntry(val label: String, val component: ComponentName, val icon: Drawable)

    private var apps: List<AppEntry> = emptyList()

    // =====================================================================
    // Ciclo de vida
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("civic", Context.MODE_PRIVATE)
        nightMode = prefs.getBoolean("night", false)
        tripMeters = prefs.getFloat("trip_m", 0f)
        maxKmh = prefs.getInt("max_kmh", 0)

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
        prefs.edit().putFloat("trip_m", tripMeters).putInt("max_kmh", maxKmh).apply()
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            // nada que hacer
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
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
        buildMaintenanceCard()
        buildMediaCard()
        buildDrawer()
        buildBottomBar()
        buildOverlays()

        uiReady = true
        showIdle()
        if (resumed) refreshDynamic()
    }

    private fun refreshDynamic() {
        refreshShortcutIcons()
        setupMedia()
        applyNight()
        updateTripViews()
        updateDate()
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

        ring = SpeedRingView(this, 14f * ss)
        root.addView(ring, lp(150f, 74f, 240f, 240f))

        val car = ImageView(this)
        car.setImageResource(R.drawable.car)
        car.scaleType = ImageView.ScaleType.FIT_CENTER
        root.addView(car, lp(70f, 256f, 400f, 204f))

        speedText = label("0", 96f, Color.WHITE, true)
        speedText.gravity = Gravity.CENTER
        root.addView(speedText, lp(150f, 144f, 240f, 100f))

        val unit = label("km/h", 30f, textMuted)
        unit.gravity = Gravity.CENTER
        root.addView(unit, lp(150f, 246f, 240f, 34f))
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
        val s = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es")).format(Date())
            .replaceFirstChar { it.uppercase() }
        if (s != lastDate) {
            lastDate = s
            dateText.text = s
        }
    }

    // ---- Viaje (datos reales del GPS) ----

    private fun statRow(name: String, value: TextView): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(label(name, 22f, textMuted), LinearLayout.LayoutParams(0, WC, 1f))
        value.gravity = Gravity.END
        row.addView(value, LinearLayout.LayoutParams(WC, WC))
        return row
    }

    private fun buildTripCard() {
        val c = card(540f, 210f, 340f, 250f)
        c.setOnLongClickListener {
            tripMeters = 0f
            maxKmh = 0
            headingText.text = "—"
            lastLoc = null
            updateTripViews()
            toast("Viaje reiniciado")
            true
        }

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(
            iconView(R.drawable.ic_nav, 30f, null, 0f, blue),
            LinearLayout.LayoutParams(px(30f), px(30f)).apply { rightMargin = px(12f) }
        )
        head.addView(label("Viaje", 28f, Color.WHITE, true))
        root.addView(head, lp(564f, 228f, 290f, 38f))

        tripDistText = label("0.0 km", 30f, Color.WHITE, true)
        tripMaxText = label("0 km/h", 30f, Color.WHITE, true)
        headingText = label("—", 30f, Color.WHITE, true)

        val rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        rows.addView(statRow("Distancia", tripDistText), LinearLayout.LayoutParams(MP, px(54f)))
        rows.addView(statRow("Vel. máxima", tripMaxText), LinearLayout.LayoutParams(MP, px(54f)))
        rows.addView(statRow("Rumbo", headingText), LinearLayout.LayoutParams(MP, px(54f)))
        root.addView(rows, lp(564f, 280f, 292f, 170f))
    }

    private fun updateTripViews() {
        tripDistText.text = String.format(Locale.US, "%.1f km", tripMeters / 1000f)
        tripMaxText.text = "$maxKmh km/h"
    }

    private fun compass(bearing: Float): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        val idx = (((bearing + 22.5f) / 45f).toInt()) % 8
        return names[idx]
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

        dockSpotify = iconView(R.drawable.ic_music, 76f, 0xFF1DB954.toInt(), 16f)
        dockYoutube = iconView(R.drawable.ic_play, 76f, 0xFFE02020.toInt(), 16f)
        dockMessages = iconView(R.drawable.ic_chat, 76f, 0xFF22C55E.toInt(), 16f)
        dockAuto = iconView(R.drawable.ic_car, 76f, 0xFF3A7BD5.toInt(), 16f)
        val appsIcon = iconView(R.drawable.ic_apps, 76f, 0xFF2A3F66.toInt(), 18f)

        val cells = listOf(
            dockCell("Spotify", dockSpotify) { openMusic() },
            dockCell("YouTube", dockYoutube) { openYoutube() },
            dockCell("Mensajes", dockMessages) { openMessages() },
            dockCell("Android Auto", dockAuto) { openAndroidAuto() },
            dockCell("Apps", appsIcon) { toggleDrawer() }
        )
        for (c in cells) dock.addView(c, LinearLayout.LayoutParams(0, MP, 1f))
        root.addView(dock, lp(20f, 480f, 860f, 140f))
    }

    /** Usa el icono real de la app si está instalada; si no, un icono genérico de color. */
    private fun refreshShortcutIcons() {
        bindAppIcon(dockSpotify, "com.spotify.music", R.drawable.ic_music, 0xFF1DB954.toInt())
        bindAppIcon(dockYoutube, "com.google.android.youtube", R.drawable.ic_play, 0xFFE02020.toInt())
        bindAppIcon(dockMessages, defaultSmsPackage(), R.drawable.ic_chat, 0xFF22C55E.toInt())
        bindAppIcon(dockAuto, "com.google.android.projection.gearhead", R.drawable.ic_car, 0xFF3A7BD5.toInt())
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

    // ---- Mantenimiento ----

    private fun maintRow(icon: Int, iconBg: Int, name: String, value: TextView, extra: TextView?): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.isClickable = true
        row.setOnClickListener { showEditDialog() }

        row.addView(
            iconView(icon, 52f, iconBg, 12f),
            LinearLayout.LayoutParams(px(52f), px(52f)).apply { rightMargin = px(16f) }
        )

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(name, 20f, textMuted))
        col.addView(value, LinearLayout.LayoutParams(WC, WC).apply { topMargin = px(4f) })
        row.addView(col, LinearLayout.LayoutParams(0, WC, 1f))

        if (extra != null) {
            row.addView(extra, LinearLayout.LayoutParams(WC, WC).apply { rightMargin = px(8f) })
        }
        row.addView(
            iconView(R.drawable.ic_chevron, 28f, null, 0f, textMuted),
            LinearLayout.LayoutParams(px(28f), px(28f))
        )
        return row
    }

    private fun divider(): View {
        val d = View(this)
        d.setBackgroundColor(strokeColor)
        return d
    }

    private fun buildMaintenanceCard() {
        card(900f, 20f, 360f, 300f)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(
            iconView(R.drawable.ic_wrench, 32f, null, 0f, Color.WHITE),
            LinearLayout.LayoutParams(px(32f), px(32f)).apply { rightMargin = px(14f) }
        )
        head.addView(label("Mantenimiento", 28f, Color.WHITE, true))
        root.addView(head, lp(924f, 36f, 320f, 38f))

        oilKmText = label(prefs.getString("oil_km", "7,500 km") ?: "", 26f, Color.WHITE, true)
        oilMonthsText = label(prefs.getString("oil_months", "2 meses") ?: "", 22f, textMuted)
        brakesText = label(prefs.getString("brakes", "15,000 km") ?: "", 26f, Color.WHITE, true)
        serviceText = label(prefs.getString("service", "12,500 km") ?: "", 26f, Color.WHITE, true)

        val rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        rows.addView(
            maintRow(R.drawable.ic_wrench, 0xFF22A06B.toInt(), "Aceite motor", oilKmText, oilMonthsText),
            LinearLayout.LayoutParams(MP, px(72f))
        )
        rows.addView(divider(), LinearLayout.LayoutParams(MP, max(1, px(1.5f))))
        rows.addView(
            maintRow(R.drawable.ic_settings, 0xFF5B6678.toInt(), "Revisión de frenos", brakesText, null),
            LinearLayout.LayoutParams(MP, px(72f))
        )
        rows.addView(divider(), LinearLayout.LayoutParams(MP, max(1, px(1.5f))))
        rows.addView(
            maintRow(R.drawable.ic_calendar, 0xFF3A7BD5.toInt(), "Próximo servicio", serviceText, null),
            LinearLayout.LayoutParams(MP, px(72f))
        )
        root.addView(rows, lp(922f, 84f, 316f, 224f))
    }

    private fun showEditDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(px(24f), px(12f), px(24f), 0)

        fun field(hint: String, current: CharSequence): EditText {
            val e = EditText(this)
            e.hint = hint
            e.setText(current)
            e.setSingleLine()
            box.addView(e)
            return e
        }

        val oilKm = field("Aceite motor (ej. 7,500 km)", oilKmText.text)
        val oilMonths = field("Aceite motor, tiempo (ej. 2 meses)", oilMonthsText.text)
        val brakes = field("Revisión de frenos (ej. 15,000 km)", brakesText.text)
        val service = field("Próximo servicio (ej. 12,500 km)", serviceText.text)

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Mantenimiento")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit()
                    .putString("oil_km", oilKm.text.toString())
                    .putString("oil_months", oilMonths.text.toString())
                    .putString("brakes", brakes.text.toString())
                    .putString("service", service.text.toString())
                    .apply()
                oilKmText.text = oilKm.text.toString()
                oilMonthsText.text = oilMonths.text.toString()
                brakesText.text = brakes.text.toString()
                serviceText.text = service.text.toString()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---- Reproductor ----

    private fun buildMediaCard() {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = panelBg()
        val pad = px(20f)
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

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        artView = ImageView(this)
        roundClip(artView, 0.16f)
        top.addView(artView, LinearLayout.LayoutParams(px(104f), px(104f)))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        titleText = label("", 28f, Color.WHITE, true)
        artistText = label("", 22f, textMuted)
        texts.addView(titleText)
        texts.addView(artistText, LinearLayout.LayoutParams(WC, WC).apply { topMargin = px(6f) })
        top.addView(
            texts,
            LinearLayout.LayoutParams(0, WC, 1f).apply { leftMargin = px(16f) }
        )
        card.addView(top)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.max = 1000
        progressBar.progressTintList = ColorStateList.valueOf(blue)
        progressBar.progressBackgroundTintList = ColorStateList.valueOf(strokeColor)
        card.addView(progressBar, LinearLayout.LayoutParams(MP, px(8f)).apply { topMargin = px(16f) })

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER

        val prev = iconView(R.drawable.ic_prev, 52f, null, 6f)
        prev.setOnClickListener { controller?.transportControls?.skipToPrevious() }
        controls.addView(prev, LinearLayout.LayoutParams(px(52f), px(52f)))

        playBtn = iconView(R.drawable.ic_play, 68f, null, 16f, blue)
        playBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x00000000)
            setStroke(max(2, px(3f)), blue)
        }
        playBtn.setOnClickListener {
            val c = controller
            if (c == null) {
                openMusic()
            } else if (isPlaying) {
                c.transportControls.pause()
            } else {
                c.transportControls.play()
            }
        }
        controls.addView(
            playBtn,
            LinearLayout.LayoutParams(px(68f), px(68f)).apply {
                leftMargin = px(30f)
                rightMargin = px(30f)
            }
        )

        val next = iconView(R.drawable.ic_next, 52f, null, 6f)
        next.setOnClickListener { controller?.transportControls?.skipToNext() }
        controls.addView(next, LinearLayout.LayoutParams(px(52f), px(52f)))

        card.addView(controls, LinearLayout.LayoutParams(MP, WC).apply { topMargin = px(12f) })

        root.addView(card, lp(900f, 340f, 360f, 280f))
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
                if (startSafe(i)) closeDrawer()
            }
        }
        drawer.addView(drawerGrid, lp(60f, 92f, 1160f, 530f))

        root.addView(drawer, FrameLayout.LayoutParams(MP, MP))
    }

    private fun loadApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map {
                AppEntry(
                    it.loadLabel(packageManager).toString(),
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private inner class AppAdapter(private val list: List<AppEntry>) : BaseAdapter() {
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
        apps = loadApps()
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
        return startSafe(i)
    }

    private fun openNavigation() {
        if (launchPackage("com.google.android.apps.maps")) return
        if (startSafe(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")))) return
        openDrawer()
        toast("No encontré una app de mapas. Elige una de la lista.")
    }

    private fun openMusic() {
        if (launchPackage("com.spotify.music")) return
        val music = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        if (startSafe(music)) return
        openDrawer()
        toast("No encontré un reproductor de música. Elige una de la lista.")
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

    private fun openPhone() {
        if (!startSafe(Intent(Intent.ACTION_DIAL))) {
            openDrawer()
            toast("No encontré la app de teléfono. Elige una de la lista.")
        }
    }

    private fun openAndroidAuto() {
        if (launchPackage("com.google.android.projection.gearhead")) return
        openDrawer()
        toast("Android Auto no está instalado. Elige tu app de conexión (CarPlay / Android Auto).")
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
        val pick = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
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
        artistText.text = if (isListenerEnabled()) "Toca para abrir tu música" else "Toca para activar el acceso"
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
            val p = px(28f)
            artView.setPadding(p, p, p, p)
            artView.background = rounded(0xFF12294D.toInt(), 104f * 0.16f)
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
        lastLoc = null
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
    }
}
