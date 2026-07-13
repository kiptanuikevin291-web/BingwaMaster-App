package com.bingwamaster.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts WatcherForegroundService after the device reboots, but only if
 * the user had it running before (tracked via SharedPreferences, set in
 * MainActivity's start/stop watcher actions).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val wasEnabled = context
            .getSharedPreferences("bingwa_prefs", Context.MODE_PRIVATE)
            .getBoolean("watcher_enabled", false)

        if (wasEnabled) {
            val serviceIntent = Intent(context, WatcherForegroundService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
