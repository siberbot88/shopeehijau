package com.example.shopeehijau.activities.admin

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.shopeehijau.databinding.ActivityAddProductBinding
import com.example.shopeehijau.models.Product
import com.example.shopeehijau.utils.ImageHelper // Utilitas Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.IOException

class AddProductActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddProductBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var imageBase64: String? = null
    private var selectedImageUri: Uri? = null // Untuk menyimpan URI gambar yang dipilih
    private val TAG = "AddProductActivityLifecycle"

    // Launcher untuk memilih gambar (mirip dengan di MODUL 5 2025.pdf)
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let {
                Log.d(TAG, "Image selected: $it")
                selectedImageUri = it // Simpan URI
                binding.ivProductPreview.setImageURI(it) // Tampilkan preview
                // Konversi ke Base64 bisa ditunda sampai tombol simpan, atau langsung
                // convertAndSetBase64(it)
            }
        } else {
            Log.d(TAG, "Image selection cancelled or failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityAddProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar dengan tombol kembali
        setSupportActionBar(binding.toolbarAddProduct)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbarAddProduct.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Sesuai dengan perilaku tombol kembali sistem
        }


        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.btnSelectImage.setOnClickListener {
            Log.d(TAG, "Select image button clicked.")
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            // Alternatif: Intent.ACTION_GET_CONTENT
            // intent.type = "image/*"
            imagePickerLauncher.launch(intent)
        }

        binding.btnSaveProduct.setOnClickListener {
            Log.d(TAG, "Save product button clicked.")
            // Pertama, konversi gambar ke Base64 jika belum
            selectedImageUri?.let { uri ->
                if (imageBase64 == null) { // Hanya konversi jika belum ada (misal, user belum klik simpan sebelumnya)
                    try {
                        val bitmap = if (Build.VERSION.SDK_INT < 28) {
                            MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        } else {
                            val source = ImageDecoder.createSource(contentResolver, uri)
                            ImageDecoder.decodeBitmap(source)
                        }
                        imageBase64 = ImageHelper.encodeBitmapToBase64(bitmap) // Gunakan helper
                        Log.d(TAG, "Image converted to Base64.")
                        // Setelah konversi berhasil, lanjutkan menyimpan produk
                        saveProductToFirestore()
                    } catch (e: IOException) {
                        Log.e(TAG, "Error converting image to Base64", e)
                        Toast.makeText(this, "Gagal memproses gambar.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Jika imageBase64 sudah ada (mungkin dari percobaan simpan sebelumnya yang gagal di tahap lain)
                    saveProductToFirestore()
                }
            } ?: run {
                // Jika tidak ada gambar dipilih tapi user klik simpan
                if (imageBase64 == null) { // Cek lagi jika memang belum ada gambar sama sekali
                    Toast.makeText(this, "Silakan pilih gambar produk.", Toast.LENGTH_SHORT).show()
                } else {
                    // Ini skenario aneh, seharusnya selectedImageUri ada jika imageBase64 ada dari proses baru
                    // Untuk keamanan, lanjutkan saja jika imageBase64 sudah ada
                    saveProductToFirestore()
                }
            }
        }
    }


    private fun validateInputs(name: String, desc: String, priceStr: String, stockStr: String): Boolean {
        binding.tilProductName.error = null
        binding.tilProductDescription.error = null
        binding.tilProductPrice.error = null
        binding.tilProductStock.error = null

        if (name.isEmpty()) {
            binding.tilProductName.error = "Nama produk tidak boleh kosong"
            return false
        }
        if (desc.isEmpty()) {
            binding.tilProductDescription.error = "Deskripsi tidak boleh kosong"
            return false
        }
        if (priceStr.isEmpty() || priceStr.toDoubleOrNull() == null || priceStr.toDouble() <= 0) {
            binding.tilProductPrice.error = "Harga tidak valid"
            return false
        }
        if (stockStr.isEmpty() || stockStr.toIntOrNull() == null || stockStr.toInt() < 0) {
            binding.tilProductStock.error = "Stok tidak valid"
            return false
        }
        if (imageBase64 == null && selectedImageUri == null) { // Jika sama sekali belum ada upaya pilih gambar
            Toast.makeText(this, "Gambar produk harus dipilih", Toast.LENGTH_SHORT).show()
            return false
        }
        if (imageBase64 == null && selectedImageUri != null) { // Jika gambar dipilih tapi belum di-encode (misalnya, jika dipisah logikanya)
            Toast.makeText(this, "Gambar sedang diproses, coba simpan lagi.", Toast.LENGTH_SHORT).show()
            // Atau panggil konversi di sini sebelum return false
            return false
        }

        return true
    }


    private fun saveProductToFirestore() {
        val name = binding.etProductName.text.toString().trim()
        val description = binding.etProductDescription.text.toString().trim()
        val priceString = binding.etProductPrice.text.toString().trim()
        val stockString = binding.etProductStock.text.toString().trim()
        val sellerId = auth.currentUser?.uid

        if (!validateInputs(name, description, priceString, stockString)) {
            return
        }
        // `imageBase64` seharusnya sudah diisi oleh pemanggilan `convertAndSetBase64`
        // atau dari logika di `btnSaveProduct.setOnClickListener`

        if (sellerId == null) {
            Log.e(TAG, "User not authenticated to save product.")
            Toast.makeText(this, "Anda harus login untuk menyimpan produk.", Toast.LENGTH_SHORT).show()
            return
        }
        if (imageBase64.isNullOrEmpty()) { // Cek final untuk Base64
            Log.e(TAG, "Image Base64 is null or empty before saving.")
            Toast.makeText(this, "Gambar produk bermasalah atau belum dipilih.", Toast.LENGTH_SHORT).show()
            return
        }


        binding.progressBarAddProduct.visibility = View.VISIBLE
        binding.btnSaveProduct.isEnabled = false

        val product = Product(
            name = name,
            description = description,
            price = priceString.toDouble(),
            stock = stockString.toInt(),
            imageUrl = imageBase64!!, // Pastikan tidak null di sini setelah validasi
            sellerId = sellerId
        )

        firestore.collection("products").add(product)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Product added successfully with ID: ${documentReference.id}")
                binding.progressBarAddProduct.visibility = View.GONE
                // btnSaveProduct biarkan disabled karena proses selesai
                Toast.makeText(this, "Produk berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK) // Kirim hasil OK ke AdminDashboardActivity
                finish() // Menutup AddProductActivity
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding product to Firestore", e)
                binding.progressBarAddProduct.visibility = View.GONE
                binding.btnSaveProduct.isEnabled = true
                Toast.makeText(this, "Gagal menambahkan produk: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    // --- Metode Lifecycle Lainnya (opsional) ---
}