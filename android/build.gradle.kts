// Top-level build configuration for the Auriya Android workspace.
// Plugin versions live in gradle/libs.versions.toml — only declare
// them with `apply false` here so submodules can opt in.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
