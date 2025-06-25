package com.example.quicktalk
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.quicktalk.databinding.ActivityRegistrationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.buttonCreateAccount.setOnClickListener {
            registerUser()
        }
    }

    private fun registerUser() {
        val name = binding.ETName.text.toString().trim()
        val lastName = binding.ETLastName.text.toString().trim()
        val email = binding.ETEmail.text.toString().trim()
        val password = binding.ETPassword.text.toString().trim()

        // Валидация данных
        if (name.isEmpty()) {
            binding.ETName.error = "Введите имя"
            binding.ETName.requestFocus()
            return
        }

        if (lastName.isEmpty()) {
            binding.ETLastName.error = "Введите фамилию"
            binding.ETLastName.requestFocus()
            return
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.ETEmail.error = "Введите корректный email"
            binding.ETEmail.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.ETPassword.error = "Пароль должен содержать минимум 6 символов"
            binding.ETPassword.requestFocus()
            return
        }

        // Создание пользователя в Firebase Auth
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Регистрация успешна
                    val user = auth.currentUser
                    user?.let {
                        // Сохраняем дополнительные данные в Firestore
                        saveUserToFirestore(it.uid, name, lastName, email)
                    }
                } else {
                    // Ошибка регистрации
                    Toast.makeText(
                        baseContext, "Ошибка регистрации: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun saveUserToFirestore(uid: String, name: String, lastName: String, email: String) {
        val user = hashMapOf(
            "uid" to uid,
            "firstName" to name,
            "lastName" to lastName,
            "email" to email,
            "online" to true
        )

        db.collection("users").document(uid)
            .set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Регистрация успешна!", Toast.LENGTH_SHORT).show()

                // Переходим на экран пользователей
                startActivity(Intent(this, UsersActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Log.w("Registration", "Ошибка сохранения данных", e)
                Toast.makeText(
                    baseContext, "Ошибка сохранения данных пользователя",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}