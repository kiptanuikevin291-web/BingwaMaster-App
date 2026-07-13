package com.bingwamaster.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Listens for incoming SMS and detects M-Pesa payment confirmation messages.
 * When a valid payment SMS is found, it parses the details and hands them
 * off to WatcherForegroundService, which will trigger the USSD flow.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"

        // Adjust this to match your actual M-Pesa sender ID / short code.
        // Safaricom M-Pesa messages typically come from "MPESA".
        private val MPESA_SENDER_IDS = setOf("MPESA")

        // Matches: "Confirmed. Ksh500.00 received from JOHN DOE 254712345678 on 7/11/26..."
        private val RECEIVED_PATTERN = Regex(
            """Ksh([\d,]+\.\d{2})\s+received from\s+(.+?)\s+(2547\d{8}|07\d{8})""",
            RegexOption.IGNORE_CASE
        )

        // Matches the transaction code, e.g. "QGH7X9K2L1 Confirmed."
        private val TXN_CODE_PATTERN = Regex("""^([A-Z0-9]{10})\s+Confirmed""")
    }

    data class MpesaPayment(
        val transactionCode: String?,
        val amount: String,
        val senderName: String,
        val senderPhone: String,
        val rawMessage: String
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val sender = sms.originatingAddress ?: continue
            val body = sms.messageBody ?: continue

            if (!isFromMpesa(sender, body)) continue

            val payment = parsePayment(body)
            if (payment != null) {
                Log.i(TAG, "Parsed M-Pesa payment: $payment")
                forwardToService(context, payment)
            } else {
                Log.d(TAG, "M-Pesa-like SMS did not match expected payment format")
            }
        }
    }

    private fun isFromMpesa(sender: String, body: String): Boolean {
        val senderMatches = MPESA_SENDER_IDS.any { sender.contains(it, ignoreCase = true) }
        val bodyLooksLikePayment = body.contains("received from", ignoreCase = true)
        return senderMatches && bodyLooksLikePayment
    }

    private fun parsePayment(body: String): MpesaPayment? {
        val match = RECEIVED_PATTERN.find(body) ?: return null
        val (amount, name, phone) = match.destructured

        val txnCode = TXN_CODE_PATTERN.find(body)?.groupValues?.get(1)

        return MpesaPayment(
            transactionCode = txnCode,
            amount = amount.replace(",", ""),
            senderName = name.trim(),
            senderPhone = normalizePhone(phone),
            rawMessage = body
        )
    }

    private fun normalizePhone(phone: String): String {
        return if (phone.startsWith("07")) {
            "254" + phone.substring(1)
        } else {
            phone
        }
    }

    private fun forwardToService(context: Context, payment: MpesaPayment) {
        val serviceIntent = Intent(context, WatcherForegroundService::class.java).apply {
            action = "com.bingwamaster.app.ACTION_PAYMENT_RECEIVED"
            putExtra("amount", payment.amount)
            putExtra("senderName", payment.senderName)
            putExtra("senderPhone", payment.senderPhone)
            putExtra("transactionCode", payment.transactionCode)
            putExtra("rawMessage", payment.rawMessage)
        }
        context.startForegroundService(serviceIntent)
    }
}
