package com.rachav2.civiclauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Read-only adapter for CAN decoder broadcasts.
 *
 * The head-unit vendor's real broadcast action/extra names must be identified on the device.
 * This adapter never sends commands to the vehicle and treats missing values as unknown.
 */
data class CanVehicleState(
    val frontLeftDoor: Boolean? = null,
    val frontRightDoor: Boolean? = null,
    val rearLeftDoor: Boolean? = null,
    val rearRightDoor: Boolean? = null,
    val trunkOpen: Boolean? = null,
    val hoodOpen: Boolean? = null,
    val lightsOn: Boolean? = null,
    val handbrakeOn: Boolean? = null,
    val receivedAtMs: Long = 0L
) {
    val hasAlerts: Boolean
        get() = frontLeftDoor == true || frontRightDoor == true ||
            rearLeftDoor == true || rearRightDoor == true ||
            trunkOpen == true || hoodOpen == true

    fun toDisplayText(): String {
        val alerts = mutableListOf<String>()
        if (frontLeftDoor == true) alerts += "puerta del. izq."
        if (frontRightDoor == true) alerts += "puerta del. der."
        if (rearLeftDoor == true) alerts += "puerta tras. izq."
        if (rearRightDoor == true) alerts += "puerta tras. der."
        if (trunkOpen == true) alerts += "maletero abierto"
        if (hoodOpen == true) alerts += "capó abierto"
        if (alerts.isNotEmpty()) return "⚠ " + alerts.joinToString(" · ")
        val known = listOf(frontLeftDoor, frontRightDoor, rearLeftDoor, rearRightDoor, trunkOpen, hoodOpen, lightsOn, handbrakeOn)
            .any { it != null }
        if (!known) return "CANbus · esperando datos de la pantalla"
        val states = mutableListOf<String>()
        if (lightsOn != null) states += if (lightsOn) "luces ON" else "luces OFF"
        if (handbrakeOn != null) states += if (handbrakeOn) "freno puesto" else "freno liberado"
        if (listOf(frontLeftDoor, frontRightDoor, rearLeftDoor, rearRightDoor).all { it == false }) states += "puertas cerradas"
        if (trunkOpen == false) states += "maletero cerrado"
        if (hoodOpen == false) states += "capó cerrado"
        return if (states.isEmpty()) "CANbus · datos parciales" else states.joinToString(" · ")
    }

    companion object {
        fun fromIntent(intent: Intent): CanVehicleState {
            fun read(vararg keys: String): Boolean? {
                for (key in keys) {
                    if (!intent.hasExtra(key)) continue
                    val value = intent.extras?.get(key) ?: continue
                    when (value) {
                        is Boolean -> return value
                        is Int -> if (value == 0 || value == 1) return value == 1
                        is String -> when (value.trim().lowercase()) {
                            "1", "true", "on", "open", "opened", "yes", "active" -> return true
                            "0", "false", "off", "closed", "close", "no", "inactive" -> return false
                        }
                    }
                }
                return null
            }
            return CanVehicleState(
                frontLeftDoor = read("door_fl", "front_left_door", "doorFrontLeft"),
                frontRightDoor = read("door_fr", "front_right_door", "doorFrontRight"),
                rearLeftDoor = read("door_rl", "rear_left_door", "doorRearLeft"),
                rearRightDoor = read("door_rr", "rear_right_door", "doorRearRight"),
                trunkOpen = read("trunk", "trunk_open", "boot_open"),
                hoodOpen = read("hood", "hood_open"),
                lightsOn = read("lights", "lights_on", "headlights_on"),
                handbrakeOn = read("handbrake", "parking_brake", "brake_hand"),
                receivedAtMs = System.currentTimeMillis()
            )
        }
    }
}

class CanBusGateway(
    private val context: Context,
    private val onState: (CanVehicleState) -> Unit
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_STATE) return
            onState(CanVehicleState.fromIntent(intent))
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter(ACTION_STATE)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            registered = true
        } catch (_: Exception) {
            registered = false
        }
    }

    fun stop() {
        if (!registered) return
        try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        registered = false
    }

    companion object {
        /** Replace/configure this bridge only after confirming the head-unit vendor API. */
        const val ACTION_STATE = "com.rachav2.civiclauncher.CANBUS_STATE"
    }
}
