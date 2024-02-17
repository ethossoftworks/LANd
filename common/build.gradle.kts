import java.io.FileInputStream
import java.util.*

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("com.android.library")
    kotlin("plugin.serialization") version "1.9.10"
}

val buildInfoProps = Properties().apply {
    load(FileInputStream(File(rootProject.rootDir, "buildInfo.properties")))
}

group = "com.ethossoftworks"
version = buildInfoProps["version"] as? String ?: "0.0.0"
val buildNumber = buildInfoProps["build"]?.toString()?.toInt() ?: 1

kotlin {
    android {
        jvmToolchain(17)
    }
    jvm("desktop") {
        jvmToolchain(17)
    }
    sourceSets {
        generateBuildInfo(rootDir, buildDir)

        val commonMain by getting {
            kotlin.srcDir("${buildDir}/generated")

            dependencies {
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material)
                implementation("com.outsidesource:oskit-kmp:4.4.0")
                implementation("com.outsidesource:oskit-compose:3.3.0")
                implementation("io.ktor:ktor-network:2.3.3")
                implementation("io.ktor:ktor-server-core:2.3.1")
                implementation("io.ktor:ktor-server-cio:2.3.1")
                implementation("io.ktor:ktor-client-core:2.3.3")
                implementation("io.ktor:ktor-client-cio:2.3.3")
                implementation("com.squareup.okio:okio:3.5.0")
                implementation("org.jmdns:jmdns:3.5.8")
                implementation("org.jetbrains.kotlinx:atomicfu:0.21.0")
                implementation("com.soywiz.korlibs.krypto:krypto:4.0.8")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                api("io.insert-koin:koin-core:3.4.3")
                api("co.touchlab:kermit:1.1.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                api("androidx.appcompat:appcompat:1.6.1")
                api("androidx.core:core-ktx:1.12.0")
                implementation("androidx.documentfile:documentfile:1.0.1")
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
            }
        }
        val desktopMain by getting {
            dependencies {
                api(compose.preview)
            }
        }
        val desktopTest by getting
    }
}

android {
    namespace = "com.ethossoftworks.land"
    compileSdk = 34
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 24
        targetSdk = 34
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}