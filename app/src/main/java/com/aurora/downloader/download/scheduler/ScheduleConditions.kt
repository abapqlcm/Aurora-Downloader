package com.aurora.downloader.download.scheduler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

/**
 * A scheduled download only starts when every condition it was created with is
 * satisfied. Keeping the checks in one place means the scheduler and the
 * "ready yet?" UI in Add-Download always agree about what a schedule means.
 */
data class ScheduleConditions(
    val startAtEpochMillis: Long = 0L,
    val requireUnmetered: Boolean = false,
    val requireCharging: Boolean = false
) {

    /** No conditions at all: the download can start immediately. */
    val isImmediate: Boolean
        get() = startAtEpochMillis <= 0L && !requireUnmetered && !requireCharging

    /**
     * Why the download is still waiting, or null if it is ready to go.
     * Reported to the user rather than leaving them staring at "Scheduled".
     */
    fun unmetReason(context: Context): String? {
        val now = System.currentTimeMillis()
        if (startAtEpochMillis > 0L && now < startAtEpochMillis) {
            return "Starts ${formatWhen(startAtEpochMillis)}"
        }
        if (requireUnmetered && !isUnmetered(context)) {
            return "Waiting for Wi-Fi"
        }
        if (requireCharging && !isCharging(context)) {
            return "Waiting for a charger"
        }
        return null
    }

    /** True when every gate is open and the download may start now. */
    fun allSatisfied(context: Context): Boolean = unmetReason(context) == null
}

fun isUnmetered(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}

fun isCharging(context: Context): Boolean {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        ?: return false
    return bm.isCharging
}

/** "9:30 PM" or "Tomorrow 9:30 AM" — short enough for a card subtitle. */
fun formatWhen(epochMillis: Long): String {
    if (epochMillis <= 0L) return "as soon as possible"
    val now = System.currentTimeMillis()
    val sameDay = sameDay(now, epochMillis)
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val time = "%d:%02d %s".format(
        if (cal.get(java.util.Calendar.HOUR) == 0) 12
        else cal.get(java.util.Calendar.HOUR),
        cal.get(java.util.Calendar.MINUTE),
        if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
    )
    return when {
        sameDay -> time
        epochMillis - now < 2 * 24 * 60 * 60 * 1000L -> "Tomorrow $time"
        else -> {
            "%s %d, %s".format(
                monthName(cal.get(java.util.Calendar.MONTH)),
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                time
            )
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.MONTH) == cb.get(java.util.Calendar.MONTH) &&
        ca.get(java.util.Calendar.DAY_OF_MONTH) == cb.get(java.util.Calendar.DAY_OF_MONTH)
}

private fun monthName(index: Int): String = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)[index]
