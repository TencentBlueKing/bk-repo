package com.tencent.bkrepo.common.artifact.resolve.file.receiver

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.api.util.TraceUtils
import com.tencent.bkrepo.common.artifact.exception.ArtifactReceiveException
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetrics
import com.tencent.bkrepo.common.artifact.metrics.TrafficHandler
import com.tencent.bkrepo.common.artifact.stream.DigestCalculateListener
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.common.artifact.util.http.IOExceptionUtils
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.ratelimiter.stream.CommonRateLimitInputStream
import com.tencent.bkrepo.common.storage.config.ReceiveProperties
import com.tencent.bkrepo.common.storage.monitor.Throughput
import io.micrometer.common.KeyValues
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import kotlin.system.measureTimeMillis

/**
 * Artifact数据接收器
 */
abstract class AbsArtifactDataReceiver(
    private val receiveProperties: ReceiveProperties,
    private val requestLimitCheckService: RequestLimitCheckService? = null,
    private val registry: ObservationRegistry,
    private val contentLength: Long? = null,
) : AutoCloseable {
    /**
     * 数据传输buffer大小
     */
    protected val bufferSize = receiveProperties.bufferSize.toBytes().toInt()

    /**
     * 动态阈值，超过该阈值将数据落磁盘
     */
    protected val fileSizeThreshold = receiveProperties.fileSizeThreshold.toBytes()

    /**
     * 内存缓存数组
     */
    protected var contentBytes: ByteArrayOutputStream? = ByteArrayOutputStream(bufferSize)

    /**
     * outputStream，初始化指向内存缓存数组
     */
    protected var outputStream: OutputStream = contentBytes!!

    /**
     * 数据摘要计算监听类
     */
    val listener = DigestCalculateListener()

    /**
     * 文件数据是否在内存缓存
     */
    var inMemory: Boolean = true
        protected set

    /**
     * 接收开始时间
     */
    var startTime = 0L
        private set

    /**
     * 接收结束时间
     */
    var endTime = 0L
        private set

    protected var flushTime = 0L

    /**
     * 接收字节数
     */
    var received = 0L
        private set

    /**
     * 接收是否完成
     */
    var finished = false
        private set

    private var trafficHandler: TrafficHandler? = null

    /**
     * 缓存数组
     */
    val cachedByteArray: ByteArray?
        get() = contentBytes?.toByteArray()


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
            afterReceived(chunk, offset, length)
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
     * 完成数据接收的回调，完成哈希计算、缓存阈值检测写盘等操作
     *
     * @param chunk 接收到的数据
     * @param offset 偏移量
     * @param length 数据大小
     */
    protected open fun afterReceived(chunk: ByteArray, offset: Int, length: Int) {
        listener.data(chunk, offset, length)
        received += length
        checkThresholdAndFlush()
    }

    /**
     * 完成数据接收的回调，完成哈希计算、缓存阈值检测写盘等操作
     *
     * @param b 接收到的数据
     */
    protected open fun afterReceived(b: Int) {
        listener.data(b)
        received += 1
        checkThresholdAndFlush()
    }

    /**
     * 检查文件接受阈值，超过内存阈值时将写入文件中，
     * 同时检查是否超过本地上传阈值，如果未超过，则使用本地磁盘
     */
    private fun checkThresholdAndFlush() {
        if (inMemory && received > fileSizeThreshold) {
            flush(false)
        }
    }

    /**
     * 将内存数据写入到磁盘中
     * @param closeStream 写入后是否关闭原始output stream, 当用户主动触发时，需要设置为true
     */
    open fun flush(closeStream: Boolean = true) {
        if (inMemory) {
            flushTime = System.currentTimeMillis()
            val millis = measureTimeMillis { outputStream = doFlush() }
            inMemory = false
            recordQuiet(contentBytes!!.size(), Duration.ofMillis(millis), true)
            if (closeStream) {
                cleanOriginalOutputStream()
            }
            // help gc
            contentBytes = null
        }
    }

    /**
     * 将数据刷入持久化输出流并返回新建的输出流
     */
    protected abstract fun doFlush(): OutputStream

    /**
     * 接收完毕后，检查接收到的字节数和实际的字节数是否一致
     * 生产环境中出现过不一致的情况，所以加此校验
     */
    private fun checkSize() {
        if (inMemory) {
            val actualSize = contentBytes!!.size().toLong()
            require(received == actualSize) {
                "$received bytes received, but $actualSize bytes saved in memory."
            }
        } else {
            doCheckSize()
        }
    }

    protected abstract fun doCheckSize()

    abstract fun getInputStream(): InputStream

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
    fun finish(): Throughput {
        if (!finished) {
            try {
                finished = true
                endTime = System.nanoTime()
                checkSize()
                listener.finished()
            } finally {
                cleanOriginalOutputStream()
            }
        }
        return Throughput(received, endTime - startTime)
    }

    override fun close() {
        cleanOriginalOutputStream()
    }

    /**
     * 关闭原始输出流
     */
    fun cleanOriginalOutputStream() {
        try {
            outputStream.flush()
        } catch (ignored: IOException) {
        }

        try {
            outputStream.close()
        } catch (ignored: IOException) {
        }
    }

    /**
     * 刷新流量处理器
     * 当文件冲刷到本地时，需要更新流量处理器，以进行正确的度量计算
     * */
    protected fun refreshTrafficHandler() {
        trafficHandler = TrafficHandler(
            ArtifactMetrics.getUploadingCounters(this),
            ArtifactMetrics.getUploadingTimer(this),
        )
    }

    /**
     * 静默采集metrics
     * */
    protected fun recordQuiet(size: Int, elapse: Duration, refresh: Boolean = false) {
        try {
            if (refresh || trafficHandler == null) {
                refreshTrafficHandler()
            }
            trafficHandler?.record(size, elapse)
        } catch (e: Exception) {
            logger.error("Record upload metrics error", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbsArtifactDataReceiver::class.java)
    }
}
