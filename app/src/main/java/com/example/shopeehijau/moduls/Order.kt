// app/src/main/java/com/example/shopeehijau/models/Order.kt
package com.example.shopeehijau.models

import com.google.firebase.Timestamp

data class Order(
    var id: String = "",
    val userId: String = "",
    val items: List<CartItem> = listOf(),
    val totalPrice: Double = 0.0,
    val shippingAddress: String = "",
    val paymentMethod: String = "",
    var status: String = "Pending", // e.g., Pending, Processing, Shipped, Delivered
    val orderDate: Timestamp = Timestamp.now()
)