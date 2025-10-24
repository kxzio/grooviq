import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10"
    id("org.jetbrains.kotlin.kapt")
    id("androidx.baselineprofile") version "1.3.1"
}

android {
    namespace = "com.example.groviq"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.groviq"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            buildConfigField("boolean", "IS_PRODUCTION", "false")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isProfileable = false
            isCrunchPngs = true

            // baseline profile интеграция
            matchingFallbacks += listOf("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            buildConfigField("boolean", "IS_PRODUCTION", "true")

            // Оптимизация dex и runtime
            multiDexEnabled = false
        }

        create("profile") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = true
            isProfileable = true
            isMinifyEnabled = false
            buildConfigField("boolean", "IS_PRODUCTION", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
            "-Xcontext-receivers"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }
}

chaquopy {
    defaultConfig {
        version = "3.10"
        pip {
            install("requests")
            install("yt-dlp")
            install("ytmusicapi==1.11.1")
            install("mutagen")
            install("syncedlyrics")
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("com.github.TeamNewPipe:NewPipeExtractor:dev-SNAPSHOT")
    }
}

dependencies {
    implementation("com.github.HaarigerHarald:android-youtubeExtractor:master-SNAPSHOT")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.github.bumptech.glide:glide:4.15.1")

    // Media3 + ExoPlayer
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // Coil 3
    implementation("io.coil-kt.coil3:coil:3.0.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")
    implementation("com.github.Commit451.coil-transformations:transformations:2.0.2")

    // Room
    implementation("androidx.room:room-runtime:2.5.2")
    kapt("androidx.room:room-compiler:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")

    // Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.navigation:navigation-compose:2.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.0.1")

    // UI & Utils
    implementation("dev.chrisbanes.haze:haze:1.6.10")
    implementation("sh.calvin.reorderable:reorderable:2.5.1")
    implementation("com.github.shalva97:NewValve:1.5")

    // Performance & optimization
    implementation("com.google.guava:guava:33.0.0-android")

    // Baseline profile runtime
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
