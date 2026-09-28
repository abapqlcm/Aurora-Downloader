package com.aurora.downloader.platform.battery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

private const val ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATION =
    "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"

/**
 * Android puts any app the user does not open for a day to sleep, killing its
 * background downloads. This is the single most-reported "my download stopped
 * for no reason" cause, so Settings offers the exemption request.
 */
object BatteryOptimization {

    /** True only when the user has already granted the exemption. */
    fun isExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Intent that takes the user to the system page for granting it, or null
     * on Android versions where the concept does not exist (< API 23).
     */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATION).apply {
            data = Uri.parse("package:${context.packageName}")
            // Some OEM ROMs have no activity for this action; the caller
            // should fall back to the app's own details page.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Fallback used when [requestIntent] cannot be resolved: the app's own
     * system details page, where the user can find the battery setting.
     */
    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    ).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    /**
     * Whether the OEM ROM actually has a page for the exemption request. On
     * ROMs without it, asking for the permission is a dead button.
     */
    fun canRequest(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            requestIntent(context)?.let {
                context.packageManager.resolveActivity(it, 0) != null
            } == true
}
