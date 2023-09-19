import java.io.FileInputStream
import java.util.*

plugins {
    id("org.jetbrains.compose")
    id("com.android.application")
    kotlin("android")
}

val buildInfoProps = Properties().apply {
    load(FileInputStream(File(rootProject.rootDir, "buildInfo.properties")))
}

group = "com.ethossoftworks"
version = buildInfoProps["version"] as? String ?: "0.0.0"
val buildNumber = buildInfoProps["buildNumber"]?.toString()?.toInt() ?: 1

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.outsidesource:oskit-kmp:4.0.0")
    implementation("com.outsidesource:oskit-compose:3.0.0")
    implementation("io.insert-koin:koin-core:3.4.3")
}

android {
    compileSdk = 34
    defaultConfig {
        applicationId = "com.ethossoftworks.LANd"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = version.toString()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
