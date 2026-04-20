plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.kumagai.myapplication"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.kumagai.myapplication"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.animation.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 1. Compose BOM (Define as versões de todas as libs de uma vez)
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01") // Versão estável hipotética em 2026
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // 2. Core & UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")

    // 3. Foundation & Layout (Box, Column, Row, LazyColumn)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")

    // 4. Material Design 3
    implementation("androidx.compose.material3:material3")

    // 5. Integração com Android (Activity, ViewModel, Lifecycle)
    implementation("androidx.activity:activity-compose:1.10.0") // Versão atualizada
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // ESSENCIAL PARA PERFORMANCE: Coleta de fluxo consciente do ciclo de vida
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")

    // 6. Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // 7. Tooling & Debug (Não vão para o APK final)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}