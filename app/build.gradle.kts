plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.lemarc.fake_gps"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lemarc.fake_gps"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
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
            signingConfig = signingConfigs.getByName(if (uploadStoreFile != null) "upload" else "debug")
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.osmdroid.android)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
