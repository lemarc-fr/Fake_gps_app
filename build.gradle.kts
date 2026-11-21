plugins {
    alias(libs.plugins.android.application) apply false
    // Non appliqué : place seulement ce Kotlin (au lieu de celui d'AGP) dans le classpath du build.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
