package com.bingwamaster.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Stays alive in the background so SmsReceiver can hand off parsed M-Pesa
 * payments immediately. On receiving a payment, builds the appropriate USSD
 * code and fires it through UssdController.
 */
class WatcherForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "bingwa_watcher_channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var ussdController: UssdController

    override fun onCreate() {
        super.onCreate()
        ussdController = UssdController(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Watching for payments…"))

        if (intent?.action == "com.bingwamaster.app.ACTION_PAYMENT_RECEIVED") {
            handlePayment(intent)
        }

        return START_STICKY
    }

    private fun handlePayment(intent: Intent) {
        val amount = intent.getStringExtra("amount") ?: return
        val senderPhone = intent.getStringExtra("senderPhone") ?: return
        val transactionCode = intent.getStringExtra("transactionCode")

        // TODO: replace with your actual USSD code format, e.g. for
        // Safaricom Bingwa reselling this is usually something like
        // *544*1*<recipient>*<amount>#. Confirm the exact code with
        // your provider before wiring this up for real transactions.
        val ussdCode = "*544*1*$senderPhone*${amount.substringBefore(".")}#"

        updateNotification("Dialing USSD for Ksh$amount to $senderPhone…")

        ussdController.sendUssd(ussdCode, object : UssdController.Callback {
            override fun onResult(response: String) {
                updateNotification("Done: $transactionCode → $response")
            }

            override fun onFailure(reason: String) {
                updateNotification("Failed for $transactionCode: $reason")
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bingwa Watcher",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bingwa Master")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
