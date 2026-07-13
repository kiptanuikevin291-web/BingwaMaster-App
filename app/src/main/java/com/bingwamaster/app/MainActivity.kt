package com.bingwamaster.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var signOutButton: Button

    private var watcherRunning = false
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val requiredPermissions = buildList {
        add(android.Manifest.permission.RECEIVE_SMS)
        add(android.Manifest.permission.READ_SMS)
        add(android.Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            statusText.text = "All permissions granted. Ready to start."
        } else {
            statusText.text = "Missing permissions — the watcher can't run without SMS, call, and notification access."
            Toast.makeText(this, "Grant all permissions to continue", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        signOutButton = findViewById(R.id.signOutButton)

        signOutButton.setOnClickListener {
            if (watcherRunning) stopWatcher()
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        toggleButton.setOnClickListener {
            if (!hasAllPermissions()) {
                permissionLauncher.launch(requiredPermissions)
                return@setOnClickListener
            }
            if (watcherRunning) stopWatcher() else startWatcher()
        }

        updateUiForState()

        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startWatcher() {
        val intent = Intent(this, WatcherForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        watcherRunning = true
        getSharedPreferences("bingwa_prefs", MODE_PRIVATE).edit().putBoolean("watcher_enabled", true).apply()
        updateUiForState()
    }

    private fun stopWatcher() {
        val intent = Intent(this, WatcherForegroundService::class.java)
        stopService(intent)
        watcherRunning = false
        getSharedPreferences("bingwa_prefs", MODE_PRIVATE).edit().putBoolean("watcher_enabled", false).apply()
        updateUiForState()
    }

    private fun updateUiForState() {
        toggleButton.text = if (watcherRunning) "Stop watching" else "Start watching"
        statusText.text = if (watcherRunning) {
            "Watching for M-Pesa payments…"
        } else if (hasAllPermissions()) {
            "Ready to start."
        } else {
            "Waiting for permissions…"
        }
    }
}
