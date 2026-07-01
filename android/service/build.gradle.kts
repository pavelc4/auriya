import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val signingPropertiesFile = rootProject.file("signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

// ── Version from Cargo.toml ─────────────────────────────────────
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
    val parts = auriyaVersionName.split(".").map { it.toIntOrNull() ?: 0 }
    when (parts.size) {
        3 -> parts[0] * 10000 + parts[1] * 100 + parts[2]
        2 -> parts[0] * 10000 + parts[1] * 100
        1 -> parts[0] * 10000
        else -> 1
    }
}

android {

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
    namespace = "dev.auriya.service"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.auriya.service"
        minSdk = 30
        targetSdk = 36
        versionCode = auriyaVersionCode
        versionName = auriyaVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = if (signingProperties.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isShrinkResources = false   // no resources to shrink
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Headless: no UI, no resources beyond the manifest stub.
    buildFeatures {
        resValues = false
        viewBinding = false
        dataBinding = false
        buildConfig = false
        shaders = false
        aidl = false
    }

    // Trim packaging — no native libs, drop stray metadata.
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
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
}
