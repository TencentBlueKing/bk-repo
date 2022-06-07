package com.tencent.bkrepo.common.api.stream

import java.io.InputStream
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.measureTimeMillis

/**
 * 分块输入流
 * 将多个文件Future封装成一个流，按文件顺序读取
 * */
class ChunkedFutureInputStream<T>(
    futures: List<ChunkedFuture<T>>,
    private val chunkedFutureListeners: List<ChunkedFutureListener<T>>? = null
) : InputStream() {

    private val iterator = futures.iterator()
    private lateinit var currentFuture: ChunkedFuture<T>
    private lateinit var cursor: InputStream
    private var getInputStreamTime: Long = 0
    private var listeners: MutableList<ChunkedFutureListener<T>> = arrayListOf()

    init {
        chunkedFutureListeners?.let { listeners.addAll(chunkedFutureListeners) }
        move2next()
    }

    override fun read(): Int {
        val read = cursor.read()
        if (read == -1 && iterator.hasNext()) {
            cursor.close()
            notify(currentFuture)
            move2next()
            return cursor.read()
        }
        return read
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = cursor.read(b, off, len)
        if (read == -1 && iterator.hasNext()) {
            cursor.close()
            notify(currentFuture)
            move2next()
            return cursor.read(b, off, len)
        }
        return read
    }

    private fun getInputStream(future: ChunkedFuture<T>): InputStream {
        val ret = future.get(TIMEOUT, TimeUnit.MILLISECONDS)
        return future.getInputStream(ret)
    }

    private fun nextFuture(): ChunkedFuture<T> {
        return iterator.next()
    }

    private fun move2next() {
        currentFuture = nextFuture()
        measureTimeMillis {
            try {
                cursor = getInputStream(currentFuture)
            } catch (e: TimeoutException) {
                getInputStreamTime = TIMEOUT
                // 由于currentFuture已经get超时，所以这里避免listener获取重复超时，所以传null下去
                notify(null)
                throw e
            }
        }.apply { getInputStreamTime = this }
    }

    override fun close() {
        cursor.close()
    }

    private fun notify(future: Future<T>?) {
        listeners.forEach { it.done(future, getInputStreamTime) }
    }

    companion object {
        // 30s
        private const val TIMEOUT = 30_000L
    }
}
