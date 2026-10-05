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
    // ImmutableList / ImmutableSet: collection parameters the Compose compiler knows are stable.
    implementation(libs.kotlinx.collections.immutable)
    // Declared so the app's coroutines move with kotlinx-coroutines-test (#85).
    implementation(libs.kotlinx.coroutines.android)

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
    constraints {
        // ML Kit GenAI brings Guava 31.0.1-jre (via kotlinx-coroutines-guava), which has two
        // advisories: use the Android build at a fixed version instead (#83).
        implementation(libs.guava) { because("GHSA-7g45-4rm6-3mm3, GHSA-5mg8-w23w-74h3") }
    }

    // Unit tests
    testImplementation(libs.bundles.test)
    // Compose UI on the JVM (Robolectric): recomposition tests
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Instrumented tests (./gradlew connectedDebugAndroidTest)
    androidTestImplementation(libs.bundles.android.test)
    // Migration tests: opens each exported schema version and migrates it (MigrationTestHelper).
    androidTestImplementation(libs.room.testing)
    // Compose UI tests with Google's Accessibility Test Framework checks (#62); the checks need
    // API 34+ on a device or emulator (they don't run on Robolectric).
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    constraints {
        // The Accessibility Test Framework brings protobuf-javalite 3.19.1 and jsoup 1.15.1, which
        // have advisories: test-only, but not vulnerable either.
        androidTestImplementation(libs.protobuf.javalite) { because("Advisories in 3.19.1") }
        androidTestImplementation(libs.jsoup) { because("Advisories in 1.15.1") }
    }
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

// AGP's own tool configurations in this module (the test platform that runs instrumented tests)
// resolve libraries with known advisories (#82). Raise them to fixed versions; never lower one.
// Remove an entry once AGP brings a fixed version itself.
val toolingSecurityFloors = mapOf(
    "org.bouncycastle" to "1.85", // GHSA-574f-3g2m-x479 and others; the jdk18on modules share one version
    "org.apache.commons:commons-lang3" to "3.21.0", // GHSA-j288-q9x7-2f5v
    "org.apache.httpcomponents:httpclient" to "4.5.14", // GHSA-7r82-7xv7-xcpj
    "org.bitbucket.b_c:jose4j" to "0.9.7", // GHSA-3677-xxcr-wjqv
    "org.jdom:jdom2" to "2.0.6.1", // GHSA-2363-cqg2-863c
)

/** "1.80.2" < "1.85", "2.0.6" < "2.0.6.1": by their numbers, part by part. */
fun isLower(version: String, than: String): Boolean {
    fun numbers(v: String) = Regex("\\d+").findAll(v).map { it.value.toInt() }.toList()
    val a = numbers(version)
    val b = numbers(than)
    val difference = a.zip(b).firstOrNull { (x, y) -> x != y }
    return if (difference != null) difference.first < difference.second else a.size < b.size
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        val floor = toolingSecurityFloors["${requested.group}:${requested.name}"]
            ?: toolingSecurityFloors[requested.group]?.takeIf { requested.name.endsWith("-jdk18on") }
            ?: return@eachDependency
        val requestedVersion = requested.version ?: return@eachDependency
        if (isLower(requestedVersion, floor)) {
            useVersion(floor)
            because("Known advisories in $requestedVersion (#82)")
        }
    }
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
                // Raise it as coverage grows (72.8% after #38), never lower it.
                minBound(72)
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
