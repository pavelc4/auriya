plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.auriya.service"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.auriya.service"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
