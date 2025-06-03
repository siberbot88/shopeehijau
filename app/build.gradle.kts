plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    alias(libs.plugins.google.gms.google.services) // Pastikan plugin ini ada
}

android {
    namespace = "com.example.shopeehijau"
    compileSdk = 36 // Sesuaikan dengan versi SDK terbaru yang stabil

    defaultConfig {
        applicationId = "com.example.shopeehijau"
        minSdk = 29 // Android 10 atau sesuai kebutuhan
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Atur true untuk rilis produksi
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true // Sangat direkomendasikan untuk interaksi UI yang lebih aman dan mudah
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat) //
    implementation(libs.androidx.activity) // atau libs.androidx.activity.ktx
    implementation(libs.androidx.constraintlayout) //

    // Material Design 3
    implementation(libs.material) // Pastikan versi terbaru untuk Material 3

    // Firebase (gunakan BOM untuk versi yang konsisten)
    implementation(platform("com.google.firebase:firebase-bom:33.1.0")) // Cek versi BOM terbaru
    implementation(libs.firebase.analytics) //
    implementation("com.google.firebase:firebase-auth-ktx")        // Firebase Authentication
    implementation("com.google.firebase:firebase-firestore-ktx")  // Firebase Firestore

    // Opsional: Glide untuk mempermudah loading gambar (termasuk Base64)
    // implementation("com.github.bumptech.glide:glide:4.16.0")
    // kapt("com.github.bumptech.glide:compiler:4.16.0") // Jika menggunakan Glide dengan anotasi

    // Testing
    testImplementation(libs.junit) //
    androidTestImplementation(libs.androidx.junit) //
    androidTestImplementation(libs.androidx.espresso.core) //
}