package com.bingwamaster.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Simple email/password auth screen backed by Firebase Authentication.
 * On success, launches MainActivity and finishes this one so the user
 * can't navigate back to the login screen with the back button.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var loginButton: Button
    private lateinit var signUpButton: Button
    private lateinit var errorText: TextView
    private lateinit var forgotPasswordText: TextView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailField = findViewById(R.id.emailField)
        passwordField = findViewById(R.id.passwordField)
        loginButton = findViewById(R.id.loginButton)
        signUpButton = findViewById(R.id.signUpButton)
        errorText = findViewById(R.id.errorText)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)

        forgotPasswordText.setOnClickListener { showResetPasswordDialog() }

        loginButton.setOnClickListener { attemptLogin() }
        signUpButton.setOnClickListener { attemptSignUp() }
    }

    override fun onStart() {
        super.onStart()
        // Already signed in from a previous session? Skip straight to MainActivity.
        if (auth.currentUser != null) {
            goToMain()
        }
    }

    private fun attemptLogin() {
        val (email, password) = readInputs() ?: return
        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    goToMain()
                } else {
                    showError(task.exception?.localizedMessage ?: "Login failed. Check your credentials.")
                }
            }
    }

    private fun attemptSignUp() {
        val (email, password) = readInputs() ?: return
        if (password.length < 6) {
            showError("Password must be at least 6 characters.")
            return
        }
        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    goToMain()
                } else {
                    showError(task.exception?.localizedMessage ?: "Sign-up failed.")
                }
            }
    }

    private fun readInputs(): Pair<String, String>? {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            showError("Enter both email and password.")
            return null
        }
        errorText.text = ""
        return email to password
    }

    private fun setLoading(loading: Boolean) {
        loginButton.isEnabled = !loading
        signUpButton.isEnabled = !loading
    }

    private fun showError(message: String) {
        errorText.text = message
    }

    private fun showResetPasswordDialog() {
        val input = EditText(this).apply {
            hint = "Email"
            setText(emailField.text.toString().trim())
        }

        AlertDialog.Builder(this)
            .setTitle("Reset password")
            .setMessage("Enter your email and we'll send you a reset link.")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isEmpty()) {
                    Toast.makeText(this, "Enter your email first", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        val message = if (task.isSuccessful) {
                            "Reset link sent — check your inbox."
                        } else {
                            task.exception?.localizedMessage ?: "Couldn't send reset email."
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
