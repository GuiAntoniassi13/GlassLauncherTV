plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vidro.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vidro.launcher"
        minSdk = 30 // Android 11 (TV Philips TPM215L)
        targetSdk = 34
        versionCode = 3 // aumente a cada atualização (o Android recusa instalar um número menor)
        versionName = "1.0.1"
    }

    // Assinatura: se existir assinatura/vidro.keystore (não versionada), ela é usada; senão, a chave de
    // depuração local do Android SDK. Atualizações só instalam por cima com a MESMA chave da versão instalada.
    val releaseKey = rootProject.file("assinatura/vidro.keystore")
    signingConfigs {
        if (releaseKey.exists()) create("vidro") {
            storeFile = releaseKey
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("vidro") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
