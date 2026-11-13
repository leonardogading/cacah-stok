plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cacahstok.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cacahstok.app"
        minSdk = 24
        targetSdk = 34
        // Nomor versi otomatis naik di setiap build GitHub Actions
        val build = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
    }

    signingConfigs {
        // Kunci tetap agar APK versi baru bisa langsung menimpa versi lama
        // (data di HP tidak hilang saat update).
        create("release") {
            storeFile = file("cacahstok.jks")
            storePassword = "cacahstok123"
            keyAlias = "cacahstok"
            keyPassword = "cacahstok123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/INDEX.LIST",
            )
        }
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    // Pemindai barcode via kamera (offline, tidak butuh Google Play Services)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Pengiriman otomatis di latar belakang (dicoba ulang saat jaringan kembali)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Akses folder bersama Windows (SMB2/3)
    implementation("com.hierynomus:smbj:0.13.0")
}
