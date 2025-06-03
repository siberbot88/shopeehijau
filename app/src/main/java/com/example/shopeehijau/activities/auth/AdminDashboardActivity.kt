package com.example.shopeehijau.activities.admin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shopeehijau.R
import com.example.shopeehijau.activities.auth.LoginActivity
import com.example.shopeehijau.adapters.ProductAdapter // Anda akan membuat adapter ini
import com.example.shopeehijau.databinding.ActivityAdminDashboardBinding
import com.example.shopeehijau.models.Product
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var productAdapter: ProductAdapter
    private val productList = mutableListOf<Product>()
    private val TAG = "AdminDashboardLifecycle"

    // Launcher untuk hasil dari AddProductActivity atau EditProductActivity
    // Mirip dengan resultLauncher di MODUL 5 2025.pdf
    private val productActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Jika ada data yang diubah (misalnya produk baru ditambahkan atau diedit),
            // muat ulang daftar produk
            Log.d(TAG, "Result OK from Add/Edit Product. Reloading products.")
            loadProducts()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarAdmin)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            // Jika tidak ada user yang login, kembali ke LoginActivity
            Log.w(TAG, "User not logged in, redirecting to LoginActivity.")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return // Hindari eksekusi kode lebih lanjut jika user tidak login
        }

        setupRecyclerView()

        binding.fabAddProduct.setOnClickListener {
            Log.d(TAG, "FAB clicked, navigating to AddProductActivity.")
            // Explicit Intent untuk pindah ke AddProductActivity
            val intent = Intent(this, AddProductActivity::class.java)
            productActivityResultLauncher.launch(intent) // Gunakan launcher untuk mendapatkan hasil
        }

        // loadProducts() dipanggil di onResume untuk memastikan data selalu fresh
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView.")
        // Definisikan aksi untuk tombol edit dan delete di adapter
        productAdapter = ProductAdapter(
            this, // context
            productList,
            isAdminView = true, // Flag untuk membedakan tampilan/aksi admin
            onEditClick = { product ->
                Log.d(TAG, "Edit product clicked: ${product.name}")
                val intent = Intent(this, EditProductActivity::class.java)
                intent.putExtra("PRODUCT_EXTRA", product) // Kirim data produk (Product harus Parcelable)
                productActivityResultLauncher.launch(intent)
            },
            onDeleteClick = { product ->
                Log.d(TAG, "Delete product clicked: ${product.name}")
                confirmDeleteProduct(product)
            },
            onItemClick = {
                // Admin mungkin tidak perlu aksi klik item di dashboard,
                // atau bisa juga untuk melihat detail (opsional)
            }
        )
        binding.rvProductsAdmin.apply {
            layoutManager = LinearLayoutManager(this@AdminDashboardActivity)
            adapter = productAdapter
        }
    }

    private fun loadProducts() {
        Log.d(TAG, "Loading products from Firestore.")
        binding.progressBarAdminDashboard.visibility = View.VISIBLE
        binding.tvNoProductsAdmin.visibility = View.GONE
        binding.rvProductsAdmin.visibility = View.GONE

        // Ambil semua produk (atau bisa difilter berdasarkan sellerId jika perlu)
        firestore.collection("products")
            .orderBy("name", Query.Direction.ASCENDING) // Urutkan berdasarkan nama
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Products loaded successfully. Count: ${documents.size()}")
                binding.progressBarAdminDashboard.visibility = View.GONE
                productList.clear()
                for (document in documents) {
                    try {
                        val product = document.toObject<Product>().copy(id = document.id)
                        productList.add(product)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting product document: ${document.id}", e)
                    }
                }
                productAdapter.notifyDataSetChanged()

                if (productList.isEmpty()) {
                    Log.d(TAG, "No products found.")
                    binding.tvNoProductsAdmin.visibility = View.VISIBLE
                    binding.rvProductsAdmin.visibility = View.GONE
                } else {
                    binding.tvNoProductsAdmin.visibility = View.GONE
                    binding.rvProductsAdmin.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error getting products: ", exception)
                binding.progressBarAdminDashboard.visibility = View.GONE
                binding.tvNoProductsAdmin.text = "Gagal memuat produk."
                binding.tvNoProductsAdmin.visibility = View.VISIBLE
                binding.rvProductsAdmin.visibility = View.GONE
                Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmDeleteProduct(product: Product) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Produk")
            .setMessage("Apakah Anda yakin ingin menghapus produk '${product.name}'?")
            .setIcon(R.drawable.ic_delete_24) // Ganti dengan ikon warning jika ada
            .setPositiveButton("Ya, Hapus") { _, _ ->
                deleteProductFromFirestore(product)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteProductFromFirestore(product: Product) {
        if (product.id.isEmpty()) {
            Log.e(TAG, "Product ID is empty, cannot delete.")
            Toast.makeText(this, "ID Produk tidak valid.", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Deleting product from Firestore: ${product.id}")
        binding.progressBarAdminDashboard.visibility = View.VISIBLE // Tampilkan loading

        firestore.collection("products").document(product.id)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Product successfully deleted from Firestore!")
                binding.progressBarAdminDashboard.visibility = View.GONE
                Toast.makeText(this, "Produk '${product.name}' berhasil dihapus.", Toast.LENGTH_SHORT).show()
                loadProducts() // Muat ulang daftar produk
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error deleting product from Firestore", e)
                binding.progressBarAdminDashboard.visibility = View.GONE
                Toast.makeText(this, "Gagal menghapus produk: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.admin_dashboard_menu, menu) // Buat file menu: res/menu/admin_dashboard_menu.xml
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout_admin -> {
                Log.d(TAG, "Logout action selected.")
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- Metode Lifecycle Lainnya ---
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume. Loading products.")
        // Muat ulang produk saat activity kembali aktif untuk memastikan data terbaru
        if (auth.currentUser != null) { // Pastikan user masih login
            loadProducts()
        } else { // Jika user somehow logout, redirect
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
    // ... (onPause, onStop, onDestroy dengan Log.d seperti di LoginActivity)
}