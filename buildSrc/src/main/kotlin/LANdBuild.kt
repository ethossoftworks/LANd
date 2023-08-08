import java.io.File
import java.io.FileInputStream
import java.util.*

fun generateLANdBuildFile(rootDir: File, buildDir: File) {
    val directory = File("${buildDir}/generated/com/ethossoftworks/land/")
    directory.mkdirs()
    val versionProps = readVersionProps(rootDir)

    File(directory, "LANdBuild.kt").bufferedWriter().use {
        it.write("package com.ethossoftworks.land\n\n")
        it.write("class LANdBuild {\n")
        it.write("\tcompanion object {\n")
        it.write("\t\tconst val version: String = \"${versionProps["version"]}\"\n")
        it.write("\t\tconst val buildNumber: String = \"${versionProps["build"]}\"\n")
        it.write("\t}\n")
        it.write("}\n")
    }
}

fun readVersionProps(rootDir: File): Properties {
    val versionFile = File(rootDir, "version.properties")
    val props = Properties()
    FileInputStream(versionFile).use { stream -> props.load(stream) }
    return props
}