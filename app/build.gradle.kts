plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.saketanand.setwise"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.saketanand.setwise"
        minSdk = 26
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates debug-only code such as the fake-data seeder.
        buildConfig = true
    }
}

composeCompiler {
    // Marks java.time.* as stable so composables taking LocalDate/LocalTime can be skipped.
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))
}

room {
    // Exported schema JSONs — commit these; you'll need them for migrations
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.core.splashscreen)

    // Compose (UI, Material 3, icons, navigation)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Data
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    // DI
    implementation(libs.bundles.koin)

    // On-device LLM: Gemini Nano via ML Kit Prompt API (AICore)
    implementation(libs.mlkit.genai.prompt)

    // Unit tests
    testImplementation(libs.bundles.test)

    // Instrumented tests (./gradlew connectedDebugAndroidTest)
    androidTestImplementation(libs.bundles.android.test)
}
