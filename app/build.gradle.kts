import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

// Cargamos local.properties una sola vez para reutilizarlo en varios lugares
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

android {
    namespace = "com.music.musicflame"
    compileSdk = 36

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    defaultConfig {
        applicationId = "com.music.musicflame"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "YOUTUBE_API_KEY", "\"${localProperties.getProperty("YOUTUBE_API_KEY", "")}\"")
    }

     signingConfigs {
         create("release") {
             val keystorePath = localProperties.getProperty("RELEASE_KEYSTORE_PATH", "")
             if (keystorePath.isNotEmpty()) {
                 storeFile = file(keystorePath)
                 storePassword = localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD", "")
                 keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
                 keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
             }
         }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // --- CAMBIADO: antes usaba signingConfigs.getByName("debug") ---
                  signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName.set("MusicFlame.apk")
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // UI y Media
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    // Coil (Imágenes y GIFs)
    implementation("io.coil-kt:coil-gif:2.5.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    // --- FIREBASE (Usando tu versión más reciente 34.15.0) ---
    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // --- GOOGLE SIGN-IN ---
    implementation("com.google.android.gms:play-services-auth:21.1.1")
    // Coroutines para Play Services
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // YouTube Player
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")
}