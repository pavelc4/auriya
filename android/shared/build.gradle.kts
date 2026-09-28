plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
}

// Android build variant task aliases for pure JVM module compatibility
tasks.register("compileDebugKotlin") {
    dependsOn(tasks.named("compileKotlin"))
}
tasks.register("compileReleaseKotlin") {
    dependsOn(tasks.named("compileKotlin"))
}
