plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lemarc.fake_gps"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lemarc.fake_gps"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }
    val uploadStoreFile = System.getenv("SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 : réduit, optimise et obfusque le code ; retire ensuite les ressources inutilisées.
            // Les bibliothèques (MapLibre/JavaCPP, Compose…) fournissent leurs propres règles de conservation.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Téléphones ARM seulement : x86/x86_64 ne servent qu'aux émulateurs (gardés en debug),
            // et le moteur MapLibre pèse ~14 Mo par architecture.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            signingConfig = signingConfigs.getByName(if (uploadStoreFile != null) "upload" else "debug")
        }
    }

    packaging {
        jniLibs {
            // Bibliothèques natives compressées dans l'APK (moteur MapLibre : 13 → 6 Mo) ;
            // Android les extrait à l'installation au lieu de les charger directement depuis l'APK.
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.maplibre.compose)
    // Sans moteur de rendu, la carte plante au lancement (libmaplibre-native-c.so introuvable).
    runtimeOnly(libs.maplibre.native.runtime.opengl)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
