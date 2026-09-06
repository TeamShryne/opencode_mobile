// Top-level build file. Built exclusively via GitHub Actions (see .github/workflows/android.yml).
// Do NOT build locally per project policy — all APKs come from CI.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
