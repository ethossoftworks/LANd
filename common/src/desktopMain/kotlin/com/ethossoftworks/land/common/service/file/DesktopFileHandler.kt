package com.ethossoftworks.land.common.service.file

import androidx.compose.ui.awt.ComposeWindow
import com.outsidesource.oskitkmp.outcome.Outcome
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_selectFolderDialog
import java.awt.FileDialog

actual sealed class FileHandlerContext {
    data class Desktop(val window: ComposeWindow): FileHandlerContext()
}

class DesktopFileHandler : IFileHandler {
    private var window: ComposeWindow? = null

    override fun defaultSaveFolder(): String? {
        return "${System.getProperty("user.home")}/Desktop"
    }

    override fun init(fileHandlerContext: FileHandlerContext) {
        this.window = (fileHandlerContext as FileHandlerContext.Desktop).window
    }

    override suspend fun selectFolder(): Outcome<String?, Any> {
        return try {
            Outcome.Ok(tinyfd_selectFolderDialog("Select Folder", ""))
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun selectFile(): Outcome<String?, Any> {
        return try {
            val window = this.window ?: return Outcome.Error(FileHandlerError.NotInitialized)
            val dialog = FileDialog(window, "Select File", FileDialog.LOAD)
            dialog.isVisible = true
            Outcome.Ok(dialog.file)
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun openFileToWrite(folder: String, name: String): Outcome<Sink, Any> {
        return try {
            Outcome.Ok(FileSystem.SYSTEM.sink("$folder/$name".toPath()))
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun openFileToRead(folder: String, name: String): Outcome<Source, Any> {
        return try {
            Outcome.Ok(FileSystem.SYSTEM.source("$folder/$name".toPath()))
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }

    override suspend fun readFileMetadata(folder: String, name: String): Outcome<FileMetadata, Any> {
        return try {
            val path = "${folder}/${name}".toPath()
            val metadata = FileSystem.SYSTEM.metadata(path)
            val length = metadata.size ?: return Outcome.Error(FileHandlerError.NoMetaData)
            Outcome.Ok(FileMetadata(length = length))
        } catch (e: Exception) {
            Outcome.Error(e)
        }
    }
}