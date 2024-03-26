import java.io.File
import java.io.FileInputStream
import java.util.*

fun generateBuildInfo(rootDir: File, buildDir: File) {
    val directory = File("${buildDir}/es-generated/com/ethossoftworks/land/")
    directory.mkdirs()
    val buildInfoProps = readBuildInfoProps(rootDir)

    File(directory, "BuildInfo.kt").bufferedWriter().use {
        it.write("package com.ethossoftworks.land\n\n")
        it.write("class BuildInfo {\n")
        it.write("\tcompanion object {\n")
        buildInfoProps.forEach { (k, v) ->
            try {
                if (v.toString() == "true" || v.toString() == "false") {
                    it.write("\t\tconst val $k: Boolean = $v\n")
                } else if (v.toString().matches(Regex("^[0-9]+$"))) {
                    it.write("\t\tconst val $k: Int = $v\n")
                } else if (v.toString().matches(Regex("^[0-9.]+$"))) {
                    it.write("\t\tconst val $k: Float = ${v.toString().toFloat()}f\n")
                } else {
                    it.write("\t\tconst val $k: String = \"$v\"\n")
                }
            } catch (e: Exception) {
                it.write("\t\tconst val $k: String = \"$v\"\n")
            }
        }
        it.write("\t}\n")
        it.write("}\n")
    }
}

fun readBuildInfoProps(rootDir: File): Properties {
    val versionFile = File(rootDir, "buildInfo.properties")
    val props = Properties()
    FileInputStream(versionFile).use { stream -> props.load(stream) }

    System.getenv().forEach { (k, v) ->
        if (!k.startsWith("buildInfo--")) return@forEach
        props.setProperty(k.removePrefix("buildInfo--"), v)
    }

    return props
}