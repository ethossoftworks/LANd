package com.ethossoftworks.land.common.service.filetransfer

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

const val FILE_TRANSFER_PORT = 7788

class FileTransferService: IFileTransferService {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val bufferSize = 65_536

    override suspend fun startServer(): Flow<FileTransferServerEvent> = channelFlow {
        withContext(Dispatchers.IO) {
            try {
                val serverSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", FILE_TRANSFER_PORT)
                send(FileTransferServerEvent.ServerStarted)

                supervisorScope {
                    while (isActive) {
                        val socket = serverSocket.accept()

                        launch {
                            receiveConnection(socket, channel)
                        }
                    }
                }
            } catch (e: Exception) {
                send(FileTransferServerEvent.ServerStopped(e))
            }
        }
    }

    override suspend fun sendFile(
        file: FileTransfer,
        destinationIp: String
    ): Flow<FileTransferProgress> = channelFlow {
        try {
            val socket = aSocket(selectorManager).tcp().connect(destinationIp, FILE_TRANSFER_PORT)
            val readBuffer = ByteArray(bufferSize)
            val writeBuffer = ByteArray(bufferSize)
            val readChannel = socket.openReadChannel()
            val writeChannel = socket.openWriteChannel()

            val read = readChannel.readAvailable(readBuffer, 0, 2)
        } catch (e: Exception) {
            send(FileTransferProgress.TransferStopped(0, FileTransferStopReason.Unknown))
        }
    }

    private suspend fun receiveConnection(
        socket: Socket,
        sendChannel: SendChannel<FileTransferServerEvent>
    ) {
        val readBuffer = ByteArray(bufferSize)
        val writeBuffer = ByteArray(bufferSize)
        val readChannel = socket.openReadChannel()
        val writeChannel = socket.openWriteChannel()

        writeChannel.writeFully(byteArrayOf(0x00,0x01), 0, 2)
        writeChannel.flush()
    }
}