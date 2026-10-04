// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    dependencies {
        constraints {
            // Kover's HTML report pulls FreeMarker 2.3.32 (GHSA-27j2-h3m2-8237, fixed in 2.3.35).
            // Remove once Kover brings a fixed version itself.
            classpath("org.freemarker:freemarker:2.3.35")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.compose.guard) apply false
}
