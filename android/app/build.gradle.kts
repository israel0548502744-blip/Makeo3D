plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the workflow run number, so every published APK has a higher versionCode
// and the in-app updater can tell which build is newer.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.triposr.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.triposr.mobile"
        minSdk = 33
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"
        ndk { abiFilters += "arm64-v8a" }
    }

    // github: the test build (APK from GitHub, self-updater, public test key, old package id so
    // installed copies and their downloaded models stay). play: the store build (AAB, no
    // self-updater, signed with the private upload key; Google re-signs it with the app key).
    flavorDimensions += "channel"
    productFlavors {
        create("github") {
            dimension = "channel"
            applicationId = "com.triposr.mobile"
            buildConfigField("boolean", "STORE", "false")
        }
        create("play") {
            dimension = "channel"
            applicationId = "com.makeo3d.app"
            buildConfigField("boolean", "STORE", "true")
        }
    }

    signingConfigs {
        // A fixed key, so each new test APK installs over the previous one as an update.
        // It is public (in the repository): never for the store.
        create("test") {
            storeFile = file("../keystore/triposr.jks")
            storePassword = "triposr"
            keyAlias = "triposr"
            keyPassword = "triposr"
        }
        // The upload key comes only from CI secrets (UPLOAD_KEYSTORE_B64 decoded to this path).
        System.getenv("UPLOAD_KEYSTORE_PATH")?.let { path ->
            create("upload") {
                storeFile = file(path)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // Per flavor, not per build type (a build type's key would win over the flavor's).
    productFlavors.getByName("github").signingConfig = signingConfigs.getByName("test")
    productFlavors.getByName("play").signingConfig = signingConfigs.findByName("upload")

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources { noCompress += listOf("onnx") }
    packaging {
        // QNN libraries must be extracted to disk so the Hexagon skeleton libraries can be found.
        jniLibs {
            useLegacyPackaging = true
            // Hexagon versions older than Snapdragon 8 Gen 2 (V73) and the legacy DSP backend.
            excludes += listOf(
                "**/libQnnHtpV66*", "**/libQnnHtpV68*", "**/libQnnHtpV69*",
                "**/libQnnDsp*",
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // QR code encoder for the QR plate (Apache-2.0).
    implementation("com.google.zxing:core:3.5.3")
    // ONNX Runtime with the Qualcomm QNN execution provider (pulls in com.qualcomm.qti:qnn-runtime).
    implementation("com.microsoft.onnxruntime:onnxruntime-android-qnn:1.29.0")
    // QNN libraries matching the QAIRT version (2.51) that compiles the NPU pack in CI
    // (onnxruntime-qnn 2.7.0); a context binary needs a runtime at least as new as its compiler.
    implementation("com.qualcomm.qti:qnn-runtime:2.51.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    // Screenshot tests (Robolectric + Roborazzi): render every screen on the JVM in CI.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
