description = "Legacy Authentication Migration"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.auth.migration"
}

dependencies {
    implementation(project(":foundation:android"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:migration"))
    implementation(project(":foundation:storage"))
    implementation(project(":mfa:oath"))
    implementation(project(":mfa:push"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.core.ktx)
    implementation(libs.androidx.startup.runtime)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}