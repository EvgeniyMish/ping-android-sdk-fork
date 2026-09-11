plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.googleServices)
}

val recognizeEnabled = (findProperty("cloudsmithTokenRecognize") as? String).orEmpty().isNotBlank()

android {
    namespace = "com.pingidentity.samples.pingsampleapp"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.pingidentity.samples.pingsampleapp"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.9"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "lib/*/libtool-file.so"
        }
    }

    sourceSets {
        named("main") {
            kotlin.directories.add(if (recognizeEnabled) "src/recognizeEnabled/kotlin" else "src/recognizeDisabled/kotlin")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

configurations.all {
    resolutionStrategy {
        force("com.google.android.gms:play-services-basement:18.4.0")
        force("com.google.android.gms:play-services-tasks:18.2.0")
        force("com.google.android.gms:play-services-base:18.5.0")
    }
}

dependencies {
    // Ping
    implementation(project(":foundation:android"))
    implementation(project(":foundation:device:device-id"))
    implementation(project(":foundation:device:device-profile"))
    implementation(project(":foundation:device:device-client"))
    implementation(project(":foundation:device:device-root"))
    implementation(project(":foundation:oidc"))
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(project(":davinci"))
    implementation(project(":journey"))
    if (recognizeEnabled) implementation(project(":recognize"))
    //Protect
    implementation(project(":protect"))

    // MFA and Binding
    implementation(project(":mfa:binding"))
    implementation(project(":mfa:binding-ui"))
    implementation(project(":mfa:binding-migration"))
    implementation(project(":mfa:fido"))
    implementation(project(":mfa:oath"))
    implementation(project(":mfa:push"))
    implementation(project(":mfa:auth-migration"))
    implementation(project(":foundation:migration"))

    // PingOne MFA
    implementation(project(":pingonemfa"))

    //Application Pin
    implementation(libs.bcpkix.jdk18on)

    // Recaptcha
    implementation(project(":recaptcha-enterprise"))
    implementation(libs.androidx.datastore.preferences)

    //To enable Native Google Sign-In and Facebook Login
    implementation(libs.googleid)
    implementation(libs.facebook.login)

    // Social Login
    implementation(project(":external-idp"))

    // FIDO2
    //To Support nod-discoverable fido2 credential
    implementation(libs.play.services.fido)
    implementation(libs.play.services.auth)

    //Protect
    implementation(project(":protect"))

    // Core dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.compose.material3)
    implementation(libs.material)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)

    // Android Studio Preview support
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    // For fetching Device location
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    // CameraX dependencies for QR scanning
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.barcode.scanning)
    // QR code generation
    implementation(libs.zxing.qr.generator)

    // HTTP client for reverse geocoding API calls
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Firebase Cloud Messaging for push notifications
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Maps for location display - using free OpenStreetMap
    implementation(libs.osmdroid.android)

    // Biometric
    implementation(libs.androidx.biometric)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}