import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Avisos push (FCM). El plugin necesita google-services.json (Firebase Console →
// configuración del proyecto → app Android org.atlas.aldia); sin ese archivo la
// app compila igual, pero no recibe avisos.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "org.atlas.aldia"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    // Pinned to the build-tools version already installed locally (36.1.0).
    // Without this, AGP defaults to 36.0.0 and tries to auto-download the SDK
    // manifest from dl.google.com/android/repository/*.xml, which this
    // network can't reach (see ~/.gradle/init.d/local-google-mirror.init.gradle.kts).
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "org.atlas.aldia"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}