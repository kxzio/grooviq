plugins {
    alias(
        libs.plugins.android.application
    )
    alias(
        libs.plugins.kotlin.android
    )
    alias(
        libs.plugins.kotlin.compose
    )
    id("com.chaquo.python")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.20"

}

android {
    namespace =
        "com.example.groviq"
    compileSdk =
        35

    defaultConfig {
        applicationId =
            "com.example.groviq"
        minSdk =
            33
        targetSdk =
            35
        versionCode =
            1
        versionName =
            "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled =
                false
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
        create("profile") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11
        targetCompatibility =
            JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget =
            "11"
    }
    buildFeatures {
        compose =
            true
    }
}

chaquopy {
    defaultConfig {
        version = "3.10"
        pip {
            install("requests")
            install("yt-dlp")
            install("git+https://github.com/sigma67/ytmusicapi.git")
            install("mutagen")
            install("syncedlyrics")
        }
    }
}
configurations.all {
    resolutionStrategy {
        //force("com.github.teamnewpipe:NewPipeExtractor:0.24.6")
        force("com.github.TeamNewPipe:NewPipeExtractor:dev-SNAPSHOT")
    }
}
dependencies {

    implementation("com.github.HaarigerHarald:android-youtubeExtractor:master-SNAPSHOT")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.github.bumptech.glide:glide:4.15.1")


    implementation("androidx.media:media:1.6.0")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")

    implementation("io.coil-kt:coil-compose:2.4.0")

    implementation("androidx.compose.material:material-icons-extended:1.0.1")

    implementation("com.github.shalva97:NewValve:1.5")

    //implementation("com.github.TeamNewPipe.NewPipeExtractor:extractor:v0.24.6")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:dev-SNAPSHOT")


    //EXOPLAYER
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.navigation:navigation-compose:2.6.0")
    implementation("sh.calvin.reorderable:reorderable:2.5.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}