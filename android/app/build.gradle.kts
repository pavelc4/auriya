import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing material lives outside the repo. The example template at
// android/signing.properties.example documents the keys; the real
// file (android/signing.properties) is gitignored. When absent the
// release build silently falls through to the debug signing config so
// CI on a clean checkout still produces a working APK for smoke
// tests.
val signingPropertiesFile = rootProject.file("signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

// ── Version from Cargo.toml ─────────────────────────────────────
// Manager version follows the daemon version automatically so the
// two never drift.  Override via -PcargoVersion=X.Y.Z and
// -PversionCode=N from CI.
val cargoFile = rootProject.file("../Cargo.toml")

val auriyaVersionName: String by lazy {
    val explicit = project.findProperty("cargoVersion") as? String
    if (explicit != null) return@lazy explicit
    if (!cargoFile.exists()) return@lazy "0.0.0"
    val match = Regex("""^version\s*=\s*"([^"]+)"""", RegexOption.MULTILINE).find(cargoFile.readText())
    match?.groupValues?.get(1) ?: "0.0.0"
}

val auriyaVersionCode: Int by lazy {
    val explicit = project.findProperty("versionCode") as? String
    if (explicit != null) return@lazy explicit.toIntOrNull() ?: 1
    // Encode semver into an integer: MAJOR * 10000 + MINOR * 100 + PATCH
    val parts = auriyaVersionName.split(".").map { it.toIntOrNull() ?: 0 }
    when (parts.size) {
        3 -> parts[0] * 10000 + parts[1] * 100 + parts[2]
        2 -> parts[0] * 10000 + parts[1] * 100
        1 -> parts[0] * 10000
        else -> 1
    }
}

android {
    namespace = "dev.auriya.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.auriya.app"
        minSdk = 30
        targetSdk = 37
        versionCode = auriyaVersionCode
        versionName = auriyaVersionName

        // ABI filtering is handled by splits.abi below; the ndk block
        // is intentionally empty to avoid a conflict with the splits
        // DSL in AGP 9+.

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (signingProperties.isNotEmpty()) {
            create("release") {
                storeFile = signingProperties.getProperty("KEYSTORE_PATH")?.let { rootProject.file(it) }
                storePassword = signingProperties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = signingProperties.getProperty("KEY_ALIAS")
                keyPassword = signingProperties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (signingProperties.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Target-only split: Auriya's daemon is arm64, so that's all we
    // ship.  No universal APK needed — one arch, one APK.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
        aidl = false
        shaders = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE*",
                "META-INF/LICENSE*",
                "kotlin/**",
                "**.txt",
                "**.proto",
            )
        }
    }

    // F-Droid friendly + smaller APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.material.kolor)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.libsu.core)
    implementation(libs.libsu.io)
    implementation(libs.androidx.glance.appwidget)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
