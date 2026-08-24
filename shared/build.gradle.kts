plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // iOS targets (iosArm64, iosSimulatorArm64) are added when the iOS port begins;
    // they require a macOS host to compile. commonMain stays pure Kotlin so the
    // existing code compiles for iOS unchanged. See docs/IOS_PORT.md.
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // api: ModelPackManager's public constructor takes an HttpClient.
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            // api: AppSettings takes a Settings in its public constructor.
            api(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.core.ktx)
            implementation(libs.onnxruntime.android)
            implementation(libs.androidx.exifinterface)
            implementation(libs.litertlm.android)
            // Plain LiteRT Interpreter API — for .tflite graphs run directly (Bonsai Image
            // pipeline), distinct from litertlm-android's conversational Engine API.
            implementation(libs.litert.android)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            // Robolectric gives org.json.JSONObject a real implementation in a JVM unit test
            // (the compile-only Android stub jar throws "Stub!" at runtime otherwise).
            implementation(libs.robolectric)
        }
    }
}

android {
    namespace = "com.zakir.vestra.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
