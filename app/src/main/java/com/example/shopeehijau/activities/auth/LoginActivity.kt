package com.example.shopeehijau.activities.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.shopeehijau.activities.admin.AdminDashboardActivity
import com.example.shopeehijau.activities.buyer.MainActivity
import com.example.shopeehijau.databinding.ActivityLoginBinding // Import ViewBinding
import com.example.shopeehijau.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding // Deklarasi ViewBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val TAG = "LoginActivityLifecycle"

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        // Cek jika user sudah login, langsung arahkan
        // Pindahkan pengecekan ini ke onResume atau setelah layout selesai di-inflate
        // jika ada interaksi UI yang bergantung pada status login awal.
        // Untuk kasus ini, onStart cocok karena hanya navigasi.
        if (auth.currentUser != null) {
            binding.progressBarLogin.visibility = View.VISIBLE // Tampilkan loading
            checkUserRoleAndRedirect(auth.currentUser!!.uid)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        // Inflate layout menggunakan ViewBinding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.btnLogin.setOnClickListener {
            handleLogin()
        }

        binding.tvRegisterLink.setOnClickListener {
            Log.d(TAG, "Navigasi ke RegisterActivity")
            // Intent Eksplisit untuk pindah ke RegisterActivity
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent) // Memulai Activity baru
        }
    }

    private fun handleLogin() {
        val email = binding.etEmailLogin.text.toString().trim()
        val password = binding.etPasswordLogin.text.toString().trim()

        if (!validateInputs(email, password)) {
            return
        }

        binding.progressBarLogin.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser
                    user?.let {
                        checkUserRoleAndRedirect(it.uid)
                    }
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    binding.progressBarLogin.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(
                        baseContext, "Login gagal: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun validateInputs(email: String, pass: String): Boolean {
        binding.tilEmailLogin.error = null
        binding.tilPasswordLogin.error = null

        if (email.isEmpty()) {
            binding.tilEmailLogin.error = "Email tidak boleh kosong"
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmailLogin.error = "Format email tidak valid"
            return false
        }
        if (pass.isEmpty()) {
            binding.tilPasswordLogin.error = "Password tidak boleh kosong"
            return false
        }
        if (pass.length < 6) {
            binding.tilPasswordLogin.error = "Password minimal 6 karakter"
            return false
        }
        return true
    }


    private fun checkUserRoleAndRedirect(uid: String) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                binding.progressBarLogin.visibility = View.GONE // Sembunyikan loading di sini
                binding.btnLogin.isEnabled = true

                if (document != null && document.exists()) {
                    val user = document.toObject<User>()
                    val intent: Intent
                    if (user?.role == "admin") {
                        Log.d(TAG, "User adalah Admin, navigasi ke AdminDashboardActivity")
                        Toast.makeText(this, "Login sebagai Admin berhasil!", Toast.LENGTH_SHORT).show()
                        intent = Intent(this, AdminDashboardActivity::class.java)
                    } else { // Default ke buyer atau jika role tidak ada (seharusnya tidak terjadi)
                        Log.d(TAG, "User adalah Buyer, navigasi ke MainActivity")
                        Toast.makeText(this, "Login berhasil!", Toast.LENGTH_SHORT).show()
                        intent = Intent(this, MainActivity::class.java)
                    }
                    // Membersihkan back stack agar tidak kembali ke LoginActivity setelah login
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish() // Menutup LoginActivity
                } else {
                    Log.d(TAG, "No such document for user role check, user might not be in Firestore")
                    Toast.makeText(this, "Data pengguna tidak ditemukan. Silakan coba lagi atau daftar.", Toast.LENGTH_LONG).show()
                    auth.signOut() // Logout jika data Firestore tidak konsisten
                }
            }
            .addOnFailureListener { exception ->
                binding.progressBarLogin.visibility = View.GONE
                binding.btnLogin.isEnabled = true
                Log.w(TAG, "Error getting user role: ", exception)
                Toast.makeText(this, "Gagal mengambil data peran: ${exception.message}", Toast.LENGTH_SHORT).show()
                auth.signOut()
            }
    }

    // --- Metode Lifecycle Lainnya (untuk observasi) ---
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }
}