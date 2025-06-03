package com.example.shopeehijau.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.shopeehijau.databinding.ItemCheckoutSummaryBinding // Pastikan binding ini ada dan sesuai dengan layout item
import com.example.shopeehijau.models.CartItem // Model data untuk item keranjang
import java.text.NumberFormat
import java.util.Locale

class CheckoutSummaryAdapter(
    private val checkoutItemList: List<CartItem>
) : RecyclerView.Adapter<CheckoutSummaryAdapter.CheckoutSummaryViewHolder>() {

    // ViewHolder bertanggung jawab untuk memegang referensi ke view untuk setiap item.
    inner class CheckoutSummaryViewHolder(val binding: ItemCheckoutSummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Fungsi untuk mengikat data CartItem ke view dalam layout item_checkout_summary.xml
        fun bind(cartItem: CartItem) {
            // Menampilkan nama produk dan kuantitasnya
            binding.tvCheckoutItemName.text = "${cartItem.productName} (x${cartItem.quantity})"

            // Menghitung total harga untuk item ini (harga satuan * kuantitas)
            val itemTotalPrice = cartItem.productPrice * cartItem.quantity

            // Format harga ke dalam format mata uang Rupiah
            val localeID = Locale("in", "ID")
            val currencyFormat = NumberFormat.getCurrencyInstance(localeID)
            currencyFormat.maximumFractionDigits = 0 // Tidak menampilkan desimal untuk Rupiah

            // Menampilkan total harga item yang sudah diformat
            binding.tvCheckoutItemPrice.text = currencyFormat.format(itemTotalPrice)
        }
    }

    // Metode ini dipanggil saat ViewHolder baru perlu dibuat.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheckoutSummaryViewHolder {
        // Inflate layout item_checkout_summary.xml menggunakan ViewBinding
        val binding = ItemCheckoutSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CheckoutSummaryViewHolder(binding)
    }

    // Metode ini dipanggil untuk mengikat data ke ViewHolder pada posisi tertentu.
    override fun onBindViewHolder(holder: CheckoutSummaryViewHolder, position: Int) {
        // Dapatkan CartItem pada posisi saat ini
        val cartItem = checkoutItemList[position]
        // Panggil fungsi bind di ViewHolder untuk mengisi view dengan data
        holder.bind(cartItem)
    }

    // Metode ini mengembalikan jumlah total item dalam list.
    override fun getItemCount(): Int {
        return checkoutItemList.size
    }
}