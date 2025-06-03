package com.example.shopeehijau.activities.buyer

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.shopeehijau.R
import com.example.shopeehijau.databinding.ActivityProductDetailBinding
import com.example.shopeehijau.models.CartItem
import com.example.shopeehijau.models.Product
import com.example.shopeehijau.utils.ImageHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent // <--- TAMBAHKAN ATAU PASTIKAN ADA
import com.example.shopeehijau.activities.auth.LoginActivity // <--- TAMBAHKAN ATAU PASTIKAN ADA
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import java.text.NumberFormat
import java.util.Locale

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private var currentProduct: Product? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var currentQuantity: Int = 1
    private val TAG = "ProductDetailActivityLifecycle"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Tombol kembali
        supportActionBar?.title = "" // Judul bisa diatur dari CollapsingToolbar atau nama produk

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        currentProduct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("PRODUCT_EXTRA", Product::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("PRODUCT_EXTRA")
        }

        if (currentProduct == null) {
            Log.e(TAG, "Product data is null. Finishing activity.")
            Toast.makeText(this, "Produk tidak ditemukan.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        populateProductDetails()
        setupQuantityControls()
        setupAddToCartButton()
    }

    private fun populateProductDetails() {
        currentProduct?.let { product ->
            Log.d(TAG, "Populating details for product: ${product.name}")
            binding.collapsingToolbarDetail.title = product.name // Judul di CollapsingToolbar
            binding.tvProductNameDetail.text = product.name // Juga di body jika perlu

            val localeID = Locale("in", "ID")
            val currencyFormat = NumberFormat.getCurrencyInstance(localeID)
            currencyFormat.maximumFractionDigits = 0
            binding.tvProductPriceDetail.text = currencyFormat.format(product.price)

            binding.tvProductStockDetail.text = "Stok Tersedia: ${product.stock} buah"
            binding.tvProductDescriptionDetail.text = product.description

            if (product.imageUrl.isNotEmpty()) {
                val bitmap = ImageHelper.decodeBase64ToBitmap(product.imageUrl)
                if (bitmap != null) {
                    binding.ivProductImageDetail.setImageBitmap(bitmap)
                } else {
                    binding.ivProductImageDetail.setImageResource(R.drawable.ic_placeholder_image)
                }
            } else {
                binding.ivProductImageDetail.setImageResource(R.drawable.ic_placeholder_image)
            }

            if (product.stock <= 0) {
                binding.btnAddToCartDetail.isEnabled = false
                binding.btnAddToCartDetail.text = "Stok Habis"
                binding.btnIncreaseQuantityDetail.isEnabled = false
                binding.btnDecreaseQuantityDetail.isEnabled = false
                binding.tvQuantityDetail.text = "0"
            }
        }
    }

    private fun setupQuantityControls() {
        binding.tvQuantityDetail.text = currentQuantity.toString()

        binding.btnIncreaseQuantityDetail.setOnClickListener {
            currentProduct?.let { product ->
                if (currentQuantity < product.stock) { // Batasi dengan stok
                    currentQuantity++
                    binding.tvQuantityDetail.text = currentQuantity.toString()
                } else {
                    Toast.makeText(this, "Kuantitas melebihi stok yang tersedia.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnDecreaseQuantityDetail.setOnClickListener {
            if (currentQuantity > 1) {
                currentQuantity--
                binding.tvQuantityDetail.text = currentQuantity.toString()
            }
        }
    }

    private fun setupAddToCartButton() {
        binding.btnAddToCartDetail.setOnClickListener {
            Log.d(TAG, "Add to cart button clicked.")
            currentProduct?.let { product ->
                if (product.stock <= 0) {
                    Toast.makeText(this, "Maaf, stok produk ini habis.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (currentQuantity > product.stock) {
                    Toast.makeText(this, "Kuantitas yang diminta ($currentQuantity) melebihi stok (${product.stock}).", Toast.LENGTH_LONG).show()
                    currentQuantity = product.stock // Set ke maks stok
                    binding.tvQuantityDetail.text = currentQuantity.toString()
                    if(currentQuantity == 0) return@setOnClickListener // Jika stok jadi 0
                }

                val userId = auth.currentUser?.uid
                if (userId == null) {
                    Toast.makeText(this, "Anda harus login untuk menambahkan ke keranjang.", Toast.LENGTH_SHORT).show()
                    // Arahkan ke LoginActivity
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finishAffinity() // Tutup semua activity buyer sebelumnya
                    return@setOnClickListener
                }
                addProductToCart(userId, product, currentQuantity)
            }
        }
    }

    private fun addProductToCart(userId: String, product: Product, quantity: Int) {
        Log.d(TAG, "Adding product ${product.name} (qty: $quantity) to cart for user $userId.")
        binding.progressBarProductDetail.visibility = View.VISIBLE
        binding.btnAddToCartDetail.isEnabled = false


        val cartItemRef = firestore.collection("carts").document(userId)
            .collection("items").document(product.id)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(cartItemRef)
            val newQuantity: Int
            if (snapshot.exists()) {
                // Item sudah ada di keranjang, update kuantitasnya
                val existingCartItem = snapshot.toObject(CartItem::class.java)
                newQuantity = (existingCartItem?.quantity ?: 0) + quantity
                Log.d(TAG, "Item exists in cart. Old qty: ${existingCartItem?.quantity}, New total qty: $newQuantity")
            } else {
                // Item baru, tambahkan ke keranjang
                newQuantity = quantity
                Log.d(TAG, "Item new to cart. Qty: $newQuantity")
            }

            // Pastikan kuantitas baru tidak melebihi stok produk
            if (newQuantity > product.stock) {
                throw FirebaseFirestoreException("Kuantitas melebihi stok yang tersedia (${product.stock}).",
                    FirebaseFirestoreException.Code.ABORTED)
            }

            val cartItem = CartItem(
                productId = product.id,
                productName = product.name,
                productPrice = product.price,
                quantity = newQuantity,
                productImageBase64 = product.imageUrl // Simpan juga gambar untuk kemudahan di cart
            )
            transaction.set(cartItemRef, cartItem)
            null // Transaksi berhasil
        }.addOnSuccessListener {
            Log.d(TAG, "Product successfully added/updated in cart!")
            binding.progressBarProductDetail.visibility = View.GONE
            binding.btnAddToCartDetail.isEnabled = true
            Toast.makeText(this, "${product.name} (x$quantity) ditambahkan ke keranjang.", Toast.LENGTH_SHORT).show()
            // Opsional: finish() atau tampilkan snackbar dengan tombol ke keranjang
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error adding product to cart", e)
            binding.progressBarProductDetail.visibility = View.GONE
            binding.btnAddToCartDetail.isEnabled = true
            Toast.makeText(this, "Gagal menambahkan ke keranjang: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle tombol kembali di toolbar
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // --- Metode Lifecycle Lainnya ---
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }
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