package com.bingwamaster.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Wraps Android's USSD APIs so the rest of the app can fire a USSD request
 * and get the network's response back in a callback, instead of dealing
 * with the dialer UI directly.
 *
 * Requires: CALL_PHONE permission (already declared in the manifest) and
 * Android O (API 26)+ for sendUssdRequest. On older versions we fall back
 * to launching the dialer with the USSD code, but can't read the response.
 */
class UssdController(private val context: Context) {

    companion object {
        private const val TAG = "UssdController"
        private const val TIMEOUT_MS = 30_000L
    }

    interface Callback {
        fun onResult(response: String)
        fun onFailure(reason: String)
    }

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Fires a USSD request, e.g. code = "*544*1*254712345678*500#"
     */
    @SuppressLint("MissingPermission") // caller must have already checked CALL_PHONE
    fun sendUssd(code: String, callback: Callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            callback.onFailure("USSD auto-response requires Android 8.0+")
            return
        }

        var finished = false
        val timeoutRunnable = Runnable {
            if (!finished) {
                finished = true
                callback.onFailure("USSD request timed out")
            }
        }
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        try {
            telephonyManager.sendUssdRequest(
                code,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence
                    ) {
                        if (finished) return
                        finished = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        Log.i(TAG, "USSD response for '$request': $response")
                        callback.onResult(response.toString())
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) {
                        if (finished) return
                        finished = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        val reason = describeFailure(failureCode)
                        Log.w(TAG, "USSD request '$request' failed: $reason")
                        callback.onFailure(reason)
                    }
                },
                mainHandler
            )
        } catch (e: SecurityException) {
            finished = true
            mainHandler.removeCallbacks(timeoutRunnable)
            callback.onFailure("Missing CALL_PHONE permission: ${e.message}")
        }
    }

    private fun describeFailure(failureCode: Int): String {
        return when (failureCode) {
            TelephonyManager.USSD_RETURN_FAILURE -> "Network returned a USSD failure"
            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "USSD service unavailable (airplane mode / no signal?)"
            else -> "Unknown USSD failure (code $failureCode)"
        }
    }
}
