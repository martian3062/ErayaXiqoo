// AGP 9 compiles Kotlin itself (built-in Kotlin). Declaring kotlin-android here with
// `apply false` only pins the Kotlin Gradle plugin version on the build classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
