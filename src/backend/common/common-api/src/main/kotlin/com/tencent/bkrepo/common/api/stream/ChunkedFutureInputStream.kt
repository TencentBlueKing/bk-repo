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
open class ChunkedFutureInputStream<T>(
    futures: List<ChunkedFuture<T>>,
    val timeout: Long,
    private val chunkedFutureListeners: List<ChunkedFutureListener<T>>? = null
) : InputStream() {

    private val iterator = futures.iterator()
    lateinit var currentFuture: ChunkedFuture<T>
    private lateinit var cursor: InputStream
    private var getInputStreamTime: Long = 0
    private var listeners: MutableList<ChunkedFutureListener<T>> = arrayListOf()

    init {
        chunkedFutureListeners?.let { listeners.addAll(chunkedFutureListeners) }
        move2next()
    }

    override fun read(): Int {
        val read = cursor.read()
        if (read == -1) {
            cursor.close()
            notify(currentFuture)
            if (iterator.hasNext()) {
                move2next()
                return cursor.read()
            }
        }
        return read
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = cursor.read(b, off, len)
        if (read == -1) {
            cursor.close()
            notify(currentFuture)
            if (iterator.hasNext()) {
                move2next()
                return cursor.read(b, off, len)
            }
        }
        return read
    }

    private fun nextFuture(): ChunkedFuture<T> {
        return iterator.next()
    }

    private fun move2next() {
        currentFuture = nextFuture()
        measureTimeMillis {
            try {
                cursor = currentFuture.getInputStream(timeout, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                getInputStreamTime = timeout
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
}
