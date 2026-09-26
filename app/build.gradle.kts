plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.flowpilot.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.flowpilot.app"
        minSdk = 26
        targetSdk = 37
        // The release workflow passes these; local and CI builds fall back to the defaults.
        versionCode = (findProperty("flowpilot.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("flowpilot.versionName") as String?) ?: "0.1.0"
    }

    signingConfigs {
        // Set by the release workflow when the FLOWPILOT_KEYSTORE secrets exist.
        val keystore = System.getenv("FLOWPILOT_KEYSTORE_PATH")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("FLOWPILOT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FLOWPILOT_KEY_ALIAS")
                keyPassword = System.getenv("FLOWPILOT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the release key when one is configured, else the debug key so the APK still installs.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.coroutines.android)

    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)
}
