package com.ethossoftworks.land.lib

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * [SequentialBufferPool] A buffer pool that allows shared access to sequential buffers across multiple threads.
 * This class was designed for concurrent filling and freeing of buffers. For example, reading a file and transferring
 * it via the network concurrently.
 */
class SequentialBufferPool(private val poolSize: Int, bufferSize: Int) {
    private val pool = Array(poolSize) { BufferWrapper(id = it, buffer = ByteArray(bufferSize)) }
    private val nextAvailable = atomic(0)
    private val nextFull = atomic(0)

    suspend fun getFreeBuffer(): BufferWrapper {
        val id = nextAvailable.value % poolSize
        nextAvailable.incrementAndGet()
//        println("Awaiting free - $id")
        pool[id].state.awaitFree()
//        println("Received free - $id")
        return pool[id]
    }

    suspend fun getFullBuffer(): BufferWrapper {
        val id = nextFull.value % poolSize
        nextFull.incrementAndGet()
//        println("Awaiting full - $id")
        pool[id].state.awaitFull()
//        println("Received full - $id")
//        val buffer = pool[id].buffer
//        val bytesUsed = pool[id].bytesUsed
        return pool[id]
    }

    fun markBufferFree(id: Int) {
//        println("Marking free - $id")
        pool[id].state.setFree()
//        println("Set to free - $id")
    }

    fun markBufferFull(id: Int, bytesUsed: Int) {
//        println("Marking full - $id")
        pool[id].bytesUsed = bytesUsed
        pool[id].state.setFull()
//        println("Set to full - $id")
    }
}

class BufferWrapper(
    val id: Int = 0,
    val buffer: ByteArray,
    var bytesUsed: Int = 0, // TODO: This should be private/internal
    internal val state: BufferStateSignal = BufferStateSignal(),
)

internal enum class BufferState {
    Free,
    Full,
}

class BufferStateSignal {
    private val state = atomic(BufferState.Free)

    suspend fun awaitFree() {
        if (state.value == BufferState.Free) return
        while (coroutineContext.isActive) { if (state.value == BufferState.Free) return }
    }

    suspend fun awaitFull() {
        if (state.value == BufferState.Full) return
        while (coroutineContext.isActive) { if (state.value == BufferState.Full) return }
    }

    fun setFree() {
        state.update { BufferState.Free }
    }

    fun setFull() {
        state.update { BufferState.Full }
    }
}