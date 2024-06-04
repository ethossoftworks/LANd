import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.FileInputStream
import java.util.*

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.skie)
    alias(libs.plugins.openSourceLicenses)
    kotlin("plugin.serialization") version(libs.versions.kotlin)
}

val buildInfoProps = Properties().apply {
    load(FileInputStream(File(rootProject.rootDir, "buildInfo.properties")))
}

version = buildInfoProps["version"] as? String ?: "0.0.0"
val buildNumber = buildInfoProps["build"]?.toString()?.toInt() ?: 1

val lwjglVersion = "3.3.3"

val lwjglNatives = Pair(
    System.getProperty("os.name")!!,
    System.getProperty("os.arch")!!
).let { (name, arch) ->
    when {
        arrayOf("Linux", "FreeBSD", "SunOS", "Unit").any { name.startsWith(it) } -> {
            if (arrayOf("arm", "aarch64").any { arch.startsWith(it) }) {
                "natives-linux${if (arch.contains("64") || arch.startsWith("armv8")) "-arm64" else "-arm32"}"
            } else {
                "natives-linux"
            }
        }
        arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) } -> {
            "natives-macos${if (arch.startsWith("aarch64")) "-arm64" else ""}"
        }
        arrayOf("Windows").any { name.startsWith(it) } -> {
            if (arch.contains("64")) {
                "natives-windows${if (arch.startsWith("aarch64")) "-arm64" else ""}"
            } else {
                "natives-windows-x86"
            }
        }
        else -> throw Error("Unrecognized or unsupported platform. Please set \"lwjglNatives\" manually")
    }
}

kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export("com.outsidesource:oskit-kmp:${libs.versions.oskitKmp.get()}")
            export("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.kotlinxCoroutinesCore.get()}")
        }
    }

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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.ktor.network)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.jmdns)
            implementation(libs.krypto)
            implementation(libs.bignumber)
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
            runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
            runtimeOnly("org.lwjgl:lwjgl-tinyfd:$lwjglVersion:$lwjglNatives")
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
            val osName = System.getProperty("os.name").lowercase()
            when {
                osName.contains("mac") -> targetFormats(TargetFormat.Dmg) // Fixes an issue where app won't run on MacOS
                else -> targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.AppImage)
            }

            packageName = "LANd"
            vendor = "Ethos Softworks"
            packageVersion = version.toString()
            modules("java.sql", "jdk.unsupported")

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

licenseReport {
    generateJsonReport = true
    generateHtmlReport = false
    copyJsonReportToAssets = true
}