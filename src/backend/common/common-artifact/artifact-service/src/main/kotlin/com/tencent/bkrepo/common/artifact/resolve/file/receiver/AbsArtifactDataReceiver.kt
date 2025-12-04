package com.tencent.bkrepo.common.artifact.resolve.file.receiver

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.api.util.TraceUtils
import com.tencent.bkrepo.common.artifact.exception.ArtifactReceiveException
import com.tencent.bkrepo.common.artifact.stream.StreamReceiveListener
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.common.artifact.util.http.IOExceptionUtils
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.ratelimiter.stream.CommonRateLimitInputStream
import com.tencent.bkrepo.common.storage.config.ReceiveProperties
import com.tencent.bkrepo.common.storage.monitor.Throughput
import io.micrometer.common.KeyValues
import io.micrometer.observation.ObservationRegistry
import java.io.IOException
import java.io.InputStream

/**
 * Artifact数据接收器
 */
abstract class AbsArtifactDataReceiver(
    private val receiveProperties: ReceiveProperties,
    private val requestLimitCheckService: RequestLimitCheckService? = null,
    private val registry: ObservationRegistry,
    protected val contentLength: Long? = null,
) : AutoCloseable {

    /**
     * 数据摘要计算监听类
     */
    abstract val listener: StreamReceiveListener

    /**
     * 文件数据是否在内存缓存
     */
    abstract var inMemory: Boolean
        protected set

    /**
     * 接收开始时间
     */
    private var startTime = 0L

    /**
     * 接收结束时间
     */
    private var endTime = 0L

    /**
     * 接收是否完成
     */
    var finished = false
        private set

    /**
     * 接收数据块
     * @param chunk 字节数组
     * @param offset 偏移量
     * @param length 数据长度
     */
    fun receiveChunk(chunk: ByteArray, offset: Int, length: Int) {
        require(!finished) { "Receiver is close" }
        if (startTime == 0L) {
            startTime = System.nanoTime()
        }
        try {
            requestLimitCheckService?.uploadBandwidthCheck(length.toLong(), receiveProperties.circuitBreakerThreshold)
            doReceiveChunk(chunk, offset, length)
        } catch (exception: IOException) {
            handleIOException(exception)
        } catch (overloadEx: OverloadException) {
            handleOverloadException(overloadEx)
        }
    }

    protected abstract fun doReceiveChunk(chunk: ByteArray, offset: Int, length: Int)

    /**
     * 接受单个字节数据
     * @param b 字节数据
     * */
    fun receive(b: Int) {
        require(!finished) { "Receiver is close" }
        if (startTime == 0L) {
            startTime = System.nanoTime()
        }
        try {
            requestLimitCheckService?.uploadBandwidthCheck(1, receiveProperties.circuitBreakerThreshold)
            doReceive(b)
        } catch (exception: IOException) {
            handleIOException(exception)
        } catch (overloadEx: OverloadException) {
            handleOverloadException(overloadEx)
        }
    }

    protected abstract fun doReceive(b: Int)

    /**
     * 接收数据流
     * @param source 数据流
     */
    fun receiveStream(source: InputStream) {
        require(!finished) { "Receiver is close" }
        TraceUtils.newSpan(registry, "receive stream", KeyValues.empty(), KeyValues.empty()) {
            if (startTime == 0L) {
                startTime = System.nanoTime()
            }
            var rateLimitFlag = false
            var exp: Exception? = null
            try {
                val input = requestLimitCheckService?.bandwidthCheck(
                    source, receiveProperties.circuitBreakerThreshold, contentLength
                ) ?: source.rateLimit(receiveProperties.rateLimit.toBytes())
                rateLimitFlag = input is CommonRateLimitInputStream
                doReceiveStream(input)
            } catch (exception: IOException) {
                exp = exception
                handleIOException(exception)
            } catch (overloadEx: OverloadException) {
                exp = overloadEx
                handleOverloadException(overloadEx)
            } finally {
                if (rateLimitFlag) {
                    requestLimitCheckService?.bandwidthFinish(exp)
                }
            }
        }
    }

    protected abstract fun doReceiveStream(source: InputStream)

    /**
     * 接收完毕后，检查接收到的字节数和实际的字节数是否一致
     * 生产环境中出现过不一致的情况，所以加此校验
     */
    protected abstract fun checkSize()

    abstract fun getInputStream(): InputStream

    /**
     * 获取已接收的数据大小
     */
    abstract fun receivedSize(): Long

    /**
     * 处理IO异常
     */
    private fun handleIOException(exception: IOException) {
        finishWithException()
        if (IOExceptionUtils.isClientBroken(exception)) {
            throw ArtifactReceiveException(exception.message.orEmpty())
        } else {
            throw exception
        }
    }

    /**
     * 处理限流请求
     */
    private fun handleOverloadException(exception: OverloadException) {
        finishWithException()
        throw exception
    }

    private fun finishWithException() {
        finished = true
        endTime = System.nanoTime()
        close()
    }

    /**
     * 数据接收完成,当数据传输完毕后需要调用该函数
     */
    open fun finish(): Throughput {
        if (!finished) {
            finished = true
            endTime = System.nanoTime()
            checkSize()
            listener.finished()
        }
        return Throughput(receivedSize(), endTime - startTime)
    }
}
