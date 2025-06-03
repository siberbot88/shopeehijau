// settings.gradle.kts

pluginManagement {
    repositories {
        google {
            content {
                // Hanya cari plugin dari grup Android, Google, dan AndroidX di repositori Google
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral() // Untuk plugin umum lainnya
        gradlePluginPortal() // Portal plugin Gradle resmi
    }
}

dependencyResolutionManagement {
    // Mode ini MENGHARUSKAN semua deklarasi repositori dependensi ada di sini (settings.gradle.kts),
    // dan akan GAGAL jika ada di build.gradle tingkat modul. Ini praktik terbaik.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google {
            content {
                // Sama seperti di pluginManagement, batasi pencarian dependensi
                // dari repositori Google hanya untuk grup Android, Google, dan AndroidX.
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral() // Untuk dependensi library umum
        // Jika Anda menggunakan library dari JitPack atau repositori kustom lainnya, tambahkan di sini:
        // maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "shopeehijau"
include(":app") // Memasukkan modul ':app' ke dalam proyek