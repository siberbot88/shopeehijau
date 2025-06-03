// app/src/main/java/com/example/shopeehijau/models/Product.kt
package com.example.shopeehijau.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Product(
    var id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    var stock: Int = 0,
    var imageUrl: String = "", // Base64 string
    val sellerId: String = ""
) : Parcelable