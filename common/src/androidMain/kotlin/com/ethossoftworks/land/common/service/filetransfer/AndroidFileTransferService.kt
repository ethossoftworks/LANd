package com.ethossoftworks.land.common.service.filetransfer

import okio.IOException
import java.net.ConnectException

actual fun FileTransferService.isClosedConnectionException(exception: Throwable): Boolean {
    if (exception is ConnectException) return true
    return exception is IOException && exception.message == "Broken pipe"
}