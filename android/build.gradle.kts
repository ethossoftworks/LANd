import java.io.FileInputStream
import java.util.*

plugins {
    id("org.jetbrains.compose")
    id("com.android.application")
    kotlin("android")
}

val versionProps = Properties().apply {
    load(FileInputStream(File(rootProject.rootDir, "version.properties")))
}

group = "com.ethossoftworks"
version = versionProps["version"] as? String ?: "0.0.0"
val buildNumber = versionProps["build"]?.toString()?.toInt() ?: 1

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
        versionCode = buildNumber
        versionName = version.toString()
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