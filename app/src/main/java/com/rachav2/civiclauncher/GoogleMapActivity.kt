package com.rachav2.civiclauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/** Segunda pantalla del launcher: mapa web integrado y selector de Google Maps/Waze. */
class GoogleMapActivity : Activity() {
    private val lightBlue = Color.rgb(108, 180, 255)
    private val muted = Color.rgb(143, 166, 201)
    private val panel = 0xCC12294F.toInt()
    private val backgroundColor = Color.rgb(3, 10, 22)
    private lateinit var root: LinearLayout
    private lateinit var mapFrame: FrameLayout
    private lateinit var mapWeb: WebView
    private lateinit var destinationInput: EditText
    private lateinit var googleButton: Button
    private lateinit var wazeButton: Button
    private lateinit var statusText: TextView
    private var provider = Provider.GOOGLE
    private var downX = 0f
    private var downY = 0f

    private enum class Provider { GOOGLE, WAZE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUi()
        buildDashboard()
        loadMap()
    }

    private fun buildDashboard() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(backgroundColor)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xCC081528.toInt(), dp(18))
            setPadding(dp(18), dp(8), dp(12), dp(8))
        }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(text("CIVIC 2012", 21f, lightBlue, true))
        titleColumn.addView(text("PANTALLA DE NAVEGACIÓN", 11f, muted, true))
        header.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("⌂  Inicio") { finish() }, LinearLayout.LayoutParams(dp(112), dp(42)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(62)))

        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        googleButton = actionButton("Google Maps") { chooseProvider(Provider.GOOGLE) }
        wazeButton = actionButton("Waze") { chooseProvider(Provider.WAZE) }
        selector.addView(googleButton, LinearLayout.LayoutParams(dp(150), dp(44)).apply { rightMargin = dp(8) })
        selector.addView(wazeButton, LinearLayout.LayoutParams(dp(110), dp(44)))
        statusText = text("Mapa integrado · vista web", 13f, muted)
        selector.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
        root.addView(selector, LinearLayout.LayoutParams(-1, dp(60)))

        mapFrame = FrameLayout(this).apply {
            background = rounded(panel, dp(20), 0xFF1E3A66.toInt())
            clipToOutline = true
        }
        mapWeb = WebView(this).apply {
            setBackgroundColor(0xFF101F35.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    statusText.text = if (provider == Provider.GOOGLE)
                        "Google Maps · integrado en el panel" else "Waze · mapa web integrado"
                }
                override fun onReceivedError(
                    view: WebView?, request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) statusText.text =
                        "No se pudo cargar el mapa; revisa la conexión"
                }
            }
            webChromeClient = WebChromeClient()
        }
        mapFrame.addView(mapWeb, FrameLayout.LayoutParams(-1, -1))
        root.addView(mapFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, 0)
        }
        destinationInput = EditText(this).apply {
            hint = "Buscar lugar o destino"
            textSize = 15f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(0xFF101F35.toInt(), dp(12), 0xFF31547D.toInt())
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> loadMap(); true }
        }
        controls.addView(destinationInput, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
        controls.addView(actionButton("Buscar") { loadMap() }, LinearLayout.LayoutParams(dp(100), dp(48)).apply { rightMargin = dp(8) })
        controls.addView(actionButton("Abrir app ↗") { openSelectedApp() }, LinearLayout.LayoutParams(dp(132), dp(48)))
        root.addView(controls, LinearLayout.LayoutParams(-1, dp(58)))

        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("DESLIZA A LA DERECHA O PULSA INICIO PARA VOLVER", 10f, muted, true))
        }, LinearLayout.LayoutParams(-1, dp(22)))
        setContentView(root)
        updateProviderButtons()
    }

    private fun chooseProvider(value: Provider) {
        provider = value
        updateProviderButtons()
        loadMap()
    }

    private fun updateProviderButtons() {
        googleButton.alpha = if (provider == Provider.GOOGLE) 1f else 0.68f
        wazeButton.alpha = if (provider == Provider.WAZE) 1f else 0.68f
        statusText.text = if (provider == Provider.GOOGLE) "Google Maps seleccionado" else "Waze seleccionado"
    }

    private fun loadMap() {
        if (!::mapWeb.isInitialized) return
        val query = destinationInput.text?.toString()?.trim().orEmpty()
        val encoded = Uri.encode(query.ifBlank { "San Salvador, El Salvador" })
        val url = if (provider == Provider.GOOGLE) {
            "https://maps.google.com/maps?q=" + encoded + "&output=embed"
        } else if (query.isBlank()) {
            "https://embed.waze.com/iframe?zoom=12&lat=13.6929&lon=-89.2182&pin=1"
        } else {
            "https://www.waze.com/live-map/directions?to=" + Uri.encode(query)
        }
        mapWeb.loadUrl(url)
    }

    private fun openSelectedApp() {
        val query = destinationInput.text?.toString()?.trim().orEmpty()
        val intent = if (provider == Provider.GOOGLE) {
            val uri = if (query.isBlank()) Uri.parse("geo:0,0") else
                Uri.parse("google.navigation:q=" + Uri.encode(query) + "&mode=d")
            Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        } else {
            val uri = if (query.isBlank()) Uri.parse("https://waze.com/ul") else
                Uri.parse("https://waze.com/ul?q=" + Uri.encode(query) + "&navigate=yes")
            Intent(Intent.ACTION_VIEW, uri).setPackage("com.waze")
        }
        try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                val label = if (provider == Provider.GOOGLE) "Google Maps" else "Waze"
                Toast.makeText(this, label + " no está instalado; el mapa web seguirá disponible.", Toast.LENGTH_LONG).show()
                try {
                    val fallback = if (provider == Provider.GOOGLE) Uri.parse("geo:0,0") else Uri.parse("https://waze.com/ul")
                    startActivity(Intent(Intent.ACTION_VIEW, fallback))
                } catch (_: Exception) { }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo abrir la aplicación de navegación.", Toast.LENGTH_LONG).show()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (dx > dp(110) && abs(dx) > abs(dy) * 1.35f) {
                    finish()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(0xFF17457A.toInt(), dp(12), 0xFF3B83D6.toInt())
            setOnClickListener { action() }
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            setTextColor(color)
            textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun rounded(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    override fun onResume() { super.onResume(); if (::mapWeb.isInitialized) mapWeb.onResume() }
    override fun onPause() { if (::mapWeb.isInitialized) mapWeb.onPause(); super.onPause() }
    override fun onDestroy() {
        if (::mapWeb.isInitialized) {
            mapFrame.removeView(mapWeb)
            mapWeb.stopLoading()
            mapWeb.destroy()
        }
        super.onDestroy()
    }
}
