package com.rachav2.civiclauncher

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

/** Performs reverse geocoding and weather requests away from the UI thread. */
internal class TripInfoRepository(
    context: Context,
    private val onPlaceUpdated: (String) -> Unit,
    private val onWeatherUpdated: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var closed = false
    private var placeBusy = false
    private var weatherBusy = false
    private var placeRetryPending = false
    private var weatherRetryPending = false
    private var lastPlaceLocation: Location? = null
    private var lastWeatherLocation: Location? = null
    private var placeRetryAt = 0L
    private var weatherRetryAt = 0L
    private var lastPlaceRequestAt = 0L
    private var lastWeatherRequestAt = 0L
    private var placeBackoffMs = INITIAL_RETRY_MS
    private var weatherBackoffMs = INITIAL_RETRY_MS

    fun refresh(location: Location) {
        val now = android.os.SystemClock.elapsedRealtime()
        var placeRequest = false
        var weatherRequest = false

        synchronized(lock) {
            if (closed) return
            val oldPlace = lastPlaceLocation
            if (!placeBusy && now >= placeRetryAt &&
                (oldPlace == null || location.distanceTo(oldPlace) >= PLACE_REFRESH_DISTANCE_M ||
                    placeRetryPending || now - lastPlaceRequestAt >= REFRESH_INTERVAL_MS)
            ) {
                placeBusy = true
                placeRetryPending = false
                lastPlaceLocation = Location(location)
                lastPlaceRequestAt = now
                placeRequest = true
            }

            val oldWeather = lastWeatherLocation
            if (!weatherBusy && now >= weatherRetryAt &&
                (oldWeather == null || location.distanceTo(oldWeather) >= WEATHER_REFRESH_DISTANCE_M ||
                    weatherRetryPending || now - lastWeatherRequestAt >= REFRESH_INTERVAL_MS)
            ) {
                weatherBusy = true
                weatherRetryPending = false
                lastWeatherLocation = Location(location)
                lastWeatherRequestAt = now
                weatherRequest = true
            }
        }

        if (placeRequest) executor.execute { fetchPlace(location) }
        if (weatherRequest) executor.execute { fetchWeather(location) }
    }

    /** Looks for a nearby OpenStreetMap road with an explicit maxspeed tag. */
    fun lookupSpeedLimit(location: Location, onResult: (Int?) -> Unit) {
        val point = Location(location)
        executor.execute {
            val limit = findNearbySpeedLimit(point)
            if (isOpen()) mainHandler.post { if (isOpen()) onResult(limit) }
        }
    }

    fun close() {
        synchronized(lock) { closed = true }
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun fetchPlace(location: Location) {
        val value = reverseGeocode(location.latitude, location.longitude)
        completePlace(value)
        if (value != null) mainHandler.post { if (isOpen()) onPlaceUpdated(value) }
    }

    private fun fetchWeather(location: Location) {
        val value = fetchWeatherLabel(location.latitude, location.longitude)
        completeWeather(value)
        if (value != null) mainHandler.post { if (isOpen()) onWeatherUpdated(value) }
    }

    private fun completePlace(value: String?) {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (closed) return
            placeBusy = false
            if (value != null) {
                placeBackoffMs = INITIAL_RETRY_MS
                placeRetryAt = now
            } else {
                placeRetryPending = true
                placeRetryAt = now + placeBackoffMs
                placeBackoffMs = (placeBackoffMs * 2).coerceAtMost(MAX_RETRY_MS)
            }
        }
    }

    private fun completeWeather(value: String?) {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (closed) return
            weatherBusy = false
            if (value != null) {
                weatherBackoffMs = INITIAL_RETRY_MS
                weatherRetryAt = now
            } else {
                weatherRetryPending = true
                weatherRetryAt = now + weatherBackoffMs
                weatherBackoffMs = (weatherBackoffMs * 2).coerceAtMost(MAX_RETRY_MS)
            }
        }
    }

    private fun isOpen(): Boolean = synchronized(lock) { !closed }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(latitude: Double, longitude: Double): String? {
        try {
            if (Geocoder.isPresent()) {
                val address = Geocoder(appContext, Locale("es"))
                    .getFromLocation(latitude, longitude, 1)?.firstOrNull()
                if (address != null) {
                    val city = address.locality ?: address.subLocality ?: address.subAdminArea
                    listOfNotNull(city, address.adminArea).distinct().joinToString(", ")
                        .takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        } catch (_: Exception) {
            // The online geocoder is the fallback when the device geocoder is unavailable.
        }

        val url = String.format(
            Locale.US,
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=14&addressdetails=1&accept-language=es&lat=%.5f&lon=%.5f",
            latitude,
            longitude
        )
        val body = httpGet(url) ?: return null
        return try {
            val address = JSONObject(body).getJSONObject("address")
            val placeKeys = listOf(
                "suburb", "neighbourhood", "city_district", "city", "town",
                "village", "municipality", "county"
            )
            val place = placeKeys.firstNotNullOfOrNull { address.optString(it).takeIf(String::isNotEmpty) }
            val state = address.optString("state").takeIf(String::isNotEmpty)
            listOfNotNull(place, state).distinct().joinToString(", ").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchWeatherLabel(latitude: Double, longitude: Double): String? {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
            latitude,
            longitude
        )
        val body = httpGet(url) ?: return null
        return try {
            val current = JSONObject(body).getJSONObject("current")
            val temperature = current.getDouble("temperature_2m").roundToInt()
            "$temperature°C · ${weatherDescription(current.getInt("weather_code"))}"
        } catch (_: Exception) {
            null
        }
    }

    private fun findNearbySpeedLimit(location: Location): Int? {
        val query = "[out:json][timeout:12];way(around:35,${location.latitude},${location.longitude})[\"highway\"][\"maxspeed\"];out tags geom;"
        val endpoint = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8")
        val body = httpGet(endpoint) ?: return null
        return try {
            val elements = JSONObject(body).optJSONArray("elements") ?: return null
            var closestDistance = Float.MAX_VALUE
            var closestLimit: Int? = null
            for (i in 0 until elements.length()) {
                val way = elements.optJSONObject(i) ?: continue
                val tags = way.optJSONObject("tags") ?: continue
                val rawLimit = tags.optString("maxspeed").lowercase(Locale.ROOT)
                val number = Regex("\\d+(?:\\.\\d+)?").find(rawLimit)?.value?.toFloatOrNull() ?: continue
                val limit = if (rawLimit.contains("mph")) (number * 1.60934f).roundToInt() else number.roundToInt()
                if (limit !in 10..180) continue
                val geometry = way.optJSONArray("geometry") ?: continue
                var roadDistance = Float.MAX_VALUE
                for (pointIndex in 0 until geometry.length()) {
                    val a = geometry.optJSONObject(pointIndex) ?: continue
                    val aLat = a.optDouble("lat", Double.NaN)
                    val aLon = a.optDouble("lon", Double.NaN)
                    if (!aLat.isFinite() || !aLon.isFinite()) continue
                    if (pointIndex == geometry.length() - 1) {
                        roadDistance = min(roadDistance, distanceMeters(location.latitude, location.longitude, aLat, aLon))
                        continue
                    }
                    val b = geometry.optJSONObject(pointIndex + 1) ?: continue
                    val bLat = b.optDouble("lat", Double.NaN)
                    val bLon = b.optDouble("lon", Double.NaN)
                    if (!bLat.isFinite() || !bLon.isFinite()) continue
                    roadDistance = min(roadDistance, segmentDistanceMeters(location.latitude, location.longitude, aLat, aLon, bLat, bLon))
                }
                if (roadDistance < closestDistance) {
                    closestDistance = roadDistance
                    closestLimit = limit
                }
            }
            closestLimit
        } catch (_: Exception) {
            null
        }
    }

    private fun segmentDistanceMeters(lat: Double, lon: Double, aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float {
        val metersPerLat = 111_320.0
        val metersPerLon = metersPerLat * cos(Math.toRadians(lat))
        val px = 0.0
        val py = 0.0
        val ax = (aLon - lon) * metersPerLon
        val ay = (aLat - lat) * metersPerLat
        val bx = (bLon - lon) * metersPerLon
        val by = (bLat - lat) * metersPerLat
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else ((px - ax) * dx + (py - ay) * dy) / lengthSquared
        val closestX = ax + t.coerceIn(0.0, 1.0) * dx
        val closestY = ay + t.coerceIn(0.0, 1.0) * dy
        return kotlin.math.sqrt(closestX * closestX + closestY * closestY).toFloat()
    }

    private fun distanceMeters(lat: Double, lon: Double, otherLat: Double, otherLon: Double): Float {
        val metersPerLat = 111_320.0
        val metersPerLon = metersPerLat * cos(Math.toRadians(lat))
        val dx = (otherLon - lon) * metersPerLon
        val dy = (otherLat - lat) * metersPerLat
        return kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
    }

    private fun httpGet(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept-Language", "es")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Mayormente despejado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        51, 53, 55, 56, 57 -> "Llovizna"
        61, 63, 65, 66, 67 -> "Lluvia"
        71, 73, 75, 77 -> "Nieve"
        80, 81, 82 -> "Chubascos"
        85, 86 -> "Nevadas"
        95, 96, 99 -> "Tormenta"
        else -> "Variable"
    }

    private companion object {
        const val HTTP_TIMEOUT_MS = 8_000
        const val PLACE_REFRESH_DISTANCE_M = 500f
        const val WEATHER_REFRESH_DISTANCE_M = 3_000f
        const val REFRESH_INTERVAL_MS = 15 * 60_000L
        const val INITIAL_RETRY_MS = 60_000L
        const val MAX_RETRY_MS = 10 * 60_000L
        const val USER_AGENT = "CivicLauncher/1.1 (Android; com.rachav2.civiclauncher)"
    }
}
