package com.rachav2.civiclauncher

import android.service.notification.NotificationListenerService

/**
 * Servicio vacío. Solo existe para que el usuario pueda concederle "Acceso a notificaciones",
 * que es el permiso que Android exige para leer la canción que se está reproduciendo.
 */
class MediaListenerService : NotificationListenerService()
