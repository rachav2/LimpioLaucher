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
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Launcher para pantallas Android de auto, con el diseño "Honda Civic 2012".
 *
 * El diseño base es de 1280x720 y todo se escala según la resolución real de la pantalla.
 * La interfaz se construye por código para no depender de la densidad (dpi) de cada unidad.
 */
class MainActivity : Activity() {

    // ---- Paleta ----
    private val blue = 0xFF2F8CFF.toInt()
    private val textMuted = 0xFF8FA6C9.toInt()
    private val panelColor = 0xCC0B1830.toInt()
    private val strokeColor = 0xFF1E3A66.toInt()

    // ---- Escala de pantalla ----
    private var sx = 1f
    private var sy = 1f
    private var ss = 1f

    private lateinit var root: FrameLayout
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    // ---- Vistas ----
    private lateinit var speedText: TextView
    private lateinit var oilText: TextView
    private lateinit var serviceText: TextView
    private lateinit var artView: ImageView
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var timeCur: TextView
    private lateinit var timeTot: TextView
    private lateinit var playBtn: ImageView
    private lateinit var sideSpotify: ImageView
    private lateinit var sideMaps: ImageView
    private lateinit var drawer: FrameLayout
    private lateinit var drawerGrid: GridView

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
            updateProgress()
            handler.postDelayed(this, 1000L)
        }
    }

    // ---- GPS (velocidad) ----
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    private val locListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            speedText.text = (if (kmh < 2f) 0 else kmh.roundToInt()).toString()
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

        val dm = resources.displayMetrics
        val w = max(dm.widthPixels, dm.heightPixels).toFloat()
        val h = min(dm.widthPixels, dm.heightPixels).toFloat()
        sx = w / 1280f
        sy = h / 720f
        ss = min(sx, sy)

        root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF02060E.toInt(), 0xFF0A1B3A.toInt(), 0xFF030A16.toInt())
        )
        setContentView(root)

        buildBackground()
        buildTitle()
        buildSpeed()
        buildDock()
        buildRightPanel()
        buildDrawer()
        buildSidebar() // al final para que quede por encima del cajón de apps

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        refreshShortcutIcons()
        setupMedia()
        startGps()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeDrawer() // al pulsar Home se vuelve a la pantalla principal
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Un launcher no debe cerrarse; solo cierra el cajón de apps si está abierto.
        if (drawer.visibility == View.VISIBLE) closeDrawer()
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

    private fun buildBackground() {
        val glow = View(this)
        glow.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = xx(380f).toFloat()
            colors = intArrayOf(0x662F8CFF, 0x00000000)
            setGradientCenter(0.5f, 0.5f)
        }
        root.addView(glow, lp(240f, 110f, 700f, 440f))

        val car = ImageView(this)
        car.setImageResource(R.drawable.car)
        car.scaleType = ImageView.ScaleType.FIT_CENTER
        root.addView(car, lp(290f, 120f, 620f, 316f))
    }

    private fun buildTitle() {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.addView(
            iconView(R.drawable.ic_car, 34f, null, 0f, textMuted),
            LinearLayout.LayoutParams(px(34f), px(34f)).apply { rightMargin = px(12f) }
        )
        row.addView(label("Honda Civic 2012", 28f, Color.WHITE, true))
        root.addView(row, lp(330f, 24f, 560f, 48f))
    }

    private fun buildSpeed() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL

        speedText = label("0", 150f, Color.WHITE, true)
        speedText.gravity = Gravity.CENTER
        box.addView(
            speedText,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(160f))
        )
        val unit = label("km/h", 32f, textMuted)
        unit.gravity = Gravity.CENTER
        box.addView(unit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(40f)))

        root.addView(box, lp(110f, 110f, 280f, 220f))
    }

    // ---- Barra lateral ----

    private fun sideItemLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(px(64f), px(64f)).apply {
            topMargin = px(14f)
            bottomMargin = px(14f)
        }

    private fun buildSidebar() {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.gravity = Gravity.CENTER
        bar.background = rounded(panelColor, 30f, strokeColor)

        val home = iconView(R.drawable.ic_home, 64f, blue, 14f)
        home.setOnClickListener { closeDrawer() }
        bar.addView(home, sideItemLp())

        sideSpotify = iconView(R.drawable.ic_music, 64f, 0xFF1DB954.toInt(), 14f)
        sideSpotify.setOnClickListener { openMusic() }
        bar.addView(sideSpotify, sideItemLp())

        sideMaps = iconView(R.drawable.ic_pin, 64f, 0xFF34A853.toInt(), 14f)
        sideMaps.setOnClickListener { openNavigation() }
        bar.addView(sideMaps, sideItemLp())

        val phone = iconView(R.drawable.ic_phone, 64f, 0xFF22C55E.toInt(), 14f)
        phone.setOnClickListener { openPhone() }
        bar.addView(phone, sideItemLp())

        val grid = iconView(R.drawable.ic_apps, 64f, null, 10f)
        grid.setOnClickListener { toggleDrawer() }
        bar.addView(grid, sideItemLp())

        root.addView(bar, lp(16f, 16f, 92f, 688f))
    }

    /** Si Spotify / Google Maps están instalados, se usa su icono real; si no, un icono genérico. */
    private fun refreshShortcutIcons() {
        bindAppShortcut(sideSpotify, "com.spotify.music", R.drawable.ic_music, 0xFF1DB954.toInt())
        bindAppShortcut(sideMaps, "com.google.android.apps.maps", R.drawable.ic_pin, 0xFF34A853.toInt())
    }

    private fun bindAppShortcut(v: ImageView, pkg: String, fallbackRes: Int, fallbackBg: Int) {
        val icon: Drawable? = try {
            packageManager.getApplicationIcon(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            null
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
            val p = px(14f)
            v.setPadding(p, p, p, p)
            v.background = rounded(fallbackBg, 64f * 0.26f)
            v.setImageResource(fallbackRes)
        }
    }

    // ---- Dock inferior ----

    private class DockItem(val label: String, val icon: Int, val bg: Int, val action: () -> Unit)

    private fun buildDock() {
        val dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        dock.background = rounded(panelColor, 30f, strokeColor)

        val items = listOf(
            DockItem("Navegación", R.drawable.ic_nav, blue) { openNavigation() },
            DockItem("Música", R.drawable.ic_music, 0xFFFF3B5C.toInt()) { openMusic() },
            DockItem("Teléfono", R.drawable.ic_phone, 0xFF22C55E.toInt()) { openPhone() },
            DockItem("Android Auto", R.drawable.ic_car, 0xFF3A7BD5.toInt()) { openAndroidAuto() },
            DockItem("Ajustes", R.drawable.ic_settings, 0xFF5B6678.toInt()) { openSettings() }
        )

        for (item in items) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.isClickable = true
            cell.setOnClickListener { item.action() }
            cell.addView(
                iconView(item.icon, 54f, item.bg, 12f),
                LinearLayout.LayoutParams(px(54f), px(54f))
            )
            val name = label(item.label, 19f, Color.WHITE)
            name.gravity = Gravity.CENTER
            cell.addView(
                name,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = px(8f) }
            )
            dock.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }

        root.addView(dock, lp(150f, 590f, 740f, 110f))
    }

    // ---- Panel derecho ----

    private fun buildRightPanel() {
        buildClock()
        buildMaintenanceCard()
        buildMediaCard()
    }

    private fun buildClock() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.END

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END or Gravity.BOTTOM

        val time = TextClock(this)
        time.format12Hour = "h:mm"
        time.format24Hour = "HH:mm"
        styleClock(time, 78f, Color.WHITE, true)
        row.addView(time)

        val ampm = TextClock(this)
        ampm.format12Hour = "a"
        ampm.format24Hour = " "
        styleClock(ampm, 28f, textMuted, false)
        row.addView(
            ampm,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = px(8f)
                bottomMargin = px(10f)
            }
        )
        box.addView(row)

        val date = TextClock(this)
        date.format12Hour = "EEE, d MMM"
        date.format24Hour = "EEE, d MMM"
        styleClock(date, 26f, textMuted, false)
        box.addView(
            date,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(6f) }
        )

        root.addView(box, lp(930f, 22f, 334f, 120f))
    }

    private fun infoRow(icon: Int, title: String, subtitle: String?, value: TextView): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL

        row.addView(
            iconView(icon, 34f, null, 0f, blue),
            LinearLayout.LayoutParams(px(34f), px(34f)).apply {
                rightMargin = px(16f)
                topMargin = px(2f)
            }
        )

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(title, 22f, blue, true))
        if (subtitle != null) {
            col.addView(
                label(subtitle, 20f, textMuted),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = px(4f) }
            )
        }
        col.addView(
            value,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(4f) }
        )
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun buildMaintenanceCard() {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.background = rounded(panelColor, 26f, strokeColor)
        val pad = px(22f)
        card.setPadding(pad, pad, pad, pad)

        oilText = label(prefs.getString("oil", "En 7,500 km · 2 mes") ?: "", 26f, Color.WHITE, true)
        serviceText = label(prefs.getString("service", "En 12,500 km") ?: "", 26f, Color.WHITE, true)

        card.addView(infoRow(R.drawable.ic_wrench, "Mantenimiento", "Aceite motor", oilText))

        val divider = View(this)
        divider.setBackgroundColor(strokeColor)
        card.addView(
            divider,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, max(1, px(1.5f))).apply {
                topMargin = px(14f)
                bottomMargin = px(14f)
            }
        )

        card.addView(infoRow(R.drawable.ic_calendar, "Próximo servicio", null, serviceText))

        // Mantén pulsada la tarjeta para editar los valores
        card.setOnLongClickListener {
            showEditDialog()
            true
        }

        root.addView(card, lp(930f, 156f, 334f, 232f))
    }

    private fun showEditDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(px(24f), px(12f), px(24f), 0)

        val oil = EditText(this)
        oil.hint = "Aceite motor (ej. En 7,500 km · 2 mes)"
        oil.setText(oilText.text)
        oil.setSingleLine()
        box.addView(oil)

        val svc = EditText(this)
        svc.hint = "Próximo servicio (ej. En 12,500 km)"
        svc.setText(serviceText.text)
        svc.setSingleLine()
        box.addView(svc)

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Mantenimiento")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val o = oil.text.toString()
                val s = svc.text.toString()
                prefs.edit().putString("oil", o).putString("service", s).apply()
                oilText.text = o
                serviceText.text = s
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun buildMediaCard() {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(panelColor, 26f, strokeColor)
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

        // Carátula + textos
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        artView = ImageView(this)
        roundClip(artView, 0.16f)
        top.addView(artView, LinearLayout.LayoutParams(px(96f), px(96f)))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        titleText = label("", 26f, Color.WHITE, true)
        artistText = label("", 21f, textMuted)
        texts.addView(titleText)
        texts.addView(
            artistText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(6f) }
        )
        top.addView(
            texts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = px(16f)
            }
        )
        card.addView(top)

        // Barra de progreso
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.max = 1000
        progressBar.progressTintList = ColorStateList.valueOf(blue)
        progressBar.progressBackgroundTintList = ColorStateList.valueOf(strokeColor)
        card.addView(
            progressBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(8f)).apply {
                topMargin = px(18f)
            }
        )

        // Tiempos
        val times = LinearLayout(this)
        times.orientation = LinearLayout.HORIZONTAL
        timeCur = label("0:00", 18f, textMuted)
        timeTot = label("0:00", 18f, textMuted)
        timeTot.gravity = Gravity.END
        times.addView(timeCur, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(timeTot, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(
            times,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(6f) }
        )

        // Controles
        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER

        val prev = iconView(R.drawable.ic_prev, 48f, null, 4f)
        prev.setOnClickListener { controller?.transportControls?.skipToPrevious() }
        controls.addView(prev, LinearLayout.LayoutParams(px(52f), px(52f)))

        playBtn = iconView(R.drawable.ic_play, 68f, Color.WHITE, 16f, 0xFF0B1830.toInt())
        playBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
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
                leftMargin = px(28f)
                rightMargin = px(28f)
            }
        )

        val next = iconView(R.drawable.ic_next, 48f, null, 4f)
        next.setOnClickListener { controller?.transportControls?.skipToNext() }
        controls.addView(next, LinearLayout.LayoutParams(px(52f), px(52f)))

        card.addView(
            controls,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(10f) }
        )

        root.addView(card, lp(930f, 404f, 334f, 284f))
        showIdle()
    }

    // ---- Cajón de aplicaciones ----

    private fun buildDrawer() {
        drawer = FrameLayout(this)
        drawer.setBackgroundColor(0xF0030812.toInt())
        drawer.visibility = View.GONE
        drawer.isClickable = true

        val title = label("Aplicaciones", 32f, Color.WHITE, true)
        drawer.addView(title, lp(130f, 36f, 600f, 44f))

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
        drawer.addView(drawerGrid, lp(130f, 100f, 1120f, 600f))

        root.addView(
            drawer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
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
                c.addView(
                    name,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = px(8f) }
                )
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
        if (::drawer.isInitialized) drawer.visibility = View.GONE
    }

    private fun toggleDrawer() {
        if (drawer.visibility == View.VISIBLE) closeDrawer() else openDrawer()
    }

    // =====================================================================
    // Accesos (navegación, música, teléfono...)
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
            showIdle()
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
            val p = px(26f)
            artView.setPadding(p, p, p, p)
            artView.background = rounded(0xFF12294D.toInt(), 96f * 0.16f)
            artView.scaleType = ImageView.ScaleType.FIT_CENTER
            artView.setColorFilter(textMuted)
            artView.setImageResource(R.drawable.ic_music)
        }
    }

    private fun updateProgress() {
        val st = lastState
        if (st == null || durationMs <= 0L) {
            progressBar.progress = 0
            timeCur.text = fmt(0L)
            timeTot.text = fmt(max(durationMs, 0L))
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
        timeCur.text = fmt(pos)
        timeTot.text = fmt(durationMs)
    }

    private fun fmt(ms: Long): String {
        val total = ms / 1000L
        return String.format("%d:%02d", total / 60L, total % 60L)
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
    }
}
