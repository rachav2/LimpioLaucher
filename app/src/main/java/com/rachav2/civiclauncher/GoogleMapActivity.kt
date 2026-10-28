package com.rachav2.civiclauncher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import java.util.Locale

class GoogleMapActivity : Activity(), OnMapReadyCallback {
    private var mapView: MapView? = null
    private var googleMap: GoogleMap? = null
    private lateinit var destinationInput: EditText
    private lateinit var root: FrameLayout
    private val locationRequestCode = 71

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUi()
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 12, 24)) }

        if (!hasMapsApiKey()) {
            setContentView(buildApiKeyNotice())
            return
        }

        val map = MapView(this)
        mapView = map
        map.onCreate(savedInstanceState)
        root.addView(map, FrameLayout.LayoutParams(-1, -1))
        root.addView(buildTopBar(), FrameLayout.LayoutParams(-1, dp(66), Gravity.TOP))
        root.addView(buildBottomControls(), FrameLayout.LayoutParams(-1, dp(104), Gravity.BOTTOM))
        setContentView(root)
        map.getMapAsync(this)
    }

    private fun hasMapsApiKey(): Boolean = try {
        applicationInfo.metaData?.getString("com.google.android.geo.API_KEY")
            ?.isNotBlank() == true
    } catch (_: Exception) {
        false
    }

    private fun buildApiKeyNotice(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(36), dp(24), dp(36), dp(24))
        }
        column.addView(TextView(this).apply {
            text = "GOOGLE MAPS"
            setTextColor(Color.rgb(108, 180, 255))
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        column.addView(TextView(this).apply {
            text = "Falta configurar la clave de Google Maps. Añade MAPS_API_KEY en los secretos del repositorio y vuelve a compilar. El launcher seguirá funcionando; mientras tanto puedes abrir Google Maps completo."
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(24))
        }, LinearLayout.LayoutParams(-1, -2))
        column.addView(actionButton("Abrir Google Maps") { openGoogleMaps(null) },
            LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
        column.addView(actionButton("Volver al launcher") { finish() },
            LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10) })
        return column
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(8))
            setBackgroundColor(0xE6081428.toInt())
        }
        val title = TextView(this).apply {
            text = "CIVIC 2012  ·  GOOGLE MAPS"
            setTextColor(Color.rgb(108, 180, 255))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        bar.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
        bar.addView(actionButton("Volver") { finish() }, LinearLayout.LayoutParams(dp(120), -1))
        return bar
    }

    private fun buildBottomControls(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(0xE6081428.toInt())
        }
        destinationInput = EditText(this).apply {
            hint = "Buscar destino o dirección"
            textSize = 17f
            singleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9DB0CB.toInt())
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedInput()
        }
        bar.addView(destinationInput, LinearLayout.LayoutParams(0, -1, 1f).apply {
            rightMargin = dp(12)
        })
        bar.addView(actionButton("Navegar") {
            val query = destinationInput.text.toString().trim()
            openGoogleMaps(query.ifBlank { null })
        }, LinearLayout.LayoutParams(dp(160), -1))
        return bar
    }

    private fun actionButton(textValue: String, action: () -> Unit): Button =
        Button(this).apply {
            text = textValue
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF17457A.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFF3B83D6.toInt())
            }
            setOnClickListener { action() }
        }

    private fun roundedInput(): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF101F35.toInt())
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), 0xFF31547D.toInt())
        }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
        map.isTrafficEnabled = true
        map.setPadding(0, dp(72), 0, dp(112))
        try {
            map.setMapStyle(MapStyleOptions(
                """[
                  {"elementType":"geometry","stylers":[{"color":"#18263b"}]},
                  {"elementType":"labels.text.fill","stylers":[{"color":"#9bb0cc"}]},
                  {"elementType":"labels.text.stroke","stylers":[{"color":"#101b2d"}]},
                  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#344b68"}]},
                  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#2f8cff"}]},
                  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#09172b"}]}
                ]""".trimIndent()
            ))
        } catch (_: Exception) { }

        val initial = lastKnownPosition() ?: LatLng(13.6929, -89.2182)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(initial, if (lastKnownPosition() == null) 11f else 15f))
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try { map.isMyLocationEnabled = true } catch (_: SecurityException) { }
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationRequestCode)
        }
    }

    private fun lastKnownPosition(): LatLng? {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        return listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> try { manager.getLastKnownLocation(provider) } catch (_: Exception) { null } }
            .maxByOrNull { it.time }
            ?.let { LatLng(it.latitude, it.longitude) }
    }

    private fun openGoogleMaps(query: String?) {
        val uri = if (query.isNullOrBlank()) Uri.parse("geo:0,0") else
            Uri.parse("google.navigation:q=${Uri.encode(query)}&mode=d")
        val mapsIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        try {
            startActivity(mapsIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, if (query.isNullOrBlank()) Uri.parse("geo:0,0") else Uri.parse("geo:0,0?q=${Uri.encode(query)}")))
            } catch (_: Exception) {
                Toast.makeText(this, "No encontré una aplicación de mapas instalada.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onStop() { mapView?.onStop(); super.onStop() }
    override fun onDestroy() { mapView?.onDestroy(); mapView = null; super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    @Deprecated("Use the Activity Result API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            try { googleMap?.isMyLocationEnabled = true } catch (_: SecurityException) { }
            lastKnownPosition()?.let { googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f)) }
        }
    }
}
