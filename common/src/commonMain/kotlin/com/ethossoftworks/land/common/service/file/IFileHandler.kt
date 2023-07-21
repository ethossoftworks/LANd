package com.ethossoftworks.land.common.service.file

import com.outsidesource.oskitkmp.outcome.Outcome
import okio.Sink
import okio.Source

enum class FileHandlerError {
    NoMetaData,
    NotInitialized,
    FolderDoesntExist,
    FileDoesntExist,
    CouldNotCreateFile,
}

interface IFileHandler {
    fun defaultSaveFolder(): String?
    fun init(fileHandlerContext: FileHandlerContext)
    suspend fun selectFolder(): Outcome<String?, Any>
    suspend fun selectFile(): Outcome<String?, Any>
    suspend fun openFileToWrite(folder: String, name: String, mode: FileWriteMode = FileWriteMode.Overwrite): Outcome<Sink, Any>
    suspend fun openFileToRead(path: String): Outcome<Source, Any>
    suspend fun readFileMetadata(path: String): Outcome<FileMetadata, Any>
    suspend fun readFileMetadata(folder: String, name: String): Outcome<FileMetadata, Any>
}

enum class FileWriteMode {
    Append,
    Overwrite,
}

expect sealed class FileHandlerContext

data class FileMetadata(
    val length: Long,
    val name: String,
    val isDirectory: Boolean,
)