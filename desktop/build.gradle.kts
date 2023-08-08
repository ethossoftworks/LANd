import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.FileInputStream
import java.util.*

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

val versionProps = Properties().apply {
    load(FileInputStream(File(rootProject.rootDir, "version.properties")))
}

group = "com.ethossoftworks"
version = versionProps["version"] as? String ?: "0.0.0"
val buildNumber = versionProps["build"]?.toString()?.toInt() ?: 1

kotlin {
    jvm {
        jvmToolchain(11)
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation(compose.desktop.currentOs)
            }
        }
        val jvmTest by getting
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
        }
    }
}
