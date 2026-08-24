plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

import java.util.Properties

android {
    namespace = "com.zakir.vestra"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zakir.vestra"
        minSdk = 35
        targetSdk = 36
        versionCode = 81
        versionName = "3.1.0-rc23"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["timeout_msec"] = "120000"
        // Never bake secrets into release APKs — debug/sideloadDebug may seed from local.properties.
        buildConfigField("String", "DEFAULT_HF_TOKEN", "\"\"")
        buildConfigField("String", "DEFAULT_OPENROUTER_TOKEN", "\"\"")
        buildConfigField("String", "DEFAULT_GROQ_TOKEN", "\"\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            buildConfigField("boolean", "APPLY_WATERMARK", "false")
        }
        create("store") {
            dimension = "distribution"
            buildConfigField("boolean", "APPLY_WATERMARK", "true")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
                ?: rootProject.file("signing/lookbook-sideload.keystore").absolutePath
            val keystoreFile = file(keystorePath)
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "lookbook-sideload"
                keyAlias = System.getenv("KEY_ALIAS") ?: "lookbook"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "lookbook-sideload"
            }
        }
    }

    buildTypes {
        debug {
            val localProps = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            val hfDefault = (localProps.getProperty("lookbook.hf.token")
                ?: System.getenv("LOOKBOOK_HF_TOKEN")
                ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
            val orDefault = (localProps.getProperty("lookbook.openrouter.token")
                ?: System.getenv("LOOKBOOK_OPENROUTER_TOKEN")
                ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
            val groqDefault = (localProps.getProperty("lookbook.groq.token")
                ?: System.getenv("LOOKBOOK_GROQ_TOKEN")
                ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
            buildConfigField("String", "DEFAULT_HF_TOKEN", "\"$hfDefault\"")
            buildConfigField("String", "DEFAULT_OPENROUTER_TOKEN", "\"$orDefault\"")
            buildConfigField("String", "DEFAULT_GROQ_TOKEN", "\"$groqDefault\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release").takeIf {
                it.storeFile?.exists() == true
            } ?: signingConfigs.getByName("debug")
            buildConfigField("String", "DEFAULT_HF_TOKEN", "\"\"")
            buildConfigField("String", "DEFAULT_OPENROUTER_TOKEN", "\"\"")
            buildConfigField("String", "DEFAULT_GROQ_TOKEN", "\"\"")
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
