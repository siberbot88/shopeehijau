package com.example.shopeehijau.activities.buyer

import android.app.Activity // Dibutuhkan untuk setResult jika ingin mengembalikan hasil
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.shopeehijau.R
import com.example.shopeehijau.activities.auth.LoginActivity
import com.example.shopeehijau.databinding.ActivityProductDetailBinding
import com.example.shopeehijau.models.CartItem
import com.example.shopeehijau.models.Product
import com.example.shopeehijau.utils.ImageHelper
import com.google.android.material.snackbar.Snackbar // Import Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.NumberFormat
import java.util.Locale

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private var currentProduct: Product? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var currentQuantity: Int = 1
    private val TAG = "ProductDetailActivity" // Lebih baik TAG unik per kelas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity Dibuat")
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "" // Judul akan diisi oleh CollapsingToolbar

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val productId = intent.getStringExtra("PRODUCT_ID_EXTRA")

        if (productId == null) {
            Log.e(TAG, "onCreate: Product ID tidak ditemukan dalam Intent. Menutup activity.")
            Toast.makeText(this, "ID Produk tidak valid.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Memuat data produk dari Firestore berdasarkan ID
        loadProductFromFirestore(productId)
    }

    private fun loadProductFromFirestore(productId: String) {
        Log.d(TAG, "loadProductFromFirestore: Memuat produk dengan ID: $productId")
        binding.progressBarProductDetail.visibility = View.VISIBLE
        // Sembunyikan konten utama saat loading
        binding.appBarDetail.visibility = View.INVISIBLE
        binding.productDetailContentContainer.visibility = View.INVISIBLE // Buat ID ini untuk NestedScrollView/LinearLayout konten
        binding.productDetailActionContainer.visibility = View.INVISIBLE // Buat ID ini untuk LinearLayout tombol

        firestore.collection("products").document(productId)
            .get()
            .addOnSuccessListener { document ->
                binding.progressBarProductDetail.visibility = View.GONE
                if (document != null && document.exists()) {
                    currentProduct = document.toObject(Product::class.java)?.copy(id = document.id)
                    if (currentProduct == null) {
                        Log.e(TAG, "loadProductFromFirestore: Gagal mengkonversi dokumen ke objek Product. Menutup activity.")
                        showErrorAndFinish("Gagal memuat detail produk.")
                        return@addOnSuccessListener
                    }
                    Log.d(TAG, "loadProductFromFirestore: Produk dimuat dari Firestore: ${currentProduct?.name}")
                    // Panggil fungsi setup UI SETELAH currentProduct berhasil di-populate
                    populateProductDetails()
                    setupQuantityControls()
                    setupAddToCartButton()
                    // Tampilkan konten setelah data siap
                    binding.appBarDetail.visibility = View.VISIBLE
                    binding.productDetailContentContainer.visibility = View.VISIBLE
                    binding.productDetailActionContainer.visibility = View.VISIBLE
                } else {
                    Log.e(TAG, "loadProductFromFirestore: Dokumen produk tidak ditemukan di Firestore dengan ID: $productId. Menutup activity.")
                    showErrorAndFinish("Detail produk tidak ditemukan.")
                }
            }
            .addOnFailureListener { exception ->
                binding.progressBarProductDetail.visibility = View.GONE
                Log.e(TAG, "loadProductFromFirestore: Error memuat produk dari Firestore", exception)
                showErrorAndFinish("Gagal memuat detail: ${exception.message}")
            }
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        // Sembunyikan semua view utama jika error dan akan finish
        binding.appBarDetail.visibility = View.INVISIBLE
        binding.productDetailContentContainer.visibility = View.INVISIBLE
        binding.productDetailActionContainer.visibility = View.INVISIBLE
        // Mungkin tampilkan pesan error di tengah layar jika diinginkan sebelum finish
        // binding.tvErrorMessage.text = message
        // binding.tvErrorMessage.visibility = View.VISIBLE
        finish()
    }


    private fun populateProductDetails() {
        currentProduct?.let { product ->
            Log.d(TAG, "populateProductDetails: Menampilkan detail untuk produk: ${product.name}")
            binding.collapsingToolbarDetail.title = product.name
            binding.tvProductNameDetail.text = product.name

            val localeID = Locale("in", "ID")
            val currencyFormat = NumberFormat.getCurrencyInstance(localeID)
            currencyFormat.maximumFractionDigits = 0
            binding.tvProductPriceDetail.text = currencyFormat.format(product.price)

            binding.tvProductStockDetail.text = "Stok: ${product.stock}"
            binding.tvProductDescriptionDetail.text = product.description

            if (product.imageUrl.isNotEmpty()) {
                val bitmap = ImageHelper.decodeBase64ToBitmap(product.imageUrl)
                if (bitmap != null) {
                    binding.ivProductImageDetail.setImageBitmap(bitmap)
                } else {
                    Log.w(TAG, "populateProductDetails: Gagal decode Base64 untuk gambar produk ${product.name}")
                    binding.ivProductImageDetail.setImageResource(R.drawable.ic_placeholder_image)
                }
            } else {
                Log.d(TAG, "populateProductDetails: Tidak ada imageUrl untuk produk ${product.name}")
                binding.ivProductImageDetail.setImageResource(R.drawable.ic_placeholder_image)
            }

            // Update status tombol berdasarkan stok
            updateButtonAndQuantityState(product.stock)
        } ?: Log.e(TAG, "populateProductDetails: currentProduct null, tidak bisa menampilkan detail.")
    }

    private fun updateButtonAndQuantityState(stock: Int) {
        if (stock <= 0) {
            binding.btnAddToCartDetail.isEnabled = false
            binding.btnAddToCartDetail.text = "Stok Habis"
            binding.btnIncreaseQuantityDetail.isEnabled = false
            binding.btnDecreaseQuantityDetail.isEnabled = false
            binding.tvQuantityDetail.text = "0"
            currentQuantity = 0 // Set kuantitas ke 0 jika stok habis
        } else {
            binding.btnAddToCartDetail.isEnabled = true
            binding.btnAddToCartDetail.text = "Tambah ke Keranjang" // Kembalikan teks tombol
            binding.btnIncreaseQuantityDetail.isEnabled = true
            binding.btnDecreaseQuantityDetail.isEnabled = currentQuantity > 1
            // Pastikan currentQuantity tidak melebihi stok baru jika ada perubahan stok drastis
            if (currentQuantity > stock) currentQuantity = stock
            if (currentQuantity < 1 && stock > 0) currentQuantity = 1 // Jika stok ada, minimal 1
            binding.tvQuantityDetail.text = currentQuantity.toString()
        }
    }


    private fun setupQuantityControls() {
        binding.tvQuantityDetail.text = currentQuantity.toString()
        // Pastikan tombol decrease dinonaktifkan jika kuantitas awal adalah 1
        binding.btnDecreaseQuantityDetail.isEnabled = currentQuantity > 1

        binding.btnIncreaseQuantityDetail.setOnClickListener {
            currentProduct?.let { product ->
                if (currentQuantity < product.stock) {
                    currentQuantity++
                    binding.tvQuantityDetail.text = currentQuantity.toString()
                    binding.btnDecreaseQuantityDetail.isEnabled = true // Aktifkan tombol kurang
                } else {
                    Toast.makeText(this, "Kuantitas melebihi stok yang tersedia.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnDecreaseQuantityDetail.setOnClickListener {
            if (currentQuantity > 1) {
                currentQuantity--
                binding.tvQuantityDetail.text = currentQuantity.toString()
                if (currentQuantity == 1) {
                    binding.btnDecreaseQuantityDetail.isEnabled = false // Nonaktifkan jika kuantitas 1
                }
            }
        }
    }

    private fun setupAddToCartButton() {
        binding.btnAddToCartDetail.setOnClickListener {
            Log.d(TAG, "setupAddToCartButton: Tombol 'Tambah ke Keranjang' diklik.")
            currentProduct?.let { product ->
                if (product.stock <= 0 || currentQuantity == 0) {
                    Toast.makeText(this, "Maaf, stok produk ini habis atau kuantitas 0.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (currentQuantity > product.stock) {
                    Toast.makeText(this, "Kuantitas yang diminta ($currentQuantity) melebihi stok (${product.stock}). Harap sesuaikan.", Toast.LENGTH_LONG).show()
                    // Opsional: otomatis set ke stok maksimal
                    // currentQuantity = product.stock
                    // binding.tvQuantityDetail.text = currentQuantity.toString()
                    return@setOnClickListener
                }

                val userId = auth.currentUser?.uid
                if (userId == null) {
                    Log.w(TAG, "setupAddToCartButton: Pengguna belum login. Mengarahkan ke LoginActivity.")
                    Toast.makeText(this, "Anda harus login untuk menambahkan ke keranjang.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finishAffinity()
                    return@setOnClickListener
                }
                addProductToCart(userId, product, currentQuantity)
            } ?: Log.e(TAG, "setupAddToCartButton: currentProduct null, tidak bisa menambah ke keranjang.")
        }
    }

    private fun addProductToCart(userId: String, product: Product, quantity: Int) {
        if (product.id.isEmpty()) {
            Log.e(TAG, "addProductToCart: Product ID kosong, tidak bisa menambah ke keranjang.")
            Toast.makeText(this, "ID Produk tidak valid.", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "addProductToCart: Menambahkan produk ${product.name} (qty: $quantity) ke keranjang untuk user $userId.")
        binding.progressBarProductDetail.visibility = View.VISIBLE
        binding.btnAddToCartDetail.isEnabled = false


        val cartItemRef = firestore.collection("carts").document(userId)
            .collection("items").document(product.id) // Gunakan product.id sebagai ID dokumen item keranjang

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(cartItemRef)
            val newQuantity: Int
            if (snapshot.exists()) {
                val existingCartItem = snapshot.toObject(CartItem::class.java)
                newQuantity = (existingCartItem?.quantity ?: 0) + quantity
                Log.d(TAG, "addProductToCart: Item sudah ada. Kuantitas lama: ${existingCartItem?.quantity}, Total kuantitas baru: $newQuantity")
            } else {
                newQuantity = quantity
                Log.d(TAG, "addProductToCart: Item baru. Kuantitas: $newQuantity")
            }

            val productSnapshot = transaction.get(firestore.collection("products").document(product.id))
            val currentStock = productSnapshot.getLong("stock")?.toInt() ?: 0

            if (newQuantity > currentStock) {
                Log.w(TAG, "addProductToCart: Kuantitas baru ($newQuantity) melebihi stok saat ini ($currentStock).")
                throw FirebaseFirestoreException(
                    "Kuantitas ($newQuantity) melebihi stok yang tersedia ($currentStock).",
                    FirebaseFirestoreException.Code.ABORTED
                )
            }

            val cartItem = CartItem(
                productId = product.id,
                productName = product.name,
                productPrice = product.price,
                quantity = newQuantity,
                productImageBase64 = product.imageUrl
            )
            transaction.set(cartItemRef, cartItem)
            null
        }.addOnSuccessListener {
            Log.d(TAG, "addProductToCart: Produk berhasil ditambahkan/diupdate di keranjang!")
            binding.progressBarProductDetail.visibility = View.GONE
            binding.btnAddToCartDetail.isEnabled = true

            // Feedback Tambahan: Snackbar dengan aksi "Lihat Keranjang"
            Snackbar.make(binding.root, "${product.name} (x$quantity) ditambahkan", Snackbar.LENGTH_LONG)
                .setAction("Lihat Keranjang") {
                    Log.d(TAG, "addProductToCart: Aksi 'Lihat Keranjang' dipilih dari Snackbar.")
                    val cartIntent = Intent(this, CartActivity::class.java)
                    startActivity(cartIntent)
                }
                .show()
            // Opsional: setResult(Activity.RESULT_OK) jika MainActivity perlu tahu ada perubahan keranjang

        }.addOnFailureListener { e ->
            Log.e(TAG, "addProductToCart: Error menambahkan produk ke keranjang", e)
            binding.progressBarProductDetail.visibility = View.GONE
            binding.btnAddToCartDetail.isEnabled = true
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // --- Metode Lifecycle ---
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // Jika ada kemungkinan data produk (misal stok) berubah saat user di activity lain
        // dan kembali ke sini, Anda mungkin ingin memuat ulang data produk.
        // Namun, jika hanya ID yang dikirim, pemuatan di onCreate sudah cukup.
        // currentProduct?.id?.let { loadProductFromFirestore(it) } // Contoh jika ingin refresh
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