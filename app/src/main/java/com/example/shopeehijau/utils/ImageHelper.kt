package com.example.shopeehijau.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

object ImageHelper {

    private const val TAG = "ImageHelper"

    fun encodeBitmapToBase64(bitmap: Bitmap, quality: Int = 50): String? {
        return try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            // Kompresi gambar, sesuaikan kualitas (0-100)
            // Gunakan PNG jika ingin lossless tapi ukuran lebih besar, atau WEBP untuk kompresi modern
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding bitmap to Base64", e)
            null
        }
    }

    fun decodeBase64ToBitmap(base64String: String?): Bitmap? {
        if (base64String.isNullOrEmpty()) {
            Log.w(TAG, "Input Base64 string is null or empty.")
            return null
        }
        return try {
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error decoding Base64 string to Bitmap: Invalid Base64 sequence.", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Generic error decoding Base64 string to Bitmap.", e)
            null
        }
    }
}