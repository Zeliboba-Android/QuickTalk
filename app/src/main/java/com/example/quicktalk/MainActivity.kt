package com.example.quicktalk

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.quicktalk.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.buttonLogin.setOnClickListener {
            loginUser()
        }

        binding.TVRegistration.setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java))
        }
    }

    private fun loginUser() {
        val email = binding.ETEmail.text.toString().trim()
        val password = binding.ETPassword.text.toString().trim()

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // ОБНОВЛЕНИЕ СТАТУСА ПРИ ВХОДЕ
                    updateOnlineStatus(true) // Добавлено
                    startActivity(Intent(this, UsersActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        baseContext, "Ошибка входа: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun updateOnlineStatus(online: Boolean) {
        val currentUser = auth.currentUser
        currentUser?.uid?.let { uid ->
            db.collection("users").document(uid)
                .update("online", online)
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Ошибка обновления статуса", e)
                }
        }
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            // ОБНОВЛЕНИЕ СТАТУСА ПРИ ЗАПУСКЕ ПРИЛОЖЕНИЯ
            updateOnlineStatus(true) // Добавлено
            startActivity(Intent(this, UsersActivity::class.java))
            finish()
        }
    }
}