package com.rachav2.civiclauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

internal data class InstalledApp(
    val label: String,
    val component: ComponentName,
    val icon: Drawable
)

/** Keeps installed-app discovery and head-unit app matching out of the activity. */
internal class InstalledAppsCatalog(context: Context) {
    private val packageManager = context.packageManager

    fun loadLauncherApps(): List<InstalledApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .map { result ->
                InstalledApp(
                    label = result.loadLabel(packageManager).toString(),
                    component = ComponentName(result.activityInfo.packageName, result.activityInfo.name),
                    icon = result.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun find(
        exactNames: List<String>,
        partialNames: List<String> = emptyList(),
        packageNameContains: String? = null
    ): ComponentName? {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = try {
            packageManager.queryIntentActivities(launcherIntent, 0).map { result ->
                Triple(
                    result.loadLabel(packageManager).toString().trim().lowercase(),
                    ComponentName(result.activityInfo.packageName, result.activityInfo.name),
                    result.activityInfo.packageName.lowercase()
                )
            }
        } catch (_: Exception) {
            return null
        }

        exactNames.forEach { name -> entries.firstOrNull { it.first == name }?.let { return it.second } }
        partialNames.forEach { part -> entries.firstOrNull { it.first.contains(part) }?.let { return it.second } }
        if (packageNameContains != null) {
            entries.firstOrNull { it.third.contains(packageNameContains) }?.let { return it.second }
        }
        return null
    }

    companion object {
        val MUSIC_BT_EXACT = listOf(
            "music bt", "musicbt", "bt music", "btmusic", "bt-music",
            "música bt", "musica bt", "bluetooth music"
        )
        val MUSIC_BT_CONTAINS = listOf("music bt", "musicbt", "bt music", "bluetooth music")
        val PHONE_BT_EXACT = listOf("bt", "bt phone", "btphone", "bluetooth phone")
        val ZLINK_EXACT = listOf("zlink5", "zlink 5", "zlink")
        val ZLINK_CONTAINS = listOf("zlink")
    }
}
