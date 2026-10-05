// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    dependencies {
        constraints {
            // Kover's HTML report pulls FreeMarker 2.3.32 (GHSA-27j2-h3m2-8237, fixed in 2.3.35).
            // Remove once Kover brings a fixed version itself.
            classpath("org.freemarker:freemarker:2.3.35")
            // AGP 9.1 pulls Bouncy Castle 1.79 for APK signing (GHSA-574f-3g2m-x479,
            // GHSA-9pwp-9qqc-pr26 and others, fixed in 1.85). The three modules must match.
            // Remove once AGP brings a fixed version itself.
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.bouncycastle:bcutil-jdk18on:1.86")
            // AGP's other dependencies with advisories (#82); remove each once AGP brings a
            // fixed version itself.
            classpath("org.bitbucket.b_c:jose4j:0.9.7") // GHSA-3677-xxcr-wjqv
            classpath("org.jdom:jdom2:2.0.6.1") // GHSA-2363-cqg2-863c
            classpath("org.apache.commons:commons-lang3:3.21.0") // GHSA-j288-q9x7-2f5v
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
