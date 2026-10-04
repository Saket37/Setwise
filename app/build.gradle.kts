plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.compose.guard)
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
    lint {
        // For GitHub code scanning: findings show on the PR's changed lines.
        sarifReport = true
    }
    testOptions {
        // JVM unit tests run against stub Android classes: make calls like Log.e() no-ops
        // instead of throwing "not mocked", so error paths can be tested.
        unitTests.isReturnDefaultValues = true
        // Robolectric (Compose UI tests on the JVM) needs the app's resources.
        unitTests.isIncludeAndroidResources = true
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
    // Is the app on screen? (rest-over alert: in-app vibration vs notification)
    implementation(libs.androidx.lifecycle.process)
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
    implementation(libs.mlkit.genai.speech.recognition)
    // On-device text recognition (body composition reports), model via Play services
    implementation(libs.mlkit.text.recognition)
    // Structured output: @Generable answer classes; KSP generates their schemas.
    implementation(libs.mlkit.genai.schema)
    ksp(libs.mlkit.genai.schema.compiler)

    // Unit tests
    testImplementation(libs.bundles.test)
    // Compose UI on the JVM (Robolectric): recomposition tests
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Instrumented tests (./gradlew connectedDebugAndroidTest)
    androidTestImplementation(libs.bundles.android.test)
}

detekt {
    // Kotlin best practices, Compose rules and ktlint formatting; config/detekt/detekt.yml
    // switches rules on top of detekt's defaults.
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    // Findings already in the code when detekt was added; new code must not add more.
    baseline = file("detekt-baseline.xml")
    parallel = true
    // Its type analysis doesn't see BuildConfig or kotlinx.serialization's generated serializer();
    // it reports those as compiler errors, which only blunt checks at those lines.
}

composeGuardCheck {
    // Strong skipping (default) skips composables with unstable parameters by instance
    // equality, so only a composable that can't skip at all is a regression worth failing on.
    ignoreUnstableParamsOnSkippableComposables = true
}

dependencies {
    detektPlugins(libs.detekt.compose.rules)
    detektPlugins(libs.detekt.ktlint.wrapper)
}

kover {
    reports {
        verify {
            rule("Line coverage") {
                // 72% with device-only code left out (#70); raise it as coverage grows, never lower it.
                minBound(71)
            }
        }
        filters {
            excludes {
                // Generated code: Room, KSP schema providers, BuildConfig, Compose singletons.
                classes("*_Impl", "*_Impl\$*", "*_GeneratedProvider", "*.BuildConfig", "*ComposableSingletons*")
                // Composables are checked on a device, not by JVM unit tests.
                annotatedBy("androidx.compose.runtime.Composable", "androidx.compose.ui.tooling.preview.Preview")
                // Code that only runs on a device: debug-only harnesses (the on-device AI check, the
                // fake-data seeder), thin wrappers over ML Kit / Gemini Nano in Play services (the
                // logic around them is tested through OnDeviceModel, TextReader and SpeechInput
                // fakes), and the app and activity setup.
                classes(
                    "dev.saketanand.setwise.domain.ai.AiCheck*",
                    "dev.saketanand.setwise.data.dev.DevDataSeeder*",
                    "dev.saketanand.setwise.llm.GeminiNanoModel*",
                    "dev.saketanand.setwise.llm.GenAiSpeechInput*",
                    "dev.saketanand.setwise.llm.MlKitTextReader*",
                    "dev.saketanand.setwise.SetwiseApp*",
                    "dev.saketanand.setwise.MainActivity*",
                )
            }
        }
    }
}
