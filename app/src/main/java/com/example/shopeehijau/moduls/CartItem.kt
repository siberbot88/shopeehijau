// app/src/main/java/com/example/shopeehijau/models/CartItem.kt
package com.example.shopeehijau.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CartItem(
    var productId: String = "",
    var productName: String = "", // Denormalisasi untuk kemudahan tampilan
    var productPrice: Double = 0.0, // Denormalisasi
    var quantity: Int = 1,
    var productImageBase64: String = "" // Denormalisasi
) : Parcelable