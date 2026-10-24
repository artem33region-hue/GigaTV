import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tmdbApiKey: String = (localProps.getProperty("TMDB_API_KEY") ?: "").trim()
val updateRepository = providers.gradleProperty("updateRepository").orElse("artem33region-hue/GigaTV").get().trim()
require(updateRepository.isEmpty() || Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(updateRepository)) {
    "updateRepository must be owner/repository"
}
val defaultUpdateUrl = if (updateRepository.isEmpty()) "" else
    "https://github.com/$updateRepository/releases/latest/download/update.json"

android {
    namespace = "com.artem.gigatv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.artem.gigatv"
        minSdk = 24
        targetSdk = 34
        versionCode = 18
        versionName = "0.4.13"
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "DEFAULT_UPDATE_URL", "\"$defaultUpdateUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.tv:tv-material:1.0.0")
    // Ряды приложения на домашнем экране телевизора (TvProvider / каналы).
    implementation("androidx.tvprovider:tvprovider:1.0.0")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // Медиасессия: система видит, что идёт воспроизведение, и корректно
    // отдаёт/забирает аудиофокус. Так делают Plex и LazyMedia.
    implementation("androidx.media3:media3-session:1.4.1")

    // WebSocket-сервер для приложения-пульта.
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
