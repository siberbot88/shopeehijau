package com.example.shopeehijau.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.shopeehijau.R
import com.example.shopeehijau.databinding.ItemProductAdminBinding
import com.example.shopeehijau.databinding.ItemProductBuyerBinding
import com.example.shopeehijau.models.Product
import com.example.shopeehijau.utils.ImageHelper
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private val context: Context,
    private val productList: List<Product>,
    private val isAdminView: Boolean,
    private val onEditClick: ((Product) -> Unit)?, // Nullable jika tidak untuk admin
    private val onDeleteClick: ((Product) -> Unit)?, // Nullable jika tidak untuk admin
    private val onItemClick: ((Product) -> Unit)? // Untuk buyer, atau detail view admin
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TAG = "ProductAdapter"

    companion object {
        private const val VIEW_TYPE_ADMIN = 1
        private const val VIEW_TYPE_BUYER = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (isAdminView) VIEW_TYPE_ADMIN else VIEW_TYPE_BUYER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_ADMIN) {
            val binding = ItemProductAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AdminViewHolder(binding)
        } else {
            val binding = ItemProductBuyerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            BuyerViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val product = productList[position]
        val localeID = Locale("in", "ID")
        val currencyFormat = NumberFormat.getCurrencyInstance(localeID)
        currencyFormat.maximumFractionDigits = 0 // Tidak menampilkan desimal untuk Rupiah

        if (holder is AdminViewHolder) {
            holder.binding.tvProductNameItemAdmin.text = product.name
            holder.binding.tvProductPriceItemAdmin.text = currencyFormat.format(product.price)
            holder.binding.tvProductStockItemAdmin.text = "Stok: ${product.stock}"

            if (product.imageUrl.isNotEmpty()) {
                val bitmap = ImageHelper.decodeBase64ToBitmap(product.imageUrl)
                if (bitmap != null) {
                    holder.binding.ivProductImageItemAdmin.setImageBitmap(bitmap)
                } else {
                    holder.binding.ivProductImageItemAdmin.setImageResource(R.drawable.ic_placeholder_image)
                    Log.w(TAG, "Admin View: Failed to decode Base64 image for ${product.name}")
                }
            } else {
                holder.binding.ivProductImageItemAdmin.setImageResource(R.drawable.ic_placeholder_image)
            }

            holder.binding.btnEditProductItemAdmin.setOnClickListener {
                onEditClick?.invoke(product)
            }
            holder.binding.btnDeleteProductItemAdmin.setOnClickListener {
                onDeleteClick?.invoke(product)
            }
            // Opsional: klik item untuk admin bisa ke detail view juga
            holder.itemView.setOnClickListener {
                onItemClick?.invoke(product)
            }

        } else if (holder is BuyerViewHolder) {
            holder.binding.tvProductNameBuyer.text = product.name
            holder.binding.tvProductPriceBuyer.text = currencyFormat.format(product.price)

            if (product.imageUrl.isNotEmpty()) {
                val bitmap = ImageHelper.decodeBase64ToBitmap(product.imageUrl)
                if (bitmap != null) {
                    holder.binding.ivProductImageBuyer.setImageBitmap(bitmap)
                } else {
                    holder.binding.ivProductImageBuyer.setImageResource(R.drawable.ic_placeholder_image)
                    Log.w(TAG, "Buyer View: Failed to decode Base64 image for ${product.name}")
                }
            } else {
                holder.binding.ivProductImageBuyer.setImageResource(R.drawable.ic_placeholder_image)
            }

            holder.itemView.setOnClickListener {
                onItemClick?.invoke(product)
            }
        }
    }

    override fun getItemCount(): Int = productList.size

    class AdminViewHolder(val binding: ItemProductAdminBinding) : RecyclerView.ViewHolder(binding.root)
    class BuyerViewHolder(val binding: ItemProductBuyerBinding) : RecyclerView.ViewHolder(binding.root)
}