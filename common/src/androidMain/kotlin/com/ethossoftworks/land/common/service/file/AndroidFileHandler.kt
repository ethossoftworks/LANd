package com.ethossoftworks.land.common.service.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Sink
import okio.Source
import okio.sink
import okio.source

actual sealed class FileHandlerContext {
    data class Android(
        val applicationContext: Context,
        val activity: ComponentActivity,
    ): FileHandlerContext()
}

class AndroidFileHandler: IFileHandler {
    private var context: Context? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var openFileResultLauncher: ActivityResultLauncher<Array<String>>? = null
    private val openFileResultFlow = MutableSharedFlow<Uri?>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var openFolderResultLauncher: ActivityResultLauncher<Uri?>? = null
    private val openFolderResultFlow = MutableSharedFlow<Uri?>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun defaultSaveFolder(): String? {
        return null
    }

    override fun init(fileHandlerContext: FileHandlerContext) {
        fileHandlerContext as FileHandlerContext.Android
        context = fileHandlerContext.applicationContext

        openFileResultLauncher = fileHandlerContext.activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { data ->
            coroutineScope.launch {
                openFileResultFlow.emit(data)
            }
        }

        openFolderResultLauncher = fileHandlerContext.activity.registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { data ->
            coroutineScope.launch {
                openFolderResultFlow.emit(data)
            }
        }
    }

    override suspend fun selectFolder(): Outcome<String?, Any> {
        return try {
            val folderResultLauncher = openFolderResultLauncher ?: return Outcome.Error(FileHandlerError.NotInitialized)
            val context = context ?: return Outcome.Error(FileHandlerError.NotInitialized)

            folderResultLauncher.launch(null)
            val uri = openFolderResultFlow.first() ?: return Outcome.Ok(null)

            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            Outcome.Ok(uri.toString())
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun selectFile(): Outcome<String?, Any> {
        return try {
            val fileResultLauncher = openFileResultLauncher ?: return Outcome.Error(FileHandlerError.NotInitialized)

            fileResultLauncher.launch(arrayOf("*/*"))

            Outcome.Ok(openFileResultFlow.first()?.toString())
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun openFileToWrite(
        folder: String,
        name: String,
        mode: FileWriteMode,
    ): Outcome<Sink, Any> {
        return try {
            val modeString = when (mode) {
                FileWriteMode.Overwrite -> "wt"
                FileWriteMode.Append -> "wa"
            }

            val context = context ?: return Outcome.Error(FileHandlerError.NotInitialized)
            val parentUri = DocumentFile.fromTreeUri(context, folder.toUri()) ?: return Outcome.Error(FileHandlerError.FolderDoesntExist)
            val file = parentUri.createFile("", name) ?: return Outcome.Error(FileHandlerError.CouldNotCreateFile)
            val outputStream = context.contentResolver.openOutputStream(file.uri, modeString) ?: return Outcome.Error(FileHandlerError.CouldNotCreateFile)

            Outcome.Ok(outputStream.sink())
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun openFileToRead(path: String): Outcome<Source, Any> {
        return try {
            val context = context ?: return Outcome.Error(FileHandlerError.NotInitialized)
            val stream = context.contentResolver.openInputStream(path.toUri()) ?: return Outcome.Error(FileHandlerError.FileDoesntExist)

            return Outcome.Ok(stream.source())
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun readFileMetadata(folder: String, name: String): Outcome<FileMetadata, Any> {
        val context = context ?: return Outcome.Error(FileHandlerError.NotInitialized)
        val parentUri = DocumentFile.fromTreeUri(context, folder.toUri()) ?: return Outcome.Error(FileHandlerError.FolderDoesntExist)
        val file = parentUri.findFile(name) ?: return Outcome.Error(FileHandlerError.FileDoesntExist)
        return readFileMetadata(file.uri.toString())
    }

    override suspend fun readFileMetadata(path: String): Outcome<FileMetadata, Any> {
        return try {
            val context = context ?: return Outcome.Error(FileHandlerError.NotInitialized)
            val size: Long
            val name: String

            val cursor = context.contentResolver.query(
                path.toUri(),
                null,
                null,
                null,
                null
            ) ?: return Outcome.Error(FileHandlerError.NoMetaData)

            cursor.use {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                it.moveToFirst()
                size = it.getLong(sizeIndex)
                name = it.getString(nameIndex)
            }

            Outcome.Ok(FileMetadata(length = size, name = name))
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }
}