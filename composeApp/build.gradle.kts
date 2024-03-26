import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.FileInputStream
import java.util.*

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    kotlin("plugin.serialization") version(libs.versions.kotlin)
}

val buildInfoProps = Properties().apply {
    load(FileInputStream(File(rootProject.rootDir, "buildInfo.properties")))
}

version = buildInfoProps["version"] as? String ?: "0.0.0"
val buildNumber = buildInfoProps["build"]?.toString()?.toInt() ?: 1

kotlin {
    androidTarget {
        jvmToolchain(17)
    }

    jvm("desktop") {
        jvmToolchain(17)
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting
        val androidInstrumentedTest by getting

        generateBuildInfo(rootDir, buildDir)

        commonMain.invoke {
            kotlin.srcDir("${buildDir}/es-generated")
        }

        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material)
            api(libs.oskit.kmp)
            api(libs.oskit.compose)
            api(libs.koin.core)
            api(libs.kermit)
            implementation(compose.components.resources)
            implementation(libs.okio)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.ktor.network)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.jmdns)
            implementation(libs.krytpo)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.ui)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.junit)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "com.ethossoftworks.land"
    compileSdk = libs.versions.android.targetSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        kotlin.jvmToolchain(17)
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LANd"
            vendor = "Ethos Softworks"
            packageVersion = version.toString()

            macOS {
                packageBuildVersion = properties["osbuild.buildNumber"]?.toString()
                iconFile.set(project.file("src/desktopMain/icons/app-icon-macos.icns"))
                bundleID = "com.ethossoftworks.LANd"
                copyright = "Copyright \u00a9 2024 Ethos Softworks"
            }

            windows {
                iconFile.set(project.file("src/desktopMain/icons/app-icon-windows.ico"))
                shortcut = true
                menu = true
                menuGroup = "LANd"
            }

            linux {
                iconFile.set(project.file("src/desktopMain/icons/app-icon-linux.png"))
            }
        }
    }
}
