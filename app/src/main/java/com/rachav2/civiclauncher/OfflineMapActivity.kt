package com.rachav2.civiclauncher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlin.math.floor
import kotlin.math.pow

/**
 * Offline raster map viewer for an MBTiles file copied to:
 * Android/data/com.rachav2.civiclauncher/files/maps/el_salvador.mbtiles
 * The database is deliberately not bundled because a country-wide map can be large and must
 * be obtained from a source whose licence permits offline redistribution.
 */
class OfflineMapActivity : Activity() {
    private lateinit var map: OfflineTilesView
    private lateinit var status: TextView
    private val manager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            map.location = location
            status.text = String.format(java.util.Locale.US, "GPS %.5f, %.5f · sin datos móviles", location.latitude, location.longitude)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF07111F.toInt())
        map = OfflineTilesView(this)
        root.addView(map, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 12, 20, 12)
            setBackgroundColor(0xDD07111F.toInt())
            text = "Mapa offline · esperando GPS"
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        val close = TextView(this).apply {
            text = "✕  Volver"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(24, 18, 24, 18)
            setBackgroundColor(0xDD15345D.toInt())
            setOnClickListener { finish() }
        }
        root.addView(close, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
        setContentView(root)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                    map.location = it
                    status.text = String.format(java.util.Locale.US, "GPS %.5f, %.5f · sin datos móviles", it.latitude, it.longitude)
                }
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 2f, listener)
            } catch (_: Exception) { }
        } else {
            status.text = "Activa ubicación para centrar el mapa"
        }
        if (!map.hasOfflineDatabase()) {
            Toast.makeText(this, "Falta el mapa MBTiles de El Salvador. Consulta la ruta indicada en la pantalla.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { manager.removeUpdates(listener) } catch (_: Exception) { }
        map.close()
    }
}

private class OfflineTilesView(context: Context) : View(context) {
    var location: Location? = null
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mapFile = File(context.getExternalFilesDir(null), "maps/el_salvador.mbtiles")
    private var db: SQLiteDatabase? = null
    private val cache = LinkedHashMap<String, Bitmap>(48, 0.75f, true)
    private val zoom = 13

    init { setBackgroundColor(0xFF0B1726.toInt()) }

    fun hasOfflineDatabase(): Boolean = mapFile.isFile

    fun close() {
        try { db?.close() } catch (_: Exception) { }
        db = null
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val loc = location
        if (!mapFile.isFile) {
            paint.color = Color.WHITE
            paint.textSize = 23f
            canvas.drawText("MAPA OFFLINE NO INSTALADO", 40f, height * 0.42f, paint)
            paint.textSize = 16f
            canvas.drawText("Copia el archivo MBTiles a:", 40f, height * 0.42f + 36f, paint)
            paint.textSize = 14f
            canvas.drawText(mapFile.absolutePath, 40f, height * 0.42f + 64f, paint)
            canvas.drawText("El GPS sigue funcionando; no se han descargado mapas automáticamente.", 40f, height * 0.42f + 94f, paint)
            loc?.let { drawMarker(canvas, width / 2f, height / 2f) }
            return
        }
        if (loc == null) {
            paint.color = Color.WHITE
            paint.textSize = 22f
            canvas.drawText("Buscando señal GPS…", 40f, height / 2f, paint)
            return
        }
        val z = zoom
        val n = 2.0.pow(z).toInt()
        val latRad = Math.toRadians(loc.latitude.coerceIn(-85.0, 85.0))
        val centerX = (loc.longitude + 180.0) / 360.0 * n
        val centerY = (1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0 * n
        val tileSize = 256f
        val originX = width / 2f - (centerX - floor(centerX)).toFloat() * tileSize
        val originY = height / 2f - (centerY - floor(centerY)).toFloat() * tileSize
        val cx = floor(centerX).toInt()
        val cy = floor(centerY).toInt()
        var drawn = 0
        for (dy in -((height / 256) / 2 + 2)..((height / 256) / 2 + 2)) {
            for (dx in -((width / 256) / 2 + 2)..((width / 256) / 2 + 2)) {
                val x = cx + dx
                val y = cy + dy
                if (x !in 0 until n || y !in 0 until n) continue
                val bmp = tile(z, x, y) ?: continue
                val left = originX + dx * tileSize
                val top = originY + dy * tileSize
                canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + tileSize, top + tileSize), paint)
                drawn++
            }
        }
        if (drawn == 0) {
            paint.color = Color.WHITE
            paint.textSize = 19f
            canvas.drawText("No hay teselas para esta zona/zoom en el MBTiles.", 30f, height - 40f, paint)
        }
        drawMarker(canvas, width / 2f, height / 2f)
    }

    private fun tile(z: Int, x: Int, y: Int): Bitmap? {
        val key = "$z/$x/$y"
        cache[key]?.let { return it }
        try {
            if (db == null) db = SQLiteDatabase.openDatabase(mapFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val tmsY = (1 shl z) - 1 - y
            db?.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                arrayOf(z.toString(), x.toString(), tmsY.toString()))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val bmp = BitmapFactory.decodeByteArray(cursor.getBlob(0), 0, cursor.getBlob(0).size)
                    if (bmp != null) {
                        cache[key] = bmp
                        while (cache.size > 36) {
                            val first = cache.entries.iterator().next()
                            if (!first.value.isRecycled) first.value.recycle()
                            cache.remove(first.key)
                        }
                        return bmp
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun drawMarker(canvas: Canvas, x: Float, y: Float) {
        paint.color = 0xFF2F8CFF.toInt()
        canvas.drawCircle(x, y, 12f, paint)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(x, y, 17f, paint)
        paint.style = Paint.Style.FILL
    }
}
