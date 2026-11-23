plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.aaideas.krossvord"
    compileSdk = 36

    defaultConfig {
        // -Plab=true: "Açar Söz Lab", a separate app for trying experiments (Maksimum şüşə) next to the real one.
        val lab = project.findProperty("lab") == "true"
        applicationId = "com.aaideas.krossvord" + if (lab) ".lab" else ""
        buildConfigField("boolean", "LAB", lab.toString())
        manifestPlaceholders["appLabel"] = if (lab) "Açar Söz Lab" else "@string/app_name"
        minSdk = 24
        targetSdk = 36
        // CI sets GITHUB_RUN_NUMBER, so every published build has a bigger
        // versionCode than the one before and installs as an update.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "2.1.$build"
    }

    // Prototype builds are signed with one fixed key, so each new APK installs
    // over the previous one. The key is not in the repo: CI writes it from the
    // PROTOTYPE_KEYSTORE_BASE64 / PROTOTYPE_KEYSTORE_PASSWORD secrets. Without
    // them the usual per-machine debug key is used. Prototype only; the Google
    // Play build will use its own upload key.
    val protoKeystore = rootProject.file("prototype.keystore")
    // trim(): a pasted secret often carries a trailing newline or space.
    val protoPassword = System.getenv("PROTOTYPE_KEYSTORE_PASSWORD")?.trim()
    if (protoKeystore.exists() && !protoPassword.isNullOrEmpty()) {
        signingConfigs {
            getByName("debug") {
                storeFile = protoKeystore
                storePassword = protoPassword
                keyAlias = "prototype"
                keyPassword = protoPassword
            }
        }
    }

    buildTypes {
        debug {
            // Where the in-app updater (src/debug only) looks for new builds.
            buildConfigField("String", "UPDATE_REPO", if (project.findProperty("lab") == "true") "\"\"" else "\"cabbari826-jpg/AAIdeas\"")
            // This build goes to real phones for testing. A debuggable app runs Compose with
            // ART optimisations off and ignores the libraries' startup profiles, which makes it
            // several times slower to start on low-end phones. Turn that off.
            isDebuggable = false
            // Same R8 shrinking and optimisation as a store build: smaller, faster to start.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        // java.time (holiday dates) on Android 7.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets["test"].resources.srcDir("src/main/assets")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Frosted-glass blur for the Şüşə theme (Apache-2.0); blurs on Android 12+, tints before that.
    implementation("dev.chrisbanes.haze:haze:1.6.10")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    // Installs the Compose startup (baseline) profiles on sideloaded APKs for faster cold start.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Screenshot tests (Robolectric + Roborazzi): ./gradlew recordRoborazziDebug
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
