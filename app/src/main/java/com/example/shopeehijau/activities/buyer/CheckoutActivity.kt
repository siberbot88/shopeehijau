package com.example.shopeehijau.activities.buyer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shopeehijau.activities.auth.LoginActivity
import com.example.shopeehijau.adapters.CheckoutSummaryAdapter // Buat adapter ini
import com.example.shopeehijau.databinding.ActivityCheckoutBinding
import com.example.shopeehijau.models.CartItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import java.text.NumberFormat
import java.util.Locale
import java.util.ArrayList // Untuk mengirim list ke intent berikutnya

class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var currentUserId: String? = null
    private val checkoutItemList = mutableListOf<CartItem>()
    private lateinit var checkoutSummaryAdapter: CheckoutSummaryAdapter
    private var grandTotalPrice: Double = 0.0
    private val TAG = "CheckoutActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarCheckout)
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
        loadCheckoutSummary()

        binding.btnProceedToPayment.setOnClickListener {
            val shippingAddress = binding.etShippingAddress.text.toString().trim()
            if (shippingAddress.isEmpty()) {
                binding.tilShippingAddress.error = "Alamat pengiriman tidak boleh kosong."
                Toast.makeText(this, "Mohon isi alamat pengiriman.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.tilShippingAddress.error = null // Clear error

            if(checkoutItemList.isEmpty()){
                Toast.makeText(this, "Keranjang kosong, tidak bisa melanjutkan.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d(TAG, "Proceed to payment clicked. Address: $shippingAddress")
            val intent = Intent(this, PaymentActivity::class.java)
            intent.putExtra("SHIPPING_ADDRESS_EXTRA", shippingAddress)
            intent.putExtra("TOTAL_PRICE_EXTRA", grandTotalPrice)
            // Mengirim list Parcelable
            intent.putParcelableArrayListExtra("CART_ITEMS_EXTRA", ArrayList(checkoutItemList))
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
        checkoutSummaryAdapter = CheckoutSummaryAdapter(checkoutItemList)
        binding.rvCheckoutItemsSummary.apply {
            layoutManager = LinearLayoutManager(this@CheckoutActivity)
            adapter = checkoutSummaryAdapter
            // isNestedScrollingEnabled = false // Jika di dalam ScrollView, agar tidak ada konflik scroll
        }
    }

    private fun loadCheckoutSummary() {
        currentUserId?.let { userId ->
            Log.d(TAG, "Loading checkout summary for user: $userId")
            binding.progressBarCheckout.visibility = View.VISIBLE
            binding.tvNoItemsCheckout.visibility = View.GONE

            firestore.collection("carts").document(userId).collection("items")
                .get()
                .addOnSuccessListener { documents ->
                    Log.d(TAG, "Checkout summary loaded. Count: ${documents.size()}")
                    binding.progressBarCheckout.visibility = View.GONE
                    checkoutItemList.clear()
                    if (documents.isEmpty) {
                        binding.tvNoItemsCheckout.visibility = View.VISIBLE
                        binding.rvCheckoutItemsSummary.visibility = View.GONE
                        binding.btnProceedToPayment.isEnabled = false // Nonaktifkan tombol jika kosong
                        updateTotalPriceUI(0.0)
                    } else {
                        var calculatedTotalPrice = 0.0
                        for (document in documents) {
                            try {
                                val cartItem = document.toObject<CartItem>()
                                checkoutItemList.add(cartItem)
                                calculatedTotalPrice += cartItem.productPrice * cartItem.quantity
                            } catch (e: Exception) {
                                Log.e(TAG, "Error converting cart item for summary: ${document.id}", e)
                            }
                        }
                        grandTotalPrice = calculatedTotalPrice // Simpan total harga
                        updateTotalPriceUI(grandTotalPrice)
                        binding.rvCheckoutItemsSummary.visibility = View.VISIBLE
                        binding.btnProceedToPayment.isEnabled = true
                    }
                    checkoutSummaryAdapter.notifyDataSetChanged()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error loading checkout summary: ", exception)
                    binding.progressBarCheckout.visibility = View.GONE
                    binding.tvNoItemsCheckout.text = "Gagal memuat ringkasan."
                    binding.tvNoItemsCheckout.visibility = View.VISIBLE
                    binding.btnProceedToPayment.isEnabled = false
                    Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun updateTotalPriceUI(total: Double) {
        val localeID = Locale("in", "ID")
        val currencyFormat = NumberFormat.getCurrencyInstance(localeID)
        currencyFormat.maximumFractionDigits = 0
        binding.tvTotalPriceCheckout.text = currencyFormat.format(total)
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