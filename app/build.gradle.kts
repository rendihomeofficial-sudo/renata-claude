plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.renata.claude"
    compileSdk = 34

    // Address of the version.json manifest used by the auto update feature.
    // Set it in gradle.properties as renata.updateManifestUrl=...
    val updateManifestUrl = ((project.findProperty("renata.updateManifestUrl") as String?) ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    // Cermin cadangan dipakai kalau URL utama tidak bisa dijangkau jaringan.
    val updateManifestFallbackUrls = ((project.findProperty("renata.updateManifestFallbackUrls") as String?) ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    defaultConfig {
        applicationId = "com.renata.claude"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "1.3.5"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
        buildConfigField("String", "UPDATE_MANIFEST_FALLBACK_URLS", "\"$updateManifestFallbackUrls\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/renata-claude.jks")
            storePassword = "renataclaude"
            keyAlias = "renata"
            keyPassword = "renataclaude"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
