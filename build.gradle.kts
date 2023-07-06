group = "com.ethossoftworks"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven {
            url = uri("https://maven.pkg.github.com/outsidesource/OSKit-KMP")
            credentials {
                val credentialProperties = java.util.Properties()
                File(rootDir, "credential.properties").reader().use { stream -> credentialProperties.load(stream) }
                username = credentialProperties["username"].toString()
                password = credentialProperties["password"].toString()
            }
        }
    }
}

plugins {
    kotlin("multiplatform") apply false
    kotlin("android") apply false
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("org.jetbrains.compose") apply false
}