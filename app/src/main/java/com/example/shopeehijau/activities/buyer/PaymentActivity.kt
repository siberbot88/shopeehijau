package com.example.shopeehijau.activities.buyer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.shopeehijau.R
import com.example.shopeehijau.activities.auth.LoginActivity
import com.example.shopeehijau.databinding.ActivityPaymentBinding
import com.example.shopeehijau.models.CartItem
import com.example.shopeehijau.models.Order
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import java.text.NumberFormat
import java.util.Locale
import java.util.ArrayList

class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var currentUserId: String? = null
    private var shippingAddress: String? = null
    private var totalPrice: Double = 0.0
    private var cartItemsToOrder: ArrayList<CartItem>? = null
    private val TAG = "PaymentActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarPayment)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            navigateToLogin()
            return
        }

        // Ambil data dari CheckoutActivity
        shippingAddress = intent.getStringExtra("SHIPPING_ADDRESS_EXTRA")
        totalPrice = intent.getDoubleExtra("TOTAL_PRICE_EXTRA", 0.0)
        cartItemsToOrder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("CART_ITEMS_EXTRA", CartItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("CART_ITEMS_EXTRA")
        }


        if (shippingAddress == null || cartItemsToOrder == null || cartItemsToOrder!!.isEmpty()) {
            Log.e(TAG, "Missing data from CheckoutActivity. Finishing.")
            Toast.makeText(this, "Data pesanan tidak lengkap.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        displayPaymentDetails()

        binding.btnConfirmPayment.setOnClickListener {
            confirmAndProcessPayment()
        }
    }
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }


    private fun displayPaymentDetails() {
        val localeID = Locale("in", "ID")
        val currencyFormat = NumberFormat.getCurrencyInstance(localeID)
        currencyFormat.maximumFractionDigits = 0
        binding.tvPaymentTotalAmount.text = currencyFormat.format(totalPrice)
    }

    private fun confirmAndProcessPayment() {
        val selectedPaymentMethodId = binding.rgPaymentMethod.checkedRadioButtonId
        if (selectedPaymentMethodId == -1) {
            Toast.makeText(this, "Silakan pilih metode pembayaran.", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedRadioButton = findViewById<RadioButton>(selectedPaymentMethodId)
        val paymentMethod = selectedRadioButton.text.toString()

        Log.d(TAG, "Processing payment. Method: $paymentMethod, Address: $shippingAddress, Total: $totalPrice")
        binding.progressBarPayment.visibility = View.VISIBLE
        binding.btnConfirmPayment.isEnabled = false

        val orderId = firestore.collection("orders").document().id // Generate ID order baru
        val order = Order(
            id = orderId,
            userId = currentUserId!!,
            items = cartItemsToOrder!!,
            totalPrice = totalPrice,
            shippingAddress = shippingAddress!!,
            paymentMethod = paymentMethod,
            status = "Pending", // Status awal
            orderDate = Timestamp.now()
        )

        // Gunakan Batch Write untuk operasi atomik: simpan order, update stok produk, hapus keranjang
        val batch = firestore.batch()

        // 1. Simpan order baru
        val orderRef = firestore.collection("orders").document(orderId)
        batch.set(orderRef, order)

        // 2. Update stok produk
        for (item in cartItemsToOrder!!) {
            val productRef = firestore.collection("products").document(item.productId)
            // Kurangi stok produk sejumlah kuantitas yang dibeli
            // FieldValue.increment() dengan nilai negatif akan mengurangi
            batch.update(productRef, "stock", FieldValue.increment(-item.quantity.toLong()))
        }

        // 3. Hapus item dari keranjang pengguna
        val cartItemsUserRef = firestore.collection("carts").document(currentUserId!!).collection("items")
        for (item in cartItemsToOrder!!){
            batch.delete(cartItemsUserRef.document(item.productId))
        }
        // Alternatif: Hapus seluruh subcollection "items" jika lebih mudah (tapi ini lebih aman per item)


        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "Order placed successfully and cart cleared. Order ID: $orderId")
                binding.progressBarPayment.visibility = View.GONE
                // btnConfirmPayment biarkan disabled karena proses selesai

                showOrderSuccessDialog(orderId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error placing order", e)
                binding.progressBarPayment.visibility = View.GONE
                binding.btnConfirmPayment.isEnabled = true
                Toast.makeText(this, "Gagal memproses pesanan: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showOrderSuccessDialog(orderId: String) {
        AlertDialog.Builder(this)
            .setTitle("Pesanan Berhasil!")
            .setMessage("Pesanan Anda dengan ID: $orderId telah berhasil dibuat. Terima kasih telah berbelanja!")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // Arahkan kembali ke MainActivity atau halaman riwayat pesanan (jika ada)
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finishAffinity() // Tutup semua activity di atas MainActivity
            }
            .setCancelable(false) // Tidak bisa ditutup dengan tombol back
            .show()
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