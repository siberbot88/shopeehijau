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
import com.example.shopeehijau.databinding.ActivityEditProductBinding // Ganti dengan binding yang sesuai
import com.example.shopeehijau.models.Product
import com.example.shopeehijau.utils.ImageHelper // Utilitas Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.IOException

class EditProductActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditProductBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var currentProduct: Product? = null
    private var newImageBase64: String? = null // Untuk menyimpan Base64 gambar baru jika ada
    private var selectedImageUri: Uri? = null
    private val TAG = "EditProductActivityLifecycle"


    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let {
                Log.d(TAG, "New image selected: $it")
                selectedImageUri = it
                binding.ivEditProductPreview.setImageURI(it)
                // Konversi ke Base64 bisa ditunda atau langsung di sini
                // convertAndSetNewBase64(it)
            }
        } else {
            Log.d(TAG, "New image selection cancelled or failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityEditProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarEditProduct)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbarEditProduct.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Ambil data produk dari Intent
        currentProduct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("PRODUCT_EXTRA", Product::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Product>("PRODUCT_EXTRA")
        }

        if (currentProduct == null) {
            Log.e(TAG, "Product data not found in Intent. Finishing activity.")
            Toast.makeText(this, "Gagal memuat data produk.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Log.d(TAG, "Editing product: ${currentProduct?.name}")
        populateFormWithProductData()

        binding.btnEditSelectImage.setOnClickListener {
            Log.d(TAG, "Select new image button clicked.")
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        binding.btnUpdateProduct.setOnClickListener {
            Log.d(TAG, "Update product button clicked.")
            // Jika ada gambar baru yang dipilih, konversi dulu
            selectedImageUri?.let { uri ->
                if (newImageBase64 == null) { // Hanya konversi jika belum (dari percobaan simpan sebelumnya)
                    try {
                        val bitmap = if (Build.VERSION.SDK_INT < 28) {
                            MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        } else {
                            val source = ImageDecoder.createSource(contentResolver, uri)
                            ImageDecoder.decodeBitmap(source)
                        }
                        newImageBase64 = ImageHelper.encodeBitmapToBase64(bitmap)
                        Log.d(TAG, "New image converted to Base64.")
                        updateProductInFirestore()
                    } catch (e: IOException) {
                        Log.e(TAG, "Error converting new image to Base64", e)
                        Toast.makeText(this, "Gagal memproses gambar baru.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    updateProductInFirestore()
                }
            } ?: run {
                // Tidak ada gambar baru dipilih, update dengan data lain
                updateProductInFirestore()
            }
        }
    }

    private fun populateFormWithProductData() {
        currentProduct?.let { product ->
            binding.etEditProductName.setText(product.name)
            binding.etEditProductDescription.setText(product.description)
            binding.etEditProductPrice.setText(product.price.toString())
            binding.etEditProductStock.setText(product.stock.toString())

            // Tampilkan gambar produk yang ada
            if (product.imageUrl.isNotEmpty()) {
                val bitmap = ImageHelper.decodeBase64ToBitmap(product.imageUrl)
                if (bitmap != null) {
                    binding.ivEditProductPreview.setImageBitmap(bitmap)
                } else {
                    binding.ivEditProductPreview.setImageResource(R.drawable.ic_placeholder_image) // Fallback
                    Log.w(TAG, "Failed to decode existing product image Base64.")
                }
            } else {
                binding.ivEditProductPreview.setImageResource(R.drawable.ic_placeholder_image)
            }
        }
    }

    private fun validateInputs(name: String, desc: String, priceStr: String, stockStr: String): Boolean {
        binding.tilEditProductName.error = null
        // ... (validasi lain seperti di AddProductActivity) ...

        if (name.isEmpty()) {
            binding.tilEditProductName.error = "Nama produk tidak boleh kosong"
            return false
        }
        if (desc.isEmpty()) {
            binding.tilEditProductDescription.error = "Deskripsi tidak boleh kosong"
            return false
        }
        if (priceStr.isEmpty() || priceStr.toDoubleOrNull() == null || priceStr.toDouble() <= 0) {
            binding.tilEditProductPrice.error = "Harga tidak valid"
            return false
        }
        if (stockStr.isEmpty() || stockStr.toIntOrNull() == null || stockStr.toInt() < 0) {
            binding.tilEditProductStock.error = "Stok tidak valid"
            return false
        }
        // Validasi gambar tidak seketat Add, karena mungkin admin hanya ingin ubah teks
        return true
    }

    private fun updateProductInFirestore() {
        val name = binding.etEditProductName.text.toString().trim()
        val description = binding.etEditProductDescription.text.toString().trim()
        val priceString = binding.etEditProductPrice.text.toString().trim()
        val stockString = binding.etEditProductStock.text.toString().trim()

        if (!validateInputs(name, description, priceString, stockString)) {
            return
        }

        currentProduct?.let { productToUpdate ->
            binding.progressBarEditProduct.visibility = View.VISIBLE
            binding.btnUpdateProduct.isEnabled = false

            val updatedProductData = hashMapOf<String, Any>(
                "name" to name,
                "description" to description,
                "price" to priceString.toDouble(),
                "stock" to stockString.toInt(),
                // Gunakan gambar baru jika ada, jika tidak, gunakan gambar lama dari currentProduct
                "imageUrl" to (newImageBase64 ?: productToUpdate.imageUrl)
            )

            firestore.collection("products").document(productToUpdate.id)
                .update(updatedProductData)
                .addOnSuccessListener {
                    Log.d(TAG, "Product successfully updated in Firestore: ${productToUpdate.id}")
                    binding.progressBarEditProduct.visibility = View.GONE
                    Toast.makeText(this, "Produk berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK) // Kirim hasil OK
                    finish() // Menutup EditProductActivity
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating product in Firestore", e)
                    binding.progressBarEditProduct.visibility = View.GONE
                    binding.btnUpdateProduct.isEnabled = true
                    Toast.makeText(this, "Gagal memperbarui produk: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Log.e(TAG, "currentProduct is null, cannot update.")
            Toast.makeText(this, "Data produk asli tidak ditemukan untuk update.", Toast.LENGTH_LONG).show()
        }
    }
    // --- Metode Lifecycle Lainnya (opsional) ---
}