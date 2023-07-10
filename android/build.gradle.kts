plugins {
    id("org.jetbrains.compose")
    id("com.android.application")
    kotlin("android")
}

group = "com.ethossoftworks"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("com.outsidesource:oskit-kmp:3.1.0")
    implementation("com.outsidesource:oskit-compose:2.1.0")
    implementation("io.insert-koin:koin-core:3.4.0")
}

android {
    compileSdk = 33
    defaultConfig {
        applicationId = "com.ethossoftworks.LANd"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}