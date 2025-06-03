// app/src/main/java/com/example/shopeehijau/models/User.kt
package com.example.shopeehijau.models

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    var profileImageUrl: String = "", // Base64 string
    var role: String = "buyer"
)