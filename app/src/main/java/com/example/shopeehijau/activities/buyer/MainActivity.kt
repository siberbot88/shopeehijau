package com.example.shopeehijau.activities.buyer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager // Menggunakan GridLayout untuk tampilan e-commerce
import com.example.shopeehijau.R
import com.example.shopeehijau.activities.auth.LoginActivity
import com.example.shopeehijau.adapters.ProductAdapter // Adapter yang sudah dibuat
import com.example.shopeehijau.databinding.ActivityMainBinding // Pastikan nama binding sesuai dengan file XML (activity_main.xml)
import com.example.shopeehijau.models.Product
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject

class MainActivity : AppCompatActivity() {

    // Binding untuk layout activity_main.xml
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // Adapter dan list untuk RecyclerView produk
    private lateinit var productAdapter: ProductAdapter
    private val productList = mutableListOf<Product>()

    private val TAG = "BuyerMainActivity" // Tag untuk logging

    // Metode onCreate dipanggil saat Activity pertama kali dibuat
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity Dibuat")
        // Inflate layout menggunakan ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.toolbarBuyer)
        supportActionBar?.title = getString(R.string.app_name) // Atau judul kustom

        // Inisialisasi Firebase Auth dan Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Cek apakah pengguna sudah login. Jika tidak, arahkan ke LoginActivity.
        if (auth.currentUser == null) {
            Log.w(TAG, "onCreate: Pengguna belum login, mengarahkan ke LoginActivity.")
            navigateToLogin()
            return // Hentikan eksekusi lebih lanjut di onCreate jika pengguna belum login
        }

        // Setup RecyclerView untuk menampilkan produk
        setupRecyclerView()

        // Memuat produk akan dipanggil di onResume untuk memastikan data selalu terbaru
        // saat Activity kembali aktif.
    }

    // Metode onStart dipanggil setelah onCreate, saat Activity menjadi terlihat oleh pengguna
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Activity Dimulai dan Terlihat")
    }

    // Metode onResume dipanggil saat Activity siap berinteraksi dengan pengguna
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Activity Siap Berinteraksi. Memuat produk.")
        // Pastikan pengguna masih login sebelum memuat produk
        if (auth.currentUser != null) {
            loadProducts()
        } else {
            // Jika pengguna logout saat activity di background, arahkan ke login saat kembali
            Log.w(TAG, "onResume: Pengguna sudah logout, mengarahkan ke LoginActivity.")
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Tutup MainActivity agar tidak bisa kembali dengan tombol back
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView: Mengatur RecyclerView untuk pembeli.")
        // Inisialisasi ProductAdapter
        productAdapter = ProductAdapter(
            context = this,
            productList = productList,
            isAdminView = false, // false karena ini adalah tampilan untuk pembeli
            onEditClick = null, // Pembeli tidak bisa mengedit produk
            onDeleteClick = null, // Pembeli tidak bisa menghapus produk
            onItemClick = { product ->
                // Aksi ketika item produk diklik: pindah ke ProductDetailActivity
                Log.d(TAG, "onItemClick: Produk '${product.name}' diklik.")
                val intent = Intent(this, ProductDetailActivity::class.java)
                // Mengirim objek Product ke Activity lain menggunakan putExtra
                // Product harus mengimplementasikan Parcelable agar bisa dikirim.
                intent.putExtra("PRODUCT_EXTRA", product)
                startActivity(intent) // Memulai ProductDetailActivity
            }
        )

        // Terapkan adapter dan layout manager ke RecyclerView
        binding.rvProductsBuyer.apply {
            // Menggunakan GridLayoutManager untuk tampilan produk dalam beberapa kolom
            layoutManager = GridLayoutManager(this@MainActivity, 2) // 2 kolom
            adapter = productAdapter
        }
    }

    private fun loadProducts() {
        Log.d(TAG, "loadProducts: Memuat daftar produk dari Firestore.")
        // Tampilkan ProgressBar saat memuat
        binding.progressBarBuyerMain.visibility = View.VISIBLE
        binding.tvNoProductsBuyer.visibility = View.GONE // Sembunyikan pesan "tidak ada produk"
        binding.rvProductsBuyer.visibility = View.GONE // Sembunyikan RecyclerView sementara

        firestore.collection("products")
            .whereGreaterThan("stock", 0) // Hanya tampilkan produk yang stoknya lebih dari 0
            .orderBy("stock") // Opsional: urutkan berdasarkan stok atau kriteria lain
            .orderBy("name", Query.Direction.ASCENDING) // Urutkan berdasarkan nama produk
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "loadProducts: Berhasil memuat ${documents.size()} produk.")
                binding.progressBarBuyerMain.visibility = View.GONE // Sembunyikan ProgressBar
                productList.clear() // Bersihkan list produk lama sebelum menambahkan yang baru

                for (document in documents) {
                    try {
                        // Konversi dokumen Firestore ke objek Product
                        val product = document.toObject<Product>().copy(id = document.id)
                        productList.add(product)
                    } catch (e: Exception) {
                        Log.e(TAG, "loadProducts: Error saat mengkonversi dokumen produk: ${document.id}", e)
                    }
                }
                productAdapter.notifyDataSetChanged() // Beri tahu adapter bahwa data telah berubah

                // Tampilkan pesan jika tidak ada produk, atau tampilkan RecyclerView jika ada produk
                if (productList.isEmpty()) {
                    Log.d(TAG, "loadProducts: Tidak ada produk yang tersedia.")
                    binding.tvNoProductsBuyer.visibility = View.VISIBLE
                    binding.rvProductsBuyer.visibility = View.GONE
                } else {
                    binding.tvNoProductsBuyer.visibility = View.GONE
                    binding.rvProductsBuyer.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "loadProducts: Gagal memuat produk.", exception)
                binding.progressBarBuyerMain.visibility = View.GONE // Sembunyikan ProgressBar
                binding.tvNoProductsBuyer.text = "Gagal memuat produk. Coba lagi nanti."
                binding.tvNoProductsBuyer.visibility = View.VISIBLE
                binding.rvProductsBuyer.visibility = View.GONE
                Toast.makeText(this, "Error memuat produk: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Membuat menu di Toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.buyer_main_menu, menu)
        return true
    }

    // Menangani aksi ketika item menu dipilih
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_cart -> {
                Log.d(TAG, "onOptionsItemSelected: Menu Keranjang dipilih.")
                startActivity(Intent(this, CartActivity::class.java))
                true
            }
            R.id.action_profile -> {
                Log.d(TAG, "onOptionsItemSelected: Menu Profil dipilih.")
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            R.id.action_logout_buyer -> {
                Log.d(TAG, "onOptionsItemSelected: Menu Logout dipilih.")
                auth.signOut() // Logout pengguna dari Firebase
                navigateToLogin()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Metode onPause dipanggil saat Activity akan masuk ke background
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Activity Dijeda")
    }

    // Metode onStop dipanggil saat Activity tidak lagi terlihat
    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Activity Dihentikan")
    }

    // Metode onDestroy dipanggil sebelum Activity dihancurkan
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Activity Dihancurkan")
    }
}