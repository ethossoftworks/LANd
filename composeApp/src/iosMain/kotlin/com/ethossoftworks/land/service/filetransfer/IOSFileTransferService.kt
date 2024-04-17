package com.ethossoftworks.land.service.filetransfer

// TODO: Can't seem to get this to fire to determine what specific exception it is
actual fun FileTransferService.isClosedConnectionException(exception: Any): Boolean {
    return false
}