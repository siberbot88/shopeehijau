package com.example.shopeehijau.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.shopeehijau.R
import com.example.shopeehijau.databinding.ItemCartBinding
import com.example.shopeehijau.models.CartItem
import com.example.shopeehijau.utils.ImageHelper
import java.text.NumberFormat
import java.util.Locale
import android.util.Log


class CartAdapter(
    private val cartItemList: List<CartItem>,
    private val onIncreaseQuantity: (CartItem) -> Unit,
    private val onDecreaseQuantity: (CartItem) -> Unit,
    private val onRemoveItem: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val TAG = "CartAdapter"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val cartItem = cartItemList[position]
        holder.bind(cartItem)
    }

    override fun getItemCount(): Int = cartItemList.size

    inner class CartViewHolder(private val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cartItem: CartItem) {
            binding.tvCartItemName.text = cartItem.productName
            binding.tvCartItemQuantity.text = cartItem.quantity.toString()

            val localeID = Locale("in", "ID")
            val currencyFormat = NumberFormat.getCurrencyInstance(localeID)
            currencyFormat.maximumFractionDigits = 0
            val pricePerItemFormatted = currencyFormat.format(cartItem.productPrice)
            binding.tvCartItemPrice.text = "$pricePerItemFormatted x ${cartItem.quantity}"


            if (cartItem.productImageBase64.isNotEmpty()) {
                val bitmap = ImageHelper.decodeBase64ToBitmap(cartItem.productImageBase64)
                if (bitmap != null) {
                    binding.ivCartItemImage.setImageBitmap(bitmap)
                } else {
                    binding.ivCartItemImage.setImageResource(R.drawable.ic_placeholder_image)
                    Log.w(TAG, "Failed to decode Base64 image for cart item: ${cartItem.productName}")
                }
            } else {
                binding.ivCartItemImage.setImageResource(R.drawable.ic_placeholder_image)
            }

            binding.btnIncreaseQuantityCart.setOnClickListener {
                onIncreaseQuantity(cartItem)
            }
            binding.btnDecreaseQuantityCart.setOnClickListener {
                onDecreaseQuantity(cartItem)
            }
            binding.btnRemoveFromCart.setOnClickListener {
                onRemoveItem(cartItem)
            }
        }
    }
}