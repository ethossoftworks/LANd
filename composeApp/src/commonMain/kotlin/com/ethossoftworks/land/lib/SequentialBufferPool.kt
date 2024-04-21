package com.ethossoftworks.land.lib

import com.outsidesource.oskitkmp.tuples.Tup2
import com.outsidesource.oskitkmp.tuples.Tup3
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

// TODO: This works but it's very slow. Tried just using channels but it was not any faster.

/**
 * [SequentialBufferPool] A buffer pool that allows shared access to sequential buffers across multiple threads.
 * This class was designed for concurrent filling and freeing of buffers. For example, reading a file and transferring
 * it via the network concurrently.
 */
class SequentialBufferPool(private val poolSize: Int, bufferSize: Int) {
    private val pool = Array(poolSize) { BufferWrapper(buffer = ByteArray(bufferSize)) }
    private val nextAvailable = atomic(0)
    private val nextFull = atomic(0)

    suspend fun getFreeBuffer(): Tup2<Int, ByteArray> {
        val id = nextAvailable.value % poolSize
        nextAvailable.incrementAndGet()
        println("Awaiting free - $id")
        pool[id].state.awaitFree()
        println("Received free - $id")
        val buffer = pool[id].buffer
        return Tup2(id, buffer)
    }

    suspend fun getFullBuffer(): Tup3<Int, Int, ByteArray> {
        val id = nextFull.value % poolSize
        nextFull.incrementAndGet()
        println("Awaiting full - $id")
        pool[id].state.awaitFull()
        println("Received full - $id")
        val buffer = pool[id].buffer
        val bytesUsed = pool[id].bytesUsed
        return Tup3(id, bytesUsed, buffer)
    }

    fun markBufferFree(id: Int) {
        println("Marking free - $id")
        pool[id].state.setFree()
        println("Set to free - $id")
    }

    fun markBufferFull(id: Int, bytesUsed: Int) {
        println("Marking full - $id")
        pool[id].bytesUsed = bytesUsed
        pool[id].state.setFull()
        println("Set to full - $id")
    }
}

private class BufferWrapper(
    var bytesUsed: Int = 0,
    val buffer: ByteArray,
    val state: BufferStateSignal = BufferStateSignal(),
)

private enum class BufferState {
    Free,
    Full,
}

private class BufferStateSignal {
    private val state = MutableStateFlow(BufferState.Free)

    suspend fun awaitFree() {
        if (state.value == BufferState.Free) return
        state.first { it == BufferState.Free }
    }

    suspend fun awaitFull() {
        if (state.value == BufferState.Full) return
        state.first { it == BufferState.Full }
    }

    fun setFree() {
        state.update { BufferState.Free }
    }

    fun setFull() {
        state.update { BufferState.Full }
    }
}