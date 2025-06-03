package com.example.shopeehijau.activities.buyer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shopeehijau.R
import com.example.shopeehijau.activities.auth.LoginActivity
import com.example.shopeehijau.adapters.CartAdapter // Anda akan membuat adapter ini
import com.example.shopeehijau.databinding.ActivityCartBinding
import com.example.shopeehijau.models.CartItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import java.text.NumberFormat
import java.util.Locale

class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var cartAdapter: CartAdapter
    private val cartItemList = mutableListOf<CartItem>()
    private val TAG = "CartActivityLifecycle"
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarCart)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.w(TAG, "User not logged in. Redirecting to Login.")
            navigateToLogin()
            return
        }

        setupRecyclerView()
        loadCartItems()

        binding.btnCheckout.setOnClickListener {
            if (cartItemList.isEmpty()) {
                Toast.makeText(this, "Keranjang Anda kosong. Tidak bisa checkout.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "Checkout button clicked.")
            val intent = Intent(this, CheckoutActivity::class.java)
            // Anda bisa mengirim total harga atau daftar item jika diperlukan oleh CheckoutActivity
            // Untuk sekarang, CheckoutActivity akan mengambil ulang data keranjang.
            startActivity(intent)
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView for cart items.")
        cartAdapter = CartAdapter(
            cartItemList,
            onIncreaseQuantity = { cartItem ->
                updateCartItemQuantity(cartItem, cartItem.quantity + 1)
            },
            onDecreaseQuantity = { cartItem ->
                if (cartItem.quantity > 1) {
                    updateCartItemQuantity(cartItem, cartItem.quantity - 1)
                } else {
                    // Jika kuantitas jadi 0 atau kurang, konfirmasi hapus
                    confirmRemoveItem(cartItem)
                }
            },
            onRemoveItem = { cartItem ->
                confirmRemoveItem(cartItem)
            }
        )
        binding.rvCartItems.apply {
            layoutManager = LinearLayoutManager(this@CartActivity)
            adapter = cartAdapter
        }
    }

    private fun loadCartItems() {
        currentUserId?.let { userId ->
            Log.d(TAG, "Loading cart items for user: $userId")
            binding.progressBarCart.visibility = View.VISIBLE
            binding.tvEmptyCart.visibility = View.GONE
            binding.rvCartItems.visibility = View.GONE

            firestore.collection("carts").document(userId).collection("items")
                .get()
                .addOnSuccessListener { documents ->
                    Log.d(TAG, "Cart items loaded successfully. Count: ${documents.size()}")
                    binding.progressBarCart.visibility = View.GONE
                    cartItemList.clear()
                    if (documents.isEmpty) {
                        binding.tvEmptyCart.visibility = View.VISIBLE
                        binding.rvCartItems.visibility = View.GONE
                    } else {
                        for (document in documents) {
                            try {
                                val cartItem = document.toObject<CartItem>()
                                cartItemList.add(cartItem)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error converting cart item document: ${document.id}", e)
                            }
                        }
                        binding.tvEmptyCart.visibility = View.GONE
                        binding.rvCartItems.visibility = View.VISIBLE
                    }
                    cartAdapter.notifyDataSetChanged()
                    updateTotalPrice()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error loading cart items: ", exception)
                    binding.progressBarCart.visibility = View.GONE
                    binding.tvEmptyCart.text = "Gagal memuat keranjang."
                    binding.tvEmptyCart.visibility = View.VISIBLE
                    binding.rvCartItems.visibility = View.GONE
                    Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        } ?: Log.e(TAG, "User ID is null, cannot load cart items.")
    }

    private fun updateCartItemQuantity(cartItem: CartItem, newQuantity: Int) {
        currentUserId?.let { userId ->
            Log.d(TAG, "Updating quantity for item ${cartItem.productId} to $newQuantity")
            binding.progressBarCart.visibility = View.VISIBLE // Tampilkan loading singkat

            val itemRef = firestore.collection("carts").document(userId)
                .collection("items").document(cartItem.productId)

            // Ambil data produk asli untuk cek stok
            firestore.collection("products").document(cartItem.productId).get()
                .addOnSuccessListener { productSnapshot ->
                    val productStock = productSnapshot.getLong("stock")?.toInt() ?: 0
                    if (newQuantity > productStock) {
                        binding.progressBarCart.visibility = View.GONE
                        Toast.makeText(this, "Kuantitas melebihi stok produk (${productStock}).", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    itemRef.update("quantity", newQuantity)
                        .addOnSuccessListener {
                            Log.d(TAG, "Cart item quantity updated.")
                            // Update UI secara lokal atau panggil loadCartItems() lagi
                            val index = cartItemList.indexOfFirst { it.productId == cartItem.productId }
                            if (index != -1) {
                                cartItemList[index].quantity = newQuantity
                                cartAdapter.notifyItemChanged(index)
                                updateTotalPrice()
                            }
                            binding.progressBarCart.visibility = View.GONE
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error updating cart item quantity", e)
                            binding.progressBarCart.visibility = View.GONE
                            Toast.makeText(this, "Gagal update kuantitas: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to fetch product stock for update", e)
                    binding.progressBarCart.visibility = View.GONE
                    Toast.makeText(this, "Gagal cek stok produk.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun confirmRemoveItem(cartItem: CartItem) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Item")
            .setMessage("Yakin ingin menghapus '${cartItem.productName}' dari keranjang?")
            .setIcon(R.drawable.ic_delete_24)
            .setPositiveButton("Ya, Hapus") { _, _ ->
                removeItemFromCart(cartItem)
            }
            .setNegativeButton("Batal", null)
            .show()
    }


    private fun removeItemFromCart(cartItem: CartItem) {
        currentUserId?.let { userId ->
            Log.d(TAG, "Removing item ${cartItem.productId} from cart.")
            binding.progressBarCart.visibility = View.VISIBLE // Tampilkan loading singkat

            firestore.collection("carts").document(userId)
                .collection("items").document(cartItem.productId)
                .delete()
                .addOnSuccessListener {
                    Log.d(TAG, "Cart item removed successfully.")
                    binding.progressBarCart.visibility = View.GONE
                    Toast.makeText(this, "${cartItem.productName} dihapus dari keranjang.", Toast.LENGTH_SHORT).show()
                    // Hapus dari list lokal dan update adapter
                    cartItemList.removeAll { it.productId == cartItem.productId }
                    cartAdapter.notifyDataSetChanged()
                    updateTotalPrice()
                    if (cartItemList.isEmpty()) {
                        binding.tvEmptyCart.visibility = View.VISIBLE
                        binding.rvCartItems.visibility = View.GONE
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error removing cart item", e)
                    binding.progressBarCart.visibility = View.GONE
                    Toast.makeText(this, "Gagal menghapus item: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateTotalPrice() {
        var totalPrice = 0.0
        for (item in cartItemList) {
            totalPrice += item.productPrice * item.quantity
        }
        val localeID = Locale("in", "ID")
        val currencyFormat = NumberFormat.getCurrencyInstance(localeID)
        currencyFormat.maximumFractionDigits = 0
        binding.tvTotalPriceCart.text = currencyFormat.format(totalPrice)
        Log.d(TAG, "Total price updated: $totalPrice")

        // Aktifkan/nonaktifkan tombol checkout berdasarkan apakah keranjang kosong
        binding.btnCheckout.isEnabled = cartItemList.isNotEmpty()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Reloading cart items.")
        // Reload items saat kembali ke activity ini, untuk memastikan data keranjang terbaru
        if (auth.currentUser != null) {
            loadCartItems()
        } else {
            navigateToLogin()
        }
    }
    // ... Lifecycle methods lainnya ...
}