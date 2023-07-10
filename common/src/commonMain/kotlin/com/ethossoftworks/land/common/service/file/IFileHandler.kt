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
    suspend fun openFileToWrite(folder: String, name: String): Outcome<Sink, Any>
    suspend fun openFileToRead(folder: String, name: String): Outcome<Source, Any>
    suspend fun readFileMetadata(folder: String, name: String): Outcome<FileMetadata, Any>
}

expect sealed class FileHandlerContext

data class FileMetadata(
    val length: Long,
)