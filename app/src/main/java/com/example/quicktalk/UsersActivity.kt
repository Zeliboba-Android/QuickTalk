package com.example.quicktalk

import UserAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quicktalk.databinding.ActivityUsersBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsersBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = "Пользователи"
        setSupportActionBar(binding.toolbar)

        loadUsers()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.item_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun logout() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            // Обновляем статус при выходе
            db.collection("users").document(currentUserId)
                .update("online", false)
                .addOnCompleteListener {
                    auth.signOut()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
        } else {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun loadUsers() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("users")
            .whereNotEqualTo("uid", currentUserId)
            .addSnapshotListener { snapshot, error -> // слушатель реального времени
                if (error != null) {
                    Log.e("UsersActivity", "Ошибка загрузки пользователей", error)
                    return@addSnapshotListener
                }

                val users = mutableListOf<User>()
                snapshot?.documents?.forEach { document ->
                    val user = document.toObject(User::class.java)
                    user?.let {
                        users.add(it)
                    }
                }
                setupRecyclerView(users)
            }
    }

    private fun setupRecyclerView(users: List<User>) {
        val adapter = UserAdapter(users) { selectedUser ->
            // Обработка выбора пользователя
             startActivity(Intent(this, ChatActivity::class.java).apply {
                 putExtra("userId", selectedUser.uid)
                 putExtra("userName", "${selectedUser.firstName} ${selectedUser.lastName}")
             })
        }

        binding.recyclerViewUsers.apply {
            layoutManager = LinearLayoutManager(this@UsersActivity)
            this.adapter = adapter
        }
    }
}