package com.example.shopeehijau.activities.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log

import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.shopeehijau.activities.buyer.MainActivity
import com.example.shopeehijau.databinding.ActivityRegisterBinding // Import ViewBinding
import com.example.shopeehijau.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding // Deklarasi ViewBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val TAG = "RegisterActivityLifecycle"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.btnRegister.setOnClickListener {
            handleRegister()
        }

        binding.tvLoginLink.setOnClickListener {
            Log.d(TAG, "Navigasi ke LoginActivity")
            // Intent Eksplisit untuk kembali ke LoginActivity
            // finish() // Menutup RegisterActivity agar tidak menumpuk di back stack
            // Atau, jika ingin user bisa kembali ke Register dari Login (jarang):
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun handleRegister() {
        val name = binding.etNameRegister.text.toString().trim()
        val email = binding.etEmailRegister.text.toString().trim()
        val password = binding.etPasswordRegister.text.toString().trim()

        if (!validateInputs(name, email, password)) {
            return
        }

        binding.progressBarRegister.visibility = View.VISIBLE
        binding.btnRegister.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:success")
                    val firebaseUser: FirebaseUser? = auth.currentUser
                    firebaseUser?.let {
                        saveUserToFirestore(it, name, email)
                    }
                } else {
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    binding.progressBarRegister.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(
                        baseContext, "Registrasi gagal: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun validateInputs(name: String, email: String, pass: String): Boolean {
        binding.tilNameRegister.error = null
        binding.tilEmailRegister.error = null
        binding.tilPasswordRegister.error = null

        if (name.isEmpty()) {
            binding.tilNameRegister.error = "Nama tidak boleh kosong"
            return false
        }
        if (email.isEmpty()) {
            binding.tilEmailRegister.error = "Email tidak boleh kosong"
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmailRegister.error = "Format email tidak valid"
            return false
        }
        if (pass.isEmpty()) {
            binding.tilPasswordRegister.error = "Password tidak boleh kosong"
            return false
        }
        if (pass.length < 6) {
            binding.tilPasswordRegister.error = "Password minimal 6 karakter"
            return false
        }
        return true
    }

    private fun saveUserToFirestore(firebaseUser: FirebaseUser, name: String, email: String) {
        val user = User(
            uid = firebaseUser.uid,
            name = name,
            email = email,
            role = "buyer" // Role default saat registrasi adalah "buyer"
        )

        firestore.collection("users").document(firebaseUser.uid)
            .set(user)
            .addOnSuccessListener {
                Log.d(TAG, "User profile created in Firestore for ${firebaseUser.uid}")
                binding.progressBarRegister.visibility = View.GONE
                // btnRegister tetap disabled karena sudah berhasil dan akan pindah activity
                Toast.makeText(this, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()

                // Arahkan ke halaman utama pembeli setelah registrasi
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish() // Menutup RegisterActivity
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding user document to Firestore", e)
                binding.progressBarRegister.visibility = View.GONE
                binding.btnRegister.isEnabled = true // Aktifkan lagi jika gagal simpan ke Firestore
                Toast.makeText(this, "Gagal menyimpan data pengguna: ${e.message}", Toast.LENGTH_SHORT).show()
                // Pertimbangkan untuk menghapus user dari Auth jika penyimpanan Firestore gagal,
                // agar tidak ada user Auth tanpa data di Firestore.
                // firebaseUser.delete().addOnCompleteListener { ... }
            }
    }
    // --- Metode Lifecycle Lainnya (opsional) ---
}