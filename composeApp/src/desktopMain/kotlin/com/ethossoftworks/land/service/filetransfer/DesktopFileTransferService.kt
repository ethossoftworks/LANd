package com.ethossoftworks.land.service.filetransfer

import com.ethossoftworks.land.service.filetransfer.FileTransferService
import java.io.IOException
import java.net.ConnectException

actual fun FileTransferService.isClosedConnectionException(exception: Any): Boolean {
    if (exception is ConnectException) return true
    return exception is IOException && exception.message == "Broken pipe"
}