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
        versionCode = 32
        versionName = "3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "YOUTUBE_API_KEY", "\"${localProperties.getProperty("YOUTUBE_API_KEY", "")}\"")
        // Supabase Storage ("En compañía"): se leen de local.properties para no subirlos a GitHub
        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProperties.getProperty("SUPABASE_KEY", "")}\"")
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

    // Movido fuera de signingConfigs: aquí es su lugar correcto
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/LGPL2.1"

            // Si después de esto te marca error por algún archivo de versión de Kotlin
            // puedes descomentar esta línea:
            // pickFirst += "META-INF/kotlinx_coroutines_core.version"
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

            // Solo en el APK release: se empaquetan las librerías nativas para teléfonos (64 y 32 bits) y se
            // dejan fuera x86/x86_64 (solo emuladores y algunas tablets Intel). Antes iban las 4 arquitecturas.
            // El debug conserva todas para que el emulador siga funcionando.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
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
    // IA local con LiteRT-LM (Gemma 4 E2B, archivos .litertlm, con MTP), Apache-2.0.
    // Versión FIJA: 0.13.1 está hecha con Kotlin 2.2 (igual que este proyecto). Las más nuevas (con latest.release)
    // traen metadata de Kotlin 2.4 y el compilador 2.2 no las puede leer ("incompatible version of Kotlin").
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    // Coil (Imágenes y GIFs)
    implementation("io.coil-kt:coil-gif:2.5.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    // --- GOOGLE SIGN-IN ---
    implementation("com.google.android.gms:play-services-auth:21.1.1")
    // Coroutines para Play Services
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // YouTube Player
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")
    implementation("com.google.api-client:google-api-client-android:1.34.1")
    implementation("com.google.api-client:google-api-client-gson:1.34.1")
    implementation("com.google.http-client:google-http-client-android:1.43.3")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // Meta de ahorro (token de GitHub cifrado en el dispositivo)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Escritura real de tags (carátula/título/artista/álbum) en el archivo de
    // audio en disco. Soporta mp3, flac, ogg, wav, m4a, wma, aiff, dsf y opus.
    // No re-codifica el audio: solo reescribe el bloque de metadatos.
    implementation("com.github.Adonai:jaudiotagger:2.3.15")

    // Recorte de imágenes (carátulas / fondo) con pan+zoom, respeta orientación EXIF
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // --- FIREBASE (modo "En compañía") ---
    // Con el BoM 34+ ya no existen los módulos -ktx
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
}