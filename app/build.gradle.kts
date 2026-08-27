plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
}

// Spatial AI quality gate: report current Kotlin findings first, then promote reviewed regressions to failures.
detekt {
    buildUponDefaultConfig = true
    allRules = false
    ignoreFailures = true
}

android {
    namespace = "com.zakir.vestra"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zakir.vestra"
        minSdk = 26
        targetSdk = 36
        versionCode = 81
        versionName = "3.1.0-rc23"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["timeout_msec"] = "120000"
        buildConfigField("boolean", "APPLY_WATERMARK", "false")
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val releaseKeystore = System.getenv("KEYSTORE_PATH")
            ?.takeIf { it.isNotBlank() }
            ?.let { file(it) }
        val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
        val releaseKeyAlias = System.getenv("KEY_ALIAS")
        val releaseKeyPassword = System.getenv("KEY_PASSWORD")
        if (releaseKeystore?.exists() == true &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()
        ) {
            create("releaseConfig") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.findByName("releaseConfig")
            val releaseRequested = gradle.startParameter.taskNames.any {
                it.contains("release", ignoreCase = true)
            }
            if (releaseRequested && releaseSigning == null) {
                error("Release signing secrets are required: KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD")
            }
            signingConfig = releaseSigning ?: signingConfigs.getByName("debugConfig")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Robolectric needs merged Android resources to inflate real UI in JVM tests.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.animation)
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.work.runtime)

    // LiteRT-LM — Gemma 4 Code / vision / audio / tools (Gallery-class on-device LLM).
    implementation(libs.litertlm.android)
    implementation(libs.litert.android)
    // Legacy Gemma 3 1B (MediaPipe) — reflective load for local-gemma-v1 fallback only.
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // Robolectric runs the Android framework on the JVM, so Compose UI tests run in CI and in
    // sandboxes with no device, emulator, or KVM.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
