package com.example.shopeehijau.activities.buyer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.shopeehijau.R
import com.example.shopeehijau.activities.auth.LoginActivity
import com.example.shopeehijau.databinding.ActivityProfileBinding
import com.example.shopeehijau.models.User
import com.example.shopeehijau.utils.ImageHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import java.io.IOException

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var currentUserId: String? = null
    private var currentUserData: User? = null
    private var newProfileImageBase64: String? = null
    private var selectedImageUri: Uri? = null
    private val TAG = "ProfileActivity"

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let {
                Log.d(TAG, "New profile image selected: $it")
                selectedImageUri = it
                binding.ivProfileUserImage.setImageURI(it) // Tampilkan preview
                // Konversi ke Base64 bisa ditunda atau langsung
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate")

        setSupportActionBar(binding.toolbarProfile)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            navigateToLogin()
            return
        }

        loadUserProfile()

        binding.btnChangeProfileImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        binding.btnUpdateProfile.setOnClickListener {
            // Jika gambar baru dipilih, konversi dulu
            selectedImageUri?.let { uri ->
                if (newProfileImageBase64 == null) { // Hanya konversi jika belum ada (dari percobaan simpan sebelumnya)
                    try {
                        val bitmap = if (Build.VERSION.SDK_INT < 28) {
                            MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        } else {
                            val source = ImageDecoder.createSource(contentResolver, uri)
                            ImageDecoder.decodeBitmap(source)
                        }
                        newProfileImageBase64 = ImageHelper.encodeBitmapToBase64(bitmap, 70) // Kualitas 70 untuk profil
                        Log.d(TAG, "New profile image converted to Base64.")
                        updateUserProfileData()
                    } catch (e: IOException) {
                        Log.e(TAG, "Error converting new profile image", e)
                        Toast.makeText(this, "Gagal memproses gambar baru.", Toast.LENGTH_SHORT).show()
                    }
                } else { // Base64 sudah ada
                    updateUserProfileData()
                }
            } ?: run {
                // Tidak ada gambar baru dipilih, update data teks saja
                updateUserProfileData()
            }
        }
    }
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun loadUserProfile() {
        currentUserId?.let { userId ->
            Log.d(TAG, "Loading profile for user: $userId")
            binding.progressBarProfile.visibility = View.VISIBLE
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    binding.progressBarProfile.visibility = View.GONE
                    if (document != null && document.exists()) {
                        currentUserData = document.toObject<User>()
                        currentUserData?.let { user ->
                            Log.d(TAG, "User data loaded: ${user.name}")
                            binding.etProfileName.setText(user.name)
                            binding.etProfileEmail.setText(user.email) // Email biasanya tidak bisa diubah
                            if (user.profileImageUrl.isNotEmpty()) {
                                val bitmap = ImageHelper.decodeBase64ToBitmap(user.profileImageUrl)
                                if (bitmap != null) {
                                    binding.ivProfileUserImage.setImageBitmap(bitmap)
                                } else {
                                    binding.ivProfileUserImage.setImageResource(R.drawable.ic_profile_placeholder)
                                }
                            } else {
                                binding.ivProfileUserImage.setImageResource(R.drawable.ic_profile_placeholder)
                            }
                        }
                    } else {
                        Log.w(TAG, "User document not found in Firestore.")
                        Toast.makeText(this, "Data profil tidak ditemukan.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBarProfile.visibility = View.GONE
                    Log.e(TAG, "Error loading profile", e)
                    Toast.makeText(this, "Gagal memuat profil: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUserProfileData() {
        val newName = binding.etProfileName.text.toString().trim()

        if (newName.isEmpty()) {
            binding.tilProfileName.error = "Nama tidak boleh kosong."
            return
        }
        binding.tilProfileName.error = null


        currentUserId?.let { userId ->
            Log.d(TAG, "Updating profile for user: $userId")
            binding.progressBarProfile.visibility = View.VISIBLE
            binding.btnUpdateProfile.isEnabled = false

            val updates = hashMapOf<String, Any>(
                "name" to newName
                // Email tidak diupdate karena biasanya tetap
            )
            // Jika ada gambar profil baru, tambahkan ke map updates
            newProfileImageBase64?.let {
                updates["profileImageUrl"] = it
            } ?: run {
                // Jika tidak ada gambar baru dipilih DAN gambar lama ada, pertahankan gambar lama.
                // Jika gambar lama kosong dan tidak ada gambar baru, field imageUrl tidak perlu diupdate (tetap kosong)
                currentUserData?.profileImageUrl?.let { oldImageUrl ->
                    if (oldImageUrl.isNotEmpty() && newProfileImageBase64 == null) { // Hanya jika newProfileImageBase64 belum diisi dari gambar baru
                        updates["profileImageUrl"] = oldImageUrl // Ini akan terjadi jika user tidak pilih gambar baru
                    }
                }
            }


            firestore.collection("users").document(userId).update(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Profile updated successfully.")
                    binding.progressBarProfile.visibility = View.GONE
                    binding.btnUpdateProfile.isEnabled = true
                    Toast.makeText(this, "Profil berhasil diperbarui.", Toast.LENGTH_SHORT).show()
                    // Opsional: muat ulang data profil atau finish activity
                    newProfileImageBase64 = null // Reset agar tidak terpakai lagi jika tidak ada perubahan gambar berikutnya
                    selectedImageUri = null
                    loadUserProfile() // Muat ulang untuk menampilkan data terbaru
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating profile", e)
                    binding.progressBarProfile.visibility = View.GONE
                    binding.btnUpdateProfile.isEnabled = true
                    Toast.makeText(this, "Gagal update profil: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    // ... Lifecycle methods lainnya ...
}